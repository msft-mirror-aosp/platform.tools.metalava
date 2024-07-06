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

package com.android.tools.metalava.model.turbine

import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.DefaultCodebase
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.DefaultTypeParameterList
import com.android.tools.metalava.model.ItemDocumentation
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.reporter.FileLocation
import com.google.turbine.binder.sym.MethodSymbol

internal class TurbineConstructorItem(
    codebase: DefaultCodebase,
    fileLocation: FileLocation,
    private val name: String,
    methodSymbol: MethodSymbol,
    containingClass: TurbineClassItem,
    returnType: ClassTypeItem,
    modifiers: DefaultModifierList,
    typeParameters: TypeParameterList,
    documentation: ItemDocumentation,
    private val defaultValue: String,
) :
    TurbineMethodItem(
        codebase = codebase,
        fileLocation = fileLocation,
        methodSymbol = methodSymbol,
        containingClass = containingClass,
        returnType = returnType,
        modifiers = modifiers,
        typeParameterList = typeParameters,
        documentation = documentation,
        defaultValue = defaultValue,
    ),
    ConstructorItem {

    override fun name(): String = name

    override var superConstructor: ConstructorItem? = null

    override fun isConstructor(): Boolean = true

    companion object {
        fun createDefaultConstructor(
            codebase: DefaultCodebase,
            containingClass: TurbineClassItem,
            symbol: MethodSymbol
        ): TurbineConstructorItem {
            val name = containingClass.simpleName()
            val modifiers = DefaultModifierList(codebase, DefaultModifierList.PACKAGE_PRIVATE, null)
            modifiers.setVisibilityLevel(containingClass.modifiers.getVisibilityLevel())
            val typeParameterList = DefaultTypeParameterList(emptyList())

            val ctorItem =
                TurbineConstructorItem(
                    codebase = codebase,
                    // Use the location of the containing class for the implicit default
                    // constructor.
                    fileLocation = containingClass.fileLocation,
                    name = name,
                    methodSymbol = symbol,
                    containingClass = containingClass,
                    returnType = containingClass.type(),
                    modifiers = modifiers,
                    typeParameters = typeParameterList,
                    documentation = ItemDocumentation.NONE,
                    defaultValue = "",
                )
            ctorItem.parameters = emptyList()
            ctorItem.throwableTypes = emptyList()
            return ctorItem
        }
    }
}
