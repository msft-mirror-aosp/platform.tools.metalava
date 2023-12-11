/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.tools.metalava.model.psi

import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.TypeModifiers
import com.android.tools.metalava.model.TypeNullability
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import com.intellij.psi.PsiWildcardType

/** Modifiers for a [PsiTypeItem]. */
internal class PsiTypeModifiers(
    private val annotations: MutableList<PsiAnnotationItem>,
    private var nullability: TypeNullability
) : TypeModifiers {
    override fun annotations(): List<AnnotationItem> = annotations

    override fun addAnnotation(annotation: AnnotationItem) {
        annotations.add(annotation as PsiAnnotationItem)
    }

    override fun removeAnnotation(annotation: AnnotationItem) {
        annotations.remove(annotation)
    }

    override fun nullability(): TypeNullability = nullability

    override fun setNullability(newNullability: TypeNullability) {
        nullability = newNullability
    }

    fun duplicate() = PsiTypeModifiers(annotations.toMutableList(), nullability)

    companion object {
        /** Creates modifiers in the given [codebase] based on the annotations of the [type]. */
        fun create(codebase: PsiBasedCodebase, type: PsiType): PsiTypeModifiers {
            val annotations = type.annotations.map { PsiAnnotationItem.create(codebase, it) }
            // Some types have defined nullness, otherwise look at the annotations and default to
            // platform nullness.
            val nullability =
                when (type) {
                    is PsiPrimitiveType -> TypeNullability.NONNULL
                    is PsiWildcardType -> TypeNullability.UNDEFINED
                    else ->
                        annotations
                            .firstOrNull { it.isNullnessAnnotation() }
                            ?.let { TypeNullability.ofAnnotation(it) }
                            ?: TypeNullability.PLATFORM
                }
            return PsiTypeModifiers(annotations.toMutableList(), nullability)
        }
    }
}
