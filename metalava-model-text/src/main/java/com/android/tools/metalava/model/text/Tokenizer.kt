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
) : FileLocationTracker {

    /** The position of the next character to read in [buffer]. */
    private var position = 0

    override fun fileLocation(): FileLocation {
        return lineMap.fileLocation(path, position)
    }

    private fun throwException(message: String): Nothing {
        throw ApiParseException(message, this)
    }

    /** Get the remainder. */
    fun remainder(): String = buffer.substring(position)

    /**
     * Eat whitespace, including newline characters.
     *
     * Scans through the [buffer] from the current [position], stopping at the first non-whitespace
     * character, updating [position] as needed.
     *
     * @return `true` if any whitespace characters were eaten, `false` otherwise.
     */
    private fun eatWhitespace(): Boolean {
        var ate = false
        while (position < buffer.length && isSpace(buffer[position])) {
            position++
            ate = true
        }
        return ate
    }

    /**
     * Eat a line comment, if any, starting at the current [position] and ending at the end of the
     * line but not moving onto the next line.
     *
     * If [position] does not point to a `/` immediately followed by another `/` then this does
     * nothing.
     *
     * @return `true` if a line comment was found, `false` otherwise.
     */
    private fun eatComment(): Boolean {
        if (position + 1 < buffer.length) {
            if (buffer[position] == '/' && buffer[position + 1] == '/') {
                position += 2
                while (position < buffer.length && !isNewline(buffer[position])) {
                    position++
                }
                return true
            }
        }
        return false
    }

    /** Eat whitespace and line comments until a non-whitespace, non-line comment is found. */
    private fun eatWhitespaceAndComments() {
        while (eatWhitespace() || eatComment()) {
            // intentionally consume whitespace and comments
        }
    }

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
        // Eat any white space or comments that come before the token.
        eatWhitespaceAndComments()

        if (position >= buffer.length) {
            return null
        }
        val start = position
        val firstChar = buffer[position]
        position++
        // If the first character is a separator then that is the token.
        if (isSeparator(firstChar, purpose)) {
            // Nothing else to do, the separator is the token.
        } else {
            scanForEndOfToken(firstChar, purpose)
        }
        current = buffer.substring(start, position)
        return current
    }

    /**
     * Scan from [firstChar] (which has already been consumed) to the end of the token and return.
     *
     * When this returns [position] will point to the character after the end of the token.
     */
    private fun scanForEndOfToken(firstChar: Char, purpose: TokenPurpose) {
        handleCharInFragment(firstChar, purpose)
        while (position < buffer.length) {
            val c = buffer[position]
            if (isSpace(c) || (c != '<' && isSeparator(c, purpose))) {
                break
            }
            position++
            handleCharInFragment(c, purpose)
        }
    }

    /**
     * Scan from [position] (which is the start of the token fragment) to the end of the token
     * fragment and return.
     *
     * A token fragment is a whole token or part of a token. e.g. while "1" is a whole token, given
     * a token of "Generic<AnotherGeneric<A>, B>" then "<AnotherGeneric<A>, B>" is a token fragment
     * of the whole token and "<A>" is a token fragment of that.
     *
     * A token fragment starts with [openChar] and ends with [closeChar]. It is an error if the end
     * of the buffer is reached before matching the corresponding [closeChar] character.
     */
    private fun scanForEndOfTokenFragment(openChar: Char, closeChar: Char) {
        // `position` has already been incremented past `openChar`, so `position - 1` is the index
        // of `openChar`.
        val startLine = lineMap.lineNumber(position - 1)
        while (true) {
            if (position >= buffer.length) {
                throwException("Unexpected end of file for $openChar starting at $startLine")
            }
            val c = buffer[position]
            position++
            if (c == closeChar) {
                return
            }
            handleCharInFragment(c, TokenPurpose.VALUE)
        }
    }

    /**
     * Handle a consumed character [c] within a token or token fragment by scanning string literals
     * and balanced `<...>`, `(...)`, or `{...}` fragments when required by [purpose].
     */
    private fun handleCharInFragment(c: Char, purpose: TokenPurpose) {
        when (c) {
            '"' -> scanForClosingQuotes()
            '<' -> scanForEndOfTokenFragment('<', '>')
            '(' -> {
                if (purpose == TokenPurpose.VALUE) {
                    scanForEndOfTokenFragment('(', ')')
                }
            }
            '{' -> {
                if (purpose == TokenPurpose.VALUE) {
                    scanForEndOfTokenFragment('{', '}')
                }
            }
        }
    }

    /**
     * Scan from [position] (which should be immediately after the opening quotes) until after the
     * matching closing quotes.
     */
    private fun scanForClosingQuotes() {
        // `position` has already been incremented past the opening quote, so `position - 1` is the
        // index of the opening quote.
        val startPosition = position - 1
        while (position < buffer.length) {
            val k = buffer[position]
            // Check for a newline before incrementing `position` so that `fileLocation()` in
            // `throwException()` reports the line containing the newline rather than the next line.
            if (k == '\n' || k == '\r') {
                val startLine = lineMap.lineNumber(startPosition)
                throwException("Unexpected newline for \" starting at $startLine")
            }
            position++

            if (k == '"') {
                return
            } else if (k == '\\' && position < buffer.length) {
                // Skip the escaped character. This only really matters if the character is a quote
                // as without skipping it would be treated as the closing quote.
                position++
            }
        }
        val startLine = lineMap.lineNumber(startPosition)
        throwException("Unexpected end of file for \" starting at $startLine")
    }

    fun assertIdent(token: String) {
        if (!isIdent(token[0])) {
            throwException("Expected identifier: $token")
        }
    }

    companion object {
        private fun isSpace(c: Char): Boolean {
            return c == ' ' || c == '\t' || c == '\n' || c == '\r'
        }

        private fun isNewline(c: Char): Boolean {
            return c == '\n' || c == '\r'
        }

        private fun isSeparator(c: Char, purpose: TokenPurpose): Boolean {
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
}
