/*
 * Copyright (C) 2026 The Android Open Source Project
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

import com.android.tools.metalava.model.ClassPathResolver
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.item.DefaultCodebase
import com.android.tools.metalava.reporter.Reporter
import java.io.File

abstract class AbstractSourceParser(protected val reporter: Reporter) : SourceParser {

    final override fun getClassPathResolver(classPath: List<File>): ClassPathResolver =
        loadCodebaseFromJars(
            classPath,
            "Codebase from classpath",
            includeKotlinInCodebase = true,
        )

    /** Load a [DefaultCodebase] from a set of [jars]. */
    protected open fun loadCodebaseFromJars(
        jars: List<File>,
        description: String,
        includeKotlinInCodebase: Boolean,
    ): DefaultCodebase {
        val inputs =
            SourceParser.Inputs(
                sourceSet = SourceSet.empty(),
                description = description,
                classPath = jars,
                includeKotlinInCodebase = includeKotlinInCodebase,
            )

        val codebase = parseSources(inputs) ?: error("Could not create codebase from $jars")

        return codebase as DefaultCodebase
    }

    /**
     * Override to ensure that [inputs] are correctly prepared for [processInputs].
     *
     * Preparation includes replacing [Inputs.sourceSet] with the result of calling
     * [SourceSet.extractRoots] on it, and making [Inputs.classPath], absolute files.
     */
    final override fun parseSources(inputs: SourceParser.Inputs): Codebase? {
        val absoluteInputs =
            inputs.copy(
                sourceSet = inputs.sourceSet.extractRoots(reporter),
                classPath = inputs.classPath.map { it.absoluteFile },
            )

        return processInputs(absoluteInputs)
    }

    /** Process the [inputs] to produce a [Codebase], if possible. */
    protected abstract fun processInputs(inputs: SourceParser.Inputs): Codebase?
}
