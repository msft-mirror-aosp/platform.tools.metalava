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

/**
 * A block tag section of [DocComment.blockTagSections].
 *
 * Looks like:
 * ```
 *     * @<tag-type> <description>
 * ```
 */
internal interface BlockTagSection {
    /** The type of the block tag. */
    val tagType: String

    /** The description of the block tag. */
    val description: JavadocContent?
}

internal class DefaultBlockTagSection(
    override val tagType: String,
    descriptionSupplier: ContentSupplier,
) : DescriptionOwner(descriptionSupplier), BlockTagSection {

    override fun toString() = buildString {
        append("@")
        append(tagType)
        append(" ")
        // Use descriptionSupplier's toString not description's as accessing the latter changes the
        // state of this which is not recommended in toString() methods that may be used for
        // debugging as that can change the behavior. It also requires lots of work and could result
        // in performance degradation while debugging which can also affect behavior.
        append(descriptionSupplier)
    }
}
