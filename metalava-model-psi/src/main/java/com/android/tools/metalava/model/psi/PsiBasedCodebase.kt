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

import com.android.SdkConstants
import com.android.tools.lint.UastEnvironment
import com.android.tools.metalava.model.ANDROIDX_NONNULL
import com.android.tools.metalava.model.ANDROIDX_NULLABLE
import com.android.tools.metalava.model.AnnotationManager
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.DefaultCodebase
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.PackageList
import com.android.tools.metalava.model.TypeParameterItem
import com.android.tools.metalava.model.TypeUse
import com.android.tools.metalava.model.source.SourceCodebase
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Reporter
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
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiImportStatement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiPackage
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.TypeAnnotationProvider
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
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.kotlin.BaseKotlinUastResolveProviderService
import org.jetbrains.uast.kotlin.isKotlin

const val PACKAGE_ESTIMATE = 500
const val CLASS_ESTIMATE = 15000
const val METHOD_ESTIMATE = 1000

/**
 * A codebase containing Java, Kotlin, or UAST PSI classes
 *
 * After creation, a list of PSI file is passed to [initializeFromSources] or a JAR file is passed
 * to [initializeFromJar]. This creates package and class items along with their members. This
 * process is broken into two phases:
 *
 * First, [initializing] is set to true, and class items are created from the supplied sources. If
 * [fromClasspath] is false, these are main classes of the codebase and have [ClassItem.emit] set to
 * true and [ClassItem.isFromClassPath] set to false. While creating these, package names are
 * reserved and associated with their classes in [packageClasses].
 *
 * If [fromClasspath] is true, all classes are assumed to be from the classpath, so [ClassItem.emit]
 * is set to false and [ClassItem.isFromClassPath] is set to true for all classes created.
 *
 * Next, package items are created for source classes based on the contents of [packageClasses] with
 * [PackageItem.emit] set to true.
 *
 * Then [initializing] is set to false and the second pass begins. This path iteratively resolves
 * supertypes of class items until all are fully resolved, creating new class and package items as
 * needed. Since all the source class and package items have been created, new items are assumed to
 * originate from the classpath and have [Item.emit] set to false and [Item.isFromClassPath] set to
 * true.
 */
