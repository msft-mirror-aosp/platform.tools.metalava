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

package com.android.tools.metalava.model.text.parser

import com.android.tools.metalava.model.TargetLanguageSet
import com.android.tools.metalava.model.parser.SharedLexer
import com.android.tools.metalava.model.parser.SharedTokenType
import com.android.tools.metalava.model.parser.Token
import com.android.tools.metalava.model.parser.TokenType

/**
 * Token types specific to API signature files, emitted by [SignatureFileLexer] alongside
 * [SharedTokenType] tokens.
 */
internal object SignatureTokenType {
    // Punctuation exclusive to signature file structure
    val SEMICOLON = TokenType("SEMICOLON")

    // Target language markers (e.g. @KotlinOnly, @BytecodeOnly, etc.)
    val TARGET_LANGUAGE = TokenType("TARGET_LANGUAGE")

    // Top-level & Class Kind Keywords (note: CLASS and EXTENDS are in SharedTokenType)
    val ANNOTATION_INTERFACE = TokenType("ANNOTATION_INTERFACE")

    // Parameter Modifier Keywords
    val OPTIONAL = TokenType("OPTIONAL", canBeIdentifier = true)
    val CONTEXT = TokenType("CONTEXT", canBeIdentifier = true)
    val RECEIVER = TokenType("RECEIVER", canBeIdentifier = true)
}

/**
 * Lexer that tokenizes API signature files into a lazy stream of [Token]s of [SharedTokenType] and
 * [SignatureTokenType].
 *
 * @param text the full string containing the signature file text to tokenize.
 * @param startInclusive the index in [text] where tokenization should begin.
 * @param endExclusive the index in [text] where tokenization should end.
 */
internal class SignatureFileLexer(
    text: String,
    startInclusive: Int = 0,
    endExclusive: Int = text.length,
) : SharedLexer(text, startInclusive, endExclusive) {

    /**
     * Matches signature-specific punctuation (`;`), `@interface`, and target language prefixes
     * (e.g. `@KotlinOnly`) at [index] before [SharedLexer] attempts its standard token matching.
     */
    override fun tryMatchAdditionalToken(): Token {
        val start = index
        return when (text[start]) {
            ';' -> {
                index = start + 1
                createToken(SignatureTokenType.SEMICOLON, start, index)
            }
            '@' -> {
                // If '@' is followed by an identifier start character, scan the full
                // '@<identifier>' candidate to check for '@interface' or a target language prefix
                // (e.g. '@KotlinOnly').
                if (start + 1 < endExclusive && Character.isJavaIdentifierStart(text[start + 1])) {
                    var end = start + 2
                    while (end < endExclusive && Character.isJavaIdentifierPart(text[end])) {
                        end++
                    }
                    // Only treat `@interface` or target language markers (e.g. `@KotlinOnly`) as
                    // special signature tokens when not followed by `.` (a qualified annotation
                    // name) or `(` (an annotation argument list). Otherwise, return Token.NONE so
                    // SharedLexer emits AT ('@') for normal annotation parsing.
                    if (end >= endExclusive || (text[end] != '.' && text[end] != '(')) {
                        val length = end - start
                        if (matchSlice(start, length, "@interface")) {
                            index = end
                            return createToken(
                                SignatureTokenType.ANNOTATION_INTERFACE,
                                start,
                                end,
                            )
                        }
                        if (
                            TargetLanguageSet.signatureFileRepresentationToTargetLanguageSet.keys
                                .any { matchSlice(start, length, it) }
                        ) {
                            index = end
                            return createToken(
                                SignatureTokenType.TARGET_LANGUAGE,
                                start,
                                end,
                            )
                        }
                    }
                }
                Token.NONE
            }
            else -> Token.NONE
        }
    }

    /**
     * Scans an identifier or keyword starting at [start], only resolving signature-specific
     * keywords when followed by whitespace or end-of-input.
     */
    override fun scanIdentifierOrKeyword(start: Int): Token {
        index = start + 1
        while (index < endExclusive && Character.isJavaIdentifierPart(text[index])) {
            index++
        }
        // Signature-specific keywords (such as 'method', 'public', 'optional', or 'value') are
        // always followed by whitespace in signature files, whereas identifiers with the same name
        // (such as method names, parameter names, or package segments) may be followed directly by
        // punctuation like '(', ':', ',', '.', or ';'. Only resolve signature keywords when
        // followed by whitespace or end-of-input; otherwise fall back to
        // SharedLexer.resolveKeywordOrIdentifier (which only recognizes shared type/value keywords
        // like 'class', 'extends', and 'super').
        val type =
            if (index >= endExclusive || isWhitespace(text[index])) {
                resolveKeywordOrIdentifier(start, index)
            } else {
                super.resolveKeywordOrIdentifier(start, index)
            }
        return createToken(
            type,
            start,
            index,
        )
    }

    /** Returns `true` if [c] is a whitespace character. */
    private fun isWhitespace(c: Char): Boolean = c == ' ' || c == '\t' || c == '\n' || c == '\r'

    /**
     * Maps the slice of [text] from [start] to [end] to a [SignatureTokenType] keyword, or
     * delegates to [SharedLexer.resolveKeywordOrIdentifier] for shared keywords and
     * [SharedTokenType.IDENTIFIER].
     */
    override fun resolveKeywordOrIdentifier(start: Int, end: Int): TokenType {
        val length = end - start
        return when {
            matchSlice(start, length, "optional") -> SignatureTokenType.OPTIONAL
            matchSlice(start, length, "context") -> SignatureTokenType.CONTEXT
            matchSlice(start, length, "receiver") -> SignatureTokenType.RECEIVER
            else -> super.resolveKeywordOrIdentifier(start, end)
        }
    }
}
