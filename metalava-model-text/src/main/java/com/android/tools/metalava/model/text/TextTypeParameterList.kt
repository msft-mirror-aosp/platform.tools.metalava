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

import com.android.tools.metalava.model.TypeParameterItem
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.text.TextTypeParameterItem.Companion.extractTypeParameterBoundsStringList

internal class TextTypeParameterList(
    val codebase: TextCodebase,
    private var owner: TypeParameterListOwner?,
    private val typeParameters: List<TextTypeParameterItem>,
) : TypeParameterList {
    override fun toString() = typeParameters.joinToString(prefix = "<", postfix = ">")

    override fun typeParameters(): List<TypeParameterItem> {
        return typeParameters
    }

    internal fun setOwner(newOwner: TypeParameterListOwner) {
        owner = newOwner
        typeParameters.forEach { it.setOwner(newOwner) }
    }

    companion object {
        /**
         * Creates a [TextTypeParameterList] without a set owner, for type parameters created before
         * their owners are. The owner should be set after it is created.
         *
         * The [typeParameterListString] should be the string representation of a list of type
         * parameters, like "<A>" or "<A, B extends java.lang.String, C>".
         */
        fun create(
            codebase: TextCodebase,
            enclosingTypeParameterScope: TypeParameterScope,
            typeParameterListString: String,
        ): TypeParameterList {
            // A type parameter list can contain cycles between its type parameters, e.g.
            //     class Node<L extends Node<L, R>, R extends Node<L, R>>
            // Parsing that requires a multi-stage approach.
            // 1. Separate the list into a mapping from `TextTypeParameterItem` that have not yet
            //    had their `bounds` property initialized to the bounds string list.
            // 2. Create a nested scope of the enclosing scope which includes the type parameters.
            //    That will allow references between them to be resolved.
            // 3. Completing the initialization by converting each bounds string into a TypeItem.

            // Split the type parameter list string into a list of strings, one for each type
            // parameter.
            val typeParameterStrings = TextTypeParser.typeParameterStrings(typeParameterListString)

            // Creating a mapping from a `TextTypeParameterItem` to the bounds string list.
            val itemToBoundsList =
                typeParameterStrings.associateBy({ TextTypeParameterItem.create(codebase, it) }) {
                    extractTypeParameterBoundsStringList(it)
                }

            // Extract the `TextTypeParameterItem`s into a list and then use that to construct a
            // scope that can be used to resolve the type parameters, including self references
            // between the ones in this list.
            val typeParameters = itemToBoundsList.keys.toList()
            val scope = enclosingTypeParameterScope.nestedScope(typeParameters)

            // Complete the initialization of the `TextTypeParameterItem`s by converting each bounds
            // string into a `TypeItem`.
            for ((typeParameterItem, boundsStringList) in itemToBoundsList) {
                typeParameterItem.bounds =
                    boundsStringList.map { codebase.typeResolver.obtainTypeFromString(it, scope) }
            }

            return TextTypeParameterList(codebase, owner = null, typeParameters)
        }
    }
}
