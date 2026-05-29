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
import com.android.tools.metalava.model.api.surface.ApiVariantSet
import com.android.tools.metalava.model.item.DefaultSelectableItem

/** Provides access to the [ApiVariantSet] to which a specific [SelectableItem] belongs. */
sealed class SelectedApi {
    /** The [ApiVariantSet] for the [SelectableItem]. */
    abstract var itemApiVariants: ApiVariantSet

    /**
     * Initialize this instance.
     *
     * This is called after this has been created and assigned to
     * [DefaultSelectableItem.selectedApi].
     */
    internal abstract fun initialize()

    companion object {
        /**
         * Create a simple [SelectedApi] that simply stores an [itemApiVariants] that is populated
         * based off information outside the [SelectableItem], e.g. signature files.
         */
        fun createSimple(item: SelectableItem): SelectedApi = SimpleSelectedApi(item)
    }
}

/** A simple [SelectedApi] that just stores [itemApiVariants] for [item]. */
private class SimpleSelectedApi(item: SelectableItem) : SelectedApi() {
    override var itemApiVariants = item.codebase.apiSurfaces.emptyVariantSet

    override fun initialize() {}
}
