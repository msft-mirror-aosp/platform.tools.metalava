/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.metalava.cli.common.ARG_MERGE_QUALIFIER_ANNOTATIONS
import com.android.tools.metalava.cli.common.CommonOptions
import com.android.tools.metalava.cli.common.DefaultSignatureFileLoader
import com.android.tools.metalava.cli.common.ExecutionEnvironment
import com.android.tools.metalava.cli.common.IssueReportingOptions
import com.android.tools.metalava.cli.common.SourceOptions
import com.android.tools.metalava.cli.common.Verbosity
import com.android.tools.metalava.cli.common.enumOption
import com.android.tools.metalava.cli.common.existingFile
import com.android.tools.metalava.cli.common.newDir
import com.android.tools.metalava.cli.common.newFile
import com.android.tools.metalava.cli.compatibility.CompatibilityCheckOptions
import com.android.tools.metalava.cli.compatibility.CompatibilityCheckOptions.CheckRequest
import com.android.tools.metalava.cli.lint.ApiLintOptions
import com.android.tools.metalava.cli.signature.SignatureFormatOptions
import com.android.tools.metalava.manifest.Manifest
import com.android.tools.metalava.manifest.emptyManifest
import com.android.tools.metalava.model.AnnotationManager
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.TypedefMode
import com.android.tools.metalava.model.annotation.DefaultAnnotationManager
import com.android.tools.metalava.model.text.ApiClassResolution
import com.android.tools.metalava.model.visitors.ApiPredicate
import com.android.tools.metalava.reporter.IssueConfiguration
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Reporter
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.unique
import com.github.ajalt.clikt.parameters.types.file
import java.io.File
import java.io.PrintWriter
import java.util.Optional

const val ARG_API_CLASS_RESOLUTION = "--api-class-resolution"
const val ARG_SDK_VALUES = "--sdk-values"
const val ARG_VALIDATE_NULLABILITY_FROM_MERGED_STUBS = "--validate-nullability-from-merged-stubs"
const val ARG_VALIDATE_NULLABILITY_FROM_LIST = "--validate-nullability-from-list"
const val ARG_NULLABILITY_WARNINGS_TXT = "--nullability-warnings-txt"
const val ARG_NULLABILITY_ERRORS_NON_FATAL = "--nullability-errors-non-fatal"
/** Used by Firebase, see b/116185431#comment15, not used by Android Platform or AndroidX */
const val ARG_PROGUARD = "--proguard"
const val ARG_EXTRACT_ANNOTATIONS = "--extract-annotations"
const val ARG_MANIFEST = "--manifest"
const val ARG_SUPPRESS_COMPATIBILITY_META_ANNOTATION = "--suppress-compatibility-meta-annotation"
const val ARG_TYPEDEFS_IN_SIGNATURES = "--typedefs-in-signatures"

