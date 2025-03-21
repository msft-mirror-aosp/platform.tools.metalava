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

import com.android.tools.metalava.model.ClassResolver
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.DefaultTypeItem
import com.android.tools.metalava.model.TypeArgumentTypeItem
import com.android.tools.metalava.model.TypeModifiers

open class DefaultClassTypeItem(
    internal val classResolver: ClassResolver,
    modifiers: TypeModifiers,
    final override val qualifiedName: String,
    final override val arguments: List<TypeArgumentTypeItem>,
    final override val outerClassType: ClassTypeItem?,
) : ClassTypeItem, DefaultTypeItem(modifiers) {
    override val className: String = ClassTypeItem.computeClassName(qualifiedName)

    private val asClassCache by
        lazy(LazyThreadSafetyMode.NONE) { classResolver.resolveClass(qualifiedName) }

    override fun asClass() = asClassCache

    @Deprecated(
        "implementation detail of this class",
        replaceWith = ReplaceWith("substitute(modifiers, outerClassType, arguments)"),
    )
    override fun duplicate(
        modifiers: TypeModifiers,
        outerClassType: ClassTypeItem?,
        arguments: List<TypeArgumentTypeItem>
    ): ClassTypeItem {
        return DefaultClassTypeItem(
            classResolver,
            modifiers,
            qualifiedName,
            arguments,
            outerClassType
        )
    }
}
