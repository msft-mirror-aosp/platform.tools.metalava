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
import com.android.tools.metalava.cli.common.Terminal
import com.android.tools.metalava.cli.common.TerminalColor
import com.android.tools.metalava.cli.common.Verbosity
import com.android.tools.metalava.cli.common.cliError
import com.android.tools.metalava.cli.common.enumOption
import com.android.tools.metalava.cli.common.fileForPathInner
import com.android.tools.metalava.cli.common.newDir
import com.android.tools.metalava.cli.common.newFile
import com.android.tools.metalava.cli.common.stringToExistingFile
import com.android.tools.metalava.cli.common.stringToNewFile
import com.android.tools.metalava.cli.compatibility.CompatibilityCheckOptions
import com.android.tools.metalava.cli.compatibility.CompatibilityCheckOptions.CheckRequest
import com.android.tools.metalava.cli.lint.ApiLintOptions
import com.android.tools.metalava.cli.signature.SignatureFormatOptions
import com.android.tools.metalava.manifest.Manifest
import com.android.tools.metalava.manifest.emptyManifest
import com.android.tools.metalava.model.AnnotationManager
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.TypedefMode
import com.android.tools.metalava.model.annotation.DefaultAnnotationManager
import com.android.tools.metalava.model.text.ApiClassResolution
import com.android.tools.metalava.model.visitors.ApiPredicate
import com.android.tools.metalava.reporter.Baseline
import com.android.tools.metalava.reporter.DefaultReporter
import com.android.tools.metalava.reporter.IssueConfiguration
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Reportable
import com.android.tools.metalava.reporter.Reporter
import com.android.utils.SdkUtils.wrap
import com.github.ajalt.clikt.core.NoSuchOption
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.unique
import com.github.ajalt.clikt.parameters.types.file
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Optional
import java.util.function.Predicate

private const val INDENT_WIDTH = 45

