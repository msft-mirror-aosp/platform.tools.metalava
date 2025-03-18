/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.android.tools.metalava.model.ItemLanguage
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.reporter.FileLocation

open class DefaultPropertyItem(
    codebase: Codebase,
    fileLocation: FileLocation,
    itemLanguage: ItemLanguage,
    documentationFactory: ItemDocumentationFactory,
    variantSelectorsFactory: ApiVariantSelectorsFactory,
    modifiers: BaseModifierList,
    name: String,
    containingClass: ClassItem,
    private var type: TypeItem,
    override val getter: MethodItem?,
    override val setter: MethodItem?,
    override val constructorParameter: ParameterItem?,
    override val backingField: FieldItem?,
    override val receiver: TypeItem?,
    override val typeParameterList: TypeParameterList,
) :
    DefaultMemberItem(
        codebase,
        fileLocation,
        itemLanguage,
        modifiers,
        documentationFactory,
        variantSelectorsFactory,
        name,
        containingClass,
    ),
    PropertyItem {

    final override fun type(): TypeItem = type

    final override fun setType(type: TypeItem) {
        this.type = type
    }
}
