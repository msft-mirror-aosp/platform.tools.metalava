/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.metalava.model.type

import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeModifiers
import com.android.tools.metalava.model.TypeNullability

/** Modifiers for a [TypeItem]. */
class DefaultTypeModifiers(
    override val annotations: List<AnnotationItem>,
    override val nullability: TypeNullability,
) : TypeModifiers {

    override fun substitute(
        nullability: TypeNullability,
        annotations: List<AnnotationItem>,
    ): TypeModifiers =
        if (nullability != this.nullability || annotations != this.annotations)
            DefaultTypeModifiers(annotations, nullability)
        else this

    override fun withoutAnnotations() = emptyModifiers(nullability)

    companion object {
        /** Get an empty (no annotations) [TypeModifiers] for [typeNullability]. */
        private fun emptyModifiers(typeNullability: TypeNullability) =
            emptyModifiersByNullability[typeNullability.ordinal]

        /**
         * A list of empty [DefaultTypeModifiers] instances, one for each [TypeNullability] indexed
         * by [TypeNullability.ordinal].
         */
        private val emptyModifiersByNullability =
            buildList<TypeModifiers> {
                for (typeNullability in TypeNullability.entries) {
                    add(DefaultTypeModifiers(emptyList(), typeNullability))
                }
            }

        /** A set of empty, non-null [TypeModifiers] for sharing. */
        val emptyNonNullModifiers = emptyModifiers(TypeNullability.NONNULL)

        /** A set of empty, nullable [TypeModifiers] for sharing. */
        val emptyNullableModifiers = emptyModifiers(TypeNullability.NULLABLE)

        /** A set of empty, platform [TypeModifiers] for sharing. */
        val emptyPlatformModifiers = emptyModifiers(TypeNullability.PLATFORM)

        /** A set of empty, undefined [TypeModifiers] for sharing. */
        val emptyUndefinedModifiers = emptyModifiers(TypeNullability.UNDEFINED)

        /** Create a [DefaultTypeModifiers]. */
        fun create(
            annotations: List<AnnotationItem>,
            nullability: TypeNullability,
        ): TypeModifiers =
            // If the annotations are empty then use one of the predefined instances.
            if (annotations.isEmpty()) {
                emptyModifiers(nullability)
            } else {
                DefaultTypeModifiers(annotations, nullability)
            }
    }
}
