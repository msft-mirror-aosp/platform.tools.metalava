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

/** Base interface for token types produced by a lexer. */
interface TokenType

/**
 * A lexical token produced by a lexer.
 *
 * @property type the [TokenType] representing the kind of token.
 * @property text the raw string content of this token.
 * @property startOffset 0-based start index of this token relative to the start of the parsed text
 *   range.
 * @property endOffset 0-based exclusive end index of this token relative to the start of the parsed
 *   text range.
 */
data class Token(
    val type: TokenType,
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
) {
    /**
     * Formats this token as a human-readable string, escaping `\r`, `\n`, and `\t` in [text], and
     * optionally appending the token's character offset range `([startOffset]..[endOffset])` when
     * [includePosition] is `true`.
     */
    fun format(includePosition: Boolean = true): String {
        val escaped = text.replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t")
        return if (includePosition) {
            "$type '$escaped' ($startOffset..$endOffset)"
        } else {
            "$type '$escaped'"
        }
    }

    override fun toString(): String = format()
}
