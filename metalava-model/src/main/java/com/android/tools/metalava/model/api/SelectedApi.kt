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
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.MemberItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.api.SurfaceSelectionRule.Effect
import com.android.tools.metalava.model.api.surface.ApiSurface
import com.android.tools.metalava.model.api.surface.ApiVariant
import com.android.tools.metalava.model.api.surface.ApiVariantSet
import com.android.tools.metalava.model.api.surface.ApiVariantType
import com.android.tools.metalava.model.item.DefaultSelectableItem

/** Provides access to the [ApiVariantSet] to which a specific [SelectableItem] belongs. */
sealed class SelectedApi {
    /** The [ApiVariantSet] for the [SelectableItem]. */
    abstract val itemApiVariants: ApiVariantSet

    /** The [ApiVariantSet] for child items. */
    abstract val contentApiVariants: ApiVariantSet

    /**
     * The [SelectableItem] from the previously released API that matches this item, if this item is
     * to be reverted.
     */
    abstract val revertItem: SelectableItem?

    /** Checks to see if the associated [SelectableItem] contains any doconly annotations. */
    open fun hasDocOnlyAnnotation(): Boolean = false

    /** Checks to see if the associated [SelectableItem] contains any removed annotations. */
    open fun hasRemovedAnnotation(): Boolean = false

    /**
     * Initialize this instance.
     *
     * This is called after this has been created and assigned to
     * [DefaultSelectableItem.selectedApi].
     */
    internal abstract fun initialize()

    /**
     * Add [value] to [itemApiVariants].
     *
     * This can only be called on items loaded from signature files.
     */
    abstract fun addItemApiVariant(value: ApiVariant)

    /**
     * Populate this instance with the state from the [original] [SelectedApi] of the item being
     * snapshotted.
     *
     * This can only be called when creating a snapshot of the codebase using a
     * [SelectedApi.SIMPLE_FACTORY].
     */
    abstract fun snapshot(original: SelectedApi)

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
            val previouslyReleasedCodebaseProvider = {
                annotationManager.previouslyReleasedCodebase
            }

            // Create an updater that will be captured by the factory below and will be used by all
            // SelectedApi instances in the Codebase that uses tha factory.
            val selectedApiUpdater =
                SelectedApiUpdater(
                    config.reporter,
                    apiSurfaceSelector,
                    previouslyReleasedCodebaseProvider,
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
                is MethodItem -> MethodSelectedApi(selectedApiUpdater, item)
                is ConstructorItem -> ConstructorSelectedApi(selectedApiUpdater, item)
                is PropertyItem -> PropertySelectedApi(selectedApiUpdater, item)
                is MemberItem -> MemberSelectedApi(selectedApiUpdater, item)
                is PackageItem -> PackageSelectedApi(selectedApiUpdater, item)
                else -> error("unknown selectable item: $item")
            }
    }
}

/**
 * A simple [SelectedApi] that stores [itemApiVariants], [contentApiVariants] without requiring a
 * [SelectedApiUpdater] or parent hierarchy.
 *
 * Used for snapshot codebases where variants are copied from the original codebase and signature
 * file codebases.
 */
private class SimpleSelectedApi : SelectedApi() {
    override var itemApiVariants = ApiVariantSet.EMPTY

    override var contentApiVariants = ApiVariantSet.EMPTY

    override val revertItem: SelectableItem?
        get() = null

    override fun initialize() {}

    override fun addItemApiVariant(value: ApiVariant) {
        itemApiVariants += value
    }

    override fun snapshot(original: SelectedApi) {
        itemApiVariants = original.itemApiVariants
        contentApiVariants = original.contentApiVariants
    }
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
    internal lateinit var parent: SourceSelectedApi<*>

    /**
     * Indicates whether the associated [SelectableItem] is accessible as part of an API.
     *
     * An item is accessible if its enclosing item (parent) is accessible and its visibility level
     * allows API access.
     */
    var accessible: Boolean = false
        internal set

    /**
     * Indicates whether the associated [SelectableItem] has a doc only annotation.
     *
     * Initialized by [SelectedApiUpdater.updateSelectedApi] called from [updateFromSelectableItem].
     */
    var docOnly: Boolean = false
        internal set

    /**
     * Indicates whether the associated [SelectableItem] has a removed annotation.
     *
     * Initialized by [SelectedApiUpdater.updateSelectedApi] called from [updateFromSelectableItem].
     */
    var removed: Boolean = false
        internal set

