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

package com.android.tools.metalava.model.source.javadoc

import com.android.tools.metalava.model.source.doc.TagData
import com.android.tools.metalava.model.source.doc.TagType

/**
 * Uses [tagType] to extract [TagData] from [JavadocContent].
 *
 * Finds the [JavadocText], if any, at the beginning of the [JavadocContent] and calls
 * [TagType.extractData] on its [JavadocText.contents] property.
 *
 * @see [extractTagData].
 */
internal class TagDataExtractor(
    private val tagType: TagType<*>,
) : JavadocContentVisitor {

    private var tagData: TagData? = null

    /**
     * Extract the [TagData], if any, from [JavadocContent].
     *
     * @return the [TagData] or `null` if it could not be found, e.g. because [TagType.extractData]
     *   returned `null` or [JavadocContent] did not start with [JavadocText].
     */
    fun extractTagData(content: JavadocContent): TagData? {
        content.accept(this)
        return tagData
    }

    /** Extract the data from the first item in the [JavadocContentList.contents]. */
    override fun visit(list: JavadocContentList) {
        // Can only extract data from the start of a list.
        val first = list.contents[0]
        first.accept(this)
    }

    /** A [JavadocInlineTag] cannot have data extracted so do nothing. */
    override fun visit(inlineTag: JavadocInlineTag) {
        // Nothing to do as cannot extract data from an inline tag.
    }

    /**
     * Calls [TagType.extractData] on [JavadocText.contents].
     *
     * This will only be called for a [JavadocText] that is at the start of the [JavadocContent]
     * passed into [extractTagData].
     */
    override fun visit(text: JavadocText) {
        val contents = text.contents
        tagData = tagType.extractData(contents)
    }
}

/** Extract [tagType]'s [TagData], if any, from this using [TagType.extractData]. */
internal fun JavadocContent.extractTagDataForTagType(
    tagType: TagType<*>,
) = TagDataExtractor(tagType).extractTagData(this)
