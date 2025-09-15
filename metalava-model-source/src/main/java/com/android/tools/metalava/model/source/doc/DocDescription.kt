/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.tools.metalava.model.source.doc

import com.android.tools.metalava.model.source.javadoc.JavadocContent
import com.android.tools.metalava.model.source.javadoc.JavadocParser

/**
 * A [DocComment] description block.
 *
 * This represents a block of text and inline tags in a [DocComment]. It can either be the main
 * description for the item or the description of a block tag in the item.
 */
interface DocDescription {
    companion object {
        /** An empty [DocDescription]. */
        val EMPTY: DocDescription = EmptyDocDescription()
    }
}

internal class EmptyDocDescription : DocDescription {
    override fun toString() = "<<>>"
}

/**
 * The default [DocDescription] whose content is a subsequence of [text] starting from
 * [startInclusive] and ending at [endExclusive].
 */
internal class DefaultDocDescription(
    private val text: String,
    private val startInclusive: Int,
    private val endExclusive: Int
) : DocDescription {

    private lateinit var _content: JavadocContent

    val content: JavadocContent
        get() {
            if (!::_content.isInitialized) {
                _content = JavadocParser.parse(text, startInclusive, endExclusive)
            }
            return _content
        }

    override fun toString() = buildString {
        append("<<")
        // Ignore any whitespace at the end of the description.
        val end = text.skipBackwardsOverTrailingWhitespace(endExclusive - 1) + 1
        for (i in startInclusive until end) {
            val c = text[i]
            if (c == '\n') append("\\n") else append(c)
        }
        append(">>")
    }
}