    /**
     * Indicates whether the associated [SelectableItem] is being reverted.
     *
     * Initialized by [SelectedApiUpdater.updateSelectedApi] called from [updateFromSelectableItem].
     */
    var revert: Boolean = false

    /**
     * The [SelectableItem] from the previously released API that matches this item, if this item is
     * being reverted.
     *
     * Initialized by [SelectedApiUpdater.updateSelectedApi] called from [updateFromSelectableItem].
     */
    override var revertItem: SelectableItem? = null

    /**
     * The [ApiVariantSet] for the [item].
     *
     * This is initialized in [initialize] which must have been called and which must initialize
     * this before it is accessed.
     */
    override var itemApiVariants = ApiVariantSet.EMPTY

    override var contentApiVariants = ApiVariantSet.EMPTY

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

    /** Checks to see if the associated [SelectableItem] contains any removed annotations. */
    override fun hasRemovedAnnotation() = removed

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
                    accessible = true
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

    /** Adopt the status from [other]. */
    fun adoptStatusFrom(other: SourceSelectedApi<*>) {
        itemApiVariants = other.itemApiVariants
        inheritableApiVariants = other.inheritableApiVariants
        docOnly = other.docOnly
        removed = other.removed
        revert = other.revert
        revertItem = other.revertItem
    }

    override fun addItemApiVariant(value: ApiVariant) {
        error("Cannot update itemApiVariants in $this")
    }

    override fun snapshot(original: SelectedApi) {
        error("Cannot populate $this in a snapshot")
    }

    /**
     * Perform any item specific initialization.
     *
     * This is called after [parent] has been initialized, and it is the responsibility of this to
     * call [updateFromSelectableItem] to update the state before accessing the
     * [SelectableItem.selectedApi] of any enclosed items.
     */
    abstract fun itemSpecificInitialization()

    /** Propagate the [childVariants] to this parent item. */
    abstract fun propagateFromChild(childVariants: ApiVariantSet)

    /**
     * Returns whether the children of this [SourceSelectedApi] are completely hidden, i.e. in all
     * API surfaces.
     *
     * If it returns `true` then all the children of the [item] will be marked as hidden in all API
     * surfaces.
     */
    abstract fun areChildrenCompletelyHidden(): Boolean

    override fun toString() = buildString {
        append("SourceSelectedApi(")

        append("item=")
        append(item)
        append(", accessible=")
        append(accessible)
        append(", itemApiVariants=")
        append(itemApiVariants.formatFor(selectedApiUpdater.apiSurfaces))
        append(", inheritableApiVariants=")
        append(inheritableApiVariants.formatFor(selectedApiUpdater.apiSurfaces))
        append(", contentApiVariants=")
        append(contentApiVariants.formatFor(selectedApiUpdater.apiSurfaces))
        append(", revert=")
        append(revert)
        append(", revertItem=")
        append(revertItem)
        append(")")
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
        contentApiVariants = ApiVariantSet.EMPTY
    }

    override fun propagateFromChild(childVariants: ApiVariantSet) {
        // Packages are not included in API surfaces directly. Instead, a package is included in an
        // API surface if one of its child classes is included in the API surface. So, add all the
        // child variants to this package's variant set.
        itemApiVariants += childVariants
    }

    /**
     * A package never hides its children (i.e. classes) as a package's API variants is the union of
     * all its children classes' API variants.
     */
    override fun areChildrenCompletelyHidden() = false
}

