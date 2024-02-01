/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterItem

internal class TextTypeParameterItem(
    codebase: TextCodebase,
    private val name: String,
    private val isReified: Boolean,
) :
    TextItem(
        codebase = codebase,
        position = SourcePositionInfo.UNKNOWN,
        modifiers = DefaultModifierList(codebase, DefaultModifierList.PUBLIC),
    ),
    TypeParameterItem {

    private var owner: TypeParameterListOwner? = null

    lateinit var bounds: List<TypeItem>

    override fun name(): String {
        return name
    }

    override fun toString() =
        if (bounds.isEmpty() && !isReified) name
        else
            buildString {
                if (isReified) append("reified ")
                append(name)
                if (bounds.isNotEmpty()) {
                    append(" extends ")
                    bounds.joinTo(this, " & ")
                }
            }

    override fun type(): TextVariableTypeItem {
        return TextVariableTypeItem(
            codebase,
            name,
            this,
            TextTypeModifiers.create(codebase, emptyList(), null)
        )
    }

    override fun typeBounds(): List<TypeItem> = bounds

    override fun isReified(): Boolean = isReified

    internal fun setOwner(newOwner: TypeParameterListOwner) {
        owner = newOwner
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TypeParameterItem) return false

        return name == other.name()
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    companion object {

        /**
         * Create a partially initialized [TextTypeParameterItem].
         *
         * This extracts the [isReified] and [name] from the [typeParameterString] and creates a
         * [TextTypeParameterItem] with those properties initialized but the [bounds] is not.
         *
         * This must ONLY be used by [TextTypeParameterList.create] as that will complete the
         * initialization of the [bounds] property.
         */
        fun create(
            codebase: TextCodebase,
            typeParameterString: String,
        ): TextTypeParameterItem {
            val length = typeParameterString.length
            var nameEnd = length

            val isReified = typeParameterString.startsWith("reified ")
            val nameStart =
                if (isReified) {
                    8 // "reified ".length
                } else {
                    0
                }

            for (i in nameStart until length) {
                val c = typeParameterString[i]
                if (!Character.isJavaIdentifierPart(c)) {
                    nameEnd = i
                    break
                }
            }
            val name = typeParameterString.substring(nameStart, nameEnd)

            return TextTypeParameterItem(
                codebase = codebase,
                name = name,
                isReified = isReified,
            )
        }

        /**
         * Extracts the bounds string list from the [typeParameterString].
         *
         * Given `T extends a.B & b.C<? super T>` this will return a list of `a.B` and `b.C<? super
         * T>`.
         */
        fun extractTypeParameterBoundsStringList(typeParameterString: String?): List<String> {
            val s = typeParameterString ?: return emptyList()
            val index = s.indexOf("extends ")
            if (index == -1) {
                return emptyList()
            }
            val list = mutableListOf<String>()
            var angleBracketBalance = 0
            var start = index + "extends ".length
            val length = s.length
            for (i in start until length) {
                val c = s[i]
                if (c == '&' && angleBracketBalance == 0) {
                    addNonBlankStringToList(list, typeParameterString, start, i)
                    start = i + 1
                } else if (c == '<') {
                    angleBracketBalance++
                } else if (c == '>') {
                    angleBracketBalance--
                    if (angleBracketBalance == 0) {
                        addNonBlankStringToList(list, typeParameterString, start, i + 1)
                        start = i + 1
                    }
                }
            }
            if (start < length) {
                addNonBlankStringToList(list, typeParameterString, start, length)
            }
            return list
        }

        private fun addNonBlankStringToList(
            list: MutableList<String>,
            s: String,
            from: Int,
            to: Int
        ) {
            val element = s.substring(from, to).trim()
            if (element.isNotEmpty()) list.add(element)
        }
    }
}
