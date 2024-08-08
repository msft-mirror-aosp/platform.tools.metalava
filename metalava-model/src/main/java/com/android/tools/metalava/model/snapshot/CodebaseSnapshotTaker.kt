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

package com.android.tools.metalava.model.snapshot

import com.android.tools.metalava.model.ApiVariantSelectors
import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.DefaultTypeParameterList
import com.android.tools.metalava.model.DelegatedVisitor
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.android.tools.metalava.model.ItemLanguage
import com.android.tools.metalava.model.ItemVisitor
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.ModifierList
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.Showability
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterItem
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.TypeParameterListAndFactory
import com.android.tools.metalava.model.item.DefaultClassItem
import com.android.tools.metalava.model.item.DefaultCodebase
import com.android.tools.metalava.model.item.DefaultCodebaseAssembler
import com.android.tools.metalava.model.item.DefaultItemFactory
import com.android.tools.metalava.model.item.DefaultPackageItem
import com.android.tools.metalava.model.item.DefaultTypeParameterItem
import com.android.tools.metalava.model.item.MutablePackageDoc
import com.android.tools.metalava.model.item.PackageDoc
import com.android.tools.metalava.model.item.PackageDocs

/** Stack of [SnapshotTypeItemFactory] */
internal typealias TypeItemFactoryStack = ArrayList<SnapshotTypeItemFactory>

/** Push new [SnapshotTypeItemFactory] onto the top of the stack. */
internal fun TypeItemFactoryStack.push(factory: SnapshotTypeItemFactory) {
    add(factory)
}

/** Pop [SnapshotTypeItemFactory] from the top of the stack. */
internal fun TypeItemFactoryStack.pop() {
    removeLast()
}

/** Constructs a [Codebase] by taking a snapshot of another [Codebase] that is being visited. */
class CodebaseSnapshotTaker private constructor() : DefaultCodebaseAssembler(), DelegatedVisitor {

    /**
     * The [Codebase] that is under construction.
     *
     * Initialized in [visitCodebase].
     */
    private lateinit var codebase: DefaultCodebase

    override val itemFactory: DefaultItemFactory by
        lazy(LazyThreadSafetyMode.NONE) {
            DefaultItemFactory(
                codebase,
                // Snapshots currently only support java.
                defaultItemLanguage = ItemLanguage.JAVA,
                // Snapshots have already been separated by API surface variants, so they can use
                // the same immutable ApiVariantSelectors.
                ApiVariantSelectors.IMMUTABLE_FACTORY,
            )
        }

    /**
     * The original [Codebase] that is being snapshotted construction.
     *
     * Initialized in [visitCodebase].
     */
    private lateinit var originalCodebase: Codebase

    private val globalTypeItemFactory by
        lazy(LazyThreadSafetyMode.NONE) { SnapshotTypeItemFactory(codebase) }

    /**
     * Stack of [SnapshotTypeItemFactory] that contain information about the [TypeParameterItem]s
     * that are in scope and can resolve a type variable reference to the parameter.
     */
    private val typeItemFactoryStack = TypeItemFactoryStack()

    /** Get the current [SnapshotTypeItemFactory], i.e. the closest enclosing one. */
    private val typeItemFactory
        get() = typeItemFactoryStack.last()

    /**
     * The current [PackageItem], set in [visitPackage], cleared in [afterVisitPackage], relies on
     * the [PackageItem]s being visited as a flat list, not a package hierarchy.
     */
    private var currentPackage: DefaultPackageItem? = null

    /**
     * The current [ClassItem], that forms a stack through the [ClassItem.containingClass].
     *
     * Set (pushed on the stack) in [visitClass]. Reset (popped off the stack) in [afterVisitClass].
     */
    private var currentClass: DefaultClassItem? = null

    /** Take a snapshot of this [ModifierList] for [codebase]. */
    private fun ModifierList.snapshot() = snapshot(codebase)

    /** General [TypeItem] specific snapshot. */
    private fun TypeItem.snapshot() = typeItemFactory.getGeneralType(this)

    /** [ClassTypeItem] specific snapshot. */
    private fun ClassTypeItem.snapshot() = typeItemFactory.getGeneralType(this) as ClassTypeItem

    /**
     * Snapshots need to preserve class nesting when visiting otherwise [ClassItem.containingClass]
     * will not be initialized correctly.
     */
    override val requiresClassNesting: Boolean
        get() = true

