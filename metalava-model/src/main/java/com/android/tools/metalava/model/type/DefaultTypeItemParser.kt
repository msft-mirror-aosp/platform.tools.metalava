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

import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeModifiers
import com.android.tools.metalava.model.TypeNullability
import com.android.tools.metalava.model.TypeParameterScope
import com.android.tools.metalava.model.parser.SharedLexer
import com.android.tools.metalava.model.parser.SharedTokenType
import com.android.tools.metalava.model.parser.Token
import com.android.tools.metalava.model.parser.TokenStream

/**
 * Recursive-descent parser for [TypeItem]s that consumes [Token]s from a [TokenStream] produced by
 * [SharedLexer] (or `SignatureFileLexer`).
 *
 * @param kotlinStyleNulls whether Kotlin-style nulls (`?` for nullable, `!` for platform, and no
 *   suffix for non-null) are supported.
 * @param errorReporter channel for reporting recoverable errors found while parsing.
 */
open class DefaultTypeItemParser(
    val kotlinStyleNulls: Boolean = false,
    private val errorReporter: TypeItemParserErrorReporter = TypeItemParserErrorReporter.THROWING,
) : TypeItemParser {
    /**
     * Parses [type] into a [TypeItem].
     *
     * @param type the raw type string to tokenize and parse.
     */
    override fun obtainTypeFromString(
        type: String,
        typeParameterScope: TypeParameterScope,
        contextNullability: ContextNullability,
    ): TypeItem = parseType(type)

    override fun typeParameterStrings(typeString: String?): List<String> = error("Unsupported")

    /**
     * Converts [type] to a [TypeItem].
     *
     * @param type the type string to parse.
     */
    protected open fun parseType(
        type: String,
    ): TypeItem =
        parseNonWildcard(
            tokens = SharedLexer(type).tokenize(),
            sourceText = type,
        )

    /** Creates a [TypeModifiers] with [nullability]. */
    private fun createModifiers(
        nullability: TypeNullability,
    ): TypeModifiers = TypeModifiers.create(emptyList(), nullability)

    /** Parses a non-wildcard type: a [PrimitiveTypeItem]. */
    private fun parseNonWildcard(
        tokens: TokenStream,
        sourceText: String,
    ): TypeItem {
        val baseStartOffset = tokens.peek().startOffset
        val firstToken = tokens.consume()
        val baseEndOffset = firstToken.endOffset

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

        // Check if it is a primitive type.
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

        error("Unsupported type: $simpleName")
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
}
