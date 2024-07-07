/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.metalava.model.text

import com.android.tools.metalava.model.ApiVariantSelectors
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.DefaultCodebase
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.ItemDocumentation
import com.android.tools.metalava.model.ItemLanguage
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.item.DefaultMemberItem
import com.android.tools.metalava.reporter.FileLocation

internal class TextFieldItem(
    codebase: DefaultCodebase,
    fileLocation: FileLocation,
    modifiers: DefaultModifierList,
    name: String,
    containingClass: ClassItem,
    private var type: TypeItem,
    private val isEnumConstant: Boolean,
    private val fieldValue: TextFieldValue? = null,
) :
    DefaultMemberItem(
        codebase = codebase,
        fileLocation = fileLocation,
        itemLanguage = ItemLanguage.UNKNOWN,
        modifiers = modifiers,
        documentation = ItemDocumentation.NONE,
        variantSelectorsFactory = ApiVariantSelectors.IMMUTABLE_FACTORY,
        name = name,
        containingClass = containingClass,
    ),
    FieldItem {

    override var inheritedFrom: ClassItem? = null

    override fun equals(other: Any?) = equalsToItem(other)

    override fun hashCode() = hashCodeForItem()

    override fun type(): TypeItem = type

    override fun setType(type: TypeItem) {
        this.type = type
    }

    override fun duplicate(targetContainingClass: ClassItem): FieldItem {
        val duplicated =
            TextFieldItem(
                codebase = codebase,
                fileLocation = fileLocation,
                modifiers = modifiers.duplicate(),
                name = name(),
                containingClass = targetContainingClass,
                type = type,
                isEnumConstant = isEnumConstant,
                fieldValue = fieldValue,
            )
        duplicated.inheritedFrom = containingClass()

        // Preserve flags that may have been inherited (propagated) from surrounding packages
        if (targetContainingClass.hidden) {
            duplicated.hidden = true
        }
        if (targetContainingClass.removed) {
            duplicated.removed = true
        }
        if (targetContainingClass.docOnly) {
            duplicated.docOnly = true
        }

        return duplicated
    }

    override fun initialValue(requireConstant: Boolean) = fieldValue?.initialValue(requireConstant)

    override fun isEnumConstant(): Boolean = isEnumConstant
}
