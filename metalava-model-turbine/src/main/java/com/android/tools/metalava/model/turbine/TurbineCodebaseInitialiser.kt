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

import com.android.tools.metalava.model.DefaultModifierList
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.turbine.binder.Binder
import com.google.turbine.binder.Binder.BindingResult
import com.google.turbine.binder.ClassPathBinder
import com.google.turbine.binder.bound.SourceTypeBoundClass
import com.google.turbine.binder.sym.ClassSymbol
import com.google.turbine.tree.Tree
import com.google.turbine.tree.Tree.CompUnit
import com.google.turbine.tree.Tree.Kind.TY_DECL
import com.google.turbine.tree.Tree.Kind.VAR_DECL
import com.google.turbine.tree.Tree.TyDecl
import com.google.turbine.tree.Tree.VarDecl
import java.io.File
import java.util.Optional

/**
 * This initializer acts as adaptor between codebase and the output from Turbine parser.
 *
 * This is used for populating all the classes,packages and other items from the data present in the
 * parsed Tree
 */
open class TurbineCodebaseInitialiser(
    val units: List<CompUnit>,
    val codebase: TurbineBasedCodebase,
    val classpath: List<File>,
) {

    /** Map items (classitems, methoditems, etc.) with their qualified name. */
    private var itemMap: MutableMap<Tree, String> = mutableMapOf<Tree, String>()

    /** The output from Turbine Binder */
    private lateinit var bindingResult: BindingResult

    /**
     * Initialize uses two passes through units. One pass is for creating all the items and the
     * other pass to set up all the hierarchy.
     *
     * The hierarchy is set up in two parts: one with the use of Turbine binder and other without
     */
    fun initialize() {
        codebase.initialize()

        // First pass for creating items
        for (unit in units) {
            createItems(unit)
        }
        // Create root package
        findOrCreatePackage("")

        // This method sets up hierarchy using only parsed source files
        setInnerClassHierarchy(units)

        try {
            bindingResult =
                Binder.bind(
                    ImmutableList.copyOf(units),
                    ClassPathBinder.bindClasspath(classpath.map { it.toPath() }),
                    ClassPathBinder.bindClasspath(listOf()),
                    Optional.empty()
                )!!
        } catch (e: Throwable) {
            throw e
        }
        setSuperClassHierarchy(bindingResult.units())
    }

    /** Extracts data from the compilation units. A unit corresponds to one parsed source file. */
    private fun createItems(unit: CompUnit) {
        val optPkg = unit.pkg()
        val pkg = if (optPkg.isPresent()) optPkg.get() else null
        var pkgName = ""
        if (pkg != null) {
            val pkgNameList = pkg.name().map { it.value() }
            pkgName = pkgNameList.joinToString(separator = ".")
        }
        val pkgItem = findOrCreatePackage(pkgName)

        val typeDecls = unit.decls()
        for (typeDecl in typeDecls) {
            populateClass(typeDecl, pkgItem, null, true)
        }
    }

    /**
     * Searches for the package with supplied name in the codebase's package map and if not found
     * creates the corresponding TurbinePackageItem and adds it to the package map.
     */
    private fun findOrCreatePackage(name: String): TurbinePackageItem {
        val pkgItem = codebase.findPackage(name)
        if (pkgItem != null) {
            return pkgItem as TurbinePackageItem
        } else {
            val modifers = DefaultModifierList(codebase)
            val turbinePkgItem = TurbinePackageItem(codebase, name, modifers)
            codebase.addPackage(turbinePkgItem)
            return turbinePkgItem
        }
    }

    /** Creates a TurbineClassItem and adds the classitem to the various maps in codebase. */
    private fun populateClass(
        typeDecl: TyDecl,
        pkgItem: TurbinePackageItem,
        containingClass: TurbineClassItem?,
        isTopClass: Boolean,
    ) {
        val className = typeDecl.name().value()
        val fullName = if (isTopClass) className else containingClass?.fullName() + "." + className
        val qualifiedName = pkgItem.qualifiedName() + "." + fullName
        val modifers = DefaultModifierList(codebase)
        val classItem =
            TurbineClassItem(
                codebase,
                className,
                fullName,
                qualifiedName,
                containingClass,
                modifers,
                TurbineClassType.getClassType(typeDecl.tykind()),
            )

        val members = typeDecl.members()
        val fields = mutableListOf<TurbineFieldItem>()
        for (member in members) {
            when (member.kind()) {
                // A class or an interface declaration
                TY_DECL -> {
                    populateClass(member as TyDecl, pkgItem, classItem, false)
                }
                // A field declaration
                VAR_DECL -> {
                    val field = member as VarDecl
                    val fieldItem =
                        TurbineFieldItem(codebase, field.name().value(), classItem, modifers)
                    fields.add(fieldItem)
                }
                else -> {
                    // Do nothing for now
                }
            }
        }
        classItem.fields = fields
        codebase.addClass(classItem, isTopClass)
        itemMap.put(typeDecl, qualifiedName)

        if (isTopClass) {
            classItem.containingPackage = pkgItem
            pkgItem.addTopClass(classItem)
        }
    }

    /** This method sets up inner class hierarchy without using binder. */
    private fun setInnerClassHierarchy(units: List<CompUnit>) {
        for (unit in units) {
            val typeDecls = unit.decls()
            for (typeDecl in typeDecls) {
                setInnerClasses(typeDecl)
            }
        }
    }

    /** Method to setup innerclasses for a single class */
    private fun setInnerClasses(typeDecl: TyDecl) {
        val className = itemMap[typeDecl]!!
        val classItem = codebase.findClass(className)!!
        val innerClasses =
            typeDecl
                .members()
                .filter { it.kind() == TY_DECL }
                .mapNotNull { it ->
                    val memberName = itemMap[it]!!
                    codebase.findClass(memberName)
                }
        classItem.innerClasses = innerClasses
    }

    /** This method uses output from binder to setup superclass and implemented interfaces */
    private fun setSuperClassHierarchy(units: ImmutableMap<ClassSymbol, SourceTypeBoundClass>) {
        for ((sym, cls) in units) {
            val classItem = codebase.findClass(sym.toString())
            if (classItem != null) {

                // Set superclass
                val superClassItem =
                    cls.superclass()?.let { superClass ->
                        codebase.findClass(superClass.toString())
                    }
                classItem.setSuperClass(superClassItem, null)

                // Set direct interfaces
                val interfaces =
                    cls.interfaces().mapNotNull { itf -> codebase.findClass(itf.toString()) }
                classItem.directInterfaces = interfaces
            }
        }
    }
}
