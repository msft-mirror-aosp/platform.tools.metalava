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
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.ItemVisitor
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.PropertyItem
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
    filterEmit: Predicate<Item>,
    filterReference: Predicate<Item>,
    includeEmptyOuterClasses: Boolean = false,
    showUnannotated: Boolean = true,
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
     * [ClassItem] that will filter out anything which is not to be written out by the
     * [FilteringApiVisitor.delegate].
     */
    private inner class FilteringClassItem(
        val delegate: ClassItem,
    ) : ClassItem by delegate

    /**
     * [ParameterItem] that will filter out anything which is not to be written out by the
     * [FilteringApiVisitor.delegate].
     */
    private inner class FilteringParameterItem(private val delegate: ParameterItem) :
        ParameterItem by delegate

    /** Get the [MethodItem.parameters] and wrap each one in a [FilteringParameterItem]. */
    fun filteredParameters(methodItem: MethodItem): List<ParameterItem> =
        methodItem.parameters().map { FilteringParameterItem(it) }

    /**
     * [ConstructorItem] that will filter out anything which is not to be written out by the
     * [FilteringApiVisitor.delegate].
     */
    private inner class FilteringConstructorItem(private val delegate: ConstructorItem) :
        ConstructorItem by delegate {

        override fun parameters() = filteredParameters(delegate)
    }

    /**
     * [MethodItem] that will filter out anything which is not to be written out by the
     * [FilteringApiVisitor.delegate].
     */
    private inner class FilteringMethodItem(private val delegate: MethodItem) :
        MethodItem by delegate {

        override fun parameters() = filteredParameters(delegate)
    }

    /**
     * [FieldItem] that will filter out anything which is not to be written out by the
     * [FilteringApiVisitor.delegate].
     */
    private inner class FilteringFieldItem(private val delegate: FieldItem) : FieldItem by delegate

    /**
     * [PropertyItem] that will filter out anything which is not to be written out by the
     * [FilteringApiVisitor.delegate].
     */
    private inner class FilteringPropertyItem(private val delegate: PropertyItem) :
        PropertyItem by delegate
}
