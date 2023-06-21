/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.metalava.model.text

import com.android.tools.metalava.ApiType
import com.android.tools.metalava.CodebaseComparator
import com.android.tools.metalava.ComparisonVisitor
import com.android.tools.metalava.FileFormat
import com.android.tools.metalava.JAVA_LANG_ANNOTATION
import com.android.tools.metalava.JAVA_LANG_ENUM
import com.android.tools.metalava.JAVA_LANG_OBJECT
import com.android.tools.metalava.JAVA_LANG_THROWABLE
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.DefaultCodebase
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.PackageList
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.text.classpath.WrappedClassItem
import com.android.tools.metalava.model.visitors.ItemVisitor
import com.android.tools.metalava.model.visitors.TypeVisitor
import com.android.tools.metalava.options
import java.io.File
import java.util.ArrayList
import java.util.HashMap
import java.util.function.Predicate
import kotlin.math.min

// Copy of ApiInfo in doclava1 (converted to Kotlin + some cleanup to make it work with metalava's
// data structures.
// (Converted to Kotlin such that I can inherit behavior via interfaces, in particular Codebase.)
class TextCodebase(
    location: File,
    val apiClassResolution: ApiClassResolution = ApiClassResolution.API_CLASSPATH,
) : DefaultCodebase(location) {
    /**
     * Whether types should be interpreted to be in Kotlin format (e.g. ? suffix means nullable, !
     * suffix means unknown, and absence of a suffix means not nullable.
     */
    var kotlinStyleNulls = false

    private val mPackages = HashMap<String, TextPackageItem>(300)
    private val mAllClasses = HashMap<String, TextClassItem>(30000)
    private val mClassToSuper = HashMap<TextClassItem, String>(30000)
    private val mClassToInterface = HashMap<TextClassItem, ArrayList<String>>(10000)

    // Classes which are not part of the API surface but are referenced by other classes.
    // These are initialized as wrapped empty stubs, but may be switched out for PSI classes.
    val wrappedStubClasses = HashMap<String, WrappedClassItem>()

    /** True if [getOrCreateClass] should add [WrapperClassItem]s around unknown classes. */
    val addWrappersForUnknownClasses = apiClassResolution == ApiClassResolution.API_CLASSPATH

    override var description = "Codebase"
    override var preFiltered: Boolean = true

    override fun trustedApi(): Boolean = true

    /**
     * Signature file format version, if found. Type "GradleVersion" is misleading; it's just a
     * convenient version class.
     */
    var format: FileFormat = FileFormat.V1 // not specifying format: assumed to be doclava, 1.0

    override fun getPackages(): PackageList {
        val list = ArrayList<PackageItem>(mPackages.values)
        list.sortWith(PackageItem.comparator)
        return PackageList(this, list)
    }

    override fun size(): Int {
        return mPackages.size
    }

    override fun findClass(className: String): TextClassItem? {
        return mAllClasses[className]
    }

    private fun resolveInterfaces(all: List<TextClassItem>) {
        for (cl in all) {
            val interfaces = mClassToInterface[cl] ?: continue
            for (interfaceName in interfaces) {
                getOrCreateClass(interfaceName, isInterface = true)
                cl.addInterface(obtainTypeFromString(interfaceName))
            }
        }
    }

    override fun supportsDocumentation(): Boolean = false

    fun mapClassToSuper(classInfo: TextClassItem, superclass: String?) {
        superclass?.let { mClassToSuper.put(classInfo, superclass) }
    }

    fun mapClassToInterface(classInfo: TextClassItem, iface: String) {
        if (!mClassToInterface.containsKey(classInfo)) {
            mClassToInterface[classInfo] = ArrayList()
        }
        mClassToInterface[classInfo]?.let { if (!it.contains(iface)) it.add(iface) }
    }

    fun implementsInterface(classInfo: TextClassItem, iface: String): Boolean {
        return mClassToInterface[classInfo]?.contains(iface) ?: false
    }

    fun addPackage(pInfo: TextPackageItem) {
        // track the set of organized packages in the API
        mPackages[pInfo.name()] = pInfo

        // accumulate a direct map of all the classes in the API
        for (cl in pInfo.allClasses()) {
            mAllClasses[cl.qualifiedName()] = cl as TextClassItem
        }
    }

    private fun resolveSuperclasses(allClasses: List<TextClassItem>) {
        for (cl in allClasses) {
            // java.lang.Object has no superclass
            if (cl.isJavaLangObject()) {
                continue
            }
            var scName: String? = mClassToSuper[cl]
            if (scName == null) {
                scName =
                    when {
                        cl.isEnum() -> JAVA_LANG_ENUM
                        cl.isAnnotationType() -> JAVA_LANG_ANNOTATION
                        else -> {
                            val existing = cl.superClassType()?.toTypeString()
                            val s = existing ?: JAVA_LANG_OBJECT
                            s // unnecessary variable, works around current compiler believing the
                            // expression to be nullable
                        }
                    }
            }

            val superclass = getOrCreateClass(scName)
            cl.setSuperClass(superclass, obtainTypeFromString(scName))
        }
    }

    private fun resolveThrowsClasses(all: List<TextClassItem>) {
        for (cl in all) {
            for (methodItem in cl.constructors()) {
                resolveThrowsClasses(methodItem)
            }
            for (methodItem in cl.methods()) {
                resolveThrowsClasses(methodItem)
            }
        }
    }

    private fun resolveThrowsClasses(methodItem: MethodItem) {
        val methodInfo = methodItem as TextMethodItem
        val names = methodInfo.throwsTypeNames()
        if (names.isNotEmpty()) {
            val result = ArrayList<ClassItem>()
            for (exception in names) {
                var exceptionClass: ClassItem? = mAllClasses[exception]
                if (exceptionClass == null) {
                    // Exception not provided by this codebase. Inject a stub.
                    exceptionClass = getOrCreateClass(exception)
                    // Set super class to throwable?
                    if (exception != JAVA_LANG_THROWABLE) {
                        exceptionClass.setSuperClass(
                            getOrCreateClass(JAVA_LANG_THROWABLE),
                            TextTypeItem(this, JAVA_LANG_THROWABLE)
                        )
                    }
                }
                result.add(exceptionClass)
            }
            methodInfo.setThrowsList(result)
        }
    }

    private fun resolveInnerClasses(packages: List<TextPackageItem>) {
        for (pkg in packages) {
            // make copy: we'll be removing non-top level classes during iteration
            val classes = ArrayList(pkg.classList())
            for (cls in classes) {
                // WrappedClassItems which are inner classes are resolved when they're created
                if (cls is WrappedClassItem) continue
                val cl = cls as TextClassItem
                val name = cl.name
                var index = name.lastIndexOf('.')
                if (index != -1) {
                    cl.name = name.substring(index + 1)
                    val qualifiedName = cl.qualifiedName
                    index = qualifiedName.lastIndexOf('.')
                    assert(index != -1) { qualifiedName }
                    val outerClassName = qualifiedName.substring(0, index)
                    // If the outer class doesn't exist in the text codebase, it should not be
                    // resolved
                    // through the classpath--if it did exist there, this inner class would be
                    // overridden
                    // by the version from the classpath.
                    val outerClass = getOrCreateClass(outerClassName, canBeFromClasspath = false)
                    cl.containingClass = outerClass
                    outerClass.addInnerClass(cl)
                }
            }
        }

        for (pkg in packages) {
            pkg.pruneClassList()
        }
    }

    /**
     * Add abstract superclass abstract methods to non-abstract class when generating from-text
     * stubs. Iterate through the hierarchy and collect all super abstract methods that need to be
     * added. These are not included in the signature files but omitting these methods will lead to
     * compile error.
     */
    private fun resolveAbstractMethods(allClasses: List<TextClassItem>) {
        for (cl in allClasses) {
            // If class is interface, naively iterate through all parent class and interfaces
            // and resolve inheritance of override equivalent signatures
            // Find intersection of super class/interface default methods
            // Resolve conflict by adding signature
            // https://docs.oracle.com/javase/specs/jls/se8/html/jls-9.html#jls-9.4.1.3
            if (cl.isInterface()) {
                // We only need to track one method item(value) with the signature(key),
                // since the containing class does not matter if a method to be added is found
                // as method.duplicate(cl) sets containing class to cl.
                // Therefore, the value of methodMap can be overwritten.
                val methodMap = mutableMapOf<String, TextMethodItem>()
                val methodCount = mutableMapOf<String, Int>()
                val hasDefault = mutableMapOf<String, Boolean>()
                for (superInterfaceOrClass in cl.getParentAndInterfaces()) {
                    val methods = superInterfaceOrClass.methods().map { it as TextMethodItem }
                    for (method in methods) {
                        val signature = method.toSignatureString()
                        val isDefault = method.modifiers.isDefault()
                        val newCount = methodCount.getOrDefault(signature, 0) + 1
                        val newHasDefault = hasDefault.getOrDefault(signature, false) || isDefault

                        methodMap[signature] = method
                        methodCount[signature] = newCount
                        hasDefault[signature] = newHasDefault

                        // If the method has appeared more than once, there may be a potential
                        // conflict
                        // thus add the method to the interface
                        if (
                            newHasDefault &&
                                newCount == 2 &&
                                !cl.containsMethodInClassContext(method)
                        ) {
                            val m = method.duplicate(cl) as TextMethodItem
                            m.modifiers.setAbstract(true)
                            m.modifiers.setDefault(false)
                            cl.addMethod(m)
                        }
                    }
                }
            }

            // If class is a concrete class, iterate through all hierarchy and
            // find all missing abstract methods.
            // Only add methods that are not implemented in the hierarchy and not included
            else if (!cl.isAbstractClass() && !cl.isEnum()) {
                val superMethodsToBeOverridden = mutableListOf<TextMethodItem>()
                val hierarchyClassesList = cl.getAllSuperClassesAndInterfaces().toMutableList()
                while (hierarchyClassesList.isNotEmpty()) {
                    val ancestorClass = hierarchyClassesList.removeLast()
                    val abstractMethods =
                        ancestorClass.methods().filter { it.modifiers.isAbstract() }
                    for (method in abstractMethods) {
                        // We do not compare this against all ancestors of cl,
                        // because an abstract method cannot be overridden at its ancestor class.
                        // Thus, we compare against hierarchyClassesList.
                        if (
                            hierarchyClassesList.all { !it.containsMethodInClassContext(method) } &&
                                !cl.containsMethodInClassContext(method)
                        ) {
                            superMethodsToBeOverridden.add(method as TextMethodItem)
                        }
                    }
                }
                for (superMethod in superMethodsToBeOverridden) {
                    // MethodItem.duplicate() sets the containing class of
                    // the duplicated method item as the input parameter.
                    // Thus, the method items to be overridden are duplicated here after the
                    // ancestor classes iteration so that the method items are correctly compared.
                    val m = superMethod.duplicate(cl) as TextMethodItem
                    m.modifiers.setAbstract(false)
                    cl.addMethod(m)
                }
            }
        }
    }

    fun registerClass(cls: TextClassItem) {
        mAllClasses[cls.qualifiedName] = cls
    }

    /**
     * Tries to find [name] in [mAllClasses]. If not found, creates an empty stub class (or
     * interface, if [isInterface] is true). If [canBeFromClasspath] is true, creates a
     * [WrappedClassItem] with the stub class inside, which might later be switched out for a PSI
     * class. Otherwise, just uses the stub class.
     *
     * Initializes outer classes and packages for the created class as needed.
     */
    fun getOrCreateClass(
        name: String,
        isInterface: Boolean = false,
        canBeFromClasspath: Boolean = addWrappersForUnknownClasses,
    ): ClassItem {
        val erased = TextTypeItem.eraseTypeArguments(name)
        val cls = mAllClasses[erased] ?: wrappedStubClasses[erased]
        if (cls != null) {
            return cls
        }

        val stubClass =
            if (isInterface) {
                TextClassItem.createInterfaceStub(this, name)
            } else {
                TextClassItem.createClassStub(this, name)
            }

        // If needed, wrap the class. Add the new class to the appropriate set
        val newClass =
            if (canBeFromClasspath) {
                val wrappedClass = WrappedClassItem(stubClass)
                wrappedStubClasses[erased] = wrappedClass
                wrappedClass
            } else {
                mAllClasses[erased] = stubClass
                stubClass
            }
        newClass.emit = false

        val fullName = newClass.fullName()
        if (fullName.contains('.')) {
            // We created a new inner class stub. We need to fully initialize it with outer classes,
            // themselves
            // possibly stubs
            val outerName = erased.substring(0, erased.lastIndexOf('.'))
            val outerClass =
                getOrCreateClass(
                    outerName,
                    isInterface = false,
                    canBeFromClasspath = canBeFromClasspath
                )
            stubClass.containingClass = outerClass
            outerClass.addInnerClass(newClass)
        } else {
            // Add to package
            val endIndex = erased.lastIndexOf('.')
            val pkgPath = if (endIndex != -1) erased.substring(0, endIndex) else ""
            val pkg =
                findPackage(pkgPath)
                    ?: run {
                        val newPkg =
                            TextPackageItem(
                                this,
                                pkgPath,
                                TextModifiers(this, DefaultModifierList.PUBLIC),
                                SourcePositionInfo.UNKNOWN
                            )
                        addPackage(newPkg)
                        newPkg.emit = false
                        newPkg
                    }
            stubClass.setContainingPackage(pkg)
            pkg.addClass(newClass)
        }

        return newClass
    }

    fun postProcess() {
        val classes = mAllClasses.values.toList()
        val packages = mPackages.values.toList()
        resolveSuperclasses(classes)
        resolveInterfaces(classes)
        resolveThrowsClasses(classes)
        resolveInnerClasses(packages)

        // Add overridden methods to the codebase only when the codebase is generated
        // from text file passed via --source-files and it does not fallback to loading classes from
        // the classpath.
        if (apiClassResolution == ApiClassResolution.API && this.location in options.sources) {
            resolveAbstractMethods(classes)
        }
    }

    override fun findPackage(pkgName: String): TextPackageItem? {
        return mPackages[pkgName]
    }

    override fun accept(visitor: ItemVisitor) {
        getPackages().accept(visitor)
    }

    override fun acceptTypes(visitor: TypeVisitor) {
        getPackages().acceptTypes(visitor)
    }

    override fun compareWith(
        visitor: ComparisonVisitor,
        other: Codebase,
        filter: Predicate<Item>?
    ) {
        CodebaseComparator().compare(visitor, this, other, filter)
    }

    override fun createAnnotation(
        source: String,
        context: Item?,
        mapName: Boolean
    ): AnnotationItem {
        return TextBackedAnnotationItem(this, source, mapName)
    }

    override fun toString(): String {
        return description
    }

    override fun unsupported(desc: String?): Nothing {
        error(desc ?: "Not supported for a signature-file based codebase")
    }

    fun obtainTypeFromString(
        type: String,
        cl: TextClassItem,
        methodTypeParameterList: TypeParameterList
    ): TextTypeItem {
        if (TextTypeItem.isLikelyTypeParameter(type)) {
            val length = type.length
            var nameEnd = length
            for (i in 0 until length) {
                val c = type[i]
                if (c == '<' || c == '[' || c == '!' || c == '?') {
                    nameEnd = i
                    break
                }
            }
            val name =
                if (nameEnd == length) {
                    type
                } else {
                    type.substring(0, nameEnd)
                }

            val isMethodTypeVar = methodTypeParameterList.typeParameterNames().contains(name)
            val isClassTypeVar = cl.typeParameterList().typeParameterNames().contains(name)

            if (isMethodTypeVar || isClassTypeVar) {
                // Confirm that it's a type variable
                // If so, create type variable WITHOUT placing it into the
                // cache, since we can't cache these; they can have different
                // inherited bounds etc
                return TextTypeItem(this, type)
            }
        }

        return obtainTypeFromString(type)
    }

    companion object {
        fun computeDelta(baseFile: File, baseApi: Codebase, signatureApi: Codebase): TextCodebase {
            // Compute just the delta
            val delta = TextCodebase(baseFile)
            delta.description = "Delta between $baseApi and $signatureApi"

            CodebaseComparator()
                .compare(
                    object : ComparisonVisitor() {
                        override fun added(new: PackageItem) {
                            delta.addPackage(new as TextPackageItem)
                        }

                        override fun added(new: ClassItem) {
                            val pkg = getOrAddPackage(new.containingPackage().qualifiedName())
                            pkg.addClass(new as TextClassItem)
                        }

                        override fun added(new: ConstructorItem) {
                            val cls = getOrAddClass(new.containingClass())
                            cls.addConstructor(new as TextConstructorItem)
                        }

                        override fun added(new: MethodItem) {
                            val cls = getOrAddClass(new.containingClass())
                            cls.addMethod(new as TextMethodItem)
                        }

                        override fun added(new: FieldItem) {
                            val cls = getOrAddClass(new.containingClass())
                            cls.addField(new as TextFieldItem)
                        }

                        override fun added(new: PropertyItem) {
                            val cls = getOrAddClass(new.containingClass())
                            cls.addProperty(new as TextPropertyItem)
                        }

                        private fun getOrAddClass(fullClass: ClassItem): TextClassItem {
                            val cls = delta.findClass(fullClass.qualifiedName())
                            if (cls != null) {
                                return cls
                            }
                            val textClass = fullClass as TextClassItem
                            val newClass =
                                TextClassItem(
                                    delta,
                                    SourcePositionInfo.UNKNOWN,
                                    textClass.modifiers,
                                    textClass.isInterface(),
                                    textClass.isEnum(),
                                    textClass.isAnnotationType(),
                                    textClass.qualifiedName,
                                    textClass.qualifiedName,
                                    textClass.name,
                                    textClass.annotations
                                )
                            val pkg = getOrAddPackage(fullClass.containingPackage().qualifiedName())
                            pkg.addClass(newClass)
                            newClass.setContainingPackage(pkg)
                            delta.registerClass(newClass)
                            return newClass
                        }

                        private fun getOrAddPackage(pkgName: String): TextPackageItem {
                            val pkg = delta.findPackage(pkgName)
                            if (pkg != null) {
                                return pkg
                            }
                            val newPkg =
                                TextPackageItem(
                                    delta,
                                    pkgName,
                                    TextModifiers(delta, DefaultModifierList.PUBLIC),
                                    SourcePositionInfo.UNKNOWN
                                )
                            delta.addPackage(newPkg)
                            return newPkg
                        }
                    },
                    baseApi,
                    signatureApi,
                    ApiType.ALL.getReferenceFilter()
                )

            delta.postProcess()
            return delta
        }
    }

    // Copied from Converter:

    fun obtainTypeFromString(type: String): TextTypeItem {
        return mTypesFromString.obtain(type) as TextTypeItem
    }

    private val mTypesFromString =
        object : Cache(this) {
            override fun make(o: Any): Any {
                val name = o as String

                // Reverse effect of TypeItem.shortenTypes(...)
                if (implicitJavaLangType(name)) {
                    return TextTypeItem(codebase, "java.lang.$name")
                }

                return TextTypeItem(codebase, name)
            }

            private fun implicitJavaLangType(s: String): Boolean {
                if (s.length <= 1) {
                    return false // Usually a type variable
                }
                if (s[1] == '[') {
                    return false // Type variable plus array
                }

                val dotIndex = s.indexOf('.')
                val array = s.indexOf('[')
                val generics = s.indexOf('<')
                if (array == -1 && generics == -1) {
                    return dotIndex == -1 && !TextTypeItem.isPrimitive(s)
                }
                val typeEnd =
                    if (array != -1) {
                        if (generics != -1) {
                            min(array, generics)
                        } else {
                            array
                        }
                    } else {
                        generics
                    }

                // Allow dotted type in generic parameter, e.g. "Iterable<java.io.File>" -> return
                // true
                return (dotIndex == -1 || dotIndex > typeEnd) &&
                    !TextTypeItem.isPrimitive(s.substring(0, typeEnd).trim())
            }
        }

    private abstract class Cache(val codebase: TextCodebase) {

        protected var mCache = HashMap<Any, Any>()

        internal fun obtain(o: Any?): Any? {
            if (o == null) {
                return null
            }
            var r: Any? = mCache[o]
            if (r == null) {
                r = make(o)
                mCache[o] = r
            }
            return r
        }

        protected abstract fun make(o: Any): Any
    }
}
