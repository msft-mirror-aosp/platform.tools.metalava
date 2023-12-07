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

package com.android.tools.metalava.model.turbine

import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.TypeModifiers
import com.android.tools.metalava.model.TypeNullability

/** Modifiers for a [TurbineTypeItem]. */
internal class TurbineTypeModifiers(initialAnnotations: List<AnnotationItem>) : TypeModifiers {
    private val annotations = initialAnnotations.toMutableList()

    // Find if there is a nullness annotation on the type, default to platform nullness if not.
    private var nullability =
        annotations
            .firstOrNull { it.isNullnessAnnotation() }
            ?.let { TypeNullability.ofAnnotation(it) }
            ?: TypeNullability.PLATFORM

    override fun annotations(): List<AnnotationItem> = annotations

    override fun addAnnotation(annotation: AnnotationItem) {
        annotations.add(annotation)
    }

    override fun removeAnnotation(annotation: AnnotationItem) {
        annotations.remove(annotation)
    }

    override fun nullability(): TypeNullability {
        return nullability
    }

    override fun setNullability(newNullability: TypeNullability) {
        nullability = newNullability
    }
}
