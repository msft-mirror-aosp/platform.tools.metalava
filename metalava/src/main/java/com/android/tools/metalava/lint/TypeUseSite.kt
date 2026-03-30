/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.tools.metalava.lint

import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.TypeItem

/** Sites where an [Item] can use a [TypeItem] */
enum class TypeUseSite(
    /** Text label that describes a [TypeUseSite]. */
    private val label: String,
    /** True for sites that are handled by the [ApiLint.legacyCheckType]. */
    val legacyCheckType: Boolean = false,
) {
    TYPE_PARAMETER(
        label = "Type parameter",
    ),
    SUPER_CLASS(
        label = "Super class",
    ),
    INTERFACE(
        label = "Implemented interface",
    ),
    THROWS(
        label = "Throws type",
    ),
    RETURN(
        label = "Return type",
        legacyCheckType = true,
    ),
    PARAMETER(
        label = "Parameter type",
        legacyCheckType = true,
    ),
    FIELD(
        label = "Field type",
        legacyCheckType = true,
    ),
    ;

    override fun toString() = label
}
