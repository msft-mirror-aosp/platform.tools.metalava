/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.tools.metalava.model.source

import com.android.tools.metalava.model.ClassResolver
import com.android.tools.metalava.model.Codebase
import java.io.File

/** Provides support for creating [Codebase] related objects from source files (including jars). */
interface SourceParser {

    /**
     * Get a [ClassResolver] instance that will resolve classes provided by jars on the [classPath].
     *
     * @param classPath a list of jar [File]s.
     */
    fun getClassResolver(classPath: List<File>): ClassResolver

    /**
     * Parse a set of sources into a [SourceCodebase].
     *
     * @param sources the list of source files.
     * @param description the description to use for [Codebase.description].
     * @param sourcePath a possibly empty list of root directories within which sources files may be
     *   found.
     * @param classPath the possibly empty list of jar files which may provide additional classes
     *   referenced by the sources.
     */
    fun parseSources(
        sources: List<File>,
        description: String,
        sourcePath: List<File>,
        classPath: List<File>,
    ): SourceCodebase

    /**
     * Load a [SourceCodebase] from a single jar.
     *
     * @param apiJar the jar file from which the [SourceCodebase] will be loaded.
     * @param preFiltered true if the jar file contains classes which have already been filtered to
     *   include only those classes that form an API surface, e.g. a stubs jar file.
     */
    fun loadFromJar(apiJar: File, preFiltered: Boolean): SourceCodebase
}
