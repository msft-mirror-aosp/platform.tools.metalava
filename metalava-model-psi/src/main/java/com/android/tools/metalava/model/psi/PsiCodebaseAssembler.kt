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

import com.android.SdkConstants
import com.android.tools.lint.UastEnvironment
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.item.CodebaseAssembler
import com.android.tools.metalava.model.item.DefaultPackageItem
import com.android.tools.metalava.model.item.PackageDoc
import com.android.tools.metalava.model.item.PackageDocs
import com.android.tools.metalava.reporter.Issues
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.file.PsiPackageImpl
import com.intellij.psi.search.GlobalSearchScope
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

internal class PsiCodebaseAssembler(
    internal val uastEnvironment: UastEnvironment,
    codebaseFactory: (PsiCodebaseAssembler) -> PsiBasedCodebase
) : CodebaseAssembler {

    internal val codebase = codebaseFactory(this)

    internal val project: Project = uastEnvironment.ideaProject

    private val reporter
        get() = codebase.reporter

    fun dispose() {
        uastEnvironment.dispose()
    }

    override fun createPackageItem(
        packageName: String,
        packageDoc: PackageDoc,
        containingPackage: PackageItem?
    ): DefaultPackageItem {
        val psiPackage =
            codebase.findPsiPackage(packageName)
                ?: run {
                    // This can happen if a class's package statement does not match its file path.
                    // In that case, this fakes up a PsiPackageImpl that matches the package
                    // statement as that is the source of truth.
                    val manager = PsiManager.getInstance(codebase.project)
                    PsiPackageImpl(manager, packageName)
                }
        return PsiPackageItem.create(
            codebase = codebase,
            psiPackage = psiPackage,
            packageDoc = packageDoc,
            containingPackage = containingPackage,
        )
    }

    override fun createClassFromUnderlyingModel(qualifiedName: String): ClassItem? {
        TODO("Not yet implemented")
    }

    internal fun initializeFromJar(jarFile: File) {
        // Create the initial set of packages that were found in the jar files. When loading from a
        // jar there is no package documentation so this will only create the root package.
        codebase.packageTracker.createInitialPackages(PackageDocs.EMPTY)

        // Find all classes referenced from the class
        val facade = JavaPsiFacade.getInstance(project)
        val scope = GlobalSearchScope.allScope(project)

        val isFromClassPath = codebase.isFromClassPath()
        try {
            ZipFile(jarFile).use { jar ->
                val enumeration = jar.entries()
                while (enumeration.hasMoreElements()) {
                    val entry = enumeration.nextElement()
                    val fileName = entry.name
                    if (fileName.contains("$")) {
                        // skip inner classes
                        continue
                    }
                    if (!fileName.endsWith(SdkConstants.DOT_CLASS)) {
                        // skip entries that are not .class files.
                        continue
                    }

                    val qualifiedName =
                        fileName.removeSuffix(SdkConstants.DOT_CLASS).replace('/', '.')
                    if (qualifiedName.endsWith(".package-info")) {
                        // skip package-info files.
                        continue
                    }

                    val psiClass = facade.findClass(qualifiedName, scope) ?: continue

                    val classItem =
                        codebase.createTopLevelClassAndContents(psiClass, isFromClassPath)
                    codebase.addTopLevelClassFromSource(classItem)
                }
            }
        } catch (e: IOException) {
            reporter.report(Issues.IO_ERROR, jarFile, e.message ?: e.toString())
        }
    }
}
