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

package com.android.tools.metalava.model

@MetalavaApi
interface TypeParameterItem : Item {

    /** The name of the type parameter. */
    fun simpleName(): String

    /** The [VariableTypeItem] representing the type of this type parameter. */
    override fun type(): VariableTypeItem

    @Deprecated(
        message = "Please use typeBounds() instead.",
        level = DeprecationLevel.ERROR,
        replaceWith = ReplaceWith("typeBounds().mapNotNull { it.asClass() }")
    )
    @MetalavaApi
    fun bounds(): List<ClassItem> = typeBounds().mapNotNull { it.asClass() }

    fun typeBounds(): List<TypeItem>

    fun isReified(): Boolean

    fun toSource(): String {
        return buildString {
            if (isReified()) {
                append("reified ")
            }
            append(simpleName())
            // If the only bound is Object, omit it because it is implied.
            if (
                typeBounds().isNotEmpty() && typeBounds().singleOrNull()?.isJavaLangObject() != true
            ) {
                append(" extends ")
                var first = true
                for (bound in typeBounds()) {
                    if (!first) {
                        append(" ")
                        append("&")
                        append(" ")
                    }
                    first = false
                    append(bound.toTypeString(spaceBetweenParameters = true))
                }
            }
        }
    }

    // Methods from [Item] that are not needed. They will be removed in a follow-up change.
    override fun parent() = error("Not needed for TypeParameterItem")

    override fun accept(visitor: ItemVisitor) = error("Not needed for TypeParameterItem")

    override fun containingPackage() = error("Not needed for TypeParameterItem")

    override fun containingClass() = error("Not needed for TypeParameterItem")

    override fun findCorrespondingItemIn(codebase: Codebase) =
        error("Not needed for TypeParameterItem")
}
