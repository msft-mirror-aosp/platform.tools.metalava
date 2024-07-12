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

package com.android.tools.metalava.model.item

import com.android.tools.metalava.model.ApiVariantSelectorsFactory
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.DefaultCodebase
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.ExceptionTypeItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.ItemDocumentation
import com.android.tools.metalava.model.ItemLanguage
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.SourceFile
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.reporter.FileLocation

/**
 * A lambda that when passed the [Item] will return the public name, or null if there is not one.
 */
typealias PublicNameProvider = (Item) -> String?

/** A factory for creating [Item] instances suitable for use by many models. */
class DefaultItemFactory(
    /** The [DefaultCodebase] to which returned [Item]s will belong. */
    private val codebase: DefaultCodebase,

    /** The default language for [Item]s created by this. */
    private val defaultItemLanguage: ItemLanguage,

    /** The default [ApiVariantSelectorsFactory] for [Item]s created by this. */
    private val defaultVariantSelectorsFactory: ApiVariantSelectorsFactory,
) {
    /** Create a [PackageItem]. */
    fun createPackageItem(
        fileLocation: FileLocation = FileLocation.UNKNOWN,
        modifiers: DefaultModifierList = DefaultModifierList(codebase),
        documentation: ItemDocumentation = ItemDocumentation.NONE,
        qualifiedName: String,
    ): DefaultPackageItem {
        modifiers.setVisibilityLevel(VisibilityLevel.PUBLIC)
        return DefaultPackageItem(
            codebase,
            fileLocation,
            defaultItemLanguage,
            modifiers,
            documentation,
            defaultVariantSelectorsFactory,
            qualifiedName,
        )
    }

    /** Create a [ConstructorItem]. */
    fun createClassItem(
        fileLocation: FileLocation,
        modifiers: DefaultModifierList,
        documentation: ItemDocumentation = ItemDocumentation.NONE,
        source: SourceFile? = null,
        classKind: ClassKind,
        containingClass: ClassItem?,
        qualifiedName: String = "",
        simpleName: String = qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1),
        fullName: String = simpleName,
        typeParameterList: TypeParameterList,
    ) =
        DefaultClassItem(
            codebase,
            fileLocation,
            defaultItemLanguage,
            modifiers,
            documentation,
            defaultVariantSelectorsFactory,
            source,
            classKind,
            containingClass,
            qualifiedName,
            simpleName,
            fullName,
            typeParameterList,
        )

    /** Create a [ConstructorItem]. */
    fun createConstructorItem(
        fileLocation: FileLocation,
        modifiers: DefaultModifierList,
        documentation: ItemDocumentation,
        name: String,
        containingClass: ClassItem,
        typeParameterList: TypeParameterList,
        returnType: ClassTypeItem,
        parameterItemsFactory: ParameterItemsFactory,
        throwsTypes: List<ExceptionTypeItem>,
        implicitConstructor: Boolean,
    ): ConstructorItem =
        DefaultConstructorItem(
            codebase,
            fileLocation,
            defaultItemLanguage,
            modifiers,
            documentation,
            defaultVariantSelectorsFactory,
            name,
            containingClass,
            typeParameterList,
            returnType,
            parameterItemsFactory,
            throwsTypes,
            implicitConstructor,
        )

    /** Create a [FieldItem]. */
    fun createFieldItem(
        fileLocation: FileLocation,
        modifiers: DefaultModifierList,
        documentation: ItemDocumentation,
        name: String,
        containingClass: ClassItem,
        type: TypeItem,
        isEnumConstant: Boolean,
        fieldValue: FieldValue?,
    ): FieldItem =
        DefaultFieldItem(
            codebase,
            fileLocation,
            defaultItemLanguage,
            defaultVariantSelectorsFactory,
            modifiers,
            documentation,
            name,
            containingClass,
            type,
            isEnumConstant,
            fieldValue,
        )

    /** Create a [MethodItem]. */
    fun createMethodItem(
        fileLocation: FileLocation,
        modifiers: DefaultModifierList,
        documentation: ItemDocumentation,
        name: String,
        containingClass: ClassItem,
        typeParameterList: TypeParameterList,
        returnType: TypeItem,
        parameterItemsFactory: ParameterItemsFactory,
        throwsTypes: List<ExceptionTypeItem>,
        annotationDefault: String,
    ): MethodItem =
        DefaultMethodItem(
            codebase,
            fileLocation,
            defaultItemLanguage,
            modifiers,
            documentation,
            defaultVariantSelectorsFactory,
            name,
            containingClass,
            typeParameterList,
            returnType,
            parameterItemsFactory,
            throwsTypes,
            annotationDefault,
        )

    /** Create a [ParameterItem]. */
    fun createParameterItem(
        fileLocation: FileLocation,
        modifiers: DefaultModifierList,
        name: String,
        publicNameProvider: PublicNameProvider,
        containingMethod: MethodItem,
        parameterIndex: Int,
        type: TypeItem,
        defaultValue: DefaultValue,
    ): ParameterItem =
        DefaultParameterItem(
            codebase,
            fileLocation,
            defaultItemLanguage,
            modifiers,
            defaultVariantSelectorsFactory,
            name,
            publicNameProvider,
            containingMethod,
            parameterIndex,
            type,
            defaultValue,
        )

    /** Create a [PropertyItem]. */
    fun createPropertyItem(
        fileLocation: FileLocation,
        modifiers: DefaultModifierList,
        name: String,
        containingClass: ClassItem,
        type: TypeItem,
    ): PropertyItem =
        DefaultPropertyItem(
            codebase,
            fileLocation,
            defaultItemLanguage,
            defaultVariantSelectorsFactory,
            modifiers,
            name,
            containingClass,
            type,
        )

    /**
     * Create a [DefaultTypeParameterItem].
     *
     * This returns [DefaultTypeParameterItem] because access is needed to its
     * [DefaultTypeParameterItem.bounds] after creation as full creation is a two stage process due
     * to cyclical dependencies between [DefaultTypeParameterItem] in a type parameters list.
     *
     * TODO(b/351410134): Provide support in this factory for two stage initialization.
     */
    fun createTypeParameterItem(
        modifiers: DefaultModifierList,
        name: String,
        isReified: Boolean,
    ) =
        DefaultTypeParameterItem(
            codebase,
            defaultItemLanguage,
            modifiers,
            defaultVariantSelectorsFactory,
            name,
            isReified,
        )
}
