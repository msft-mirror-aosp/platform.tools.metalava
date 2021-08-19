/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.metalava.model.kotlin

import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.psi.PsiBasedCodebase
import org.jetbrains.kotlin.psi.KtElement

interface KotlinItem : Item {
    override val codebase: PsiBasedCodebase
    val element: KtElement
    override val modifiers: KotlinModifierList
    override var documentation: String

    // KotlinClassItem tracks if it came from source or class path, members
    override fun isFromClassPath(): Boolean {
        return containingClass()?.isFromClassPath() ?: false
    }
}
