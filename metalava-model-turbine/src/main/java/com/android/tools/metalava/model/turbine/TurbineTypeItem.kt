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

import com.android.tools.metalava.model.ArrayTypeItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.DefaultTypeItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.PrimitiveTypeItem.Primitive
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeModifiers
import com.android.tools.metalava.model.TypeParameterItem
import com.android.tools.metalava.model.VariableTypeItem
import com.android.tools.metalava.model.WildcardTypeItem
import com.google.turbine.binder.sym.TyVarSymbol
import java.util.function.Predicate

sealed class TurbineTypeItem(
    open val codebase: Codebase,
    override val modifiers: TypeModifiers,
) : DefaultTypeItem() {

    override fun toString(): String {
        return toTypeString()
    }

    override fun asClass(): TurbineClassItem? = TODO("b/295800205")

    override fun convertType(replacementMap: Map<String, String>?, owner: Item?): TypeItem =
        TODO("b/295800205")

    override fun markRecent() = TODO("b/295800205")

    override fun scrubAnnotations() {
        TODO("b/295800205")
    }

    override fun toTypeString(
        annotations: Boolean,
        kotlinStyleNulls: Boolean,
        context: Item?,
        filter: Predicate<Item>?,
    ): String {
        if (!annotations && !kotlinStyleNulls && filter == null) {
            return super.toTypeString(annotations, kotlinStyleNulls, context, filter)
        }

        TODO("b/295800205")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true

        return when (other) {
            is TypeItem -> TypeItem.equalsWithoutSpace(toTypeString(), other.toTypeString())
            else -> false
        }
    }

    override fun typeArgumentClasses(): List<ClassItem> = TODO("b/295800205")
}

class TurbinePrimitiveTypeItem(
    override val codebase: Codebase,
    override val modifiers: TypeModifiers,
    override val kind: Primitive,
) : PrimitiveTypeItem, TurbineTypeItem(codebase, modifiers)

class TurbineArrayTypeItem(
    override val codebase: Codebase,
    override val modifiers: TypeModifiers,
    override val componentType: TurbineTypeItem,
    override val isVarargs: Boolean,
) : ArrayTypeItem, TurbineTypeItem(codebase, modifiers)

class TurbineClassTypeItem(
    override val codebase: Codebase,
    override val modifiers: TypeModifiers,
    override val qualifiedName: String,
    override val parameters: List<TurbineTypeItem>,
    override val outerClassType: TurbineClassTypeItem?,
) : ClassTypeItem, TurbineTypeItem(codebase, modifiers) {
    override val className: String = ClassTypeItem.computeClassName(qualifiedName)
}

class TurbineVariableTypeItem(
    override val codebase: TurbineBasedCodebase,
    override val modifiers: TypeModifiers,
    private val symbol: TyVarSymbol
) : VariableTypeItem, TurbineTypeItem(codebase, modifiers) {
    override val name: String = symbol.name()
    override val asTypeParameter: TypeParameterItem by lazy { codebase.findTypeParameter(symbol) }
}

class TurbineWildcardTypeItem(
    override val codebase: TurbineBasedCodebase,
    override val modifiers: TypeModifiers,
    override val extendsBound: TurbineTypeItem?,
    override val superBound: TurbineTypeItem?,
) : WildcardTypeItem, TurbineTypeItem(codebase, modifiers)
