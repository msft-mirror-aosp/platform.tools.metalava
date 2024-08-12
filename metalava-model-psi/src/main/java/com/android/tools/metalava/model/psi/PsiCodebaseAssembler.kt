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
import com.android.tools.metalava.model.ANDROIDX_NONNULL
import com.android.tools.metalava.model.ANDROIDX_NULLABLE
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.JAVA_PACKAGE_INFO
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.TypeParameterScope
import com.android.tools.metalava.model.item.CodebaseAssembler
import com.android.tools.metalava.model.item.DefaultPackageItem
import com.android.tools.metalava.model.item.MutablePackageDoc
import com.android.tools.metalava.model.item.PackageDoc
import com.android.tools.metalava.model.item.PackageDocs
import com.android.tools.metalava.model.source.SourceSet
import com.android.tools.metalava.model.source.utils.gatherPackageJavadoc
import com.android.tools.metalava.reporter.Issues
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.JavaRecursiveElementVisitor
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiCodeBlock
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiEllipsisType
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiImportStatement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiPackage
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.TypeAnnotationProvider
import com.intellij.psi.impl.file.PsiPackageImpl
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile
import org.jetbrains.kotlin.analysis.api.types.KtTypeNullability
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.kotlin.BaseKotlinUastResolveProviderService

internal class PsiCodebaseAssembler(
    private val uastEnvironment: UastEnvironment,
    codebaseFactory: (PsiCodebaseAssembler) -> PsiBasedCodebase
) : CodebaseAssembler {

    internal val codebase = codebaseFactory(this)

    internal val globalTypeItemFactory = PsiTypeItemFactory(this, TypeParameterScope.empty)

    internal val project: Project = uastEnvironment.ideaProject

    private val reporter
        get() = codebase.reporter

    fun dispose() {
        uastEnvironment.dispose()
    }

    private fun getFactory() = JavaPsiFacade.getElementFactory(project)

    internal fun getClassType(cls: PsiClass): PsiClassType =
        getFactory().createType(cls, PsiSubstitutor.EMPTY)

    internal fun getComment(documentation: String, parent: PsiElement? = null): PsiDocComment =
        getFactory().createDocCommentFromText(documentation, parent)

    internal fun createPsiType(s: String, parent: PsiElement? = null): PsiType =
        getFactory().createTypeFromText(s, parent)

    private fun createPsiAnnotation(s: String, parent: PsiElement? = null): PsiAnnotation =
        getFactory().createAnnotationFromText(s, parent)

    internal fun findPsiPackage(pkgName: String): PsiPackage? {
        return JavaPsiFacade.getInstance(project).findPackage(pkgName)
    }

    override fun createPackageItem(
        packageName: String,
        packageDoc: PackageDoc,
        containingPackage: PackageItem?
    ): DefaultPackageItem {
        val psiPackage =
            findPsiPackage(packageName)
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

    override fun createClassFromUnderlyingModel(qualifiedName: String) =
        findOrCreateClass(qualifiedName)

    /**
     * Create top level classes, their inner classes and all the other members.
     *
     * All the classes are registered by name and so can be found by [findOrCreateClass].
     */
    private fun createTopLevelClassAndContents(
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

        val packageItem = codebase.packageTracker.findOrCreatePackage(packageName)

        return PsiClassItem.create(
            codebase,
            psiClass,
            containingClassItem,
            packageItem,
            enclosingClassTypeItemFactory,
            isFromClassPath,
        )
    }

    private fun findOrCreateClass(qualifiedName: String): ClassItem? {
        // Check to see if the class has already been seen and if so return it immediately.
        codebase.findClass(qualifiedName)?.let {
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
     * Called when no [ClassItem] was found by [PsiBasedCodebase.findClass]`([PsiClass]) when called
     * on [psiClass].
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
            codebase.findClass(containing)?.let { containingClassItem ->
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
        codebase.findClass(psiClass)?.let {
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
            codebase.findClass(psiClass)!!
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

    internal fun createAnnotation(
        source: String,
        context: Item?,
    ): AnnotationItem? {
        val psiAnnotation = createPsiAnnotation(source, (context as? PsiItem)?.psi())
        return PsiAnnotationItem.create(codebase, psiAnnotation)
    }

    fun getPsiTypeForPsiParameter(psiParameter: PsiParameter): PsiType {
        // UAST workaround: nullity of element type in last `vararg` parameter's array type
        val psiType = psiParameter.type
        return if (
            psiParameter is UParameter &&
                psiParameter.sourcePsi is KtParameter &&
                psiParameter.isVarArgs && // last `vararg`
                psiType is PsiArrayType
        ) {
            val ktParameter = psiParameter.sourcePsi as KtParameter
            val annotationProvider =
                when (uastResolveService?.nullability(ktParameter)) {
                    KtTypeNullability.NON_NULLABLE -> getNonNullAnnotationProvider()
                    KtTypeNullability.NULLABLE -> getNullableAnnotationProvider()
                    else -> null
                }
            val annotatedType =
                if (annotationProvider != null) {
                    psiType.componentType.annotate(annotationProvider)
                } else {
                    psiType.componentType
                }
            PsiEllipsisType(annotatedType, annotatedType.annotationProvider)
        } else {
            psiType
        }
    }

    private val uastResolveService: BaseKotlinUastResolveProviderService? by lazy {
        ApplicationManager.getApplication()
            .getService(BaseKotlinUastResolveProviderService::class.java)
    }

    private var nonNullAnnotationProvider: TypeAnnotationProvider? = null
    private var nullableAnnotationProvider: TypeAnnotationProvider? = null

    /** Type annotation provider which provides androidx.annotation.NonNull */
    private fun getNonNullAnnotationProvider(): TypeAnnotationProvider {
        return nonNullAnnotationProvider
            ?: run {
                val provider =
                    TypeAnnotationProvider.Static.create(
                        arrayOf(createPsiAnnotation("@$ANDROIDX_NONNULL"))
                    )
                nonNullAnnotationProvider
                provider
            }
    }

    /** Type annotation provider which provides androidx.annotation.Nullable */
    private fun getNullableAnnotationProvider(): TypeAnnotationProvider {
        return nullableAnnotationProvider
            ?: run {
                val provider =
                    TypeAnnotationProvider.Static.create(
                        arrayOf(createPsiAnnotation("@$ANDROIDX_NULLABLE"))
                    )
                nullableAnnotationProvider
                provider
            }
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

            val classItem = createTopLevelClassAndContents(psiClass, isFromClassPath)
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
                packageNameFilter = { findPsiPackage(it) != null },
                packageInfoFiles,
                packageInfoDocExtractor = { getOptionalPackageDocFromPackageInfoFile(it) },
            )

        // Create the initial set of packages that were found in the source files.
        codebase.packageTracker.createInitialPackages(packageDocs)

        // Process the `PsiClass`es.
        val isFromClassPath = codebase.isFromClassPath()
        for (psiClass in psiClasses) {
            val classItem = createTopLevelClassAndContents(psiClass, isFromClassPath)
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
        findPsiPackage(packageName) ?: return null

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
