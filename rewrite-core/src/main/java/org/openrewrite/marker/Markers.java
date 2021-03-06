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
package org.openrewrite.marker;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import org.openrewrite.Incubating;
import org.openrewrite.SourceVisitor;
import org.openrewrite.Tree;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

@Incubating(since = "7.0.0")
@JsonAutoDetect(
        fieldVisibility = Visibility.ANY,
        getterVisibility = Visibility.NONE,
        setterVisibility = Visibility.NONE
)
public class Markers {
    public static Markers EMPTY = new Markers(emptyList()) {
        @Override
        public String toString() {
            return "markers{EMPTY}";
        }
    };

    private final Collection<Marker> markers;

    @JsonCreator
    public Markers(@JsonProperty("markers") Collection<Marker> markers) {
        this.markers = markers;
    }

    /**
     * {@link SourceVisitor} may respond to a marker to determine whether to act on
     * a source file or not.
     *
     * @return A marker collection containing any additional context about the containing {@link Tree} element.
     */
    public Collection<Marker> entrySet() {
        return markers;
    }

    /**
     * Overrides marker of the same type if it exists, otherwise adds
     * a new marker element to the collection.
     *
     * @param marker The data to add or update.
     * @return A new {@link Markers} with an added marker.
     */
    public Markers add(Marker marker) {
        List<Marker> updatedmarker = new ArrayList<>(markers.size() + 1);
        updatedmarker.add(marker);
        return new Markers(updatedmarker);
    }

    /**
     * Add a new marker or update some existing marker with the same type (equality not assignability).
     * @param marker The marker to add.
     * @return A new {@link Markers} with an added or updated marker.
     */
    public Markers addOrUpdate(Marker marker) {
        List<Marker> updatedmarker = new ArrayList<>(markers.size() + 1);
        boolean updated = false;
        for (Marker m : this.markers) {
            if (m.getClass().equals(marker.getClass())) {
                updatedmarker.add(marker);
                updated = true;
            } else {
                updatedmarker.add(m);
            }
        }
        if (!updated) {
            updatedmarker.add(marker);
        }
        return new Markers(updatedmarker);
    }

    public <M extends Marker> Collection<M> findAll(Class<M> markerType) {
        return markers.stream()
                .filter(markerType::isInstance)
                .map(markerType::cast)
                .collect(toList());
    }

    public <M extends Marker> Optional<M> findFirst(Class<M> markerType) {
        return markers.stream()
                .filter(markerType::isInstance)
                .map(markerType::cast)
                .findFirst();
    }
}
