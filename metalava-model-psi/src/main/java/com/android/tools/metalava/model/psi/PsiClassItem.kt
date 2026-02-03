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

package com.android.tools.metalava.model.psi

import com.android.tools.metalava.model.ClassItem
import com.intellij.psi.PsiClass
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.uast.UClass

internal class PsiClassItem {
    companion object {
        /** Whether the [psiClass] is a file-facade class. See [ClassItem.isFileFacade]. */
        fun isFileFacade(psiClass: PsiClass): Boolean {
            return psiClass.isKotlin() &&
                psiClass is UClass &&
                psiClass.javaPsi is KtLightClassForFacade
        }

        /** Whether the [psiClass] is a multi-file class. See [ClassItem.isMultiFileClass]. */
        fun isMultiFileClass(psiClass: PsiClass) =
            ((psiClass as? UClass)?.javaPsi as? KtLightClassForFacade)?.multiFileClass == true
    }
}
