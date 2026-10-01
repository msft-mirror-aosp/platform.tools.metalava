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

package com.android.tools.metalava.model.value

import com.android.tools.metalava.model.AnnotationContext
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.javaUnescapeString
import com.android.tools.metalava.model.parser.SharedLexer
import com.android.tools.metalava.model.parser.SharedTokenType
import com.android.tools.metalava.model.parser.Token
import com.android.tools.metalava.model.parser.TokenStream
import com.android.tools.metalava.model.type.TypeItemParser

/**
 * Recursive-descent parser for [Value]s and [AnnotationItem]s that consumes [Token]s from a
 * [TokenStream] produced by [SharedLexer] (or `SignatureFileLexer`).
 */
class DefaultValueParser(
    @Suppress("unused") private val annotationContext: AnnotationContext,
    @Suppress("unused") private val typeItemParser: TypeItemParser,
) : ValueParser, ValueFactory, ImplementationValueToModelFactory<String> {

    override fun providerFor(
        typeItem: TypeItem,
        text: String,
        valueUseSite: ValueUseSite,
    ): CombinedValueProvider = CachingValueProvider(this, typeItem, text, valueUseSite)

    override fun implementationValueToModelValue(
        optionalTypeItem: TypeItem?,
        implementationValue: String,
        valueUseSite: ValueUseSite,
    ) =
        when (valueUseSite) {
            ValueUseSite.ANNOTATION -> {
                // For annotations convert to any Value.
                parse(optionalTypeItem, implementationValue)
            }
            ValueUseSite.FIELD -> {
                // For fields convert to ConstantValues if possible, otherwise throw an exception.
                parseConstant(optionalTypeItem, implementationValue)
                    ?: unknownToken(optionalTypeItem, implementationValue)
            }
        }

    override fun parse(optionalTypeItem: TypeItem?, text: String): Value? {
        if (text.isEmpty()) return null
        val tokens = SharedLexer(text).tokenize()
        return parseFromStream(
            optionalTypeItem,
            tokens,
            text,
            expectEndOfStream = true,
        )
    }

    /**
     * Parse a [Value] of the [optionalTypeItem] directly from [tokens] (backed by [sourceText]).
     *
     * @param optionalTypeItem the optional target [TypeItem] for the value.
     * @param tokens the [TokenStream] positioned at the start of the value.
     * @param sourceText the full source text backing [tokens].
     * @param expectEndOfStream if `true`, requires that the parsed value consumes all tokens up to
     *   [SharedTokenType.EOF].
     */
    fun parseFromStream(
        optionalTypeItem: TypeItem?,
        tokens: TokenStream,
        sourceText: String,
        expectEndOfStream: Boolean = false,
    ): Value? =
        when {
            tokens.peekType() == SharedTokenType.EOF -> null
            else -> {
                parseArrayElementValue(
                    optionalTypeItem,
                    tokens,
                    sourceText,
                    expectEndOfStream = expectEndOfStream,
                )
            }
        }

    /** Parse an [ArrayElementValue] of the [optionalTypeItem] from [tokens]. */
    private fun parseArrayElementValue(
        optionalTypeItem: TypeItem?,
        tokens: TokenStream,
        sourceText: String,
        expectEndOfStream: Boolean,
    ): ArrayElementValue {
        val peekType = tokens.peekType()
        return when {
            peekType == SharedTokenType.STRING_LITERAL -> {
                val token = tokens.consume()
                if (expectEndOfStream && tokens.peekType() != SharedTokenType.EOF) {
                    unknownToken(optionalTypeItem, sourceText)
                }
                parseStringLiteral(optionalTypeItem, token.text)
            }
            peekType == SharedTokenType.CHAR_LITERAL -> {
                val token = tokens.consume()
                if (expectEndOfStream && tokens.peekType() != SharedTokenType.EOF) {
                    unknownToken(optionalTypeItem, sourceText)
                }
                parseCharLiteral(optionalTypeItem, token.text)
            }
            peekType.canBeIdentifier -> {
                parseIdentifierLedElementValue(
                    optionalTypeItem,
                    tokens,
                    sourceText,
                    expectEndOfStream = expectEndOfStream,
                )
            }
            else -> unknownToken(optionalTypeItem, sourceText)
        }
    }

    /**
     * Parses an identifier-led [ArrayElementValue] from [tokens]:
     * - Boolean literals (`true`, `false`)
     */
    private fun parseIdentifierLedElementValue(
        optionalTypeItem: TypeItem?,
        tokens: TokenStream,
        sourceText: String,
        expectEndOfStream: Boolean,
    ): ArrayElementValue {
        val firstIdent = tokens.consume()
        if (expectEndOfStream && tokens.peekType() != SharedTokenType.EOF) {
            unknownToken(optionalTypeItem, sourceText)
        }

        knownNamedConstantValues[firstIdent.text]?.let { constantValue ->
            return constantValue.convertToType(optionalTypeItem)
        }

        unknownToken(optionalTypeItem, sourceText)
    }

    /** Parse the [text] to provide a [ConstantValue] of the [optionalTypeItem]. */
    private fun parseConstant(optionalTypeItem: TypeItem?, text: String): ConstantValue? {
        if (text.isEmpty()) return null
        val tokens = SharedLexer(text).tokenize()
        return parseConstantFromStream(optionalTypeItem, tokens, text)
    }

    /**
     * Parses a [ConstantValue] of the [optionalTypeItem] from [tokens], requiring the entire stream
     * to be consumed. Returns `null` if [tokens] does not represent a constant value.
     */
    private fun parseConstantFromStream(
        optionalTypeItem: TypeItem?,
        tokens: TokenStream,
        @Suppress("unused") sourceText: String,
    ): ConstantValue? {
        val peekType = tokens.peekType()
        val constant =
            when {
                peekType == SharedTokenType.STRING_LITERAL -> {
                    val token = tokens.consume()
                    parseStringLiteral(optionalTypeItem, token.text)
                }
                peekType == SharedTokenType.CHAR_LITERAL -> {
                    val token = tokens.consume()
                    parseCharLiteral(optionalTypeItem, token.text)
                }
                peekType.canBeIdentifier -> {
                    val firstIdent = tokens.consume()
                    if (tokens.peekType() != SharedTokenType.EOF) {
                        return null
                    }
                    knownNamedConstantValues[firstIdent.text]?.convertToType(optionalTypeItem)
                }
                else -> null
            } ?: return null

        return if (tokens.peekType() == SharedTokenType.EOF) constant else null
    }

    /** Parses a double-quoted [text] token into a String [ConstantValue]. */
    private fun parseStringLiteral(optionalTypeItem: TypeItem?, text: String): ConstantValue {
        if (text.length < 2 || text.last() != '"') {
            error("string '$text' starts with \" but does not end with \"")
        }
        val string = javaUnescapeString(text.substring(1, text.length - 1))
        return createLiteralValue(optionalTypeItem, string)
    }

    /** Parses a single-quoted [text] token into a Char [ConstantValue]. */
    private fun parseCharLiteral(optionalTypeItem: TypeItem?, text: String): ConstantValue {
        if (text.length < 2 || text.last() != '\'') {
            error("character \"$text\" starts with ' but does not end with '")
        }
        val string = javaUnescapeString(text.substring(1, text.length - 1))
        if (string.length != 1) {
            error(
                "character \"$text\" should contain a single character but contains ${string.length}"
            )
        }
        val char = string[0]
        return createLiteralValue(optionalTypeItem, char)
    }

    /** Throw an exception when [text] cannot be parsed. */
    private fun unknownToken(optionalTypeItem: TypeItem?, text: String): Nothing =
        throw ValueProviderException("Unknown token <$text> of $optionalTypeItem")

    override fun parseAnnotationItem(text: String, unshorten: Boolean): AnnotationItem? =
        TODO("Annotation parsing from String is not yet supported by DefaultValueParser")

    companion object {
        /** Map of all known named constant values (booleans). */
        private val knownNamedConstantValues: Map<String, LiteralValue<*>> =
            mapOf(
                "false" to BooleanValue.FALSE,
                "true" to BooleanValue.TRUE,
            )
    }
}
