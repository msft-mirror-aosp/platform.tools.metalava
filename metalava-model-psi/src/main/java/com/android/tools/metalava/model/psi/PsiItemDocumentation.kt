/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.tools.metalava.model.psi

import com.android.tools.metalava.model.AbstractItemDocumentation
import com.android.tools.metalava.model.ItemDocumentation
import com.intellij.psi.PsiElement

/** A Psi specialization of [ItemDocumentation]. */
internal class PsiItemDocumentation(override var text: String, private val psi: PsiElement) :
    AbstractItemDocumentation() {

    override fun duplicate() = PsiItemDocumentation(text, psi)

    override fun appendDocumentation(comment: String, tagSection: String?) {
        if (comment.isBlank()) {
            return
        }

        // Micro-optimization: we're very often going to be merging @apiSince and to a lesser
        // extent @deprecatedSince into existing comments, since we're flagging every single
        // public API. Normally merging into documentation has to be done carefully, since
        // there could be existing versions of the tag we have to append to, and some parts
        // of the comment needs to be present in certain places. For example, you can't
        // just append to the description of a method by inserting something right before "*/"
        // since you could be appending to a javadoc tag like @return.
        //
        // However, for @apiSince and @deprecatedSince specifically, in addition to being frequent,
        // they will (a) never appear in existing docs, and (b) they're separate tags, which means
        // it's safe to append them at the end. So we'll special case these two tags here, to
        // help speed up the builds since these tags are inserted 30,000+ times for each framework
        // API target (there are many), and each time would have involved constructing a full
        // javadoc
        // AST with lexical tokens using IntelliJ's javadoc parsing APIs. Instead, we'll just
        // do some simple string heuristics.
        if (
            tagSection == "@apiSince" ||
                tagSection == "@deprecatedSince" ||
                tagSection == "@sdkExtSince"
        ) {
            text = addUniqueTag(text, tagSection, comment)
            return
        }

        text = mergeDocumentation(text, psi, comment.trim(), tagSection, append = true)
    }

    private fun addUniqueTag(text: String, tagSection: String, commentLine: String): String {
        assert(commentLine.indexOf('\n') == -1) // Not meant for multi-line comments

        if (text.isBlank()) {
            return "/** $tagSection $commentLine */"
        }

        // Already single line?
        if (text.indexOf('\n') == -1) {
            val end = text.lastIndexOf("*/")
            return "/**\n *" + text.substring(3, end) + "\n * $tagSection $commentLine\n */"
        }

        var end = text.lastIndexOf("*/")
        while (end > 0 && text[end - 1].isWhitespace() && text[end - 1] != '\n') {
            end--
        }
        // The comment ends with:
        // * some comment here */
        val insertNewLine: Boolean = text[end - 1] != '\n'

        val indent: String
        var linePrefix = ""
        val secondLine = text.indexOf('\n')
        if (secondLine == -1) {
            // Single line comment
            indent = "\n * "
        } else {
            val indentStart = secondLine + 1
            var indentEnd = indentStart
            while (indentEnd < text.length) {
                if (!text[indentEnd].isWhitespace()) {
                    break
                }
                indentEnd++
            }
            indent = text.substring(indentStart, indentEnd)
            // TODO: If it starts with "* " follow that
            if (text.startsWith("* ", indentEnd)) {
                linePrefix = "* "
            }
        }
        return text.substring(0, end) +
            (if (insertNewLine) "\n" else "") +
            indent +
            linePrefix +
            tagSection +
            " " +
            commentLine +
            "\n" +
            indent +
            " */"
    }
}
