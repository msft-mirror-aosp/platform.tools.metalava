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

import com.android.tools.metalava.model.AnnotationManager
import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.MutableCodebase
import com.android.tools.metalava.model.item.DefaultCodebase
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Reporter
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiPackage
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.search.GlobalSearchScope
import java.io.File
import org.jetbrains.uast.UMethod

const val METHOD_ESTIMATE = 1000

/**
 * A codebase containing Java, Kotlin, or UAST PSI classes
 *
 * After creation, a list of PSI file is passed to [PsiCodebaseAssembler.initializeFromSources] or a
 * JAR file is passed to [PsiCodebaseAssembler.initializeFromJar]. This creates package and class
 * items along with their members. Any classes defined in those files will have
 * [ClassItem.isFromClassPath] set to [fromClasspath].
 *
 * Classes that are created through [findOrCreateClass] will have [ClassItem.isFromClassPath] set to
 * `true`. That will include classes defined elsewhere on the source path or found on the class
 * path.
 */
internal class PsiBasedCodebase(
    location: File,
    description: String = "Unknown",
    annotationManager: AnnotationManager,
    override val reporter: Reporter,
    val allowReadingComments: Boolean,
    val fromClasspath: Boolean = false,
    assembler: PsiCodebaseAssembler,
) :
    DefaultCodebase(
        location = location,
        description = description,
        preFiltered = false,
        annotationManager = annotationManager,
        trustedApi = false,
        supportsDocumentation = true,
        assembler = assembler,
    ),
    MutableCodebase {

    internal val psiAssembler = assembler

    internal val project: Project
        get() = psiAssembler.project

    /**
     * Printer which can convert PSI, UAST and constants into source code, with ability to filter
     * out elements that are not part of a codebase etc
     */
    internal val printer = CodePrinter(this, reporter)

    /**
     * Map from classes to the set of callables for each (but only for classes where we've called
     * [findCallableByPsiMethod]
     */
    private val methodMap: MutableMap<ClassItem, MutableMap<PsiMethod, PsiCallableItem>> =
        HashMap(METHOD_ESTIMATE)

    /** [PsiTypeItemFactory] used to create [PsiTypeItem]s. */
    internal val globalTypeItemFactory
        get() = psiAssembler.globalTypeItemFactory

    override fun dispose() {
        psiAssembler.dispose()
        super.dispose()
    }

    /**
     * Create top level classes, their inner classes and all the other members.
     *
     * All the classes are registered by name and so can be found by [findOrCreateClass].
     */
    internal fun createTopLevelClassAndContents(
        psiClass: PsiClass,
        isFromClassPath: Boolean,
    ): ClassItem {
        if (psiClass.containingClass != null) error("$psiClass is not a top level class")
        return createClass(psiClass, null, globalTypeItemFactory, isFromClassPath)
    }

    internal fun createClass(
        psiClass: PsiClass,
        containingClassItem: ClassItem?,
        enclosingClassTypeItemFactory: PsiTypeItemFactory,
        isFromClassPath: Boolean,
    ): ClassItem {
        val packageName = getPackageName(psiClass)

        // If the package could not be found then report an error.
        findPsiPackage(packageName)
            ?: run {
                val directory =
                    psiClass.containingFile.containingDirectory.virtualFile.canonicalPath
                reporter.report(
                    Issues.INVALID_PACKAGE,
                    psiClass,
                    "Could not find package $packageName for class ${psiClass.qualifiedName}." +
                        " This is most likely due to a mismatch between the package statement" +
                        " and the directory $directory"
                )
            }

        val packageItem = packageTracker.findOrCreatePackage(packageName)

        // If initializing is true, this class is from source
        val classItem =
            PsiClassItem.create(
                this,
                psiClass,
                containingClassItem,
                packageItem,
                enclosingClassTypeItemFactory,
                isFromClassPath,
            )
        // Set emit to `true` for source classes but `false` for classpath classes.
        classItem.emit = !classItem.isFromClassPath()

        return classItem
    }

    internal fun findPsiPackage(pkgName: String): PsiPackage? {
        return JavaPsiFacade.getInstance(project).findPackage(pkgName)
    }

    fun findClass(psiClass: PsiClass): ClassItem? {
        val qualifiedName: String = psiClass.qualifiedName ?: psiClass.name!!
        return findClass(qualifiedName)
    }

    internal fun findOrCreateClass(qualifiedName: String): ClassItem? {
        // Check to see if the class has already been seen and if so return it immediately.
        findClass(qualifiedName)?.let {
            return it
        }

        // The following cannot find a class whose name does not correspond to the file name, e.g.
        // in Java a class that is a second top level class.
        val finder = JavaPsiFacade.getInstance(project)
        val psiClass =
            finder.findClass(qualifiedName, GlobalSearchScope.allScope(project)) ?: return null
        return findOrCreateClass(psiClass)
    }

    /**
     * Identifies a point in the [ClassItem] nesting structure where new [ClassItem]s need
     * inserting.
     */
    data class NewClassInsertionPoint(
        /**
         * The [PsiClass] that is the root of the nested classes that need creation, is a top level
         * class if [containingClassItem] is `null`.
         */
        val missingPsiClass: PsiClass,

        /** The containing class item, or `null` if the top level. */
        val containingClassItem: ClassItem?,
    )

    /**
     * Called when no [ClassItem] was found by [findClass]`([PsiClass]) when called on [psiClass].
     *
     * The purpose of this is to find where a new [ClassItem] should be inserted in the nested class
     * structure. It finds the outermost [PsiClass] with no associated [ClassItem] but which is
     * either a top level class or whose containing [PsiClass] does have an associated [ClassItem].
     * That is the point where new classes need to be created.
     *
     * e.g. if the nesting structure is `A.B.C` and `A` has already been created then the insertion
     * point would consist of [ClassItem] for `A` (the containing class item) and the [PsiClass] for
     * `B` (the outermost [PsiClass] with no associated item).
     *
     * If none had already been created then it would return an insertion point consisting of no
     * containing class item and the [PsiClass] for `A`.
     */
    private fun findNewClassInsertionPoint(psiClass: PsiClass): NewClassInsertionPoint {
        var current = psiClass
        do {
            // If the current has no containing class then it has reached the top level class so
            // return an insertion point that has no containing class item and the current class.
            val containing = current.containingClass ?: return NewClassInsertionPoint(current, null)

            // If the containing class has a matching class item then return an insertion point that
            // uses that containing class item and the current class.
            findClass(containing)?.let { containingClassItem ->
                return NewClassInsertionPoint(current, containingClassItem)
            }
            current = containing
        } while (true)
    }

    internal fun findOrCreateClass(psiClass: PsiClass): ClassItem {
        if (psiClass is PsiTypeParameter) {
            error(
                "Must not be called with PsiTypeParameter; call findOrCreateTypeParameter(...) instead"
            )
        }

        // If it has already been created then return it.
        findClass(psiClass)?.let {
            return it
        }

        // Otherwise, find an insertion point at which new classes should be created.
        val (missingPsiClass, containingClassItem) = findNewClassInsertionPoint(psiClass)

        // Create a top level or nested class as appropriate.
        val createdClassItem =
            if (containingClassItem == null) {
                createTopLevelClassAndContents(missingPsiClass, isFromClassPath = true)
            } else {
                createClass(
                    missingPsiClass,
                    containingClassItem,
                    globalTypeItemFactory.from(containingClassItem),
                    isFromClassPath = containingClassItem.isFromClassPath(),
                )
            }

        // Select the class item to return.
        return if (missingPsiClass == psiClass) {
            // The created class item was what was requested so just return it.
            createdClassItem
        } else {
            // Otherwise, a nested class was requested so find it. It was created when its
            // containing class was created.
            findClass(psiClass)!!
        }
    }

    private fun getPackageName(clz: PsiClass): String {
        var top: PsiClass? = clz
        while (top?.containingClass != null) {
            top = top.containingClass
        }
        top ?: return ""

        val name = top.name
        val fullName = top.qualifiedName ?: return ""

        if (name == fullName) {
            return ""
        }

        return fullName.substring(0, fullName.length - 1 - name!!.length)
    }

    internal fun findCallableByPsiMethod(method: PsiMethod): PsiCallableItem {
        val containingClass = method.containingClass
        val cls = findOrCreateClass(containingClass!!)

        // Ensure initialized/registered via [#registerMethods]
        if (methodMap[cls] == null) {
            val map = HashMap<PsiMethod, PsiCallableItem>(40)
            registerCallablesByPsiMethod(cls.methods(), map)
            registerCallablesByPsiMethod(cls.constructors(), map)
            methodMap[cls] = map
        }

        val methods = methodMap[cls]!!
        val methodItem = methods[method]
        if (methodItem == null) {
            // Probably switched psi classes (e.g. used source PsiClass in registry but found
            // duplicate class in .jar library, and we're now pointing to it; in that case, find the
            // equivalent method by signature
            val psiClass = (cls as PsiClassItem).psiClass
            val updatedMethod = psiClass.findMethodBySignature(method, true)
            val result = methods[updatedMethod!!]
            if (result == null) {
                val extra =
                    PsiMethodItem.create(this, cls, updatedMethod, globalTypeItemFactory.from(cls))
                methods[method] = extra
                methods[updatedMethod] = extra

                return extra
            }
            return result
        }

        return methodItem
    }

    internal fun findField(field: PsiField): FieldItem? {
        val containingClass = field.containingClass ?: return null
        val cls = findOrCreateClass(containingClass)
        return cls.findField(field.name)
    }

    private fun registerCallablesByPsiMethod(
        callables: List<CallableItem>,
        map: MutableMap<PsiMethod, PsiCallableItem>
    ) {
        for (callable in callables) {
            val psiMethod = (callable as PsiCallableItem).psiMethod
            map[psiMethod] = callable
            if (psiMethod is UMethod) {
                // Register LC method as a key too
                // so that we can find the corresponding [CallableItem]
                // Otherwise, we will end up creating a new [CallableItem]
                // without source PSI, resulting in wrong modifier.
                map[psiMethod.javaPsi] = callable
            }
        }
    }

    override fun isFromClassPath() = fromClasspath

    override fun createAnnotation(source: String, context: Item?) =
        psiAssembler.createAnnotation(source, context)
}
