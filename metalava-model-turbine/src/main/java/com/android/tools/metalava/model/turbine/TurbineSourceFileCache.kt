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

package com.android.tools.metalava.model.turbine

import com.android.tools.metalava.model.item.DefaultCodebase
import com.google.turbine.diag.SourceFile
import com.google.turbine.tree.Tree.CompUnit

/** Caches the [TurbineSourceFile]s by their path. */
internal class TurbineSourceFileCache(private val codebase: DefaultCodebase) {

    /** Map from file path to the [TurbineSourceFile]. */
    private val turbineSourceFiles = mutableMapOf<String, TurbineSourceFile>()

    /**
     * Create a [TurbineSourceFile] for the specified [compUnit].
     *
     * This may be called multiple times for the same [compUnit] in which case it will return the
     * same [TurbineSourceFile]. It will throw an exception if two [CompUnit]s have the same path.
     */
    internal fun createTurbineSourceFile(compUnit: CompUnit): TurbineSourceFile {
        val path = compUnit.source().path()
        val existing = turbineSourceFiles[path]
        if (existing != null && existing.compUnit != compUnit) {
            error("duplicate source file found for $path")
        }
        return TurbineSourceFile(codebase, compUnit).also { turbineSourceFiles[path] = it }
    }

    /**
     * Get the [TurbineSourceFile] for a [SourceFile], failing if it could not be found.
     *
     * A [TurbineSourceFile] must be created by [createTurbineSourceFile] before calling this.
     */
    internal fun turbineSourceFile(sourceFile: SourceFile): TurbineSourceFile =
        turbineSourceFiles[sourceFile.path()]
            ?: error("unrecognized source file: ${sourceFile.path()}")
}
