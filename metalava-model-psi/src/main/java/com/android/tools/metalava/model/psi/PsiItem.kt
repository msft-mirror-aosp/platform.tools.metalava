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

import com.android.tools.metalava.model.AbstractItem
import com.android.tools.metalava.model.ApiVariantSelectors
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.ItemDocumentation
import com.android.tools.metalava.model.ItemDocumentation.Companion.toItemDocumentationFactory
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.android.tools.metalava.reporter.FileLocation
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
    documentationFactory: ItemDocumentationFactory,
) :
    AbstractItem(
        fileLocation = fileLocation,
        modifiers = modifiers,
        documentationFactory = documentationFactory,
        variantSelectorsFactory = ApiVariantSelectors.MUTABLE_FACTORY,
    ) {

    /** The source PSI provided by UAST */
    internal val sourcePsi: PsiElement? = (element as? UElement)?.sourcePsi

    /** Returns the PSI element for this item */
    abstract fun psi(): PsiElement

    override fun isFromClassPath(): Boolean {
        return codebase.fromClasspath || containingClass()?.isFromClassPath() ?: false
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
         * If [PsiBasedCodebase.allowReadingComments] is `true` then this will return a factory that
         * creates a [PsiItemDocumentation] instance. If [extraDocs] is not-null then this will
         * return a factory that will create an [ItemDocumentation] wrapper around [extraDocs],
         * otherwise it will return [ItemDocumentation.NONE_FACTORY].
         */
        internal fun javadocAsItemDocumentationFactory(
            element: PsiElement,
            codebase: PsiBasedCodebase,
            extraDocs: String? = null,
        ) =
            if (codebase.allowReadingComments) {
                // When reading comments provide full access to them.
                { PsiItemDocumentation(element, codebase, extraDocs) }
            } else {
                // If extraDocs are provided then they most likely contain documentation for the
                // package from a `package-info.java` or `package.html` file. Make sure that they
                // are included in the `ItemDocumentation`, otherwise package hiding will not work.
                extraDocs?.toItemDocumentationFactory()
                // Otherwise, there is no documentation to use.
                ?: ItemDocumentation.NONE_FACTORY
            }

        internal fun modifiers(
            codebase: PsiBasedCodebase,
            element: PsiModifierListOwner,
        ): DefaultModifierList {
            return PsiModifierItem.create(codebase, element)
        }
    }
}

/** Check whether a [PsiElement] is Kotlin or not. */
fun PsiElement.isKotlin(): Boolean {
    return language === KotlinLanguage.INSTANCE
}
