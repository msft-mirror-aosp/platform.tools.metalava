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

import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.ShowOrHide
import com.android.tools.metalava.model.Showability

/** Helps determine to which api surface a [SelectableItem] belongs. */
class ApiSurfaceSelector(
    showAnnotationValues: List<String> = emptyList(),
    showSingleAnnotationValues: List<String> = emptyList(),
    showForStubPurposesAnnotationValues: List<String> = emptyList(),
    hideAnnotationValues: List<String> = emptyList(),
) {
    /** True if this has annotations that include a [SelectableItem] in the stubs only. */
    val hasAnyShowForStubPurposesAnnotations = showForStubPurposesAnnotationValues.isNotEmpty()

    /** True if this has any annotations that can hide a [SelectableItem] from the public API. */
    val hasAnyHideAnnotations = hideAnnotationValues.isNotEmpty()

    private val showAnnotations = AnnotationMatcher.create(showAnnotationValues)
    private val showSingleAnnotations = AnnotationMatcher.create(showSingleAnnotationValues)
    private val showForStubPurposesAnnotations =
        AnnotationMatcher.create(showForStubPurposesAnnotationValues)
    private val hideAnnotations = AnnotationMatcher.create(hideAnnotationValues)

    /** The qualified names of all annotations that can affect API surface selection. */
    val annotationNames = buildSet {
        // The list of all matchers.
        val matchers =
            listOf(
                showAnnotations,
                showSingleAnnotations,
                showForStubPurposesAnnotations,
                hideAnnotations,
            )

        // Iterate over all the annotation names matched by all the matchers currently used by
        // [LazyAnnotationInfo] and associate them with a [KeyFactory] that will use the
        // complete source representation of the annotation as the key. This is needed because
        // matchers can match on attribute values as well as the name.
        for (matcher in matchers) {
            addAll(matcher.annotationNames)
        }
    }

    /**
     * Compute the [Showability] for [annotationItem], returns `null` if [annotationItem] does not
     * affect API selection.
     */
    fun showability(annotationItem: AnnotationItem): Showability? {
        // The showAnnotations matcher includes all the annotation patterns that are matched by
        // the first two matchers plus 0 or more additional patterns. Excluding the patterns that
        // are purposely duplicated in showAnnotations the matchers should not overlap, i.e. an
        // AnnotationItem should not be matched by multiple matchers. However, the matchers could
        // use the same annotation class (with different attributes). e.g. showAnnotations could
        // match `@SystemApi(client=MODULE_LIBRARIES)` and showForStubPurposesAnnotations could
        // match `@SystemApi(client=PRIVILEGED_APPS)`.
        //
        // Compare from most likely to match to least likely to match.
        return when {
            showAnnotations.matches(annotationItem) -> SHOW
            showForStubPurposesAnnotations.matches(annotationItem) -> SHOW_FOR_STUBS
            showSingleAnnotations.matches(annotationItem) -> SHOW_SINGLE
            hideAnnotations.matches(annotationItem) -> HIDE
            else -> null
        }
    }

    companion object {
        /**
         * The annotation will cause the annotated item (and any enclosed items unless overridden by
         * a closer annotation) to be shown.
         */
        private val SHOW =
            Showability(
                show = ShowOrHide.SHOW,
                recursive = ShowOrHide.SHOW,
                forStubsOnly = ShowOrHide.NO_EFFECT,
            )

        /**
         * The annotation will cause the annotated item (and any enclosed items unless overridden by
         * a closer annotation) to be shown in the stubs only.
         */
        private val SHOW_FOR_STUBS =
            Showability(
                show = ShowOrHide.NO_EFFECT,
                recursive = ShowOrHide.NO_EFFECT,
                forStubsOnly = ShowOrHide.SHOW,
            )

        /** The annotation will cause the annotated item (but not enclosed items) to be shown. */
        private val SHOW_SINGLE =
            Showability(
                show = ShowOrHide.SHOW,
                recursive = ShowOrHide.NO_EFFECT,
                forStubsOnly = ShowOrHide.NO_EFFECT,
            )

        /**
         * The annotation will cause the annotated item (and any enclosed items unless overridden by
         * a closer annotation) to not be shown.
         */
        private val HIDE =
            Showability(
                show = ShowOrHide.HIDE,
                recursive = ShowOrHide.HIDE,
                forStubsOnly = ShowOrHide.NO_EFFECT,
            )
    }
}
