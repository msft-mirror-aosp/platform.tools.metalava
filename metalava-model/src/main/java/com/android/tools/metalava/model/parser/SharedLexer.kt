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

package com.android.tools.metalava.model.parser

/**
 * Shared token types required for parsing types, type parameter lists, values, and annotations.
 *
 * Emitted by [SharedLexer] (and `SignatureFileLexer`).
 */
object SharedTokenType {
    // Identifiers & Literals
    val IDENTIFIER = TokenType("IDENTIFIER", canBeIdentifier = true)
    val NUMBER_LITERAL = TokenType("NUMBER_LITERAL")
    val STRING_LITERAL = TokenType("STRING_LITERAL")
    val CHAR_LITERAL = TokenType("CHAR_LITERAL")

    // Delimiters / Punctuation
    val DOT = TokenType("DOT")
    val PAREN_OPEN = TokenType("PAREN_OPEN")
    val PAREN_CLOSE = TokenType("PAREN_CLOSE")
    val PLUS = TokenType("PLUS")
    val MINUS = TokenType("MINUS")
    val SLASH = TokenType("SLASH")

    // Fallback for unrecognized characters
    val UNKNOWN = TokenType("UNKNOWN")

    // End of input
    val EOF = TokenType("EOF")
}

/**
 * Shared lexer that tokenizes type strings, type parameter lists, values, and annotations into a
 * lazy stream of [Token]s of [SharedTokenType].
 *
 * Subclasses (such as `SignatureFileLexer`) may override [tryMatchAdditionalToken] and
 * [resolveKeywordOrIdentifier] to recognize additional token types and keywords.
 *
 * @param text the full string containing the text to tokenize.
 * @param startInclusive the index in [text] where tokenization should begin.
 * @param endExclusive the index in [text] where tokenization should end.
 */
open class SharedLexer(
    text: String,
    startInclusive: Int = 0,
    endExclusive: Int = text.length,
) :
    AbstractLexer(
        text,
        startInclusive,
        endExclusive,
    ) {
    /** Cached [SharedTokenType.EOF] token returned once the end of input is reached. */
    private var eofToken: Token? = null

    override fun nextToken(): Token {
        skipWhitespaceAndComments()

        if (index >= endExclusive) {
            eofToken?.let {
                return it
            }
            return createToken(
                    SharedTokenType.EOF,
                    "",
                    endExclusive,
                    endExclusive,
                )
                .also { eofToken = it }
        }

        tryMatchAdditionalToken()?.let {
            return it
        }

        val start = index
        return when (val c = text[start]) {
            '.' -> {
                index = start + 1
                createToken(SharedTokenType.DOT, ".", start, index)
            }
            '(' -> {
                index = start + 1
                createToken(SharedTokenType.PAREN_OPEN, "(", start, index)
            }
            ')' -> {
                index = start + 1
                createToken(SharedTokenType.PAREN_CLOSE, ")", start, index)
            }
            '+' -> {
                index = start + 1
                createToken(SharedTokenType.PLUS, "+", start, index)
            }
            '-' -> {
                index = start + 1
                createToken(SharedTokenType.MINUS, "-", start, index)
            }
            '/' -> {
                index = start + 1
                createToken(SharedTokenType.SLASH, "/", start, index)
            }
            '"' -> scanStringLiteral(start)
            '\'' -> scanCharLiteral(start)
            in '0'..'9' -> scanNumberLiteral(start)
            else -> {
                if (Character.isJavaIdentifierStart(c)) {
                    scanIdentifierOrKeyword(start)
                } else {
                    index = start + 1
                    createToken(
                        SharedTokenType.UNKNOWN,
                        text.substring(start, index),
                        start,
                        index,
                    )
                }
            }
        }
    }

    /** Skips whitespace characters and `//` line comments. */
    private fun skipWhitespaceAndComments() {
        while (index < endExclusive) {
            val c = text[index]
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                index++
            } else if (c == '/' && index + 1 < endExclusive && text[index + 1] == '/') {
                index += 2
                while (index < endExclusive) {
                    val ch = text[index]
                    if (ch == '\n' || ch == '\r') break
                    index++
                }
            } else {
                break
            }
        }
    }

    /**
     * Hook for subclasses to match additional punctuation or prefix tokens at the current [index]
     * before [SharedLexer] attempts its standard matching.
     */
    protected open fun tryMatchAdditionalToken(): Token? = null

    /**
     * Maps [tokenText] to a keyword [TokenType] or [SharedTokenType.IDENTIFIER].
     *
     * Subclasses may override this to recognize additional keywords.
     */
    protected open fun resolveKeywordOrIdentifier(tokenText: String): TokenType =
        SharedTokenType.IDENTIFIER

    /** Scans a double-quoted string literal starting at [start]. */
    private fun scanStringLiteral(start: Int): Token {
        index = start + 1
        while (index < endExclusive) {
            val c = text[index++]
            if (c == '"') {
                break
            } else if (c == '\\' && index < endExclusive) {
                index++
            }
        }
        return createToken(
            SharedTokenType.STRING_LITERAL,
            text.substring(start, index),
            start,
            index,
        )
    }

    /** Scans a single-quoted character literal starting at [start]. */
    private fun scanCharLiteral(start: Int): Token {
        index = start + 1
        while (index < endExclusive) {
            val c = text[index++]
            if (c == '\'') {
                break
            } else if (c == '\\' && index < endExclusive) {
                index++
            }
        }
        return createToken(
            SharedTokenType.CHAR_LITERAL,
            text.substring(start, index),
            start,
            index,
        )
    }

    /** Scans a numeric literal (decimal, hex, integer, or floating-point) starting at [start]. */
    private fun scanNumberLiteral(start: Int): Token {
        index = start + 1
        val isHex =
            index < endExclusive && text[start] == '0' && (text[index] == 'x' || text[index] == 'X')
        while (index < endExclusive) {
            val c = text[index]
            if (c in '0'..'9' || c in 'a'..'z' || c in 'A'..'Z' || c == '_') {
                index++
                val isExponent = if (isHex) (c == 'p' || c == 'P') else (c == 'e' || c == 'E')
                if (
                    isExponent && index < endExclusive && (text[index] == '+' || text[index] == '-')
                ) {
                    index++
                }
            } else if (
                c == '.' &&
                    index + 1 < endExclusive &&
                    (text[index + 1] in '0'..'9' ||
                        (isHex && (text[index + 1] in 'a'..'f' || text[index + 1] in 'A'..'F')))
            ) {
                index += 2
            } else {
                break
            }
        }
        return createToken(
            SharedTokenType.NUMBER_LITERAL,
            text.substring(start, index),
            start,
            index,
        )
    }

    /** Scans a Java/Kotlin identifier or keyword starting at [start]. */
    protected open fun scanIdentifierOrKeyword(start: Int): Token {
        index = start + 1
        while (index < endExclusive && Character.isJavaIdentifierPart(text[index])) {
            index++
        }
        val tokenText = text.substring(start, index)
        val type = resolveKeywordOrIdentifier(tokenText)
        return createToken(
            type,
            tokenText,
            start,
            index,
        )
    }
}