    override fun visitCodebase(codebase: Codebase) {
        this.originalCodebase = codebase
        val newCodebase =
            DefaultCodebase(
                location = codebase.location,
                description = "snapshot of ${codebase.description}",
                preFiltered = true,
                annotationManager = codebase.annotationManager,
                trustedApi = true,
                // Supports documentation if the copied codebase does.
                supportsDocumentation = codebase.supportsDocumentation(),
                assembler = this,
            )

        this.codebase = newCodebase
        typeItemFactoryStack.push(globalTypeItemFactory)
    }

    override fun afterVisitCodebase(codebase: Codebase) {
        typeItemFactoryStack.pop()
    }

    /**
     * Construct a [PackageDocs] that contains a [PackageDoc] that in turn contains information
     * extracted from [packageItem] that can be used to create a new [PackageItem] that is a
     * snapshot of [packageItem].
     */
    private fun packageDocsForPackageItem(packageItem: PackageItem) =
        MutablePackageDoc(
                qualifiedName = packageItem.qualifiedName(),
                fileLocation = packageItem.fileLocation,
                modifiers = packageItem.modifiers.snapshot(),
                commentFactory = packageItem.documentation::snapshot,
                overview = packageItem.overviewDocumentation,
            )
            .let { PackageDocs(mapOf(it.qualifiedName to it)) }

    override fun visitPackage(pkg: PackageItem) {
        // Get a PackageDocs that contains a PackageDoc that contains information extracted from the
        // PackageItem being visited. This is needed to ensure that the findOrCreatePackage(...)
        // call below will use the correct information when creating the package. As only a single
        // PackageDoc is provided for this package it means that if findOrCreatePackage(...) had to
        // created a containing package that package would not have a PackageDocs and might be
        // incorrect. However, that should not be a problem as the packages are visited in order
        // such that a containing package is visited before any contained packages.
        val packageDocs = packageDocsForPackageItem(pkg)
        val packageName = pkg.qualifiedName()
        val newPackage = codebase.findOrCreatePackage(packageName, packageDocs)
        currentPackage = newPackage
    }

    override fun afterVisitPackage(pkg: PackageItem) {
        currentPackage = null
    }

    /**
     * Create a snapshot of this [TypeParameterList] and an associated [SnapshotTypeItemFactory].
     *
     * @param description the description to use when failing to resolve a type parameter by name.
     */
    private fun TypeParameterList.snapshot(description: String) =
        if (this == TypeParameterList.NONE) TypeParameterListAndFactory(this, typeItemFactory)
        else
            DefaultTypeParameterList.createTypeParameterItemsAndFactory(
                typeItemFactory,
                description,
                this,
                { typeParameterItem ->
                    DefaultTypeParameterItem(
                        codebase = codebase,
                        itemLanguage = typeParameterItem.itemLanguage,
                        modifiers = typeParameterItem.modifiers.snapshot(),
                        name = typeParameterItem.name(),
                        isReified = typeParameterItem.isReified()
                    )
                },
                // Create, set and return the [BoundsTypeItem] list.
                { typeItemFactory, typeParameterItem ->
                    typeParameterItem.typeBounds().map { typeItemFactory.getBoundsType(it) }
                },
            )

    /**
     * Take a snapshot of the documentation.
     *
     * If necessary revert the documentation change that accompanied a deprecation change.
     *
     * Deprecating an API requires adding an `@Deprecated` annotation and an `@deprecated` Javadoc
     * tag with text that explains why it is being deprecated and what will replace it. When the
     * deprecation change is being reverted then this will remove the `@deprecated` tag and its
     * associated text to avoid warnings when compiling and misleading information being written
     * into the Javadoc.
     */
    private fun snapshotDocumentation(
        itemToSnapshot: Item,
        documentedItem: Item,
    ): ItemDocumentationFactory {
        // The documentation does not need to be reverted if...
        if (
            // the item is not being reverted
            itemToSnapshot === documentedItem
            // or if the deprecation status has not changed
            ||
                itemToSnapshot.effectivelyDeprecated == documentedItem.effectivelyDeprecated
                // or if the item was previously deprecated
                ||
                itemToSnapshot.effectivelyDeprecated
        )
            return documentedItem.documentation::snapshot

        val documentation = documentedItem.documentation
        return { item -> documentation.snapshot(item).apply { removeDeprecatedSection() } }
    }

