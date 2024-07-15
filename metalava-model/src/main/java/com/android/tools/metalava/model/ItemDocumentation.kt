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

package com.android.tools.metalava.model

/** A factory that will create an [ItemDocumentation] for a specific [Item]. */
typealias ItemDocumentationFactory = (Item) -> ItemDocumentation

/**
 * The documentation associated with an [Item].
 *
 * This implements [CharSequence] to simplify migration.
 */
interface ItemDocumentation : CharSequence {
    val text: String

    override val length
        get() = text.length

    override fun get(index: Int) = text.get(index)

    override fun subSequence(startIndex: Int, endIndex: Int) =
        text.subSequence(startIndex, endIndex)

    /**
     * True if the documentation contains one of the following tags that indicates that it should
     * not be part of an API, unless overridden by a show annotation:
     * * `@hide`
     * * `@pending`
     * * `@suppress`
     */
    val isHidden: Boolean

    /**
     * True if the documentation contains `@doconly` which indicates that it should only be included
     * in stubs that are generated for documentation purposes.
     */
    val isDocOnly: Boolean

    /**
     * True if the documentation contains `@removed` which indicates that the [Item] must not be
     * included in stubs or the main signature file but will be included in the `removed` signature
     * file as it is still considered part of the API available at runtime and so cannot be removed
     * altogether.
     */
    val isRemoved: Boolean

    /**
     * Return a duplicate of this instance.
     *
     * [ItemDocumentation] instances can be mutable, and if they are then they must not be shared.
     */
    fun duplicate(): ItemDocumentation

    /** Work around javadoc cutting off the summary line after the first ". ". */
    fun workAroundJavaDocSummaryTruncationIssue() {}

    /**
     * Add the given text to the documentation.
     *
     * If the [tagSection] is null, add the comment to the initial text block of the description.
     * Otherwise, if it is "@return", add the comment to the return value. Otherwise, the
     * [tagSection] is taken to be the parameter name, and the comment added as parameter
     * documentation for the given parameter.
     */
    fun appendDocumentation(comment: String, tagSection: String?)

    /**
     * Looks up docs for the first instance of a specific javadoc tag having the (optionally)
     * provided value (e.g. parameter name).
     */
    fun findTagDocumentation(tag: String, value: String? = null): String?

    /** Returns the main documentation for the method (the documentation before any tags). */
    fun findMainDocumentation(): String

    companion object {
        /**
         * A special [ItemDocumentation] that contains no documentation.
         *
         * Used where there is no documentation possible, e.g. text model, type parameters,
         * parameters.
         */
        val NONE: ItemDocumentation = EmptyItemDocumentation()

        /** Wrap a [String] in an [ItemDocumentation]. */
        fun String.toItemDocumentation(): ItemDocumentation = DefaultItemDocumentation(this)
    }

    /** An empty [ItemDocumentation] that can never contain any text. */
    private class EmptyItemDocumentation : ItemDocumentation {
        override val text
            get() = ""

        override val isHidden
            get() = false

        override val isDocOnly
            get() = false

        override val isRemoved
            get() = false

        // This is ok to share as it is immutable.
        override fun duplicate() = this

        override fun findTagDocumentation(tag: String, value: String?): String? = null

        override fun appendDocumentation(comment: String, tagSection: String?) {
            error("cannot modify documentation on an item that does not support documentation")
        }

        override fun findMainDocumentation() = ""
    }
}

/**
 * Abstract [ItemDocumentation] into which functionality that is common to all models will be added.
 */
abstract class AbstractItemDocumentation : ItemDocumentation {

    /**
     * The mutable text contents of the documentation. This is abstract to allow the implementations
     * of this to optimize how it is accessed, e.g. initialize it lazily.
     */
    abstract override var text: String

    override val isHidden
        get() =
            text.contains('@') &&
                (text.contains("@hide") ||
                    text.contains("@pending") ||
                    // KDoc:
                    text.contains("@suppress"))

    override val isDocOnly
        get() = text.contains("@doconly")

    override val isRemoved
        get() = text.contains("@removed")

    override fun workAroundJavaDocSummaryTruncationIssue() {
        // Work around javadoc cutting off the summary line after the first ". ".
        val firstDot = text.indexOf(".")
        if (firstDot > 0 && text.regionMatches(firstDot - 1, "e.g. ", 0, 5, false)) {
            text = text.substring(0, firstDot) + ".g.&nbsp;" + text.substring(firstDot + 4)
        }
    }

    override fun findTagDocumentation(tag: String, value: String?): String? {
        TODO("Not yet implemented")
    }

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

        mergeDocumentation(comment.trim(), tagSection)
    }

    /**
     * Merge the comment into the appropriate [tagSection].
     *
     * See [Item.appendDocumentation] for more details.
     */
    protected abstract fun mergeDocumentation(comment: String, tagSection: String?)

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

/** A default [ItemDocumentation] containing JavaDoc/KDoc. */
internal class DefaultItemDocumentation(override var text: String) : AbstractItemDocumentation() {

    override fun duplicate() = DefaultItemDocumentation(text)

    override fun mergeDocumentation(comment: String, tagSection: String?) {
        TODO("Not yet implemented")
    }

    override fun findMainDocumentation(): String {
        TODO("Not yet implemented")
    }
}
