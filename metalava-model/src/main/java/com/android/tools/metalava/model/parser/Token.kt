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
 * Represents the kind of a [Token] produced by a lexer.
 *
 * @property name the name of the token type, used for debugging and formatting.
 * @property canBeIdentifier `true` if this token is an identifier or a keyword that can appear in
 *   an identifier position (such as a package, class, method, field, property, or parameter name).
 */
class TokenType(
    val name: String,
    val canBeIdentifier: Boolean = false,
) {
    override fun toString(): String = name
}

/**
 * A lexical token produced by a lexer.
 *
 * @property type the [TokenType] representing the kind of token.
 * @property startOffset 0-based start index of this token relative to the start of the parsed text
 *   range.
 * @property endOffset 0-based exclusive end index of this token relative to the start of the parsed
 *   text range.
 */
data class Token(
    val type: TokenType,
    val startOffset: Int,
    val endOffset: Int,
) {
    /** Returns the raw string content of this token sliced from [sourceText]. */
    fun text(sourceText: String): String = sourceText.substring(startOffset, endOffset)

    /**
     * Formats this token as a human-readable string, escaping `\r`, `\n`, and `\t` in the token
     * text sliced from [sourceText], and optionally appending the token's character offset range
     * `([startOffset]..[endOffset])` when [includePosition] is `true`.
     */
    fun format(sourceText: String, includePosition: Boolean = true): String {
        val escaped =
            text(sourceText).replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t")
        return if (includePosition) {
            "$type '$escaped' ($startOffset..$endOffset)"
        } else {
            "$type '$escaped'"
        }
    }

    override fun toString(): String = "$type ($startOffset..$endOffset)"
}
