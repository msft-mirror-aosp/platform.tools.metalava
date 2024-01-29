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

import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.ItemVisitor
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterItem
import com.google.turbine.binder.sym.TyVarSymbol

internal class TurbineTypeParameterItem(
    codebase: TurbineBasedCodebase,
    modifiers: TurbineModifierItem,
    internal val symbol: TyVarSymbol,
    private val name: String = symbol.name(),
    private val bounds: List<TypeItem>,
) :
    TurbineItem(
        codebase,
        modifiers,
        "",
    ),
    TypeParameterItem {

    override fun simpleName() = name

    // Java does not supports reified generics
    override fun isReified(): Boolean = false

    override fun typeBounds(): List<TypeItem> = bounds

    override fun toType(): TurbineTypeItem {
        return TurbineVariableTypeItem(codebase, TurbineTypeModifiers(emptyList()), symbol)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TypeParameterItem) return false

        return name == other.simpleName()
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    // Methods from [Item] that are not needed. They will be removed in a follow-up change.
    override fun type() = error("Not needed for TypeParameterItem")

    override fun parent() = error("Not needed for TypeParameterItem")

    override fun accept(visitor: ItemVisitor) = error("Not needed for TypeParameterItem")

    override fun containingPackage() = error("Not needed for TypeParameterItem")

    override fun containingClass() = error("Not needed for TypeParameterItem")

    override fun findCorrespondingItemIn(codebase: Codebase) =
        error("Not needed for TypeParameterItem")
}
