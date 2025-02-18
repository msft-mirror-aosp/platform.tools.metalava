/*
 * Copyright (C) 2023 The Android Open Source Project
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
import com.android.tools.metalava.model.BaseModifierList
import com.android.tools.metalava.model.CallableBody
import com.android.tools.metalava.model.CallableBodyFactory
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.ExceptionTypeItem
import com.android.tools.metalava.model.ItemDocumentation
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.android.tools.metalava.model.ItemLanguage
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.createImmutableModifiers
import com.android.tools.metalava.reporter.FileLocation

open class DefaultConstructorItem(
    codebase: Codebase,
    fileLocation: FileLocation,
    itemLanguage: ItemLanguage,
    modifiers: BaseModifierList,
    documentationFactory: ItemDocumentationFactory,
    variantSelectorsFactory: ApiVariantSelectorsFactory,
    name: String,
    containingClass: ClassItem,
    typeParameterList: TypeParameterList,
    returnType: ClassTypeItem,
    parameterItemsFactory: ParameterItemsFactory,
    throwsTypes: List<ExceptionTypeItem>,
    callableBodyFactory: CallableBodyFactory,
    private val implicitConstructor: Boolean,
    override val isPrimary: Boolean = false,
) :
    DefaultCallableItem(
        codebase = codebase,
        fileLocation = fileLocation,
        itemLanguage = itemLanguage,
        modifiers = modifiers,
        documentationFactory = documentationFactory,
        variantSelectorsFactory = variantSelectorsFactory,
        name = name,
        containingClass = containingClass,
        typeParameterList = typeParameterList,
        returnType = returnType,
        parameterItemsFactory = parameterItemsFactory,
        throwsTypes = throwsTypes,
        callableBodyFactory = callableBodyFactory,
    ),
    ConstructorItem {

    /** Override to specialize the return type. */
    final override fun returnType() = super.returnType() as ClassTypeItem

    /** Override to make sure that [type] is a [ClassTypeItem]. */
    final override fun setType(type: TypeItem) {
        super.setType(type as ClassTypeItem)
    }

    final override fun isImplicitConstructor() = implicitConstructor

    companion object {
        fun createDefaultConstructor(
            codebase: Codebase,
            itemLanguage: ItemLanguage,
            variantSelectorsFactory: ApiVariantSelectorsFactory,
            containingClass: ClassItem,
            visibility: VisibilityLevel,
        ): ConstructorItem {
            val name = containingClass.simpleName()
            val modifiers = createImmutableModifiers(visibility)

            val ctorItem =
                DefaultConstructorItem(
                    codebase = codebase,
                    // Use the location of the containing class for the default constructor.
                    fileLocation = containingClass.fileLocation,
                    itemLanguage = itemLanguage,
                    modifiers = modifiers,
                    documentationFactory = ItemDocumentation.NONE_FACTORY,
                    variantSelectorsFactory = variantSelectorsFactory,
                    name = name,
                    containingClass = containingClass,
                    typeParameterList = TypeParameterList.NONE,
                    returnType = containingClass.type(),
                    parameterItemsFactory = { emptyList() },
                    throwsTypes = emptyList(),
                    callableBodyFactory = CallableBody.UNAVAILABLE_FACTORY,
                    // This is not an implicit constructor as it was not created by the compiler.
                    implicitConstructor = false,
                )
            return ctorItem
        }
    }
}
