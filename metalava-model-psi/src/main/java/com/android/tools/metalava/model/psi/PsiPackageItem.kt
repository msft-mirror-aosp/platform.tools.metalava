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
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.item.DefaultPackageItem
import com.intellij.psi.PsiPackage

internal class PsiPackageItem
internal constructor(
    override val codebase: PsiBasedCodebase,
    private val psiPackage: PsiPackage,
    modifiers: DefaultModifierList,
    documentationFactory: ItemDocumentationFactory,
    qualifiedName: String,
    override val overviewDocumentation: String?,
    /** True if this package is from the classpath (dependencies). Exposed in [isFromClassPath]. */
    private val fromClassPath: Boolean
) :
    DefaultPackageItem(
        codebase = codebase,
        fileLocation = PsiFileLocation.fromPsiElement(psiPackage),
        itemLanguage = psiPackage.itemLanguage,
        modifiers = modifiers,
        documentationFactory = documentationFactory,
        variantSelectorsFactory = ApiVariantSelectors.MUTABLE_FACTORY,
        qualifiedName = qualifiedName,
    ),
    PackageItem,
    PsiItem {

    override fun psi() = psiPackage

    override fun isFromClassPath(): Boolean = fromClassPath

    // N.A. a package cannot be contained in a class
    override fun containingClass(): ClassItem? = null

    fun addTopClass(classItem: PsiClassItem) {
        if (!classItem.isTopLevelClass()) {
            return
        }

        super.addTopClass(classItem)
        classItem.containingPackage = this
    }

    fun addClasses(classList: List<PsiClassItem>) {
        for (cls in classList) {
            addTopClass(cls)
        }
    }

    companion object {
        fun create(
            codebase: PsiBasedCodebase,
            psiPackage: PsiPackage,
            extraDocs: String?,
            overviewHtml: String?,
            fromClassPath: Boolean,
        ): PsiPackageItem {
            val modifiers = PsiModifierItem.create(codebase, psiPackage)
            if (modifiers.isPackagePrivate()) {
                // packages are always public (if not hidden explicitly with private)
                modifiers.setVisibilityLevel(VisibilityLevel.PUBLIC)
            }
            val qualifiedName = psiPackage.qualifiedName

            return PsiPackageItem(
                codebase = codebase,
                psiPackage = psiPackage,
                modifiers = modifiers,
                documentationFactory =
                    PsiItemDocumentation.factory(psiPackage, codebase, extraDocs),
                qualifiedName = qualifiedName,
                overviewDocumentation = overviewHtml,
                fromClassPath = fromClassPath
            )
        }
    }
}
