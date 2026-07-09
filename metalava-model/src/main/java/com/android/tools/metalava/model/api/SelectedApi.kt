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

    /** Checks to see if the associated [SelectableItem] contains any doconly annotations. */
    open fun hasDocOnlyAnnotation(): Boolean = false

    /**
     * Initialize this instance.
     *
     * This is called after this has been created and assigned to
     * [DefaultSelectableItem.selectedApi].
     */
    internal abstract fun initialize()

    companion object {
        /**
         * Return a [SelectedApi] factory that will create [SelectedApi] instances suitable for
         * being populated based off information outside the [SelectableItem], e.g. signature files.
         */
        val SIMPLE_FACTORY: (SelectableItem) -> SelectedApi = { SimpleSelectedApi() }

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

/** A simple [SelectedApi] that just stores [itemApiVariants]. */
private class SimpleSelectedApi : SelectedApi() {
    override var itemApiVariants = ApiVariantSet.EMPTY

    override fun initialize() {}
}

/** Base [SelectedApi] class for use on [SelectableItem]s created from sources. */
internal sealed class SourceSelectedApi<S : SelectableItem>(
    internal val selectedApiUpdater: SelectedApiUpdater,
    internal val item: S,
) : SelectedApi() {
    /**
     * The parent [SourceSelectedApi], used for propagating information up to the parent
     * [SourceSelectedApi].
     *
     * e.g. A package belongs in the API surfaces of all its top level child classes. That requires
     * the child classes propagate information about the API surfaces to which they belong up to the
     * parent package.
     *
     * This is the [SelectableItem.selectedApi] for [item]'s [SelectableItem.parent]. If the latter
     * is `null`, i.e. [item] is the root package then this will refer to [item]. That avoids having
     * to check this for `null` every time it is used at the expense of have a cycle at the top.
     *
     * The cycle should not be an issue as while packages are hierarchical when it comes to hiding
     * them they are otherwise flat. That means a [PackageSelectedApi] will never try and propagate
     * information to its parent. So, the root [PackageSelectedApi] will never use its [parent].
     *
     * Initialized in [initialize] which is called after creation but before the object is stored
     * anywhere so it is impossible for this to be accessed before [initialize] has been called so
     * there is no need to check is this has been initialized before using it.
     */
    protected lateinit var parent: SourceSelectedApi<*>

    /**
     * Indicates whether the associated [SelectableItem] has a doc only annotation.
     *
     * Initialized by [SelectedApiUpdater.updateSelectedApi] called from [updateFromSelectableItem].
     */
    var docOnly: Boolean = false
        internal set

    /**
     * The [ApiVariantSet] for the [item].
     *
     * This is initialized in [initialize] which must have been called and which must initialize
     * this before it is accessed.
     */
    override var itemApiVariants = ApiVariantSet.EMPTY

    /**
     * The [ApiVariantSet] that will be inherited by [SelectableItem]s enclosed within [item].
     *
     * This is initialized in [initialize] which must have been called and which must initialize
     * this before it is accessed.
     *
     * This is tracked separately to [itemApiVariants] for a couple of reasons:
     * * Non-recursive show annotations can include an item in a surface without automatically
     *   including enclosed items.
     * * [itemApiVariants] can be modified by enclosed items, e.g. a package's [itemApiVariants] is
     *   the aggregate of all its classes.
     */
    var inheritableApiVariants = ApiVariantSet.EMPTY

    /** Checks to see if the associated [SelectableItem] contains any doconly annotations. */
    override fun hasDocOnlyAnnotation() = docOnly

    final override fun initialize() {
        // Initialize the parent first.
        parent =
            item.parent().let { parentItem ->
                if (parentItem == null) {
                    // Initialize inheritableApiVariants for the root package to the default variant
                    // set so that any unannotated items will inherit the correct surfaces. This is
                    // done here as otherwise this will be accessed in [updateFromSelectableItem]
                    // before it is initialized.
                    inheritableApiVariants = selectedApiUpdater.defaultVariantSet

                    // Use this as its own parent to avoid having to make parent nullable.
                    this
                } else {
                    parentItem.selectedApi as? SourceSelectedApi<*>
                        // This error should never happen as all items in a codebase use the same
                        // SelectedApi factory.
                        ?: error("Incompatible selectable items for $item and $parentItem")
                }
            }

        // Perform any item specific initialization.
        itemSpecificInitialization()
    }

    /** Update this from information in [item]. */
    fun updateFromSelectableItem() {
        selectedApiUpdater.updateSelectedApi(this, parent)
    }

    /**
     * Perform any item specific initialization.
     *
     * This is called after [parent] has been initialized, and it is the responsibility of this to
     * call [updateFromSelectableItem] to update the state before accessing the
     * [SelectableItem.selectedApi] of any enclosed items.
     */
    abstract fun itemSpecificInitialization()

    override fun toString(): String {
        return buildString {
            append("SourceSelectedApi(")

            append("item=")
            append(item)
            append(", itemApiVariants=")
            append(itemApiVariants.formatFor(selectedApiUpdater.apiSurfaces))
            append(")")
        }
    }
}

/** Base [SelectedApi] class for source [PackageItem]s. */
private class PackageSelectedApi(
    selectedApiUpdater: SelectedApiUpdater,
    item: PackageItem,
) : SourceSelectedApi<PackageItem>(selectedApiUpdater, item) {
    override fun itemSpecificInitialization() {

        updateFromSelectableItem()

        // Packages do not belong to an API surface in their own right. They belong to the union of
        // the API surfaces to which their contained classes belong. So, reset this to empty to
        // ignore the default values.
        itemApiVariants = ApiVariantSet.EMPTY

        // At this point itemApiVariants has been set which means it is now safe to compute the
        // selectedApi for the contained classes which may access itemApiVariants.

        // Make sure that all the classes in the package have also had their selectedApi initialized
        // as that can affect this package's selectedApi.
        for (classItem in item.topLevelClasses()) {
            classItem.selectedApi
        }
    }
}

/** Base [SelectedApi] class for source [ClassItem]s. */
private class ClassSelectedApi(
    selectedApiUpdater: SelectedApiUpdater,
    item: ClassItem,
) : SourceSelectedApi<ClassItem>(selectedApiUpdater, item) {
    override fun itemSpecificInitialization() {
        updateFromSelectableItem()

        // Propagate information from this to the parent, which may be a containing class or
        // package.
        parent.itemApiVariants += itemApiVariants
    }
}

/** Base [SelectedApi] class for source [MemberItem]s. */
private class MemberSelectedApi(
    selectedApiUpdater: SelectedApiUpdater,
    item: MemberItem,
) : SourceSelectedApi<MemberItem>(selectedApiUpdater, item) {
    override fun itemSpecificInitialization() {
        updateFromSelectableItem()
    }
}
