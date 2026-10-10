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

package com.android.tools.metalava.model.text

import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.ClassOrigin
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.JAVA_LANG_DEPRECATED
import com.android.tools.metalava.model.MutableModifierList
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.TargetLanguage
import com.android.tools.metalava.model.TargetLanguageSet
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.api.surface.ApiVariant
import com.android.tools.metalava.model.createMutableModifiers
import com.android.tools.metalava.model.item.DefaultCodebase
import com.android.tools.metalava.model.item.SealedClassImplicitPermitTypesUpdater
import com.android.tools.metalava.model.parser.LineMap
import com.android.tools.metalava.model.parser.SharedTokenType
import com.android.tools.metalava.model.parser.TokenStream
import com.android.tools.metalava.model.text.CustomizableProperty.Companion.KOTLIN_STYLE_NULLS
import com.android.tools.metalava.model.type.TypeItemParser
import com.android.tools.metalava.model.type.TypeItemParserErrorReporter
import com.android.tools.metalava.model.value.Value
import com.android.tools.metalava.model.value.ValueParser
import com.android.tools.metalava.reporter.FileLocation
import com.android.tools.metalava.reporter.Issues
import java.nio.file.Path

/**
 * [SignatureFileConsumer] implementation that populates a [DefaultCodebase] from one or more
 * signature files.
 */
internal class SignatureFileCodebaseBuilder(
    /** Populates the [Codebase] from the parsed signature file. */
    val assembler: TextCodebaseAssembler,

    /** The [TargetLanguageSet] to use if an item does not have one specified. */
    override val defaultTargetLanguageSet: Set<TargetLanguage>,

    /** Merges class re-definitions across signature files. */
    val classMerger: ClassMerger,

    /** Consumer of [TextTypeParser.Stats] after all signature files have been parsed. */
    private val apiStatsConsumer: (TextTypeParser.Stats) -> Unit = {},
) : SignatureFileConsumer {
    /** The [DefaultCodebase] being populated. */
    val codebase: DefaultCodebase = assembler.codebase

    /** Report recoverable errors encountered while parsing types. */
    private val typeItemParserErrorReporter =
        object : TypeItemParserErrorReporter {
            override fun report(issue: Issues.Issue, message: String, charOffset: Int) {
                reportIssue(issue, message, fileLocation(charOffset))
            }
        }

    /** The [ValueParser] to use for creating [Value]s from a signature file. */
    val valueParser: ValueParser =
        ValueParser(
            codebase,
            TypeItemParser.forValueParser(codebase, typeItemParserErrorReporter),
        )

    /** Provides support for parsing and caching [TypeItem]s. Initialized in [startFile]. */
    lateinit var typeParser: TextTypeParser
        private set

    /** Provides support for creating [TypeItem]s for specific uses. Initialized in [startFile]. */
    lateinit var globalTypeItemFactory: TextTypeItemFactory
        private set

    /** The [Path] of the signature file currently being parsed. */
    lateinit var currentPath: Path
        private set

    /** The [LineMap] of the signature file currently being parsed. */
    lateinit var currentLineMap: LineMap
        private set

    /** The [ApiVariant] of the signature file currently being parsed. */
    lateinit var currentApiVariant: ApiVariant
        private set

    /**
     * The [ClassOrigin] to assign to classes and type aliases defined in the signature file
     * currently being parsed.
     */
    lateinit var currentClassOrigin: ClassOrigin
        private set

    /**
     * Whether types in the current file should be interpreted to be in Kotlin format (e.g. `?`
     * suffix means nullable, `!` suffix means unknown, and absence of a suffix means not nullable).
     */
    var kotlinStyleNulls: Boolean = false
        private set

    /** The [Path] of the previously parsed signature file, if any. */
    private var previousPath: Path? = null

    /** The [kotlinStyleNulls] setting of the previously parsed signature file, if any. */
    private var previousKotlinStyleNulls: Boolean? = null

    /** Get the [FileLocation] at the 0-based [charOffset] in the current file. */
    fun fileLocation(charOffset: Int): FileLocation =
        currentLineMap.fileLocation(currentPath, charOffset)

    /**
     * Report a recoverable issue encountered while parsing at [location].
     *
     * Note: Non-recoverable issues result in an exception being thrown.
     */
    fun reportIssue(issue: Issues.Issue, message: String, location: FileLocation) {
        codebase.reporter.report(issue, null, message, location)
    }

    /**
     * Record that [item] was loaded from a signature file that contains [currentApiVariant].
     *
     * If this item was already defined in a different API surface, this will not add the new
     * surface.
     */
    fun markSelectedApiVariant(item: SelectableItem) {
        item.selectedApi.addItemApiVariant(currentApiVariant)
    }

    /**
     * Called before parsing each [signatureFile] at [path] with [lineMap] and [format].
     *
     * Validates that [KOTLIN_STYLE_NULLS] is consistent across all parsed files and initializes
     * [typeParser] and [globalTypeItemFactory] on the first file.
     */
    override fun startFile(
        signatureFile: SignatureFile,
        path: Path,
        lineMap: LineMap,
        format: FileFormat,
    ) {
        currentPath = path
        currentLineMap = lineMap
        currentApiVariant = signatureFile.apiVariantFor(codebase.config.apiSurfaces)
        currentClassOrigin = signatureFile.classOrigin

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
        kotlinStyleNulls = kotlinStyleNullsForThisFile

        if (!::typeParser.isInitialized) {
            typeParser =
                TextTypeParser(codebase, kotlinStyleNullsForThisFile, typeItemParserErrorReporter)
            globalTypeItemFactory = TextTypeItemFactory(assembler, typeParser)
        }
    }

    /**
     * Completes processing after all signature files have been parsed, performing any deferred
     * class merges, reporting type parser statistics, and updating implicit permit types on sealed
     * classes.
     */
    override fun finish() {
        classMerger.performAnyDeferredMerges()

        apiStatsConsumer(typeParser.stats())

        // Update implicit permit types in any sealed class that does not have one provided.
        SealedClassImplicitPermitTypesUpdater.updateImplicitPermitTypes(codebase)
    }

    override fun parseModifierAnnotations(
        tokenStream: TokenStream,
        apiText: String,
        visibilityLevel: VisibilityLevel,
    ): MutableModifierList {
        val annotations = getAnnotations(tokenStream, apiText)
        val modifiers = createMutableModifiers(visibilityLevel, annotations)
        // @Deprecated is also treated as a "modifier"
        if (annotations.any { it.qualifiedName == JAVA_LANG_DEPRECATED }) {
            modifiers.setDeprecated(true)
        }
        return modifiers
    }

    /**
     * Collects all the sequential annotations from [tokenStream], returning them as a (possibly
     * empty) list.
     */
    private fun getAnnotations(
        tokenStream: TokenStream,
        apiText: String,
    ): List<AnnotationItem> = buildList {
        while (tokenStream.peekType() == SharedTokenType.AT) {
            // Parse the annotation from the tokenStream, unshortening the class name if necessary,
            // and add it to the list if it is recognized.
            valueParser.parseAnnotationItem(tokenStream, apiText, unshorten = true)?.let {
                annotationItem ->
                add(annotationItem)
            }
        }
    }
}
