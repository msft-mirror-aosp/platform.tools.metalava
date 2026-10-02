/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.metalava.model.text

import com.android.tools.metalava.model.ANDROIDX_NONNULL
import com.android.tools.metalava.model.ANDROIDX_NULLABLE
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.AnnotationItem.Companion.unshortenAnnotation
import com.android.tools.metalava.model.ArrayTypeItem
import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.ClassOrigin
import com.android.tools.metalava.model.ClassPathResolver
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.ExceptionTypeItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.ItemDocumentation
import com.android.tools.metalava.model.JAVA_LANG_DEPRECATED
import com.android.tools.metalava.model.JAVA_LANG_OBJECT
import com.android.tools.metalava.model.MetalavaApi
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.MutableModifierList
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.ParameterKind
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.SkeletonClassItem
import com.android.tools.metalava.model.SkeletonTypeParameterItem
import com.android.tools.metalava.model.TargetLanguage
import com.android.tools.metalava.model.TargetLanguageSet
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeNullability
import com.android.tools.metalava.model.TypeParameterItem
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.TypeParameterScope
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.WellKnownTypes
import com.android.tools.metalava.model.api.surface.ApiSurfaces
import com.android.tools.metalava.model.api.surface.ApiVariant
import com.android.tools.metalava.model.api.surface.ApiVariantType
import com.android.tools.metalava.model.createImmutableModifiers
import com.android.tools.metalava.model.createMutableModifiers
import com.android.tools.metalava.model.item.DefaultCodebase
import com.android.tools.metalava.model.item.PackageInfo
import com.android.tools.metalava.model.item.SealedClassImplicitPermitTypesUpdater
import com.android.tools.metalava.model.multiplatform.MultiplatformCodebase
import com.android.tools.metalava.model.parser.FileLocationTracker
import com.android.tools.metalava.model.parser.TokenPurpose
import com.android.tools.metalava.model.parser.Tokenizer
import com.android.tools.metalava.model.text.CustomizableProperty.Companion.KOTLIN_NAME_TYPE_ORDER
import com.android.tools.metalava.model.text.CustomizableProperty.Companion.KOTLIN_STYLE_NULLS
import com.android.tools.metalava.model.type.MethodFingerprint
import com.android.tools.metalava.model.type.TypeItemParser
import com.android.tools.metalava.model.type.TypeItemParserErrorReporter
import com.android.tools.metalava.model.type.TypeParameterListAndFactory
import com.android.tools.metalava.model.utils.extractOptionalQualifierName
import com.android.tools.metalava.model.utils.extractSimpleName
import com.android.tools.metalava.model.value.Value
import com.android.tools.metalava.model.value.ValueParser
import com.android.tools.metalava.model.value.ValueUseSite
import com.android.tools.metalava.reporter.FileLocation
import com.android.tools.metalava.reporter.Issues
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.StringReader
import java.nio.file.Path
import kotlin.text.Charsets.UTF_8

/** Encapsulates information needed to process a signature file. */
sealed class SignatureFile {
    /** The underlying signature [File]. */
    abstract val file: File

    /**
     * Indicates whether [file] is for the main API surface, i.e. the one that is being created, or
     * a base API surface that it extends.
     */
    protected open val forMainApiSurface: Boolean
        get() = true

    /** The [ApiVariantType] of the signature files. */
    protected open val apiVariantType: ApiVariantType
        get() = ApiVariantType.CORE

    /**
     * Get the [ApiVariant] that this signature file represents.
     *
     * If [forMainApiSurface] is `false` then [apiSurfaces] must provide a non-null value for
     * [ApiSurfaces.base]. An exception will be thrown if it is not.
     *
     * @param apiSurfaces the [ApiSurfaces] the returned [Codebase] is required to support.
     */
    fun apiVariantFor(apiSurfaces: ApiSurfaces): ApiVariant {
        val apiSurface =
            if (forMainApiSurface) apiSurfaces.main
            else
                apiSurfaces.base
                    ?: error("$file expects a base API surface to be available but it is not")
        return apiSurface.variantFor(apiVariantType)
    }

    /** Read the contents of this signature file. */
    abstract fun readContents(): String

    companion object {
        /** Create a list of [SignatureFile]s from a varargs array of [File]s. */
        fun fromFiles(vararg files: File): List<SignatureFile> =
            files.map {
                SignatureFileFromFile(
                    it,
                )
            }

        /**
         * Create a list of [SignatureFile]s from a list of [File]s.
         *
         * @param files the list of [File]s.
         * @param apiVariantTypeChooser A lambda that will be called with the [File] of each item in
         *   [files] and whose return value will be stored in [SignatureFile.apiVariantType].
         * @param forMainApiSurfacePredicate A predicate that will be called with the index and
         *   [File] of each item in [files] and whose return value will be stored in
         *   [SignatureFile.forMainApiSurface].
         */
        fun fromFiles(
            files: List<File>,
            apiVariantTypeChooser: (File) -> ApiVariantType = { ApiVariantType.CORE },
            forMainApiSurfacePredicate: (Int, File) -> Boolean = { _, _ -> true },
        ): List<SignatureFile> =
            files.mapIndexed { index, file ->
                SignatureFileFromFile(
                    file,
                    forMainApiSurface = forMainApiSurfacePredicate(index, file),
                    apiVariantType = apiVariantTypeChooser(file),
                )
            }

        /** Create a [SignatureFile] that wraps an [InputStream]. */
        fun fromStream(filename: String, inputStream: InputStream): SignatureFile {
            return SignatureFileFromStream(File(filename), inputStream)
        }

        /**
         * Create a [SignatureFile] that wraps a [String].
         *
         * @param filename the name of the file, used for error reporting.
         * @param contents the contents of the file, will be trimmed using [String.trimIndent].
         */
        fun fromText(filename: String, contents: String): SignatureFile {
            return SignatureFileFromText(File(filename), contents.trimIndent())
        }
    }

    /** A [SignatureFile] that will read the text from the [file]. */
    private data class SignatureFileFromFile(
        override val file: File,
        override val forMainApiSurface: Boolean = true,
        override val apiVariantType: ApiVariantType = ApiVariantType.CORE,
    ) : SignatureFile() {
        override fun readContents() =
            try {
                file.readText(UTF_8)
            } catch (ex: IOException) {
                throw ApiParseException(
                        "Error reading API file",
                        location = FileLocation.createLocation(file.toPath()),
                    )
                    .apply { initCause(ex) }
            }
    }

    /** A [SignatureFile] that wraps an [InputStream]. */
    private data class SignatureFileFromStream(
        override val file: File,
        val inputStream: InputStream,
    ) : SignatureFile() {
        override fun readContents() = inputStream.bufferedReader().readText()
    }

    /** A [SignatureFile] that wraps a [String]. */
    private data class SignatureFileFromText(
        override val file: File,
        val contents: String,
    ) : SignatureFile() {
        override fun readContents() = contents
    }
}

