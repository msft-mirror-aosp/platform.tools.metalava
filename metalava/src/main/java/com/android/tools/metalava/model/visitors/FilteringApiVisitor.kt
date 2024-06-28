/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.tools.metalava.model.visitors

import com.android.tools.metalava.model.BaseItemVisitor
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.ExceptionTypeItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.ItemVisitor
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.SourceFile
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeTransformer
import com.android.tools.metalava.model.typeUseAnnotationFilter
import java.util.function.Predicate

/**
 * An [ApiVisitor] that filters the input and forwards it to the [delegate] [ItemVisitor].
 *
 * This defines a number of `Filtering*Item` classes that will filter out any [Item] references for
 * which [filterReference] returns false. They are not suitable for general use. Their sole purpose
 * is to provide enough functionality for use when writing a representation of the item, e.g. for
 * signatures, stubs, etc. That means that there may be some methods that are not use by those
 * writers which will allow access to unfiltered `Item`s.
 */
class FilteringApiVisitor(
    val delegate: BaseItemVisitor,
    visitConstructorsAsMethods: Boolean = true,
    nestInnerClasses: Boolean = false,
    inlineInheritedFields: Boolean = true,
    methodComparator: Comparator<MethodItem> = MethodItem.comparator,
    /**
     * Lambda for returning a filtered, list of interfaces from a [ClassItem] filtered by the
     * [Predicate].
     *
     * Each interface is represented as a [ClassTypeItem] and the caller will filter out any
     * unwanted type use annotations from them using the same [Predicate].
     *
     * The [Boolean] parameter is set to [preFiltered] so if `true` the [Predicate] can be assumed
     * to be `{ true }`.
     */
    @Suppress("NAME_SHADOWING")
    private val interfaceListAccessor:
        (ClassItem, Predicate<Item>, Boolean) -> List<ClassTypeItem> =
        { classItem, filterReference, preFiltered ->
            if (preFiltered) classItem.interfaceTypes()
            else classItem.filteredInterfaceTypes(filterReference).toList()
        },
    /** Optional comparator to use for sorting interface list types. */
    private val interfaceListComparator: Comparator<TypeItem>? = null,
    filterEmit: Predicate<Item>,
    filterReference: Predicate<Item>,
    private val preFiltered: Boolean,
    includeEmptyOuterClasses: Boolean = false,
    showUnannotated: Boolean = true,
    /**
     * If true then this will visit the [ClassItem.stubConstructor] if it would not otherwise be
     * visited. See [dispatchStubsConstructorIfAvailable].
     */
    private val visitStubsConstructorIfNeeded: Boolean = false,
    config: Config,
) :
    ApiVisitor(
        visitConstructorsAsMethods,
        nestInnerClasses,
        inlineInheritedFields,
        methodComparator,
        filterEmit,
        filterReference,
        includeEmptyOuterClasses,
        showUnannotated,
        config,
    ),
    ItemVisitor {

    /**
     * A [TypeTransformer] that will remove any type annotations for which [filterReference] returns
     * false when called against the annotation's [ClassItem].
     */
    private val typeAnnotationFilter = typeUseAnnotationFilter(filterReference)

    override fun visitCodebase(codebase: Codebase) {
        // This does not create a filtering wrapper around the Codebase as the classes to which this
        // currently delegates do not access any fields within the Codebase.
        delegate.visitCodebase(codebase)
    }

    override fun afterVisitCodebase(codebase: Codebase) {
        // This does not create a filtering wrapper around the Codebase as the classes to which this
        // currently delegates do not access any fields within the Codebase.
        delegate.afterVisitCodebase(codebase)
    }

    override fun visitPackage(pkg: PackageItem) {
        delegate.visitPackage(pkg)
    }

    override fun afterVisitPackage(pkg: PackageItem) {
        delegate.afterVisitPackage(pkg)
    }

    /** Stack of the containing classes. */
    private val containingClassStack = ArrayDeque<FilteringClassItem?>()

    /** The current [ClassItem] being visited, */
    private var currentClassItem: FilteringClassItem? = null

    override fun visitClass(cls: ClassItem) {
        // Switch the current class, if any, to be a containing class.
        containingClassStack.addLast(currentClassItem)

        // Create a new FilteringClassItem for the current class and visit it before its contents.
        currentClassItem = FilteringClassItem(delegate = cls)
        delegate.visitClass(currentClassItem!!)

        if (visitStubsConstructorIfNeeded) {
            dispatchStubsConstructorIfAvailable(cls)
        }
    }

    /**
     * Stubs that have no accessible constructor may still need to generate one and that constructor
     * is available from [ClassItem.stubConstructor].
     *
     * However, sometimes that constructor is ignored by this because it is not accessible either,
     * e.g. it might be package private. In that case this will pass it to
     * [BaseItemVisitor.visitConstructor] directly.
     */
    private fun dispatchStubsConstructorIfAvailable(cls: ClassItem) {
        val clsStubConstructor = cls.stubConstructor
        val constructors = cls.filteredConstructors(filterEmit)
        // If the default stub constructor is not publicly visible then it won't be output during
        // the normal visiting so visit it specially to ensure that it is output.
        if (clsStubConstructor != null && !constructors.contains(clsStubConstructor)) {
            visitConstructor(clsStubConstructor)
        }
    }

    override fun afterVisitClass(cls: ClassItem) {
        // Consistency check to make sure that the visitClass/afterVisitClass are called correctly.
        if (currentClassItem?.delegate !== cls)
            throw IllegalStateException("Expected ${currentClassItem?.delegate}, found ${cls}")

        // Visit the class after its contents.
        delegate.afterVisitClass(currentClassItem!!)

        // Switch back to the containing class, if any.
        currentClassItem = containingClassStack.removeLast()
    }

    override fun visitConstructor(constructor: ConstructorItem) {
        val filteringConstructor = FilteringConstructorItem(constructor)
        delegate.visitConstructor(filteringConstructor)
    }

    override fun visitMethod(method: MethodItem) {
        val filteringMethod = FilteringMethodItem(method)
        delegate.visitMethod(filteringMethod)
    }

    override fun visitField(field: FieldItem) {
        val filteringField = FilteringFieldItem(field)
        delegate.visitField(filteringField)
    }

    override fun visitProperty(property: PropertyItem) {
        val filteringProperty = FilteringPropertyItem(property)
        delegate.visitProperty(filteringProperty)
    }

    /**
     * [SourceFile] that will filter out anything which is not to be written out by the
     * [FilteringApiVisitor.delegate].
     */
    private inner class FilteringSourceFile(val delegate: SourceFile) : SourceFile by delegate {

        override fun getImports() = delegate.getImports(filterReference)
    }

    /**
     * [ClassItem] that will filter out anything which is not to be written out by the
     * [FilteringApiVisitor.delegate].
     */
    private inner class FilteringClassItem(
        val delegate: ClassItem,
    ) : ClassItem by delegate {

        override fun getSourceFile() = delegate.getSourceFile()?.let { FilteringSourceFile(it) }

        override fun superClass() = superClassType()?.asClass()

        override fun superClassType() =
            if (preFiltered) delegate.superClassType()
            else delegate.filteredSuperClassType(filterReference)?.transform(typeAnnotationFilter)

        override fun interfaceTypes(): List<ClassTypeItem> {
            // Get the list of filtered by unsorted interface types.
            val unsorted = interfaceListAccessor(delegate, filterReference, preFiltered)

            // Sort them if required, or use
            val ordered =
                if (interfaceListComparator == null) unsorted.toList()
                else unsorted.sortedWith(interfaceListComparator)

            // If required then filter annotation types from the ordered list before returning.
            return if (preFiltered) ordered
            else
                ordered.map {
                    // Filter any inaccessible annotations from the interfaces
                    it.transform(typeAnnotationFilter)
                }
        }

        override fun constructors() =
            delegate
                .filteredConstructors(filterReference)
                .map { FilteringConstructorItem(it) }
                .toList()

        override fun fields(): List<FieldItem> =
            delegate.filteredFields(filterReference, showUnannotated).map { FilteringFieldItem(it) }
    }

    /**
     * [ParameterItem] that will filter out anything which is not to be written out by the
     * [FilteringApiVisitor.delegate].
     */
    private inner class FilteringParameterItem(private val delegate: ParameterItem) :
        ParameterItem by delegate {

        override fun type() = delegate.type().transform(typeAnnotationFilter)
    }

    /** Get the [MethodItem.returnType] and apply the [typeAnnotationFilter] to it. */
    fun filteredReturnType(methodItem: MethodItem) =
        methodItem.returnType().transform(typeAnnotationFilter)

    /** Get the [MethodItem.parameters] and wrap each one in a [FilteringParameterItem]. */
    fun filteredParameters(methodItem: MethodItem): List<ParameterItem> =
        methodItem.parameters().map { FilteringParameterItem(it) }

    /**
     * Get the [MethodItem.filteredThrowsTypes] and apply [typeAnnotationFilter] to each
     * [ExceptionTypeItem] in the list.
     */
    private fun filteredThrowsTypes(methodItem: MethodItem) =
        if (preFiltered) methodItem.throwsTypes()
        else
            methodItem.filteredThrowsTypes(filterReference).map {
                it.transform(typeAnnotationFilter)
            }

    /**
     * [ConstructorItem] that will filter out anything which is not to be written out by the
     * [FilteringApiVisitor.delegate].
     */
    private inner class FilteringConstructorItem(private val delegate: ConstructorItem) :
        ConstructorItem by delegate {

        override fun containingClass() = FilteringClassItem(delegate.containingClass())

        override var superConstructor: ConstructorItem?
            get() = delegate.superConstructor?.let { FilteringConstructorItem(it) }
            set(_) {
                error("cannot set value")
            }

        override fun returnType() = filteredReturnType(delegate)

        override fun parameters() = filteredParameters(delegate)

        override fun throwsTypes() = filteredThrowsTypes(delegate)
    }

    /**
     * [MethodItem] that will filter out anything which is not to be written out by the
     * [FilteringApiVisitor.delegate].
     */
    private inner class FilteringMethodItem(private val delegate: MethodItem) :
        MethodItem by delegate {

        override fun returnType() = filteredReturnType(delegate)

        override fun parameters() = filteredParameters(delegate)

        override fun throwsTypes() = filteredThrowsTypes(delegate)
    }

    /**
     * [FieldItem] that will filter out anything which is not to be written out by the
     * [FilteringApiVisitor.delegate].
     */
    private inner class FilteringFieldItem(private val delegate: FieldItem) :
        FieldItem by delegate {

        override fun type() = delegate.type().transform(typeAnnotationFilter)
    }

    /**
     * [PropertyItem] that will filter out anything which is not to be written out by the
     * [FilteringApiVisitor.delegate].
     */
    private inner class FilteringPropertyItem(private val delegate: PropertyItem) :
        PropertyItem by delegate {

        override fun type() = delegate.type().transform(typeAnnotationFilter)
    }
}