    override fun visitClass(cls: ClassItem) {
        val classToSnapshot = cls.actualItemToSnapshot

        // Create a TypeParameterList and SnapshotTypeItemFactory for the class.
        val (typeParameterList, classTypeItemFactory) =
            classToSnapshot.typeParameterList.snapshot("class ${classToSnapshot.qualifiedName()}")

        // Push on the stack before resolving any types just in case they refer to a type parameter.
        typeItemFactoryStack.push(classTypeItemFactory)

        // Snapshot the super class type, if any.
        val snapshotSuperClassType =
            classToSnapshot.superClassType()?.let { superClassType ->
                typeItemFactory.getSuperClassType(superClassType)
            }
        val snapshotInterfaceTypes =
            classToSnapshot.interfaceTypes().map { typeItemFactory.getInterfaceType(it) }

        val containingClass = currentClass
        val containingPackage = currentPackage!!
        val newClass =
            itemFactory.createClassItem(
                fileLocation = classToSnapshot.fileLocation,
                itemLanguage = classToSnapshot.itemLanguage,
                modifiers = classToSnapshot.modifiers.snapshot(),
                documentationFactory = snapshotDocumentation(classToSnapshot, cls),
                source = null,
                classKind = classToSnapshot.classKind,
                containingClass = containingClass,
                containingPackage = containingPackage,
                qualifiedName = classToSnapshot.qualifiedName(),
                typeParameterList = typeParameterList,
                isFromClassPath = classToSnapshot.isFromClassPath(),
                superClassType = snapshotSuperClassType,
                interfaceTypes = snapshotInterfaceTypes,
            )

        currentClass = newClass
    }

    override fun afterVisitClass(cls: ClassItem) {
        currentClass = currentClass?.containingClass() as? DefaultClassItem
        typeItemFactoryStack.pop()
    }

    /** Push this [SnapshotTypeItemFactory] in scope before executing [body] and pop afterwards. */
    private inline fun SnapshotTypeItemFactory.inScope(body: () -> Unit) {
        typeItemFactoryStack.push(this)
        body()
        typeItemFactoryStack.pop()
    }

    /** Return a factory that will create a snapshot of this list of [ParameterItem]s. */
    private fun List<ParameterItem>.snapshot(containingCallable: CallableItem) =
        map { parameterItem ->
            // Retrieve the public name immediately to remove any dependencies on this in the
            // lambda passed to publicNameProvider.
            val publicName = parameterItem.publicName()
            itemFactory.createParameterItem(
                fileLocation = parameterItem.fileLocation,
                itemLanguage = parameterItem.itemLanguage,
                modifiers = parameterItem.modifiers.snapshot(),
                name = parameterItem.name(),
                publicNameProvider = { publicName },
                containingCallable = containingCallable,
                parameterIndex = parameterItem.parameterIndex,
                type = parameterItem.type().snapshot(),
                defaultValueFactory = parameterItem.defaultValue::snapshot,
            )
        }

    override fun visitConstructor(constructor: ConstructorItem) {
        val constructorToSnapshot = constructor.actualItemToSnapshot

        // Create a TypeParameterList and SnapshotTypeItemFactory for the constructor.
        val (typeParameterList, constructorTypeItemFactory) =
            constructorToSnapshot.typeParameterList.snapshot(constructorToSnapshot.describe())

        // Resolve any type parameters used in the constructor's parameter items within the scope of
        // the constructor's SnapshotTypeItemFactory.
        constructorTypeItemFactory.inScope {
            val containingClass = currentClass!!
            val newConstructor =
                itemFactory.createConstructorItem(
                    fileLocation = constructorToSnapshot.fileLocation,
                    itemLanguage = constructorToSnapshot.itemLanguage,
                    modifiers = constructorToSnapshot.modifiers.snapshot(),
                    documentationFactory =
                        snapshotDocumentation(constructorToSnapshot, constructor),
                    name = constructorToSnapshot.name(),
                    containingClass = containingClass,
                    typeParameterList = typeParameterList,
                    returnType = constructorToSnapshot.returnType().snapshot(),
                    parameterItemsFactory = { containingCallable ->
                        constructorToSnapshot.parameters().snapshot(containingCallable)
                    },
                    throwsTypes =
                        constructorToSnapshot.throwsTypes().map {
                            typeItemFactory.getExceptionType(it)
                        },
                    callableBodyFactory = constructorToSnapshot.body::snapshot,
                    implicitConstructor = constructorToSnapshot.isImplicitConstructor(),
                )

            containingClass.addConstructor(newConstructor)
        }
    }

