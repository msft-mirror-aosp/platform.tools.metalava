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

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.reporter.FileLocation

internal class TurbineFieldItem(
    codebase: TurbineBasedCodebase,
    fileLocation: FileLocation,
    private val name: String,
    containingClass: ClassItem,
    private var type: TypeItem,
    modifiers: DefaultModifierList,
    documentation: String,
    private val isEnumConstant: Boolean,
    private val fieldValue: TurbineFieldValue?,
) :
    TurbineMemberItem(codebase, fileLocation, modifiers, documentation, containingClass),
    FieldItem {

    override var inheritedFrom: ClassItem? = null

    override fun name(): String = name

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        return other is FieldItem &&
            name() == other.name() &&
            containingClass() == other.containingClass()
    }

    override fun hashCode(): Int = name().hashCode()

    override fun type(): TypeItem = type

    override fun setType(type: TypeItem) {
        this.type = type
    }

    override fun duplicate(targetContainingClass: ClassItem): FieldItem {
        val duplicated =
            TurbineFieldItem(
                codebase,
                fileLocation,
                name(),
                targetContainingClass,
                type,
                modifiers.duplicate(),
                documentation,
                isEnumConstant,
                fieldValue,
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

/** Provides access to the initial values of a field. */
class TurbineFieldValue(
    private var initialValueWithRequiredConstant: Any?,
    private var initialValueWithoutRequiredConstant: Any?,
) {

    fun initialValue(requireConstant: Boolean) =
        if (requireConstant) initialValueWithRequiredConstant
        else initialValueWithoutRequiredConstant
}
