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

package com.android.tools.metalava.model.turbine

import com.android.tools.metalava.model.ANNOTATION_ATTR_VALUE
import com.android.tools.metalava.model.AnnotationAttribute
import com.android.tools.metalava.model.AnnotationAttributeValue
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.ArrayTypeItem
import com.android.tools.metalava.model.BaseItemVisitor
import com.android.tools.metalava.model.BoundsTypeItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.DefaultAnnotationArrayAttributeValue
import com.android.tools.metalava.model.DefaultAnnotationAttribute
import com.android.tools.metalava.model.DefaultAnnotationSingleAttributeValue
import com.android.tools.metalava.model.DefaultTypeParameterList
import com.android.tools.metalava.model.ExceptionTypeItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PrimitiveTypeItem.Primitive
import com.android.tools.metalava.model.ReferenceTypeItem
import com.android.tools.metalava.model.ThrowableType
import com.android.tools.metalava.model.TypeArgumentTypeItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeModifiers
import com.android.tools.metalava.model.TypeNullability
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.TypeParameterScope
import com.android.tools.metalava.model.TypeUse
import com.android.tools.metalava.model.type.DefaultTypeModifiers
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.turbine.binder.Binder
import com.google.turbine.binder.Binder.BindingResult
import com.google.turbine.binder.ClassPathBinder
import com.google.turbine.binder.Processing.ProcessorInfo
import com.google.turbine.binder.bound.EnumConstantValue
import com.google.turbine.binder.bound.SourceTypeBoundClass
import com.google.turbine.binder.bound.TurbineClassValue
import com.google.turbine.binder.bound.TypeBoundClass
import com.google.turbine.binder.bound.TypeBoundClass.FieldInfo
import com.google.turbine.binder.bound.TypeBoundClass.MethodInfo
import com.google.turbine.binder.bound.TypeBoundClass.ParamInfo
import com.google.turbine.binder.bound.TypeBoundClass.TyVarInfo
import com.google.turbine.binder.bytecode.BytecodeBoundClass
import com.google.turbine.binder.env.CompoundEnv
import com.google.turbine.binder.lookup.LookupKey
import com.google.turbine.binder.lookup.TopLevelIndex
import com.google.turbine.binder.sym.ClassSymbol
import com.google.turbine.binder.sym.TyVarSymbol
import com.google.turbine.diag.TurbineLog
import com.google.turbine.model.Const
import com.google.turbine.model.Const.ArrayInitValue
import com.google.turbine.model.Const.Kind
import com.google.turbine.model.Const.Value
import com.google.turbine.model.TurbineConstantTypeKind as PrimKind
import com.google.turbine.model.TurbineFlag
import com.google.turbine.model.TurbineTyKind
import com.google.turbine.tree.Tree
import com.google.turbine.tree.Tree.ArrayInit
import com.google.turbine.tree.Tree.Assign
import com.google.turbine.tree.Tree.CompUnit
import com.google.turbine.tree.Tree.Expression
import com.google.turbine.tree.Tree.Ident
import com.google.turbine.tree.Tree.Literal
import com.google.turbine.tree.Tree.TyDecl
import com.google.turbine.type.AnnoInfo
import com.google.turbine.type.Type
import com.google.turbine.type.Type.ArrayTy
import com.google.turbine.type.Type.ClassTy
import com.google.turbine.type.Type.ClassTy.SimpleClassTy
import com.google.turbine.type.Type.ErrorTy
import com.google.turbine.type.Type.PrimTy
import com.google.turbine.type.Type.TyKind
import com.google.turbine.type.Type.TyVar
import com.google.turbine.type.Type.WildTy
import com.google.turbine.type.Type.WildTy.BoundKind
import java.io.File
import java.util.Optional
import javax.lang.model.SourceVersion
import kotlin.collections.ArrayDeque

/**
 * This initializer acts as an adapter between codebase and the output from Turbine parser.
 *
 * This is used for populating all the classes,packages and other items from the data present in the
 * parsed Tree
 */
