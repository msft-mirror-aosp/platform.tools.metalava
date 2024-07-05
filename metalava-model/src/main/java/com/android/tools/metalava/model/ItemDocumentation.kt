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
interface ItemDocumentation : CharSequence {
    val text: String

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
    fun duplicate(): ItemDocumentation

    /** Work around javadoc cutting off the summary line after the first ". ". */
    fun workAroundJavaDocSummaryTruncationIssue() {}

    companion object {
        /**
         * A special [ItemDocumentation] that contains no documentation.
         *
         * Used where there is no documentation possible, e.g. text model, type parameters,
         * parameters.
         */
        val NONE: ItemDocumentation = EmptyItemDocumentation()

        /** Wrap a [String] in an [ItemDocumentation]. */
        fun String.toItemDocumentation(): ItemDocumentation = DefaultItemDocumentation(this)
    }

    /** An empty [ItemDocumentation] that can never contain any text. */
    private class EmptyItemDocumentation : ItemDocumentation {
        override val text
            get() = ""

        // This is ok to share as it is immutable.
        override fun duplicate() = this
    }
}

/**
 * Abstract [ItemDocumentation] into which functionality that is common to all models will be added.
 */
abstract class AbstractItemDocumentation : ItemDocumentation {

    /**
     * The mutable text contents of the documentation. This is abstract to allow the implementations
     * of this to optimize how it is accessed, e.g. initialize it lazily.
     */
    abstract override var text: String

    override fun workAroundJavaDocSummaryTruncationIssue() {
        // Work around javadoc cutting off the summary line after the first ". ".
        val firstDot = text.indexOf(".")
        if (firstDot > 0 && text.regionMatches(firstDot - 1, "e.g. ", 0, 5, false)) {
            text = text.substring(0, firstDot) + ".g.&nbsp;" + text.substring(firstDot + 4)
        }
    }
}

/** A default [ItemDocumentation] containing JavaDoc/KDoc. */
internal class DefaultItemDocumentation(override var text: String) : AbstractItemDocumentation() {

    override fun duplicate() = DefaultItemDocumentation(text)
}
