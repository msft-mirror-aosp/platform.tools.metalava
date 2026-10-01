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
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterScope
import com.android.tools.metalava.model.javaUnescapeString
import com.android.tools.metalava.model.parser.SharedLexer
import com.android.tools.metalava.model.parser.SharedTokenType
import com.android.tools.metalava.model.parser.Token
import com.android.tools.metalava.model.parser.TokenStream
import com.android.tools.metalava.model.type.ContextNullability
import com.android.tools.metalava.model.type.TypeItemParser

/**
 * Recursive-descent parser for [Value]s and [AnnotationItem]s that consumes [Token]s from a
 * [TokenStream] produced by [SharedLexer] (or `SignatureFileLexer`).
 */
class DefaultValueParser(
    private val annotationContext: AnnotationContext,
    private val typeItemParser: TypeItemParser,
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
            peekType == SharedTokenType.PAREN_OPEN ||
                peekType == SharedTokenType.PLUS ||
                peekType == SharedTokenType.MINUS ||
                peekType == SharedTokenType.NUMBER_LITERAL -> {
                val constant =
                    parseNumberOrSpecialFloatDivision(optionalTypeItem, tokens, sourceText)
                        ?: unknownToken(optionalTypeItem, sourceText)
                if (expectEndOfStream && tokens.peekType() != SharedTokenType.EOF) {
                    unknownToken(optionalTypeItem, sourceText)
                }
                constant
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
     * - Named special float constants (`Double.NaN`, `java.lang.Double.NaN`, etc.)
     * - Class literals (`<type>.class`, `<type>::class`, `<type>::class.java`)
     * - Field references (`(QualifiedClassName.)?FieldName(.toXxx())?`)
     */
    private fun parseIdentifierLedElementValue(
        optionalTypeItem: TypeItem?,
        tokens: TokenStream,
        sourceText: String,
        expectEndOfStream: Boolean,
    ): ArrayElementValue {
        val firstIdent = tokens.consume()
        val startOffset = firstIdent.startOffset
        var endOffset = firstIdent.endOffset
        var secondPenultEndOffset = -1
        var penultEndOffset = -1
        var penultLastIdent = ""
        var lastIdent = firstIdent.text

        // Consume dot-separated identifier segments (`pkg.Outer.Inner.FIELD` or `pkg.Foo.class`).
        while (tokens.peekType() == SharedTokenType.DOT) {
            tokens.consume() // consume '.'
            if (!tokens.peekType().canBeIdentifier) {
                unknownToken(optionalTypeItem, sourceText)
            }
            val nextIdent = tokens.consume()
            secondPenultEndOffset = penultEndOffset
            penultEndOffset = endOffset
            penultLastIdent = lastIdent
            lastIdent = nextIdent.text
            endOffset = nextIdent.endOffset
        }

        val nextType = tokens.peekType()

        // 1. Simple or qualified Java class literal `<type>.class` without generics or brackets.
        if (
            penultEndOffset != -1 &&
                lastIdent == "class" &&
                nextType != SharedTokenType.ANGLE_OPEN &&
                nextType != SharedTokenType.BRACKET_OPEN &&
                nextType != SharedTokenType.PAREN_OPEN &&
                nextType != SharedTokenType.DOUBLE_COLON
        ) {
            if (expectEndOfStream && nextType != SharedTokenType.EOF) {
                unknownToken(optionalTypeItem, sourceText)
            }
            val typeString = sourceText.substring(startOffset, penultEndOffset)
            val fullText = sourceText.substring(startOffset, endOffset)
            return createClassLiteralValue(typeString, fullText)
        }

        // 2. Class literal with generics `<...>`, array brackets `[]`, or Kotlin `::class(.java)?`.
        if (
            nextType == SharedTokenType.ANGLE_OPEN ||
                nextType == SharedTokenType.BRACKET_OPEN ||
                nextType == SharedTokenType.DOUBLE_COLON
        ) {
            var typeEndOffset = endOffset
            var matchedDotClass = false

            while (true) {
                when (tokens.peekType()) {
                    SharedTokenType.ANGLE_OPEN -> {
                        tokens.consume()
                        var angleDepth = 1
                        while (angleDepth > 0 && tokens.peekType() != SharedTokenType.EOF) {
                            val token = tokens.consume()
                            if (token.type == SharedTokenType.ANGLE_OPEN) {
                                angleDepth++
                            } else if (token.type == SharedTokenType.ANGLE_CLOSE) {
                                angleDepth--
                            }
                            typeEndOffset = token.endOffset
                        }
                        if (angleDepth != 0) {
                            unknownToken(optionalTypeItem, sourceText)
                        }
                    }
                    SharedTokenType.BRACKET_OPEN -> {
                        tokens.consume()
                        if (tokens.peekType() != SharedTokenType.BRACKET_CLOSE) {
                            unknownToken(optionalTypeItem, sourceText)
                        }
                        typeEndOffset = tokens.consume().endOffset
                    }
                    SharedTokenType.DOT -> {
                        tokens.consume()
                        if (tokens.peekType() == SharedTokenType.CLASS) {
                            endOffset = tokens.consume().endOffset
                            matchedDotClass = true
                            break
                        } else if (tokens.peekType().canBeIdentifier) {
                            typeEndOffset = tokens.consume().endOffset
                        } else {
                            unknownToken(optionalTypeItem, sourceText)
                        }
                    }
                    else -> break
                }
            }

            if (!matchedDotClass) {
                if (
                    !tokens.match(SharedTokenType.DOUBLE_COLON) ||
                        tokens.peekType() != SharedTokenType.CLASS
                ) {
                    unknownToken(optionalTypeItem, sourceText)
                }
                endOffset = tokens.consume().endOffset // consume 'class'

                // Optional `.java` suffix on Kotlin class literals (`<type>::class.java`).
                if (tokens.peekType() == SharedTokenType.DOT) {
                    tokens.consume() // consume '.'
                    if (tokens.peek().text != "java") {
                        unknownToken(optionalTypeItem, sourceText)
                    }
                    endOffset = tokens.consume().endOffset // consume 'java'
                }
            }

            if (expectEndOfStream && tokens.peekType() != SharedTokenType.EOF) {
                unknownToken(optionalTypeItem, sourceText)
            }

            val typeString = sourceText.substring(startOffset, typeEndOffset)
            val fullText = sourceText.substring(startOffset, endOffset)
            return createClassLiteralValue(typeString, fullText)
        }

        // 3. Followed by `(`: a Kotlin numeric conversion call `.toXxx()` on a field reference.
        if (nextType == SharedTokenType.PAREN_OPEN) {
            val conversionKind =
                if (penultEndOffset != -1) {
                    PrimitiveTypeItem.Primitive.forKotlinNumericConversionFunctionName(lastIdent)
                } else {
                    null
                }

            if (conversionKind != null) {
                tokens.consume() // consume '('
                if (!tokens.match(SharedTokenType.PAREN_CLOSE)) {
                    unknownToken(optionalTypeItem, sourceText)
                }
                if (expectEndOfStream && tokens.peekType() != SharedTokenType.EOF) {
                    unknownToken(optionalTypeItem, sourceText)
                }
                val fieldName = penultLastIdent
                val className =
                    if (secondPenultEndOffset != -1) {
                        sourceText.substring(startOffset, secondPenultEndOffset)
                    } else {
                        ""
                    }
                return createFieldReference(
                    className = className,
                    fieldName = fieldName,
                    optionalTypeItem = optionalTypeItem,
                    explicitConversionTo = conversionKind,
                )
            }

            unknownToken(optionalTypeItem, sourceText)
        }

        // 4. Plain simple or dot-qualified identifier: check boolean/special float constants first,
        // otherwise treat as a field reference.
        if (expectEndOfStream && nextType != SharedTokenType.EOF) {
            unknownToken(optionalTypeItem, sourceText)
        }

        val fullName =
            if (penultEndOffset == -1) {
                lastIdent
            } else {
                sourceText.substring(startOffset, endOffset)
            }

        knownNamedConstantValues[fullName]?.let { constantValue ->
            return constantValue.convertToType(optionalTypeItem)
        }

        val fieldName = lastIdent
        val className =
            if (penultEndOffset != -1) {
                sourceText.substring(startOffset, penultEndOffset)
            } else {
                ""
            }
        return createFieldReference(
            className = className,
            fieldName = fieldName,
            optionalTypeItem = optionalTypeItem,
            explicitConversionTo = null,
        )
    }

    /** Creates a [ClassObjectValue] by parsing [typeString] via [typeItemParser]. */
    private fun createClassLiteralValue(
        typeString: String,
        fullText: String,
    ): ClassObjectValue {
        val classLiteralTypeItem =
            typeItemParser.obtainTypeFromString(
                typeString,
                TypeParameterScope.empty,
                ContextNullability.forceNonNull,
            )
        return createClassObjectValue(classLiteralTypeItem, fullText)
    }

    /**
     * Creates a [FieldReferenceValue] (or normalized constant value) for [className].[fieldName].
     */
    private fun createFieldReference(
        className: String,
        fieldName: String,
        optionalTypeItem: TypeItem?,
        explicitConversionTo: PrimitiveTypeItem.Primitive?,
    ): ArrayElementValue {
        val classTypeItem =
            typeItemParser.obtainTypeFromString(
                className,
                TypeParameterScope.empty,
                ContextNullability.forceNonNull,
            ) as ClassTypeItem

        val qualifiedClassName = classTypeItem.qualifiedName
        return createFieldReferenceValueWithDeferredConstantValue(
            annotationContext,
            qualifiedClassName,
            fieldName,
            optionalTypeItem,
            explicitConversionTo = explicitConversionTo,
        )
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
        sourceText: String,
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
                peekType == SharedTokenType.PAREN_OPEN ||
                    peekType == SharedTokenType.PLUS ||
                    peekType == SharedTokenType.MINUS ||
                    peekType == SharedTokenType.NUMBER_LITERAL -> {
                    parseNumberOrSpecialFloatDivision(optionalTypeItem, tokens, sourceText)
                }
                peekType.canBeIdentifier -> {
                    val firstIdent = tokens.consume()
                    val startOffset = firstIdent.startOffset
                    var endOffset = firstIdent.endOffset
                    var hasDots = false
                    while (tokens.peekType() == SharedTokenType.DOT) {
                        tokens.consume()
                        if (!tokens.peekType().canBeIdentifier) {
                            return null
                        }
                        endOffset = tokens.consume().endOffset
                        hasDots = true
                    }
                    if (tokens.peekType() != SharedTokenType.EOF) {
                        return null
                    }
                    val fullName =
                        if (hasDots) {
                            sourceText.substring(startOffset, endOffset)
                        } else {
                            firstIdent.text
                        }
                    knownNamedConstantValues[fullName]?.convertToType(optionalTypeItem)
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

    /**
     * Parses either a numeric literal (with optional leading `+`/`-` and optional `- 1` suffix) or
     * a special floating-point division-by-zero expression (`(0.0/0.0)`, `-1.0f / 0.0`, etc.).
     */
    private fun parseNumberOrSpecialFloatDivision(
        optionalTypeItem: TypeItem?,
        tokens: TokenStream,
        sourceText: String,
    ): ConstantValue? {
        if (tokens.peekType() == SharedTokenType.PAREN_OPEN) {
            tokens.consume() // consume '('
            val isNegative = tokens.match(SharedTokenType.MINUS)
            if (tokens.peekType() != SharedTokenType.NUMBER_LITERAL) return null
            val numerator = tokens.consume().text
            if (!tokens.match(SharedTokenType.SLASH)) return null
            if (tokens.peekType() != SharedTokenType.NUMBER_LITERAL) return null
            val denominator = tokens.consume().text
            if (!tokens.match(SharedTokenType.PAREN_CLOSE)) return null

            val specialFloat =
                when {
                    !isNegative && numerator == "0.0" && denominator == "0.0" -> DoubleValue.NaN
                    isNegative && numerator == "1.0" && denominator == "0.0" ->
                        DoubleValue.NEGATIVE_INFINITY
                    !isNegative && numerator == "1.0" && denominator == "0.0" ->
                        DoubleValue.POSITIVE_INFINITY
                    !isNegative && numerator == "0.0f" && denominator == "0.0f" -> FloatValue.NaN
                    isNegative && numerator == "1.0f" && denominator == "0.0f" ->
                        FloatValue.NEGATIVE_INFINITY
                    !isNegative && numerator == "1.0f" && denominator == "0.0f" ->
                        FloatValue.POSITIVE_INFINITY
                    else -> return null
                }
            return specialFloat.convertToType(optionalTypeItem)
        }

        val signToken =
            if (
                tokens.peekType() == SharedTokenType.PLUS ||
                    tokens.peekType() == SharedTokenType.MINUS
            ) {
                tokens.consume()
            } else {
                null
            }

        if (tokens.peekType() != SharedTokenType.NUMBER_LITERAL) return null
        val numToken = tokens.consume()

        // Check for unparenthesized special float division: `0.0 / 0.0`, `-1.0f / 0.0`, etc.
        if (tokens.peekType() == SharedTokenType.SLASH) {
            tokens.consume() // consume '/'
            if (tokens.peekType() != SharedTokenType.NUMBER_LITERAL) return null
            val denominator = tokens.consume().text
            if (denominator != "0.0") return null

            val isNegative = signToken?.type == SharedTokenType.MINUS
            val hasPlus = signToken?.type == SharedTokenType.PLUS
            if (hasPlus) return null

            val numerator = numToken.text
            val specialFloat =
                when {
                    !isNegative && numerator == "0.0" -> DoubleValue.NaN
                    isNegative && numerator == "1.0" -> DoubleValue.NEGATIVE_INFINITY
                    !isNegative && numerator == "1.0" -> DoubleValue.POSITIVE_INFINITY
                    !isNegative && numerator == "0.0f" -> FloatValue.NaN
                    isNegative && (numerator == "1.0f" || numerator == "1.0F") ->
                        FloatValue.NEGATIVE_INFINITY
                    !isNegative && numerator == "1.0f" -> FloatValue.POSITIVE_INFINITY
                    else -> return null
                }
            return specialFloat.convertToType(optionalTypeItem)
        }

        val numberText =
            if (signToken == null) {
                numToken.text
            } else if (signToken.endOffset == numToken.startOffset) {
                sourceText.substring(signToken.startOffset, numToken.endOffset)
            } else {
                signToken.text + numToken.text
            }

        // TODO(b/354633349): Temporary workaround that is needed because some historical files from
        //  `prebuilts/sdk` have expressions like `0x40000000 - 1`. Those files have been fixed
        //  downstream but the `prebuilts/sdk` repository is not modifiable in aosp/metalava-main.
        if (tokens.peekType() == SharedTokenType.MINUS) {
            tokens.consume() // consume '-'
            val subtrahend = tokens.consume()
            require(subtrahend.text == "1") {
                """Expected "... - 1" but found "... - ${subtrahend.text}""""
            }
            val patchedInt = Integer.decode(numberText) - 1
            return createLiteralValue(optionalTypeItem, patchedInt, nonLiteralInSource = false)
        }

        return parseNumber(optionalTypeItem, numberText)
    }

    /** Throw an exception when [text] cannot be parsed. */
    private fun unknownToken(optionalTypeItem: TypeItem?, text: String): Nothing =
        throw ValueProviderException("Unknown token <$text> of $optionalTypeItem")

    /**
     * Parse a number from [text].
     *
     * @param optionalTypeItem the optional [TypeItem], if present then the parsed value will be
     *   converted to be appropriate for this [TypeItem].
     * @param text the text to parse.
     */
    private fun parseNumber(
        optionalTypeItem: TypeItem?,
        text: String,
    ): ConstantValue {
        // Handle hexadecimal numbers first as they could end with a 'f' which would be treated as
        // a float below.
        if (text.startsWith("0x")) {
            // Check for a binary exponent as that means it is a hex floating point number.
            if (text.any { it == 'p' || it == 'P' }) {
                // Floating point hex value.
                val last = text.last()
                val number =
                    if (last == 'f') {
                        text.substring(0, text.length - 1).toFloat()
                    } else {
                        text.toDouble()
                    }
                return createLiteralValue(
                    optionalTypeItem,
                    number,
                    // Hexadecimal floating point numbers can only be present in the signature file
                    // if they were present in the source.
                    nonLiteralInSource = false,
                )
            }

            // Remove the leading "0x"
            val withoutLeading0x = text.substring(2)

            // Parse as long as a number like 0xFFFFFFFF is parsed as a positive number and will
            // fail because the largest positive int is 0x80000000. So, parse as long and then cast
            // down to an int. That is done explicitly here rather than rely on the casting done by
            // createLiteralValue(...) as it will fail because this cast will be lossy for numbers
            // larger than the largest positive int. They will become negative numbers. However,
            // that is what the original number was so it is ok.
            val int = withoutLeading0x.toLong(16).toInt()
            return createLiteralValue(
                optionalTypeItem,
                int,
                // AnnotationItem.toSource() will use format ints obtained from literals as decimals
                // and ints obtained from complex expressions as decimals so treat hexadecimals as
                // if they are not literals. That should allow signature files to be read and then
                // written out again without changing the formatting.
                nonLiteralInSource = true,
            )
        }

        // Check the last character to see if it indicated the type of the number.
        when (val suffix = text.last()) {
            'L',
            'l' -> {
                val long = text.substring(0, text.length - 1).toLong()
                return createLiteralValue(optionalTypeItem, long)
            }
            'F',
            'f' -> {
                val float = text.substring(0, text.length - 1).toFloat()
                // AnnotationItem.toSource() uses 'F' as the suffix for floats obtained from
                // expressions and 'f' for those obtained from literals.
                val nonLiteralInSource = suffix == 'F'
                return createLiteralValue(optionalTypeItem, float, nonLiteralInSource)
            }
        }

        // Try parsing as a long first. This will cover bytes, ints, longs, and shorts.
        text.toLongOrNull()?.let { long ->
            // Cast down to an int if allowed as an integer number without a trailing L or l is
            // treated as an integer in source.
            if (long in Int.MIN_VALUE..Int.MAX_VALUE) {
                return createLiteralValue(optionalTypeItem, long.toInt())
            } else {
                // Otherwise, rely on createLiteralValue(...) to do appropriate non-lossy casting to
                // match the optional type item.
                return createLiteralValue(optionalTypeItem, long)
            }
        }

        // Try parsing as a double. This will cover floats too.
        text.toDoubleOrNull()?.let { double ->
            if (
                optionalTypeItem is PrimitiveTypeItem &&
                    optionalTypeItem.kind == PrimitiveTypeItem.Primitive.FLOAT
            ) {
                return createLiteralValue(optionalTypeItem, double.toFloat())
            } else {
                return createLiteralValue(optionalTypeItem, double)
            }
        }

        throw ValueProviderException("Unsupported numeric value <$text> of $optionalTypeItem")
    }

    override fun parseAnnotationItem(text: String, unshorten: Boolean): AnnotationItem? =
        TODO("Annotation parsing from String is not yet supported by DefaultValueParser")

    companion object {
        /**
         * Map of named special floating-point constants (excluding division expressions, which are
         * parsed directly from tokens in [parseNumberOrSpecialFloatDivision]).
         */
        private val namedSpecialFloats =
            mapOf(
                DoubleValue.NaN to
                    listOf(
                        "Double.NaN",
                        "java.lang.Double.NaN",
                        "kotlin.jvm.internal.DoubleCompanionObject.NaN",
                    ),
                DoubleValue.NEGATIVE_INFINITY to
                    listOf(
                        "Double.NEGATIVE_INFINITY",
                        "java.lang.Double.NEGATIVE_INFINITY",
                        "kotlin.jvm.internal.DoubleCompanionObject.NEGATIVE_INFINITY",
                    ),
                DoubleValue.POSITIVE_INFINITY to
                    listOf(
                        "Double.POSITIVE_INFINITY",
                        "java.lang.Double.POSITIVE_INFINITY",
                        "kotlin.jvm.internal.DoubleCompanionObject.POSITIVE_INFINITY",
                    ),
                FloatValue.NaN to
                    listOf(
                        "Float.NaN",
                        "java.lang.Float.NaN",
                        "kotlin.jvm.internal.FloatCompanionObject.NaN",
                    ),
                FloatValue.NEGATIVE_INFINITY to
                    listOf(
                        "Float.NEGATIVE_INFINITY",
                        "java.lang.Float.NEGATIVE_INFINITY",
                        "kotlin.jvm.internal.FloatCompanionObject.NEGATIVE_INFINITY",
                    ),
                FloatValue.POSITIVE_INFINITY to
                    listOf(
                        "Float.POSITIVE_INFINITY",
                        "java.lang.Float.POSITIVE_INFINITY",
                        "kotlin.jvm.internal.FloatCompanionObject.POSITIVE_INFINITY",
                    ),
            )

        /** Map of all known named constant values (booleans and named special floats). */
        private val knownNamedConstantValues: Map<String, LiteralValue<*>> =
            mapOf(
                "false" to BooleanValue.FALSE,
                "true" to BooleanValue.TRUE,
            ) +
                namedSpecialFloats.flatMap { (value, alternatives) ->
                    alternatives.map { it to value }
                }
    }
}
