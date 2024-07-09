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

import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.ApiVariantSelectors
import com.android.tools.metalava.model.DefaultCodebase
import com.android.tools.metalava.model.DefaultItem
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.ItemDocumentation
import com.android.tools.metalava.model.ItemLanguage
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterBindings
import com.android.tools.metalava.model.findAnnotation
import com.android.tools.metalava.model.hasAnnotation
import com.android.tools.metalava.reporter.FileLocation

internal class TurbineParameterItem(
    codebase: DefaultCodebase,
    fileLocation: FileLocation,
    modifiers: DefaultModifierList,
    private val name: String,
    private val containingMethod: MethodItem,
    override val parameterIndex: Int,
    private var type: TypeItem,
) :
    DefaultItem(
        codebase = codebase,
        fileLocation = fileLocation,
        itemLanguage = ItemLanguage.JAVA,
        modifiers = modifiers,
        documentation = ItemDocumentation.NONE,
        variantSelectorsFactory = ApiVariantSelectors.MUTABLE_FACTORY,
    ),
    ParameterItem {

    override fun name(): String = name

    override fun publicName(): String? {
        // Java: Look for @ParameterName annotation
        val annotation = modifiers.findAnnotation(AnnotationItem::isParameterName)
        return annotation?.attributes?.firstOrNull()?.value?.value()?.toString()
    }

    override fun containingMethod(): MethodItem = containingMethod

    override fun isVarArgs(): Boolean = modifiers.isVarArg()

    override fun type(): TypeItem = type

    override fun setType(type: TypeItem) {
        this.type = type
    }

    override fun hasDefaultValue(): Boolean = isDefaultValueKnown()

    override fun isDefaultValueKnown(): Boolean {
        return modifiers.hasAnnotation(AnnotationItem::isDefaultValue)
    }

    override fun defaultValue(): String? {
        val annotation = modifiers.findAnnotation(AnnotationItem::isDefaultValue)
        return annotation?.attributes?.firstOrNull()?.value?.value()?.toString()
    }

    internal fun duplicate(
        containingMethod: MethodItem,
        typeVariableMap: TypeParameterBindings,
    ) =
        TurbineParameterItem(
            codebase,
            fileLocation,
            modifiers.duplicate(),
            name(),
            containingMethod,
            parameterIndex,
            type().convertType(typeVariableMap),
        )
}