    override fun visitMethod(method: MethodItem) {
        val methodToSnapshot = method.actualItemToSnapshot

        // Create a TypeParameterList and SnapshotTypeItemFactory for the method.
        val (typeParameterList, methodTypeItemFactory) =
            methodToSnapshot.typeParameterList.snapshot(methodToSnapshot.describe())

        // Resolve any type parameters used in the method's parameter items within the scope of
        // the method's SnapshotTypeItemFactory.
        methodTypeItemFactory.inScope {
            val containingClass = currentClass!!
            val newMethod =
                itemFactory.createMethodItem(
                    fileLocation = methodToSnapshot.fileLocation,
                    itemLanguage = methodToSnapshot.itemLanguage,
                    modifiers = methodToSnapshot.modifiers.snapshot(),
                    documentationFactory = snapshotDocumentation(methodToSnapshot, method),
                    name = methodToSnapshot.name(),
                    containingClass = containingClass,
                    typeParameterList = typeParameterList,
                    returnType = methodToSnapshot.returnType().snapshot(),
                    parameterItemsFactory = { containingCallable ->
                        methodToSnapshot.parameters().snapshot(containingCallable)
                    },
                    throwsTypes =
                        methodToSnapshot.throwsTypes().map { typeItemFactory.getExceptionType(it) },
                    callableBodyFactory = methodToSnapshot.body::snapshot,
                    annotationDefault = methodToSnapshot.defaultValue(),
                )

            containingClass.addMethod(newMethod)
        }
    }

    override fun visitField(field: FieldItem) {
        val fieldToSnapshot = field.actualItemToSnapshot

        val containingClass = currentClass!!
        val newField =
            itemFactory.createFieldItem(
                fileLocation = fieldToSnapshot.fileLocation,
                itemLanguage = fieldToSnapshot.itemLanguage,
                modifiers = fieldToSnapshot.modifiers.snapshot(),
                documentationFactory = snapshotDocumentation(fieldToSnapshot, field),
                name = fieldToSnapshot.name(),
                containingClass = containingClass,
                type = fieldToSnapshot.type().snapshot(),
                isEnumConstant = fieldToSnapshot.isEnumConstant(),
                fieldValue = fieldToSnapshot.fieldValue?.snapshot(),
            )

        containingClass.addField(newField)
    }

    override fun visitProperty(property: PropertyItem) {
        val propertyToSnapshot = property.actualItemToSnapshot

        val containingClass = currentClass!!
        val newProperty =
            itemFactory.createPropertyItem(
                fileLocation = propertyToSnapshot.fileLocation,
                itemLanguage = propertyToSnapshot.itemLanguage,
                modifiers = propertyToSnapshot.modifiers.snapshot(),
                documentationFactory = snapshotDocumentation(propertyToSnapshot, property),
                name = propertyToSnapshot.name(),
                containingClass = containingClass,
                type = propertyToSnapshot.type().snapshot(),
            )

        containingClass.addProperty(newProperty)
    }

    /**
     * Take a snapshot of [qualifiedName].
     *
     * TODO(b/353737744): Handle resolving nested classes.
     */
    override fun createClassFromUnderlyingModel(qualifiedName: String): ClassItem? {
        // Resolve the class in the original codebase, if possible.
        val originalClass = originalCodebase.resolveClass(qualifiedName) ?: return null

        // Take a snapshot of the class, that should add a new class to the snapshot codebase.
        val visitor = NonFilteringDelegatingVisitor(this)
        val originalPackage = originalClass.containingPackage()

        // Set up the state for taking a snapshot of a class.
        typeItemFactoryStack.push(globalTypeItemFactory)
        visitPackage(originalPackage)
        originalClass.accept(visitor)
        afterVisitPackage(originalPackage)
        typeItemFactoryStack.pop()

        // Find the newly added class.
        return codebase.findClass(originalClass.qualifiedName())!!
    }

    companion object {
        /** Take a snapshot of [codebase]. */
        fun takeSnapshot(
            codebase: Codebase,
            visitorFactory: (DelegatedVisitor) -> ItemVisitor = ::NonFilteringDelegatingVisitor
        ): Codebase {
            // Create a snapshot taker that will construct the snapshot.
            val taker = CodebaseSnapshotTaker()

            // Wrap it in a visitor and visit the codebase.
            val visitor = visitorFactory(taker)
            codebase.accept(visitor)

            // Return the constructed snapshot.
            return taker.codebase
        }
    }
}

/**
 * Get the actual item to snapshot, this takes into account whether the item has been reverted.
 *
 * The [Showability.revertItem] is only set to a non-null value if changes to this [Item] have been
 * reverted AND this [Item] existed in the previously released API.
 *
 * This casts the [Showability.revertItem] to the same type as this is called upon. That is safe as,
 * if set to a non-null value the [Showability.revertItem] will always point to an [Item] of the
 * same type.
 */
val <reified T : Item> T.actualItemToSnapshot: T
    inline get() = (showability.revertItem ?: this) as T
