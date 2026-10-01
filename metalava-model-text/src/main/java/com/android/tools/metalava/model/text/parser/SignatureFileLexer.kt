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
     * Matches signature-specific punctuation (`;`) at [index] before [SharedLexer] attempts its
     * standard token matching.
     */
    override fun tryMatchAdditionalToken(): Token? {
        val start = index
        return when (text[start]) {
            ';' -> {
                index = start + 1
                createToken(SignatureTokenType.SEMICOLON, ";", start, index)
            }
            else -> null
        }
    }
}
