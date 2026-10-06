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

import com.android.tools.metalava.model.parser.SharedTokenType
import com.android.tools.metalava.model.parser.Token
import com.android.tools.metalava.model.parser.TokenType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SignatureFileLexerTest {

    /**
     * Tokenizes [text] using [SignatureFileLexer] and returns all emitted [Token]s, including the
     * trailing [SharedTokenType.EOF] token.
     */
    private fun tokenize(text: String): List<Token> {
        val stream = SignatureFileLexer(text).tokenize()
        val tokens = mutableListOf<Token>()
        while (true) {
            val token = stream.consume()
            tokens.add(token)
            if (token.type == SharedTokenType.EOF) break
        }
        return tokens
    }

    /**
     * Tokenizes [text] using [SignatureFileLexer] and asserts that the emitted tokens (excluding
     * [SharedTokenType.EOF]) match the [expected] `(type, text)` pairs in order.
     */
    private fun assertTokenTypes(text: String, vararg expected: Pair<TokenType, String>) {
        val tokens = tokenize(text).dropLast(1) // Drop EOF
        assertThat(tokens.map { it.type to it.text(text) })
            .containsExactlyElementsIn(expected)
            .inOrder()
    }

    @Test
    fun `Tokenize signature semicolon punctuation`() {
        assertTokenTypes(
            "package test.pkg { class Foo { } }",
            SharedTokenType.IDENTIFIER to "package",
            SharedTokenType.IDENTIFIER to "test",
            SharedTokenType.DOT to ".",
            SharedTokenType.IDENTIFIER to "pkg",
            SharedTokenType.BRACE_OPEN to "{",
            SharedTokenType.CLASS to "class",
            SharedTokenType.IDENTIFIER to "Foo",
            SharedTokenType.BRACE_OPEN to "{",
            SharedTokenType.BRACE_CLOSE to "}",
            SharedTokenType.BRACE_CLOSE to "}",
        )
        assertTokenTypes(
            "int;",
            SharedTokenType.IDENTIFIER to "int",
            SignatureTokenType.SEMICOLON to ";",
        )
    }
}