/** Base [SelectedApi] class for source [ClassItem]s. */
private class ClassSelectedApi(
    selectedApiUpdater: SelectedApiUpdater,
    item: ClassItem,
) : SourceSelectedApi<ClassItem>(selectedApiUpdater, item) {
    override fun itemSpecificInitialization() {
        updateFromSelectableItem()

        // Propagate variants to the containing package, skipping any intermediate nested classes
        // as they will be flattened when generating signature files.
        propagateToContainingPackage(itemApiVariants)

        // Replicate similar behavior to what is done in ApiPredicate.
        item.superClass()?.let { superClass ->
            val superClassVariants = superClass.selectedApi.itemApiVariants
            val apiSurfaces = selectedApiUpdater.apiSurfaces

            // Get the narrowest surface to which the super class belongs and the widest surface to
            // which this class belongs.
            val superClassSurface = superClassVariants.narrowestSurfaceFor(apiSurfaces)
            val itemSurface = itemApiVariants.widestSurfaceFor(apiSurfaces)
            if (superClassSurface == null || itemSurface == null) return@let

            // If the super class' surface is wider than this class' surface then add the super
            // class' variants to this class' content variants so that this class will be included,
            // but only for variant types that this class also belongs to.
            if (superClassSurface > itemSurface) {
                // Translate this class's variants from its surface to the super class's surface.
                // This acts as a mask containing only the variant types that this class belongs to,
                // but situated in the super class's surface.
                val superVariantsMask =
                    itemApiVariants.moveVariantsBetweenSurfaces(itemSurface, superClassSurface)

                // Only add super class variants whose types match this class's own variant types
                // (e.g. only inherit system(C) if this class has public(C)).
                contentApiVariants += superClassVariants.intersectionWith(superVariantsMask)
            }
        }
    }

    /** Propagate [childVariants] to the containing package of this. */
    private fun propagateToContainingPackage(childVariants: ApiVariantSet) {
        // Find the enclosing package. This purposely skips classes as variants must not be
        // propagated from nested classes to their containing class as that is unnecessary for
        // signature file generation where nested classes are flattened.
        var ancestor: SourceSelectedApi<*> = parent
        while (ancestor !is PackageSelectedApi) {
            ancestor = ancestor.parent
        }

        // Propagate information from this to the enclosing package. This is necessary to ensure
        // that the package and its classes are correctly included in a signature file.
        ancestor.propagateFromChild(childVariants)
    }

    override fun propagateFromChild(childVariants: ApiVariantSet) {
        // Include every variant to which the child belongs except those to which this already
        // belongs.
        val propagateVariants = childVariants - itemApiVariants

        // If there are no variants to propagate then return immediately.
        if (propagateVariants.isEmpty()) return

        // Add them to the class.
        contentApiVariants += propagateVariants

        // Propagate to containing package.
        propagateToContainingPackage(propagateVariants)
    }

    override fun areChildrenCompletelyHidden() = itemApiVariants.isEmpty()
}

/** Base [SelectedApi] class for source [MemberItem]s. */
private open class MemberSelectedApi<M : MemberItem>(
    selectedApiUpdater: SelectedApiUpdater,
    item: M,
) : SourceSelectedApi<M>(selectedApiUpdater, item) {

    /** This does not initialize [inheritableApiVariants] as [MemberItem]s do not have children. */
    override fun itemSpecificInitialization() {
        updateFromSelectableItem()

        // Propagate information from this to the parent, i.e. the containing class.
        parent.propagateFromChild(itemApiVariants)
    }

    override fun propagateFromChild(childVariants: ApiVariantSet) =
        throw NotImplementedError("class members do not have any children")

    override fun areChildrenCompletelyHidden() =
        throw NotImplementedError("class members do not have any children")
}

/**
 * Selected API class for methods, ensuring record component getter methods inherit the parent
 * class's API variants.
 */
private class MethodSelectedApi(
    selectedApiUpdater: SelectedApiUpdater,
    item: MethodItem,
) : MemberSelectedApi<MethodItem>(selectedApiUpdater, item) {

    override fun itemSpecificInitialization() {
        // Make sure that the record component getters are all in the same API surfaces as the
        // class.
        if (item.isRecordComponentGetter) {
            itemApiVariants = parent.itemApiVariants
            return
        }

        super.itemSpecificInitialization()

        // If the parent is not part of any API then the method cannot be either.
        if (parent.itemApiVariants.isEmpty()) return

        // Unlike classes and fields, methods implicitly inherit API surface membership from the
        // methods they override (e.g. an unannotated or @Hide method implementing a public
        // interface method). If this method did not directly specify any API variants, find the
        // widest API surface inherited from overridden super methods.
        if (itemApiVariants.isEmpty()) {
            val apiSurfaces = selectedApiUpdater.apiSurfaces
            val parentSurface = parent.itemApiVariants.widestSurfaceFor(apiSurfaces) ?: return
            var maxSuperSurface: ApiSurface? = null
            var maxSuperVariants = ApiVariantSet.EMPTY

            for (superMethod in item.superMethods()) {
                val superVariants = superMethod.selectedApi.itemApiVariants
                val superSurface = superVariants.narrowestSurfaceFor(apiSurfaces) ?: continue

                // Do not inherit removed or doconly status from overridden methods.
                val superCoreVariant = superSurface.variantFor(ApiVariantType.CORE)
                if (superCoreVariant !in superVariants) {
                    continue
                }

                // A method can only inherit API variants from a super method in a narrower API
                // surface than the containing class.
                if (superSurface >= parentSurface) {
                    continue
                }

                // Find the widest API surface among the super methods.
                if (maxSuperSurface == null || superSurface > maxSuperSurface) {
                    maxSuperSurface = superSurface
                    maxSuperVariants = superVariants
                }

                // Stop searching if maxSuperSurface is at least as wide as the main surface being
                // generated.
                if (maxSuperSurface >= apiSurfaces.main) {
                    break
                }
            }

            // Adopt the API variants from the super method in the widest surface found.
            if (maxSuperSurface != null) {
                itemApiVariants = maxSuperVariants
            }
        }
    }
}