@MetalavaApi
class ApiFile
private constructor(
    /** Location to use for the created [Codebase]. */
    codebaseLocation: File,
    /** Description to use for the created [Codebase]. */
    codebaseDescription: String,
    /** [Codebase.Config] to use for the created [Codebase]. */
    codebaseConfig: Codebase.Config,
    /** [ClassPathResolver] to use for the created [Codebase]. */
    classPathResolver: ClassPathResolver?,
    private val formatForLegacyFiles: FileFormat?,
    allowClassModifierChanges: Boolean,
    /** The [TargetLanguageSet] to use if an item does not have one specified. */
    private val defaultTargetLanguageSet: Set<TargetLanguage> = TargetLanguageSet.ALL,
) {
    private val assembler =
        TextCodebaseAssembler.createAssembler(
            codebaseLocation,
            codebaseDescription,
            codebaseConfig,
            classPathResolver
        )

    private val codebase = assembler.codebase

    /**
     * The [FileLocationTracker] for the current file being parsed.
     *
     * Set by [parseMultipleFiles].
     */
    private lateinit var fileLocationTracker: FileLocationTracker

    /** Report recoverable errors encountered while parsing types. */
    private val typeItemParserErrorReporter =
        object : TypeItemParserErrorReporter {
            override fun report(issue: Issues.Issue, message: String) {
                reportIssue(issue, message)
            }
        }

    /**
     * Provides support for parsing and caching [TypeItem]s.
     *
     * Defer creation until after the first file has been read and [deferredKotlinStyleNulls] has
     * been set to a non-null value to ensure that it picks up the correct setting of
     * [kotlinStyleNulls].
     */
    private val typeParser by
        lazy(LazyThreadSafetyMode.NONE) {
            TextTypeParser(codebase, kotlinStyleNulls, typeItemParserErrorReporter)
        }

    /**
     * Provides support for creating [TypeItem]s for specific uses.
     *
     * Defer creation as it depends on [typeParser].
     */
    private val globalTypeItemFactory by
        lazy(LazyThreadSafetyMode.NONE) { TextTypeItemFactory(assembler, typeParser) }

    /** The [ValueParser] to use for creating [Value]s from a signature file. */
    private val valueParser =
        ValueParser(
            codebase,
            TypeItemParser.forValueParser(codebase, typeItemParserErrorReporter),
        )

    /**
     * Backing property for [kotlinStyleNulls]; should not be read directly outside
     * [parseMultipleFiles] where it is initialized. All other code should read [kotlinStyleNulls]
     * instead.
     */
    private var deferredKotlinStyleNulls: Boolean? = null

    /**
     * Whether types should be interpreted to be in Kotlin format (e.g. `?` suffix means nullable,
     * `!` suffix means unknown, and absence of a suffix means not nullable).
     *
     * Initialized from the header of the signature file being parsed in [parseMultipleFiles], so it
     * is only safe to read after the header of the first signature file has been parsed.
     */
    private val kotlinStyleNulls: Boolean
        get() = deferredKotlinStyleNulls!!

    /** Merges class re-definitions across signature files. */
    private val classMerger = ClassMerger(allowClassModifierChanges)

    companion object {
        /**
         * Parse API signature files.
         *
         * Used by non-Metalava Kotlin code.
         */
        @MetalavaApi
        fun parseApi(
            files: List<File>,
        ) = parseApi(SignatureFile.fromFiles(files))

        /**
         * Read API signature files into a [DefaultCodebase].
         *
         * Note: when reading from them multiple files, [DefaultCodebase.location] would refer to
         * the first file specified. each [Item.fileLocation] would correctly point out the source
         * file of each item.
         *
         * @param signatureFiles input signature files
         */
        fun parseApi(
            signatureFiles: List<SignatureFile>,
            codebaseConfig: Codebase.Config = Codebase.Config.NOOP,
            description: String? = null,
            classPathResolver: ClassPathResolver? = null,
            formatForLegacyFiles: FileFormat? = null,
            /** Whether different signature files can have non-equivalent modifiers for a class. */
            allowClassModifierChanges: Boolean = false,
            // Provides the called with access to the ApiFile.
            apiStatsConsumer: (Stats) -> Unit = {},
        ): Codebase {
            require(signatureFiles.isNotEmpty()) { "files must not be empty" }
            val actualDescription =
                description
                    ?: buildString {
                        append("Codebase loaded from ")
                        signatureFiles.joinTo(this)
                        if (classPathResolver == null) {
                            append(" without a class path resolver")
                        } else {
                            append(" with class path resolver ")
                            append(classPathResolver)
                        }
                    }
            val parser =
                ApiFile(
                    codebaseLocation = signatureFiles[0].file,
                    codebaseDescription = actualDescription,
                    codebaseConfig = codebaseConfig,
                    classPathResolver = classPathResolver,
                    formatForLegacyFiles = formatForLegacyFiles,
                    allowClassModifierChanges = allowClassModifierChanges
                )
            parser.parseMultipleFiles(signatureFiles)

            val codebase = parser.codebase

            // Update implicit permit types in any sealed class that does not have one provided.
            SealedClassImplicitPermitTypesUpdater.updateImplicitPermitTypes(codebase)

            apiStatsConsumer(parser.stats)
            return codebase
        }

        /**
         * Parses the [signatureFiles] into a [MultiplatformCodebase].
         *
         * Each signature file represents a source set. If there is a common signature file, all
         * other signature files are parsed as a delta on the common one.
         */
        fun parseMultiplatformApi(
            signatureFiles: List<SignatureFile>,
            codebaseConfig: Codebase.Config = Codebase.Config.NOOP,
        ): MultiplatformCodebase {
            // Find the common signature file, if it exists.
            val commonSignatureFile =
                signatureFiles.firstOrNull {
                    it.file.nameWithoutExtension ==
                        MultiplatformSignatureWriter.COMMON_SOURCE_SET_NAME
                }
            val sourceSetToCodebase =
                if (commonSignatureFile != null) {
                    // When there is a common source set, each other signature file is parsed as an
                    // extension on common.
                    val commonNameToSourceSet = parseSourceSet(codebaseConfig, commonSignatureFile)
                    signatureFiles.associate { signatureFile ->
                        if (signatureFile == commonSignatureFile) {
                            commonNameToSourceSet
                        } else {
                            parseSourceSet(codebaseConfig, signatureFile, commonSignatureFile)
                        }
                    }
                } else {
                    // When there is no common source set, each signature file is parsed separately.
                    signatureFiles.associate { signatureFile ->
                        parseSourceSet(codebaseConfig, signatureFile)
                    }
                }
            return MultiplatformCodebase(sourceSetToCodebase)
        }

        /**
         * Parses the [sourceSetSignatureFile], returning a pair of the name of the source set to
         * the [Codebase] for the source set.
         *
         * If [baseSignatureFile] is not null, it is parsed first with [sourceSetSignatureFile]
         * treated as an extension.
         */
        private fun parseSourceSet(
            codebaseConfig: Codebase.Config,
            sourceSetSignatureFile: SignatureFile,
            baseSignatureFile: SignatureFile? = null,
        ): Pair<String, Codebase> {
            val name = sourceSetSignatureFile.file.nameWithoutExtension
            val parser =
                ApiFile(
                    codebaseLocation = sourceSetSignatureFile.file,
                    codebaseDescription = "Codebase for source set $name",
                    codebaseConfig = codebaseConfig,
                    classPathResolver = null,
                    formatForLegacyFiles = null,
                    allowClassModifierChanges = true,
                    defaultTargetLanguageSet = TargetLanguageSet.KOTLIN_ONLY,
                )
            // Parse the base file first if it exists.
            parser.parseMultipleFiles(listOfNotNull(baseSignatureFile) + sourceSetSignatureFile)
            return name to parser.codebase
        }

        /**
         * Parse the API signature file from the [inputStream].
         *
         * This will consume the whole contents of the [inputStream] but it is the caller's
         * responsibility to close it.
         */
        @JvmStatic
        @MetalavaApi
        @Throws(ApiParseException::class)
        fun parseApi(filename: String, inputStream: InputStream): Codebase {
            val signatureFile = SignatureFile.fromStream(filename, inputStream)
            return parseApi(listOf(signatureFile))
        }
    }

    /**
     * Report a recoverable issue encountered while parsing.
     *
     * Retrieves the location of the error from [fileLocationTracker].
     *
     * Note: Non-recoverable issues result in an exception being thrown.
     */
    private fun reportIssue(issue: Issues.Issue, message: String) {
        val location = fileLocationTracker.fileLocation()
        codebase.reporter.report(issue, null, message, location)
    }

    /**
     * Parses all the [signatureFiles], treating the first file as the base API and all other files
     * as extensions.
     */
    private fun parseMultipleFiles(signatureFiles: List<SignatureFile>) {
        val apiSurfaces = codebase.config.apiSurfaces
        var appending = false
        var previousPath: Path? = null
        var previousKotlinStyleNulls: Boolean? = null
        var parserContext: ParserContext? = null
        for (signatureFile in signatureFiles) {
            // When we're appending, and the content is empty, there is nothing to do.
            val apiText = signatureFile.readContents()
            if (appending && apiText.isBlank()) {
                continue
            }

            val path = signatureFile.file.toPath()
            val apiVariant = signatureFile.apiVariantFor(apiSurfaces)

            // Parse the header of the signature file to determine the format. If the signature file
            // is empty then `parseHeader` will return null, so it will default to `FileFormat.V2`.
            val format =
                FileFormat.parseHeader(path, StringReader(apiText), formatForLegacyFiles)
                    ?: FileFormat.V2

            // Disallow a mixture of kotlinStyleNulls settings.
            val kotlinStyleNullsForThisFile = format[KOTLIN_STYLE_NULLS]
            if (
                previousKotlinStyleNulls != null &&
                    previousKotlinStyleNulls != kotlinStyleNullsForThisFile
            ) {
                codebase.reporter.report(
                    Issues.SIGNATURE_FILE_ERROR,
                    null,
                    "Preceding file $previousPath has different setting of kotlin-style-nulls which may cause issues",
                    FileLocation.createLocation(path, 1),
                )
            }
            previousPath = path
            previousKotlinStyleNulls = kotlinStyleNullsForThisFile
            deferredKotlinStyleNulls = kotlinStyleNullsForThisFile

            val context = parserContext ?: createParserContext().also { parserContext = it }

            val tokenizer = Tokenizer(path, apiText, ::ApiParseException)

            // Set the file location tracker to provide location information about the current file.
            fileLocationTracker = tokenizer

            val parser =
                SingleSignatureFileParser(
                    context = context,
                    tokenizer = tokenizer,
                    appending = appending,
                    kotlinStyleNulls = kotlinStyleNulls,
                    kotlinNameTypeOrder = format[KOTLIN_NAME_TYPE_ORDER],
                    apiVariant = apiVariant,
                )
            parser.parse()

            appending = true
        }

        // Known to be non-null as `signatureFiles` is never empty and the first file is never
        // skipped.
        parserContext!!

        classMerger.performAnyDeferredMerges()
    }

    /**
     * Creates the [ParserContext] shared across all signature files parsed by [parseMultipleFiles].
     */
    private fun createParserContext(): ParserContext =
        ParserContext(
            assembler = assembler,
            typeParser = typeParser,
            globalTypeItemFactory = globalTypeItemFactory,
            valueParser = valueParser,
            defaultTargetLanguageSet = defaultTargetLanguageSet,
            classMerger = classMerger,
        )

    private val stats
        get() =
            Stats(
                codebase.getPackages().allClasses().count(),
                typeParser.requests,
                typeParser.cacheSkip,
                typeParser.cacheHit,
                typeParser.cacheSize,
            )

    data class Stats(
        val totalClasses: Int,
        val typeCacheRequests: Int,
        val typeCacheSkip: Int,
        val typeCacheHit: Int,
        val typeCacheSize: Int,
    )
}

/**
 * Context shared across [SingleSignatureFileParser] instances when parsing multiple signature files
 * into a single [Codebase].
 */
internal class ParserContext(
    /** Populates the [Codebase] from the parsed signature file. */
    val assembler: TextCodebaseAssembler,

    /** Provides support for parsing and caching [TypeItem]s. */
    val typeParser: TextTypeParser,

    /** Provides support for creating [TypeItem]s for specific uses. */
    val globalTypeItemFactory: TextTypeItemFactory,

    /** The [ValueParser] to use for creating [Value]s from a signature file. */
    val valueParser: ValueParser,

    /** The [TargetLanguageSet] to use if an item does not have one specified. */
    val defaultTargetLanguageSet: Set<TargetLanguage>,

    /** Merges class re-definitions across signature files. */
    val classMerger: ClassMerger,
)

