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
import com.android.tools.metalava.model.TypeParameterListOwner

class TextTypeParameterList(
    val codebase: TextCodebase,
    var owner: TypeParameterListOwner?,
    private val typeListString: String
) : TypeParameterList {
    private var typeParameters: List<TypeParameterItem>? = null
    private var typeParameterNames: List<String>? = null

    override fun toString(): String = typeListString

    override fun typeParameterNames(): List<String> {
        if (typeParameterNames == null) {
            //     TODO: Delete this method now that I'm doing it differently:
            // typeParameterNames(typeListString)
            val typeParameters = typeParameters()
            val names = ArrayList<String>(typeParameters.size)
            for (parameter in typeParameters) {
                names.add(parameter.simpleName())
            }
            typeParameterNames = names
        }
        return typeParameterNames!!
    }

    override fun typeParameters(): List<TypeParameterItem> {
        if (typeParameters == null) {
            val strings = TextTypeParser.typeParameterStrings(typeListString)
            val list = ArrayList<TypeParameterItem>(strings.size)
            strings.mapTo(list) { TextTypeParameterItem.create(codebase, owner, it) }
            typeParameters = list
        }
        return typeParameters!!
    }

    companion object {
        fun create(
            codebase: TextCodebase,
            owner: TypeParameterListOwner?,
            typeListString: String
        ): TypeParameterList {
            return TextTypeParameterList(codebase, owner, typeListString)
        }
    }
}
