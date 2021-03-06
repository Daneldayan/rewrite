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
package org.openrewrite.properties.tree

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.openrewrite.marker.Markers
import org.openrewrite.TreeSerializer
import org.openrewrite.git.Git
import org.openrewrite.properties.PropertiesParser

class PropertiesFileSerializerTest {

    @Test
    fun roundTripSerialization() {
        val serializer = TreeSerializer<Properties.File>()
        val a = PropertiesParser().parse("key=value")[0]
                .withMarkers(Markers(listOf(Git().apply {
                    headCommitId = "123"
                })))

        val aBytes = serializer.write(a)
        val aDeser = serializer.read(aBytes)

        assertEquals(a, aDeser)
    }

    @Test
    fun roundTripSerializationList() {
        val serializer = TreeSerializer<Properties.File>()
        val p1 = PropertiesParser().parse("key=value")[0]
                .withMarkers(Markers(listOf(Git().apply {
                    headCommitId = "123"
                })))
        val p2 = PropertiesParser().parse("key=value")[0]
                .withMarkers(Markers(listOf(Git().apply {
                    headCommitId = "123"
                })))

        val serialized = serializer.write(listOf(p1, p2))
        val deserialized = serializer.readList(serialized)

        assertEquals(p1, deserialized[0])
    }
}
