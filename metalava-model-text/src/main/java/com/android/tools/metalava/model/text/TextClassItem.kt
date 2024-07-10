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

package com.android.tools.metalava.model.text

import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.AnnotationRetention
import com.android.tools.metalava.model.ApiVariantSelectors
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.DefaultCodebase
import com.android.tools.metalava.model.DefaultItem
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.ItemDocumentation
import com.android.tools.metalava.model.ItemLanguage
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.type.DefaultResolvedClassTypeItem
import com.android.tools.metalava.reporter.FileLocation
import java.util.function.Predicate

internal open class TextClassItem(
    codebase: DefaultCodebase,
    fileLocation: FileLocation = FileLocation.UNKNOWN,
    modifiers: DefaultModifierList,
    override val classKind: ClassKind = ClassKind.CLASS,
    private val qualifiedName: String = "",
    private val simpleName: String = qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1),
    private val fullName: String = simpleName,
    override val typeParameterList: TypeParameterList = TypeParameterList.NONE
) :
    DefaultItem(
        codebase = codebase,
        fileLocation = fileLocation,
        itemLanguage = ItemLanguage.UNKNOWN,
        modifiers = modifiers,
        documentation = ItemDocumentation.NONE,
        variantSelectorsFactory = ApiVariantSelectors.IMMUTABLE_FACTORY,
    ),
    ClassItem {

    override fun interfaceTypes(): List<ClassTypeItem> = interfaceTypes

    override fun allInterfaces(): Sequence<ClassItem> {
        return sequenceOf(
                // Add this if and only if it is an interface.
                if (classKind == ClassKind.INTERFACE) sequenceOf(this) else emptySequence(),
                interfaceTypes.asSequence().map { it.asClass() }.filterNotNull(),
            )
            .flatten()
    }

    private var nestedClasses: MutableList<ClassItem> = mutableListOf()

    override var stubConstructor: ConstructorItem? = null

    override var hasPrivateConstructor: Boolean = false

    override fun nestedClasses(): List<ClassItem> = nestedClasses

    override fun hasImplicitDefaultConstructor(): Boolean {
        return false
    }

    var containingClass: ClassItem? = null

    override fun containingClass(): ClassItem? = containingClass

    private var containingPackage: PackageItem? = null

    fun setContainingPackage(containingPackage: PackageItem) {
        this.containingPackage = containingPackage
    }

    override fun containingPackage(): PackageItem =
        containingClass?.containingPackage() ?: containingPackage ?: error(this)

    override fun hasTypeVariables(): Boolean = typeParameterList.isNotEmpty()

    private var superClassType: ClassTypeItem? = null

    override fun superClassType(): ClassTypeItem? = superClassType

    internal fun setSuperClassType(superClassType: ClassTypeItem?) {
        this.superClassType = superClassType
    }

    override fun setInterfaceTypes(interfaceTypes: List<ClassTypeItem>) {
        this.interfaceTypes = interfaceTypes
    }

    /** Must only be used by [type] to cache its result. */
    private lateinit var cachedType: ClassTypeItem

    override fun type(): ClassTypeItem {
        if (!::cachedType.isInitialized) {
            cachedType = DefaultResolvedClassTypeItem.createForClass(this)
        }
        return cachedType
    }

    private var interfaceTypes = emptyList<ClassTypeItem>()
    private val constructors = mutableListOf<ConstructorItem>()
    private val methods = mutableListOf<MethodItem>()
    private val fields = mutableListOf<FieldItem>()
    private val properties = mutableListOf<PropertyItem>()

    override fun constructors(): List<ConstructorItem> = constructors

    override fun methods(): List<MethodItem> = methods

    override fun fields(): List<FieldItem> = fields

    override fun properties(): List<PropertyItem> = properties

    fun addConstructor(constructor: ConstructorItem) {
        constructors += constructor
    }

    override fun addMethod(method: MethodItem) {
        methods += method
    }

    /**
     * Replace an existing method with [method], if no such method exists then just add [method] to
     * the list of methods.
     */
    fun replaceOrAddMethod(method: MethodItem) {
        val iterator = methods.listIterator()
        while (iterator.hasNext()) {
            val existing = iterator.next()
            if (existing == method) {
                iterator.set(method)
                return
            }
        }
        methods += method
    }

    fun addField(field: FieldItem) {
        fields += field
    }

    fun addProperty(property: PropertyItem) {
        properties += property
    }

    fun addNestedClass(cls: ClassItem) {
        nestedClasses.add(cls)
    }

    fun addAnnotation(annotation: AnnotationItem) {
        modifiers.addAnnotation(annotation)
    }

    override fun filteredSuperClassType(predicate: Predicate<Item>): ClassTypeItem? {
        // No filtering in signature files: we assume signature APIs
        // have already been filtered and all items should match.
        // This lets us load signature files and rewrite them using updated
        // output formats etc.
        return superClassType
    }

    private var retention: AnnotationRetention? = null

    override fun getRetention(): AnnotationRetention {
        retention?.let {
            return it
        }

        if (!isAnnotationType()) {
            error("getRetention() should only be called on annotation classes")
        }

        retention = ClassItem.findRetention(this)
        return retention!!
    }

    override fun simpleName(): String = simpleName

    override fun fullName(): String = fullName

    override fun qualifiedName(): String = qualifiedName

    override fun createDefaultConstructor(): ConstructorItem {
        return TextConstructorItem.createDefaultConstructor(codebase, this)
    }
}
