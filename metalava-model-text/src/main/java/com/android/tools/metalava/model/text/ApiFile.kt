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
import com.android.tools.metalava.model.parser.LineMap
import com.android.tools.metalava.model.parser.SharedTokenType
import com.android.tools.metalava.model.parser.Token
import com.android.tools.metalava.model.parser.TokenStream
import com.android.tools.metalava.model.parser.TokenType
import com.android.tools.metalava.model.text.CustomizableProperty.Companion.KOTLIN_NAME_TYPE_ORDER
import com.android.tools.metalava.model.text.CustomizableProperty.Companion.KOTLIN_STYLE_NULLS
import com.android.tools.metalava.model.text.parser.SignatureFileLexer
import com.android.tools.metalava.model.text.parser.SignatureTokenType
import com.android.tools.metalava.model.type.MethodFingerprint
import com.android.tools.metalava.model.type.TypeItemParser
import com.android.tools.metalava.model.type.TypeItemParserErrorReporter
import com.android.tools.metalava.model.type.TypeParameterListAndFactory
import com.android.tools.metalava.model.type.TypeString
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
    private val allowClassModifierChanges: Boolean,
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
     * The [SingleSignatureFileParser] for the current file being parsed.
     *
     * Set by [parseMultipleFiles].
     */
    private lateinit var currentParser: SingleSignatureFileParser

    /** Report recoverable errors encountered while parsing types. */
    private val typeItemParserErrorReporter =
        object : TypeItemParserErrorReporter {
            override fun report(issue: Issues.Issue, message: String, charOffset: Int) {
                reportIssue(issue, message, charOffset)
            }
        }

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
            /** Provides the caller with access to the [TextTypeParser.Stats]. */
            apiStatsConsumer: (TextTypeParser.Stats) -> Unit = {},
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
            val codebase = parser.parseMultipleFiles(signatureFiles, apiStatsConsumer)

            // Update implicit permit types in any sealed class that does not have one provided.
            SealedClassImplicitPermitTypesUpdater.updateImplicitPermitTypes(codebase)

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
            val codebase =
                parser.parseMultipleFiles(listOfNotNull(baseSignatureFile) + sourceSetSignatureFile)
            return name to codebase
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
     * Retrieves the location of the error at [charOffset] from [currentParser].
     *
     * Note: Non-recoverable issues result in an exception being thrown.
     */
    private fun reportIssue(issue: Issues.Issue, message: String, charOffset: Int) {
        val location = currentParser.fileLocation(charOffset)
        codebase.reporter.report(issue, null, message, location)
    }

    /**
     * Parses all the [signatureFiles], treating the first file as the base API and all other files
     * as extensions.
     */
    private fun parseMultipleFiles(
        signatureFiles: List<SignatureFile>,
        apiStatsConsumer: (TextTypeParser.Stats) -> Unit = {},
    ): Codebase {
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

            val context =
                parserContext
                    ?: createParserContext(kotlinStyleNullsForThisFile).also { parserContext = it }

            val parser =
                SingleSignatureFileParser(
                    context = context,
                    path = path,
                    apiText = apiText,
                    appending = appending,
                    kotlinStyleNulls = kotlinStyleNullsForThisFile,
                    kotlinNameTypeOrder = format[KOTLIN_NAME_TYPE_ORDER],
                    apiVariant = apiVariant,
                )

            // Set the current parser to provide location information about the current file.
            currentParser = parser

            parser.parse()

            appending = true
        }

        // Known to be non-null as `signatureFiles` is never empty and the first file is never
        // skipped.
        parserContext!!

        parserContext.classMerger.performAnyDeferredMerges()

        apiStatsConsumer(parserContext.typeParser.stats())

        return codebase
    }

    /**
     * Creates the [ParserContext] shared across all signature files parsed by [parseMultipleFiles].
     *
     * Called once the header of the first signature file has been parsed so that the
     * [TextTypeParser] is configured with the [kotlinStyleNulls] setting from the first file.
     *
     * @param kotlinStyleNulls whether types should be interpreted to be in Kotlin format (e.g. `?`
     *   suffix means nullable, `!` suffix means unknown, and absence of a suffix means not
     *   nullable).
     */
    private fun createParserContext(kotlinStyleNulls: Boolean): ParserContext {
        val typeParser = TextTypeParser(codebase, kotlinStyleNulls, typeItemParserErrorReporter)
        val globalTypeItemFactory = TextTypeItemFactory(assembler, typeParser)
        val valueParser =
            ValueParser(
                codebase,
                TypeItemParser.forValueParser(codebase, typeItemParserErrorReporter),
            )
        return ParserContext(
            assembler = assembler,
            typeParser = typeParser,
            globalTypeItemFactory = globalTypeItemFactory,
            valueParser = valueParser,
            defaultTargetLanguageSet = defaultTargetLanguageSet,
            classMerger = ClassMerger(allowClassModifierChanges),
        )
    }
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

    /** The [Path] to the signature file being parsed. */
    private val path: Path,

    /** The contents of the signature file being parsed. */
    private val apiText: String,

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

    /** Maps character offsets in [apiText] to line numbers. */
    private val lineMap: LineMap = LineMap.create(apiText)

    /** The [TokenStream] of tokens from [apiText]. */
    private val tokenStream: TokenStream = SignatureFileLexer(apiText).tokenize()

    /** Get the [FileLocation] at the 0-based [charOffset]. */
    fun fileLocation(charOffset: Int): FileLocation = lineMap.fileLocation(path, charOffset)

    /** Get the [FileLocation] of the start of [token]. */
    private fun fileLocation(token: Token): FileLocation = fileLocation(token.startOffset)

    /** Get the contents of the file being parsed from [start] to [end]. */
    private fun fileSubstring(start: Int, end: Int): String = apiText.substring(start, end)

    /** Extract the text of [token] from [apiText]. */
    private fun text(token: Token): String = token.text(apiText)

    /** Returns the next [Token] in [tokenStream] without consuming it. */
    private fun peek(): Token = tokenStream.peek()

    /** Returns the [TokenType] of the next [Token] in [tokenStream] without consuming it. */
    private fun peekType(): TokenType = tokenStream.peekType()

    /** Consumes and returns the next [Token] from [tokenStream]. */
    private fun consume(): Token = tokenStream.consume()

    /** Consumes the next [Token] in [tokenStream] if its type equals [type], returning `true`. */
    private fun match(type: TokenType): Boolean = tokenStream.match(type)

    /**
     * Consumes and returns the next [Token] from [tokenStream], throwing an [ApiParseException] if
     * the end of the file has been reached.
     */
    private fun requireNonEofToken(): Token {
        val token = peek()
        if (token.type == SharedTokenType.EOF) {
            throw parseException("Unexpected end of file", token)
        }
        return consume()
    }

    /**
     * Checks that [token] can be used as an identifier, throwing an [ApiParseException] at the
     * location of [token] if it cannot.
     */
    private fun assertIdent(token: Token) {
        if (!token.type.canBeIdentifier) {
            throw parseException("Expected identifier: ${text(token)}", token)
        }
    }

    /** Creates an [ApiParseException] with [message] at the location of [token]. */
    private fun parseException(message: String, token: Token): ApiParseException =
        ApiParseException(
            message,
            fileLocation(token),
        )

    /**
     * Parses a dot-separated identifier (such as a package, class, or constructor name) from
     * [tokenStream] and returns the complete qualified name.
     */
    private fun parseQualifiedName(): String {
        // Consume the first identifier segment.
        val firstToken = requireNonEofToken()
        assertIdent(firstToken)
        var endOffset = firstToken.endOffset

        // Consume any subsequent '.<identifier>' segments.
        while (peekType() == SharedTokenType.DOT) {
            consume()
            val nextToken = requireNonEofToken()
            assertIdent(nextToken)
            endOffset = nextToken.endOffset
        }

        return fileSubstring(firstToken.startOffset, endOffset)
    }

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
     * Retrieves the location of the error for [token] from [fileLocation].
     *
     * Note: Non-recoverable issues result in an exception being thrown.
     */
    private fun reportIssue(issue: Issues.Issue, message: String, token: Token) {
        val location = fileLocation(token)
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

    /** Parse the signature file from [tokenStream], populating [codebase]. */
    fun parse() {
        // Consume each top-level `package` block until the end of the file is reached.
        while (peekType() != SharedTokenType.EOF) {
            val token = consume()
            // TODO: Accept annotations on packages.
            if (token.type == SignatureTokenType.PACKAGE) {
                parsePackage(token)
            } else {
                throw parseException("expected package got ${text(token)}", token)
            }
        }
    }

    /**
     * Find an existing package called [name] or create a new one.
     *
     * If an existing package exists then this makes sure that its annotations match [annotations].
     */
    private fun findOrCreatePackage(
        packageToken: Token,
        name: String,
        annotations: List<AnnotationItem>,
    ): PackageItem {
        // Check to see if the package already exists, if it does then return it.
        codebase.findPackage(name)?.let { existing ->
            // If the same package showed up multiple times, make sure they have the same modifiers.
            // (Packages can't have public/private/etc., but they can have annotations, which are
            // part of ModifierList.)
            val existingAnnotations = existing.modifiers.annotations()
            if (annotations != existingAnnotations) {
                throw parseException(
                    String.format(
                        "Contradicting declaration of package %s." +
                            " Previously seen with annotations \"%s\", but now with \"%s\"",
                        name,
                        existingAnnotations,
                        annotations
                    ),
                    packageToken,
                )
            }

            return existing
        }

        // Wrap the file location and annotations in a PackageInfo.
        val packageInfo =
            PackageInfo(
                fileLocation = fileLocation(packageToken),
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

    private fun parsePackage(packageToken: Token) {
        // Metalava: including annotations in file now
        val annotations = getAnnotations()
        val name: String = parseQualifiedName()

        val pkg = findOrCreatePackage(packageToken, name, annotations)

        // Note: pkg.markSelectedApiVariant() is not called here because packages do not belong to
        // an API surface in their own right; their API variants are populated via propagation from
        // their contained classes and members.

        // Consume the `{` opening the package body, then parse each class/typealias until `}`.
        val openBrace = requireNonEofToken()
        if (openBrace.type != SharedTokenType.BRACE_OPEN) {
            throw parseException("expected '{' got ${text(openBrace)}", openBrace)
        }
        while (!match(SharedTokenType.BRACE_CLOSE)) {
            parseClass(pkg)
        }
    }

    /**
     * Creates a type alias in the [pkg] with the [modifiers].
     *
     * Before calling, the `typealias` keyword should have been consumed from [tokenStream], and the
     * next token will be the name and optional type parameter list.
     *
     * When the method returns, [tokenStream] will have consumed the `;` at the end of the typealias
     * line.
     */
    private fun parseTypeAlias(
        pkg: PackageItem,
        modifiers: MutableModifierList,
        location: FileLocation
    ) {
        // Parse the typealias name and optional `<...>` type parameter list.
        val name = parseQualifiedName()
        val typeParameterListString = scanTypeParameterListString()

        val (typeParameterList, typeItemFactory) =
            if (typeParameterListString == null) {
                TypeParameterListAndFactory(TypeParameterList.NONE, globalTypeItemFactory)
            } else {
                createTypeParameterList(
                    globalTypeItemFactory,
                    "typealias $name",
                    typeParameterListString
                )
            }
        val qualifiedClassName = pkg.qualifiedName() + "." + name

        // Consume `=`, scan the aliased type, and consume the terminating `;`.
        val equalsToken = requireNonEofToken()
        if (equalsToken.type != SharedTokenType.EQUALS) {
            throw parseException("expected = found ${text(equalsToken)}", equalsToken)
        }

        val typeString = scanForTypeString()
        val semicolon = requireNonEofToken()
        if (semicolon.type != SignatureTokenType.SEMICOLON) {
            throw parseException("expected ; found ${text(semicolon)}", semicolon)
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

    /** Parse a class in [pkg]. */
    private fun parseClass(pkg: PackageItem) {
        val (modifiers, targetLanguages) = parseModifiersAndTargetLanguages()
        val kindToken = requireNonEofToken()
        // Remember this position as this seems like a good place to use to report issues with the
        // class item.
        val classPosition = fileLocation(kindToken)

        val classKind =
            when (kindToken.type) {
                SharedTokenType.CLASS -> ClassKind.CLASS
                SignatureTokenType.INTERFACE -> ClassKind.INTERFACE
                SignatureTokenType.ENUM -> ClassKind.ENUM
                SignatureTokenType.ANNOTATION_INTERFACE -> ClassKind.ANNOTATION_TYPE
                SignatureTokenType.RECORD -> ClassKind.RECORD
                SignatureTokenType.TYPEALIAS -> ClassKind.TYPEALIAS
                else ->
                    throw parseException(
                        "expected one of ${ClassKind.entries.joinToString { it.signatureKeyword }}; found: ${text(kindToken)}",
                        kindToken,
                    )
            }

        if (classKind == ClassKind.TYPEALIAS) {
            // Type aliases aren't classes, but they are defined at the same level as classes
            parseTypeAlias(pkg, modifiers, classPosition)
            // Don't continue creating a class item
            return
        }

        classKind.setImplicitModifiers(modifiers)

        // Extract lots of information from the declared class type.
        val (
            fullName,
            qualifiedClassName,
            outerClass,
            typeParameterList,
            typeItemFactory,
        ) = parseDeclaredClassType(pkg, classPosition)

        var superClassType = parseSuperClassType(classKind, typeItemFactory)
        val interfaceTypes = parseInterfaceTypes(classKind, typeItemFactory)
        val permitTypes = parsePermitTypes(typeItemFactory)

        val openBrace = requireNonEofToken()
        if (openBrace.type != SharedTokenType.BRACE_OPEN) {
            throw parseException("expected {, was ${text(openBrace)}", openBrace)
        }

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

    /**
     * Parse the class body, adding members to [containingClass].
     *
     * When the method returns, [tokenStream] will have consumed the closing `}` of the class body.
     */
    private fun parseClassBody(
        containingClass: SkeletonClassItem,
        classTypeItemFactory: TextTypeItemFactory,
    ) {
        // Dispatch each member declaration by its leading keyword token type until the closing `}`
        // is consumed.
        while (!match(SharedTokenType.BRACE_CLOSE)) {
            val memberToken = requireNonEofToken()
            when (memberToken.type) {
                SignatureTokenType.CTOR -> parseConstructor(containingClass, classTypeItemFactory)
                SignatureTokenType.ENUM_CONSTANT ->
                    parseEnumConstant(containingClass, classTypeItemFactory)
                SignatureTokenType.FIELD -> parseField(containingClass, classTypeItemFactory)
                SignatureTokenType.METHOD -> parseMethod(containingClass, classTypeItemFactory)
                SignatureTokenType.PROPERTY -> parseProperty(containingClass, classTypeItemFactory)
                else ->
                    throw parseException(
                        "expected one of ctor, enum_constant, field, method, property",
                        memberToken,
                    )
            }
        }
    }

    /**
     * Parses the optional `extends <superclass>` clause for a non-interface class, falling back to
     * [ClassKind.implicitSuperClassType] if none is present.
     */
    private fun parseSuperClassType(
        classKind: ClassKind,
        typeItemFactory: TextTypeItemFactory,
    ): ClassTypeItem? {
        // Interfaces use `extends` for super-interfaces rather than a superclass.
        if (peekType() == SharedTokenType.EXTENDS && classKind != ClassKind.INTERFACE) {
            consume()
            val superClassTypeString = parseSuperTypeString()
            return typeItemFactory.getSuperClassType(superClassTypeString)
        }
        return classKind.implicitSuperClassType
    }

    /**
     * Parses the optional `implements` (or `extends` for interfaces) clause and combines it with
     * any [ClassKind.implicitInterfaceType].
     */
    private fun parseInterfaceTypes(
        classKind: ClassKind,
        typeItemFactory: TextTypeItemFactory,
    ): Set<ClassTypeItem> {
        val interfaceTypes = mutableSetOf<ClassTypeItem>()

        // Add any ClassKind specific implicit interface types.
        classKind.implicitInterfaceType?.let { interfaceType -> interfaceTypes.add(interfaceType) }

        if (peekType() == SignatureTokenType.IMPLEMENTS || peekType() == SharedTokenType.EXTENDS) {
            consume()
            // Consume super-interface types separated by optional commas until the class body `{`
            // or a `permits` clause is reached.
            while (true) {
                val nextType = peekType()
                if (
                    nextType == SharedTokenType.BRACE_OPEN || nextType == SignatureTokenType.PERMITS
                ) {
                    break
                } else if (nextType != SharedTokenType.COMMA) {
                    val interfaceTypeString = parseSuperTypeString()
                    val interfaceType = typeItemFactory.getInterfaceType(interfaceTypeString)
                    interfaceTypes.add(interfaceType)
                } else {
                    consume()
                }
            }
        }
        return interfaceTypes
    }

    /**
     * Parses the optional `permits` clause for a sealed class or interface, returning the permitted
     * subclass types sorted by qualified name.
     */
    private fun parsePermitTypes(
        typeItemFactory: TextTypeItemFactory,
    ): List<ClassTypeItem> {
        val permitTypes = mutableListOf<ClassTypeItem>()

        if (match(SignatureTokenType.PERMITS)) {
            // Consume permitted subclass types up to the opening `{` of the class body.
            while (peekType() != SharedTokenType.BRACE_OPEN) {
                val typeString = parseSuperTypeString()
                val permitsType = typeItemFactory.getHierarchicalClassType(typeString)
                permitTypes.add(permitsType)
            }
            permitTypes.sortWith(TypeItem.qualifiedComparator)
        }
        return permitTypes
    }

    /**
     * Skips any leading `@...` type-use annotations and consumes the following identifier token,
     * returning the `endOffset` of that identifier token.
     */
    private fun skipAnnotatedIdentifier(): Int {
        while (peekType() == SharedTokenType.AT) {
            skipAnnotation()
        }
        val identToken = requireNonEofToken()
        assertIdent(identToken)
        return identToken.endOffset
    }

    /**
     * Skips a balanced `<...>` type argument list starting at the next `<` token in [tokenStream],
     * along with any immediately adjacent identifier token following `>`, returning the `endOffset`
     * of the last consumed token.
     */
    private fun skipTypeArgumentList(): Int {
        var endOffset = skipAngleBracketList()
        // Include any identifier token immediately adjacent to the closing `>` (without intervening
        // whitespace).
        if (peek().startOffset == endOffset && peekType().canBeIdentifier) {
            endOffset = consume().endOffset
        }
        return endOffset
    }

    /**
     * Parse a super type string, i.e. a string representing a super class type, super interface
     * type, or permitted subclass type.
     */
    private fun parseSuperTypeString(): TypeString {
        val firstToken = peek()
        if (firstToken.type == SharedTokenType.EOF) {
            throw parseException("Unexpected end of file", firstToken)
        }
        val startOffset = firstToken.startOffset

        // Consume the initial (possibly annotated) identifier segment of the type.
        var endOffset = skipAnnotatedIdentifier()

        // Continue consuming qualified segments (`.Foo`), type argument lists (`<...>`), and
        // Kotlin nullability suffixes (`?` or `!`).
        while (true) {
            when (peekType()) {
                SharedTokenType.DOT -> {
                    consume()
                    endOffset = skipAnnotatedIdentifier()
                }
                SharedTokenType.ANGLE_OPEN -> {
                    endOffset = skipTypeArgumentList()
                }
                SharedTokenType.QUESTION,
                SharedTokenType.EXCLAMATION -> {
                    endOffset = consume().endOffset
                }
                else -> break
            }
        }

        return TypeString(fileSubstring(startOffset, endOffset), startOffset)
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
     * Parses the declared class type from [tokenStream] into [DeclaredClassTypeComponents].
     *
     * For example "Foo" would split into full name "Foo" and an empty type parameter list, while
     * `"Foo.Bar<A, B extends java.lang.String, C>"` would split into full name `"Foo.Bar"` and type
     * parameter list with `"A"`,`"B extends java.lang.String"`, and `"C"` as type parameters.
     *
     * If the qualified name matches an existing class then return its information.
     */
    private fun parseDeclaredClassType(
        pkg: PackageItem,
        classFileLocation: FileLocation,
    ): DeclaredClassTypeComponents {
        val fullName = parseQualifiedName()
        val typeParameterListString = scanTypeParameterListString()
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
            if (typeParameterListString == null)
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
     * Skips a `@QualifiedName(...)` annotation starting at the current `@` token in [tokenStream],
     * returning the `endOffset` of the last token of the annotation.
     */
    private fun skipAnnotation(): Int {
        // Consume the leading `@` token.
        val atToken = requireNonEofToken()
        var endOffset = atToken.endOffset

        // Consume the dot-separated qualified name of the annotation class.
        val firstIdent = requireNonEofToken()
        assertIdent(firstIdent)
        endOffset = firstIdent.endOffset
        while (peekType() == SharedTokenType.DOT) {
            consume()
            val nextIdent = requireNonEofToken()
            assertIdent(nextIdent)
            endOffset = nextIdent.endOffset
        }

        // If the annotation has an argument list, consume balanced `(...)` tokens.
        if (peekType() == SharedTokenType.PAREN_OPEN) {
            consume()
            var balance = 1
            while (balance > 0) {
                val tok = requireNonEofToken()
                endOffset = tok.endOffset
                if (tok.type == SharedTokenType.PAREN_OPEN) {
                    balance++
                } else if (tok.type == SharedTokenType.PAREN_CLOSE) {
                    balance--
                }
            }
        }
        return endOffset
    }

    /**
     * Collects all the sequential annotations from [tokenStream], returning them as a (possibly
     * empty) list.
     */
    private fun getAnnotations(): List<AnnotationItem> = buildList {
        while (peekType() == SharedTokenType.AT) {
            // Record the start of the annotation, skip its tokens, and extract the raw source span.
            val startOffset = peek().startOffset
            val endOffset = skipAnnotation()
            val annotationSource = fileSubstring(startOffset, endOffset)

            // Parse the annotation from the source, unshortening the class name if necessary, and
            // add it to the list if it is recognized.
            valueParser.parseAnnotationItem(annotationSource, unshorten = true)?.let {
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
        val method: ConstructorItem

        val (modifiers, targetLanguages) = parseModifiersAndTargetLanguages()

        // Get a TypeParameterList and accompanying TypeItemFactory
        val (typeParameterList, typeItemFactory) = parseTypeParameterList(classTypeItemFactory)

        // For nested classes, strip outer classes from name
        val name: String = parseQualifiedName().extractSimpleName()
        val parameters = parseParameterList()
        // Parse the optional `throws` clause and terminating `;`.
        var throwsList = emptyList<ExceptionTypeItem>()
        if (match(SignatureTokenType.THROWS)) {
            throwsList = parseThrows(typeItemFactory)
        }
        val semicolon = requireNonEofToken()
        if (semicolon.type != SignatureTokenType.SEMICOLON) {
            throw parseException("expected ; found ${text(semicolon)}", semicolon)
        }

        method =
            itemFactory.createConstructorItem(
                fileLocation = fileLocation(semicolon),
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
        val method: MethodItem

        val (modifiers, targetLanguages) = parseModifiersAndTargetLanguages()

        // Get a TypeParameterList and accompanying TypeParameterScope
        val (typeParameterList, typeItemFactory) = parseTypeParameterList(classTypeItemFactory)

        val returnTypeString: TypeString
        val parameters: List<ParameterInfo>
        val name: String
        if (kotlinNameTypeOrder) {
            // Kotlin style: parse the name, the parameter list, the `:` separator, then the return
            // type.
            val nameToken = requireNonEofToken()
            assertIdent(nameToken)
            name = text(nameToken)
            parameters = parseParameterList()
            val colonToken = requireNonEofToken()
            if (colonToken.type != SharedTokenType.COLON) {
                throw parseException(
                    "Expecting \":\" after parameter list, found ${text(colonToken)}.",
                    colonToken,
                )
            }
            returnTypeString = scanForTypeString()
        } else {
            // Java style: parse the return type, the name, and then the parameter list.
            returnTypeString = scanForTypeString()
            val nameToken = requireNonEofToken()
            assertIdent(nameToken)
            name = text(nameToken)
            parameters = parseParameterList()
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

        // Parse an optional `throws` clause or annotation method `default` value before the
        // terminating `;`.
        when {
            match(SignatureTokenType.THROWS) -> {
                throwsList = parseThrows(typeItemFactory)
            }
            match(SignatureTokenType.DEFAULT) -> {
                defaultAnnotationMethodValue = scanValueUntilSemicolon()
            }
        }
        val semicolon = requireNonEofToken()
        if (semicolon.type != SignatureTokenType.SEMICOLON) {
            throw parseException("expected ; found ${text(semicolon)}", semicolon)
        }

        val defaultValueProvider =
            defaultAnnotationMethodValue?.let { valueString ->
                valueParser.providerFor(returnType, valueString, ValueUseSite.ANNOTATION)
            }

        method =
            itemFactory.createMethodItem(
                fileLocation = fileLocation(semicolon),
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
        val (modifiers, targetLanguages) = parseModifiersAndTargetLanguages()

        val typeString: TypeString
        val name: String
        if (kotlinNameTypeOrder) {
            // Kotlin style: parse the name, then the type.
            name = parseNameWithColon()
            typeString = scanForTypeString()
        } else {
            // Java style: parse the type, then the name.
            typeString = scanForTypeString()
            val nameToken = requireNonEofToken()
            assertIdent(nameToken)
            name = text(nameToken)
        }
        var token = requireNonEofToken()

        // If an `=` follows, scan the field's initial value up to the terminating `;` and then
        // consume that `;`.
        val valueString =
            if (token.type == SharedTokenType.EQUALS) {
                scanValueUntilSemicolon().also { token = requireNonEofToken() }
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
                        "Field $name in $containingClass has a value of `$valueString` but is not `static` and `final`; ignoring value",
                        token,
                    )
                    null
                }
            } else null

        if (token.type != SignatureTokenType.SEMICOLON) {
            throw parseException("expected ; found ${text(token)}", token)
        }
        val field =
            itemFactory.createFieldItem(
                fileLocation = fileLocation(token),
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
     * When the method returns, the next token in [tokenStream] will be the first token after the
     * modifiers.
     */
    private fun parseModifiersAndTargetLanguages(): Pair<MutableModifierList, Set<TargetLanguage>> {
        // Check if there's a token describing the target languages of the item. If there is,
        // consume it, if not, use the set of all languages.
        val targetLanguages =
            if (peekType() == SignatureTokenType.TARGET_LANGUAGE) {
                val token = consume()
                TargetLanguageSet.signatureFileRepresentationToTargetLanguageSet[text(token)]
                    ?: defaultTargetLanguageSet
            } else {
                defaultTargetLanguageSet
            }

        val modifiers = parseModifiers()
        return modifiers to targetLanguages
    }

    /**
     * Parses and creates modifiers, including annotations and keyword modifiers.
     *
     * If there is no visibility modifier, [VisibilityLevel.PACKAGE_PRIVATE] is used.
     *
     * When the method returns, the next token in [tokenStream] will be the first token after the
     * modifiers.
     */
    private fun parseModifiers(): MutableModifierList {
        val modifiers = parseModifierAnnotations(VisibilityLevel.PACKAGE_PRIVATE)
        parseKeywordModifiers(modifiers)
        return modifiers
    }

    /**
     * Updates the [modifiers] to reflect all modifier keywords parsed from [tokenStream].
     *
     * When the method returns, the next token in [tokenStream] will be the first token after the
     * modifiers.
     */
    private fun parseKeywordModifiers(modifiers: MutableModifierList) {
        // Peek at each subsequent token and update `modifiers` while modifier keywords are seen.
        while (true) {
            when (peekType()) {
                SignatureTokenType.PUBLIC -> {
                    modifiers.setVisibilityLevel(VisibilityLevel.PUBLIC)
                }
                SignatureTokenType.PROTECTED -> {
                    modifiers.setVisibilityLevel(VisibilityLevel.PROTECTED)
                }
                SignatureTokenType.PRIVATE -> {
                    modifiers.setVisibilityLevel(VisibilityLevel.PRIVATE)
                }
                SignatureTokenType.INTERNAL -> {
                    modifiers.setVisibilityLevel(VisibilityLevel.INTERNAL)
                }
                SignatureTokenType.STATIC -> {
                    modifiers.setStatic(true)
                }
                SignatureTokenType.FINAL -> {
                    modifiers.setFinal(true)
                }
                SignatureTokenType.DEPRECATED -> {
                    modifiers.setDeprecated(true)
                }
                SignatureTokenType.ABSTRACT -> {
                    modifiers.setAbstract(true)
                }
                SignatureTokenType.TRANSIENT -> {
                    modifiers.setTransient(true)
                }
                SignatureTokenType.VOLATILE -> {
                    modifiers.setVolatile(true)
                }
                SignatureTokenType.SEALED -> {
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
                SignatureTokenType.NON_SEALED -> {
                    modifiers.setNonSealed(true)
                }
                SignatureTokenType.EXHAUSTIVE -> {
                    modifiers.setExhaustive(true)
                }
                SignatureTokenType.NON_EXHAUSTIVE -> {
                    modifiers.setExhaustive(false)
                }
                SignatureTokenType.DEFAULT -> {
                    modifiers.setDefault(true)
                }
                SignatureTokenType.SYNCHRONIZED -> {
                    modifiers.setSynchronized(true)
                }
                SignatureTokenType.NATIVE -> {
                    modifiers.setNative(true)
                }
                SignatureTokenType.STRICTFP -> {
                    modifiers.setStrictFp(true)
                }
                SignatureTokenType.INFIX -> {
                    modifiers.setInfix(true)
                }
                SignatureTokenType.OPERATOR -> {
                    modifiers.setOperator(true)
                }
                SignatureTokenType.INLINE -> {
                    modifiers.setInline(true)
                }
                SignatureTokenType.VALUE -> {
                    modifiers.setValue(true)
                }
                SignatureTokenType.SUSPEND -> {
                    modifiers.setSuspend(true)
                }
                SignatureTokenType.VARARG -> {
                    modifiers.setVarArg(true)
                }
                SignatureTokenType.FUN -> {
                    modifiers.setFunctional(true)
                }
                SignatureTokenType.DATA -> {
                    modifiers.setData(true)
                }
                // Stop without consuming once a non-modifier token is reached.
                else -> break
            }

            // Consume the matched modifier keyword token and continue to the next token.
            consume()
        }
    }

    /**
     * Parses and creates modifiers, including annotations but not keyword modifiers.
     *
     * When the method returns, the next token in [tokenStream] will be the first token after the
     * modifiers.
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
        val modifiers = parseModifiers()

        // Get a TypeParameterList and accompanying TypeParameterScope
        val (typeParameterList, typeItemFactory) = parseTypeParameterList(classTypeItemFactory)

        val typeString: TypeString
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

        // If an opening `(` follows the property declaration, parse its context parameter list.
        val contextParameters =
            if (peekType() == SharedTokenType.PAREN_OPEN) {
                parseParameterList(
                    useUnderscoreAsDefaultName = true,
                )
            } else {
                emptyList()
            }

        // Consume the terminating `;` of the property declaration.
        val token = requireNonEofToken()
        if (token.type != SignatureTokenType.SEMICOLON) {
            throw parseException("expected ; found ${text(token)}", token)
        }
        val property =
            itemFactory.createPropertyItem(
                fileLocation = fileLocation(token),
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
     * Parses the optional receiver type and then the name of a property from [tokenStream].
     *
     * After the method returns, the caller should continue processing at the next token in
     * [tokenStream].
     */
    private fun parsePropertyReceiverAndName(
        typeItemFactory: TextTypeItemFactory
    ): Pair<TypeItem?, String> {
        // If there's no receiver, scanning for the type string should just return the name.
        // If there is a receiver, it will return "receiver.name", which can then be split on the
        // last "." to the receiver and name.
        val receiverAndName = scanForTypeString()
        val namePossiblyWithColon: String
        val receiverTypeString: TypeString?
        if (receiverAndName.type.contains(".")) {
            namePossiblyWithColon = receiverAndName.type.substringAfterLast(".")
            receiverTypeString =
                TypeString(
                    receiverAndName.type.substringBeforeLast("."),
                    receiverAndName.offset,
                )
        } else {
            namePossiblyWithColon = receiverAndName.type
            receiverTypeString = null
        }

        val name =
            if (kotlinNameTypeOrder) {
                parseNameWithColon(namePossiblyWithColon)
            } else {
                namePossiblyWithColon
            }
        val receiverType = receiverTypeString?.let { typeItemFactory.getGeneralType(it) }

        return receiverType to name
    }

    /** Parse `#<record-component-index>` from [tokenStream]. */
    private fun parseRecordComponentIndex(): Int {
        // `#<index>` is tokenized as a `#` token immediately adjacent to a non-negative integer
        // literal token.
        val firstToken = requireNonEofToken()
        if (
            firstToken.type == SignatureTokenType.HASH &&
                peekType() == SharedTokenType.NUMBER_LITERAL &&
                peek().startOffset == firstToken.endOffset
        ) {
            val numberToken = requireNonEofToken()
            val index = text(numberToken).toIntOrNull()
            if (index != null && index >= 0) {
                return index
            }
        }
        throw parseException(
            "Expected record component index #<index> but found '${text(firstToken)}'",
            firstToken,
        )
    }

    /**
     * Parse record components, returning them as a list of [TextRecordComponent].
     *
     * Consumes all consecutive `record_component` declarations from [tokenStream].
     */
    private fun parseRecordComponents() = buildList {
        while (peekType() == SignatureTokenType.RECORD_COMPONENT) {
            val textRecordComponent = parseRecordComponent()
            add(textRecordComponent)
        }
    }

    /** Encapsulates information about a record component extracted from the signature file. */
    private data class TextRecordComponent(
        val location: FileLocation,
        val modifiers: MutableModifierList,
        val name: String,
        val typeString: TypeString,
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
        // Consume the `record_component` keyword and record its location.
        val recordComponentToken = requireNonEofToken()
        val location = fileLocation(recordComponentToken)

        // Parse a record component index.
        val recordComponentIndex = parseRecordComponentIndex()

        // Parse the modifiers, which will really just be annotations. Record components are always
        // public.
        val modifiers = parseModifierAnnotations(VisibilityLevel.PUBLIC)

        // Parse the component name.
        val name = parseNameWithColon()

        // Parse the type.
        val typeString = scanForTypeString()

        // Make sure that the whole record component was parsed.
        val semicolon = requireNonEofToken()
        if (semicolon.type != SignatureTokenType.SEMICOLON) {
            throw parseException("expected ; found ${text(semicolon)}", semicolon)
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
     * Skips a balanced `<...>` list starting at the next `<` token in [tokenStream], returning the
     * `endOffset` of the closing `>` token.
     */
    private fun skipAngleBracketList(): Int {
        // Consume the opening `<` token and track nesting depth until the matching `>` is consumed.
        val startToken = requireNonEofToken()
        var endOffset = startToken.endOffset
        var balance = 1
        while (balance > 0) {
            val token = requireNonEofToken()
            endOffset = token.endOffset
            if (token.type == SharedTokenType.ANGLE_OPEN) {
                balance++
            } else if (token.type == SharedTokenType.ANGLE_CLOSE) {
                balance--
            }
        }
        return endOffset
    }

    /**
     * Scans a balanced `<...>` type parameter list from [tokenStream] if the next token is `<`,
     * returning the [TypeString] or `null` if not present.
     */
    private fun scanTypeParameterListString(): TypeString? {
        if (peekType() != SharedTokenType.ANGLE_OPEN) {
            return null
        }
        // Record the start of `<`, skip the balanced `<...>` list, and slice the substring.
        val startOffset = peek().startOffset
        val endOffset = skipAngleBracketList()
        return TypeString(fileSubstring(startOffset, endOffset), startOffset)
    }

    /**
     * Parses a type parameter list enclosed in "<>", if one exists.
     *
     * If the next token in [tokenStream] is not `<`, returns an empty type parameter list without
     * consuming any tokens. Otherwise, consumes the balanced `<...>` tokens and returns the parsed
     * [TypeParameterListAndFactory].
     */
    private fun parseTypeParameterList(
        enclosingTypeItemFactory: TextTypeItemFactory,
    ): TypeParameterListAndFactory<TextTypeItemFactory> {
        val firstToken = peek()
        val typeParameterListString = scanTypeParameterListString()
        return if (typeParameterListString == null) {
            TypeParameterListAndFactory(TypeParameterList.NONE, enclosingTypeItemFactory)
        } else {
            // Use the file location as a part of the description of the scope as at this point
            // there is no other information available.
            val scopeDescription = "${fileLocation(firstToken)}"
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
        typeParameterListString: TypeString
    ): TypeParameterListAndFactory<TextTypeItemFactory> {
        // Split the type parameter list string into a list of strings, one for each type
        // parameter.
        val typeParameterStrings = typeParser.typeParameterStrings(typeParameterListString.type)

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
                    boundsStringList.map {
                        typeItemFactory.getBoundsType(
                            TypeString(it, typeParameterListString.offset)
                        )
                    }
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
     * Before calling, [tokenStream] should point to the opening `(` of the parameter list.
     *
     * If [useUnderscoreAsDefaultName] is true, parameters without a public name will have "_" as
     * their name. If it is false, they will have "arg<index>" as their name.
     *
     * When the method returns, [tokenStream] will have consumed the closing `)` of the parameter
     * list.
     */
    private fun parseParameterList(
        useUnderscoreAsDefaultName: Boolean = false,
    ): List<ParameterInfo> {
        val parameters = mutableListOf<ParameterInfo>()
        // Consume the opening `(` of the parameter list.
        val openParen = requireNonEofToken()
        if (openParen.type != SharedTokenType.PAREN_OPEN) {
            throw parseException("expected (, was ${text(openParen)}", openParen)
        }
        var index = 0
        while (true) {
            if (match(SharedTokenType.PAREN_CLOSE)) {
                // All parameters are parsed, return them.
                return parameters
            }

            // Each item can be:
            //   optional-"optional" annotations optional-modifiers
            //   type-with-use-annotations-and-generics optional-name

            // Used to represent the presence of a default value, instead of showing the entire
            // default value
            val hasOptionalKeyword = match(SignatureTokenType.OPTIONAL)

            // The kind of the parameter might be specified.
            val optionalKind =
                when (peekType()) {
                    SignatureTokenType.CONTEXT -> {
                        consume()
                        ParameterKind.CONTEXT
                    }
                    SignatureTokenType.RECEIVER -> {
                        consume()
                        ParameterKind.RECEIVER
                    }
                    else -> null
                }

            val modifiers = parseModifiers()

            val typeString: TypeString
            val publicName: String?
            if (kotlinNameTypeOrder) {
                // Kotlin style: parse the name (only considered a public name if it is not `_`,
                // which is used as a placeholder for params without public names), then the type.
                val nameOrPlaceholder = parseNameWithColon()
                publicName =
                    if (nameOrPlaceholder == "_") {
                        null
                    } else {
                        nameOrPlaceholder
                    }
                // Token should now represent the type
                typeString = scanForTypeString()
            } else {
                // Java style: parse the type, then the public name if an identifier follows before
                // the `,` or `)` delimiter.
                typeString = scanForTypeString()
                if (peekType().canBeIdentifier) {
                    publicName = text(consume())
                } else {
                    publicName = null
                }
            }

            // Consume the parameter delimiter (`,` if more parameters follow, or `)` at the end of
            // the parameter list).
            val delimiter = requireNonEofToken()
            val isDone =
                when (delimiter.type) {
                    SharedTokenType.COMMA -> false
                    SharedTokenType.PAREN_CLOSE -> true
                    else -> {
                        throw parseException(
                            "expected , or ), found ${text(delimiter)}",
                            delimiter,
                        )
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
                    fileLocation(delimiter),
                    index,
                    optionalKind,
                )
            )
            index++
            if (isDone) {
                return parameters
            }
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
        val typeString: TypeString,
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

    /**
     * Scans a field or annotation method default value expression from [tokenStream] up to (but not
     * consuming) the terminating `;`.
     */
    private fun scanValueUntilSemicolon(): String {
        // Consume the first token of the value expression.
        val firstToken = requireNonEofToken()
        val startOffset = firstToken.startOffset
        var endOffset = firstToken.endOffset

        // Track nesting depth of parentheses, braces, and angle brackets so that tokens inside
        // nested expressions (such as array initializers or annotation arguments) are included.
        var parenDepth = 0
        var braceDepth = 0
        var angleDepth = 0
        var current = firstToken
        while (true) {
            when (current.type) {
                SharedTokenType.PAREN_OPEN -> parenDepth++
                SharedTokenType.PAREN_CLOSE -> if (parenDepth > 0) parenDepth--
                SharedTokenType.BRACE_OPEN -> braceDepth++
                SharedTokenType.BRACE_CLOSE -> if (braceDepth > 0) braceDepth--
                SharedTokenType.ANGLE_OPEN -> angleDepth++
                SharedTokenType.ANGLE_CLOSE -> if (angleDepth > 0) angleDepth--
                else -> {}
            }
            val nextType = peekType()
            // Stop before consuming ';' when all delimiters are balanced (or at EOF).
            if (
                nextType == SharedTokenType.EOF ||
                    (nextType == SignatureTokenType.SEMICOLON &&
                        parenDepth == 0 &&
                        braceDepth == 0 &&
                        angleDepth == 0)
            ) {
                break
            }
            current = requireNonEofToken()
            endOffset = current.endOffset
        }

        return fileSubstring(startOffset, endOffset)
    }

    /**
     * Parses a comma-separated list of exception types in a `throws` clause up to (but not
     * consuming) the terminating `;`.
     */
    private fun parseThrows(
        typeItemFactory: TextTypeItemFactory,
    ): List<ExceptionTypeItem> {
        return buildList {
            // Tracks whether an exception type is expected next (true at the start of the list and
            // immediately after a comma).
            var comma = true
            while (true) {
                when (peekType()) {
                    SignatureTokenType.SEMICOLON -> {
                        // Leave the terminating ';' in the stream for the caller to consume.
                        break
                    }
                    SharedTokenType.COMMA -> {
                        val commaToken = consume()
                        if (comma) {
                            throw parseException("Expected exception, got ','", commaToken)
                        }
                        comma = true
                    }
                    else -> {
                        if (!comma) {
                            val unexpected = requireNonEofToken()
                            throw parseException(
                                "Expected ',' or ';' got ${text(unexpected)}",
                                unexpected,
                            )
                        }
                        comma = false
                        // Parse the qualified name of the thrown exception and resolve its type.
                        val startOffset = peek().startOffset
                        val exceptionType =
                            typeItemFactory.getExceptionType(
                                TypeString(parseQualifiedName(), startOffset)
                            )
                        add(exceptionType)
                    }
                }
            }
        }
    }

    /**
     * Scans the token stream from [tokenStream] for a type string, ensuring that the full type
     * string is gathered, even when there are type-use annotations.
     *
     * Note: this **should not** be used when the token after the type could contain annotations,
     * such as when multiple types appear as consecutive tokens. (This happens in the `implements`
     * list of a class definition, e.g. `class Foo implements test.pkg.Bar test.pkg.@A Baz`.)
     *
     * @return the complete [TypeString].
     */
    private fun scanForTypeString(): TypeString {
        val firstToken = peek()
        if (firstToken.type == SharedTokenType.EOF) {
            throw parseException("Unexpected end of file", firstToken)
        }
        val startOffset = firstToken.startOffset

        // Consume the initial (possibly annotated) identifier segment of the type.
        var endOffset = skipAnnotatedIdentifier()

        // Continue consuming qualified segments (`.Foo`), type argument lists (`<...>`),
        // nullability/vararg suffixes (`?`, `!`, `...`), array dimensions (`[]`), and type-use
        // annotations on array dimensions (`Foo @A []`).
        while (true) {
            when (peekType()) {
                SharedTokenType.DOT -> {
                    consume()
                    endOffset = skipAnnotatedIdentifier()
                }
                SharedTokenType.ANGLE_OPEN -> {
                    endOffset = skipTypeArgumentList()
                }
                SharedTokenType.QUESTION,
                SharedTokenType.EXCLAMATION,
                SharedTokenType.ELLIPSIS -> {
                    endOffset = consume().endOffset
                }
                SharedTokenType.BRACKET_OPEN -> {
                    consume()
                    val closeBracket = requireNonEofToken()
                    if (closeBracket.type != SharedTokenType.BRACKET_CLOSE) {
                        throw parseException(
                            "expected ], was ${text(closeBracket)}",
                            closeBracket,
                        )
                    }
                    endOffset = closeBracket.endOffset
                }
                SharedTokenType.AT -> {
                    // Type-use annotation before an array dimension or varargs suffix (e.g.
                    // `Foo @A []`).
                    endOffset = skipAnnotation()
                }
                else -> break
            }
        }

        return TypeString(fileSubstring(startOffset, endOffset), startOffset)
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
     * Parses an identifier token followed by a `:` token from [tokenStream], returning the
     * identifier text and throwing an [ApiParseException] if either is missing.
     */
    private fun parseNameWithColon(): String {
        val nameToken = requireNonEofToken()
        assertIdent(nameToken)
        return parseNameWithColon(text(nameToken))
    }

    /**
     * For Kotlin-style name/type ordering in signature files, the name is generally followed by a
     * colon (besides methods, where the colon comes after the parameter list). This method verifies
     * that a colon token follows [token] in [tokenStream] and consumes it, throwing an
     * [ApiParseException] if one isn't present.
     */
    private fun parseNameWithColon(token: String): String {
        if (!match(SharedTokenType.COLON)) {
            throw parseException(
                "Expecting name ending with \":\" but found $token.",
                peek(),
            )
        }
        return token
    }

    private fun qualifiedName(pkg: String, className: String): String {
        return "$pkg.$className"
    }
}
