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
package org.openrewrite.xml;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import org.openrewrite.Formatting;
import org.openrewrite.marker.Markers;
import org.openrewrite.xml.tree.Xml;

import static java.util.Collections.singletonList;
import static org.openrewrite.Tree.randomId;

public class ChangeTagValue {
    private ChangeTagValue() {
    }

    public static class Scoped extends XmlRefactorVisitor {
        private final Xml.Tag scope;
        private final String value;

        public Scoped(Xml.Tag scope, String value) {
            this.scope = scope;
            this.value = value;
            setCursoringOn();
        }

        @Override
        public Iterable<Tag> getTags() {
            return Tags.of("value", value);
        }

        @Override
        public Xml visitTag(Xml.Tag tag) {
            Xml.Tag t = refactor(tag, super::visitTag);

            if (scope.isScope(tag)) {
                Formatting formatting = Formatting.EMPTY;
                if (t.getContent() != null && t.getContent().size() == 1 && t.getContent().get(0) instanceof Xml.CharData) {
                    Xml.CharData existingValue = (Xml.CharData) t.getContent().get(0);

                    if (existingValue.getText().equals(value)) {
                        return t;
                    }

                    // if the previous content was also character data, preserve its formatting
                    formatting = existingValue.getFormatting();
                }
                t = t.withContent(singletonList(new Xml.CharData(randomId(), false, value, formatting, Markers.EMPTY)));
            }

            return t;
        }
    }
}
