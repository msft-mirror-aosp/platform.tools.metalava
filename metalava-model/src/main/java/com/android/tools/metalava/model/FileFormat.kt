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

package com.android.tools.metalava.model

import java.util.Locale

/** File formats that metalava can emit APIs to */
enum class FileFormat(
    val description: String,
    val version: String? = null,
    val conciseDefaultValues: Boolean = false,
) {
    V1("Doclava signature file", "1.0"),
    V2("Metalava signature file", "2.0"),
    V3("Metalava signature file", "3.0"),
    V4("Metalava signature file", "4.0", conciseDefaultValues = true);

    /** The value to use in a command line option. */
    val optionValue: String = name.lowercase(Locale.US)

    fun useKotlinStyleNulls(): Boolean {
        return this >= V3
    }

    fun header(): String? {
        val prefix = headerPrefix() ?: return null
        return prefix + version + "\n"
    }

    private fun headerPrefix(): String? {
        return when (this) {
            V1 -> null
            V2,
            V3,
            V4 -> "// Signature format: "
        }
    }

    companion object {
        /** The latest signature file version, equivalent to --format=latest */
        val latest = values().maxOrNull()!!
    }
}