/** Parser for a single signature file. */
internal class SingleSignatureFileParser(
    context: ParserContext,

    /** The [Tokenizer] for the file being parsed. */
    private val tokenizer: Tokenizer,

    /**
     * True if this is appending information from one signature file to a [Codebase] created from
     * another signature file.
     */
    private val appending: Boolean,

    /**
     * Whether types should be interpreted to be in Kotlin format (e.g. `?` suffix means nullable,
     * `!` suffix means unknown, and absence of a suffix means not nullable).
     */
    private val kotlinStyleNulls: Boolean,

    /** See [KOTLIN_NAME_TYPE_ORDER]. */
    private val kotlinNameTypeOrder: Boolean,

    /** The [ApiVariant] which is defined within the current signature file being parsed. */
    private val apiVariant: ApiVariant,
) {
    private val assembler = context.assembler
    private val codebase = assembler.codebase

    /** Creates [Item] instances for [codebase]. */
    private val itemFactory = assembler.itemFactory

    private val typeParser = context.typeParser
    private val globalTypeItemFactory = context.globalTypeItemFactory
    private val valueParser = context.valueParser
    private val defaultTargetLanguageSet = context.defaultTargetLanguageSet
    private val classMerger = context.classMerger

    companion object {
        /**
         * Extracts the bounds string list from the [typeParameterString].
         *
         * Given `T extends a.B & b.C<? super T>` this will return a list of `a.B` and `b.C<? super
         * T>`.
         */
        fun extractTypeParameterBoundsStringList(typeParameterString: String?): List<String> {
            val s = typeParameterString ?: return emptyList()
            val index = s.indexOf("extends ")
            if (index == -1) {
                return emptyList()
            }
            val list = mutableListOf<String>()
            var angleBracketBalance = 0
            var start = index + "extends ".length
            val length = s.length
            for (i in start until length) {
                val c = s[i]
                if (c == '&' && angleBracketBalance == 0) {
                    addNonBlankStringToList(list, typeParameterString, start, i)
                    start = i + 1
                } else if (c == '<') {
                    angleBracketBalance++
                } else if (c == '>') {
                    angleBracketBalance--
                    if (angleBracketBalance == 0) {
                        addNonBlankStringToList(list, typeParameterString, start, i + 1)
                        start = i + 1
                    }
                }
            }
            if (start < length) {
                addNonBlankStringToList(list, typeParameterString, start, length)
            }
            return list
        }

        private fun addNonBlankStringToList(
            list: MutableList<String>,
            s: String,
            from: Int,
            to: Int
        ) {
            val element = s.substring(from, to).trim()
            if (element.isNotEmpty()) list.add(element)
        }
    }

    /**
     * Report a recoverable issue encountered while parsing.
     *
     * Retrieves the location of the error from [tokenizer].
     *
     * Note: Non-recoverable issues result in an exception being thrown.
     */
    private fun reportIssue(issue: Issues.Issue, message: String) {
        val location = tokenizer.fileLocation()
        codebase.reporter.report(issue, null, message, location)
    }

    /**
     * Record that this [SelectableItem] was loaded from a signature file that contains
     * [apiVariant].
     *
     * If this class was already defined in a different API surface, this will not add the new
     * surface.
     */
    private fun SelectableItem.markSelectedApiVariant() {
        selectedApi.addItemApiVariant(apiVariant)
    }

    /** Parse the signature file from [tokenizer], populating [codebase]. */
    fun parse() {
        while (true) {
            val token = tokenizer.getToken() ?: break
            // TODO: Accept annotations on packages.
            if ("package" == token) {
                parsePackage()
            } else {
                throw ApiParseException("expected package got $token", tokenizer)
            }
        }
    }

    /**
     * Find an existing package called [name] or create a new one.
     *
     * If an existing package exists then this makes sure that its annotations match [annotations].
     */
    private fun findOrCreatePackage(name: String, annotations: List<AnnotationItem>): PackageItem {
        // Check to see if the package already exists, if it does then return it.
        codebase.findPackage(name)?.let { existing ->
            // If the same package showed up multiple times, make sure they have the same modifiers.
            // (Packages can't have public/private/etc., but they can have annotations, which are
            // part of ModifierList.)
            val existingAnnotations = existing.modifiers.annotations()
            if (annotations != existingAnnotations) {
                throw ApiParseException(
                    String.format(
                        "Contradicting declaration of package %s." +
                            " Previously seen with annotations \"%s\", but now with \"%s\"",
                        name,
                        existingAnnotations,
                        annotations
                    ),
                    tokenizer,
                )
            }

            return existing
        }

        // Wrap the file location and annotations in a PackageInfo.
        val packageInfo =
            PackageInfo(
                fileLocation = tokenizer.fileLocation(),
                annotations = annotations,
                // Packages loaded from signature files have [SelectableItem.documentation] set to
                // `null`. That is not a problem as it is only needed when creating stubs containing
                // enhanced documentation which cannot be created from signature files.
                commentFactory = ItemDocumentation.NONE_FACTORY,
            )

        // Create the package. This relies on containing packages always being processed before any
        // contained package which is guaranteed by the signature file order.
        return codebase.packageTracker.createPackage(name, packageInfo)
    }

    private fun parsePackage() {
        tokenizer.requireToken()

        // Metalava: including annotations in file now
        val annotations = getAnnotations()
        var token = tokenizer.current
        tokenizer.assertIdent(token)
        val name: String = token

        val pkg = findOrCreatePackage(name, annotations)

        // Note: pkg.markSelectedApiVariant() is not called here because packages do not belong to
        // an API surface in their own right; their API variants are populated via propagation from
        // their contained classes and members.

        token = tokenizer.requireToken()
        if ("{" != token) {
            throw ApiParseException("expected '{' got $token", tokenizer)
        }
        while (true) {
            token = tokenizer.requireToken()
            if ("}" == token) {
                break
            } else {
                parseClass(pkg)
            }
        }
    }

    /**
     * Creates a type alias in the [pkg] with the [modifiers].
     *
     * It is expected that the starting position of the [tokenizer] is the "typealias" keyword, and
     * the next token will be the name and option type parameter list.
     *
     * When the method returns, the current [tokenizer] position will be the ";" at the end of the
     * typealias line.
     */
    private fun parseTypeAlias(
        pkg: PackageItem,
        modifiers: MutableModifierList,
        location: FileLocation
    ) {
        var token = tokenizer.requireToken()
        tokenizer.assertIdent(token)

        val typeParameterListIndex = token.indexOf("<")

        val (name, typeParameterList, typeItemFactory) =
            if (typeParameterListIndex == -1) {
                Triple(token, TypeParameterList.NONE, globalTypeItemFactory)
            } else {
                val name = token.substring(0, typeParameterListIndex)
                val typeParameterListAndFactory =
                    createTypeParameterList(
                        globalTypeItemFactory,
                        "typealias $name",
                        token.substring(typeParameterListIndex)
                    )
                Triple(
                    name,
                    typeParameterListAndFactory.typeParameterList,
                    typeParameterListAndFactory.factory
                )
            }
        val qualifiedClassName = pkg.qualifiedName() + "." + name

        token = tokenizer.requireToken()
        if ("=" != token) {
            throw ApiParseException("expected = found $token", tokenizer)
        }

        tokenizer.requireToken()
        val typeString = scanForTypeString()
        token = tokenizer.current
        if (";" != token) {
            throw ApiParseException("expected ; found $token", tokenizer)
        }

        val type = typeItemFactory.getGeneralType(typeString)

        // Check for the existing class from a previously parsed file. If it was found then use that
        // and return. If it could not be found then drop through to create it.
        val classCharacteristics =
            ClassCharacteristics(
                fileLocation = location,
                qualifiedName = qualifiedClassName,
                fullName = name,
                classKind = ClassKind.TYPEALIAS,
                modifiers = modifiers.toImmutable(),
                superClassType = null,
                interfaceTypes = emptySet(),
                optionalAliasedType = type,
            )
        if (checkForExistingClass(classCharacteristics)) {
            return
        }

        val typeAlias =
            itemFactory.createTypeAliasItem(
                fileLocation = location,
                modifiers = modifiers,
                qualifiedName = pkg.qualifiedName() + "." + name,
                containingPackage = pkg,
                aliasedType = type,
                typeParameterList = typeParameterList,
                // All signature files have to be explicitly specified.
                origin = ClassOrigin.COMMAND_LINE,
            )
        // Mark type alias as belonging to the main API surface of this signature file.
        typeAlias.markSelectedApiVariant()
    }

    /** Parse a class starting with [Tokenizer.current]. */
    private fun parseClass(pkg: PackageItem) {
        val (modifiers, targetLanguages) = parseModifiersAndTargetLanguages()
        // Remember this position as this seems like a good place to use to report issues with the
        // class item.
        val classPosition = tokenizer.fileLocation()

        var token = tokenizer.current
        val classKind =
            ClassKind.bySignatureKeyword(token)
                ?: throw ApiParseException(
                    "expected one of ${ClassKind.entries.joinToString { it.signatureKeyword }}; found: $token",
                    tokenizer
                )

        if (classKind == ClassKind.TYPEALIAS) {
            // Type aliases aren't classes, but they are defined at the same level as classes
            parseTypeAlias(pkg, modifiers, classPosition)
            // Don't continue creating a class item
            return
        }

        classKind.setImplicitModifiers(modifiers)

        var superClassType = classKind.implicitSuperClassType

        token = tokenizer.requireToken()
        tokenizer.assertIdent(token)

        // The declaredClassType consists of the full name (i.e. preceded by the containing class's
        // full name followed by a '.' if there is one) plus the type parameter string.
        val declaredClassType: String = token

        // Extract lots of information from the declared class type.
        val (
            fullName,
            qualifiedClassName,
            outerClass,
            typeParameterList,
            typeItemFactory,
        ) = parseDeclaredClassType(pkg, declaredClassType, classPosition)

        token = tokenizer.requireToken()

        if ("extends" == token && classKind != ClassKind.INTERFACE) {
            tokenizer.requireToken()
            val superClassTypeString = parseSuperTypeString()
            superClassType =
                typeItemFactory.getSuperClassType(
                    superClassTypeString,
                )
            token = tokenizer.current
        }

        val interfaceTypes = mutableSetOf<ClassTypeItem>()

        // Add any ClassKind specific implicit interface types.
        classKind.implicitInterfaceType?.let { interfaceType -> interfaceTypes.add(interfaceType) }

        if ("implements" == token || "extends" == token) {
            token = tokenizer.requireToken()
            while (true) {
                if (token == "{" || token == "permits") {
                    break
                } else if ("," != token) {
                    val interfaceTypeString = parseSuperTypeString()
                    val interfaceType = typeItemFactory.getInterfaceType(interfaceTypeString)
                    interfaceTypes.add(interfaceType)
                    token = tokenizer.current
                } else {
                    token = tokenizer.requireToken()
                }
            }
        }

        val permitTypes = mutableListOf<ClassTypeItem>()

        if (token == "permits") {
            token = tokenizer.requireToken()
            while (true) {
                if ("{" == token) {
                    break
                } else {
                    val typeString = parseSuperTypeString()
                    val permitsType = typeItemFactory.getHierarchicalClassType(typeString)
                    permitTypes.add(permitsType)
                    token = tokenizer.current
                }
            }
            permitTypes.sortWith(TypeItem.qualifiedComparator)
        }

        if ("{" != token) {
            throw ApiParseException("expected {, was $token", tokenizer)
        }
        // Move to the next token.
        tokenizer.requireToken()

        // Above we marked all enums as static but for a top level class it's implicit
        if (classKind == ClassKind.ENUM && !fullName.contains(".")) {
            modifiers.setStatic(false)
        }

        // Check for the existing class from a previously parsed file. If it was found then use that
        // and return. If it could not be found then drop through to create it.
        val classCharacteristics =
            ClassCharacteristics(
                fileLocation = classPosition,
                qualifiedName = qualifiedClassName,
                fullName = fullName,
                classKind = classKind,
                modifiers = modifiers.toImmutable(),
                superClassType = superClassType,
                interfaceTypes = interfaceTypes,
                optionalAliasedType = null,
            )
        if (checkForExistingClass(classCharacteristics)) {
            return
        }

        // Default the superClassType() to java.lang.Object for any class that is not an interface,
        // annotation, or enum and which is not itself java.lang.Object.
        if (
            classKind == ClassKind.CLASS &&
                superClassType == null &&
                qualifiedClassName != JAVA_LANG_OBJECT
        ) {
            superClassType = WellKnownTypes.JAVA_LANG_OBJECT_NON_NULL_TYPE
        }

        val textRecordComponents =
            if (classKind == ClassKind.RECORD) {
                // Parse record components
                parseRecordComponents()
            } else {
                null
            }

        // Create the DefaultClassItem and set its package but do not add it to the package or
        // register it.
        val cl =
            itemFactory.createClassItem(
                fileLocation = classPosition,
                modifiers = modifiers,
                classKind = classKind,
                containingClass = outerClass,
                containingPackage = pkg,
                qualifiedName = qualifiedClassName,
                typeParameterList = typeParameterList,
                // All signature files have to be explicitly specified.
                origin = ClassOrigin.COMMAND_LINE,
                superClassType = superClassType,
                interfaceTypes = interfaceTypes.toList(),
                permitTypes = permitTypes,
                targetLanguages = targetLanguages,
                // Classes with the placeholder name for top level declarations in a
                // MultiplatformCodebase are definitely facade classes. There isn't enough
                // information to tell for other classes, so this defaults to false otherwise.
                isFileFacade = fullName == ClassItem.TOP_LEVEL_DECLARATION_FACADE_NAME,
                recordComponentItemsFactory =
                    if (textRecordComponents == null) null
                    else
                        { classItem ->
                            textRecordComponents.map {
                                it.createRecordComponent(classItem, typeItemFactory)
                            }
                        }
            )
        cl.markSelectedApiVariant()

        // Parse the class body adding each member created to the class item being populated.
        parseClassBody(cl, typeItemFactory)
    }

    /**
     * Checks to see if there is an existing class with the same qualified name as
     * [classCharacteristics] already existing in the codebase. If there is, marks that the
     * [classCharacteristics] should be merged into the existing class.
     *
     * Returns whether a matching class was found.
     */
    private fun checkForExistingClass(
        classCharacteristics: ClassCharacteristics,
    ): Boolean {
        val existingClass =
            codebase.findClassInCodebase(classCharacteristics.qualifiedName) ?: return false

        // Parse the class body adding each member created to the existing class (typealiases do not
        // have a class body).
        if (classCharacteristics.classKind != ClassKind.TYPEALIAS) {
            parseClassBody(existingClass, typeItemFactoryForClass(existingClass))
        }

        // Perform any merge checks after loading all the files. That is needed because merging
        // may resolve classes and doing that during parsing can lead to issues.
        classMerger.deferMergingIntoExistingClass(existingClass, classCharacteristics)

        return true
    }

    /** Get the [TextTypeItemFactory] for a previously created [ClassItem]. */
    private fun typeItemFactoryForClass(classItem: ClassItem?): TextTypeItemFactory =
        globalTypeItemFactory.from(classItem)

    /** Map from class member kind token to its parse function. */
    private val classMemberKindToParseFunction =
        mapOf<String, (SkeletonClassItem, TextTypeItemFactory) -> Unit>(
            "ctor" to ::parseConstructor,
            "enum_constant" to ::parseEnumConstant,
            "field" to ::parseField,
            "method" to ::parseMethod,
            "property" to ::parseProperty,
        )

    /**
     * Parse the class body, adding members to [containingClass].
     *
     * Starts with [Tokenizer.current]. On return [Tokenizer.current] points to the next token after
     * the last member.
     */
    private fun parseClassBody(
        containingClass: SkeletonClassItem,
        classTypeItemFactory: TextTypeItemFactory,
    ) {
        var token = tokenizer.current
        while (true) {
            if ("}" == token) {
                break
            } else {
                val parseFunction =
                    classMemberKindToParseFunction[token]
                        ?: throw ApiParseException(
                            "expected one of ${classMemberKindToParseFunction.keys.joinToString()}",
                            tokenizer
                        )
                parseFunction(containingClass, classTypeItemFactory)
            }
            token = tokenizer.requireToken()
        }
    }

    /**
     * Parse a super type string, i.e. a string representing a super class type or a super interface
     * type.
     */
    private fun parseSuperTypeString(): String {
        var token = getAnnotationCompleteToken()

        // Use the token directly if it is complete, otherwise construct the super class type
        // string from as many tokens as necessary.
        return if (!isIncompleteTypeToken(token)) {
            token
        } else {
            buildString {
                append(token)

                // Make sure full super class name is found if there are type use
                // annotations. This can't use [parseType] because the next token might be a
                // separate type (classes only have a single `extends` type, but all
                // interface supertypes are listed as `extends` instead of `implements`).
                // However, this type cannot be an array, so unlike [parseType] this does
                // not need to check if the next token has annotations.
                do {
                    token = getAnnotationCompleteToken()
                    append(" ")
                    append(token)
                } while (isIncompleteTypeToken(token))
            }
        }
    }

    /** Encapsulates multiple return values from [parseDeclaredClassType]. */
    private data class DeclaredClassTypeComponents(
        /** The full name of the class, including outer class prefix. */
        val fullName: String,
        /** The fully qualified name, including package and full name. */
        val qualifiedName: String,
        /** The optional, resolved outer [ClassItem]. */
        val outerClass: SkeletonClassItem?,
        /** The set of type parameters. */
        val typeParameterList: TypeParameterList,
        /**
         * The [TextTypeItemFactory] including any type parameters in the [typeParameterList] in its
         * [TextTypeItemFactory.typeParameterScope].
         */
        val typeItemFactory: TextTypeItemFactory,
    )

    /**
     * Splits the declared class type into [DeclaredClassTypeComponents].
     *
     * For example "Foo" would split into full name "Foo" and an empty type parameter list, while
     * `"Foo.Bar<A, B extends java.lang.String, C>"` would split into full name `"Foo.Bar"` and type
     * parameter list with `"A"`,`"B extends java.lang.String"`, and `"C"` as type parameters.
     *
     * If the qualified name matches an existing class then return its information.
     */
    private fun parseDeclaredClassType(
        pkg: PackageItem,
        declaredClassType: String,
        classFileLocation: FileLocation,
    ): DeclaredClassTypeComponents {
        // Split the declared class type into full name and type parameters.
        val paramIndex = declaredClassType.indexOf('<')
        val (fullName, typeParameterListString) =
            if (paramIndex == -1) {
                Pair(declaredClassType, "")
            } else {
                Pair(
                    declaredClassType.substring(0, paramIndex),
                    declaredClassType.substring(paramIndex)
                )
            }
        val pkgName = pkg.qualifiedName()
        val qualifiedName = qualifiedName(pkgName, fullName)

        // Split the full name into an optional outer class and a simple name.
        val outerClassFullName = fullName.extractOptionalQualifierName()
        val outerClass =
            if (outerClassFullName == null) {
                null
            } else {
                val qualifiedOuterClassName = qualifiedName(pkgName, outerClassFullName)

                // Search for the outer class in the codebase. This is safe as the outer class
                // always precedes its nested classes.
                assembler.getOrCreateClass(
                    qualifiedOuterClassName,
                    isOuterClassOfClassInThisCodebase = true
                ) as SkeletonClassItem
            }

        // Get the [TextTypeItemFactory] for the outer class, if any.
        val outerClassTypeItemFactory = typeItemFactoryForClass(outerClass)

        // Create type parameter list and factory from the string and optional outer class factory.
        val (typeParameterList, typeItemFactory) =
            if (typeParameterListString == "")
                TypeParameterListAndFactory(TypeParameterList.NONE, outerClassTypeItemFactory)
            else
                createTypeParameterList(
                    outerClassTypeItemFactory,
                    "class $qualifiedName",
                    typeParameterListString,
                )

        // Decide which type parameter list and factory to actually use.
        //
        // If the class already exists then reuse its type parameter list and factory, otherwise use
        // the newly created one.
        //
        // The reason for this is that otherwise any types parsed with the newly created factory
        // would reference type parameters in the newly created list which are different to the ones
        // belonging to the existing class.
        val (actualTypeParameterList, actualTypeItemFactory) =
            codebase.findClassInCodebase(qualifiedName)?.let { existingClass ->
                // Check to make sure that the type parameter lists are the same.
                val existingTypeParameterList = existingClass.typeParameterList
                val existingTypeParameterListString = existingTypeParameterList.toString()
                val normalizedTypeParameterListString = typeParameterList.toString()
                if (normalizedTypeParameterListString != existingTypeParameterListString) {
                    val location = existingClass.fileLocation
                    throw ApiParseException(
                        "Inconsistent type parameter list for $qualifiedName, this has $normalizedTypeParameterListString but it was previously defined as $existingTypeParameterListString at $location",
                        classFileLocation
                    )
                }

                Pair(existingTypeParameterList, typeItemFactoryForClass(existingClass))
            } ?: Pair(typeParameterList, typeItemFactory)

        return DeclaredClassTypeComponents(
            fullName = fullName,
            qualifiedName = qualifiedName,
            outerClass = outerClass,
            typeParameterList = actualTypeParameterList,
            typeItemFactory = actualTypeItemFactory,
        )
    }

    /**
     * If [Tokenizer.current] contains the beginning of an annotation, pulls additional tokens from
     * [tokenizer] to complete the annotation, returning the full token. If there isn't an
     * annotation, returns the original [Tokenizer.current].
     *
     * When the method returns, the [tokenizer] will point to the token after the end of the
     * returned string.
     *
     * @return the complete token string.
     */
    private fun getAnnotationCompleteToken(): String {
        val startingToken = tokenizer.current
        val atIndex = startingToken.indexOf('@')
        return if (atIndex != -1) {
            // An annotation starts at or within this token (e.g. `@Nullable` or
            // `prefix.@Nullable`).
            // Parse the complete annotation (including any arguments) from the tokenizer.
            val annotationStart = startingToken.substring(atIndex)
            val annotation = getAnnotationSource(annotationStart)
            buildString {
                append(startingToken, 0, atIndex)
                append(annotation)
            }
        } else {
            // No annotation is present; advance the tokenizer and return the token directly.
            tokenizer.requireToken()
            startingToken
        }
    }

    /**
     * If the [startingToken] is the beginning of an annotation, returns the annotation parsed from
     * the [tokenizer]. Returns null otherwise.
     *
     * When the method returns, the [tokenizer] will point to the token after the annotation.
     */
    private fun getAnnotationSource(startingToken: String): String? {
        var token = startingToken
        if (token.startsWith('@')) {
            return buildString {
                append('@')

                // Restore annotations that were shortened on export
                val annotationClassName = unshortenAnnotation(token.substring(1))
                append(annotationClassName)

                token = tokenizer.requireToken()
                if (token == "(") {
                    // Annotation arguments; potentially nested
                    append(tokenizer.scanBalancedTokens("(", ")"))

                    // Move the tokenizer so that when the method returns it points to the token
                    // after the end of the annotation.
                    tokenizer.requireToken()
                }
            }
        } else {
            return null
        }
    }

    /**
     * Collects all the sequential annotations from the [tokenizer] beginning with
     * [Tokenizer.current], returning them as a (possibly empty) list.
     *
     * When the method returns, the [tokenizer] will point to the token after the annotation list.
     */
    private fun getAnnotations() = buildList {
        while (true) {
            val annotationSource = getAnnotationSource(tokenizer.current) ?: break

            // Parse the annotation from the source. If it was not `null`
            valueParser.parseAnnotationItem(annotationSource, unshorten = false)?.let {
                annotationItem ->
                add(annotationItem)
            }
        }
    }

    /**
     * Create [ParameterItem]s for the [containingCallable] from the [parameters] using the
     * [typeItemFactory] to create types.
     *
     * This is called from within the constructor of the [containingCallable] so must only access
     * its `name` and its reference. In particularly it must not access its
     * [CallableItem.parameters] property as this is called during its initialization.
     */
    private fun createParameterItems(
        containingCallable: CallableItem,
        parameters: List<ParameterInfo>,
        typeItemFactory: TextTypeItemFactory
    ): List<ParameterItem> {
        val methodFingerprint = MethodFingerprint(containingCallable.name(), parameters.size)
        return parameters.map { it.create(containingCallable, typeItemFactory, methodFingerprint) }
    }

    /** Parse a constructor member of [containingClass]. */
    private fun parseConstructor(
        containingClass: SkeletonClassItem,
        classTypeItemFactory: TextTypeItemFactory,
    ) {
        tokenizer.requireToken()
        val method: ConstructorItem

        val (modifiers, targetLanguages) = parseModifiersAndTargetLanguages()

        // Get a TypeParameterList and accompanying TypeItemFactory
        val (typeParameterList, typeItemFactory) = parseTypeParameterList(classTypeItemFactory)
        var token = tokenizer.current

        tokenizer.assertIdent(token)
        // For nested classes, strip outer classes from name
        val name: String = token.extractSimpleName()
        val parameters = parseParameterList()
        token = tokenizer.requireToken()
        var throwsList = emptyList<ExceptionTypeItem>()
        if ("throws" == token) {
            throwsList = parseThrows(typeItemFactory)
            token = tokenizer.current
        }
        if (";" != token) {
            throw ApiParseException("expected ; found $token", tokenizer)
        }

        method =
            itemFactory.createConstructorItem(
                fileLocation = tokenizer.fileLocation(),
                modifiers = modifiers,
                documentationFactory = ItemDocumentation.NONE_FACTORY,
                name = name,
                containingClass = containingClass,
                typeParameterList = typeParameterList,
                returnType = containingClass.type(),
                parameterItemsFactory = { methodItem ->
                    createParameterItems(methodItem, parameters, typeItemFactory)
                },
                throwsTypes = throwsList,
                // Signature files do not track implicit constructors, all constructors are treated
                // the same as whether it was created by the compiler or in the source has no effect
                // on the API surface.
                implicitConstructor = false,
                targetLanguages = targetLanguages,
            )
        method.markSelectedApiVariant()

        if (appending) {
            // If there is already a constructor with the same signature from a previous file,
            // replaces the old version with this one, otherwise just adds the constructor.
            containingClass.replaceOrAddConstructor(method)
        } else {
            // Just add the constructor to the class.
            containingClass.addConstructor(method)
        }
    }

    /** Parse a method member of [containingClass]. */
    private fun parseMethod(
        containingClass: SkeletonClassItem,
        classTypeItemFactory: TextTypeItemFactory,
    ) {
        tokenizer.requireToken()
        val method: MethodItem

        val (modifiers, targetLanguages) = parseModifiersAndTargetLanguages()

        // Get a TypeParameterList and accompanying TypeParameterScope
        val (typeParameterList, typeItemFactory) = parseTypeParameterList(classTypeItemFactory)
        var token = tokenizer.current
        tokenizer.assertIdent(token)

        val returnTypeString: String
        val parameters: List<ParameterInfo>
        val name: String
        if (kotlinNameTypeOrder) {
            // Kotlin style: parse the name, the parameter list, then the return type.
            name = token
            parameters = parseParameterList()
            token = tokenizer.requireToken()
            if (token != ":") {
                throw ApiParseException(
                    "Expecting \":\" after parameter list, found $token.",
                    tokenizer
                )
            }
            token = tokenizer.requireToken()
            tokenizer.assertIdent(token)
            returnTypeString = scanForTypeString()
            token = tokenizer.current
        } else {
            // Java style: parse the return type, the name, and then the parameter list.
            returnTypeString = scanForTypeString()
            token = tokenizer.current
            tokenizer.assertIdent(token)
            name = token
            parameters = parseParameterList()
            token = tokenizer.requireToken()
        }

        val returnType =
            typeItemFactory.getMethodReturnType(
                returnTypeString,
                modifiers.annotations(),
                MethodFingerprint(name, parameters.size),
                containingClass.isAnnotationType()
            )
        synchronizeNullability(returnType, modifiers)

        if (containingClass.isInterface() && !modifiers.isDefault() && !modifiers.isStatic()) {
            modifiers.setAbstract(true)
        }

        var throwsList = emptyList<ExceptionTypeItem>()
        var defaultAnnotationMethodValue: String? = null

        when (token) {
            "throws" -> {
                throwsList = parseThrows(typeItemFactory)
                token = tokenizer.current
            }
            "default" -> {
                defaultAnnotationMethodValue = parseDefault()
                token = tokenizer.current
            }
        }
        if (";" != token) {
            throw ApiParseException("expected ; found $token", tokenizer)
        }

        val defaultValueProvider =
            defaultAnnotationMethodValue?.let { valueString ->
                valueParser.providerFor(returnType, valueString, ValueUseSite.ANNOTATION)
            }

        method =
            itemFactory.createMethodItem(
                fileLocation = tokenizer.fileLocation(),
                modifiers = modifiers,
                documentationFactory = ItemDocumentation.NONE_FACTORY,
                name = name,
                containingClass = containingClass,
                typeParameterList = typeParameterList,
                returnType = returnType,
                parameterItemsFactory = { containingCallable ->
                    createParameterItems(containingCallable, parameters, typeItemFactory)
                },
                throwsTypes = throwsList,
                defaultValueProvider = defaultValueProvider,
                targetLanguages = targetLanguages,
                isExtensionMethod = false, // no way to tell if this is an extension method
            )

        // Ignore enum synthetic methods. They are no longer included in signature files as they add
        // no information. However, they did use to be included and so this filters them out to
        // ensure that the resulting Codebase is consistent with the original source Codebase.
        if (method.isEnumSyntheticMethod()) return

        method.markSelectedApiVariant()

        if (appending) {
            // If the method already exists in the class item because it was defined in a previous
            // signature file then replace it with this one, otherwise just add this method.
            containingClass.replaceOrAddMethod(method)
        } else {
            // Just add the method to the class.
            containingClass.addMethod(method)
        }
    }

    /** Parse a field member of [containingClass]. */
    private fun parseField(
        containingClass: SkeletonClassItem,
        classTypeItemFactory: TextTypeItemFactory,
    ) =
        parseFieldOrEnumConstant(
            containingClass,
            classTypeItemFactory,
            isEnumConstant = false,
        )

    /** Parse an enum constant member of [containingClass]. */
    private fun parseEnumConstant(
        containingClass: SkeletonClassItem,
        classTypeItemFactory: TextTypeItemFactory,
    ) =
        parseFieldOrEnumConstant(
            containingClass,
            classTypeItemFactory,
            isEnumConstant = true,
        )

    /** Parse a field or enum constant of [containingClass]. */
    private fun parseFieldOrEnumConstant(
        containingClass: SkeletonClassItem,
        classTypeItemFactory: TextTypeItemFactory,
        isEnumConstant: Boolean,
    ) {
        tokenizer.requireToken()
        val (modifiers, targetLanguages) = parseModifiersAndTargetLanguages()
        var token = tokenizer.current
        tokenizer.assertIdent(token)

        val typeString: String
        val name: String
        if (kotlinNameTypeOrder) {
            // Kotlin style: parse the name, then the type.
            name = parseNameWithColon(token)
            token = tokenizer.requireToken()
            tokenizer.assertIdent(token)
            typeString = scanForTypeString()
            token = tokenizer.current
        } else {
            // Java style: parse the name, then the type.
            typeString = scanForTypeString()
            token = tokenizer.current
            tokenizer.assertIdent(token)
            name = token
            token = tokenizer.requireToken()
        }

        // Get the optional value.
        val valueString =
            if ("=" == token) {
                token = tokenizer.requireToken(purpose = TokenPurpose.VALUE)
                token.also { token = tokenizer.requireToken() }
            } else null

        // Parse the type string and then synchronize the field's nullability with the type.
        val type =
            classTypeItemFactory.getFieldType(
                underlyingType = typeString,
                isEnumConstant = isEnumConstant,
                isFinal = modifiers.isFinal(),
                isInitialValueNonNull = { valueString != null && valueString != "null" },
                itemAnnotations = modifiers.annotations(),
            )
        synchronizeNullability(type, modifiers)

        // In signature files fields have to be static and final in order for them to have a
        // constant value in addition to a value.
        val constantValueProvider =
            if (valueString != null) {
                if (modifiers.isStatic() && modifiers.isFinal())
                    valueParser.providerFor(type, valueString, ValueUseSite.FIELD)
                else {
                    // Report that the value is being ignored.
                    reportIssue(
                        Issues.SIGNATURE_FILE_ERROR,
                        "Field $name in $containingClass has a value of `$valueString` but is not `static` and `final`; ignoring value"
                    )
                    null
                }
            } else null

        if (";" != token) {
            throw ApiParseException("expected ; found $token", tokenizer)
        }
        val field =
            itemFactory.createFieldItem(
                fileLocation = tokenizer.fileLocation(),
                modifiers = modifiers,
                documentationFactory = ItemDocumentation.NONE_FACTORY,
                name = name,
                containingClass = containingClass,
                type = type,
                isEnumConstant = isEnumConstant,
                constantValueProvider = constantValueProvider,
                targetLanguages = targetLanguages,
            )
        field.markSelectedApiVariant()
        if (appending) {
            // If the field already exists in the class item because it was defined in a previous
            // signature file then replace it with this one, otherwise just add this field.
            containingClass.replaceOrAddField(field)
        } else {
            // Just add the field to the class.
            containingClass.addField(field)
        }
    }

    /**
     * Parses and creates an optional target language set and modifiers (see [parseModifiers]).
     *
     * When the method returns, the current token of [tokenizer] will be the first token after the
     * modifiers.
     */
    private fun parseModifiersAndTargetLanguages(): Pair<MutableModifierList, Set<TargetLanguage>> {
        val token = tokenizer.current
        // Check if there's a token describing the target languages of the item. If there is, get
        // the next token, if not, use the set of all languages.
        val targetLanguages =
            TargetLanguageSet.signatureFileRepresentationToTargetLanguageSet[token]?.also {
                tokenizer.requireToken()
            } ?: defaultTargetLanguageSet

        val modifiers = parseModifiers()
        return modifiers to targetLanguages
    }

    /**
     * Parses and creates modifiers, including annotations and keyword modifiers.
     *
     * If there is no visibility modifier, [VisibilityLevel.PACKAGE_PRIVATE] is used.
     *
     * The method starts processing using [Tokenizer.current] from [tokenizer]. When the method
     * returns, the current token of [tokenizer] will be the first token after the modifiers.
     */
    private fun parseModifiers(): MutableModifierList {
        val modifiers = parseModifierAnnotations(VisibilityLevel.PACKAGE_PRIVATE)
        parseKeywordModifiers(modifiers)
        return modifiers
    }

    /**
     * Updates the [modifiers] to reflect all modifier keywords parsed from [tokenizer].
     *
     * The method starts processing from the current token of [tokenizer]. When the method returns,
     * the current token of [tokenizer] will be the first token after the modifiers.
     */
    private fun parseKeywordModifiers(modifiers: MutableModifierList) {
        var token = tokenizer.current
        while (true) {
            when (token) {
                "public" -> {
                    modifiers.setVisibilityLevel(VisibilityLevel.PUBLIC)
                }
                "protected" -> {
                    modifiers.setVisibilityLevel(VisibilityLevel.PROTECTED)
                }
                "private" -> {
                    modifiers.setVisibilityLevel(VisibilityLevel.PRIVATE)
                }
                "internal" -> {
                    modifiers.setVisibilityLevel(VisibilityLevel.INTERNAL)
                }
                "static" -> {
                    modifiers.setStatic(true)
                }
                "final" -> {
                    modifiers.setFinal(true)
                }
                "deprecated" -> {
                    modifiers.setDeprecated(true)
                }
                "abstract" -> {
                    modifiers.setAbstract(true)
                }
                "transient" -> {
                    modifiers.setTransient(true)
                }
                "volatile" -> {
                    modifiers.setVolatile(true)
                }
                "sealed" -> {
                    modifiers.setSealed(true)
                    // When reading in a sealed class, for backwards compatibility we want
                    // to label it as non-exhaustive (for more details on what this means,
                    // see b/447143803) in case the signature file doesn't have one of
                    // "exhaustive" or "nonexhaustive" after the "sealed" modifier. This
                    // allows compatibility checks to not raise unnecessary errors for
                    // sealed classes without an exhaustivity modifier. If the class is indeed
                    // labeled with an exhaustivity modifier in the signature file, the class's
                    // exhaustivity will be adjusted accordingly in the following match
                    // statements.
                    modifiers.setExhaustive(false)
                }
                "non-sealed" -> {
                    modifiers.setNonSealed(true)
                }
                "exhaustive" -> {
                    modifiers.setExhaustive(true)
                }
                "non-exhaustive",
                "nonexhaustive" -> {
                    modifiers.setExhaustive(false)
                }
                "default" -> {
                    modifiers.setDefault(true)
                }
                "synchronized" -> {
                    modifiers.setSynchronized(true)
                }
                "native" -> {
                    modifiers.setNative(true)
                }
                "strictfp" -> {
                    modifiers.setStrictFp(true)
                }
                "infix" -> {
                    modifiers.setInfix(true)
                }
                "operator" -> {
                    modifiers.setOperator(true)
                }
                "inline" -> {
                    modifiers.setInline(true)
                }
                "value" -> {
                    modifiers.setValue(true)
                }
                "suspend" -> {
                    modifiers.setSuspend(true)
                }
                "vararg" -> {
                    modifiers.setVarArg(true)
                }
                "fun" -> {
                    modifiers.setFunctional(true)
                }
                "data" -> {
                    modifiers.setData(true)
                }
                else -> break
            }

            token = tokenizer.requireToken()
        }
    }

    /**
     * Parses and creates modifiers, including annotations but not keyword modifiers.
     *
     * The method starts processing using [Tokenizer.current] from [tokenizer]. When the method
     * returns, the current token of [tokenizer] will be the first token after the modifiers.
     */
    private fun parseModifierAnnotations(
        visibilityLevel: VisibilityLevel,
    ): MutableModifierList {
        val annotations = getAnnotations()
        val modifiers = createMutableModifiers(visibilityLevel, annotations)
        // @Deprecated is also treated as a "modifier"
        if (annotations.any { it.qualifiedName == JAVA_LANG_DEPRECATED }) {
            modifiers.setDeprecated(true)
        }
        return modifiers
    }

    private fun parseProperty(
        containingClass: SkeletonClassItem,
        classTypeItemFactory: TextTypeItemFactory,
    ) {
        tokenizer.requireToken()
        val modifiers = parseModifiers()

        // Get a TypeParameterList and accompanying TypeParameterScope
        val (typeParameterList, typeItemFactory) = parseTypeParameterList(classTypeItemFactory)

        val typeString: String
        val receiverNamePair: Pair<TypeItem?, String>
        if (kotlinNameTypeOrder) {
            // Kotlin style: parse the name, then the type.
            receiverNamePair = parsePropertyReceiverAndName(typeItemFactory)
            typeString = scanForTypeString()
        } else {
            // Java style: parse the type, then the name.
            typeString = scanForTypeString()
            receiverNamePair = parsePropertyReceiverAndName(typeItemFactory)
        }
        val type = typeItemFactory.getGeneralType(typeString)
        synchronizeNullability(type, modifiers)

        var token = tokenizer.current
        val contextParameters =
            if (token == "(") {
                val params =
                    parseParameterList(
                        // The current token is already the "("
                        startWithCurrentToken = true,
                        useUnderscoreAsDefaultName = true,
                    )
                // `parseParameterList` ends with the tokenizer on the closing ")", skip to the next
                // token to continue parsing
                token = tokenizer.requireToken()
                params
            } else {
                emptyList()
            }

        if (";" != token) {
            throw ApiParseException("expected ; found $token", tokenizer)
        }
        val property =
            itemFactory.createPropertyItem(
                fileLocation = tokenizer.fileLocation(),
                modifiers = modifiers,
                name = receiverNamePair.second,
                containingClass = containingClass,
                type = type,
                receiver = receiverNamePair.first,
                typeParameterList = typeParameterList,
                // There isn't any information about whether a setter exists or its visibility if it
                // does in API files currently.
                setterVisibility = null,
                contextParameterFactory = { propertyItem ->
                    contextParameters.map { it.create(propertyItem, typeItemFactory) }
                },
            )
        property.markSelectedApiVariant()

        if (appending) {
            // If there is already a property with the same signature from a previous file, replaces
            // the old version with this one, otherwise just adds the property.
            containingClass.replaceOrAddProperty(property)
        } else {
            // Just add the property to the class.
            containingClass.addProperty(property)
        }
    }

    /**
     * Starting from the current token of [tokenizer], parses the optional receiver type and then
     * the name of a property.
     *
     * After the method returns, the caller should continue processing at the new current token of
     * [tokenizer], which will be the token after
     */
    private fun parsePropertyReceiverAndName(
        typeItemFactory: TextTypeItemFactory
    ): Pair<TypeItem?, String> {
        // If there's no receiver, scanning for the type string should just return the name.
        // If there is a receiver, because of how the tokens are broken up, it should return
        // "receiver.name", which can then be split on the last "." to the receiver and name.
        val receiverAndName = scanForTypeString()
        val namePossiblyWithColon: String
        val receiverTypeString: String?
        if (receiverAndName.contains(".")) {
            namePossiblyWithColon = receiverAndName.substringAfterLast(".")
            receiverTypeString = receiverAndName.substringBeforeLast(".")
        } else {
            namePossiblyWithColon = receiverAndName
            receiverTypeString = null
        }

        val name =
            if (kotlinNameTypeOrder) {
                parseNameWithColon(namePossiblyWithColon)
            } else {
                tokenizer.assertIdent(namePossiblyWithColon)
                namePossiblyWithColon
            }
        val receiverType = receiverTypeString?.let { typeItemFactory.getGeneralType(it) }

        return receiverType to name
    }

    /** Parse [token] which is expected to be of the format `#<record-component-index>`. */
    private fun parseRecordComponentIndex(token: String): Int? {
        if (!token.startsWith('#')) return null
        val index =
            try {
                token.substring(1).toInt()
            } catch (_: NumberFormatException) {
                return null
            }

        if (index < 0) return null

        return index
    }

    /**
     * Parse record components, returning them as a list of [TextRecordComponent].
     *
     * Starts with [Tokenizer.current]. On return [Tokenizer.current] points to the next token after
     * the record component.
     */
    private fun parseRecordComponents() = buildList {
        var token = tokenizer.current
        while (true) {
            if (token != "record_component") break

            val textRecordComponent = parseRecordComponent()
            add(textRecordComponent)
            token = tokenizer.requireToken()
        }
    }

    /** Encapsulates information about a record component extracted from the signature file. */
    private data class TextRecordComponent(
        val location: FileLocation,
        val modifiers: MutableModifierList,
        val name: String,
        val typeString: String,
        val recordComponentIndex: Int,
    )

    private fun TextRecordComponent.createRecordComponent(
        classItem: ClassItem,
        typeItemFactory: TextTypeItemFactory,
    ) =
        itemFactory.createRecordComponentItem(
            fileLocation = location,
            modifiers = modifiers,
            name = name,
            containingClass = classItem,
            type = typeItemFactory.getGeneralType(typeString),
            recordComponentIndex = recordComponentIndex,
        )

    /** Parse a record component class member into a [TextRecordComponent]. */
    private fun parseRecordComponent(): TextRecordComponent {
        val location = tokenizer.fileLocation()

        // Parse a record component index.
        var token = tokenizer.requireToken()
        val recordComponentIndex =
            parseRecordComponentIndex(token)
                ?: throw ApiParseException(
                    "Expected record component index #<index> but found '$token'",
                    tokenizer
                )

        // Parse the modifiers, which will really just be annotations. Record components are always
        // public.
        tokenizer.requireToken()
        val modifiers = parseModifierAnnotations(VisibilityLevel.PUBLIC)

        // Parse the component name.
        token = tokenizer.current
        val name = parseNameWithColon(token)

        // Parse the type.
        tokenizer.requireToken()
        val typeString = scanForTypeString()

        // Make sure that the whole record component was parsed.
        token = tokenizer.current
        if (";" != token) {
            throw ApiParseException("expected ; found $token", tokenizer)
        }

        return TextRecordComponent(
            location,
            modifiers,
            name,
            typeString,
            recordComponentIndex,
        )
    }

    /**
     * Parses a type parameter list enclosed in "<>", if one exists.
     *
     * Starts processing from the current token of [tokenizer]. If that token is not "<", returns an
     * empty type parameter list.
     *
     * After the method returns, the caller should continue processing at the new current token of
     * [tokenizer], which will be the token after the type parameter list, if it exists, or the same
     * as the original current token, if there was no type parameter list.
     */
    private fun parseTypeParameterList(
        enclosingTypeItemFactory: TextTypeItemFactory,
    ): TypeParameterListAndFactory<TextTypeItemFactory> {
        val token: String = tokenizer.current
        // No type parameters to parse. The current token is unchanged
        if ("<" != token) {
            return TypeParameterListAndFactory(TypeParameterList.NONE, enclosingTypeItemFactory)
        }

        val typeParameterListString = tokenizer.scanBalancedTokens("<", ">")
        // Set the tokenizer to the next token, so that the caller should continue processing at
        // tokenizer.current (in alignment with the no type parameter case).
        tokenizer.requireToken()
        return if (typeParameterListString.isEmpty()) {
            TypeParameterListAndFactory(TypeParameterList.NONE, enclosingTypeItemFactory)
        } else {
            // Use the file location as a part of the description of the scope as at this point
            // there is no other information available.
            val scopeDescription = "${tokenizer.fileLocation()}"
            createTypeParameterList(
                enclosingTypeItemFactory,
                scopeDescription,
                typeParameterListString
            )
        }
    }

    /**
     * Creates a [TypeParameterList] and accompanying [TypeParameterScope].
     *
     * The [typeParameterListString] should be the string representation of a list of type
     * parameters, like "<A>" or "<A, B extends java.lang.String, C>".
     *
     * @return a [Pair] of [TypeParameterList] and [TextTypeItemFactory] that contains those type
     *   parameters.
     */
    private fun createTypeParameterList(
        enclosingTypeItemFactory: TextTypeItemFactory,
        scopeDescription: String,
        typeParameterListString: String
    ): TypeParameterListAndFactory<TextTypeItemFactory> {
        // Split the type parameter list string into a list of strings, one for each type
        // parameter.
        val typeParameterStrings = typeParser.typeParameterStrings(typeParameterListString)

        // Create the List<TypeParameterItem> and the corresponding TypeItemFactory that can be
        // used to resolve TypeParameterItems from the list. This performs the construction in two
        // stages to handle cycles between the parameters.
        return enclosingTypeItemFactory.createTypeParameterItemsAndFactory(
            scopeDescription,
            typeParameterStrings,
            // Create a `TextTypeParameterItem` from the type parameter string.
            { createTypeParameterItem(it) },
            // Create, set and return the [BoundsTypeItem] list.
            { typeItemFactory, typeParameterString ->
                val boundsStringList = extractTypeParameterBoundsStringList(typeParameterString)
                if (boundsStringList.isEmpty()) {
                    WellKnownTypes.defaultTypeParameterBounds(forKotlin = false)
                } else {
                    boundsStringList.map { typeItemFactory.getBoundsType(it) }
                }
            },
        )
    }

    /**
     * Create a partially initialized [SkeletonTypeParameterItem].
     *
     * This extracts the [TypeParameterItem.isReified] and [TypeParameterItem.name] from the
     * [typeParameterString] and creates a [SkeletonTypeParameterItem] with those properties
     * initialized but the [SkeletonTypeParameterItem.bounds] is not.
     */
    private fun createTypeParameterItem(typeParameterString: String): SkeletonTypeParameterItem {
        val length = typeParameterString.length
        var nameEnd = length

        val isReified = typeParameterString.startsWith("reified ")
        val nameStart =
            if (isReified) {
                8 // "reified ".length
            } else {
                0
            }

        for (i in nameStart until length) {
            val c = typeParameterString[i]
            if (!Character.isJavaIdentifierPart(c)) {
                nameEnd = i
                break
            }
        }
        val name = typeParameterString.substring(nameStart, nameEnd)

        // TODO: Type use annotations support will need to handle annotations on the parameter.
        val modifiers = createImmutableModifiers(VisibilityLevel.PUBLIC)

        return itemFactory.createTypeParameterItem(
            modifiers = modifiers,
            name = name,
            isReified = isReified,
        )
    }

    /**
     * Parses a list of parameters.
     *
     * If [startWithCurrentToken] is true, before calling [tokenizer] should point to the opening
     * `(` of the parameter list. If [startWithCurrentToken] is false, [tokenizer] should point to
     * the token *before* the opening `(` (and the method will start by calling
     * [Tokenizer.requireToken]).
     *
     * If [useUnderscoreAsDefaultName] is true, parameters without a public name will have "_" as
     * their name. If it is false, they will have "arg<index>" as their name.
     *
     * When the method returns, [tokenizer] will point to the closing `)` of the parameter list.
     */
    private fun parseParameterList(
        startWithCurrentToken: Boolean = false,
        useUnderscoreAsDefaultName: Boolean = false,
    ): List<ParameterInfo> {
        val parameters = mutableListOf<ParameterInfo>()
        var token: String =
            if (startWithCurrentToken) {
                tokenizer.current
            } else {
                tokenizer.requireToken()
            }
        if ("(" != token) {
            throw ApiParseException("expected (, was $token", tokenizer)
        }
        token = tokenizer.requireToken()
        var index = 0
        while (true) {
            if (")" == token) {
                // All parameters are parsed, return them.
                return parameters
            }

            // Each item can be:
            //   optional-"optional" annotations optional-modifiers
            //   type-with-use-annotations-and-generics optional-name

            // Used to represent the presence of a default value, instead of showing the entire
            // default value
            val hasOptionalKeyword = token == "optional"
            if (hasOptionalKeyword) {
                tokenizer.requireToken()
            }

            // The kind of the parameter might be specified.
            val optionalKind =
                when (token) {
                    "context" -> ParameterKind.CONTEXT
                    "receiver" -> ParameterKind.RECEIVER
                    else -> null
                }
            if (optionalKind != null) {
                tokenizer.requireToken()
            }

            val modifiers = parseModifiers()
            token = tokenizer.current

            val typeString: String
            val publicName: String?
            if (kotlinNameTypeOrder) {
                // Kotlin style: parse the name (only considered a public name if it is not `_`,
                // which is used as a placeholder for params without public names), then the type.
                val nameOrPlaceholder = parseNameWithColon(token)
                publicName =
                    if (nameOrPlaceholder == "_") {
                        null
                    } else {
                        nameOrPlaceholder
                    }
                tokenizer.requireToken()
                // Token should now represent the type
                typeString = scanForTypeString()
                token = tokenizer.current
            } else {
                // Java style: parse the type, then the public name if it has one.
                typeString = scanForTypeString()
                token = tokenizer.current
                if (Tokenizer.isIdent(token)) {
                    publicName = token
                    token = tokenizer.requireToken()
                } else {
                    publicName = null
                }
            }

            when (token) {
                "," -> {
                    token = tokenizer.requireToken()
                }
                ")" -> {
                    // closing parenthesis
                }
                else -> {
                    throw ApiParseException("expected , or ), found $token", tokenizer)
                }
            }

            val name = publicName ?: (if (useUnderscoreAsDefaultName) "_" else "arg${index + 1}")
            parameters.add(
                ParameterInfo(
                    name,
                    publicName,
                    // The optional keyword indicates whether a parameter has a default value
                    hasDefaultValue = hasOptionalKeyword,
                    typeString,
                    modifiers,
                    tokenizer.fileLocation(),
                    index,
                    optionalKind,
                )
            )
            index++
        }
    }

    /**
     * Container for parsed information on a parameter. This is an intermediate step before a
     * [ParameterItem] is created, which is needed because
     * [TextTypeItemFactory.getMethodParameterType] requires a [MethodFingerprint] with the total
     * number of method parameters.
     */
    private inner class ParameterInfo(
        val name: String,
        val publicName: String?,
        val hasDefaultValue: Boolean,
        val typeString: String,
        val modifiers: MutableModifierList,
        val location: FileLocation,
        val index: Int,
        val optionalKind: ParameterKind?,
    ) {
        /**
         * Turn this [ParameterInfo] into a [ParameterItem] of the [containingCallable] by parsing
         * the [typeString].
         */
        fun create(
            containingCallable: CallableItem,
            typeItemFactory: TextTypeItemFactory,
            methodFingerprint: MethodFingerprint
        ): ParameterItem {
            val type =
                typeItemFactory.getMethodParameterType(
                    typeString,
                    modifiers.annotations(),
                    methodFingerprint,
                    index,
                    modifiers.isVarArg()
                )
            synchronizeNullability(type, modifiers)

            // The last parameter of a suspend function is the continuation parameter. If there was
            // a parameter kind listed in the file, use that, otherwise this is a value parameter.
            val kind =
                if (
                    containingCallable.modifiers.isSuspend() &&
                        index == methodFingerprint.parameterCount - 1
                ) {
                    ParameterKind.CONTINUATION
                } else {
                    optionalKind ?: ParameterKind.VALUE
                }

            val parameter =
                itemFactory.createParameterItem(
                    fileLocation = location,
                    modifiers = modifiers,
                    name = name,
                    publicName = publicName,
                    containingItem = containingCallable,
                    parameterIndex = index,
                    type = type,
                    hasDefaultValue = hasDefaultValue,
                    kind = kind,
                )

            return parameter
        }

        /**
         * Turn this [ParameterInfo] into a context [ParameterItem] of the [containingProperty] by
         * parsing the [typeString].
         */
        fun create(
            containingProperty: PropertyItem,
            typeItemFactory: TextTypeItemFactory
        ): ParameterItem {
            val type = typeItemFactory.getGeneralType(typeString)
            synchronizeNullability(type, modifiers)
            return itemFactory.createParameterItem(
                fileLocation = location,
                modifiers = modifiers,
                name = name,
                publicName = publicName,
                containingItem = containingProperty,
                parameterIndex = index,
                type = type,
                hasDefaultValue = hasDefaultValue,
                kind = ParameterKind.CONTEXT, // All ParameterItems for a property are context
            )
        }
    }

    private fun parseDefault(): String {
        return buildString {
            while (true) {
                val token = tokenizer.requireToken()
                if (";" == token) {
                    break
                } else {
                    append(token)
                }
            }
        }
    }

    private fun parseThrows(
        typeItemFactory: TextTypeItemFactory,
    ): List<ExceptionTypeItem> {
        var token = tokenizer.requireToken()
        val throwsList = buildList {
            var comma = true
            while (true) {
                when (token) {
                    ";" -> {
                        break
                    }
                    "," -> {
                        if (comma) {
                            throw ApiParseException("Expected exception, got ','", tokenizer)
                        }
                        comma = true
                    }
                    else -> {
                        if (!comma) {
                            throw ApiParseException("Expected ',' or ';' got $token", tokenizer)
                        }
                        comma = false
                        val exceptionType = typeItemFactory.getExceptionType(token)
                        add(exceptionType)
                    }
                }
                token = tokenizer.requireToken()
            }
        }

        return throwsList
    }

    /**
     * Scans the token stream from [tokenizer] for a type string, starting with [Tokenizer.current]
     * and ensuring that the full type string is gathered, even when there are type-use annotations.
     *
     * After this method is called, `tokenizer.current` will point to the token after the type.
     *
     * Note: this **should not** be used when the token after the type could contain annotations,
     * such as when multiple types appear as consecutive tokens. (This happens in the `implements`
     * list of a class definition, e.g. `class Foo implements test.pkg.Bar test.pkg.@A Baz`.)
     *
     * To handle arrays with type-use annotations, this looks forward at the next token and includes
     * it if it contains an annotation. This is necessary to handle type strings like "Foo @A []".
     *
     * @return the complete type string.
     */
    private fun scanForTypeString(): String {
        val prev = getAnnotationCompleteToken()
        var prevIsIncomplete = isIncompleteTypeToken(prev)
        var token = tokenizer.current
        var tokenIsIncomplete = isIncompleteTypeToken(token)

        // If neither the initial token nor the next token has annotations that break up the type,
        // the initial token is the entire type string (the common case, avoiding StringBuilder).
        if (!prevIsIncomplete && !tokenIsIncomplete) {
            return prev
        }

        return buildString {
            append(prev)

            // Look both at the last used token and the next one:
            // 1. If the last token has annotations, the type string was broken up by annotations
            //    and the next token is also part of the type.
            // 2. If the next token has annotations, this is an array type like "Foo @A []",
            //    so the next token is part of the type.
            while (prevIsIncomplete || tokenIsIncomplete) {
                token = getAnnotationCompleteToken()
                append(' ').append(token)

                // The token just consumed becomes `prev`. Its incompleteness was already evaluated
                // as `tokenIsIncomplete`, so transfer that status without scanning again.
                prevIsIncomplete = tokenIsIncomplete

                // Look ahead at the next token and evaluate only this new token.
                token = tokenizer.current
                tokenIsIncomplete = isIncompleteTypeToken(token)
            }
        }
    }

    /**
     * Synchronize nullability annotations on the API item and [TypeNullability].
     *
     * If the type string uses a Kotlin nullability suffix, this adds an annotation representing
     * that nullability to [modifiers].
     *
     * @param typeItem the type of the API item.
     * @param modifiers the API item's modifiers.
     */
    private fun synchronizeNullability(typeItem: TypeItem, modifiers: MutableModifierList) {
        if (kotlinStyleNulls) {
            // Add an annotation to the context item for the type's nullability if applicable.
            val annotationClassNameToAdd =
                // Treat varargs as non-null for consistency with the psi model.
                if (typeItem is ArrayTypeItem && typeItem.isVarargs) {
                    ANDROIDX_NONNULL
                } else {
                    val nullability = typeItem.modifiers.nullability
                    if (typeItem !is PrimitiveTypeItem && nullability == TypeNullability.NONNULL) {
                        ANDROIDX_NONNULL
                    } else if (nullability == TypeNullability.NULLABLE) {
                        ANDROIDX_NULLABLE
                    } else {
                        // No annotation to add, return.
                        return
                    }
                }
            val annotation =
                AnnotationItem.createMarkerAnnotation(codebase, annotationClassNameToAdd)
            modifiers.addAnnotation(annotation)
        }
    }

    /**
     * Determines whether the [type] is an incomplete type string broken up by annotations. This is
     * the case when there's an annotation that isn't contained within a parameter list (because
     * [Tokenizer.requireToken] handles not breaking in the middle of a parameter list).
     *
     * @param type the type token to check.
     * @return true if the token is an incomplete type string broken up by annotations.
     */
    private fun isIncompleteTypeToken(type: String): Boolean {
        // If there is no '@' at all, the token cannot have type annotations.
        val firstAnnotationIndex = type.indexOf('@')
        if (firstAnnotationIndex == -1) return false

        // If there are no type parameters ('<') or the first annotation appears before '<',
        // then the annotation is outside the parameter list and breaks up the type string.
        val paramStartIndex = type.indexOf('<')
        if (paramStartIndex == -1 || firstAnnotationIndex < paramStartIndex) return true

        // Otherwise, the first annotation is inside '<...>'. Check whether any annotation
        // appears after the parameter list (e.g. `List<String> @Nullable []`).
        val lastAnnotationIndex = type.lastIndexOf('@')
        val paramEndIndex = type.lastIndexOf('>')
        return paramEndIndex == -1 || paramEndIndex < lastAnnotationIndex
    }

    /**
     * For Kotlin-style name/type ordering in signature files, the name is generally followed by a
     * colon (besides methods, where the colon comes after the parameter list). This method takes
     * the name [token] and removes the trailing colon, throwing an [ApiParseException] if one isn't
     * present.
     */
    private fun parseNameWithColon(token: String): String {
        if (!token.endsWith(':')) {
            throw ApiParseException("Expecting name ending with \":\" but found $token.", tokenizer)
        }
        return token.removeSuffix(":")
    }

    private fun qualifiedName(pkg: String, className: String): String {
        return "$pkg.$className"
    }
}
