/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.tools.metalava.model.text.classpath

import com.android.tools.lint.UastEnvironment
import com.android.tools.metalava.FileFormat
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.DefaultCodebase
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.PackageDocs
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.PackageList
import com.android.tools.metalava.model.psi.PsiBasedCodebase
import com.android.tools.metalava.model.text.TextBackedAnnotationItem
import com.android.tools.metalava.model.text.TextCodebase
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope

/**
 * A codebase based on the given [textCodebase] that uses the [classpathEnvironment] to resolve
 * classes that are referenced by the [textCodebase] API but not part of it.
 *
 * For instance, if a class in a [textCodebase] extends a class which is not part of the API, the
 * parent class will appear as an empty stub in the [textCodebase]. If that [textCodebase] were
 * directly compared to another codebase (rather than through a [TextCodebaseWithClasspath] with the
 * parent class resolved from the classpath), it could incorrectly make it seem that there are API
 * compatibility errors.
 */
class TextCodebaseWithClasspath(
    private val textCodebase: TextCodebase,
    private val classpathEnvironment: UastEnvironment
) : DefaultCodebase(textCodebase.location) {
    override var description: String = "Text codebase with resolved classes from the classpath"

    private val classpathCodebase: PsiBasedCodebase

    // Properties used to resolve classes from the classpath
    private val project = classpathEnvironment.ideaProject
    private val javaPsiFacade = JavaPsiFacade.getInstance(project)
    private val searchScope = GlobalSearchScope.everythingScope(project)

    private val packages: PackageList

    override fun getPackages(): PackageList = packages

    override fun size(): Int = packages.packages.size

    init {
        classpathCodebase =
            PsiBasedCodebase(location, "Codebase from classpath", fromClasspath = true)
        val emptyPackageDocs = PackageDocs(mutableMapOf(), mutableMapOf(), mutableSetOf())
        classpathCodebase.initialize(classpathEnvironment, emptyList(), emptyPackageDocs)

        // For each wrapped class, try to find class from classpath.
        for ((erasedName, wrappedStubClass) in textCodebase.wrappedStubClasses) {
            val psiClass = javaPsiFacade.findClass(erasedName, searchScope)
            if (psiClass != null) {
                // The class was found on the classpath so find or create a PsiClassItem for it.
                val psiClassItem = classpathCodebase.findOrCreateClass(psiClass)
                wrappedStubClass.wrappedItem = psiClassItem
            }
        }

        // The packages for the combined codebase is the same as the text codebase as each
        // WrappedClassItem has already been added to the package.
        packages = textCodebase.getPackages()
    }

    override fun createAnnotation(
        source: String,
        context: Item?,
        mapName: Boolean
    ): AnnotationItem {
        return TextBackedAnnotationItem(this, source, mapName)
    }

    // Search the [textCodebase] before the [classpathCodebase]
    override fun findClass(className: String): ClassItem? =
        textCodebase.findClass(className) ?: classpathCodebase.findClass(className)

    override fun findPackage(pkgName: String): PackageItem? =
        packages.packages.firstOrNull { it.qualifiedName() == pkgName }

    // Inherit properties from the backing [textCodebase]
    val format: FileFormat = textCodebase.format
    override var preFiltered: Boolean = textCodebase.preFiltered

    override fun getPackageDocs(): PackageDocs? = textCodebase.getPackageDocs()

    override fun supportsDocumentation(): Boolean = textCodebase.supportsDocumentation()

    override fun trustedApi(): Boolean = textCodebase.trustedApi()
}
