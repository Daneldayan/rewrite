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
package org.openrewrite.java.tree

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.openrewrite.Formatting
import org.openrewrite.marker.Markers
import java.util.*

class ChangeVisibilityModifierTest {
    @Test
    fun publicToPrivate() {
        val beforeMods = listOf(
                J.Modifier.Public(UUID.randomUUID(), Formatting.EMPTY, Markers.EMPTY),
                J.Modifier.Static(UUID.randomUUID(), Formatting.EMPTY, Markers.EMPTY)
        )

        val expectedMods = listOf(
                J.Modifier.Private(UUID.randomUUID(), Formatting.EMPTY, Markers.EMPTY),
                J.Modifier.Static(UUID.randomUUID(), Formatting.EMPTY, Markers.EMPTY)
        )

        val actualMods = J.Modifier.withVisibility(beforeMods, "private")

        assertThat(actualMods.map { it.javaClass })
                .isEqualTo(expectedMods.map { it.javaClass })
    }

    @Test
    fun protectedToPrivate() {
        val beforeMods = listOf(
                J.Modifier.Protected(UUID.randomUUID(), Formatting.EMPTY, Markers.EMPTY),
                J.Modifier.Static(UUID.randomUUID(), Formatting.EMPTY, Markers.EMPTY)
        )

        val expectedMods = listOf(
                J.Modifier.Private(UUID.randomUUID(), Formatting.EMPTY, Markers.EMPTY),
                J.Modifier.Static(UUID.randomUUID(), Formatting.EMPTY, Markers.EMPTY)
        )

        val actualMods = J.Modifier.withVisibility(beforeMods, "private")

        assertThat(actualMods.map { it.javaClass })
                .isEqualTo(expectedMods.map { it.javaClass })
    }

    @Test
    fun packagePrivateToPrivate() {
        val beforeMods = listOf(
                J.Modifier.Static(UUID.randomUUID(), Formatting.EMPTY, Markers.EMPTY)
        )

        val expectedMods = listOf(
                J.Modifier.Private(UUID.randomUUID(), Formatting.EMPTY, Markers.EMPTY),
                J.Modifier.Static(UUID.randomUUID(), Formatting.EMPTY, Markers.EMPTY)
        )

        val actualMods = J.Modifier.withVisibility(beforeMods, "private")

        assertThat(actualMods.map { it.javaClass })
                .isEqualTo(expectedMods.map { it.javaClass })
    }

    @Test
    fun publicToPackagePrivate() {
        val beforeMods = listOf(
                J.Modifier.Public(UUID.randomUUID(), Formatting.EMPTY, Markers.EMPTY),
                J.Modifier.Static(UUID.randomUUID(), Formatting.EMPTY, Markers.EMPTY)
        )

        val expectedMods = listOf(
                J.Modifier.Static(UUID.randomUUID(), Formatting.EMPTY, Markers.EMPTY)
        )

        val actualMods = J.Modifier.withVisibility(beforeMods, "package")

        assertThat(actualMods.map { it.javaClass })
                .isEqualTo(expectedMods.map { it.javaClass })
    }
}
