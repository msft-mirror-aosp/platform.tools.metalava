/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.parser.LineMap
import com.android.tools.metalava.model.parser.SharedTokenType
import com.android.tools.metalava.model.parser.Token
import com.android.tools.metalava.model.parser.TokenStream
import com.android.tools.metalava.model.parser.TokenType
import com.android.tools.metalava.model.text.parser.SignatureFileLexer
import com.android.tools.metalava.model.text.parser.SignatureTokenType
import com.android.tools.metalava.model.value.Value
import com.android.tools.metalava.reporter.FileLocation
import java.nio.file.Path

/**
 * Extracts tokens from a sequence of characters.
 *
 * The tokens are not the usual sort of tokens created by a tokenizer, e.g. some tokens contain
 * white spaces and even whole strings. e.g. an annotation, including parameters if present, can be
 * returned as a single token, if requested (e.g. by calling [requireToken] with
 * `purpose=TokenPurpose.VALUE`).
 *
 * @param path the [Path] to the source being read.
 * @param buffer the [String] from which this will read tokens.
 * @param lineMap the [LineMap] for mapping character offsets in [buffer] to line numbers.
 */
class Tokenizer(
    private val path: Path,
    private val buffer: String,
    private val lineMap: LineMap = LineMap.create(buffer),
) : FileLocationTracker, TokenStream {

    /** The position of the next character to read in [buffer]. */
    private var position = 0

    private val tokenStream: TokenStream = SignatureFileLexer(buffer).tokenize()

    override fun fileLocation(): FileLocation {
        return lineMap.fileLocation(path, position)
    }

    override fun fileLocation(charOffset: Int): FileLocation {
        return lineMap.fileLocation(path, charOffset)
    }

    /** Get the [FileLocation] of the start of [token]. */
    fun fileLocation(token: Token): FileLocation {
        return fileLocation(token.startOffset)
    }

    private fun throwException(message: String): Nothing {
        throw ApiParseException(message, this)
    }

    /** Get the remainder. */
    fun remainder(): String = buffer.substring(position)

    /**
     * Get the next token, failing if the end of the file is reached.
     *
     * @param purpose determines which characters will be included in the token.
     * @return the token String found.
     */
    fun requireToken(purpose: TokenPurpose = TokenPurpose.GENERAL): String {
        val token = getToken(purpose)
        return token ?: throwException("Unexpected end of file")
    }

    /**
     * The current [position], used to record the start of a block of text that will be retrieved
     * later by [getStringFromOffset].
     */
    private fun offset(): Int {
        return position
    }

    /**
     * Get the contents of [buffer] from [offset] to [position].
     *
     * @param offset an offset previously returned by [offset].
     */
    private fun getStringFromOffset(offset: Int): String {
        return buffer.substring(offset, position)
    }

    /**
     * Scans balanced [openToken] and [closeToken] tokens starting from [current] (which must be
     * [openToken]) and returns the substring from the start of the opening [openToken] to the end
     * of the matching [closeToken].
     *
     * Does not advance past the matching [closeToken]; on return, [current] is the matching
     * [closeToken].
     */
    fun scanBalancedTokens(openToken: String, closeToken: String): String {
        require(current == openToken) { "Expected '$openToken' but found '$current'" }
        val start = offset() - openToken.length
        var balance = 1
        while (balance > 0) {
            val token = requireToken()
            if (token == openToken) {
                balance++
            } else if (token == closeToken) {
                balance--
            }
        }
        return getStringFromOffset(start)
    }

    /** The current token. */
    lateinit var current: String

    /**
     * Get the next token, returning null if the end of the file is reached.
     *
     * @param purpose determines which characters will be included in the token.
     * @return the token String found, or null.
     */
    fun getToken(purpose: TokenPurpose = TokenPurpose.GENERAL): String? {
        val firstToken = peek()
        if (firstToken.type == SharedTokenType.EOF) {
            return null
        }
        consume()
        val start = firstToken.startOffset
        // If the first token is a separator then that is the token.
        if (isSeparator(firstToken.type, purpose)) {
            // Nothing else to do, the separator is the token.
        } else {
            scanForEndOfToken(firstToken, purpose)
        }
        current = buffer.substring(start, position)
        return current
    }

    /**
     * Scan from [firstToken] (which has already been consumed) to the end of the token and return.
     *
     * When this returns [position] will point to the character after the end of the token.
     */
    private fun scanForEndOfToken(firstToken: Token, purpose: TokenPurpose) {
        handleTokenInFragment(firstToken, purpose)
        while (true) {
            val next = peek()
            if (next.type == SharedTokenType.EOF || next.startOffset != position) {
                break
            }
            if (next.type != SharedTokenType.ANGLE_OPEN && isSeparator(next.type, purpose)) {
                break
            }
            consume()
            handleTokenInFragment(next, purpose)
        }
    }

    /**
     * Scan from after [openToken] (which is the start of the token fragment) to the end of the
     * token fragment and return.
     *
     * A token fragment is a whole token or part of a token. e.g. while "1" is a whole token, given
     * a token of "Generic<AnotherGeneric<A>, B>" then "<AnotherGeneric<A>, B>" is a token fragment
     * of the whole token and "<A>" is a token fragment of that.
     *
     * A token fragment starts with [openChar] and ends with [closeType]. It is an error if the end
     * of the buffer is reached before matching the corresponding [closeType] token.
     */
    private fun scanForEndOfTokenFragment(
        openChar: Char,
        closeType: TokenType,
        openToken: Token,
    ) {
        val startLine = lineMap.lineNumber(openToken.startOffset)
        while (true) {
            val token = peek()
            if (token.type == SharedTokenType.EOF) {
                throwException("Unexpected end of file for $openChar starting at $startLine")
            }
            consume()
            if (token.type == closeType) {
                return
            }
            handleTokenInFragment(token, TokenPurpose.VALUE)
        }
    }

    /**
     * Handle a consumed [token] within a token or token fragment by validating string literals and
     * scanning balanced `<...>`, `(...)`, or `{...}` fragments when required by [purpose].
     */
    private fun handleTokenInFragment(token: Token, purpose: TokenPurpose) {
        when (token.type) {
            SharedTokenType.STRING_LITERAL -> scanForClosingQuotes(token)
            SharedTokenType.ANGLE_OPEN ->
                scanForEndOfTokenFragment('<', SharedTokenType.ANGLE_CLOSE, token)
            SharedTokenType.PAREN_OPEN -> {
                if (purpose == TokenPurpose.VALUE) {
                    scanForEndOfTokenFragment('(', SharedTokenType.PAREN_CLOSE, token)
                }
            }
            SharedTokenType.BRACE_OPEN -> {
                if (purpose == TokenPurpose.VALUE) {
                    scanForEndOfTokenFragment('{', SharedTokenType.BRACE_CLOSE, token)
                }
            }
        }
    }

    /** Scan from after the opening quotes of [token] until after the matching closing quotes. */
    private fun scanForClosingQuotes(token: Token) {
        val startLine = lineMap.lineNumber(token.startOffset)
        var pos = token.startOffset + 1
        val end = token.endOffset
        while (pos < end) {
            val k = buffer[pos]
            // Check for a newline before incrementing `pos` so that `fileLocation()` in
            // `throwException()` reports the line containing the newline rather than the next line.
            if (k == '\n' || k == '\r') {
                position = pos
                throwException("Unexpected newline for \" starting at $startLine")
            }
            pos++

            if (k == '"') {
                return
            } else if (k == '\\') {
                // Skip the escaped character. This only really matters if the character is a quote
                // as without skipping it would be treated as the closing quote.
                pos++
            }
        }
        throwException("Unexpected end of file for \" starting at $startLine")
    }

    fun assertIdent(token: String) {
        if (!isIdent(token[0])) {
            throwException("Expected identifier: $token")
        }
    }

    /** Returns the next token to be consumed without consuming it. */
    override fun peek(): Token = tokenStream.peek()

    /** Consumes and returns the next token from the stream. */
    override fun consume(): Token =
        tokenStream.consume().also { token ->
            if (token.type != SharedTokenType.EOF) {
                position = token.endOffset
            }
        }

    companion object {
        /**
         * Returns `true` if a token of [type] is a separator for the given [purpose].
         *
         * A separator token forms its own token when encountered at the start of [getToken] and
         * terminates a preceding token in [scanForEndOfToken] (except for
         * [SharedTokenType.ANGLE_OPEN], which starts a balanced `<...>` type argument fragment).
         */
        private fun isSeparator(type: TokenType, purpose: TokenPurpose): Boolean {
            if (purpose == TokenPurpose.GENERAL) {
                // This only affects whether an open parenthesis is treated as a separator. A close
                // parenthesis is always treated as a separator because:
                // 1. If an open parenthesis is a separator then so should a close parenthesis.
                // 2. If an open parenthesis is not a separator then its matching close parenthesis
                //    will be included in the token irrespective of whether it is a separator or
                //    not.
                // 3. An unbalanced close parenthesis, e.g. in `attr=1)`, should be treated as a
                //    separator so it is not included in the preceding token, e.g. the above should
                //    tokenize as `attr`, `=`, `1`, `)`  and NOT `attr`, `=`, `1)`.
                // Ditto for open and close braces.
                if (type == SharedTokenType.PAREN_OPEN || type == SharedTokenType.BRACE_OPEN) {
                    return true
                }
            }
            return type == SharedTokenType.PAREN_CLOSE ||
                type == SharedTokenType.BRACE_CLOSE ||
                type == SharedTokenType.COMMA ||
                type == SignatureTokenType.SEMICOLON ||
                type == SharedTokenType.ANGLE_OPEN ||
                type == SharedTokenType.ANGLE_CLOSE ||
                type == SharedTokenType.EQUALS
        }

        /**
         * Returns `true` if character [c] is a separator character for the given [purpose].
         *
         * Used by [isIdent] to check whether the first character of a token string is a separator.
         */
        private fun isSeparator(c: Char, purpose: TokenPurpose): Boolean {
            if (purpose == TokenPurpose.GENERAL) {
                if (c == '(' || c == '{') {
                    return true
                }
            }
            return c == ')' || c == '}' || c == ',' || c == ';' || c == '<' || c == '>' || c == '='
        }

        private fun isIdent(c: Char): Boolean {
            return c != '"' && !isSeparator(c, TokenPurpose.GENERAL)
        }

        fun isIdent(token: String): Boolean {
            return isIdent(token[0])
        }
    }
}

/** The purpose for which a token will be used. */
enum class TokenPurpose {
    /**
     * General purpose, e.g. for parsing signature files.
     *
     * This will generally return unbalanced tokens, e.g. `{` and `}` will be returned separately.
     * The sole exception is `<` and `>` which will be balanced for use in [TypeItem]s.
     */
    GENERAL,

    /**
     * The token will represent a [Value].
     *
     * This will balance out delimiters like `(` and `)`.
     */
    VALUE,
}

/**
 * Interface implemented by [Tokenizer] which keeps track of the [FileLocation] for the current
 * token.
 *
 * This is provided to avoid passing [Tokenizer] to code that might need access to the current
 * [FileLocation] but does not consume tokens. That makes that code and the [Tokenizer] state easier
 * to reason about.
 */
interface FileLocationTracker {
    /** Get the current [FileLocation]. */
    fun fileLocation(): FileLocation

    /** Get the [FileLocation] at the 0-based [charOffset]. */
    fun fileLocation(charOffset: Int): FileLocation
}
