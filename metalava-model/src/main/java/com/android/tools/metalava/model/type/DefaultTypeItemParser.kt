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

package com.android.tools.metalava.model.type

import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.ReferenceTypeItem
import com.android.tools.metalava.model.TypeArgumentTypeItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeModifiers
import com.android.tools.metalava.model.TypeNullability
import com.android.tools.metalava.model.TypeParameterScope
import com.android.tools.metalava.model.VariableTypeItem
import com.android.tools.metalava.model.WellKnownTypes.JAVA_LANG_OBJECT_NON_NULL_TYPE
import com.android.tools.metalava.model.WellKnownTypes.JAVA_LANG_OBJECT_PLATFORM_TYPE
import com.android.tools.metalava.model.WildcardTypeItem
import com.android.tools.metalava.model.parser.SharedLexer
import com.android.tools.metalava.model.parser.SharedTokenType
import com.android.tools.metalava.model.parser.Token
import com.android.tools.metalava.model.parser.TokenStream
import com.android.tools.metalava.model.parser.TokenType

/**
 * Recursive-descent parser for [TypeItem]s that consumes [Token]s from a [TokenStream] produced by
 * [SharedLexer] (or `SignatureFileLexer`).
 *
 * @param unqualifiedClassHandler responsible for determining how to handle unqualified types.
 * @param kotlinStyleNulls whether Kotlin-style nulls (`?` for nullable, `!` for platform, and no
 *   suffix for non-null) are supported.
 * @param errorReporter channel for reporting recoverable errors found while parsing.
 */