internal open class TurbineCodebaseInitialiser(
    val units: List<CompUnit>,
    val codebase: TurbineBasedCodebase,
    val classpath: List<File>,
) {
    /** The output from Turbine Binder */
    private lateinit var bindingResult: BindingResult

    /** Map between ClassSymbols and TurbineClass for classes present in source */
    private lateinit var sourceClassMap: ImmutableMap<ClassSymbol, SourceTypeBoundClass>

    /** Map between ClassSymbols and TurbineClass for classes present in classPath */
    private lateinit var envClassMap: CompoundEnv<ClassSymbol, BytecodeBoundClass>

    private lateinit var index: TopLevelIndex

    /** Map between Class declaration and the corresponding source CompUnit */
    private val classSourceMap: MutableMap<TyDecl, CompUnit> = mutableMapOf<TyDecl, CompUnit>()

    /**
     * Binds the units with the help of Turbine's binder.
     *
     * Then creates the packages, classes and their members, as well as sets up various class
     * hierarchies using the binder's output
     */
    fun initialize() {
        // Bind the units
        try {
            val procInfo =
                ProcessorInfo.create(
                    ImmutableList.of(),
                    null,
                    ImmutableMap.of(),
                    SourceVersion.latest()
                )

            // Any non-fatal error (like unresolved symbols) will be captured in this log and will
            // be ignored.
            val log = TurbineLog()

            bindingResult =
                Binder.bind(
                    log,
                    ImmutableList.copyOf(units),
                    ClassPathBinder.bindClasspath(classpath.map { it.toPath() }),
                    procInfo,
                    ClassPathBinder.bindClasspath(listOf()),
                    Optional.empty()
                )!!
            sourceClassMap = bindingResult.units()
            envClassMap = bindingResult.classPathEnv()
            index = bindingResult.tli()
        } catch (e: Throwable) {
            throw e
        }
        createAllPackages()
        createAllClasses()
        correctNullability()
    }

    /**
     * Corrects the nullability of types in the codebase based on their context items. If an item is
     * non-null or nullable, its type is too.
     */
    private fun correctNullability() {
        codebase.accept(
            object : BaseItemVisitor() {
                override fun visitItem(item: Item) {
                    val type = item.type() ?: return
                    val implicitNullness = item.implicitNullness()
                    if (implicitNullness == true || item.modifiers.isNullable()) {
                        type.modifiers.setNullability(TypeNullability.NULLABLE)
                    } else if (implicitNullness == false || item.modifiers.isNonNull()) {
                        type.modifiers.setNullability(TypeNullability.NONNULL)
                    }
                    // Also make array components for annotation types non-null
                    if (
                        type is ArrayTypeItem && item.containingClass()?.isAnnotationType() == true
                    ) {
                        type.componentType.modifiers.setNullability(TypeNullability.NONNULL)
                    }
                }
            }
        )
    }

    private fun createAllPackages() {
        // Root package
        findOrCreatePackage("", "")

        for (unit in units) {
            var doc = ""
            // No class declarations. Will be a case of package-info file
            if (unit.decls().isEmpty()) {
                val source = unit.source().source()
                doc = getHeaderComments(source)
            }
            findOrCreatePackage(getPackageName(unit), doc)
            unit.decls().forEach { decl -> classSourceMap.put(decl, unit) }
        }
    }

    /**
     * Searches for the package with supplied name in the codebase's package map and if not found
     * creates the corresponding TurbinePackageItem and adds it to the package map.
     */
    private fun findOrCreatePackage(name: String, document: String): TurbinePackageItem {
        val pkgItem = codebase.findPackage(name)
        if (pkgItem != null) {
            return pkgItem as TurbinePackageItem
        } else {
            val modifiers = TurbineModifierItem.create(codebase, 0, null, false)
            val turbinePkgItem = TurbinePackageItem.create(codebase, name, modifiers, document)
            modifiers.setOwner(turbinePkgItem)
            codebase.addPackage(turbinePkgItem)
            return turbinePkgItem
        }
    }

    private fun createAllClasses() {
        for ((classSymbol, sourceBoundClass) in sourceClassMap) {

            // Turbine considers package-info as class and creates one for empty packages which is
            // not consistent with Psi
            if (classSymbol.simpleName() == "package-info") {
                continue
            }

            // Ignore inner classes, they will be created when the outer class is created.
            if (sourceBoundClass.owner() != null) {
                continue
            }

            createTopLevelClassAndContents(classSymbol)
        }
    }

    val ClassSymbol.isTopClass
        get() = !binaryName().contains('$')

    /**
     * Create top level classes, their inner classes and all the other members.
     *
     * All the classes are registered by name and so can be found by [findOrCreateClass].
     */
    private fun createTopLevelClassAndContents(classSymbol: ClassSymbol) {
        if (!classSymbol.isTopClass) error("$classSymbol is not a top level class")
        createClass(classSymbol, TypeParameterScope.empty)
    }

    /** Tries to create a class if not already present in codebase's classmap */
    internal fun findOrCreateClass(name: String): TurbineClassItem? {
        var classItem = codebase.findClass(name)

        if (classItem == null) {
            // This will get the symbol for the top class even if the class name is for an inner
            // class.
            val topClassSym = getClassSymbol(name)

            // Create the top level class, along with any inner classes and register them all by
            // name.
            topClassSym?.let { createTopLevelClassAndContents(topClassSym) }

            // Now try and find the actual class by name.
            classItem = codebase.findClass(name)
        }

        return classItem
    }

    private fun createClass(
        sym: ClassSymbol,
        enclosingClassTypeParameterScope: TypeParameterScope,
    ): TurbineClassItem {

        var cls: TypeBoundClass? = sourceClassMap[sym]
        cls = if (cls != null) cls else envClassMap.get(sym)!!
        val decl = (cls as? SourceTypeBoundClass)?.decl()

        val isTopClass = cls.owner() == null
        val isFromClassPath = !(cls is SourceTypeBoundClass)

        // Get the package item
        val pkgName = sym.packageName().replace('/', '.')
        val pkgItem = findOrCreatePackage(pkgName, "")

        // Create class
        val qualifiedName = getQualifiedName(sym.binaryName())
        val simpleName = qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1)
        val fullName = sym.simpleName().replace('$', '.')
        val annotations = createAnnotations(cls.annotations())
        val documentation = decl?.javadoc() ?: ""
        val modifierItem =
            TurbineModifierItem.create(
                codebase,
                cls.access(),
                annotations,
                isDeprecated(documentation)
            )
        val (typeParameters, classTypeParameterScope) =
            createTypeParameters(
                cls.typeParameterTypes(),
                enclosingClassTypeParameterScope,
                "class $qualifiedName",
            )
        // Create the sourcefile
        val sourceFile =
            if (isTopClass && !isFromClassPath) {
                classSourceMap[(cls as SourceTypeBoundClass).decl()]?.let {
                    TurbineSourceFile(codebase, it)
                }
            } else null
        val classItem =
            TurbineClassItem(
                codebase,
                simpleName,
                fullName,
                qualifiedName,
                sym,
                modifierItem,
                getClassKind(cls.kind()),
                typeParameters,
                getCommentedDoc(documentation),
                sourceFile,
            )
        modifierItem.setOwner(classItem)
        modifierItem.setSynchronized(false) // A class can not be synchronized in java

        // Setup the SuperClass
        if (!classItem.isInterface()) {
            val superClassType = cls.superClassType()
            val superClassTypeItem =
                if (superClassType == null) null
                else createSuperType(superClassType, classTypeParameterScope)
            classItem.setSuperClassType(superClassTypeItem)
        }

        // Set interface types
        classItem.setInterfaceTypes(
            cls.interfaceTypes().map { createSuperType(it, classTypeParameterScope) }
        )

        // Create fields
        createFields(classItem, cls.fields(), classTypeParameterScope)

        // Create methods
        createMethods(classItem, cls.methods(), classTypeParameterScope)

        // Create constructors
        createConstructors(classItem, cls.methods(), classTypeParameterScope)

        // Add to the codebase
        codebase.addClass(classItem, isTopClass)

        // Add the class to corresponding PackageItem
        if (isTopClass) {
            classItem.containingPackage = pkgItem
            pkgItem.addTopClass(classItem)
            // If the class is top class, fix the constructor return type right away. Otherwise wait
            // for containingClass to be set via createInnerClasses
            fixCtorReturnType(classItem)
        }

        // Do not emit to signature file if it is from classpath
        if (isFromClassPath) {
            pkgItem.emit = false
            classItem.emit = false
        }

        // Create InnerClasses.
        val children = cls.children()
        createInnerClasses(classItem, children.values.asList(), classTypeParameterScope)

        return classItem
    }

    fun getClassKind(type: TurbineTyKind): ClassKind {
        return when (type) {
            TurbineTyKind.INTERFACE -> ClassKind.INTERFACE
            TurbineTyKind.ENUM -> ClassKind.ENUM
            TurbineTyKind.ANNOTATION -> ClassKind.ANNOTATION_TYPE
            else -> ClassKind.CLASS
        }
    }

    /** Creates a list of AnnotationItems from given list of Turbine Annotations */
    private fun createAnnotations(annotations: List<AnnoInfo>): List<AnnotationItem> {
        return annotations.mapNotNull { createAnnotation(it) }
    }

    private fun createAnnotation(annotation: AnnoInfo): TurbineAnnotationItem? {
        val annoAttrs = getAnnotationAttributes(annotation.values(), annotation.tree()?.args())

        val simpleName = annotation.tree()?.let { extractNameFromIdent(it.name()) }
        val clsSym = annotation.sym()
        val qualifiedName =
            if (clsSym == null) simpleName!! else getQualifiedName(clsSym.binaryName())

        return TurbineAnnotationItem(codebase, qualifiedName, annoAttrs)
    }

    /** Creates a list of AnnotationAttribute from the map of name-value attribute pairs */
    private fun getAnnotationAttributes(
        attrs: ImmutableMap<String, Const>,
        exprs: ImmutableList<Expression>?
    ): List<AnnotationAttribute> {
        val attributes = mutableListOf<AnnotationAttribute>()
        if (exprs != null) {
            for (exp in exprs) {
                when (exp.kind()) {
                    Tree.Kind.ASSIGN -> {
                        exp as Assign
                        val name = exp.name().value()
                        val assignExp = exp.expr()
                        attributes.add(
                            DefaultAnnotationAttribute(
                                name,
                                createAttrValue(attrs[name]!!, assignExp)
                            )
                        )
                    }
                    else -> {
                        val name = ANNOTATION_ATTR_VALUE
                        attributes.add(
                            DefaultAnnotationAttribute(name, createAttrValue(attrs[name]!!, exp))
                        )
                    }
                }
            }
        } else {
            for ((name, value) in attrs) {
                attributes.add(DefaultAnnotationAttribute(name, createAttrValue(value, null)))
            }
        }
        return attributes
    }

    private fun createAttrValue(const: Const, expr: Expression?): AnnotationAttributeValue {
        if (const.kind() == Kind.ARRAY) {
            const as ArrayInitValue
            if (const.elements().count() == 1 && expr != null && !(expr is ArrayInit)) {
                // This is case where defined type is array type but provided attribute value is
                // single non-array element
                // For e.g. @Anno(5) where Anno is @interfacce Anno {int [] value()}
                val constLiteral = const.elements().single()
                return DefaultAnnotationSingleAttributeValue(
                    { getSource(constLiteral, expr) },
                    { getValue(constLiteral) }
                )
            }
            return DefaultAnnotationArrayAttributeValue(
                { getSource(const, expr) },
                { const.elements().map { createAttrValue(it, null) } }
            )
        }
        return DefaultAnnotationSingleAttributeValue(
            { getSource(const, expr) },
            { getValue(const) }
        )
    }

    private fun getSource(const: Const, expr: Expression?): String {
        return when (const.kind()) {
            Kind.PRIMITIVE -> {
                when ((const as Value).constantTypeKind()) {
                    PrimKind.INT -> {
                        val value = (const as Const.IntValue).value()
                        if (value < 0 || (expr != null && expr.kind() == Tree.Kind.TYPE_CAST))
                            "0x" + value.toUInt().toString(16) // Hex Value
                        else value.toString()
                    }
                    PrimKind.SHORT -> {
                        val value = (const as Const.ShortValue).value()
                        if (value < 0) "0x" + value.toUInt().toString(16) else value.toString()
                    }
                    PrimKind.FLOAT -> {
                        val value = (const as Const.FloatValue).value()
                        when {
                            value == Float.POSITIVE_INFINITY -> "java.lang.Float.POSITIVE_INFINITY"
                            value == Float.NEGATIVE_INFINITY -> "java.lang.Float.NEGATIVE_INFINITY"
                            value < 0 -> value.toString() + "F" // Handling negative values
                            else -> value.toString() + "f" // Handling positive values
                        }
                    }
                    PrimKind.DOUBLE -> {
                        val value = (const as Const.DoubleValue).value()
                        when {
                            value == Double.POSITIVE_INFINITY ->
                                "java.lang.Double.POSITIVE_INFINITY"
                            value == Double.NEGATIVE_INFINITY ->
                                "java.lang.Double.NEGATIVE_INFINITY"
                            else -> const.toString()
                        }
                    }
                    PrimKind.BYTE -> const.getValue().toString()
                    else -> const.toString()
                }
            }
            Kind.ARRAY -> {
                const as ArrayInitValue
                val pairs =
                    if (expr != null) const.elements().zip((expr as ArrayInit).exprs())
                    else const.elements().map { Pair(it, null) }
                buildString {
                        append("{")
                        pairs.joinTo(this, ", ") { getSource(it.first, it.second) }
                        append("}")
                    }
                    .toString()
            }
            Kind.ENUM_CONSTANT -> getValue(const).toString()
            Kind.CLASS_LITERAL -> {
                if (expr != null) expr.toString() else getValue(const).toString()
            }
            else -> const.toString()
        }
    }

    private fun getValue(const: Const): Any? {
        when (const.kind()) {
            Kind.PRIMITIVE -> {
                val value = const as Value
                return value.getValue()
            }
            // For cases like AnyClass.class, return the qualified name of AnyClass
            Kind.CLASS_LITERAL -> {
                val value = const as TurbineClassValue
                return value.type().toString()
            }
            Kind.ENUM_CONSTANT -> {
                val value = const as EnumConstantValue
                val temp =
                    getQualifiedName(value.sym().owner().binaryName()) + "." + value.toString()
                return temp
            }
            else -> {
                return const.toString()
            }
        }
    }

    /**
     * Creates a [ClassTypeItem] that is suitable for use as a super type, e.g. in an `extends` or
     * `implements` list.
     */
    private fun createSuperType(type: Type, typeParameterScope: TypeParameterScope): ClassTypeItem =
        createType(type, false, typeParameterScope, TypeUse.SUPER_TYPE) as ClassTypeItem

    private fun createType(
        type: Type,
        isVarArg: Boolean,
        typeParameterScope: TypeParameterScope,
        typeUse: TypeUse = TypeUse.GENERAL,
    ): TypeItem {
        return when (val kind = type.tyKind()) {
            TyKind.PRIM_TY -> {
                type as PrimTy
                val annotations = createAnnotations(type.annos())
                // Primitives are always non-null.
                val modifiers = DefaultTypeModifiers.create(annotations, TypeNullability.NONNULL)
                when (type.primkind()) {
                    PrimKind.BOOLEAN -> TurbinePrimitiveTypeItem(modifiers, Primitive.BOOLEAN)
                    PrimKind.BYTE -> TurbinePrimitiveTypeItem(modifiers, Primitive.BYTE)
                    PrimKind.CHAR -> TurbinePrimitiveTypeItem(modifiers, Primitive.CHAR)
                    PrimKind.DOUBLE -> TurbinePrimitiveTypeItem(modifiers, Primitive.DOUBLE)
                    PrimKind.FLOAT -> TurbinePrimitiveTypeItem(modifiers, Primitive.FLOAT)
                    PrimKind.INT -> TurbinePrimitiveTypeItem(modifiers, Primitive.INT)
                    PrimKind.LONG -> TurbinePrimitiveTypeItem(modifiers, Primitive.LONG)
                    PrimKind.SHORT -> TurbinePrimitiveTypeItem(modifiers, Primitive.SHORT)
                    else ->
                        throw IllegalStateException("Invalid primitive type in API surface: $type")
                }
            }
            TyKind.ARRAY_TY -> {
                createArrayType(type as ArrayTy, isVarArg, typeParameterScope)
            }
            TyKind.CLASS_TY -> {
                type as ClassTy
                var outerClass: TurbineClassTypeItem? = null
                // A ClassTy is represented by list of SimpleClassTY each representing an inner
                // class. e.g. , Outer.Inner.Inner1 will be represented by three simple classes
                // Outer, Outer.Inner and Outer.Inner.Inner1
                for (simpleClass in type.classes()) {
                    // For all outer class types, set the nullability to non-null.
                    outerClass?.modifiers?.setNullability(TypeNullability.NONNULL)
                    outerClass =
                        createSimpleClassType(simpleClass, outerClass, typeParameterScope, typeUse)
                }
                outerClass!!
            }
            TyKind.TY_VAR -> {
                type as TyVar
                val annotations = createAnnotations(type.annos())
                val modifiers = DefaultTypeModifiers.create(annotations)
                val typeParameter = typeParameterScope.getTypeParameter(type.sym().name())
                TurbineVariableTypeItem(modifiers, typeParameter)
            }
            TyKind.WILD_TY -> {
                type as WildTy
                val annotations = createAnnotations(type.annotations())
                // Wildcards themselves don't have a defined nullability.
                val modifiers = DefaultTypeModifiers.create(annotations, TypeNullability.UNDEFINED)
                when (type.boundKind()) {
                    BoundKind.UPPER -> {
                        val upperBound = createWildcardBound(type.bound(), typeParameterScope)
                        TurbineWildcardTypeItem(modifiers, upperBound, null)
                    }
                    BoundKind.LOWER -> {
                        // LowerBounded types have java.lang.Object as upper bound
                        val upperBound = createWildcardBound(ClassTy.OBJECT, typeParameterScope)
                        val lowerBound = createWildcardBound(type.bound(), typeParameterScope)
                        TurbineWildcardTypeItem(modifiers, upperBound, lowerBound)
                    }
                    BoundKind.NONE -> {
                        // Unbounded types have java.lang.Object as upper bound
                        val upperBound = createWildcardBound(ClassTy.OBJECT, typeParameterScope)
                        TurbineWildcardTypeItem(modifiers, upperBound, null)
                    }
                    else ->
                        throw IllegalStateException("Invalid wildcard type in API surface: $type")
                }
            }
            TyKind.VOID_TY ->
                TurbinePrimitiveTypeItem(
                    // Primitives are always non-null.
                    DefaultTypeModifiers.create(emptyList(), TypeNullability.NONNULL),
                    Primitive.VOID
                )
            TyKind.NONE_TY ->
                TurbinePrimitiveTypeItem(
                    // Primitives are always non-null.
                    DefaultTypeModifiers.create(emptyList(), TypeNullability.NONNULL),
                    Primitive.VOID
                )
            TyKind.ERROR_TY -> {
                // This is case of unresolved superclass or implemented interface
                type as ErrorTy
                TurbineClassTypeItem(
                    codebase,
                    DefaultTypeModifiers.create(emptyList(), TypeNullability.UNDEFINED),
                    type.name(),
                    emptyList(),
                    null,
                )
            }
            else -> throw IllegalStateException("Invalid type in API surface: $kind")
        }
    }

    private fun createWildcardBound(type: Type, typeParameterScope: TypeParameterScope) =
        createType(type, false, typeParameterScope) as ReferenceTypeItem

    private fun createArrayType(
        type: ArrayTy,
        isVarArg: Boolean,
        typeParameterScope: TypeParameterScope,
    ): TypeItem {
        // For Turbine's ArrayTy, the annotations for multidimentional arrays comes out in reverse
        // order. This method attaches annotations in the correct order by applying them in reverse
        val modifierStack = ArrayDeque<TypeModifiers>()
        var curr: Type = type
        while (curr.tyKind() == TyKind.ARRAY_TY) {
            curr as ArrayTy
            val annotations = createAnnotations(curr.annos())
            modifierStack.addLast(DefaultTypeModifiers.create(annotations))
            curr = curr.elementType()
        }
        var componentType = createType(curr, false, typeParameterScope)
        while (modifierStack.isNotEmpty()) {
            val modifiers = modifierStack.removeFirst()
            if (modifierStack.isEmpty()) {
                // Outermost array. Should be called with correct value of isvararg
                componentType = createSimpleArrayType(modifiers, componentType, isVarArg)
            } else {
                componentType = createSimpleArrayType(modifiers, componentType, false)
            }
        }
        return componentType
    }

    private fun createSimpleArrayType(
        modifiers: TypeModifiers,
        componentType: TypeItem,
        isVarArg: Boolean
    ): TypeItem {
        return TurbineArrayTypeItem(modifiers, componentType, isVarArg)
    }

    private fun createSimpleClassType(
        type: SimpleClassTy,
        outerClass: TurbineClassTypeItem?,
        typeParameterScope: TypeParameterScope,
        typeUse: TypeUse = TypeUse.GENERAL,
    ): TurbineClassTypeItem {
        // Super types are always NONNULL.
        val nullability = if (typeUse == TypeUse.SUPER_TYPE) TypeNullability.NONNULL else null
        val annotations = createAnnotations(type.annos())
        val modifiers = DefaultTypeModifiers.create(annotations, nullability)
        val qualifiedName = getQualifiedName(type.sym().binaryName())
        val parameters =
            type.targs().map { createType(it, false, typeParameterScope) as TypeArgumentTypeItem }
        return TurbineClassTypeItem(codebase, modifiers, qualifiedName, parameters, outerClass)
    }

    private fun createTypeParameters(
        tyParams: ImmutableMap<TyVarSymbol, TyVarInfo>,
        enclosingClassTypeParameterScope: TypeParameterScope,
        description: String,
    ): Pair<TypeParameterList, TypeParameterScope> {

        if (tyParams.isEmpty())
            return Pair(TypeParameterList.NONE, enclosingClassTypeParameterScope)

        // Create a list of [TypeParameterItem]s from turbine specific classes.
        val (typeParameters, scope) =
            DefaultTypeParameterList.createTypeParameterItemsAndScope(
                enclosingClassTypeParameterScope,
                description,
                tyParams.toList(),
                { (sym, tyParam) -> createTypeParameter(sym, tyParam) },
                { scope, item, (_, tParam) ->
                    createTypeParameterBounds(tParam, scope).also { item.bounds = it }
                },
            )

        val tyParamList = TurbineTypeParameterList(codebase)
        tyParamList.typeParameters = typeParameters
        return Pair(tyParamList, scope)
    }

    /**
     * Create the [TurbineTypeParameterItem] without any bounds and register it so that any uses of
     * it within the type bounds, e.g. `<E extends Enum<E>>`, or from other type parameters within
     * the same [TypeParameterList] can be resolved.
     */
    private fun createTypeParameter(sym: TyVarSymbol, param: TyVarInfo): TurbineTypeParameterItem {
        val modifiers =
            TurbineModifierItem.create(codebase, 0, createAnnotations(param.annotations()), false)
        val typeParamItem = TurbineTypeParameterItem(codebase, modifiers, name = sym.name())
        modifiers.setOwner(typeParamItem)
        return typeParamItem
    }

    /** Create a [BoundsTypeItem]. */
    private fun createBoundsTypeItem(type: Type, typeParameterScope: TypeParameterScope) =
        createType(type, false, typeParameterScope) as BoundsTypeItem

    /** Create the bounds of a [TurbineTypeParameterItem]. */
    private fun createTypeParameterBounds(
        param: TyVarInfo,
        typeParameterScope: TypeParameterScope,
    ): List<BoundsTypeItem> {
        val typeBounds = mutableListOf<BoundsTypeItem>()
        val upperBounds = param.upperBound()

        upperBounds.bounds().mapTo(typeBounds) { createBoundsTypeItem(it, typeParameterScope) }
        param.lowerBound()?.let { typeBounds.add(createBoundsTypeItem(it, typeParameterScope)) }

        return typeBounds.toList()
    }

    /** This method sets up the inner class hierarchy. */
    private fun createInnerClasses(
        classItem: TurbineClassItem,
        innerClasses: ImmutableList<ClassSymbol>,
        enclosingClassTypeParameterScope: TypeParameterScope
    ) {
        classItem.innerClasses =
            innerClasses.map { cls ->
                val innerClassItem = createClass(cls, enclosingClassTypeParameterScope)
                innerClassItem.containingClass = classItem
                fixCtorReturnType(innerClassItem)
                innerClassItem
            }
    }

    /** This methods creates and sets the fields of a class */
    private fun createFields(
        classItem: TurbineClassItem,
        fields: ImmutableList<FieldInfo>,
        typeParameterScope: TypeParameterScope,
    ) {
        classItem.fields =
            fields.map { field ->
                val annotations = createAnnotations(field.annotations())
                val fieldModifierItem =
                    TurbineModifierItem.create(
                        codebase,
                        field.access(),
                        annotations,
                        isDeprecated(field.decl()?.javadoc())
                    )
                val type = createType(field.type(), false, typeParameterScope)
                val documentation = field.decl()?.javadoc() ?: ""
                val fieldItem =
                    TurbineFieldItem(
                        codebase,
                        field.name(),
                        classItem,
                        type,
                        fieldModifierItem,
                        getCommentedDoc(documentation),
                    )
                fieldModifierItem.setOwner(fieldItem)
                val optExpr = field.decl()?.init()
                val expr = if (optExpr != null && optExpr.isPresent()) optExpr.get() else null
                setInitialValue(fieldItem, field.value()?.getValue(), expr)
                fieldItem
            }
    }

    private fun createMethods(
        classItem: TurbineClassItem,
        methods: List<MethodInfo>,
        enclosingClassTypeParameterScope: TypeParameterScope,
    ) {
        val methodItems =
            methods
                .filter { it.sym().name() != "<init>" }
                .map { method ->
                    val annotations = createAnnotations(method.annotations())
                    val methodModifierItem =
                        TurbineModifierItem.create(
                            codebase,
                            method.access(),
                            annotations,
                            isDeprecated(method.decl()?.javadoc())
                        )
                    val (typeParams, methodTypeParameterScope) =
                        createTypeParameters(
                            method.tyParams(),
                            enclosingClassTypeParameterScope,
                            method.name(),
                        )
                    val documentation = method.decl()?.javadoc() ?: ""
                    val defaultValueExpr = getAnnotationDefaultExpression(method)
                    val defaultValue =
                        if (method.defaultValue() != null)
                            extractAnnotationDefaultValue(method.defaultValue()!!, defaultValueExpr)
                        else ""
                    val methodItem =
                        TurbineMethodItem(
                            codebase,
                            method.sym(),
                            classItem,
                            createType(method.returnType(), false, methodTypeParameterScope),
                            methodModifierItem,
                            typeParams,
                            getCommentedDoc(documentation),
                            defaultValue,
                        )
                    methodModifierItem.setOwner(methodItem)
                    createParameters(methodItem, method.parameters(), methodTypeParameterScope)
                    methodItem.throwableTypes =
                        getThrowsList(method.exceptions(), methodTypeParameterScope)
                    methodItem
                }
        // Ignore default enum methods
        classItem.methods =
            methodItems.filter { !isDefaultEnumMethod(classItem, it) }.toMutableList()
    }

    private fun createParameters(
        methodItem: TurbineMethodItem,
        parameters: List<ParamInfo>,
        typeParameterScope: TypeParameterScope
    ) {
        methodItem.parameters =
            parameters.mapIndexed { idx, parameter ->
                val annotations = createAnnotations(parameter.annotations())
                val parameterModifierItem =
                    TurbineModifierItem.create(codebase, parameter.access(), annotations, false)
                val type =
                    createType(
                        parameter.type(),
                        parameterModifierItem.isVarArg(),
                        typeParameterScope,
                    )
                val parameterItem =
                    TurbineParameterItem(
                        codebase,
                        parameter.name(),
                        methodItem,
                        idx,
                        type,
                        parameterModifierItem,
                    )
                parameterModifierItem.setOwner(parameterItem)
                parameterItem
            }
    }

    private fun createConstructors(
        classItem: TurbineClassItem,
        methods: List<MethodInfo>,
        enclosingClassTypeParameterScope: TypeParameterScope,
    ) {
        var hasImplicitDefaultConstructor = false
        classItem.constructors =
            methods
                .filter { it.sym().name() == "<init>" }
                .map { constructor ->
                    val annotations = createAnnotations(constructor.annotations())
                    val constructorModifierItem =
                        TurbineModifierItem.create(
                            codebase,
                            constructor.access(),
                            annotations,
                            isDeprecated(constructor.decl()?.javadoc())
                        )
                    val (typeParams, constructorTypeParameterScope) =
                        createTypeParameters(
                            constructor.tyParams(),
                            enclosingClassTypeParameterScope,
                            constructor.name(),
                        )
                    hasImplicitDefaultConstructor =
                        (constructor.access() and TurbineFlag.ACC_SYNTH_CTOR) != 0
                    val name = classItem.simpleName()
                    val documentation = constructor.decl()?.javadoc() ?: ""
                    val constructorItem =
                        TurbineConstructorItem(
                            codebase,
                            name,
                            constructor.sym(),
                            classItem,
                            createType(
                                constructor.returnType(),
                                false,
                                constructorTypeParameterScope
                            ),
                            constructorModifierItem,
                            typeParams,
                            getCommentedDoc(documentation),
                            "",
                        )
                    constructorModifierItem.setOwner(constructorItem)
                    createParameters(
                        constructorItem,
                        constructor.parameters(),
                        constructorTypeParameterScope
                    )
                    constructorItem.throwableTypes =
                        getThrowsList(constructor.exceptions(), constructorTypeParameterScope)
                    constructorItem
                }
        classItem.hasImplicitDefaultConstructor = hasImplicitDefaultConstructor
    }

    private fun getQualifiedName(binaryName: String): String {
        return binaryName.replace('/', '.').replace('$', '.')
    }

    /**
     * Turbine's Binder gives return type of constructors as void. This needs to be changed to
     * Class.toType().
     */
    private fun fixCtorReturnType(classItem: TurbineClassItem) {
        val result =
            classItem.constructors.map {
                it.setReturnType(classItem.type())
                it
            }
        classItem.constructors = result
    }

    /**
     * Get the ClassSymbol corresponding to a qualified name. Since the Turbine's lookup method
     * returns only top-level classes, this method will return the ClassSymbol of outermost class
     * for inner classes.
     */
    private fun getClassSymbol(name: String): ClassSymbol? {
        val result = index.scope().lookup(createLookupKey(name))
        return result?.let { it.sym() as ClassSymbol }
    }

    /** Creates a LookupKey from a given name */
    private fun createLookupKey(name: String): LookupKey {
        val idents = name.split(".").mapIndexed { idx, it -> Ident(idx, it) }
        return LookupKey(ImmutableList.copyOf(idents))
    }

    private fun isDeprecated(javadoc: String?): Boolean {
        return javadoc?.contains("@deprecated") ?: false
    }

    private fun getThrowsList(
        throwsTypes: List<Type>,
        enclosingTypeParameterScope: TypeParameterScope
    ): List<ThrowableType> {
        return throwsTypes.map { type ->
            val exceptionTypeItem =
                createType(type, false, enclosingTypeParameterScope) as ExceptionTypeItem
            ThrowableType.ofExceptionType(exceptionTypeItem)
        }
    }

    private fun getCommentedDoc(doc: String): String {
        return buildString {
                if (doc != "") {
                    append("/**")
                    append(doc)
                    append("*/")
                }
            }
            .toString()
    }

    private fun setInitialValue(fieldItem: TurbineFieldItem, value: Any?, expr: Expression?) {
        if (value != null) {
            fieldItem.initialValueWithRequiredConstant = value
            fieldItem.initialValueWithoutRequiredConstant = value
            return
        }
        if (expr != null) {
            return when (expr.kind()) {
                Tree.Kind.LITERAL -> {
                    fieldItem.initialValueWithRequiredConstant = null
                    fieldItem.initialValueWithoutRequiredConstant =
                        getValue((expr as Literal).value())
                }
                // Class Type
                Tree.Kind.CLASS_LITERAL -> {
                    fieldItem.initialValueWithRequiredConstant = null
                    fieldItem.initialValueWithoutRequiredConstant = expr
                }
                else -> {
                    fieldItem.initialValueWithRequiredConstant = null
                    fieldItem.initialValueWithoutRequiredConstant = null
                }
            }
        }
    }

    /** Determines whether the given method is a default enum method ("values" or "valueOf"). */
    private fun isDefaultEnumMethod(classItem: ClassItem, methodItem: MethodItem): Boolean =
        classItem.isEnum() &&
            (methodItem.name() == "values" && isValuesMethod(classItem, methodItem) ||
                methodItem.name() == "valueOf" && isValueOfMethod(classItem, methodItem))

    /** Checks if the given method matches the signature of the "values" enum method. */
    private fun isValuesMethod(classItem: ClassItem, methodItem: MethodItem): Boolean =
        methodItem.returnType().let { returnType ->
            returnType is ArrayTypeItem &&
                matchType(returnType.componentType, classItem) &&
                methodItem.parameters().isEmpty()
        }

    /** Checks if the given method matches the signature of the "valueOf" enum method. */
    private fun isValueOfMethod(classItem: ClassItem, methodItem: MethodItem): Boolean =
        matchType(methodItem.returnType(), classItem) &&
            methodItem.parameters().singleOrNull()?.type()?.let {
                it is ClassTypeItem && it.qualifiedName == "java.lang.String"
            }
                ?: false

    private fun matchType(typeItem: TypeItem, classItem: ClassItem): Boolean =
        typeItem is ClassTypeItem && typeItem.qualifiedName == classItem.qualifiedName()

    /**
     * Extracts the expression corresponding to the default value of a given annotation method. If
     * the method does not have a default value, returns null.
     */
    private fun getAnnotationDefaultExpression(method: MethodInfo): Tree? {
        val optExpr = method.decl()?.defaultValue()
        return if (optExpr != null && optExpr.isPresent()) optExpr.get() else null
    }

    /**
     * Extracts the default value of an annotation method and returns it as a string.
     *
     * @param const The constant object representing the annotation value.
     * @param expr An optional expression tree that might provide additional context for value
     *   extraction.
     * @return The default value of the annotation method as a string.
     */
    private fun extractAnnotationDefaultValue(const: Const, expr: Tree?): String {
        return when (const.kind()) {
            Kind.PRIMITIVE -> {
                when ((const as Value).constantTypeKind()) {
                    PrimKind.FLOAT -> {
                        val value = (const as Const.FloatValue).value()
                        when {
                            value == Float.POSITIVE_INFINITY -> "java.lang.Float.POSITIVE_INFINITY"
                            value == Float.NEGATIVE_INFINITY -> "java.lang.Float.NEGATIVE_INFINITY"
                            else -> value.toString() + "f"
                        }
                    }
                    PrimKind.DOUBLE -> {
                        val value = (const as Const.DoubleValue).value()
                        when {
                            value == Double.POSITIVE_INFINITY ->
                                "java.lang.Double.POSITIVE_INFINITY"
                            value == Double.NEGATIVE_INFINITY ->
                                "java.lang.Double.NEGATIVE_INFINITY"
                            else -> const.toString()
                        }
                    }
                    PrimKind.BYTE -> const.getValue().toString()
                    else -> const.toString()
                }
            }
            Kind.ARRAY -> {
                const as ArrayInitValue
                // This is case where defined type is array type but default value is
                // single non-array element
                // For e.g. char[] letter() default 'a';
                if (const.elements().count() == 1 && expr != null && !(expr is ArrayInit)) {
                    extractAnnotationDefaultValue(const.elements().single(), expr)
                } else getValue(const).toString()
            }
            Kind.CLASS_LITERAL -> getValue(const).toString() + ".class"
            else -> getValue(const).toString()
        }
    }
}
