/*
 * Copyright 2020 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.maven.internal;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.openrewrite.Parser;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.maven.cache.CacheResult;
import org.openrewrite.maven.cache.MavenCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toList;

public class MavenDownloader {
    private static final Logger logger = LoggerFactory.getLogger(MavenDownloader.class);

    private static final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectionSpecs(Arrays.asList(ConnectionSpec.CLEARTEXT, ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS))
            .build();

    // https://maven.apache.org/ref/3.6.3/maven-model-builder/super-pom.html
    private static final RawRepositories.Repository SUPER_POM_REPOSITORY = new RawRepositories.Repository("https://repo.maven.apache.org/maven2",
            new RawRepositories.ArtifactPolicy(true), new RawRepositories.ArtifactPolicy(false));

    private final MavenCache mavenCache;
    private final Map<String, RawMaven> projectPoms;

    public MavenDownloader(MavenCache mavenCache) {
        this(mavenCache, emptyMap());
    }

    public MavenDownloader(MavenCache mavenCache, Map<String, RawMaven> projectPoms) {
        this.mavenCache = mavenCache;
        this.projectPoms = projectPoms;
    }

    public MavenMetadata downloadMetadata(String groupId, String artifactId,
                                          List<RawRepositories.Repository> repositories) {
        Timer.Sample sample = Timer.start();

        return Stream.concat(repositories.stream().distinct().map(this::normalizeRepository), Stream.of(SUPER_POM_REPOSITORY))
                .filter(Objects::nonNull)
                .map(repo -> {
                    Timer.Builder timer = Timer.builder("rewrite.maven.download")
                            .tag("group.id", groupId)
                            .tag("artifact.id", artifactId)
                            .tag("type", "metadata");

                    try {
                        CacheResult<MavenMetadata> result = mavenCache.computeMavenMetadata(URI.create(repo.getUrl()).toURL(), groupId, artifactId,
                                () -> forceDownloadMetadata(groupId, artifactId, null, repo));

                        sample.stop(addTagsByResult(timer, result).register(Metrics.globalRegistry));
                        return result.getData();
                    } catch (Exception e) {
                        sample.stop(timer.tags("outcome", "error", "exception", e.getClass().getName())
                                .register(Metrics.globalRegistry));
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .reduce(MavenMetadata.EMPTY, (m1, m2) -> {
                    if (m1 == MavenMetadata.EMPTY) {
                        if (m2 == MavenMetadata.EMPTY) {
                            return m1;
                        } else {
                            return m2;
                        }
                    } else if (m2 == MavenMetadata.EMPTY) {
                        return m1;
                    } else {
                        return new MavenMetadata(new MavenMetadata.Versioning(
                                Stream.concat(m1.getVersioning().getVersions().stream(),
                                        m2.getVersioning().getVersions().stream()).collect(toList()),
                                null
                        ));
                    }
                });
    }

    @Nullable
    private MavenMetadata forceDownloadMetadata(String groupId, String artifactId,
                                                @Nullable String version, RawRepositories.Repository repo) throws IOException {
        logger.debug("Resolving {}:{} metadata from {}", groupId, artifactId, repo.getUrl());

        String uri = repo.getUrl() + "/" +
                groupId.replace('.', '/') + '/' +
                artifactId + '/' +
                (version == null ? "" : version + '/') +
                "maven-metadata.xml";

        Request request = new Request.Builder().url(uri).get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                @SuppressWarnings("ConstantConditions") byte[] responseBody = response.body()
                        .bytes();

                return MavenMetadata.parse(responseBody);
            }
        }

        return null;
    }

    private Timer.Builder addTagsByResult(Timer.Builder timer, CacheResult<?> result) {
        switch (result.getState()) {
            case Cached:
                timer = timer.tags("outcome", "cached", "exception", "none");
                break;
            case Unavailable:
                timer = timer.tags("outcome", "unavailable", "exception", "none");
                break;
            case Updated:
                timer = timer.tags("outcome", "downloaded", "exception", "none");
                break;
        }
        return timer;
    }

    @Nullable
    public RawMaven download(String groupId,
                             String artifactId,
                             String version,
                             @Nullable String classifier,
                             @Nullable String relativePath,
                             @Nullable RawMaven containingPom,
                             List<RawRepositories.Repository> repositories) {

        String versionMaybeDatedSnapshot = findDatedSnapshotVersionIfNecessary(groupId, artifactId, version, repositories);
        if (versionMaybeDatedSnapshot == null) {
            return null;
        }

        Timer.Sample sample = Timer.start();

        if (containingPom == null || !containingPom.getSourcePath().contains("http")) {
            if (!StringUtils.isBlank(relativePath)) {
                return Optional.ofNullable(containingPom)
                        .map(pom -> {
                            Path relativePomPath = Paths.get(pom.getSourcePath())
                                    .getParent() // "relativeTo" the directory containing this pom
                                    .resolve(Paths.get(relativePath, "pom.xml"))
                                    .normalize();
                            return projectPoms.get(relativePomPath.toString());
                        })
                        .orElse(null);
            }

            for (RawMaven projectPom : projectPoms.values()) {
                if (groupId.equals(projectPom.getPom().getGroupId()) &&
                        artifactId.equals(projectPom.getPom().getArtifactId())) {
                    return projectPom;
                }
            }
        }

        return Stream.concat(repositories.stream().distinct().map(this::normalizeRepository), Stream.of(SUPER_POM_REPOSITORY))
                .filter(Objects::nonNull)
                .filter(repo -> repo.acceptsVersion(version))
                .map(repo -> {
                    Timer.Builder timer = Timer.builder("rewrite.maven.download")
                            .tag("group.id", groupId)
                            .tag("artifact.id", artifactId)
                            .tag("type", "pom");

                    try {
                        CacheResult<RawMaven> result = mavenCache.computeMaven(URI.create(repo.getUrl()).toURL(), groupId, artifactId,
                                versionMaybeDatedSnapshot, () -> {
                            String uri = repo.getUrl() + "/" +
                                    groupId.replace('.', '/') + '/' +
                                    artifactId + '/' +
                                    version + '/' +
                                    artifactId + '-' + versionMaybeDatedSnapshot + ".pom";

                            Request request = new Request.Builder().url(uri).get().build();
                            try (Response response = httpClient.newCall(request).execute()) {
                                if (response.isSuccessful() && response.body() != null) {
                                    @SuppressWarnings("ConstantConditions") byte[] responseBody = response.body()
                                            .bytes();

                                    return RawMaven.parse(
                                            new Parser.Input(URI.create(uri), () -> new ByteArrayInputStream(responseBody)),
                                            null,
                                            versionMaybeDatedSnapshot.equals(version) ? null : versionMaybeDatedSnapshot
                                    );
                                }
                            }

                            return null;
                        });

                        sample.stop(addTagsByResult(timer, result).register(Metrics.globalRegistry));
                        return result.getData();
                    } catch (Exception e) {
                        logger.debug("Failed to download {}:{}:{}:{}", groupId, artifactId, version, classifier, e);
                        sample.stop(timer.tags("outcome", "error", "exception", e.getClass().getName())
                                .register(Metrics.globalRegistry));
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    @Nullable
    private String findDatedSnapshotVersionIfNecessary(String groupId, String artifactId, String version, List<RawRepositories.Repository> repositories) {
        if (version.endsWith("-SNAPSHOT")) {
            MavenMetadata mavenMetadata = repositories.stream()
                    .distinct()
                    .map(this::normalizeRepository)
                    .filter(Objects::nonNull)
                    .filter(repo -> repo.acceptsVersion(version))
                    .map(repo -> {
                        try {
                            return forceDownloadMetadata(groupId, artifactId, version, repo);
                        } catch (IOException e) {
                            logger.debug("Failed to download snapshot metadata for {}:{}:{}", groupId, artifactId, version);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);

            if (mavenMetadata != null) {
                MavenMetadata.Snapshot snapshot = mavenMetadata.getVersioning().getSnapshot();
                if (snapshot == null) {
                    return null;
                }
                return version.replaceFirst("SNAPSHOT$", snapshot.getTimestamp() + "-" + snapshot.getBuildNumber());
            }
        }

        return version;
    }

    @Nullable
    private RawRepositories.Repository normalizeRepository(RawRepositories.Repository repository) {
        try {
            CacheResult<RawRepositories.Repository> result = mavenCache.computeRepository(repository, () -> {
                // FIXME add retry logic
                String url = repository.getUrl();
                Request request = new Request.Builder().url(url).head().build();
                try (Response response = httpClient.newCall(request).execute()) {
                    if (url.toLowerCase().contains("http://")) {
                        return normalizeRepository(
                                new RawRepositories.Repository(
                                        url.toLowerCase().replace("http://", "https://"),
                                        repository.getReleases(),
                                        repository.getSnapshots()
                                )
                        );
                    } else if (response.isSuccessful()) {
                        return new RawRepositories.Repository(
                                url,
                                repository.getReleases(),
                                repository.getSnapshots()
                        );
                    }

                    return null;
                }
            });

            return result.getData();
        } catch (Exception e) {
            return null;
        }
    }
}
