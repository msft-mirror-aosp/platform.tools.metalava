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

import com.android.tools.metalava.model.AnnotationRetention
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterList
import java.util.function.Predicate

internal open class TextClassItem(
    override val codebase: TextCodebase,
    position: SourcePositionInfo = SourcePositionInfo.UNKNOWN,
    modifiers: DefaultModifierList,
    override val classKind: ClassKind = ClassKind.CLASS,
    val qualifiedName: String = "",
    var simpleName: String = qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1),
    val fullName: String = simpleName,
    val annotations: List<String>? = null,
    val typeParameterList: TypeParameterList = TypeParameterList.NONE
) : TextItem(codebase = codebase, position = position, modifiers = modifiers), ClassItem {

    override var artifact: String? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TextClassItem

        return qualifiedName == other.qualifiedName()
    }

    override fun hashCode(): Int {
        return qualifiedName.hashCode()
    }

    override fun interfaceTypes(): List<ClassTypeItem> = interfaceTypes

    override fun allInterfaces(): Sequence<ClassItem> {
        return sequenceOf(
                // Add this if and only if it is an interface.
                if (classKind == ClassKind.INTERFACE) sequenceOf(this) else emptySequence(),
                interfaceTypes.asSequence().map { it.asClass() }.filterNotNull(),
            )
            .flatten()
    }

    private var innerClasses: MutableList<ClassItem> = mutableListOf()

    override var stubConstructor: ConstructorItem? = null

    override var hasPrivateConstructor: Boolean = false

    override fun innerClasses(): List<ClassItem> = innerClasses

    override fun hasImplicitDefaultConstructor(): Boolean {
        return false
    }

    var containingClass: ClassItem? = null

    override fun containingClass(): ClassItem? = containingClass

    private var containingPackage: PackageItem? = null

    fun setContainingPackage(containingPackage: TextPackageItem) {
        this.containingPackage = containingPackage
    }

    override fun containingPackage(): PackageItem =
        containingClass?.containingPackage() ?: containingPackage ?: error(this)

    override fun hasTypeVariables(): Boolean = typeParameterList.typeParameterCount() > 0

    override fun typeParameterList(): TypeParameterList = typeParameterList

    private var superClassType: ClassTypeItem? = null

    override fun superClass(): ClassItem? = superClassType?.asClass()

    override fun superClassType(): ClassTypeItem? = superClassType

    internal fun setSuperClassType(superClassType: ClassTypeItem?) {
        this.superClassType = superClassType
    }

    override fun setInterfaceTypes(interfaceTypes: List<ClassTypeItem>) {
        this.interfaceTypes = interfaceTypes
    }

    private var typeInfo: TextClassTypeItem? = null

    override fun type(): TextClassTypeItem {
        if (typeInfo == null) {
            val params = typeParameterList.typeParameters().map { it.type() }
            // Create a [TextTypeItem] representing the type of this class.
            typeInfo =
                TextClassTypeItem(
                    codebase,
                    qualifiedName,
                    params,
                    containingClass()?.type(),
                    codebase.emptyTypeModifiers,
                )
        }
        return typeInfo!!
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

    fun addConstructor(constructor: TextConstructorItem) {
        constructors += constructor
    }

    fun addMethod(method: TextMethodItem) {
        methods += method
    }

    fun addField(field: TextFieldItem) {
        fields += field
    }

    fun addProperty(property: TextPropertyItem) {
        properties += property
    }

    fun addEnumConstant(field: TextFieldItem) {
        field.setEnumConstant(true)
        fields += field
    }

    override fun addInnerClass(cls: ClassItem) {
        innerClasses.add(cls)
    }

    override fun filteredSuperClassType(predicate: Predicate<Item>): TypeItem? {
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

    override fun isDefined(): Boolean {
        assert(emit == (position != SourcePositionInfo.UNKNOWN))
        return emit
    }

    override fun toString(): String = "class ${qualifiedName()}"

    override fun createDefaultConstructor(): ConstructorItem {
        return TextConstructorItem.createDefaultConstructor(codebase, this, position)
    }

    companion object {
        internal fun createStubClass(
            codebase: TextCodebase,
            qualifiedName: String,
            isInterface: Boolean
        ): TextClassItem {
            val fullName = getFullName(qualifiedName)
            val cls =
                TextClassItem(
                    codebase = codebase,
                    qualifiedName = qualifiedName,
                    fullName = fullName,
                    classKind = if (isInterface) ClassKind.INTERFACE else ClassKind.CLASS,
                    modifiers = DefaultModifierList(codebase, DefaultModifierList.PUBLIC),
                )
            cls.emit = false // it's a stub

            return cls
        }

        private fun getFullName(qualifiedName: String): String {
            var end = -1
            val length = qualifiedName.length
            var prev = qualifiedName[length - 1]
            for (i in length - 2 downTo 0) {
                val c = qualifiedName[i]
                if (c == '.' && prev.isUpperCase()) {
                    end = i + 1
                }
                prev = c
            }
            if (end != -1) {
                return qualifiedName.substring(end)
            }

            return qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1)
        }
    }
}
