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

package com.android.tools.metalava.model.turbine

import com.android.tools.metalava.model.ApiVariantSelectors
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.DefaultCodebase
import com.android.tools.metalava.model.DefaultItem
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.ItemDocumentation
import com.android.tools.metalava.model.ItemLanguage
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.reporter.FileLocation

internal class TurbinePackageItem(
    codebase: DefaultCodebase,
    fileLocation: FileLocation,
    modifiers: DefaultModifierList,
    documentation: ItemDocumentation,
    private val qualifiedName: String,
) :
    DefaultItem(
        codebase = codebase,
        fileLocation = fileLocation,
        itemLanguage = ItemLanguage.JAVA,
        modifiers = modifiers,
        documentation = documentation,
        variantSelectorsFactory = ApiVariantSelectors.MUTABLE_FACTORY,
    ),
    PackageItem {

    private var topClasses = mutableListOf<TurbineClassItem>()

    private var containingPackage: PackageItem? = null

    override fun qualifiedName(): String = qualifiedName

    override fun topLevelClasses(): List<ClassItem> = topClasses.toList()

    // N.A. a package cannot be contained in a class
    override fun containingClass(): ClassItem? = null

    override fun containingPackage(): PackageItem? {
        // if this package is root package, then return null
        return if (qualifiedName.isEmpty()) null
        else {
            if (containingPackage == null) {
                // If package is of the form A.B then the containing package is A
                // If package is top level, then containing package is the root package
                val name = qualifiedName()
                val lastDot = name.lastIndexOf('.')
                containingPackage =
                    if (lastDot != -1) codebase.findPackage(name.substring(0, lastDot))
                    else codebase.findPackage("")
            }
            return containingPackage
        }
    }

    internal fun addTopClass(classItem: TurbineClassItem) {
        topClasses.add(classItem)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        return other is PackageItem && qualifiedName == other.qualifiedName()
    }

    override fun hashCode(): Int = qualifiedName.hashCode()

    companion object {
        fun create(
            codebase: DefaultCodebase,
            fileLocation: FileLocation = FileLocation.UNKNOWN,
            modifiers: DefaultModifierList = DefaultModifierList(codebase),
            documentation: ItemDocumentation = ItemDocumentation.NONE,
            qualifiedName: String,
        ): TurbinePackageItem {
            modifiers.setVisibilityLevel(VisibilityLevel.PUBLIC)
            return TurbinePackageItem(
                codebase,
                fileLocation,
                modifiers,
                documentation,
                qualifiedName,
            )
        }
    }
}
