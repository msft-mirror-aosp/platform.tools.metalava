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

package com.android.tools.metalava.model.text

import com.android.tools.metalava.model.TypeParameterList
import java.util.HashMap
import kotlin.math.min

/** Parses and caches types for a [codebase]. */
internal class TextTypeParser(val codebase: TextCodebase) {

    fun obtainTypeFromString(
        type: String,
        cl: TextClassItem,
        methodTypeParameterList: TypeParameterList
    ): TextTypeItem {
        if (TextTypeItem.isLikelyTypeParameter(type)) {
            val length = type.length
            var nameEnd = length
            for (i in 0 until length) {
                val c = type[i]
                if (c == '<' || c == '[' || c == '!' || c == '?') {
                    nameEnd = i
                    break
                }
            }
            val name =
                if (nameEnd == length) {
                    type
                } else {
                    type.substring(0, nameEnd)
                }

            val isMethodTypeVar = methodTypeParameterList.typeParameterNames().contains(name)
            val isClassTypeVar = cl.typeParameterList().typeParameterNames().contains(name)

            if (isMethodTypeVar || isClassTypeVar) {
                // Confirm that it's a type variable
                // If so, create type variable WITHOUT placing it into the
                // cache, since we can't cache these; they can have different
                // inherited bounds etc
                return TextTypeItem(codebase, type)
            }
        }

        return obtainTypeFromString(type)
    }

    // Copied from Converter:

    fun obtainTypeFromString(type: String): TextTypeItem {
        return mTypesFromString.obtain(type) as TextTypeItem
    }

    private val mTypesFromString =
        object : Cache(codebase) {
            override fun make(o: Any): Any {
                val name = o as String

                // Reverse effect of TypeItem.shortenTypes(...)
                if (implicitJavaLangType(name)) {
                    return TextTypeItem(codebase, "java.lang.$name")
                }

                return TextTypeItem(codebase, name)
            }

            private fun implicitJavaLangType(s: String): Boolean {
                if (s.length <= 1) {
                    return false // Usually a type variable
                }
                if (s[1] == '[') {
                    return false // Type variable plus array
                }

                val dotIndex = s.indexOf('.')
                val array = s.indexOf('[')
                val generics = s.indexOf('<')
                if (array == -1 && generics == -1) {
                    return dotIndex == -1 && !TextTypeItem.isPrimitive(s)
                }
                val typeEnd =
                    if (array != -1) {
                        if (generics != -1) {
                            min(array, generics)
                        } else {
                            array
                        }
                    } else {
                        generics
                    }

                // Allow dotted type in generic parameter, e.g. "Iterable<java.io.File>" -> return
                // true
                return (dotIndex == -1 || dotIndex > typeEnd) &&
                    !TextTypeItem.isPrimitive(s.substring(0, typeEnd).trim())
            }
        }

    private abstract class Cache(val codebase: TextCodebase) {

        protected var mCache = HashMap<Any, Any>()

        internal fun obtain(o: Any?): Any? {
            if (o == null) {
                return null
            }
            var r: Any? = mCache[o]
            if (r == null) {
                r = make(o)
                mCache[o] = r
            }
            return r
        }

        protected abstract fun make(o: Any): Any
    }
}
