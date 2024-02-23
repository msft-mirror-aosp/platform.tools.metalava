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

package com.android.tools.metalava.model.psi

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.ModelOptions
import com.android.tools.metalava.model.noOpAnnotationManager
import com.android.tools.metalava.model.source.EnvironmentManager
import com.android.tools.metalava.model.source.SourceModelSuiteRunner
import com.android.tools.metalava.model.source.SourceSet
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.model.testsuite.InputFormat
import com.android.tools.metalava.model.testsuite.ModelProviderAwareTest.ModelProviderTestInfo
import com.android.tools.metalava.reporter.BasicReporter
import com.android.tools.metalava.reporter.Reporter
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

open class BasePsiTest :
    BaseModelTest(
        fixedModelProviderTestInfo =
            ModelProviderTestInfo(SourceModelSuiteRunner(), InputFormat.KOTLIN)
    ) {

    /** Project directory; initialized by [testCodebase] */
    protected lateinit var projectDir: File

    /**
     * Writer into which the output like error reports are written; initialized by [testCodebase]
     */
    private lateinit var outputWriter: StringWriter

    /** The contents of [outputWriter], cleaned up to remove any references to temporary files. */
    protected val output
        get() = cleanupString(outputWriter.toString(), projectDir)

    /** The [Reporter] that is used to intercept reports. */
    protected lateinit var reporter: Reporter

    fun testCodebase(
        vararg sources: TestFile,
        classPath: List<File> = emptyList(),
        isK2: Boolean = false,
        action: (Codebase) -> Unit,
    ) {
        testCodebase(sources.toList(), emptyList(), classPath, isK2, action)
    }

    fun testCodebase(
        sources: List<TestFile>,
        commonSources: List<TestFile>,
        classPath: List<File> = emptyList(),
        isK2: Boolean = false,
        action: (Codebase) -> Unit,
    ) {
        projectDir = temporaryFolder.newFolder()
        PsiEnvironmentManager().use { environmentManager ->
            outputWriter = StringWriter()
            reporter = BasicReporter(PrintWriter(outputWriter))
            val codebase =
                createTestCodebase(
                    environmentManager,
                    projectDir,
                    sources,
                    commonSources,
                    classPath,
                    reporter,
                    isK2,
                )
            action(codebase)
        }
    }

    private fun createTestCodebase(
        environmentManager: EnvironmentManager,
        directory: File,
        sources: List<TestFile>,
        commonSources: List<TestFile>,
        classPath: List<File>,
        reporter: Reporter,
        isK2: Boolean = false,
    ): Codebase {
        val (sourceDirectory, commonDirectory) =
            if (commonSources.isEmpty()) {
                directory to null
            } else {
                temporaryFolder.newFolder() to temporaryFolder.newFolder()
            }
        return environmentManager
            .createSourceParser(
                reporter,
                noOpAnnotationManager,
                modelOptions =
                    ModelOptions.build("test") { this[PsiModelOptions.useK2Uast] = isK2 },
            )
            .parseSources(
                createSourceSet(sources, sourceDirectory),
                createSourceSet(commonSources, commonDirectory),
                description = "Test Codebase",
                classPath = classPath,
            )
    }

    private fun createSourceSet(
        sources: List<TestFile>,
        sourceDirectory: File?,
    ): SourceSet {
        return SourceSet(
            sources.map { it.createFile(sourceDirectory) },
            listOfNotNull(sourceDirectory)
        )
    }
}
