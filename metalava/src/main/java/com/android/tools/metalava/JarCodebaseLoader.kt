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

package com.android.tools.metalava

import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.source.SourceParser
import com.android.tools.metalava.reporter.Reporter
import java.io.File

/** Provides support for loading [Codebase]s from jar files. */
sealed interface JarCodebaseLoader {

    /** Load a [Codebase] from a jar file. */
    fun loadFromJarFile(
        apiJar: File,
        apiAnalyzerConfig: ApiAnalyzer.Config = ApiAnalyzer.Config(),
    ): Codebase

    companion object {
        /** Create an instance fo [JarCodebaseLoader] from an existing [SourceParser]. */
        fun createForSourceParser(
            progressTracker: ProgressTracker,
            reporter: Reporter,
            sourceParser: SourceParser,
        ): JarCodebaseLoader {
            return FromSourceParser(progressTracker, reporter, sourceParser)
        }
    }

    /** A [JarCodebaseLoader] created from an existing [SourceParser]. */
    private class FromSourceParser(
        private val progressTracker: ProgressTracker,
        private val reporter: Reporter,
        private val sourceParser: SourceParser,
    ) : JarCodebaseLoader {
        override fun loadFromJarFile(
            apiJar: File,
            apiAnalyzerConfig: ApiAnalyzer.Config,
        ): Codebase {
            progressTracker.progress("Processing jar file: ")

            val apiPredicateConfig = apiAnalyzerConfig.apiPredicateConfig
            val apiEmit =
                ApiPredicate(
                    config = apiPredicateConfig.copy(ignoreShown = true),
                )
            val apiReference = apiEmit

            val codebase = sourceParser.loadFromJar(apiJar)
            val analyzer = ApiAnalyzer(sourceParser, codebase, reporter, apiAnalyzerConfig)
            analyzer.mergeExternalInclusionAnnotations()
            analyzer.computeApi()
            analyzer.mergeExternalQualifierAnnotations()
            analyzer.generateInheritedStubs(apiEmit, apiReference)
            return codebase
        }
    }
}