open class DefaultTypeItemParser(
    private val unqualifiedClassHandler: UnqualifiedClassHandler,
    val kotlinStyleNulls: Boolean = false,
    private val errorReporter: TypeItemParserErrorReporter = TypeItemParserErrorReporter.THROWING,
) : TypeItemParser {
    /** A [TypeItem] representing `java.lang.Object`, suitable for general use. */
    private val objectType =
        if (kotlinStyleNulls) JAVA_LANG_OBJECT_NON_NULL_TYPE else JAVA_LANG_OBJECT_PLATFORM_TYPE

    /**
     * Parses [type] into a [TypeItem] in the context of the type parameters from
     * [typeParameterScope], if applicable.
     *
     * @param type the raw type string to tokenize and parse.
     * @param typeParameterScope the in-scope type parameters for resolving [VariableTypeItem]s.
     * @param contextNullability contextual nullability constraints (such as forced non-null for
     *   supertype clauses) to apply after parsing.
     */
    override fun obtainTypeFromString(
        type: String,
        typeParameterScope: TypeParameterScope,
        contextNullability: ContextNullability,
    ): TypeItem {
        val typeItem =
            parseTypeWithContextNullability(
                type = type,
                typeParameterScope = typeParameterScope,
                contextNullability = contextNullability,
            )
        return applyContextNullability(typeItem, contextNullability)
    }

    override fun typeParameterStrings(typeString: String?): List<String> =
        Companion.typeParameterStrings(typeString)

    /**
     * Parses a [TypeItem] directly from [tokens] (backed by [sourceText]) in the context of
     * [typeParameterScope].
     *
     * Unlike [obtainTypeFromString], this does not require the type to consume the entire stream;
     * tokens following the type (such as parameter names, method names, or closing delimiters) are
     * left in [tokens] for the caller to consume.
     *
     * @param tokens the [TokenStream] positioned at the start of the type.
     * @param sourceText the full source text backing [tokens] (used for slicing substrings by token
     *   offsets).
     * @param typeParameterScope the in-scope type parameters for resolving [VariableTypeItem]s.
     * @param contextNullability contextual nullability constraints to apply after parsing.
     */
    fun obtainTypeFromStream(
        tokens: TokenStream,
        sourceText: String,
        typeParameterScope: TypeParameterScope = TypeParameterScope.empty,
        contextNullability: ContextNullability = ContextNullability.none,
    ): TypeItem {
        val forceClassToBeNonNull =
            contextNullability.forcedNullability == TypeNullability.NONNULL || kotlinStyleNulls
        val typeItem =
            parseTypeFromStream(
                tokens,
                sourceText = sourceText,
                typeParameterScope = typeParameterScope,
                forceClassToBeNonNull = forceClassToBeNonNull,
                expectEndOfStream = false,
            )
        return applyContextNullability(typeItem, contextNullability)
    }

    /**
     * Post-processes a parsed [typeItem] to apply any forced or inferred nullability from
     * [contextNullability].
     */
    private fun applyContextNullability(
        typeItem: TypeItem,
        contextNullability: ContextNullability,
    ): TypeItem {
        // Check if the type's nullability needs to be updated based on the context.
        val typeNullability = typeItem.modifiers.nullability
        val actualTypeNullability =
            contextNullability.compute(typeNullability, typeItem.modifiers.annotations)
        return if (actualTypeNullability != typeNullability) {
            typeItem.substitute(actualTypeNullability)
        } else typeItem
    }

    /**
     * Parse [type] and return a [TypeItem], in the context of type parameters from
     * [typeParameterScope], if applicable.
     *
     * This will also map [contextNullability] to a [Boolean] that controls whether a
     * [ClassTypeItem] is forced to be non-null, taking into account [kotlinStyleNulls].
     */
    private fun parseTypeWithContextNullability(
        type: String,
        typeParameterScope: TypeParameterScope,
        contextNullability: ContextNullability = ContextNullability.none,
    ): TypeItem {
        // Class types used as super types, i.e. in an extends or implements list are forced to be
        // [TypeNullability.NONNULL], just as they would be if kotlinStyleNulls was true. Use the
        // same cache key for both so that they reuse cached types where possible.
        val forceClassToBeNonNull =
            contextNullability.forcedNullability == TypeNullability.NONNULL || kotlinStyleNulls

        return parseType(type, typeParameterScope, forceClassToBeNonNull)
    }

    /**
     * Converts [type] to a [TypeItem] in the context of [typeParameterScope].
     *
     * @param type the type string to parse.
     * @param typeParameterScope the in-scope type parameters.
     * @param forceClassToBeNonNull if `true`, forces an outermost [ClassTypeItem] without a
     *   nullability suffix to have [TypeNullability.NONNULL].
     */
    protected open fun parseType(
        type: String,
        typeParameterScope: TypeParameterScope,
        forceClassToBeNonNull: Boolean = false,
    ): TypeItem =
        parseTypeFromStream(
            tokens = SharedLexer(type).tokenize(),
            sourceText = type,
            typeParameterScope = typeParameterScope,
            forceClassToBeNonNull = forceClassToBeNonNull,
            expectEndOfStream = true,
        )

    /**
     * Creates a [TypeModifiers] from nullability information.
     *
     * If [knownNullability] is `null`, falls back to [defaultNullability], or to
     * [TypeNullability.NONNULL] when [kotlinStyleNulls] is `true` and [TypeNullability.PLATFORM]
     * otherwise.
     */
    private fun createModifiers(
        knownNullability: TypeNullability?,
        defaultNullability: TypeNullability? = null,
    ): TypeModifiers {
        val nullability =
            knownNullability
                ?: defaultNullability
                ?: if (kotlinStyleNulls) {
                    TypeNullability.NONNULL
                } else {
                    TypeNullability.PLATFORM
                }

        return TypeModifiers.create(emptyList(), nullability)
    }

    /**
     * Represents a nested `.Inner<T>` class segment in a qualified class type.
     *
     * @property name the simple name of the nested class.
     * @property typeArgStrings type argument strings `<...>` applied to this nested class segment.
     */
    private class ClassSegment(
        val name: String,
        val typeArgStrings: List<String>,
    )

    /** Parses a [TypeItem] from [tokens], dispatching to [parseWildcard] or [parseNonWildcard]. */
    private fun parseTypeFromStream(
        tokens: TokenStream,
        sourceText: String,
        typeParameterScope: TypeParameterScope,
        forceClassToBeNonNull: Boolean,
        expectEndOfStream: Boolean,
    ): TypeItem {
        // A leading `?` always starts a wildcard type (`?`, `? extends T`, or `? super T`).
        if (tokens.peekType() == SharedTokenType.QUESTION) {
            return parseWildcard(
                tokens,
                sourceText,
                typeParameterScope,
            )
        }

        return parseNonWildcard(
            tokens,
            sourceText,
            typeParameterScope,
            forceClassToBeNonNull,
            expectEndOfStream,
        )
    }

    /**
     * Parses a wildcard type starting with `?` (`?`, `? extends Bound`, or `? super Bound`).
     *
     * If `?` is followed by unexpected tokens (e.g. `? blah`), reports an error to [errorReporter],
     * consumes the unexpected tokens up to the next type boundary, and falls back to an unbounded
     * wildcard.
     */
    private fun parseWildcard(
        tokens: TokenStream,
        sourceText: String,
        typeParameterScope: TypeParameterScope,
    ): WildcardTypeItem {
        val questionToken = tokens.consume()
        // Wildcard types always have UNDEFINED nullability.
        val typeModifiers = createModifiers(TypeNullability.UNDEFINED)

        return when (tokens.peekType()) {
            // Unbounded wildcard `?` at the end of a type or type argument list: uses an implicit
            // java.lang.Object extends bound.
            SharedTokenType.EOF,
            SharedTokenType.COMMA,
            SharedTokenType.ANGLE_CLOSE -> {
                TypeItem.createWildcardType(typeModifiers, objectType, null)
            }
            // Upper-bounded wildcard `? extends Bound`.
            SharedTokenType.EXTENDS -> {
                tokens.consume()
                val extendsBound =
                    parseWildcardBound(
                        tokens,
                        sourceText,
                        typeParameterScope,
                    )
                TypeItem.createWildcardType(typeModifiers, extendsBound, null)
            }
            // Lower-bounded wildcard `? super Bound`: also carries an implicit java.lang.Object
            // extends bound.
            SharedTokenType.SUPER -> {
                tokens.consume()
                val superBound =
                    parseWildcardBound(
                        tokens,
                        sourceText,
                        typeParameterScope,
                    )
                TypeItem.createWildcardType(typeModifiers, objectType, superBound)
            }
            // Malformed wildcard: report an error and recover as an unbounded wildcard.
            else -> {
                val lastToken = consumeUntilTypeBoundary(tokens)
                val wildcardText =
                    sourceText.substring(questionToken.startOffset, lastToken.endOffset)
                errorReporter.report(
                    "Type starts with \"?\" but doesn't appear to be wildcard: $wildcardText"
                )
                TypeItem.createWildcardType(typeModifiers, objectType, null)
            }
        }
    }

    /**
     * Scans the tokens of a wildcard bound up to the enclosing `,`, `>`, `)`, or `EOF` and parses
     * them as a [ReferenceTypeItem].
     */
    private fun parseWildcardBound(
        tokens: TokenStream,
        sourceText: String,
        typeParameterScope: TypeParameterScope,
    ): ReferenceTypeItem {
        val startOffset = tokens.peek().startOffset
        val lastToken = consumeUntilTypeBoundary(tokens)
        val boundType = sourceText.substring(startOffset, lastToken.endOffset)
        return parseTypeWithContextNullability(
            boundType,
            typeParameterScope,
        )
            as ReferenceTypeItem
    }

    /**
     * Parses a non-wildcard type: a [VariableTypeItem], [PrimitiveTypeItem], or [ClassTypeItem].
     */
    private fun parseNonWildcard(
        tokens: TokenStream,
        sourceText: String,
        typeParameterScope: TypeParameterScope,
        forceClassToBeNonNull: Boolean,
        expectEndOfStream: Boolean,
    ): TypeItem {
        val baseStartOffset = tokens.peek().startOffset
        val firstToken = tokens.consume()
        var baseEndOffset = firstToken.endOffset

        // Fast path: single-identifier base type (not followed by '.' or '<').
        // Handles primitives (`int`), type variables (`T`), and unqualified classes (`String`).
        if (
            tokens.peekType() != SharedTokenType.DOT &&
                tokens.peekType() != SharedTokenType.ANGLE_OPEN
        ) {
            val baseNullToken = matchNullabilityToken(tokens)
            val baseSliceEnd = baseNullToken?.endOffset ?: baseEndOffset
            val nullability =
                resolveNullability(
                    sourceText,
                    baseNullToken,
                    baseStartOffset,
                    baseSliceEnd,
                )
            val simpleName = firstToken.text

            // 1. Check if it is a type variable in scope first. If a type parameter in Kotlin
            // shadows a primitive or class name (e.g. `<int>` or `<String>`), it must be resolved
            // as a type variable rather than a primitive or class.
            typeParameterScope.findTypeParameter(simpleName)?.let { param ->
                val modifiers =
                    createModifiers(
                        nullability,
                        defaultNullability = TypeNullability.UNDEFINED,
                    )
                return TypeItem.createVariableType(modifiers, param)
            }

            // 2. Check if it is a primitive type.
            asPrimitive(
                    sourceText,
                    simpleName,
                    nullability,
                    baseStartOffset,
                    baseSliceEnd,
                )
                ?.let {
                    return it
                }

            // 3. Otherwise, it is an unqualified class type (or Kotlin `dynamic`).
            val qualifiedName =
                if (simpleName == "dynamic") {
                    // Kotlin `dynamic` types are currently represented as a ClassTypeItem
                    // (b/495459207).
                    simpleName
                } else {
                    unqualifiedClassHandler.handleUnqualifiedType(errorReporter, simpleName)
                }
            val defaultNullability = if (forceClassToBeNonNull) TypeNullability.NONNULL else null
            val classModifiers = createModifiers(nullability, defaultNullability)
            return TypeItem.createClassType(
                classModifiers,
                qualifiedName,
                emptyList(),
                null,
            )
        }

        // Qualified and/or parameterized class type.
        var outerRawName = firstToken.text

        // If the first identifier is lowercase (a package segment), consume `.pkg` segments until
        // we reach the first class name (which contains an uppercase character).
        if (!firstToken.text.any { it.isUpperCase() } && tokens.peekType() == SharedTokenType.DOT) {
            var nameBuilder: StringBuilder? = null
            while (tokens.peekType() == SharedTokenType.DOT) {
                val dotToken = tokens.consume() // consume '.'
                val nextIdent = tokens.consume()
                if (
                    nameBuilder != null ||
                        dotToken.startOffset != baseEndOffset ||
                        nextIdent.startOffset != dotToken.endOffset
                ) {
                    if (nameBuilder == null) {
                        nameBuilder =
                            StringBuilder(sourceText.length)
                                .append(
                                    sourceText,
                                    baseStartOffset,
                                    baseEndOffset,
                                )
                    }
                    nameBuilder.append('.').append(nextIdent.text)
                }
                baseEndOffset = nextIdent.endOffset
                if (nextIdent.text.any { it.isUpperCase() }) {
                    break
                }
            }
            outerRawName =
                nameBuilder?.toString() ?: sourceText.substring(baseStartOffset, baseEndOffset)
        }

        // If the outer class is parameterized (`Outer<P1>`), scan its type argument strings.
        var outerTypeArgStrings: List<String> = emptyList()
        var lastSegmentHadTypeArgs = false
        if (tokens.peekType() == SharedTokenType.ANGLE_OPEN) {
            val (argStrings, angleEndOffset) = scanTypeArguments(tokens, sourceText)
            outerTypeArgStrings = argStrings
            baseEndOffset = angleEndOffset
            lastSegmentHadTypeArgs = true
        }

        // Parse any nested class segments (`.Inner` or `.Inner<P2>`).
        var nestedSegments: MutableList<ClassSegment>? = null
        var lastSegmentStartOffset = baseStartOffset
        while (tokens.peekType() == SharedTokenType.DOT) {
            tokens.consume() // consume '.'
            lastSegmentStartOffset = tokens.peek().startOffset
            val innerIdent = tokens.consume()
            baseEndOffset = innerIdent.endOffset
            var innerTypeArgStrings: List<String> = emptyList()
            lastSegmentHadTypeArgs = false
            if (tokens.peekType() == SharedTokenType.ANGLE_OPEN) {
                val (argStrings, angleEndOffset) = scanTypeArguments(tokens, sourceText)
                innerTypeArgStrings = argStrings
                baseEndOffset = angleEndOffset
                lastSegmentHadTypeArgs = true
            }
            if (nestedSegments == null) {
                nestedSegments = mutableListOf()
            }
            nestedSegments.add(
                ClassSegment(
                    innerIdent.text,
                    innerTypeArgStrings,
                )
            )
        }

        // Check for unexpected trailing tokens immediately following a `<...>` type argument list
        // (e.g. `Comparable<test.pkg.Foo>blah2`).
        if (
            lastSegmentHadTypeArgs &&
                (expectEndOfStream || tokens.peek().startOffset == baseEndOffset) &&
                !isValidAfterBaseType(tokens.peekType(), expectEndOfStream)
        ) {
            val remainderStart = baseEndOffset
            val lastUnexpected = consumeUntilTypeBoundary(tokens)
            val fullText = sourceText.substring(lastSegmentStartOffset, lastUnexpected.endOffset)
            val remainderText = sourceText.substring(remainderStart, lastUnexpected.endOffset)
            errorReporter.report(
                "Could not parse type `$fullText`. Found unexpected string after type parameters: $remainderText"
            )
        }

        val baseNullToken = matchNullabilityToken(tokens)
        val baseSliceEnd = baseNullToken?.endOffset ?: baseEndOffset
        val nullability =
            resolveNullability(
                sourceText,
                baseNullToken,
                baseStartOffset,
                baseSliceEnd,
            )

        // Resolve the outer class's qualified name and parse its type arguments.
        val outerQualifiedName =
            if (outerRawName.contains('.')) {
                outerRawName
            } else {
                unqualifiedClassHandler.handleUnqualifiedType(errorReporter, outerRawName)
            }
        val outerTypeArgs =
            outerTypeArgStrings.map { argType ->
                parseTypeWithContextNullability(
                    argType,
                    typeParameterScope,
                )
                    as TypeArgumentTypeItem
            }

        // If there are nested class segments (e.g. `Outer.Inner`), the outer class is always
        // non-null.
        val hasNested = !nestedSegments.isNullOrEmpty()
        val defaultNullability = if (forceClassToBeNonNull) TypeNullability.NONNULL else null
        val outerModifiers =
            if (hasNested) {
                createModifiers(TypeNullability.NONNULL)
            } else {
                createModifiers(nullability, defaultNullability)
            }

        var currentClassType =
            TypeItem.createClassType(
                outerModifiers,
                outerQualifiedName,
                outerTypeArgs,
                null,
            )

        // Build each nested ClassTypeItem in order from outer to inner, linking each to its
        // enclosing `currentClassType`.
        if (nestedSegments != null) {
            val lastIndex = nestedSegments.lastIndex
            for (i in 0..lastIndex) {
                val segment = nestedSegments[i]
                val isLast = i == lastIndex
                // Enclosing nested classes are non-null; the innermost nested class receives
                // `nullability`.
                val segmentModifiers =
                    if (!isLast) {
                        createModifiers(TypeNullability.NONNULL)
                    } else {
                        createModifiers(nullability, defaultNullability)
                    }
                val segmentTypeArgs =
                    segment.typeArgStrings.map { argType ->
                        parseTypeWithContextNullability(
                            argType,
                            typeParameterScope,
                        )
                            as TypeArgumentTypeItem
                    }
                val nestedQualifiedName = "${currentClassType.qualifiedName}.${segment.name}"
                currentClassType =
                    TypeItem.createClassType(
                        segmentModifiers,
                        nestedQualifiedName,
                        segmentTypeArgs,
                        currentClassType,
                    )
            }
        }

        return currentClassType
    }

    /**
     * Attempts to match [name] as a primitive type name, returning a [PrimitiveTypeItem] or `null`
     * if [name] is not a primitive type.
     *
     * Reports an error to [errorReporter] if a non-`NONNULL` [nullability] suffix was applied to a
     * primitive type.
     */
    private fun asPrimitive(
        sourceText: String,
        name: String,
        nullability: TypeNullability?,
        startOffset: Int,
        endOffset: Int,
    ): PrimitiveTypeItem? {
        val kind =
            when (name) {
                "byte" -> PrimitiveTypeItem.Primitive.BYTE
                "char" -> PrimitiveTypeItem.Primitive.CHAR
                "double" -> PrimitiveTypeItem.Primitive.DOUBLE
                "float" -> PrimitiveTypeItem.Primitive.FLOAT
                "int" -> PrimitiveTypeItem.Primitive.INT
                "long" -> PrimitiveTypeItem.Primitive.LONG
                "short" -> PrimitiveTypeItem.Primitive.SHORT
                "boolean" -> PrimitiveTypeItem.Primitive.BOOLEAN
                "void" -> PrimitiveTypeItem.Primitive.VOID
                else -> return null
            }
        if (nullability != null && nullability != TypeNullability.NONNULL) {
            val original = sourceText.substring(startOffset, endOffset)
            errorReporter.report("Invalid nullability suffix on primitive: $original")
        }
        // Primitives are always non-null.
        val typeModifiers = createModifiers(TypeNullability.NONNULL)
        return TypeItem.createPrimitiveType(typeModifiers, kind)
    }

    /**
     * Consumes and returns the next token if it is a nullability suffix (`?` or `!`), or returns
     * `null` otherwise.
     */
    private fun matchNullabilityToken(
        tokens: TokenStream,
    ): Token? {
        val type = tokens.peekType()
        return if (type == SharedTokenType.QUESTION || type == SharedTokenType.EXCLAMATION) {
            tokens.consume()
        } else {
            null
        }
    }

    /**
     * Converts a matched [nullToken] (`?` or `!`) into a [TypeNullability].
     *
     * If [kotlinStyleNulls] is `false`, reports an error for the slice `[startOffset, endOffset)`
     * and falls back to [TypeNullability.PLATFORM].
     */
    private fun resolveNullability(
        sourceText: String,
        nullToken: Token?,
        startOffset: Int,
        endOffset: Int,
    ): TypeNullability? {
        if (nullToken == null) return null
        return if (kotlinStyleNulls) {
            if (nullToken.type == SharedTokenType.QUESTION) {
                TypeNullability.NULLABLE
            } else {
                TypeNullability.PLATFORM
            }
        } else {
            val typeSlice = sourceText.substring(startOffset, endOffset)
            errorReporter.report(
                "Format does not support Kotlin-style null type syntax: $typeSlice"
            )
            TypeNullability.PLATFORM
        }
    }

    /**
     * Returns `true` if [type] can validly follow a parameterized class type.
     *
     * When [expectEndOfStream] is `false` (i.e. [obtainTypeFromStream]), non-identifier delimiter
     * tokens (such as `)` at the end of a parameter list) are also permitted immediately after `>`.
     */
    private fun isValidAfterBaseType(type: TokenType, expectEndOfStream: Boolean): Boolean =
        type == SharedTokenType.EOF ||
            type == SharedTokenType.QUESTION ||
            type == SharedTokenType.EXCLAMATION ||
            type == SharedTokenType.COMMA ||
            type == SharedTokenType.ANGLE_CLOSE ||
            (!expectEndOfStream && !type.canBeIdentifier)

    /**
     * Consumes and discards tokens from [tokens] up to the end of the current type (stopping before
     * an unbalanced `,`, `>`, `)`, or `EOF`), returning the last consumed [Token].
     */
    private fun consumeUntilTypeBoundary(tokens: TokenStream): Token {
        var lastToken = tokens.peek()
        var angleDepth = 0
        while (tokens.peekType() != SharedTokenType.EOF) {
            val nextType = tokens.peekType()
            if (
                angleDepth == 0 &&
                    (nextType == SharedTokenType.COMMA ||
                        nextType == SharedTokenType.ANGLE_CLOSE ||
                        nextType == SharedTokenType.PAREN_CLOSE)
            ) {
                break
            }
            val token = tokens.consume()
            lastToken = token
            when (token.type) {
                SharedTokenType.ANGLE_OPEN -> angleDepth++
                SharedTokenType.ANGLE_CLOSE -> if (angleDepth > 0) angleDepth--
            }
        }
        return lastToken
    }

    companion object {
        /**
         * Breaks a string representing type parameters into a list of the type parameter strings.
         *
         * E.g. `"<A, B, C>"` -> `["A", "B", "C"]` and `"<List<A>, B>"` -> `["List<A>", "B"]`.
         */
        fun typeParameterStrings(typeString: String?): List<String> =
            typeParameterStringsWithRemainder(typeString).first

        /**
         * Breaks a string representing type parameters into a list of the type parameter strings,
         * and also returns the remainder of the string after the closing `>`.
         *
         * E.g. `"<A, B, C>.Inner"` -> `Pair(["A", "B", "C"], ".Inner")`
         */
        fun typeParameterStringsWithRemainder(typeString: String?): Pair<List<String>, String?> {
            val s = typeString ?: return Pair(emptyList(), null)
            val tokens = SharedLexer(s).tokenize()
            if (tokens.peekType() != SharedTokenType.ANGLE_OPEN) {
                return Pair(emptyList(), s)
            }
            val (list, endOffset) = scanTypeArguments(tokens, s)
            val remainder =
                if (endOffset == -1 || endOffset == s.length) {
                    null
                } else {
                    s.substring(endOffset)
                }
            return Pair(list, remainder)
        }

        /**
         * Consumes a `<...>` type argument/parameter list from [tokens] and returns the list of
         * type argument strings sliced from [sourceText] along with the exclusive end offset of the
         * closing `>` (or `-1` if unclosed).
         */
        private fun scanTypeArguments(
            tokens: TokenStream,
            sourceText: String,
        ): Pair<List<String>, Int> {
            val openAngle = tokens.consume() // consume '<'
            var endOffset = openAngle.endOffset
            val args = mutableListOf<String>()
            var angleDepth = 1
            var argStartOffset = -1
            var argEndOffset = -1

            while (angleDepth > 0 && tokens.peekType() != SharedTokenType.EOF) {
                val token = tokens.consume()
                endOffset = token.endOffset
                when (token.type) {
                    // Track nested angle bracket depth for nested generic types (`List<Map<A,
                    // B>>`).
                    SharedTokenType.ANGLE_OPEN -> {
                        if (argStartOffset == -1) argStartOffset = token.startOffset
                        argEndOffset = token.endOffset
                        angleDepth++
                    }
                    SharedTokenType.ANGLE_CLOSE -> {
                        angleDepth--
                        if (angleDepth == 0) {
                            // Reached the closing `>` of the outermost type argument list.
                            if (argStartOffset != -1) {
                                args.add(sourceText.substring(argStartOffset, argEndOffset))
                            }
                            return Pair(args, endOffset)
                        } else {
                            if (argStartOffset == -1) argStartOffset = token.startOffset
                            argEndOffset = token.endOffset
                        }
                    }
                    SharedTokenType.COMMA -> {
                        if (angleDepth == 1) {
                            // Top-level comma separating type arguments.
                            if (argStartOffset != -1) {
                                args.add(sourceText.substring(argStartOffset, argEndOffset))
                                argStartOffset = -1
                                argEndOffset = -1
                            }
                        } else {
                            if (argStartOffset == -1) argStartOffset = token.startOffset
                            argEndOffset = token.endOffset
                        }
                    }
                    else -> {
                        if (argStartOffset == -1) argStartOffset = token.startOffset
                        argEndOffset = token.endOffset
                    }
                }
            }
            return Pair(emptyList(), -1)
        }
    }
}
