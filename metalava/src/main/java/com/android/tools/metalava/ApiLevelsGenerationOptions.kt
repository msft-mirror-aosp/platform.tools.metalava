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

import com.android.tools.metalava.apilevels.ApiHistoryUpdater
import com.android.tools.metalava.apilevels.ApiVersion
import com.android.tools.metalava.apilevels.GenerateApiHistoryConfig
import com.android.tools.metalava.apilevels.MatchedPatternFile
import com.android.tools.metalava.apilevels.MissingClassAction
import com.android.tools.metalava.apilevels.PatternNode
import com.android.tools.metalava.apilevels.VersionedApi
import com.android.tools.metalava.apilevels.VersionedJarApi
import com.android.tools.metalava.apilevels.VersionedSignatureApi
import com.android.tools.metalava.apilevels.VersionedSourceApi
import com.android.tools.metalava.apilevels.createGenerateApiHistoryConfig
import com.android.tools.metalava.cli.common.EarlyOptions
import com.android.tools.metalava.cli.common.ExecutionEnvironment
import com.android.tools.metalava.cli.common.MetalavaOptionGroup
import com.android.tools.metalava.cli.common.RequiresOtherGroups
import com.android.tools.metalava.cli.common.SignatureFileLoader
import com.android.tools.metalava.cli.common.cliError
import com.android.tools.metalava.cli.common.existingFile
import com.android.tools.metalava.cli.common.fileForPathInner
import com.android.tools.metalava.cli.common.newFile
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.CodebaseFragment
import com.android.tools.metalava.model.api.surface.ApiSurfaces
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.OptionWithValues
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.split
import java.io.File

// XML API version related arguments.
const val ARG_GENERATE_API_LEVELS = "--generate-api-levels"

const val ARG_REMOVE_MISSING_CLASS_REFERENCES_IN_API_LEVELS =
    "--remove-missing-class-references-in-api-levels"

const val ARG_API_VERSION_FOR_SOURCES = "--api-version-for-sources"

const val ARG_API_VERSION_RANGE = "--api-version-range"

const val ARG_API_VERSION_FOR_SDK_EXTENSION = "--api-version-for-sdk-extension"
const val ARG_SDK_EXTENSION_VERSION_RANGE = "--sdk-extension-version-range"

const val ARG_ANDROID_JAR_PATTERN = "--android-jar-pattern"

const val ARG_SDK_INFO_FILE = "--sdk-extensions-info"

const val ARG_API_VERSION_SIGNATURE_PATTERN = "--api-version-signature-pattern"

// JSON API version related arguments
const val ARG_GENERATE_API_VERSION_HISTORY = "--generate-api-version-history"
const val ARG_API_VERSION_SIGNATURE_FILES = "--api-version-signature-files"

/** Convert an option value to an [ApiVersion]. */
internal fun OptionWithValues<String?, String, String>.apiVersion() = convert {
    ApiVersion.fromString(it)
}

/** Convert an option value to a [ClosedRange] of [ApiVersion]. */
internal fun OptionWithValues<String?, String, String>.apiVersionRange() = convert { text ->
    val parts = text.split(':')
    if (parts.size != 2) {
        error("Must be of the form <version>:<version> but found '$text'")
    }
    val (from, to) = parts
    ApiVersion.fromString(from).rangeTo(ApiVersion.fromString(to))
}

