/*
 * Copyright 2020 the original authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.rewrite.tree;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.netflix.rewrite.internal.StringUtils;
import com.netflix.rewrite.internal.lang.Nullable;
import com.netflix.rewrite.tree.visitor.AstVisitor;
import com.netflix.rewrite.tree.visitor.PrintVisitor;
import com.netflix.rewrite.tree.visitor.RetrieveCursorVisitor;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static java.util.Collections.emptyList;

@JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS, property = "@c")
public interface Tree {
    Formatting getFormatting();

    /**
     * An id that can be used to identify a particular AST element, even after transformations have taken place on it
     */
    UUID getId();

    /**
     * An overload that allows us to create a copy of any Tree element, optionally
     * changing formatting
     */
    <T extends Tree> T withFormatting(Formatting fmt);

    default <R> R accept(AstVisitor<R> v)  {
        return v.defaultTo(null);
    }

    default String printTrimmed() {
        return StringUtils.trimIndent(print().stripLeading());
    }

    default String print() {
        return new PrintVisitor().visit(this);
    }

    @Nullable
    default Cursor cursor(Tree t) {
        return new RetrieveCursorVisitor(t.getId()).visit(this);
    }
}