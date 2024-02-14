/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.metalava.model.DefaultTypeParameterList
import com.android.tools.metalava.model.TypeParameterItem
import com.android.tools.metalava.model.TypeParameterList
import com.intellij.psi.PsiTypeParameterListOwner

internal class PsiTypeParameterList(
    val codebase: PsiBasedCodebase,
    private val typeParameters: List<TypeParameterItem>,
) : DefaultTypeParameterList() {

    override fun typeParameters() = typeParameters

    companion object {
        fun create(
            codebase: PsiBasedCodebase,
            enclosingTypeItemFactory: PsiTypeItemFactory,
            scopeDescription: String,
            psiOwner: PsiTypeParameterListOwner
        ): Pair<TypeParameterList, PsiTypeItemFactory> {
            val psiTypeParameterList =
                psiOwner.typeParameterList
                    ?: return Pair(TypeParameterList.NONE, enclosingTypeItemFactory)

            val typeParameters =
                psiTypeParameterList.typeParameters.map {
                    PsiTypeParameterItem.create(codebase, it)
                }

            // Get a nested factory containing any new parameters.
            val typeItemFactory =
                enclosingTypeItemFactory.nestedFactory(scopeDescription, typeParameters)

            return Pair(PsiTypeParameterList(codebase, typeParameters), typeItemFactory)
        }
    }
}