class Options(
    private val executionEnvironment: ExecutionEnvironment = ExecutionEnvironment(),
    private val commonOptions: CommonOptions = CommonOptions(),
    private val sourceOptions: SourceOptions = SourceOptions(),
    private val issueReportingOptions: IssueReportingOptions =
        IssueReportingOptions(commonOptions = commonOptions),
    private val generalReportingOptions: GeneralReportingOptions = GeneralReportingOptions(),
    internal val configFileOptions: ConfigFileOptions = ConfigFileOptions(),
    val apiSelectionOptions: ApiSelectionOptions = ApiSelectionOptions(),
    val apiLintOptions: ApiLintOptions = ApiLintOptions(),
    private val compatibilityCheckOptions: CompatibilityCheckOptions = CompatibilityCheckOptions(),
    signatureFormatOptions: SignatureFormatOptions = SignatureFormatOptions(),
) : OptionGroup() {
    /** Writer to direct output to. */
    val stdout: PrintWriter
        get() = executionEnvironment.stdout

    /** Writer to direct error messages to. */
    val stderr: PrintWriter
        get() = executionEnvironment.stderr

    /**
     * Backing property for [nullabilityAnnotationsValidator]
     *
     * This uses [Optional] to wrap the value as [lazy] cannot handle nullable values as it uses
     * `null` as a special value.
     *
     * Creates [NullabilityAnnotationsValidator] lazily as it depends on a number of different
     * options which may be supplied in different orders.
     */
    private val optionalNullabilityAnnotationsValidator by lazy {
        Optional.ofNullable(
            if (validateNullabilityFromMergedStubs || validateNullabilityFromList != null) {
                NullabilityAnnotationsValidator(
                    reporter,
                    nullabilityErrorsFatal,
                    nullabilityWarningsTxt,
                    apiPredicateConfig,
                    validateNullabilityFromList,
                )
            } else null
        )
    }

    /** Validator for nullability annotations, if validation is enabled. */
    val nullabilityAnnotationsValidator: NullabilityAnnotationsValidator?
        get() = optionalNullabilityAnnotationsValidator.orElse(null)

    /** Whether nullability validation errors should be considered fatal. */
    private val nullabilityErrorsNonFatal by
        option(
                ARG_NULLABILITY_ERRORS_NON_FATAL,
                help =
                    """
                        Specifies that errors encountered during validation of nullability
                        annotations should not be treated as errors. They will be written out to the
                        file specified in $ARG_NULLABILITY_WARNINGS_TXT instead.
                    """
                        .trimIndent(),
            )
            .flag()

    private val nullabilityErrorsFatal
        get() = !nullabilityErrorsNonFatal

    /**
     * A file to write non-fatal nullability validation issues to. If null, all issues are treated
     * as fatal or else logged as warnings, depending on the value of [nullabilityErrorsFatal].
     */
    private val nullabilityWarningsTxt by
        option(
                ARG_NULLABILITY_WARNINGS_TXT,
                metavar = "<file>",
                help =
                    """
                        Specifies where to write warnings encountered during validation of
                        nullability annotations. (Does not trigger validation by itself.)
                    """
                        .trimIndent(),
            )
            .newFile()

    /**
     * Whether to validate nullability for all the classes where we are merging annotations from
     * external java stub files. If true, [nullabilityAnnotationsValidator] must be set.
     */
    val validateNullabilityFromMergedStubs by
        option(
                ARG_VALIDATE_NULLABILITY_FROM_MERGED_STUBS,
                help =
                    """
                        Triggers validation of nullability annotations for any class where
                        $ARG_MERGE_QUALIFIER_ANNOTATIONS includes a Java stub file.
                    """
                        .trimIndent(),
            )
            .flag()

    /**
     * A file containing a list of classes whose nullability annotations should be validated. If
     * set, [nullabilityAnnotationsValidator] must also be set.
     */
    private val validateNullabilityFromList by
        option(
                ARG_VALIDATE_NULLABILITY_FROM_LIST,
                help =
                    """
                        Triggers validation of nullability annotations for any class listed in the
                        named file (one top-level class per line, # prefix for comment line).
                    """
                        .trimIndent(),
            )
            .existingFile()

    val apiClassResolution by
        enumOption(
            help =
                """
                Determines how class resolution is performed when loading API signature files. Any
                classes that cannot be found will be treated as empty.",
            """
                    .trimIndent(),
            enumValueHelpGetter = { it.help },
            default = ApiClassResolution.API_CLASSPATH,
            key = { it.optionValue },
        )

    /**
     * Whether to include unannotated elements if {@link #showAnnotations} is set. Note: This only
     * applies to signature files, not stub files.
     */
    val showUnannotated
        get() = apiSelectionOptions.showUnannotated

    private val apiFlags by lazy {
        ApiFlagsCreator.createFromConfig(configFileOptions.config.apiFlags)
    }

    private val annotationManager: AnnotationManager by lazy {
        DefaultAnnotationManager(
            DefaultAnnotationManager.Config(
                reporter = reporter,
                passThroughAnnotations = apiSelectionOptions.passThroughAnnotations,
                allShowAnnotations = apiSelectionOptions.allShowAnnotations,
                showAnnotations = apiSelectionOptions.showAnnotations,
                showSingleAnnotations = apiSelectionOptions.showSingleAnnotations,
                showForStubPurposesAnnotations = apiSelectionOptions.showForStubPurposesAnnotations,
                hideAnnotations = apiSelectionOptions.hideAnnotations,
                suppressCompatibilityMetaAnnotations = suppressCompatibilityMetaAnnotations,
                excludeAnnotations = apiSelectionOptions.excludeAnnotations,
                typedefMode = typedefMode,
                apiPredicate = ApiPredicate(config = apiPredicateConfig),
                previouslyReleasedCodebaseProvider = {
                    previouslyReleasedApi?.load { signatureFileCache.load(it) }
                },
                apiFlags = apiFlags,
            )
        )
    }

    /** Make this available for testing purposes. */
    internal val previouslyReleasedApi
        get() = compatibilityCheckOptions.previouslyReleasedApi

    internal val codebaseConfig by
        lazy(LazyThreadSafetyMode.NONE) {
            Codebase.Config(
                allowReadingComments = sourceOptions.allowReadingComments,
                annotationManager = annotationManager,
                apiFlags = apiFlags,
                apiSurfaces = apiSelectionOptions.apiSurfaces,
                reporter = reporter,
            )
        }

    internal val signatureFileLoader by
        lazy(LazyThreadSafetyMode.NONE) { DefaultSignatureFileLoader(codebaseConfig) }

    internal val signatureFileCache by
        lazy(LazyThreadSafetyMode.NONE) { SignatureFileCache(signatureFileLoader) }

    /** Meta-annotations for which annotated APIs should not be checked for compatibility. */
    private val suppressCompatibilityMetaAnnotations by
        option(
                ARG_SUPPRESS_COMPATIBILITY_META_ANNOTATION,
                help =
                    """
                       Suppress compatibility checks for any elements within the scope of an
                       annotation which is itself annotated with the given meta-annotation.
                    """
                        .trimIndent(),
                metavar = "<meta-annotation class>",
            )
            .multiple()
            .unique()

    /** The configuration options for the [ApiAnalyzer] class. */
    val apiAnalyzerConfig by lazy {
        val skipEmitPackages = executionEnvironment.testEnvironment?.skipEmitPackages ?: emptyList()
        ApiAnalyzer.Config(
            manifest = manifest,
            skipEmitPackages = skipEmitPackages,
            mergeQualifierAnnotations = sourceOptions.mergeQualifierAnnotations,
            mergeInclusionAnnotations = sourceOptions.mergeInclusionAnnotations,
            allShowAnnotations = apiSelectionOptions.allShowAnnotations,
            apiPredicateConfig = apiPredicateConfig,
            annotationsMergerConfig =
                AnnotationsMerger.Config(
                    apiPredicateConfig = apiPredicateConfig,
                    sources = sourceOptions.sourceFiles,
                    sourcePath = sourceOptions.sourcePath,
                    classpath = sourceOptions.classpath,
                    apiPackageFilter = sourceOptions.apiPackageFilter,
                    nullabilityAnnotationsValidator =
                        if (validateNullabilityFromMergedStubs) nullabilityAnnotationsValidator
                        else null,
                ),
        )
    }

    val apiPredicateConfig by lazy {
        ApiPredicate.Config(
            ignoreShown = showUnannotated,
            addAdditionalOverrides = signatureFormatOptions.fileFormat.addAdditionalOverrides,
        )
    }

    private var verbosity: Verbosity = Verbosity.NORMAL

    /**
     * Whether to report extra diagnostics along the way (note that verbose isn't the same as not
     * quiet)
     */
    val verbose: Boolean
        get() = verbosity.verbose

    /** Proguard Keep list file to write */
    val proguardFile by
        option(
                ARG_PROGUARD,
                metavar = "<file>",
                help = "Write a ProGuard keep file for the API.",
            )
            .newFile()

    /** Path to directory to write SDK values to */
    val sdkValueDir by
        option(
                ARG_SDK_VALUES,
                metavar = "<dir>",
                help = "Write SDK values files to the given directory.",
            )
            .newDir()

    /**
     * If set, a file to write extracted annotations to. Corresponds to the --extract-annotations
     * flag.
     */
    val externalAnnotationsFile by
        option(
                ARG_EXTRACT_ANNOTATIONS,
                metavar = "<zipfile>",
                help =
                    """
                        Extracts source annotations from the source files and writes them into the
                        given zip file.
                    """
                        .trimIndent(),
            )
            .newFile()

    /** An optional manifest [File]. */
    private val manifestFile by
        option(
                ARG_MANIFEST,
                help =
                    """
        A manifest file, used to check permissions to cross check APIs and retrieve min_sdk_version.
        (default: no manifest)
                    """
                        .trimIndent()
            )
            .file(mustExist = true, canBeDir = false, mustBeReadable = true)

    /**
     * A [Manifest] object to look up available permissions and min_sdk_version.
     *
     * Created lazily to make sure that the [reporter] has been initialized.
     */
    val manifest by lazy { manifestFile?.let { Manifest(it, reporter) } ?: emptyManifest }

    /** The list of compatibility checks to run */
    val compatibilityChecks: List<CheckRequest> by compatibilityCheckOptions::compatibilityChecks

    /** The set of annotation classes that should be treated as API compatibility important */
    val apiCompatAnnotations by compatibilityCheckOptions::apiCompatAnnotations

    val reporterManager by
        lazy(LazyThreadSafetyMode.NONE) {
            ReporterManager(
                executionEnvironment.reporterEnvironment,
                apiLintOptions,
                compatibilityCheckOptions,
                generalReportingOptions,
                issueReportingOptions,
                sourceOptions,
            )
        }

    /** [IssueConfiguration] used by all reporters. */
    val issueConfiguration by issueReportingOptions::issueConfiguration

    /** [Reporter] that will redirect [Issues.Issue] depending on their [Issues.Category]. */
    val reporter
        get() = reporterManager.reporter

    /**
     * How to handle typedef annotations in signature files; corresponds to
     * $ARG_TYPEDEFS_IN_SIGNATURES
     */
    private val typedefMode by
        enumOption(
            ARG_TYPEDEFS_IN_SIGNATURES,
            help = """Whether to include typedef annotations in signature files.""",
            enumValueHelpGetter = { it.help },
            default = TypedefMode.NONE,
            key = { it.optionValue },
        )
}
