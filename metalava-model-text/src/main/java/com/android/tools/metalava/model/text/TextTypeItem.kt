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

import com.android.tools.metalava.model.ArrayTypeItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.DefaultTypeItem
import com.android.tools.metalava.model.JAVA_LANG_OBJECT
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.ReferenceTypeItem
import com.android.tools.metalava.model.TypeArgumentTypeItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeNullability
import com.android.tools.metalava.model.TypeParameterItem
import com.android.tools.metalava.model.VariableTypeItem
import com.android.tools.metalava.model.WildcardTypeItem

internal sealed class TextTypeItem(
    val codebase: TextCodebase,
    override val modifiers: TextTypeModifiers,
) : DefaultTypeItem(codebase) {

    internal abstract fun duplicate(withNullability: TypeNullability): TextTypeItem
}

/** A [PrimitiveTypeItem] parsed from a signature file. */
internal class TextPrimitiveTypeItem(
    codebase: TextCodebase,
    override val kind: PrimitiveTypeItem.Primitive,
    modifiers: TextTypeModifiers
) : PrimitiveTypeItem, TextTypeItem(codebase, modifiers) {
    override fun duplicate(withNullability: TypeNullability): TextTypeItem {
        return TextPrimitiveTypeItem(codebase, kind, modifiers.duplicate(withNullability))
    }

    // Text types are immutable, so the modifiers don't actually need to be duplicated.
    override fun duplicate(): PrimitiveTypeItem = this
}

/** An [ArrayTypeItem] parsed from a signature file. */
internal class TextArrayTypeItem(
    codebase: TextCodebase,
    override val componentType: TypeItem,
    override val isVarargs: Boolean,
    modifiers: TextTypeModifiers
) : ArrayTypeItem, TextTypeItem(codebase, modifiers) {
    override fun duplicate(withNullability: TypeNullability): TextTypeItem {
        return TextArrayTypeItem(
            codebase,
            componentType,
            isVarargs,
            modifiers.duplicate(withNullability)
        )
    }

    override fun duplicate(componentType: TypeItem): ArrayTypeItem {
        return TextArrayTypeItem(codebase, componentType, isVarargs, modifiers)
    }
}

/** A [ClassTypeItem] parsed from a signature file. */
internal class TextClassTypeItem(
    codebase: TextCodebase,
    override val qualifiedName: String,
    override val arguments: List<TypeArgumentTypeItem>,
    override val outerClassType: ClassTypeItem?,
    modifiers: TextTypeModifiers
) : ClassTypeItem, TextTypeItem(codebase, modifiers) {
    override val className: String = ClassTypeItem.computeClassName(qualifiedName)

    private val asClassCache by
        lazy(LazyThreadSafetyMode.NONE) { codebase.resolveClass(qualifiedName) }

    override fun asClass() = asClassCache

    override fun duplicate(withNullability: TypeNullability): TextTypeItem {
        return TextClassTypeItem(
            codebase,
            qualifiedName,
            arguments,
            outerClassType,
            modifiers.duplicate(withNullability)
        )
    }

    override fun duplicate(
        outerClass: ClassTypeItem?,
        arguments: List<TypeArgumentTypeItem>
    ): ClassTypeItem {
        return TextClassTypeItem(codebase, qualifiedName, arguments, outerClass, modifiers)
    }
}

/** A [VariableTypeItem] parsed from a signature file. */
internal class TextVariableTypeItem(
    codebase: TextCodebase,
    override val name: String,
    override val asTypeParameter: TypeParameterItem,
    modifiers: TextTypeModifiers
) : VariableTypeItem, TextTypeItem(codebase, modifiers) {

    override fun asClass(): ClassItem {
        return asTypeParameter.typeBounds().firstOrNull()?.asClass()
            ?: codebase.getOrCreateClass(JAVA_LANG_OBJECT)
    }

    override fun duplicate(withNullability: TypeNullability): TextTypeItem {
        return TextVariableTypeItem(
            codebase,
            name,
            asTypeParameter,
            modifiers.duplicate(withNullability)
        )
    }

    // Text types are immutable, so the modifiers don't actually need to be duplicated.
    override fun duplicate(): VariableTypeItem = this
}

/** A [WildcardTypeItem] parsed from a signature file. */
internal class TextWildcardTypeItem(
    codebase: TextCodebase,
    override val extendsBound: ReferenceTypeItem?,
    override val superBound: ReferenceTypeItem?,
    modifiers: TextTypeModifiers
) : WildcardTypeItem, TextTypeItem(codebase, modifiers) {
    override fun duplicate(withNullability: TypeNullability): TextTypeItem {
        return TextWildcardTypeItem(
            codebase,
            extendsBound,
            superBound,
            modifiers.duplicate(withNullability)
        )
    }

    override fun duplicate(
        extendsBound: ReferenceTypeItem?,
        superBound: ReferenceTypeItem?
    ): WildcardTypeItem {
        return TextWildcardTypeItem(codebase, extendsBound, superBound, modifiers)
    }
}
