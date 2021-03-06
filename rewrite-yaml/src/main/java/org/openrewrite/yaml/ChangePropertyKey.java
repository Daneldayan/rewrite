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
package org.openrewrite.yaml;

import org.openrewrite.Formatting;
import org.openrewrite.marker.Markers;
import org.openrewrite.Validated;
import org.openrewrite.yaml.tree.Yaml;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;
import static org.openrewrite.Formatting.formatFirstPrefix;
import static org.openrewrite.Tree.randomId;
import static org.openrewrite.Validated.required;

/**
 * When nested YAML mappings are interpreted as dot
 * separated property names, e.g. as Spring Boot
 * interprets application.yml files.
 */
public class ChangePropertyKey extends YamlRefactorVisitor {
    private String property;
    private String toProperty;
    private boolean coalesce = true;

    public ChangePropertyKey() {
        setCursoringOn();
    }

    public void setProperty(String property) {
        this.property = property;
    }

    public void setToProperty(String toProperty) {
        this.toProperty = toProperty;
    }

    public void setCoalesce(boolean coalesce) {
        this.coalesce = coalesce;
    }

    @Override
    public Validated validate() {
        return required("property", property)
                .and(required("toProperty", toProperty));
    }

    @Override
    public boolean isIdempotent() {
        return false;
    }

    @Override
    public Yaml visitMappingEntry(Yaml.Mapping.Entry entry) {
        Yaml.Mapping.Entry e = refactor(entry, super::visitMappingEntry);

        Deque<Yaml.Mapping.Entry> propertyEntries = getCursor().getPathAsStream()
                .filter(Yaml.Mapping.Entry.class::isInstance)
                .map(Yaml.Mapping.Entry.class::cast)
                .collect(Collectors.toCollection(ArrayDeque::new));

        String property = stream(spliteratorUnknownSize(propertyEntries.descendingIterator(), 0), false)
                .map(e2 -> e2.getKey().getValue())
                .collect(Collectors.joining("."));

        String propertyToTest = this.toProperty;
        if (property.equals(this.property)) {
            Iterator<Yaml.Mapping.Entry> propertyEntriesLeftToRight = propertyEntries.descendingIterator();
            while (propertyEntriesLeftToRight.hasNext()) {
                Yaml.Mapping.Entry propertyEntry = propertyEntriesLeftToRight.next();
                String value = propertyEntry.getKey().getValue();

                if (!propertyToTest.startsWith(value)) {
                    andThen(new InsertSubproperty(
                            propertyEntry,
                            propertyToTest,
                            entry.getValue()
                    ));
                    andThen(new DeleteProperty(entry));
                    if (coalesce) {
                        maybeCoalesceProperties();
                    }
                    break;
                }

                propertyToTest = propertyToTest.substring(value.length() + 1);
            }
        }

        return e;
    }

    private static class InsertSubproperty extends YamlRefactorVisitor {
        private final Yaml.Mapping.Entry scope;
        private final String subproperty;
        private final Yaml.Block value;

        private InsertSubproperty(Yaml.Mapping.Entry scope, String subproperty, Yaml.Block value) {
            this.scope = scope;
            this.subproperty = subproperty;
            this.value = value;
        }

        @Override
        public Yaml visitMapping(Yaml.Mapping mapping) {
            Yaml.Mapping m = refactor(mapping, super::visitMapping);

            if (m.getEntries().contains(scope)) {
                Formatting newEntryFormatting = scope.getFormatting();
                if (newEntryFormatting.getPrefix().isEmpty()) {
                    newEntryFormatting = newEntryFormatting.withPrefix("\n");
                }

                m = m.withEntries(Stream.concat(
                        m.getEntries().stream(),
                        Stream.of(
                                new Yaml.Mapping.Entry(randomId(),
                                        new Yaml.Scalar(randomId(), Yaml.Scalar.Style.PLAIN, subproperty,
                                                Formatting.EMPTY, Markers.EMPTY),
                                        value.copyPaste(),
                                        newEntryFormatting,
                                        Markers.EMPTY
                                )
                        )
                ).collect(toList()));
            }

            return m;
        }
    }

    private static class DeleteProperty extends YamlRefactorVisitor {
        private final Yaml.Mapping.Entry scope;

        private DeleteProperty(Yaml.Mapping.Entry scope) {
            this.scope = scope;
            setCursoringOn();
        }

        @Override
        public Yaml visitMapping(Yaml.Mapping mapping) {
            Yaml.Mapping m = refactor(mapping, super::visitMapping);

            boolean changed = false;
            List<Yaml.Mapping.Entry> entries = new ArrayList<>();
            for (Yaml.Mapping.Entry entry : m.getEntries()) {
                if (entry == scope || (entry.getValue() instanceof Yaml.Mapping && ((Yaml.Mapping) entry.getValue()).getEntries().isEmpty())) {
                    changed = true;
                } else {
                    entries.add(entry);
                }
            }

            if (changed) {
                m = m.withEntries(entries);

                if (getCursor().getParentOrThrow().getTree() instanceof Yaml.Document) {
                    Yaml.Document document = getCursor().getParentOrThrow().getTree();
                    if (!document.isExplicit()) {
                        m = m.withEntries(formatFirstPrefix(m.getEntries(), ""));
                    }
                }
            }

            return m;
        }
    }
}
