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
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.PrimitiveTypeItem.Primitive
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeModifiers
import java.util.function.Predicate

sealed class TurbineTypeItem(
    open val codebase: Codebase,
    override val modifiers: TypeModifiers,
) : TypeItem {

    override fun asClass(): TurbineClassItem? = TODO("b/295800205")

    override fun convertType(replacementMap: Map<String, String>?, owner: Item?): TypeItem =
        TODO("b/295800205")

    override fun markRecent() = TODO("b/295800205")

    override fun scrubAnnotations() {
        TODO("b/295800205")
    }

    override fun toErasedTypeString(context: Item?): String {
        TODO("b/295800205")
    }

    override fun toTypeString(
        annotations: Boolean,
        erased: Boolean,
        kotlinStyleNulls: Boolean,
        context: Item?,
        filter: Predicate<Item>?,
    ): String = TODO("b/295800205")

    override fun typeArgumentClasses(): List<ClassItem> = TODO("b/295800205")
}

class TurbinePrimitiveTypeItem(
    override val codebase: Codebase,
    override val modifiers: TypeModifiers,
    override val kind: Primitive,
) : PrimitiveTypeItem, TurbineTypeItem(codebase, modifiers) {}
