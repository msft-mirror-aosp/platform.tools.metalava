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
import com.android.tools.lint.annotations.Extractor
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.JAVA_PACKAGE_INFO
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.item.CodebaseAssembler
import com.android.tools.metalava.model.item.DefaultPackageItem
import com.android.tools.metalava.model.item.MutablePackageDoc
import com.android.tools.metalava.model.item.PackageDoc
import com.android.tools.metalava.model.item.PackageDocs
import com.android.tools.metalava.model.source.SourceSet
import com.android.tools.metalava.model.source.utils.gatherPackageJavadoc
import com.android.tools.metalava.reporter.Issues
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.JavaRecursiveElementVisitor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiCodeBlock
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiImportStatement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.file.PsiPackageImpl
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UastFacade

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
        // Extract the list of class names from the jar file.
        val classNames = buildList {
            try {
                ZipFile(jarFile).use { jar ->
                    for (entry in jar.entries().iterator()) {
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

                        add(qualifiedName)
                    }
                }
            } catch (e: IOException) {
                reporter.report(Issues.IO_ERROR, jarFile, e.message ?: e.toString())
            }
        }

        // Create the initial set of packages that were found in the jar files. When loading from a
        // jar there is no package documentation so this will only create the root package.
        codebase.packageTracker.createInitialPackages(PackageDocs.EMPTY)

        // Find all classes referenced from the class
        val facade = JavaPsiFacade.getInstance(project)
        val scope = GlobalSearchScope.allScope(project)

        val isFromClassPath = codebase.isFromClassPath()
        for (className in classNames) {
            val psiClass = facade.findClass(className, scope) ?: continue

            val classItem = codebase.createTopLevelClassAndContents(psiClass, isFromClassPath)
            codebase.addTopLevelClassFromSource(classItem)
        }
    }

    internal fun initializeFromSources(sourceSet: SourceSet) {
        // Get the list of `PsiFile`s from the `SourceSet`.
        val psiFiles = Extractor.createUnitsForFiles(uastEnvironment.ideaProject, sourceSet.sources)

        // Split the `PsiFile`s into `PsiClass`es and `package-info.java` `PsiJavaFile`s.
        val (packageInfoFiles, psiClasses) = splitPsiFilesIntoClassesAndPackageInfoFiles(psiFiles)

        // Gather all package related javadoc.
        val packageDocs =
            gatherPackageJavadoc(
                reporter,
                sourceSet,
                packageNameFilter = { codebase.findPsiPackage(it) != null },
                packageInfoFiles,
                packageInfoDocExtractor = { getOptionalPackageDocFromPackageInfoFile(it) },
            )

        // Create the initial set of packages that were found in the source files.
        codebase.packageTracker.createInitialPackages(packageDocs)

        // Process the `PsiClass`es.
        val isFromClassPath = codebase.isFromClassPath()
        for (psiClass in psiClasses) {
            val classItem = codebase.createTopLevelClassAndContents(psiClass, isFromClassPath)
            codebase.addTopLevelClassFromSource(classItem)
        }
    }

    /**
     * Split the [psiFiles] into separate `package-info.java` [PsiJavaFile]s and [PsiClass]es.
     *
     * During the processing this checks each [PsiFile] for unresolved imports and each [PsiClass]
     * for syntax errors.
     */
    private fun splitPsiFilesIntoClassesAndPackageInfoFiles(
        psiFiles: List<PsiFile>
    ): Pair<List<PsiJavaFile>, List<PsiClass>> {
        // A set to track `@JvmMultifileClass`es that have already been added to psiClasses.
        val multiFileClassNames = HashSet<FqName>()

        val psiClasses = mutableListOf<PsiClass>()
        val packageInfoFiles = mutableListOf<PsiJavaFile>()

        // Make sure we only process the files once; sometimes there's overlap in the source lists
        for (psiFile in psiFiles.asSequence().distinct()) {
            checkForUnresolvedImports(psiFile)

            val classes = getPsiClassesFromPsiFile(psiFile)
            when {
                classes.isEmpty() && psiFile is PsiJavaFile -> {
                    if (psiFile.name == JAVA_PACKAGE_INFO) {
                        packageInfoFiles.add(psiFile)
                    }
                }
                else -> {
                    for (psiClass in classes) {
                        checkForSyntaxErrors(psiClass)

                        // Multi file classes appear identically from each file they're defined in,
                        // don't add duplicates
                        val multiFileClassName = getOptionalMultiFileClassName(psiClass)
                        if (multiFileClassName != null) {
                            if (multiFileClassName in multiFileClassNames) {
                                continue
                            } else {
                                multiFileClassNames.add(multiFileClassName)
                            }
                        }

                        psiClasses.add(psiClass)
                    }
                }
            }
        }
        return Pair(packageInfoFiles, psiClasses)
    }

    /** Check to see if [psiFile] contains any unresolved imports. */
    private fun checkForUnresolvedImports(psiFile: PsiFile?) {
        // Visiting psiFile directly would eagerly load the entire file even though we only need
        // the importList here.
        (psiFile as? PsiJavaFile)
            ?.importList
            ?.accept(
                object : JavaRecursiveElementVisitor() {
                    override fun visitImportStatement(element: PsiImportStatement) {
                        super.visitImportStatement(element)
                        if (element.resolve() == null) {
                            reporter.report(
                                Issues.UNRESOLVED_IMPORT,
                                element,
                                "Unresolved import: `${element.qualifiedName}`"
                            )
                        }
                    }
                }
            )
    }

    /** Get, the possibly empty, list of [PsiClass]es from the [psiFile]. */
    private fun getPsiClassesFromPsiFile(psiFile: PsiFile): List<PsiClass> {
        // First, check for Java classes, return any that are found.
        (psiFile as? PsiClassOwner)?.classes?.toList()?.let { if (it.isNotEmpty()) return it }

        // Then, check for Kotlin classes, returning any that are found, or an empty list.
        val uFile = UastFacade.convertElementWithParent(psiFile, UFile::class.java) as? UFile?
        return uFile?.classes?.map { it }?.toList() ?: emptyList()
    }

    /**
     * Get the optional [MutablePackageDoc] from [psiFile].
     *
     * @param psiFile must be a `package-info.java` file.
     */
    private fun getOptionalPackageDocFromPackageInfoFile(psiFile: PsiJavaFile): MutablePackageDoc? {
        val packageStatement = psiFile.packageStatement ?: return null
        val packageName = packageStatement.packageName

        // Make sure that this is actually a package.
        codebase.findPsiPackage(packageName) ?: return null

        // Look for javadoc on the package statement; this is NOT handed to us on the PsiPackage!
        val comment = PsiTreeUtil.getPrevSiblingOfType(packageStatement, PsiDocComment::class.java)
        if (comment != null) {
            return MutablePackageDoc(
                qualifiedName = packageName,
                fileLocation = PsiFileLocation.fromPsiElement(psiFile),
                commentFactory =
                    PsiItemDocumentation.factory(packageStatement, codebase, comment.text),
            )
        }

        // No comment could be found.
        return null
    }

    /** Check the [psiClass] for any syntax errors. */
    private fun checkForSyntaxErrors(psiClass: PsiClass) {
        psiClass.accept(
            object : JavaRecursiveElementVisitor() {
                override fun visitErrorElement(element: PsiErrorElement) {
                    super.visitErrorElement(element)
                    reporter.report(
                        Issues.INVALID_SYNTAX,
                        element,
                        "Syntax error: `${element.errorDescription}`"
                    )
                }

                override fun visitCodeBlock(block: PsiCodeBlock) {
                    // Ignore to avoid eagerly parsing all method bodies.
                }

                override fun visitDocComment(comment: PsiDocComment) {
                    // Ignore to avoid eagerly parsing all doc comments.
                    // Doc comments cannot contain error elements.
                }
            }
        )
    }

    /** Get the optional multi file class name. */
    private fun getOptionalMultiFileClassName(psiClass: PsiClass): FqName? {
        val ktLightClass = (psiClass as? UClass)?.javaPsi as? KtLightClassForFacade
        val multiFileClassName =
            if (ktLightClass?.multiFileClass == true) {
                ktLightClass.facadeClassFqName
            } else {
                null
            }
        return multiFileClassName
    }
}