open class PsiBasedCodebase(
    location: File,
    description: String = "Unknown",
    annotationManager: AnnotationManager,
    private val reporter: Reporter,
    val fromClasspath: Boolean = false
) : DefaultCodebase(location, description, false, annotationManager), SourceCodebase {
    private lateinit var uastEnvironment: UastEnvironment
    internal val project: Project
        get() = uastEnvironment.ideaProject

    /**
     * Returns the compilation units used in this codebase (may be empty when the codebase is not
     * loaded from source, such as from .jar files or from signature files)
     */
    internal var units: List<PsiFile> = emptyList()

    /**
     * Printer which can convert PSI, UAST and constants into source code, with ability to filter
     * out elements that are not part of a codebase etc
     */
    @Suppress("LeakingThis") internal val printer = CodePrinter(this, reporter)

    /** Supports fully qualifying Javadoc. */
    internal val docQualifier = DocQualifier(reporter)

    /** Map from class name to class item. Classes are added via [registerClass] */
    private val classMap: MutableMap<String, PsiClassItem> = HashMap(CLASS_ESTIMATE)

    /**
     * Map from classes to the set of methods for each (but only for classes where we've called
     * [findMethod]
     */
    private lateinit var methodMap: MutableMap<PsiClassItem, MutableMap<PsiMethod, PsiMethodItem>>

    /** Map from package name to the corresponding package item */
    private lateinit var packageMap: MutableMap<String, PsiPackageItem>

    /**
     * Map from package name to list of classes in that package. Initialized in [initializeFromJar]
     * and [initializeFromSources], updated by [registerPackageClass], and used and cleared in
     * [finishInitialization].
     */
    private var packageClasses: MutableMap<String, MutableList<PsiClassItem>>? = null

    /** A set of packages to hide */
    private lateinit var hiddenPackages: MutableMap<String, Boolean?>

    /**
     * A list of the top-level classes declared in the codebase's source (rather than on its
     * classpath).
     */
    private lateinit var topLevelClassesFromSource: MutableList<PsiClassItem>

    /**
     * Set to true in [initializeFromJar] and [initializeFromSources] for the first pass of creating
     * class items for all classes in the codebase sources and false for the second pass of creating
     * class items for the supertypes of the codebase classes. New class items created in the
     * supertypes pass must come from the classpath (dependencies) since all source classes have
     * been created.
     *
     * This information is used in [createClass] to set [ClassItem.emit] to true for source classes
     * and [ClassItem.isFromClassPath] to true for classpath classes. It is also used in
     * [registerPackage] to set [PackageItem.emit] to true for source packages.
     */
    private var initializing = false

    private var hideClassesFromJars = true

    private lateinit var emptyPackage: PsiPackageItem

    internal fun initializeFromSources(
        uastEnvironment: UastEnvironment,
        psiFiles: List<PsiFile>,
        packages: PackageDocs,
    ) {
        initializing = true
        this.units = psiFiles

        this.uastEnvironment = uastEnvironment
        // there are currently ~230 packages in the public SDK, but here we need to account for
        // internal ones too
        val hiddenPackages: MutableSet<String> = packages.hiddenPackages
        val packageDocs = packages.packageDocs
        this.hiddenPackages = HashMap(100)
        for (pkgName in hiddenPackages) {
            this.hiddenPackages[pkgName] = true
        }

        packageMap = HashMap(PACKAGE_ESTIMATE)
        packageClasses = HashMap(PACKAGE_ESTIMATE)
        packageClasses!![""] = ArrayList()
        this.methodMap = HashMap(METHOD_ESTIMATE)
        topLevelClassesFromSource = ArrayList(CLASS_ESTIMATE)

        // A set to track @JvmMultifileClasses that have already been added to
        // [topLevelClassesFromSource]
        val multifileClassNames = HashSet<FqName>()

        // Make sure we only process the files once; sometimes there's overlap in the source lists
        for (psiFile in psiFiles.asSequence().distinct()) {
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

            var classes = (psiFile as? PsiClassOwner)?.classes?.toList() ?: emptyList()
            if (classes.isEmpty()) {
                val uFile =
                    UastFacade.convertElementWithParent(psiFile, UFile::class.java) as? UFile?
                classes = uFile?.classes?.map { it }?.toList() ?: emptyList()
            }
            when {
                classes.isEmpty() && psiFile is PsiJavaFile -> {
                    // package-info.java ?
                    val packageStatement = psiFile.packageStatement
                    // Look for javadoc on the package statement; this is NOT handed to us on
                    // the PsiPackage!
                    if (packageStatement != null) {
                        val comment =
                            PsiTreeUtil.getPrevSiblingOfType(
                                packageStatement,
                                PsiDocComment::class.java
                            )
                        if (comment != null) {
                            val packageName = packageStatement.packageName
                            val text = comment.text
                            if (text.contains("@hide")) {
                                this.hiddenPackages[packageName] = true
                            }
                            if (packageDocs[packageName] != null) {
                                reporter.report(
                                    Issues.BOTH_PACKAGE_INFO_AND_HTML,
                                    psiFile,
                                    "It is illegal to provide both a package-info.java file and " +
                                        "a package.html file for the same package"
                                )
                            }
                            packageDocs[packageName] = text
                        }
                    }
                }
                else -> {
                    for (psiClass in classes) {
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

                        // Multifile classes appear identically from each file they're defined in,
                        // don't add duplicates
                        val ktLightClass = (psiClass as? UClass)?.javaPsi as? KtLightClassForFacade
                        if (ktLightClass?.multiFileClass == true) {
                            if (multifileClassNames.contains(ktLightClass.facadeClassFqName)) {
                                continue
                            } else {
                                multifileClassNames.add(ktLightClass.facadeClassFqName)
                            }
                        }
                        topLevelClassesFromSource += createClass(psiClass)
                    }
                }
            }
        }

        finishInitialization(packages)
    }

    /**
     * Finish initializing a [PsiClassItem] by calling [PsiClassItem.finishedInitialization].
     *
     * This must only be called when [initializing] is `false`.
     *
     * It will invoke [PsiClassItem.finishInitialization] immediately and add it directly to the
     * [PsiPackageItem].
     */
    private fun finishClassInitialization(classItem: PsiClassItem) {
        if (initializing) {
            error("incorrectly called on $classItem when initializing=`true`")
        }

        classItem.finishInitialization()
        val pkgName = getPackageName(classItem.psiClass)
        val pkg = findPackage(pkgName)
        if (pkg == null) {
            val psiPackage = findPsiPackage(pkgName)
            if (psiPackage != null) {
                val packageItem = registerPackage(pkgName, psiPackage, null)
                packageItem.addClass(classItem)
            }
        } else {
            pkg.addClass(classItem)
        }
    }

    /**
     * Finish initialising this codebase.
     *
     * Involves:
     * * Constructing packages, setting [emptyPackage].
     * * Finalizing [PsiClassItem]s which may involve creating some more, e.g. super classes and
     *   interfaces referenced from the source code but provided on the class path.
     */
    internal fun finishInitialization(packages: PackageDocs?) {

        // Next construct packages
        val packageDocs = packages?.packageDocs ?: emptyMap()
        val overviewDocs = packages?.overviewDocs ?: emptyMap()
        for ((pkgName, classes) in packageClasses!!) {
            val psiPackage = findPsiPackage(pkgName)
            if (psiPackage == null) {
                println("Could not find package $pkgName")
                continue
            }

            val sortedClasses = classes.toMutableList().sortedWith(ClassItem.fullNameComparator)
            registerPackage(
                pkgName,
                psiPackage,
                sortedClasses,
                packageDocs[pkgName],
                overviewDocs[pkgName],
            )
        }

        // Not used after this point.
        packageClasses = null

        initializing = false

        emptyPackage = findPackage("")!!

        // Finish initialization
        // Take a copy of the list just in case additional classes are added during iteration that
        // require additional packages to be added. Those classes will have their
        // [PsiClassItem.finishInitialization] called so there is no need to handle them here.
        val initialPackages = ArrayList(packageMap.values)
        for (pkg in initialPackages) {
            pkg.finishInitialization()
        }

        // Point to "parent" packages, since doclava treats packages as nested (e.g. an @hide on
        // android.foo will also apply to android.foo.bar)
        addParentPackages(packageMap.values)
    }

    override fun dispose() {
        uastEnvironment.dispose()
        super.dispose()
    }

    private fun addParentPackages(packages: Collection<PsiPackageItem>) {
        val missingPackages =
            packages
                .mapNotNull {
                    val name = it.qualifiedName()
                    val index = name.lastIndexOf('.')
                    val parent =
                        if (index != -1) {
                            name.substring(0, index)
                        } else {
                            ""
                        }
                    if (packageMap.containsKey(parent)) {
                        // Already registered
                        null
                    } else {
                        parent
                    }
                }
                .toSet()

        // Create PackageItems for any packages that weren't in the source
        for (pkgName in missingPackages) {
            val psiPackage = findPsiPackage(pkgName) ?: continue
            val sortedClasses = emptyList<PsiClassItem>()
            registerPackage(pkgName, psiPackage, sortedClasses)
        }

        // Connect up all the package items
        for (pkg in packageMap.values) {
            var name = pkg.qualifiedName()
            // Find parent package; we have to loop since we don't always find a PSI package
            // for intermediate elements; e.g. we may jump from java.lang straight up to the default
            // package
            while (name.isNotEmpty()) {
                val index = name.lastIndexOf('.')
                name =
                    if (index != -1) {
                        name.substring(0, index)
                    } else {
                        ""
                    }
                val parent = findPackage(name) ?: continue
                pkg.containingPackageField = parent
                break
            }
        }
    }

    private fun registerPackage(
        pkgName: String,
        psiPackage: PsiPackage,
        sortedClasses: List<PsiClassItem>?,
        packageHtml: String? = null,
        overviewHtml: String? = null,
    ): PsiPackageItem {
        val packageItem =
            PsiPackageItem.create(
                this,
                psiPackage,
                packageHtml,
                overviewHtml,
                fromClassPath = fromClasspath || !initializing
            )
        packageItem.emit = !packageItem.isFromClassPath()

        packageMap[pkgName] = packageItem
        if (isPackageHidden(pkgName)) {
            packageItem.hidden = true
        }

        sortedClasses?.let { packageItem.addClasses(it) }
        return packageItem
    }

    internal fun initializeFromJar(
        uastEnvironment: UastEnvironment,
        jarFile: File,
        preFiltered: Boolean = false,
    ) {
        this.preFiltered = preFiltered
        initializing = true
        hideClassesFromJars = false

        this.uastEnvironment = uastEnvironment

        // Find all classes referenced from the class
        val facade = JavaPsiFacade.getInstance(project)
        val scope = GlobalSearchScope.allScope(project)

        hiddenPackages = HashMap(100)
        packageMap = HashMap(PACKAGE_ESTIMATE)
        packageClasses = HashMap(PACKAGE_ESTIMATE)
        packageClasses!![""] = ArrayList()
        this.methodMap = HashMap(1000)
        val packageToClasses: MutableMap<String, MutableList<PsiClassItem>> =
            HashMap(PACKAGE_ESTIMATE)
        packageToClasses[""] = ArrayList() // ensure we construct one for the default package

        topLevelClassesFromSource = ArrayList(CLASS_ESTIMATE)

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
                    if (fileName.endsWith(SdkConstants.DOT_CLASS)) {
                        val qualifiedName =
                            fileName.removeSuffix(SdkConstants.DOT_CLASS).replace('/', '.')
                        if (qualifiedName.endsWith(".package-info")) {
                            // Ensure we register a package for this, even if empty
                            val packageName = qualifiedName.removeSuffix(".package-info")
                            var list = packageToClasses[packageName]
                            if (list == null) {
                                list = mutableListOf()
                                packageToClasses[packageName] = list
                            }
                            continue
                        } else {
                            val psiClass = facade.findClass(qualifiedName, scope) ?: continue

                            val classItem = createClass(psiClass)
                            topLevelClassesFromSource.add(classItem)

                            val packageName = getPackageName(psiClass)
                            var list = packageToClasses[packageName]
                            if (list == null) {
                                list = mutableListOf(classItem)
                                packageToClasses[packageName] = list
                            } else {
                                list.add(classItem)
                            }
                        }
                    }
                }
            }
        } catch (e: IOException) {
            reporter.report(Issues.IO_ERROR, jarFile, e.message ?: e.toString())
        }

        hideClassesFromJars = true

        // When loading from a jar there is no package documentation.
        finishInitialization(null)
    }

    private fun registerPackageClass(packageName: String, cls: PsiClassItem) {
        var list = packageClasses!![packageName]
        if (list == null) {
            list = ArrayList()
            packageClasses!![packageName] = list
        }

        list.add(cls)
    }

    private fun isPackageHidden(packageName: String): Boolean {
        val hidden = hiddenPackages[packageName]
        if (hidden == true) {
            return true
        } else if (hidden == null) {
            // Compute for all prefixes of this package
            var pkg = packageName
            while (true) {
                if (hiddenPackages[pkg] != null) {
                    hiddenPackages[packageName] = hiddenPackages[pkg]
                    if (hiddenPackages[pkg] == true) {
                        return true
                    }
                }
                val last = pkg.lastIndexOf('.')
                if (last == -1) {
                    hiddenPackages[packageName] = false
                    break
                } else {
                    pkg = pkg.substring(0, last)
                }
            }
        }

        return false
    }

    private fun createClass(clz: PsiClass): PsiClassItem {
        // If initializing is true, this class is from source
        val classItem =
            PsiClassItem.create(this, clz, fromClassPath = fromClasspath || !initializing)
        // Set emit to true for source classes but false for classpath classes
        classItem.emit = !classItem.isFromClassPath()

        if (!initializing) {
            // Workaround: we're pulling in .aidl files from .jar files. These are
            // marked @hide, but since we only see the .class files we don't know that.
            if (
                classItem.simpleName().startsWith("I") &&
                    classItem.isFromClassPath() &&
                    clz.interfaces.any { it.qualifiedName == "android.os.IInterface" }
            ) {
                classItem.hidden = true
            }
        }

        if (initializing) {
            // If initializing then keep track of the class in [packageClasses]. This is not needed
            // after initializing as [packageClasses] is not needed then.
            // TODO: Cache for adjacent files!
            val packageName = getPackageName(clz)
            registerPackageClass(packageName, classItem)
        } else {
            finishClassInitialization(classItem)
        }

        return classItem
    }

    override fun getPackages(): PackageList {
        // TODO: Sorting is probably not necessary here!
        return PackageList(
            this,
            packageMap.values.toMutableList().sortedWith(PackageItem.comparator)
        )
    }

    override fun size(): Int {
        return packageMap.size
    }

    override fun findPackage(pkgName: String): PsiPackageItem? {
        return packageMap[pkgName]
    }

    internal fun findPsiPackage(pkgName: String): PsiPackage? {
        return JavaPsiFacade.getInstance(project).findPackage(pkgName)
    }

    override fun findClass(className: String): PsiClassItem? {
        return classMap[className]
    }

    override fun resolveClass(className: String): ClassItem? = findOrCreateClass(className)

    open fun findClass(psiClass: PsiClass): PsiClassItem? {
        val qualifiedName: String = psiClass.qualifiedName ?: psiClass.name!!
        return classMap[qualifiedName]
    }

    internal fun findOrCreateClass(qualifiedName: String): PsiClassItem? {
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

    internal fun findOrCreateClass(psiClass: PsiClass): PsiClassItem {
        if (psiClass is PsiTypeParameter) {
            error(
                "Must not be called with PsiTypeParameter; call findOrCreateTypeParameter(...) instead"
            )
        }

        val existing = findClass(psiClass)
        if (existing != null) {
            return existing
        }

        var curr = psiClass.containingClass
        if (curr != null && findClass(curr) == null) {
            // Make sure we construct outer/top level classes first
            if (findClass(curr) == null) {
                while (true) {
                    val containing = curr?.containingClass
                    if (containing == null) {
                        break
                    } else {
                        curr = containing
                    }
                }
                curr!!
                createClass(
                    curr
                ) // this will also create inner classes, which should now be in the map
                val inner = findClass(psiClass)
                inner!! // should be there now
                return inner
            }
        }

        return existing ?: return createClass(psiClass)
    }

    internal fun findClass(psiType: PsiType): PsiClassItem? {
        if (psiType is PsiClassType) {
            val cls = psiType.resolve() ?: return null
            return findOrCreateClass(cls)
        } else if (psiType is PsiArrayType) {
            var componentType = psiType.componentType
            // We repeatedly get the component type because the array may have multiple dimensions
            while (componentType is PsiArrayType) {
                componentType = componentType.componentType
            }
            if (componentType is PsiClassType) {
                val cls = componentType.resolve() ?: return null
                return findOrCreateClass(cls)
            }
        }
        return null
    }

    /**
     * Find a [PsiTypeParameterItem] representing [PsiTypeParameter].
     *
     * The corresponding [TypeParameterItem] must always exist, otherwise the source code has
     * serious syntactic and/or semantic errors.
     */
    internal fun findTypeParameter(psiTypeParameter: PsiTypeParameter): TypeParameterItem {
        // Find the [TypeParameterListOwner] of the type parameter by searching for the
        // [MethodItem]/[ClassItem] corresponding to the underlying [PsiTypeParameter]'s owner.
        val psiOwner = psiTypeParameter.owner
        val typeParameterListOwner =
            when (psiOwner) {
                is PsiMethod -> findMethod(psiOwner)
                is PsiClass -> findClass(psiOwner)
                else -> null
            }
                ?: error("Could not find or recognize owner $psiOwner")

        // Search through the owner's [TypeParameterList] to find the parameter with the matching
        // name and return that.
        val typeParameterList = typeParameterListOwner.typeParameterList()
        val name = psiTypeParameter.name
        return typeParameterList.typeParameters().firstOrNull { it.name() == name }
            ?: error(
                "Could not find type parameter $name in $typeParameterList of $typeParameterListOwner"
            )
    }

    internal fun getClassType(cls: PsiClass): PsiClassType =
        getFactory().createType(cls, PsiSubstitutor.EMPTY)

    internal fun getComment(string: String, parent: PsiElement? = null): PsiDocComment =
        getFactory().createDocCommentFromText(string, parent)

    /**
     * Creates a [PsiClassTypeItem] that is suitable for use as a super type, e.g. in an `extends`
     * or `implements` list.
     */
    internal fun getSuperType(psiType: PsiType): PsiClassTypeItem {
        return getType(psiType, typeUse = TypeUse.SUPER_TYPE) as PsiClassTypeItem
    }

    /**
     * Returns a [PsiTypeItem] representing the [psiType]. The [context] is used to get nullability
     * information for Kotlin types.
     */
    internal fun getType(
        psiType: PsiType,
        context: PsiElement? = null,
        typeUse: TypeUse = TypeUse.GENERAL
    ): PsiTypeItem {
        val kotlinTypeInfo =
            if (context != null && isKotlin(context)) {
                KotlinTypeInfo.fromContext(context)
            } else {
                null
            }

        // Note: We do *not* cache these; it turns out that storing PsiType instances
        // in a map is bad for performance; it has a very expensive equals operation
        // for some type comparisons (and we sometimes end up with unexpected results,
        // e.g. where we fetch an "equals" type from the map but its representation
        // is slightly different than we intended
        return PsiTypeItem.create(this, psiType, kotlinTypeInfo, typeUse)
    }

    internal fun getType(psiClass: PsiClass): PsiTypeItem {
        // Create a PsiType for the class. Specifies `PsiSubstitutor.EMPTY` so that if the class has
        // any type parameters then the PsiType will include references to those parameters.
        val psiTypeWithTypeParametersIfAny = getFactory().createType(psiClass, PsiSubstitutor.EMPTY)
        return PsiTypeItem.create(this, psiTypeWithTypeParametersIfAny, null)
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

    internal fun findMethod(method: PsiMethod): PsiMethodItem {
        val containingClass = method.containingClass
        val cls = findOrCreateClass(containingClass!!)

        // Ensure initialized/registered via [#registerMethods]
        if (methodMap[cls] == null) {
            val map = HashMap<PsiMethod, PsiMethodItem>(40)
            registerMethods(cls.methods(), map)
            registerMethods(cls.constructors(), map)
            methodMap[cls] = map
        }

        val methods = methodMap[cls]!!
        val methodItem = methods[method]
        if (methodItem == null) {
            // Probably switched psi classes (e.g. used source PsiClass in registry but
            // found duplicate class in .jar library and we're now pointing to it; in that
            // case, find the equivalent method by signature
            val psiClass = cls.psiClass
            val updatedMethod = psiClass.findMethodBySignature(method, true)
            val result = methods[updatedMethod!!]
            if (result == null) {
                val extra = PsiMethodItem.create(this, cls, updatedMethod)
                methods[method] = extra
                methods[updatedMethod] = extra
                if (!initializing) {
                    extra.finishInitialization()
                }

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

    private fun registerMethods(
        methods: List<MethodItem>,
        map: MutableMap<PsiMethod, PsiMethodItem>
    ) {
        for (method in methods) {
            val psiMethod = (method as PsiMethodItem).psiMethod
            map[psiMethod] = method
            if (psiMethod is UMethod) {
                // Register LC method as a key too
                // so that we can find the corresponding [MethodItem]
                // Otherwise, we will end up creating a new [MethodItem]
                // without source PSI, resulting in wrong modifier.
                map[psiMethod.javaPsi] = method
            }
        }
    }

    override fun getTopLevelClassesFromSource(): List<ClassItem> {
        return topLevelClassesFromSource
    }

    internal fun createPsiMethod(s: String, parent: PsiElement? = null): PsiMethod =
        getFactory().createMethodFromText(s, parent)

    internal fun createPsiType(s: String, parent: PsiElement? = null): PsiType =
        getFactory().createTypeFromText(s, parent)

    private fun createPsiAnnotation(s: String, parent: PsiElement? = null): PsiAnnotation =
        getFactory().createAnnotationFromText(s, parent)

    private fun getFactory() = JavaPsiFacade.getElementFactory(project)

    private var nonNullAnnotationProvider: TypeAnnotationProvider? = null
    private var nullableAnnotationProvider: TypeAnnotationProvider? = null

    /** Type annotation provider which provides androidx.annotation.NonNull */
    internal fun getNonNullAnnotationProvider(): TypeAnnotationProvider {
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
    internal fun getNullableAnnotationProvider(): TypeAnnotationProvider {
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

    override fun createAnnotation(
        source: String,
        context: Item?,
    ): PsiAnnotationItem {
        val psiAnnotation = createPsiAnnotation(source, (context as? PsiItem)?.psi())
        return PsiAnnotationItem.create(this, psiAnnotation)
    }

    override fun supportsDocumentation(): Boolean = true

    override fun toString(): String = description

    /** Add a class to the codebase. Called from [PsiClassItem.create]. */
    internal fun registerClass(classItem: PsiClassItem) {
        val qualifiedName = classItem.qualifiedName()
        val existing = classMap.put(qualifiedName, classItem)
        if (existing != null) {
            error("Attempted to register $qualifiedName twice, $classItem and $existing")
        }

        classMap[qualifiedName] = classItem
    }

    internal val uastResolveService: BaseKotlinUastResolveProviderService? by lazy {
        ApplicationManager.getApplication()
            .getService(BaseKotlinUastResolveProviderService::class.java)
    }
}
