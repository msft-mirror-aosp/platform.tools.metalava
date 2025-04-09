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

package com.android.tools.metalava.model.parser

import com.android.tools.metalava.reporter.FileLocation
import java.nio.file.Path

/**
 * Extracts tokens from a sequence of characters.
 *
 * The tokens are not the usual sort of tokens created by a tokenizer, e.g. some tokens contain
 * white spaces and even whole strings. e.g. an annotation, including parameters if present, can be
 * returned as a single token, if requested (e.g. by calling [requireToken] with
 * `parenIsSep=false`).
 *
 * @param path the [Path] to the source being read.
 * @param buffer the [CharArray] from which this will read tokens.
 * @param exceptionCreator factory method for creating exceptions that will be thrown.
 */
class Tokenizer(
    private val path: Path,
    private val buffer: CharArray,
    private val exceptionCreator: (String, FileLocation) -> ParseException = ::ParseException,
) : FileLocationTracker {

    /** The position of the next character to read in [buffer]. */
    private var position = 0

    /** The current line being read. */
    private var line = 1

    override fun fileLocation(): FileLocation {
        return FileLocation.createLocation(path, line)
    }

    private fun throwException(message: String): Nothing {
        throw exceptionCreator(message, fileLocation())
    }

    /**
     * Eat whitespace, including newline characters.
     *
     * Scans through the [buffer] from the current [position], stopping at the first non-whitespace
     * character, updating [position] and [line] as needed.
     *
     * @return `true` if any whitespace characters were eaten, `false` otherwise.
     */
    private fun eatWhitespace(): Boolean {
        var ate = false
        while (position < buffer.size && isSpace(buffer[position])) {
            if (buffer[position] == '\n') {
                line++
            }
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
        if (position + 1 < buffer.size) {
            if (buffer[position] == '/' && buffer[position + 1] == '/') {
                position += 2
                while (position < buffer.size && !isNewline(buffer[position])) {
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
     * @param parenIsSep If `true` then treat `(` and `)` as separators, otherwise do not.
     * @return the token String found.
     */
    fun requireToken(parenIsSep: Boolean = true): String {
        val token = getToken(parenIsSep)
        return token ?: throwException("Unexpected end of file")
    }

    /**
     * The current [position], used to record the start of a block of text that will be retrieved
     * later by [getStringFromOffset].
     */
    fun offset(): Int {
        return position
    }

    /**
     * Get the contents of [buffer] from [offset] to [position].
     *
     * @param offset an offset previously returned by [offset].
     */
    fun getStringFromOffset(offset: Int): String {
        return String(buffer, offset, position - offset)
    }

    /** The current token. */
    lateinit var current: String

    /**
     * Get the next token, returning null if the end of the file is reached.
     *
     * @param parenIsSep If `true` then treat `(` and `)` as separators, otherwise do not.
     * @return the token String found, or null.
     */
    fun getToken(parenIsSep: Boolean = true): String? {
        // Eat any white space or comments that come before the token.
        eatWhitespaceAndComments()

        if (position >= buffer.size) {
            return null
        }
        val start = position
        scanForEndOfToken(parenIsSep)
        current = String(buffer, start, position - start)
        return current
    }

    /**
     * Scan from [position] (which is the start of the token) to the end of the token and return.
     *
     * When this returns [position] will point to the character after the end of the token.
     */
    private fun scanForEndOfToken(parenIsSep: Boolean) {
        val line = line

        // If the first character is a separator then that is the token.
        if (isSeparator(buffer[position], parenIsSep)) {
            // Nothing else to do, the separator is the token.
            position++
            return
        }

        // A count of the number of tokens that have been started but not finished, e.g. strings
        // that have not yet seen the closing double quotes, , etc.
        var incompleteDepth = 0
        while (position < buffer.size) {
            // Get the next character and assume that it is part of the token by incrementing the
            // position.
            val c = buffer[position]
            position++

            if (c == '"') {
                scanForClosingQuotes()
            } else if (c == '<') {
                // Open a type parameter/argument list. Make sure to continue to the next `>`.
                incompleteDepth++
            } else if (incompleteDepth != 0 && c == '>') {
                // If this closes a previously opened type parameter/argument list then close
                // it.
                incompleteDepth--
            } else if (incompleteDepth == 0 && (isSpace(c) || isSeparator(c, parenIsSep))) {
                // If there are no incomplete tokens then a space or separator ends the token but
                // is not part of it. Remove it from the token by decrementing the position and then
                // return.
                position--
                return
            }
        }

        // If reached the end of the buffer but the token is incomplete then throw an error.
        if (incompleteDepth != 0) {
            throwException("Unexpected end of file for < starting at $line")
        }
    }

    /**
     * Scan from [position] (which should be immediately after the opening quotes) until after the
     * matching closing quotes.
     */
    private fun scanForClosingQuotes() {
        while (position < buffer.size) {
            val k = buffer[position]
            position++
            if (k == '\n' || k == '\r') {
                throwException("Unexpected newline for \" starting at $line")
            }

            if (k == '"') {
                return
            } else if (k == '\\') {
                // Skip the escaped character. This only really matters if the character is a quote
                // as without skipping it would be treated as the closing quote.
                position++
            }
        }
        throwException("Unexpected end of file for \" starting at $line")
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

        private fun isSeparator(c: Char, parenIsSep: Boolean): Boolean {
            if (parenIsSep) {
                if (c == '(' || c == ')') {
                    return true
                }
            }
            return c == '{' || c == '}' || c == ',' || c == ';' || c == '<' || c == '>' || c == '='
        }

        private fun isIdent(c: Char): Boolean {
            return c != '"' && !isSeparator(c, true)
        }

        fun isIdent(token: String): Boolean {
            return isIdent(token[0])
        }
    }
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