class ApiLevelsGenerationOptions(
    private val executionEnvironment: ExecutionEnvironment = ExecutionEnvironment(),
    private val earlyOptions: EarlyOptions = EarlyOptions(),
) :
    MetalavaOptionGroup(
        name = "Api Levels Generation",
        help =
            """
                Options controlling the API levels file, e.g. `api-versions.xml` file.
            """
                .trimIndent()
    ),
    RequiresOtherGroups {

    /** Make sure that the [earlyOptions] is correctly initialized when testing. */
    override val requiredGroups: List<OptionGroup>
        get() = listOf(earlyOptions)

    /** API level XML file to generate. */
    val generateApiLevelsXmlFile: File? by
        option(
                ARG_GENERATE_API_LEVELS,
                metavar = "<xmlfile>",
                help =
                    """
                        Reads android.jar SDK files and generates an XML file recording the API
                        level for each class, method and field. if $ARG_API_VERSION_FOR_SOURCES is provided,
                        sources will also be included in the file with the api level $ARG_API_VERSION_FOR_SOURCES
                    """
                        .trimIndent(),
            )
            .newFile()

    internal val apiVersionForSources: ApiVersion? by
        option(
                ARG_API_VERSION_FOR_SOURCES,
                metavar = "<api-version>",
                help =
                    """
                        Sets the API version of unfinalized apis in the current source code. This supports a
                        single integer level, `major.minor`, `major.minor.patch` and
                        `major.minor.patch-quality` formats. Where `major`, `minor` and `patch` are
                        all non-negative integers and `quality` is an alphanumeric string.
                    """
                        .trimIndent(),
            )
            .apiVersion()

    /** Whether references to missing classes should be removed from the api levels file. */
    private val removeMissingClassReferencesInApiLevels: Boolean by
        option(
                ARG_REMOVE_MISSING_CLASS_REFERENCES_IN_API_LEVELS,
                help =
                    """
                        Removes references to missing classes when generating the API levels XML
                        file. This can happen when generating the XML file for the non-updatable
                        portions of the module-lib sdk, as those non-updatable portions can
                        reference classes that are part of an updatable apex.
                    """
                        .trimIndent(),
            )
            .flag()

    /** The range of historical API versions that can be included in the API version history. */
    private val apiVersionRange: ClosedRange<ApiVersion>? by
        option(
                ARG_API_VERSION_RANGE,
                metavar = "<api-version>:<api-version>",
                help =
                    """
                        The optional range of historical versions that can be included in the API
                        version history. The `from` and `to` parts of the range are separated by a
                        `:` and are both inclusive. See $ARG_API_VERSION_FOR_SOURCES for acceptable
                        `<api-version>`s.

                        If unspecified, version history will default to include every historical version.
                    """
                        .trimIndent()
            )
            .apiVersionRange()

    /**
     * The range of historical SDK Extension API versions that can be included in the API version
     * history.
     */
    private val sdkExtensionVersionRange: ClosedRange<ApiVersion>? by
        option(
                ARG_SDK_EXTENSION_VERSION_RANGE,
                metavar = "<api-version>:<api-version>",
                help =
                    """
                        The optional range of historical sdk extensions versions that can be included in the API
                        version history. The `from` and `to` parts of the range are separated by a
                        `:` and are both inclusive. See $ARG_API_VERSION_FOR_SOURCES for acceptable
                        `<api-version>`s.

                        If unspecified then allow every historical version.
                    """
                        .trimIndent()
            )
            .apiVersionRange()

    /** The list of patterns used to find matching jars in the set of files visible to Metalava. */
    private val androidJarPatterns: List<String> by
        option(
                ARG_ANDROID_JAR_PATTERN,
                metavar = "<historical-api-pattern>",
                help =
                    """
                        Pattern to use to locate Android JAR files. Must end with `.jar`.

                        See `metalava help historical-api-patterns` for more information.
                    """
                        .trimIndent(),
            )
            .multiple(default = emptyList())

    /**
     * The list of patterns used to find matching signature files in the set of files visible to
     * Metalava.
     */
    private val signaturePatterns by
        option(
                ARG_API_VERSION_SIGNATURE_PATTERN,
                metavar = "<historical-api-pattern>",
                help =
                    """
                        Pattern to use to locate signature files. Typically ends with `.txt`.

                        See `metalava help historical-api-patterns` for more information.
                    """
                        .trimIndent(),
            )
            .multiple()

    /**
     * Rules to filter out some extension SDK APIs from the API, and assign extensions to the APIs
     * that are kept
     */
    private val sdkInfoFile: File? by
        option(
                ARG_SDK_INFO_FILE,
                metavar = "<sdk-info-file>",
                help =
                    """
                        Points to map of extension SDK APIs to include, if any. The file is a plain
                        text file and describes, per extension SDK, what APIs from that extension
                        to include in the file created via $ARG_GENERATE_API_LEVELS. The format of
                        each line is one of the following:
                        \"<module-name> <pattern> <ext-name> [<ext-name> [...]]\", where
                        <module-name> is the name of the mainline module this line refers to,
                        <pattern> is a common Java name prefix of the APIs this line refers to, and
                        <ext-name> is a list of extension SDK names in which these SDKs first
                        appeared, or \"<ext-name> <ext-id> <type>\", where <ext-name> is the name of
                        an SDK, <ext-id> its numerical ID and <type> is one of \"platform\" (the
                        Android platform SDK), \"platform-ext\" (an extension to the Android
                        platform SDK), \"standalone\" (a separate SDK). Fields are separated by
                        whitespace. A mainline module may be listed multiple times.
                        The special pattern \"*\" refers to all APIs in the given mainline module.
                        Lines beginning with # are comments.

                        If specified then the $ARG_ANDROID_JAR_PATTERN must include at least one
                        pattern that uses `{version:extension}` and `{module}` placeholders and that
                        pattern must match at least one file.
                    """
                        .trimIndent(),
            )
            .existingFile()

    private val apiVersionForSdkExtension: ApiVersion? by
        option(
                ARG_API_VERSION_FOR_SDK_EXTENSION,
                metavar = "<api-version>",
                help =
                    """
                        SDK extension APIs can be added between SDK versions and they do not become
                        available in an SDK version until the next SDK version is released. However,
                        when generating an API history it is required that every API is in an API
                        version, even those added as part of an SDK extension.

                        If an SDK extension is being prepared for inclusion in an SDK version then
                        this should be the SDK version. If an SDK extension is being prepared
                        between SDK versions than this should be a magic version number that
                        indicates that it the newly added SDK extension APIs are not yet present in
                        any SDK version.

                        In the latter case the $ARG_API_VERSION_LABEL should be used when generating
                        documentation from that `api-versions.xml` file to give the magic version a
                        meaningful name in the documentation.
                    """
                        .trimIndent(),
            )
            .apiVersion()

    /**
     * Create a [VersionedApi] from [updater] and [files].
     *
     * It requires [files] to contain a single entry.
     */
    private fun createVersionedJarApi(
        updater: ApiHistoryUpdater,
        files: List<MatchedPatternFile>,
    ): VersionedApi {
        val version = updater.apiVersion
        val jarFiles = files.map { it.file }
        verbosePrint { "Found API $version at ${jarFiles.joinToString()}" }
        return VersionedJarApi(jarFiles, updater)
    }

    /** Print string returned by [message] if verbose output has been requested. */
    private inline fun verbosePrint(message: () -> String) {
        if (earlyOptions.verbosity.verbose) {
            executionEnvironment.stdout.println(message())
        }
    }

    /**
     * Get the [GenerateApiHistoryConfig] for Android.
     *
     * This has some Android specific code, e.g. structure of SDK extensions.
     */
    fun forAndroidConfig(
        signatureFileLoader: SignatureFileLoader,
        apiSurfaces: ApiSurfaces?,
        codebaseFragmentProvider: () -> CodebaseFragment,
    ) =
        generateApiLevelsXmlFile?.let { outputFile ->
            fun createVersionedSignatureApi(
                updater: ApiHistoryUpdater,
                files: List<MatchedPatternFile>,
            ) = VersionedSignatureApi(signatureFileLoader, files.map { it.file }, updater)

            if (androidJarPatterns.isNotEmpty() && signaturePatterns.isNotEmpty()) {
                cliError(
                    "Cannot combine $ARG_API_VERSION_SIGNATURE_PATTERN with $ARG_ANDROID_JAR_PATTERN"
                )
            }

            // Scan for all the files that could contribute to the API history.
            val currentDir = fileForPathInner(".")
            val (apiFilePatterns, versionedApiFactory) =
                if (signaturePatterns.isEmpty()) {
                    Pair(androidJarPatterns, ::createVersionedJarApi)
                } else {
                    Pair(signaturePatterns, ::createVersionedSignatureApi)
                }

            createGenerateApiHistoryConfig(
                outputFile = outputFile,
                currentDir = currentDir,
                apiFilePatterns = apiFilePatterns,
                apiVersionRange = apiVersionRange,
                sdkExtensionVersionRange = sdkExtensionVersionRange,
                sdkInfoFile = sdkInfoFile,
                apiVersionForSdkExtension = apiVersionForSdkExtension,
                apiSurfaces = apiSurfaces,
                currentVersion = apiVersionForSources,
                currentVersionedApiFactory = { version ->
                    VersionedSourceApi(
                        codebaseFragmentProvider,
                        version,
                    )
                },
                versionedApiFactory = versionedApiFactory,
                missingClassAction =
                    if (removeMissingClassReferencesInApiLevels) MissingClassAction.REMOVE
                    else MissingClassAction.REPORT,
                // Use internal names.
                useInternalNames = true,
            )
        }

    /** API version history file to generate */
    private val generateApiVersionHistory by
        option(
                ARG_GENERATE_API_VERSION_HISTORY,
                metavar = "<output-file>",
                help =
                    """
                        Reads API signature files and generates a JSON or XML file depending on the
                        extension, which must be one of `json` or `xml` respectively. The JSON file
                        will record the API version in which each class, method, and field. was
                        added in and (if applicable) deprecated in. The XML file will include that
                        information and more but will be optimized to exclude information from
                        class members which is the same as the containing class.
                    """
                        .trimIndent(),
            )
            .newFile()

    /**
     * Ordered list of signatures for each past API version, when generating
     * [generateApiVersionHistory].
     */
    private val apiVersionSignatureFiles by
        option(
                ARG_API_VERSION_SIGNATURE_FILES,
                metavar = "<files>",
                help =
                    """
                        An ordered list of text API signature files. The oldest API version should
                        be first, the newest last. This should not include a signature file for the
                        current API version, which will be parsed from the provided source files.
                        Not required to generate API version JSON if the current version is the only
                        version.
                    """
                        .trimIndent(),
            )
            .existingFile()
            .split(File.pathSeparator)
            .default(emptyList(), defaultForHelp = "")

    /**
     * Construct the [GenerateApiHistoryConfig] from the options.
     *
     * If no relevant command line options were provided then this will return `null`, otherwise it
     * will validate the options and if all is well construct and return a
     * [GenerateApiHistoryConfig] object.
     *
     * @param signatureFileLoader used for loading [Codebase]s from signature files.
     * @param codebaseFragmentProvider provides access to the [CodebaseFragment] for the API defined
     *   in the sources. This will only be called if a [GenerateApiHistoryConfig] needs to be
     *   created.
     */
    fun fromSignatureFilesConfig(
        signatureFileLoader: SignatureFileLoader,
        codebaseFragmentProvider: () -> CodebaseFragment,
    ): GenerateApiHistoryConfig? {
        val apiVersionsFile = generateApiVersionHistory
        return if (apiVersionsFile != null) {
            val (sourceVersion, matchedPatternFiles) =
                sourceVersionAndMatchedPatternFilesFromSignaturePatterns()

            // Create VersionedApis for the signature files and the source codebase.
            val versionedApis = buildList {
                matchedPatternFiles.mapTo(this) {
                    val updater = ApiHistoryUpdater.forApiVersion(it.version)
                    VersionedSignatureApi(signatureFileLoader, listOf(it.file), updater)
                }
                // Add a VersionedSourceApi for the source code.
                add(VersionedSourceApi(codebaseFragmentProvider, sourceVersion))
            }

            GenerateApiHistoryConfig(
                versionedApis = versionedApis,
                outputFile = apiVersionsFile,
                // None are available when generating from signature files.
                sdkExtensionsArguments = null,
                // Keep any references to missing classes.
                missingClassAction = MissingClassAction.KEEP,
                // Do not use internal names.
                useInternalNames = false,
            )
        } else {
            null
        }
    }

    /**
     * Get the source [ApiVersion] and list of [MatchedPatternFile]s from [signaturePatterns] as
     * well as [apiVersionForSources] and [apiVersionSignatureFiles].
     */
    private fun sourceVersionAndMatchedPatternFilesFromSignaturePatterns():
        Pair<ApiVersion, List<MatchedPatternFile>> {

        // TODO b/454050901 : Remove dependency on current version
        val sourceVersion =
            apiVersionForSources
                ?: cliError(
                    "Must specify or $ARG_API_VERSION_FOR_SOURCES with $ARG_API_VERSION_SIGNATURE_PATTERN"
                )

        val historicalSignatureFiles = apiVersionSignatureFiles
        if (historicalSignatureFiles.isEmpty()) return sourceVersion to emptyList()

        val patternNode = PatternNode.parsePatterns(signaturePatterns)
        val matchedFiles =
            patternNode.scan(
                PatternNode.ScanConfig(
                    dir = fileForPathInner("."),
                    fileProvider = PatternNode.LimitedFileSystemProvider(historicalSignatureFiles)
                )
            )

        if (matchedFiles.size != historicalSignatureFiles.size) {
            val matched = matchedFiles.map { it.file }.toSet()
            val unmatched = historicalSignatureFiles.filter { it !in matched }
            cliError(
                "$ARG_API_VERSION_SIGNATURE_FILES: The following files were unmatched by a signature pattern:\n${unmatched.joinToString("\n") {"    $it"}}"
            )
        }

        return sourceVersion to matchedFiles
    }
}
