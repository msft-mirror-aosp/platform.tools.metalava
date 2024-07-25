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

package com.android.tools.metalava.model.psi

import com.android.tools.metalava.model.AbstractItem
import com.android.tools.metalava.model.ApiVariantSelectors
import com.android.tools.metalava.model.ApiVariantSelectorsFactory
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.android.tools.metalava.reporter.FileLocation
import com.intellij.psi.PsiElement

internal abstract class AbstractPsiItem(
    final override val codebase: PsiBasedCodebase,
    element: PsiElement,
    fileLocation: FileLocation = PsiFileLocation(element),
    modifiers: DefaultModifierList,
    documentationFactory: ItemDocumentationFactory,
    variantSelectorsFactory: ApiVariantSelectorsFactory = ApiVariantSelectors.MUTABLE_FACTORY,
) :
    AbstractItem(
        fileLocation = fileLocation,
        itemLanguage = element.itemLanguage,
        modifiers = modifiers,
        documentationFactory = documentationFactory,
        variantSelectorsFactory = variantSelectorsFactory,
    )
