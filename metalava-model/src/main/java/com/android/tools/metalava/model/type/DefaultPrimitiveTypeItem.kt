/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.tools.metalava.model.type

import com.android.tools.metalava.model.DefaultValueClassTypeItem
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.PrimitiveTypeItem.Primitive
import com.android.tools.metalava.model.TypeModifiers

internal class DefaultPrimitiveTypeItem(
    modifiers: TypeModifiers,
    override val kind: Primitive,
    isValueClassType: Boolean = false,
) : PrimitiveTypeItem, DefaultValueClassTypeItem(modifiers, isValueClassType) {

    override fun substitute(modifiers: TypeModifiers) =
        if (modifiers !== this.modifiers)
            DefaultPrimitiveTypeItem(modifiers, kind, isValueClassType)
        else this

    override fun equalsImpl(other: DefaultValueClassTypeItem): Boolean {
        if (other !is PrimitiveTypeItem) return false
        return kind == other.kind
    }

    override fun hashCodeImpl(): Int {
        return kind.hashCode()
    }
}
