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

import com.android.tools.metalava.model.ArrayTypeItem
import com.android.tools.metalava.model.DefaultTypeItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeModifiers

class DefaultArrayTypeItem(
    modifiers: TypeModifiers,
    override val componentType: TypeItem,
    override val isVarargs: Boolean,
) : ArrayTypeItem, DefaultTypeItem(modifiers) {
    @Deprecated(
        "implementation detail of this class",
        replaceWith = ReplaceWith("substitute(modifiers, componentType)"),
    )
    override fun duplicate(modifiers: TypeModifiers, componentType: TypeItem): ArrayTypeItem {
        return DefaultArrayTypeItem(modifiers, componentType, isVarargs)
    }
}
