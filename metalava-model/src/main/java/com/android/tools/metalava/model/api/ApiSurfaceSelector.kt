/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.tools.metalava.model.api

import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.annotation.AnnotationFilter

/** Helps determine to which api surface a [SelectableItem] belongs. */
class ApiSurfaceSelector(
    val showAnnotations: AnnotationFilter = AnnotationFilter.emptyFilter(),
    val showSingleAnnotations: AnnotationFilter = AnnotationFilter.emptyFilter(),
    val showForStubPurposesAnnotations: AnnotationFilter = AnnotationFilter.emptyFilter(),
    val hideAnnotations: AnnotationFilter = AnnotationFilter.emptyFilter(),
) {
    /** True if this has annotations that include a [SelectableItem] in the stubs only. */
    val hasAnyShowForStubPurposesAnnotations = showForStubPurposesAnnotations.isNotEmpty()

    /** True if this has any annotations that can hide a [SelectableItem] from the public API. */
    val hasAnyHideAnnotations = hideAnnotations.isNotEmpty()

    /** The qualified names of all annotations that can affect API surface selection. */
    val annotationNames = buildSet {
        // The list of all filters.
        val filters =
            listOf(
                showAnnotations,
                showSingleAnnotations,
                showForStubPurposesAnnotations,
                hideAnnotations,
            )

        // Iterate over all the annotation names matched by all the filters currently used by
        // [LazyAnnotationInfo] and associate them with a [KeyFactory] that will use the
        // complete source representation of the annotation as the key. This is needed because
        // filters can match on attribute values as well as the name.
        for (filter in filters) {
            addAll(filter.getIncludedAnnotationNames())
        }
    }
}
