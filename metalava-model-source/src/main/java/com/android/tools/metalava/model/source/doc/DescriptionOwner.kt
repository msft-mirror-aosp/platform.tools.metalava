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

import com.android.tools.metalava.model.doc.DocContent
import com.android.tools.metalava.model.doc.DocContentOwner
import com.android.tools.metalava.model.source.javadoc.JavadocContent

/**
 * Base class for classes that own a [JavadocContent] description.
 *
 * @param descriptionSupplier Supplies a [JavadocContent] instance when requested. May produce it
 *   lazily.
 */
internal open class DescriptionOwner(
    protected val descriptionSupplier: ContentSupplier,
) : DocContentOwner {
    /**
     * Get the [JavadocContent] from [descriptionSupplier].
     *
     * The [descriptionSupplier] may need to do a lot of work to produce the [JavadocContent] so
     * this must only be accessed when absolutely necessary and only when processing actual API
     * documentation.
     */
    val description: JavadocContent?
        get() = descriptionSupplier.content

    override val docContent: DocContent?
        get() = description
}
