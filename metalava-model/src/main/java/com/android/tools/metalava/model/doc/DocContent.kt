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

package com.android.tools.metalava.model.doc

import com.android.tools.metalava.model.ItemDocumentation

/**
 * An immutable block of content extracted from a documentation comment.
 *
 * @see DocContentOwner.docContent
 */
interface DocContent

/**
 * The owner of a block of content extracted from a documentation comment.
 *
 * @see ItemDocumentation.mainDescriptionOwner
 * @see ItemDocumentation.blockTagDescriptionOwner
 * @see ItemDocumentation.paramTagDescriptionOwner
 */
interface DocContentOwner {
    /**
     * The optional [DocContent], returns `null` if the underlying content is empty, i.e. has no
     * significant non-whitespace content .
     */
    val docContent: DocContent?

    // TODO(b/450228132): Add support for mutating the [docContent].
}
