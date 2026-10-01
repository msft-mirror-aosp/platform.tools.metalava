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

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Test

class SharedLexerTest {
    private fun checkTokenize(
        text: String,
        expectedTokens: String,
        startInclusive: Int = 0,
        endExclusive: Int = text.length,
        includePosition: Boolean = false,
    ) {
        val lexer = SharedLexer(text, startInclusive, endExclusive)
        val tokenStream = lexer.tokenize()
        val tokens = buildList {
            do {
                val token = tokenStream.consume()
                add(token)
            } while (token.type != SharedTokenType.EOF)
        }
        val actual = tokens.joinToString("\n") { it.format(includePosition) }
        assertEquals(expectedTokens.trimIndent(), actual)
    }

    @Test
    fun `Test identifiers and unknown tokens`() {
        checkTokenize(
            "foo bar_1 $ #",
            expectedTokens =
                """
                    IDENTIFIER 'foo'
                    IDENTIFIER 'bar_1'
                    IDENTIFIER '$'
                    UNKNOWN '#'
                    EOF ''
                """,
        )
        assertTrue(SharedTokenType.IDENTIFIER.canBeIdentifier)
        assertFalse(SharedTokenType.UNKNOWN.canBeIdentifier)
        assertFalse(SharedTokenType.EOF.canBeIdentifier)
    }

    @Test
    fun `Test string and char literals`() {
        checkTokenize(
            """"hi\"@" 'x' '\''""",
            expectedTokens =
                """
                    STRING_LITERAL '"hi\"@"'
                    CHAR_LITERAL ''x''
                    CHAR_LITERAL ''\'''
                    EOF ''
                """,
        )
    }

    @Test
    fun `Test whitespace and line comments`() {
        checkTokenize(
            """
                // Leading comment
                foo // inline comment
                    bar
            """,
            expectedTokens =
                """
                    IDENTIFIER 'foo'
                    IDENTIFIER 'bar'
                    EOF ''
                """,
        )
    }

    @Test
    fun `Test subrange tokenization and positions`() {
        val text = "prefix foo bar suffix"
        val start = text.indexOf("foo")
        val end = start + "foo bar".length
        checkTokenize(
            text,
            startInclusive = start,
            endExclusive = end,
            includePosition = true,
            expectedTokens =
                """
                    IDENTIFIER 'foo' (0..3)
                    IDENTIFIER 'bar' (4..7)
                    EOF '' (7..7)
                """,
        )
    }

    @Test
    fun `Test EOF token is cached on subsequent nextToken calls`() {
        val lexer = SharedLexer("int")
        assertEquals(SharedTokenType.IDENTIFIER, lexer.nextToken().type)
        val eof1 = lexer.nextToken()
        val eof2 = lexer.nextToken()
        assertEquals(SharedTokenType.EOF, eof1.type)
        assertSame(eof1, eof2)
    }
}
