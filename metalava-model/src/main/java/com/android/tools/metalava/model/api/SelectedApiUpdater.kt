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
import com.android.tools.metalava.model.api.surface.ApiSurfaces
import com.android.tools.metalava.model.api.surface.ApiVariant
import com.android.tools.metalava.model.api.surface.ApiVariantSet
import com.android.tools.metalava.model.api.surface.BaseApiVariantSet

/** Provides support for updating [SourceSelectedApi] instances. */
class SelectedApiUpdater(
    apiSurfaceSelector: ApiSurfaceSelector,
) {
    /** The [ApiSurfaces] with which this will associate [SelectableItem]s */
    private val apiSurfaces = apiSurfaceSelector.apiSurfaces

    /** The set of empty [ApiVariant]s for [apiSurfaces]. */
    internal val emptyVariantSet = apiSurfaces.emptyVariantSet

    /**
     * The default set of variants that are used on unannotated items.
     *
     * If unannotated items are not included in any surface then it is empty, otherwise it is the
     * default variants for the unannotated surface.
     */
    internal val defaultVariantSet =
        apiSurfaceSelector.unannotatedApiSurface?.defaultVariantSet ?: emptyVariantSet

    /** Check whether this [SelectableItem] has an `@hide` doc tag. */
    private val SelectableItem.hasHideDocTag: Boolean
        get() = documentation?.isHidden == true

    /** Mark this [SourceSelectedApi] as being hidden. */
    private fun SourceSelectedApi<*>.markAsHidden() {
        // A hidden item does not belong to any API surfaces.
        itemApiVariants = emptyVariantSet
        inheritableApiVariants = emptyVariantSet
    }

    /**
     * Union this option [BaseApiVariantSet] with [other] [ApiVariantSet].
     *
     * If this is `null` then this just returns [other], otherwise it calls
     * [BaseApiVariantSet.unionWith] on [other].
     */
    private fun BaseApiVariantSet?.unionWith(other: ApiVariantSet) = this?.unionWith(other) ?: other

    /**
     * Update [selectedApi] with information about [ApiVariant]s to which the
     * [SourceSelectedApi.item] belongs.
     */
    internal fun updateSelectedApi(
        selectedApi: SourceSelectedApi<*>,
        parent: SourceSelectedApi<*>,
    ) {
        // Get the item that owns selectedApi.
        val item = selectedApi.item

        val enclosingApiVariants = parent.inheritableApiVariants

        // Keep track of the ApiVariants to which the context item belong. Is `null` to avoid
        // creating a MutableApiVariantSet when most items are unannotated.
        var itemApiVariants: BaseApiVariantSet? = null

        // Keep track of the ApiVariants which the context item's enclosed items will inherit.
        var inheritableApiVariants: BaseApiVariantSet? = null

        // Indicates whether a hide annotation has been seen. Hide annotations are superseded by
        // show annotations so any processing of a hide annotation is deferred until after all
        // annotations have been checked. Hide annotations are always recursive so this just tracks
        // whether one has been seen.
        var hide = false

        // Iterate over the annotations, checking to see if any match the surface rules.
        val annotations = item.modifiers.annotations()
        for (annotationItem in annotations) {
            // Ignore any annotation that does not match.
            annotationItem.surfaceData?.let { surfaceData ->
                if (surfaceData.show) {
                    val resultSurface = surfaceData.surface

                    // It is a show annotation so add the context surfaces variants.
                    val resultVariants = resultSurface.defaultVariantSet

                    // Add the surface variants to the variants for the context item.
                    itemApiVariants = itemApiVariants.unionWith(resultVariants)

                    // If the rule is recursive then add the surface variants to those that are
                    // inherited by enclosed items.
                    if (surfaceData.recursive) {
                        inheritableApiVariants = inheritableApiVariants.unionWith(resultVariants)
                    }
                } else {
                    // A hide annotation was seen.
                    hide = true
                }
            }
        }

        // Check to see if any show rules matched; if they had then they would have set
        // itemApiVariants to non-null.
        if (itemApiVariants == null) {
            // No show rules matched. Check to see if the context item should be hidden.

            // If no hide annotations were found then check for @hide doc tag.
            if (!hide) {
                hide = item.hasHideDocTag
            }

            if (hide) {
                // Mark the selectedApi as being hidden.
                selectedApi.markAsHidden()

                // Return immediately to avoid falling through.
                return
            }

            // The context item did not specify the API surfaces to which it belongs so use the
            // enclosing item's API variants for the context item and its enclosed items.
            itemApiVariants =
                if (enclosingApiVariants.isNotEmpty()) {
                    enclosingApiVariants
                } else {
                    emptyVariantSet
                }
            inheritableApiVariants = enclosingApiVariants
        }

        // Store the variant set in selectedApi.
        selectedApi.itemApiVariants = itemApiVariants.toImmutable()
        selectedApi.inheritableApiVariants =
            inheritableApiVariants?.toImmutable() ?: emptyVariantSet
    }
}
