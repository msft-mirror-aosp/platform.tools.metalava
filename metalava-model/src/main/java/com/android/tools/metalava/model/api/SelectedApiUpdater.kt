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
    }

    /**
     * Update [selectedApi] with information about [ApiVariant]s to which the
     * [SourceSelectedApi.item] belongs.
     */
    internal fun updateSelectedApi(
        selectedApi: SourceSelectedApi<*>,
    ) {
        // Get the item that owns selectedApi.
        val item = selectedApi.item

        // Compute the variant set for the item.
        val itemApiVariants =
            if (item.hasHideDocTag) {
                // Mark the selectedApi as being hidden.
                selectedApi.markAsHidden()

                // Return immediately to avoid falling through.
                return
            } else {
                // It belongs to the default set of API surfaces.
                defaultVariantSet
            }

        // Store the variant set in selectedApi.
        selectedApi.itemApiVariants = itemApiVariants
    }
}
