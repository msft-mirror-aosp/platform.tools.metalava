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

package com.android.tools.metalava.model

/**
 * The documentation associated with an [Item].
 *
 * This implements [CharSequence] to simplify migration.
 */
class ItemDocumentation private constructor(val text: String) : CharSequence {
    override val length
        get() = text.length

    override fun get(index: Int) = text.get(index)

    override fun subSequence(startIndex: Int, endIndex: Int) =
        text.subSequence(startIndex, endIndex)

    /**
     * Return a duplicate of this instance.
     *
     * [ItemDocumentation] instances can be mutable, and if they are then they must not be shared.
     */
    fun duplicate(): ItemDocumentation = this

    companion object {
        /**
         * A special [ItemDocumentation] that contains no documentation.
         *
         * Used where there is no documentation possible, e.g. text model, type parameters,
         * parameters.
         */
        val NONE: ItemDocumentation = ItemDocumentation("")

        /** Wrap a [String] in an [ItemDocumentation]. */
        fun String.toItemDocumentation(): ItemDocumentation = ItemDocumentation(this)
    }
}