const val ARG_SOURCE_FILES = "--source-files"
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
const val ARG_PASS_THROUGH_ANNOTATION = "--pass-through-annotation"
const val ARG_EXCLUDE_ANNOTATION = "--exclude-annotation"
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

    /** Internal list backing [sources] */
    private val mutableSources: MutableList<File> = mutableListOf()
    /** Internal list backing [passThroughAnnotations] */
    private val mutablePassThroughAnnotations: MutableSet<String> = mutableSetOf()
    /** Internal list backing [excludeAnnotations] */
    private val mutableExcludeAnnotations: MutableSet<String> = mutableSetOf()

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
                )
            } else null
        )
    }

    /** Validator for nullability annotations, if validation is enabled. */
    val nullabilityAnnotationsValidator: NullabilityAnnotationsValidator?
        get() = optionalNullabilityAnnotationsValidator.orElse(null)

    /** Whether nullability validation errors should be considered fatal. */
    private var nullabilityErrorsFatal = true

    /**
     * A file to write non-fatal nullability validation issues to. If null, all issues are treated
     * as fatal or else logged as warnings, depending on the value of [nullabilityErrorsFatal].
     */
    private var nullabilityWarningsTxt: File? = null

    /**
     * Whether to validate nullability for all the classes where we are merging annotations from
     * external java stub files. If true, [nullabilityAnnotationsValidator] must be set.
     */
    var validateNullabilityFromMergedStubs = false

    /**
     * A file containing a list of classes whose nullability annotations should be validated. If
     * set, [nullabilityAnnotationsValidator] must also be set.
     */
    var validateNullabilityFromList: File? = null

    /** All source files to parse */
    var sources: List<File> = mutableSources

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

    val allShowAnnotations by apiSelectionOptions::allShowAnnotations

    /**
     * Whether to include unannotated elements if {@link #showAnnotations} is set. Note: This only
     * applies to signature files, not stub files.
     */
    val showUnannotated
        get() = apiSelectionOptions.showUnannotated

    /**
     * An optional [Reportable] predicate that will ignore issues from (i.e. return false for)
     * [Item]s that do not match the [SourceOptions.apiPackageFilter] filter. If no filter is
     * provided then this will be `null`.
     */
    private val reportableFilter: Predicate<Reportable>? by
        lazy(LazyThreadSafetyMode.NONE) {
            sourceOptions.apiPackageFilter?.let { packageFilter ->
                Predicate { reportable ->
                    // If we are only emitting some packages (--stub-packages), don't report
                    // issues from other packages
                    (reportable as? Item)?.let { item ->
                        val pkg = (item as? PackageItem) ?: item.containingPackage()
                        pkg == null || packageFilter.matches(pkg)
                    } ?: true
                }
            }
        }

    private val apiFlags by lazy {
        ApiFlagsCreator.createFromConfig(configFileOptions.config.apiFlags)
    }

    private val annotationManager: AnnotationManager by lazy {
        DefaultAnnotationManager(
            DefaultAnnotationManager.Config(
                reporter = reporter,
                passThroughAnnotations = passThroughAnnotations,
                allShowAnnotations = allShowAnnotations,
                showAnnotations = apiSelectionOptions.showAnnotations,
                showSingleAnnotations = apiSelectionOptions.showSingleAnnotations,
                showForStubPurposesAnnotations = apiSelectionOptions.showForStubPurposesAnnotations,
                hideAnnotations = apiSelectionOptions.hideAnnotations,
                suppressCompatibilityMetaAnnotations = suppressCompatibilityMetaAnnotations,
                excludeAnnotations = excludeAnnotations,
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
            allShowAnnotations = allShowAnnotations,
            apiPredicateConfig = apiPredicateConfig,
            annotationsMergerConfig =
                AnnotationsMerger.Config(
                    apiPredicateConfig = apiPredicateConfig,
                    sources = sources,
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

    /** This is set directly by [preprocessArgv]. */
    private var verbosity: Verbosity = Verbosity.NORMAL

    /** Whether to report warnings and other diagnostics along the way */
    val quiet: Boolean
        get() = verbosity.quiet

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
                Extracts source annotations from the source files and writes them into the given zip
                file.
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

    /** The set of annotation classes that should be passed through unchanged */
    private var passThroughAnnotations = mutablePassThroughAnnotations

    /** The set of annotation classes that should be removed from all outputs */
    private var excludeAnnotations = mutableExcludeAnnotations

    /** The list of compatibility checks to run */
    val compatibilityChecks: List<CheckRequest> by compatibilityCheckOptions::compatibilityChecks

    /** The set of annotation classes that should be treated as API compatibility important */
    val apiCompatAnnotations by compatibilityCheckOptions::apiCompatAnnotations

    var allBaselines: List<Baseline> = emptyList()

    /** [IssueConfiguration] used by all reporters. */
    val issueConfiguration by issueReportingOptions::issueConfiguration

    /** [Reporter] that will redirect [Issues.Issue] depending on their [Issues.Category]. */
    lateinit var reporter: Reporter
        private set

    internal var allReporters: List<DefaultReporter> = emptyList()

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

    fun parse(args: Array<String>) {
        var index = 0
        while (index < args.size) {
            when (val arg = args[index]) {
                ARG_SOURCE_FILES -> {
                    val listString = getValue(args, ++index)
                    listString.split(",").forEach { path ->
                        mutableSources.addAll(stringToExistingFiles(path))
                    }
                }
                ARG_VALIDATE_NULLABILITY_FROM_MERGED_STUBS -> {
                    validateNullabilityFromMergedStubs = true
                }
                ARG_VALIDATE_NULLABILITY_FROM_LIST -> {
                    validateNullabilityFromList = stringToExistingFile(getValue(args, ++index))
                }
                ARG_NULLABILITY_WARNINGS_TXT ->
                    nullabilityWarningsTxt = stringToNewFile(getValue(args, ++index))
                ARG_NULLABILITY_ERRORS_NON_FATAL -> nullabilityErrorsFatal = false
                ARG_PASS_THROUGH_ANNOTATION -> {
                    val annotations = getValue(args, ++index)
                    annotations.split(",").forEach { path ->
                        mutablePassThroughAnnotations.add(path)
                    }
                }
                ARG_EXCLUDE_ANNOTATION -> {
                    val annotations = getValue(args, ++index)
                    annotations.split(",").forEach { path -> mutableExcludeAnnotations.add(path) }
                }
                else -> {
                    if (arg.startsWith("-")) {
                        // Some other argument: display usage info and exit
                        throw NoSuchOption(givenName = arg)
                    } else {
                        // All args that don't start with "-" are taken to be filenames
                        mutableSources.addAll(stringToExistingFiles(arg))
                    }
                }
            }

            ++index
        }

        // Initialize the reporters.
        val baseline = generalReportingOptions.baseline
        val reporterUnknown =
            createReporter(
                executionEnvironment = executionEnvironment,
                baseline = baseline,
                errorMessage = null,
            )

        val reporterApiLint =
            createReporter(
                executionEnvironment = executionEnvironment,
                baseline = apiLintOptions.baseline ?: baseline,
                errorMessage = apiLintOptions.errorMessage,
            )

        // [Reporter] for "check-compatibility:*:released".
        // i.e.
        //      [ARG_CHECK_COMPATIBILITY_API_RELEASED] and
        //      [ARG_CHECK_COMPATIBILITY_REMOVED_RELEASED].
        val reporterCompatibilityReleased =
            createReporter(
                executionEnvironment = executionEnvironment,
                baseline = compatibilityCheckOptions.baseline ?: baseline,
                errorMessage = compatibilityCheckOptions.errorMessage,
            )

        // A Reporter that will redirect issues to the appropriate reporter based on the issue's
        // Category.
        reporter =
            CategoryRedirectingReporter(
                defaultReporter = reporterUnknown,
                apiLintReporter = reporterApiLint,
                compatibilityReporter = reporterCompatibilityReleased,
            )

        // Build "all baselines" and "all reporters"

        // Baselines are nullable, so selectively add to the list.
        allBaselines =
            listOfNotNull(baseline, apiLintOptions.baseline, compatibilityCheckOptions.baseline)

        // Reporters are non-null.
        allReporters =
            listOf(
                reporterUnknown,
                reporterApiLint,
                reporterCompatibilityReleased,
            )

        // Make sure that any config files are processed.
        configFileOptions.config
    }

    /**
     * Create a [Reporter] that checks for known issues in [baseline] and prints [errorMessage], if
     * provided, when errors have been reported.
     */
    private fun createReporter(
        executionEnvironment: ExecutionEnvironment,
        baseline: Baseline?,
        errorMessage: String?,
    ) =
        DefaultReporter(
            environment = executionEnvironment.reporterEnvironment,
            issueConfiguration = issueConfiguration,
            baseline = baseline,
            errorMessage = errorMessage,
            reportableFilter = reportableFilter,
            config = issueReportingOptions.reporterConfig,
        )

    private fun getValue(args: Array<String>, index: Int): String {
        if (index >= args.size) {
            cliError("Missing argument for ${args[index - 1]}")
        }
        return args[index]
    }

    private fun stringToExistingFiles(value: String): List<File> {
        return value
            .split(File.pathSeparatorChar)
            .map { fileForPathInner(it) }
            .map { file ->
                if (!file.isFile) {
                    cliError("$file is not a file")
                }
                file
            }
    }
}

object OptionsHelp {
    fun getUsage(terminal: Terminal, width: Int): String {
        val usage = StringWriter()
        val printWriter = PrintWriter(usage)
        usage(printWriter, terminal, width)
        return usage.toString()
    }

    private fun usage(out: PrintWriter, terminal: Terminal, width: Int) {
        val args =
            arrayOf(
                "",
                "API sources:",
                "$ARG_SOURCE_FILES <files>",
                "A comma separated list of source files to be parsed. Can also be " +
                    "@ followed by a path to a text file containing paths to the full set of files to parse.",
                ARG_VALIDATE_NULLABILITY_FROM_MERGED_STUBS,
                "Triggers validation of nullability annotations " +
                    "for any class where $ARG_MERGE_QUALIFIER_ANNOTATIONS includes a Java stub file.",
                ARG_VALIDATE_NULLABILITY_FROM_LIST,
                "Triggers validation of nullability annotations " +
                    "for any class listed in the named file (one top-level class per line, # prefix for comment line).",
                "$ARG_NULLABILITY_WARNINGS_TXT <file>",
                "Specifies where to write warnings encountered during " +
                    "validation of nullability annotations. (Does not trigger validation by itself.)",
                ARG_NULLABILITY_ERRORS_NON_FATAL,
                "Specifies that errors encountered during validation of " +
                    "nullability annotations should not be treated as errors. They will be written out to the " +
                    "file specified in $ARG_NULLABILITY_WARNINGS_TXT instead.",
                "",
                "Generating Stubs:",
                "$ARG_PASS_THROUGH_ANNOTATION <annotation classes>",
                "A comma separated list of fully qualified names of " +
                    "annotation classes that must be passed through unchanged.",
                "$ARG_EXCLUDE_ANNOTATION <annotation classes>",
                "A comma separated list of fully qualified names of " +
                    "annotation classes that must be stripped from metalava's outputs.",
                "",
                "Environment Variables:",
                ENV_VAR_METALAVA_DUMP_ARGV,
                "Set to true to have metalava emit all the arguments it was invoked with. " +
                    "Helpful when debugging or reproducing under a debugger what the build system is doing.",
                ENV_VAR_METALAVA_PREPEND_ARGS,
                "One or more arguments (concatenated by space) to insert into the " +
                    "command line, before the documentation flags.",
                ENV_VAR_METALAVA_APPEND_ARGS,
                "One or more arguments (concatenated by space) to append to the " +
                    "end of the command line, after the generate documentation flags."
            )

        val indent = " ".repeat(INDENT_WIDTH)

        var i = 0
        while (i < args.size) {
            val arg = args[i]
            if (arg.isEmpty()) {
                val groupTitle = args[i + 1]
                out.println("\n")
                out.println(terminal.colorize(groupTitle, TerminalColor.YELLOW))
            } else {
                val description = "\n" + args[i + 1]
                val formattedArg = terminal.bold(arg)
                val invisibleChars = formattedArg.length - arg.length
                // +invisibleChars: the extra chars in the above are counted but don't
                // contribute to width so allow more space
                val formatString = "%1$-" + (INDENT_WIDTH + invisibleChars) + "s%2\$s"

                val output =
                    wrap(
                        String.format(formatString, formattedArg, description),
                        width + invisibleChars,
                        width,
                        indent
                    )

                // Remove trailing whitespace
                val lines = output.lines()
                lines.forEachIndexed { index, line ->
                    out.print(line.trimEnd())
                    if (index < lines.size - 1) {
                        out.println()
                    }
                }
            }
            i += 2
        }
    }
}
