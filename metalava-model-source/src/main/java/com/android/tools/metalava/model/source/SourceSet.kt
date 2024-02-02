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

package com.android.tools.metalava.model.source

import com.android.tools.metalava.model.source.utils.extractRoots
import com.android.tools.metalava.model.source.utils.gatherSources
import com.android.tools.metalava.reporter.Reporter
import java.io.File

/**
 * An abstraction of source files and root directories.
 *
 * Those are always paired together or computed from one another.
 *
 * @param sources the list of source files
 * @param sourcePath a possibly empty list of root directories within which source files may be
 *   found.
 */
class SourceSet(val sources: List<File>, val sourcePath: List<File>) {

    val absoluteSources: List<File>
        get() {
            return sources.map { it.absoluteFile }
        }

    val absoluteSourcePaths: List<File>
        get() {
            return sourcePath.filter { it.path.isNotBlank() }.map { it.absoluteFile }
        }

    /** Creates a copy of [SourceSet], but with elements mapped with [File.getAbsoluteFile] */
    fun absoluteCopy(): SourceSet {
        return SourceSet(absoluteSources, absoluteSourcePaths)
    }

    /**
     * Creates a new instance of [SourceSet], adding in source roots implied by the source files in
     * the current [SourceSet]
     */
    fun extractRoots(reporter: Reporter): SourceSet {
        val sourceRoots = extractRoots(reporter, sources, sourcePath.toMutableList())
        return SourceSet(sources, sourceRoots)
    }

    companion object {
        fun empty(): SourceSet = SourceSet(emptyList(), emptyList())

        /** * Creates [SourceSet] from the given [sourcePath] */
        fun createFromSourcePath(reporter: Reporter, sourcePath: List<File>): SourceSet {
            val sources = gatherSources(reporter, sourcePath)
            return SourceSet(sources, sourcePath)
        }
    }
}
