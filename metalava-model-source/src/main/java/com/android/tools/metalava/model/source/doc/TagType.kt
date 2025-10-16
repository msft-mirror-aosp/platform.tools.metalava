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

/**
 * Base type of all tag specific data.
 *
 * Provides support for adding tag specific behavior that can access the tag data.
 */
internal interface TagData

/** Provides tag type specific functionality for block and inline tags. */
internal abstract class TagType<D : TagData>(
    /**
     * The name of the type, as used in Javadoc, e.g. `param` for `@param p ...` block tags and
     * `link` for `{@link Class}` inline tags.
     */
    val name: String
) {
    /** This must be the [name] of the tag type. */
    override fun toString() = name
}

/** The default [TagType] used for all tags that do not have special behavior. */
internal class DefaultTagType(name: String) : TagType<TagData>(name)

/**
 * Collection of registered [TagType]s.
 *
 * Used below to intern block and inline tags.
 *
 * Although the set of tag types is not known at compile time it is safe to intern them globally as
 * the set of tag types that could be used in a specific invocation of Metalava is small. It will
 * consist of a fixed number of standard tag types and a small set of custom tags.
 */
internal open class BaseTagTypes {
    /**
     * Cache from [TagType.name] to [TagType].
     *
     * Populated on demand by [tagTypeOf].
     */
    private val tagTypes = mutableMapOf<String, TagType<*>>()

    /**
     * Get a [TagType] for [name].
     *
     * If no such [TagType] has been registered then creates a [DefaultTagType] and caches that.
     */
    fun tagTypeOf(name: String): TagType<*> {
        return tagTypes.computeIfAbsent(name, ::DefaultTagType)
    }
}

/** Collection of all the block [TagType]s that have been created. */
internal object BlockTagTypes : BaseTagTypes() {
    val DEPRECATED = tagTypeOf("deprecated")
    val HIDE = tagTypeOf("hide")
}

/** Collection of all the inline [TagType]s that have been created. */
internal object InlineTagTypes : BaseTagTypes()
