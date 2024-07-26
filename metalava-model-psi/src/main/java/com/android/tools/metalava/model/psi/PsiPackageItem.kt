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

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.findClosestEnclosingNonEmptyPackage
import com.intellij.psi.PsiPackage

internal class PsiPackageItem
internal constructor(
    codebase: PsiBasedCodebase,
    private val psiPackage: PsiPackage,
    modifiers: DefaultModifierList,
    documentationFactory: ItemDocumentationFactory,
    private val qualifiedName: String,
    override val overviewDocumentation: String?,
    /** True if this package is from the classpath (dependencies). Exposed in [isFromClassPath]. */
    private val fromClassPath: Boolean
) :
    AbstractPsiItem(
        codebase = codebase,
        modifiers = modifiers,
        documentationFactory = documentationFactory,
        element = psiPackage
    ),
    PackageItem,
    PsiItem {

    override fun psi() = psiPackage

    private val topClasses = mutableListOf<ClassItem>()

    override fun qualifiedName(): String = qualifiedName

    override fun isFromClassPath(): Boolean = fromClassPath

    override fun topLevelClasses(): List<ClassItem> =
        // Return a copy to avoid a ConcurrentModificationException.
        topClasses.toList()

    // N.A. a package cannot be contained in a class
    override fun containingClass(): ClassItem? = null

    lateinit var containingPackageField: PackageItem

    override fun containingPackage(): PackageItem? {
        return if (qualifiedName.isEmpty()) null
        else {
            if (!::containingPackageField.isInitialized) {
                containingPackageField = codebase.findClosestEnclosingNonEmptyPackage(qualifiedName)
            }
            containingPackageField
        }
    }

    fun addTopClass(classItem: PsiClassItem) {
        if (!classItem.isTopLevelClass()) {
            return
        }

        topClasses.add(classItem)
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

            val pkg =
                PsiPackageItem(
                    codebase = codebase,
                    psiPackage = psiPackage,
                    modifiers = modifiers,
                    documentationFactory =
                        PsiItemDocumentation.factory(psiPackage, codebase, extraDocs),
                    qualifiedName = qualifiedName,
                    overviewDocumentation = overviewHtml,
                    fromClassPath = fromClassPath
                )
            return pkg
        }
    }
}
