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

import java.nio.CharBuffer
import kotlin.math.max
import org.antlr.v4.runtime.ANTLRErrorListener
import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CodePointBuffer
import org.antlr.v4.runtime.CodePointCharStream
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.NoViableAltException
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.TokenStream

/**
 * Parses a block of text into a [JavadocContent].
 *
 * A wrapper around [antlrParser] which actually does the parsing.
 */
internal class JavadocParser private constructor(private val antlrParser: AntlrJavadocParser) {

    companion object {
        /**
         * Parse [text] from [startInclusive] up to, but not including [endExclusive] as a javadoc
         * comment (optionally including the /** ... */).
         *
         * @param text the String to be parsed.
         * @param startInclusive the index of the first character to parse.
         * @param endExclusive the index after the last character to parse.
         * @param fileName the file where the comment was located.
         * @param startLineNumber the line number where within [fileName] where the comment starts.
         * @param errorListener the optional [ANTLRErrorListener], defaults to
         *   [JavadocErrorListener] which will throw an exception.
         */
        fun parse(
            text: String,
            startInclusive: Int,
            endExclusive: Int,
            fileName: String = "<unknown>",
            startLineNumber: Int = 1,
            errorListener: ANTLRErrorListener = JavadocErrorListener(fileName, startLineNumber),
        ): JavadocContent {
            val charStream = charStreamFromStringRange(text, startInclusive, endExclusive, fileName)
            val lexer = AntlrJavadocLexer(charStream)
            val tokenStream = CommonTokenStream(lexer)
            val antlrParser = AntlrJavadocParser(tokenStream)
            antlrParser.removeErrorListeners()
            antlrParser.addErrorListener(errorListener)
            val parser = JavadocParser(antlrParser)
            return parser.parse()
        }

        /**
         * Create a [CodePointCharStream] that is backed by [text] from [startInclusive] up to but
         * not including [endExclusive].
         */
        private fun charStreamFromStringRange(
            text: String,
            startInclusive: Int,
            endExclusive: Int,
            fileName: String,
        ): CodePointCharStream {
            var length = endExclusive - startInclusive
            val codePointBufferBuilder = CodePointBuffer.builder(length)
            val cb = CharBuffer.allocate(length)
            cb.put(text, startInclusive, endExclusive)
            cb.flip()
            codePointBufferBuilder.append(cb)
            return CodePointCharStream.fromBuffer(codePointBufferBuilder.build(), fileName)
        }
    }

    private fun parse(): JavadocContent {
        val descriptionContext = antlrParser.description()
        return JavadocContentBuilder.buildFrom(descriptionContext) ?: JavadocContent.EMPTY
    }
}

/** A [BaseErrorListener] that throws an exception for syntax errors. */
internal class JavadocErrorListener(
    private val fileName: String,
    startLineNumber: Int,
) : BaseErrorListener() {
    private val lineOffset = max(0, startLineNumber - 1)

    override fun syntaxError(
        recognizer: Recognizer<*, *>?,
        offendingSymbol: Any?,
        line: Int,
        charPositionInLine: Int,
        msg: String?,
        e: RecognitionException?
    ) {
        // Construct a full message that includes lots of debug information about the nature of the
        // problem. This is not intended to report issues for developers to
        val fullMsg =
            e?.expectedTokens?.let { expectedTokens ->
                buildString {
                    append(msg)
                    append("\n")
                    append("  Expected:\n")
                    val vocabulary = recognizer?.vocabulary
                    for (interval in expectedTokens.intervals) {
                        for (tokenTypeIndex in interval.a.rangeTo(interval.b)) {
                            append("    ${vocabulary?.getSymbolicName(tokenTypeIndex)}\n")
                        }
                    }
                    val offendingToken = e.offendingToken
                    val tokens = recognizer?.inputStream as? TokenStream
                    if (e is NoViableAltException && tokens != null) {
                        append("  No viable alternative found for sequence:\n")
                        for (tokenIndex in
                            e.startToken.tokenIndex.rangeTo(offendingToken.tokenIndex)) {
                            val token = tokens[tokenIndex]
                            append(
                                "    ${vocabulary?.getSymbolicName(token.type)} \"${token.text}\"\n"
                            )
                        }
                    } else {
                        append("  Found:\n")
                        append(
                            "    ${vocabulary?.getSymbolicName(offendingToken.type)} \"${offendingToken.text}\"\n"
                        )
                    }
                }
            } ?: msg
        val actualLine = line + lineOffset
        error("$fileName:$actualLine:$charPositionInLine $fullMsg")
    }
}

/** Builds [JavadocContent] from [AntlrJavadocParser.DescriptionContext]. */
private class JavadocContentBuilder : AntlrJavadocParserBaseVisitor<Unit>() {
    /**
     * A [MutableList] of consecutive [JavadocContent] instances that have been created from the
     * Javadoc.
     *
     * Is `null` if no [JavadocContent] has yet been added. This backs [contentList] and should not
     * be accessed directly except by [contentList], [nestedContent] and [getContent].
     */
    @Deprecated(message = "Do not access directly", replaceWith = ReplaceWith("contentList"))
    private var _contentList: MutableList<JavadocContent>? = null

