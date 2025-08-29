/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.tools.metalava.model.source.javadoc

import com.android.tools.metalava.model.source.doc.CollatingDocumentationIssueReporter
import com.android.tools.metalava.model.source.doc.DefaultDocDescription
import com.android.tools.metalava.model.source.doc.DocComment
import kotlin.test.assertEquals
import org.junit.Test

class JavadocParserTest {
    /** Check that [text] is parsed correctly by [JavadocParser]. */
    private fun checkParse(text: String) {
        val reporter = CollatingDocumentationIssueReporter()
        val docComment = DocComment.createDocComment(text.trimIndent(), reporter)
        // Make sure that no unexpected DocComment errors were found.
        assertEquals("", reporter.toString().trim(), message = "doc comment parser errors")

        // Parse the main description
        val description = docComment.description as DefaultDocDescription
        description.content
    }

    @Test
    fun `Test simple comment`() {
        checkParse(
            "/** Simple text */",
        )
    }

    @Test
    fun `Test simple comment - leading newline`() {
        checkParse(
            "\n/** Simple text */",
        )
    }

    @Test
    fun `Test simple comment - trailing newline`() {
        checkParse(
            "/** Simple text */\n",
        )
    }

    @Test
    fun `Test comment with nested javadoc start`() {
        checkParse(
            "/** /** */\n",
        )
    }

    @Test
    fun `Test link - standalone`() {
        checkParse(
            """
                /**
                 * {@link Class}
                 */
            """,
        )
    }

    @Test
    fun `Test link - in text`() {
        checkParse(
            """
                /**
                 * Text before link {@link Class} and some text after.
                 */
            """,
        )
    }

    @Test
    fun `Test link - on new line`() {
        checkParse(
            """
                /**
                 * Text before link
                 * {@link Class}
                 * and some text after.
                 */
            """,
        )
    }

    @Test
    fun `Test @ inside inline tag`() {
        checkParse(
            """
                /**
                 * {@code @Annotation}
                 */
            """,
        )
    }

    @Test
    fun `Test nested inline tags`() {
        checkParse(
            """
                /**
                 * {@code some {@code nested} inline tags}
                 */
            """,
        )
    }

    @Test
    fun `Test unclosed inline tags`() {
        checkParse(
            """
                /**
                 * {@code not closed
                 */
            """,
        )
    }

    @Test
    fun `Test space between @ and inline tag name`() {
        checkParse(
            """
                /**
                 * {@ code extra space}
                 */
            """,
        )
    }

    @Test
    fun `Test empty inline tag`() {
        checkParse(
            """
                /** {@inheritDoc} */
            """,
        )
    }
}