/**
 * Selected API class for constructors, ensuring canonical record constructors inherit the parent
 * class's API variants.
 */
private class ConstructorSelectedApi(
    selectedApiUpdater: SelectedApiUpdater,
    item: ConstructorItem,
) : MemberSelectedApi<ConstructorItem>(selectedApiUpdater, item) {

    override fun itemSpecificInitialization() {
        // Make sure that the canonical record constructor is in the same API surfaces as the class.
        if (item.isCanonicalRecordComponentConstructor) {
            itemApiVariants = parent.itemApiVariants
            return
        }

        super.itemSpecificInitialization()
    }
}

/**
 * Selected API class for properties, ensuring properties with an exposed backing field (e.g. `const
 * val` or `@JvmField`) inherit the backing field's API variants and status, and properties with an
 * explicitly hidden private backing field are also marked as hidden.
 */
private class PropertySelectedApi(
    selectedApiUpdater: SelectedApiUpdater,
    item: PropertyItem,
) : MemberSelectedApi<PropertyItem>(selectedApiUpdater, item) {

    override fun itemSpecificInitialization() {
        updateFromSelectableItem()

        // If the property is already hidden (e.g. parent is hidden, property has no API
        // visibility, or has an explicit hide annotation), do not unhide it.
        if (itemApiVariants.isNotEmpty()) {
            item.backingField?.let { backingField ->
                // A property's backing field can take one of two forms:
                // 1. Backing fields with API visibility (e.g. `const val` or `@JvmField`):
                //    These fields are exposed in bytecode and have API visibility. Their
                //    SelectedApi status already reflects their accessibility, enclosing
                //    surfaces, and annotations. The property adopts their status directly.
                // 2. Private backing fields (standard Kotlin properties):
                //    These are private and thus always lack API visibility, which causes
                //    their SelectedApi status to be inaccessible and have empty API variants
                //    by default. The property itself is represented in the API by its accessors,
                //    so its backing field being inaccessible does not mean the property should
                //    be hidden. However, if the private backing field has an explicit hide
                //    annotation (e.g. `@field:Hide` or `@field:RestrictTo`), that intent should
                //    hide the property as well. Therefore, we explicitly check for hide
                //    annotations rather than relying on the backing field's accessibility or
                //    empty API variants.
                if (selectedApiUpdater.hasApiVisibility(backingField.modifiers)) {
                    val fieldSelectedApi = backingField.selectedApi as? SourceSelectedApi<*>
                    if (fieldSelectedApi != null) {
                        adoptStatusFrom(fieldSelectedApi)
                    }
                } else if (isExplicitlyHidden(backingField)) {
                    selectedApiUpdater.markAsHidden(this, revert = false)
                }
            }
        }

        // Propagate information from this to the parent, i.e. the containing class.
        parent.propagateFromChild(itemApiVariants)
    }

    /**
     * Check whether this [FieldItem] is explicitly hidden via a hide annotation and is not shown
     * via a show annotation.
     */
    private fun isExplicitlyHidden(field: FieldItem): Boolean {
        var hide = false
        for (annotationItem in field.modifiers.annotations()) {
            annotationItem.surfaceData?.let { surfaceData ->
                when (surfaceData.effect) {
                    Effect.SHOW -> return false
                    Effect.HIDE -> hide = true
                    else -> {}
                }
            }
        }
        return hide
    }
}
