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

import com.android.tools.metalava.model.ItemDocumentation
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.source.AbstractItemDocumentation
import com.android.tools.metalava.model.source.toItemDocumentationFactory
import com.android.tools.metalava.reporter.FileLocation
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiDocCommentOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPackageStatement
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.uast.UElement

/** A Psi specialization of [ItemDocumentation]. */
internal class PsiItemDocumentation(
    item: SelectableItem,
    private val psiElement: PsiElement,
) : AbstractItemDocumentation(item) {
    /**
     * Lazily initialized reference to the [PsiComment], if any, from which the [text] was
     * retrieved.
     *
     * This is initialized in [initializeTextBackingField].
     */
    private var psiComment: PsiComment? = null

    override val fileLocation: FileLocation
        get() {
            // Make sure that the psiComment is initialized by making sure that the text backing
            // field has been initialized as they are initialized together in
            // [initializeTextBackingField].
            ensureTextBackingFieldHasBeenInitialized()
            return PsiFileLocation.fromPsiElement(psiComment)
        }

    /**
     * Lazy initializer for [_text] and [psiComment].
     *
     * Updates the [_text] field with the text of the document comment associated with [element] and
     * [psiComment] with the [PsiComment] for the document comment.
     */
    override fun initializeTextBackingField() {
        when (psiElement) {
            is PsiCompiledElement -> {
                // Drop through.
            }
            is KtDeclaration -> {
                psiElement.docComment?.let { comment ->
                    psiComment = comment
                    _text = comment.text
                    return
                }
            }
            is UElement -> {
                val comments = psiElement.comments
                if (comments.isNotEmpty()) {
                    for (comment in comments) {
                        val text = comment.text
                        if (text.startsWith("/**")) {
                            psiComment = comment.sourcePsi
                            _text = text
                            return
                        }
                    }
                }
            }
            is PsiDocCommentOwner -> {
                val docComment = psiElement.docComment
                if (docComment != null) {
                    val text = docComment.text
                    // Make sure that the text is a doc comment, i.e. starts with /**.
                    if (text.startsWith("/**")) {
                        psiComment = docComment
                        _text = text
                        return
                    }
                }
            }
            is PsiPackageStatement -> {
                val docComment =
                    PsiTreeUtil.getPrevSiblingOfType(psiElement, PsiDocComment::class.java)
                if (docComment != null) {
                    val text = docComment.text
                    // Make sure that the text is a doc comment, i.e. starts with /**.
                    if (text.startsWith("/**")) {
                        psiComment = docComment
                        _text = text
                        return
                    }
                }
            }
        }

        psiComment = null
        _text = ""
    }

    override fun duplicate(item: SelectableItem) =
        // Duplicating the text will fully qualify the comment in its original item before
        // copying it into the new item.
        text.toItemDocumentationFactory().create(item)!!
}
