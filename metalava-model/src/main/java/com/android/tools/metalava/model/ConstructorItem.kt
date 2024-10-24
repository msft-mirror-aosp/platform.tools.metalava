/*
 * Copyright (C) 2017 The Android Open Source Project
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

interface ConstructorItem : CallableItem {

    /** Override to specialize return type. */
    override fun returnType(): ClassTypeItem

    /** Override to specialize return type. */
    override fun type() = returnType()

    override fun accept(visitor: ItemVisitor) {
        visitor.visit(this)
    }

    @Deprecated(
        message =
            "There is no point in calling this method on ConstructorItem as it always returns true",
        ReplaceWith("")
    )
    override fun isConstructor(): Boolean = true

    /** Returns the internal name of the class, as seen in bytecode */
    override fun internalName(): String = "<init>"

    override fun findCorrespondingItemIn(
        codebase: Codebase,
        superMethods: Boolean,
        duplicate: Boolean,
    ) = containingClass().findCorrespondingItemIn(codebase)?.findConstructor(this)

    /** True if this is the primary constructor in Kotlin. */
    val isPrimary: Boolean

    /**
     * True if this is a [ConstructorItem] that was created implicitly by the compiler and so does
     * not have any corresponding source code.
     */
    fun isImplicitConstructor(): Boolean = false
}
