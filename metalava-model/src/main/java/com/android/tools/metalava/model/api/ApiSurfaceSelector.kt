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
    val showUnannotated: Boolean = true,
    showAnnotationValues: List<String> = emptyList(),
    showSingleAnnotationValues: List<String> = emptyList(),
    showForStubPurposesAnnotationValues: List<String> = emptyList(),
    hideAnnotationValues: List<String> = emptyList(),
) {
    /** True if this has annotations that include a [SelectableItem] in the stubs only. */
    val hasAnyShowForStubPurposesAnnotations = showForStubPurposesAnnotationValues.isNotEmpty()

    /** True if this has any annotations that can hide a [SelectableItem] from the public API. */
    val hasAnyHideAnnotations = hideAnnotationValues.isNotEmpty()

    /** Create an [AnnotationMatcher] from the [List] of annotation sources. */
    private fun List<String>.addRules(
        mutableList: MutableList<AnnotationMatcher.Rule<Showability>>,
        showability: Showability
    ) = mapTo(mutableList) { AnnotationMatcher.Rule(it, showability) }

    /**
     * Associates an annotation pattern, e.g. `--show-annotation android.annotation.TestApi` with
     * its [Showability].
     */
    internal val matcher =
        AnnotationMatcher.createFromRules(
            buildList {
                showForStubPurposesAnnotationValues.addRules(this, SHOW_FOR_STUBS)
                showSingleAnnotationValues.addRules(this, SHOW_SINGLE)
                showAnnotationValues.addRules(this, SHOW)
                // Hide are at the end as these are processed in order and show has priority.
                hideAnnotationValues.addRules(this, HIDE)
            }
        )

    /** The qualified names of all annotations that can affect API surface selection. */
    val annotationNames = matcher.annotationNames

    /**
     * Compute the [Showability] for [annotationItem], returns `null` if [annotationItem] does not
     * affect API selection.
     */
    fun showability(annotationItem: AnnotationItem) = matcher.matchResult(annotationItem)

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
