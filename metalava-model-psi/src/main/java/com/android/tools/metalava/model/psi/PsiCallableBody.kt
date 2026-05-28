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

import com.android.tools.metalava.model.CallableBody
import com.android.tools.metalava.model.CallableItem
import com.intellij.psi.PsiMethod

internal class PsiCallableBody(
    private val psiCodebase: PsiBasedCodebase,
    private val psiMethod: PsiMethod,
) : CallableBody {
    override fun duplicate(callableItem: CallableItem) = PsiCallableBody(psiCodebase, psiMethod)

    // Cannot create a copy of this as callableItem cannot be cast to PsiCallableItem. There is no
    // easy way to capture the state of this sufficiently well to implement the necessary behavior
    // so just pretend it is unavailable for now.
    override fun snapshot(callableItem: CallableItem): CallableBody {
        return CallableBody.UNAVAILABLE
    }
}
