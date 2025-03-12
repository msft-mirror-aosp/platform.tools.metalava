/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.tools.metalava.model.item

import com.android.tools.metalava.model.ApiVariantSelectorsFactory
import com.android.tools.metalava.model.BaseModifierList
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.android.tools.metalava.model.ItemLanguage
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.TypeAliasItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.reporter.FileLocation

open class DefaultTypeAliasItem(
    codebase: DefaultCodebase,
    fileLocation: FileLocation,
    modifiers: BaseModifierList,
    documentationFactory: ItemDocumentationFactory,
    variantSelectorsFactory: ApiVariantSelectorsFactory,
    final override val aliasedType: TypeItem,
    final override val qualifiedName: String,
    final override val typeParameterList: TypeParameterList,
    private val containingPackage: DefaultPackageItem,
) :
    TypeAliasItem,
    DefaultSelectableItem(
        codebase = codebase,
        fileLocation = fileLocation,
        // Type aliases only exist in Kotlin
        itemLanguage = ItemLanguage.KOTLIN,
        modifiers = modifiers,
        documentationFactory = documentationFactory,
        variantSelectorsFactory = variantSelectorsFactory,
    ) {

    init {
        // Register the new type alias with the codebase and package. Leaking `this` is ok as it
        // only uses its qualified name, which has been initialized.
        codebase.addTypeAlias(@Suppress("LeakingThis") this)
        containingPackage.addTypeAlias(@Suppress("LeakingThis") this)

        // If this type alias is emittable then make sure its package is too.
        if (emit) {
            containingPackage.emit = true
        }
    }

    override val simpleName: String = qualifiedName.substringAfterLast(".")

    override fun containingPackage(): PackageItem = containingPackage
}
