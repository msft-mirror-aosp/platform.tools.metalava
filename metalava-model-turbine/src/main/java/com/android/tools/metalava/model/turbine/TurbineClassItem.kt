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

import com.android.tools.metalava.model.AnnotationRetention
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterList

open class TurbineClassItem(
    override val codebase: Codebase,
    private val name: String,
    private val fullName: String,
    private val qualifiedName: String,
    private val containingClass: TurbineClassItem?,
    override val modifiers: TurbineModifierItem,
    private val classType: TurbineClassType,
) : ClassItem, TurbineItem(codebase = codebase, modifiers = modifiers) {

    override var artifact: String? = null

    override var hasPrivateConstructor: Boolean = false

    override val isTypeParameter: Boolean = false

    override var stubConstructor: ConstructorItem? = null

    internal lateinit var innerClasses: List<TurbineClassItem>

    private var superClass: TurbineClassItem? = null

    private var superClassType: TypeItem? = null

    internal lateinit var directInterfaces: List<TurbineClassItem>

    private var allInterfaces: List<TurbineClassItem>? = null

    internal lateinit var containingPackage: TurbinePackageItem

    internal lateinit var fields: List<TurbineFieldItem>

    override fun allInterfaces(): Sequence<TurbineClassItem> {
        if (allInterfaces == null) {
            val interfaces = mutableSetOf<TurbineClassItem>()

            // Add self as interface if applicable
            if (isInterface()) {
                interfaces.add(this)
            }

            // Add all the interfaces of super class
            superClass()?.let { supClass ->
                supClass.allInterfaces().forEach { interfaces.add(it) }
            }

            // Add all the interfaces of direct interfaces
            directInterfaces.map { itf -> itf.allInterfaces().forEach { interfaces.add(it) } }

            allInterfaces = interfaces.toList()
        }

        return allInterfaces!!.asSequence()
    }

    override fun constructors(): List<ConstructorItem> {
        TODO("b/295800205")
    }

    override fun containingClass(): ClassItem? = containingClass

    override fun containingPackage(): PackageItem =
        containingClass?.containingPackage() ?: containingPackage

    override fun fields(): List<FieldItem> = fields

    override fun getRetention(): AnnotationRetention {
        TODO("b/295800205")
    }

    override fun hasImplicitDefaultConstructor(): Boolean {
        TODO("b/295800205")
    }

    override fun hasTypeVariables(): Boolean {
        TODO("b/295800205")
    }

    override fun innerClasses(): List<ClassItem> = innerClasses

    override fun interfaceTypes(): List<TypeItem> {
        TODO("b/295800205")
    }

    override fun isAnnotationType(): Boolean = classType == TurbineClassType.ANNOTATION

    override fun isDefined(): Boolean {
        TODO("b/295800205")
    }

    override fun isEnum(): Boolean = classType == TurbineClassType.ENUM

    override fun isInterface(): Boolean = classType == TurbineClassType.INTERFACE

    override fun methods(): List<MethodItem> {
        TODO("b/295800205")
    }

    override fun properties(): List<PropertyItem> {
        TODO("b/295800205")
    }

    override fun simpleName(): String = name

    override fun qualifiedName(): String = qualifiedName

    override fun fullName(): String = fullName

    override fun setInterfaceTypes(interfaceTypes: List<TypeItem>) {
        TODO("b/295800205")
    }

    override fun setSuperClass(superClass: ClassItem?, superClassType: TypeItem?) {
        this.superClass = superClass as? TurbineClassItem
        this.superClassType = superClassType
    }

    override fun superClass(): TurbineClassItem? = superClass

    override fun superClassType(): TypeItem? = superClassType

    override fun toType(): TypeItem {
        TODO("b/295800205")
    }

    override fun typeParameterList(): TypeParameterList {
        TODO("b/295800205")
    }

    override fun hashCode(): Int = qualifiedName.hashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        return other is ClassItem && qualifiedName() == other.qualifiedName()
    }
}
