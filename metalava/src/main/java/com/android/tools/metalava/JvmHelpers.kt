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

package com.android.tools.metalava

import com.android.tools.lint.detector.api.ClassContext
import com.android.tools.metalava.model.TypeItem

// Contains extension functions to access JVM representations of the model.

/**
 * Returns the internal name of the type, as seen in bytecode.
 *
 * TODO(b/111253910): Look into moving this back to [TypeItem]. This was moved here because
 *   [toSlashFormat] depends on the lint class [ClassContext] and the model package cannot depend on
 *   lint classes. The proper type support can almost implement this without using [ClassContext] in
 *   which case it might make sense to move this back to [TypeItem]. One issue with
 *   [ClassContext.getInternalName] is that it uses a heuristic to determine whether something is a
 *   class or a package. Proper support for handling types could certainly do a better job.
 */
fun TypeItem.internalName(): String {
    // Default implementation; PSI subclass is more accurate
    return toSlashFormat(toErasedTypeString())
}

// Copied from doclava1
internal fun toSlashFormat(typeName: String): String {
    var name = typeName
    var dimension = ""
    while (name.endsWith("[]")) {
        dimension += "["
        name = name.substring(0, name.length - 2)
    }

    val base: String
    base =
        when (name) {
            "void" -> "V"
            "byte" -> "B"
            "boolean" -> "Z"
            "char" -> "C"
            "short" -> "S"
            "int" -> "I"
            "long" -> "J"
            "float" -> "F"
            "double" -> "D"
            else -> "L" + getInternalName(name) + ";"
        }

    return dimension + base
}

/**
 * Computes the internal class name of the given fully qualified class name. For example, it
 * converts foo.bar.Foo.Bar into foo/bar/Foo$Bar
 *
 * @param qualifiedName the fully qualified class name
 * @return the internal class name
 */
private fun getInternalName(qualifiedName: String): String {
    if (qualifiedName.indexOf('.') == -1) {
        return qualifiedName
    }

    // If class name contains $, it's not an ambiguous inner class name.
    if (qualifiedName.indexOf('$') != -1) {
        return qualifiedName.replace('.', '/')
    }
    // Let's assume that components that start with Caps are class names.
    return buildString {
        var prev: String? = null
        for (part in qualifiedName.split(".")) {
            if (!prev.isNullOrEmpty()) {
                if (Character.isUpperCase(prev[0])) {
                    append('$')
                } else {
                    append('/')
                }
            }
            append(part)
            prev = part
        }
    }
}
