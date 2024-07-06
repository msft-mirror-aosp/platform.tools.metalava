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
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiDocCommentOwner
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.uast.UElement
import org.jetbrains.uast.sourcePsiElement

/** A Psi specialization of [ItemDocumentation]. */
internal class PsiItemDocumentation(
    private val psi: PsiElement,
    private val codebase: PsiBasedCodebase,
    private val extraDocs: String?,
) : AbstractItemDocumentation() {

    /** Lazily initialized backing property for [text]. */
    private lateinit var _text: String

    override var text: String
        get() = if (::_text.isInitialized) _text else initializeText()
        set(value) {
            _text = value
        }

    /** Lazy initializer for [_text]. */
    private fun initializeText(): String {
        _text =
            javadoc(psi, codebase.allowReadingComments).let {
                if (extraDocs != null) it + "\n$extraDocs" else it
            }
        return _text
    }

    override fun duplicate() = PsiItemDocumentation(psi, codebase, extraDocs)

    override fun mergeDocumentation(comment: String, tagSection: String?) {
        text = mergeDocumentation(text, psi, comment, tagSection, append = true)
    }

    companion object {
        // Gets the javadoc of the current element, unless reading comments is
        // disabled via allowReadingComments
        private fun javadoc(element: PsiElement, allowReadingComments: Boolean): String {
            if (!allowReadingComments) {
                return ""
            }
            if (element is PsiCompiledElement) {
                return ""
            }

            if (element is KtDeclaration) {
                return element.docComment?.text.orEmpty()
            }

            if (element is UElement) {
                val comments = element.comments
                if (comments.isNotEmpty()) {
                    val sb = StringBuilder()
                    comments.asSequence().joinTo(buffer = sb, separator = "\n") { it.text }
                    return sb.toString()
                } else {
                    // Temporary workaround: UAST seems to not return document nodes
                    // https://youtrack.jetbrains.com/issue/KT-22135
                    val first = element.sourcePsiElement?.firstChild
                    if (first is KDoc) {
                        return first.text
                    }
                }
            }

            if (element is PsiDocCommentOwner && element.docComment !is PsiCompiledElement) {
                return element.docComment?.text ?: ""
            }

            return ""
        }
    }
}
