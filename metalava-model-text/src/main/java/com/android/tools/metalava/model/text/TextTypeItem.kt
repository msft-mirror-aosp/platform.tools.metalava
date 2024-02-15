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
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.DefaultTypeItem
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.ReferenceTypeItem
import com.android.tools.metalava.model.TypeArgumentTypeItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeModifiers
import com.android.tools.metalava.model.TypeParameterItem
import com.android.tools.metalava.model.VariableTypeItem
import com.android.tools.metalava.model.WildcardTypeItem

internal sealed class TextTypeItem(
    override val modifiers: TypeModifiers,
) : DefaultTypeItem() {}

/** A [PrimitiveTypeItem] parsed from a signature file. */
internal class TextPrimitiveTypeItem(
    override val kind: PrimitiveTypeItem.Primitive,
    modifiers: TypeModifiers
) : PrimitiveTypeItem, TextTypeItem(modifiers) {
    override fun duplicate(): PrimitiveTypeItem = TextPrimitiveTypeItem(kind, modifiers.duplicate())
}

/** An [ArrayTypeItem] parsed from a signature file. */
internal class TextArrayTypeItem(
    override val componentType: TypeItem,
    override val isVarargs: Boolean,
    modifiers: TypeModifiers
) : ArrayTypeItem, TextTypeItem(modifiers) {

    override fun duplicate(componentType: TypeItem): ArrayTypeItem {
        return TextArrayTypeItem(componentType, isVarargs, modifiers.duplicate())
    }
}

/** A [ClassTypeItem] parsed from a signature file. */
internal class TextClassTypeItem(
    private val codebase: Codebase,
    override val qualifiedName: String,
    override val arguments: List<TypeArgumentTypeItem>,
    override val outerClassType: ClassTypeItem?,
    modifiers: TypeModifiers
) : ClassTypeItem, TextTypeItem(modifiers) {
    override val className: String = ClassTypeItem.computeClassName(qualifiedName)

    private val asClassCache by
        lazy(LazyThreadSafetyMode.NONE) { codebase.resolveClass(qualifiedName) }

    override fun asClass() = asClassCache

    override fun duplicate(
        outerClass: ClassTypeItem?,
        arguments: List<TypeArgumentTypeItem>
    ): ClassTypeItem {
        return TextClassTypeItem(
            codebase,
            qualifiedName,
            arguments,
            outerClass,
            modifiers.duplicate()
        )
    }
}

/** A [VariableTypeItem] parsed from a signature file. */
internal class TextVariableTypeItem(
    override val name: String,
    override val asTypeParameter: TypeParameterItem,
    modifiers: TypeModifiers
) : VariableTypeItem, TextTypeItem(modifiers) {

    override fun duplicate(): VariableTypeItem {
        return TextVariableTypeItem(name, asTypeParameter, modifiers.duplicate())
    }
}

/** A [WildcardTypeItem] parsed from a signature file. */
internal class TextWildcardTypeItem(
    override val extendsBound: ReferenceTypeItem?,
    override val superBound: ReferenceTypeItem?,
    modifiers: TypeModifiers
) : WildcardTypeItem, TextTypeItem(modifiers) {

    override fun duplicate(
        extendsBound: ReferenceTypeItem?,
        superBound: ReferenceTypeItem?
    ): WildcardTypeItem {
        return TextWildcardTypeItem(extendsBound, superBound, modifiers.duplicate())
    }
}
