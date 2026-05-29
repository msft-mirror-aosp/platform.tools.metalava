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

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.MemberItem
import com.android.tools.metalava.model.PackageItem
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

        /**
         * Create a [SelectedApi] factory that will create [SelectedApi] instances suitable for a
         * [Codebase] created from [config].
         */
        fun sourceFactory(config: Codebase.Config): (SelectableItem) -> SelectedApi {
            // Get the ApiSurfaceSelector that is used by the AnnotationManager.
            val annotationManager = config.annotationManager
            val apiSurfaceSelector = annotationManager.apiSurfaceSelector

            // Create an updater that will be captured by the factory below and will be used by all
            // SelectedApi instances in the Codebase that uses tha factory.
            val selectedApiUpdater =
                SelectedApiUpdater(
                    apiSurfaceSelector,
                )
            return { item -> createFromSource(selectedApiUpdater, item) }
        }

        /** Create a [SelectedApi] for a source [item]. */
        fun createFromSource(
            selectedApiUpdater: SelectedApiUpdater,
            item: SelectableItem,
        ): SelectedApi =
            when (item) {
                is ClassItem -> ClassSelectedApi(selectedApiUpdater, item)
                is MemberItem -> MemberSelectedApi(selectedApiUpdater, item)
                is PackageItem -> PackageSelectedApi(selectedApiUpdater, item)
                else -> error("unknown selectable item: $item")
            }
    }
}

/** A simple [SelectedApi] that just stores [itemApiVariants] for [item]. */
private class SimpleSelectedApi(item: SelectableItem) : SelectedApi() {
    override var itemApiVariants = item.codebase.apiSurfaces.emptyVariantSet

    override fun initialize() {}
}

/** Base [SelectedApi] class for use on [SelectableItem]s created from sources. */
internal sealed class SourceSelectedApi<S : SelectableItem>(
    internal val selectedApiUpdater: SelectedApiUpdater,
    internal val item: S,
) : SelectedApi() {
    /**
     * The [ApiVariantSet] for the [item].
     *
     * This is initialized in [initialize] which must have been called and which must initialize
     * this before it is accessed.
     */
    override lateinit var itemApiVariants: ApiVariantSet

    override fun initialize() {
        // Update this.
        selectedApiUpdater.updateSelectedApi(this)
    }

    override fun toString(): String {
        val itemApiVariantsString =
            if (::itemApiVariants.isInitialized) itemApiVariants.toString() else "UNSET"
        return buildString {
            append("SourceSelectedApi(")

            append("item=")
            append(item)
            append(", itemApiVariants=")
            append(itemApiVariantsString)
            append(")")
        }
    }
}

/** Base [SelectedApi] class for source [PackageItem]s. */
private class PackageSelectedApi(
    selectedApiUpdater: SelectedApiUpdater,
    item: PackageItem,
) : SourceSelectedApi<PackageItem>(selectedApiUpdater, item)

/** Base [SelectedApi] class for source [ClassItem]s. */
private class ClassSelectedApi(
    selectedApiUpdater: SelectedApiUpdater,
    item: ClassItem,
) : SourceSelectedApi<ClassItem>(selectedApiUpdater, item)

/** Base [SelectedApi] class for source [MemberItem]s. */
private class MemberSelectedApi(
    selectedApiUpdater: SelectedApiUpdater,
    item: MemberItem,
) : SourceSelectedApi<MemberItem>(selectedApiUpdater, item)
