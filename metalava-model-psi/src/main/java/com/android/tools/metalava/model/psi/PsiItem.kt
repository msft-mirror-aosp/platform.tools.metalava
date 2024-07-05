/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.metalava.model.ApiVariantSelectors
import com.android.tools.metalava.model.DefaultItem
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.ItemDocumentation
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.reporter.FileLocation
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifierListOwner
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.uast.UElement

abstract class PsiItem
internal constructor(
    final override val codebase: PsiBasedCodebase,
    element: PsiElement,
    fileLocation: FileLocation = PsiFileLocation(element),
    modifiers: DefaultModifierList,
    documentation: ItemDocumentation,
) :
    DefaultItem(
        fileLocation = fileLocation,
        modifiers = modifiers,
        documentation = documentation,
        variantSelectorsFactory = ApiVariantSelectors.MUTABLE_FACTORY,
    ) {

    /** The source PSI provided by UAST */
    internal val sourcePsi: PsiElement? = (element as? UElement)?.sourcePsi

    /** Returns the PSI element for this item */
    abstract fun psi(): PsiElement

    override fun isFromClassPath(): Boolean {
        return codebase.fromClasspath || containingClass()?.isFromClassPath() ?: false
    }

    final override fun findTagDocumentation(tag: String, value: String?): String? {
        if (psi() is PsiCompiledElement) {
            return null
        }
        if (documentation.isBlank()) {
            return null
        }

        // We can't just use element.docComment here because we may have modified
        // the comment and then the comment snapshot in PSI isn't up to date with our
        // latest changes
        val docComment = codebase.getComment(documentation.text)
        val tagComment =
            if (value == null) {
                docComment.findTagByName(tag)
            } else {
                docComment.findTagsByName(tag).firstOrNull { it.valueElement?.text == value }
            }

        if (tagComment == null) {
            return null
        }

        val text = tagComment.text
        // Trim trailing next line (javadoc *)
        var index = text.length - 1
        while (index > 0) {
            val c = text[index]
            if (!(c == '*' || c.isWhitespace())) {
                break
            }
            index--
        }
        index++
        if (index < text.length) {
            return text.substring(0, index)
        } else {
            return text
        }
    }

    final override fun appendDocumentation(comment: String, tagSection: String?) {
        if (comment.isBlank()) {
            return
        }

        // TODO: Figure out if an annotation should go on the return value, or on the method.
        // For example; threading: on the method, range: on the return value.
        // TODO: Find a good way to add or append to a given tag (@param <something>, @return, etc)

        if (this is ParameterItem) {
            // For parameters, the documentation goes into the surrounding method's documentation!
            // Find the right parameter location!
            val parameterName = name()
            val target = containingMethod()
            target.appendDocumentation(comment, parameterName)
            return
        }

        documentation.appendDocumentation(comment, tagSection)
    }

    final override fun fullyQualifiedDocumentation(): String {
        return fullyQualifiedDocumentation(documentation.text)
    }

    final override fun fullyQualifiedDocumentation(documentation: String): String {
        return codebase.docQualifier.toFullyQualifiedDocumentation(this, documentation)
    }

    final override fun isJava(): Boolean {
        return !isKotlin()
    }

    final override fun isKotlin(): Boolean {
        return psi().isKotlin()
    }

    companion object {

        /**
         * Get the javadoc for the [element] as an [ItemDocumentation] instance.
         *
         * If [allowReadingComments] is `false` then this will return [ItemDocumentation.NONE].
         */
        internal fun javadocAsItemDocumentation(
            element: PsiElement,
            codebase: PsiBasedCodebase,
            extraDocs: String? = null,
        ): ItemDocumentation {
            return PsiItemDocumentation(element, codebase, extraDocs)
        }

        internal fun modifiers(
            codebase: PsiBasedCodebase,
            element: PsiModifierListOwner,
            documentation: ItemDocumentation? = null,
        ): DefaultModifierList {
            return PsiModifierItem.create(codebase, element, documentation)
        }
    }
}

/** Check whether a [PsiElement] is Kotlin or not. */
fun PsiElement.isKotlin(): Boolean {
    return language === KotlinLanguage.INSTANCE
}
