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
    private val constantValue: Any?,
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FieldItem) return false

        if (name() != other.name()) {
            return false
        }

        return containingClass() == other.containingClass()
    }

    override fun hashCode(): Int = name().hashCode()

    override fun type(): TypeItem = type

    override fun setType(type: TypeItem) {
        this.type = type
    }

    override fun initialValue(requireConstant: Boolean): Any? = constantValue

    override fun duplicate(targetContainingClass: ClassItem): TextFieldItem {
        val duplicated =
            TextFieldItem(
                codebase = codebase,
                fileLocation = fileLocation,
                modifiers = modifiers.duplicate(),
                name = name(),
                containingClass = targetContainingClass,
                type = type,
                isEnumConstant = isEnumConstant,
                constantValue = constantValue,
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

    override var inheritedFrom: ClassItem? = null

    override fun isEnumConstant(): Boolean = isEnumConstant
}
