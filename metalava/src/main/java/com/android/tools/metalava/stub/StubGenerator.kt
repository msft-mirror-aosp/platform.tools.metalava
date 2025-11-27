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

package com.android.tools.metalava.stub

import com.android.tools.metalava.MarkPackagesAsRecent
import com.android.tools.metalava.NullnessMigration
import com.android.tools.metalava.Options
import com.android.tools.metalava.PROGRAM_NAME
import com.android.tools.metalava.ProgressTracker
import com.android.tools.metalava.SignatureFileCache
import com.android.tools.metalava.cli.common.ExecutionEnvironment
import com.android.tools.metalava.cli.common.PreviouslyReleasedApi
import com.android.tools.metalava.doc.DocAnalyzer
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.CodebaseFragment
import com.android.tools.metalava.model.FilterPredicate
import com.android.tools.metalava.model.PackageFilter
import com.android.tools.metalava.model.visitors.ApiPredicate
import com.android.tools.metalava.reporter.Reporter
import com.google.common.base.Stopwatch
import java.io.File
import java.util.concurrent.TimeUnit.SECONDS

/** Generates stubs from [codebase]. */
internal class StubGenerator
private constructor(
    private val options: Options,
    private val codebase: Codebase,
    private val progressTracker: ProgressTracker,
    private val executionEnvironment: ExecutionEnvironment,
    private val reporter: Reporter,
    private val signatureFileCache: SignatureFileCache,
) {
    /** Generate the stubs. */
    private fun generateStubs() {
        // Use information from various sources to enhance the documentation, if required.
        enhanceCodebaseDocumentationFromOptions(
            options,
            codebase,
            progressTracker,
            executionEnvironment,
            reporter,
        )

        // Generate the documentation stubs *before* we migrate nullness information.
        options.docStubsDir?.let { stubDir ->
            createStubFiles(
                progressTracker,
                options,
                stubDir,
                codebase,
                isDocStubs = true,
            )
        }

        // Convert nullability annotations to warning nullability annotations, if needed.
        convertToWarningNullabilityAnnotations(
            codebase,
            options.migrateNullsFrom,
            options.forceConvertToWarningNullabilityAnnotations,
            signatureFileCache
        )

        // Now that we've migrated nullness information we can proceed to write non-doc stubs, if
        // any.
        options.stubsDir?.let { stubDir ->
            createStubFiles(
                progressTracker,
                options,
                stubDir,
                codebase,
                isDocStubs = false,
            )
        }
    }

    /** Depending on option flags, enhance codebase documentation */
    private fun enhanceCodebaseDocumentationFromOptions(
        options: Options,
        codebase: Codebase,
        progressTracker: ProgressTracker,
        executionEnvironment: ExecutionEnvironment,
        reporter: Reporter,
    ) {
        if (options.docStubsDir == null && !options.enhanceDocumentation) return
        if (!codebase.supportsDocumentation()) {
            error("Codebase does not support documentation, so it cannot be enhanced.")
        }

        progressTracker.progress("Enhancing docs: ")
        val docAnalyzer =
            DocAnalyzer(
                executionEnvironment,
                codebase,
                reporter,
                options.apiVersionLabelProvider,
                options.includeApiLevelInDocumentation,
                options.apiPredicateConfig,
            )
        docAnalyzer.enhance()
        val applyApiLevelsXmlFile = options.applyApiLevelsXmlFile
        if (applyApiLevelsXmlFile != null) {
            progressTracker.progress("Applying API levels")
            docAnalyzer.applyApiVersions(applyApiLevelsXmlFile)
        }
    }

    private fun createStubFiles(
        progressTracker: ProgressTracker,
        options: Options,
        stubDir: File,
        codebase: Codebase,
        isDocStubs: Boolean,
    ) {
        if (isDocStubs) {
            progressTracker.progress("Generating documentation stub files: ")
        } else {
            progressTracker.progress("Generating stub files: ")
        }

        val localTimer = Stopwatch.createStarted()

        var codebaseFragment =
            CodebaseFragment.create(codebase) { delegate ->
                createFilteringVisitorForStubs(
                    delegate = delegate,
                    isDocStubs = isDocStubs,
                    preFiltered = codebase.preFiltered,
                    apiPredicateConfig = options.apiPredicateConfig,
                )
            }

        // If reverting some changes then create a snapshot that combines the items from the sources
        // for any un-reverted changes and items from the previously released API for any reverted
        // changes.
        if (codebaseFragment.codebase.containsRevertedItem) {
            codebaseFragment =
                codebaseFragment.snapshotIncludingRevertedItems(
                    referenceVisitorFactory = { delegate ->
                        createFilteringVisitorForStubs(
                            delegate = delegate,
                            isDocStubs = isDocStubs,
                            preFiltered = codebase.preFiltered,
                            apiPredicateConfig = options.apiPredicateConfig,
                            ignoreEmit = true,
                        )
                    },
                )
        }

        // Add additional constructors needed by the stubs.
        val filterEmit: FilterPredicate =
            if (codebaseFragment.codebase.preFiltered) {
                FilterPredicate { true }
            } else {
                val apiPredicateConfigIgnoreShown =
                    options.apiPredicateConfig.copy(ignoreShown = true)
                ApiPredicate(ignoreRemoved = false, config = apiPredicateConfigIgnoreShown)
            }
        val stubConstructorManager = StubConstructorManager(codebaseFragment.codebase)
        stubConstructorManager.addConstructors(filterEmit)

        val stubWriter =
            StubWriter(
                stubsDir = stubDir,
                generateAnnotations = options.generateAnnotations,
                isDocStubs = isDocStubs,
                reporter = options.reporter,
                config = options.stubWriterConfig,
                stubConstructorManager = stubConstructorManager,
            )

        codebaseFragment.accept(stubWriter)

        if (isDocStubs) {
            // Overview docs? These are generally in the empty package.
            codebase.findPackage("")?.let { empty ->
                val overview = empty.overviewDocumentation
                if (overview != null) {
                    stubWriter.writeDocOverview(empty, overview)
                }
            }
        }

        progressTracker.progress(
            "$PROGRAM_NAME wrote ${if (isDocStubs) "documentation" else ""} stubs directory $stubDir in ${
                localTimer.elapsed(SECONDS)} seconds\n"
        )
    }

    private fun convertToWarningNullabilityAnnotations(
        codebase: Codebase,
        previouslyReleasedApi: PreviouslyReleasedApi?,
        filter: PackageFilter?,
        signatureFileCache: SignatureFileCache
    ) {
        if (previouslyReleasedApi != null) {
            val previousCodebase =
                previouslyReleasedApi.load { signatureFiles ->
                    signatureFileCache.load(signatureFiles)
                }

            // If configured, checks for newly added nullness information compared
            // to the previous stable API and marks the newly annotated elements
            // as migrated (which will cause the Kotlin compiler to treat problems
            // as warnings instead of errors

            NullnessMigration.migrateNulls(codebase, previousCodebase)

            previousCodebase.dispose()
        }

        if (filter != null) {
            // Our caller has asked for these APIs to not trigger nullness errors (only warnings) if
            // their callers make incorrect nullness assumptions (for example, calling a function on
            // a reference of nullable type). The way to communicate this to kotlinc is to mark
            // these APIs as RecentlyNullable/RecentlyNonNull
            codebase.accept(MarkPackagesAsRecent(filter))
        }
    }

    companion object {
        /** Generate stubs if necessary based on the [options]. */
        fun generateStubs(
            options: Options,
            codebase: Codebase,
            progressTracker: ProgressTracker,
            executionEnvironment: ExecutionEnvironment,
            reporter: Reporter,
            signatureFileCache: SignatureFileCache,
        ) {
            StubGenerator(
                    options,
                    codebase,
                    progressTracker,
                    executionEnvironment,
                    reporter,
                    signatureFileCache,
                )
                .generateStubs()
        }
    }
}