    /**
     * A [MutableList] of consecutive [JavadocContent] instances that have been created from the
     * Javadoc.
     */
    @Suppress("DEPRECATION")
    private val contentList: MutableList<JavadocContent>
        get() =
            _contentList
                ?: let {
                    val list = mutableListOf<JavadocContent>()
                    _contentList = list
                    list
                }

    /**
     * Append [javadocContent] to [contentList].
     *
     * This will flush any text that has been buffered in [textBuffer].
     */
    private fun appendContent(javadocContent: JavadocContent) {
        // Make sure that any text which has been appended to [textBuffer] has been added to the
        // content list so that it appears before [javadocContent].
        flushText()

        contentList.add(javadocContent)
    }

    /** [StringBuilder] into which consecutive blocks of text from the Javadoc are accumulated. */
    private val textBuffer = StringBuilder()

    /** Append [text] to [textBuffer]. */
    private fun appendText(text: String) {
        textBuffer.append(text)
    }

    /** Append newline character to [textBuffer]. */
    private fun appendNewline() {
        appendText("\n")
    }

    /**
     * If [textBuffer] is not empty then this will wrap it in a [JavadocText] object, add that to
     * the [contentList] and clear [textBuffer].
     */
    private fun flushText() {
        if (textBuffer.isNotEmpty()) {
            var text = textBuffer.toString()
            textBuffer.clear()
            contentList.add(JavadocText(text))
        }
    }

    /**
     * Create a [JavadocContent] for nested content.
     *
     * This flushes the [textBuffer], saves away [_contentList], setting it to `null` and then
     * invokes [body] to apply this visitor to the nested content to populate [textBuffer] and
     * [contentList]. It then calls [getContent]
     */
    @Suppress("DEPRECATION")
    private fun nestedContent(body: () -> Unit): JavadocContent? {
        // Make sure that any text which has been appended to [textBuffer] has been added to the
        // content list so that it appears before any nested content.
        flushText()

        // Save away the current _contentList and set it to null so a new list will be created if
        // any nested content is added.
        val oldContentList = _contentList
        _contentList = null
        try {
            // Call the body lambda which will add any nested content.
            body()

            // Get the nested content that was added by [body].
            return getContent()
        } finally {
            // Restore _contentList back to what it was before.
            _contentList = oldContentList
        }
    }

    /**
     * Get a [JavadocContent] object for any content that has been added to the [textBuffer] and
     * [_contentList].
     *
     * If [textBuffer] contains any textual content then it is added to [_contentList] first.
     *
     * If [_contentList] has not yet been created, or was created but is empty then there is no
     * content so this returns `null`. If [_contentList] contains a single item then it is returned.
     * Otherwise, the [_contentList] is wrapped in a [JavadocContentList].
     *
     * Irrespective of what this returns, [_contentList] will be `null` after this returns.
     */
    @Suppress("DEPRECATION")
    private fun getContent(): JavadocContent? {
        // Make sure that any text which has been appended to [textBuffer] has been added to the
        // content list so that it will be included in the returned content.
        flushText()

        val contentList = _contentList
        return if (contentList == null) {
            null
        } else {
            // Discard the content list to force a new one to be created next time content is added.
            // This will ensure correct behavior even if _contentList is wrapped in a
            // [JavadocContentList].
            _contentList = null

            val size = contentList.size
            when (size) {
                0 -> null
                1 -> contentList[0]
                else -> {
                    JavadocContentList(contentList.toList())
                }
            }
        }
    }

    override fun visitDescriptionLineText(ctx: AntlrJavadocParser.DescriptionLineTextContext) {
        // Add the text to the text buffer.
        appendText(ctx.text)
    }

    override fun visitDescriptionNewline(ctx: AntlrJavadocParser.DescriptionNewlineContext?) {
        // This matches a newline possible following by white space and then a `*` (but not '*/').
        // However, the white space and `*` are not considered part of the comment so are ignored.
        appendNewline()
    }

    override fun visitInlineTag(ctx: AntlrJavadocParser.InlineTagContext) {
        val tagType = ctx.inlineTagName().NAME().text

        // Get the nested content, if any.
        val inlineTagContentContext = ctx.inlineTagContent()
        val tagContent =
            inlineTagContentContext?.let { inlineCtx ->
                // Construct a nested JavadocContent object from the content of the inline tag.
                nestedContent {
                    // Visit the inline tag content.
                    inlineCtx.accept(this)
                }
            }

        // Add an inline tag to the content.
        appendContent(JavadocInlineTag(tagType, tagContent))
    }

    override fun visitBraceExpression(ctx: AntlrJavadocParser.BraceExpressionContext) {
        // A brace expression implicitly starts and stops with braces so add them into the model.
        appendText("{")
        super.visitBraceExpression(ctx)
        appendText("}")
    }

    override fun visitBraceText(ctx: AntlrJavadocParser.BraceTextContext) {
        // Brace text is just a set of possible different blocks of text so just add them into the
        // buffer.
        appendText(ctx.text)
    }

    companion object {
        /** Build a optional [JavadocContent] from [descriptionContext]. */
        fun buildFrom(descriptionContext: AntlrJavadocParser.DescriptionContext): JavadocContent? {
            // Create a builder that will create [JavadocContent] by traversing the
            // [descriptionContext] structure.
            val builder = JavadocContentBuilder()

            // Traverse the [descriptionContent] structure.
            descriptionContext.accept(builder)

            // Get the [JavadocContent], if any, that was created.
            return builder.getContent()
        }
    }
}
