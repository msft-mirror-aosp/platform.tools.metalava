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

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.SourceFile

internal class TurbineSourceFile(
    val codebase: TurbineBasedCodebase,
    val source: String,
) : SourceFile {

    override fun getHeaderComments(): String? {
        val packageIndex = source.indexOf("package")
        // Return everything before "package" keyword
        return if (packageIndex == -1) "" else source.substring(0, packageIndex)
    }

    override fun classes(): Sequence<ClassItem> = TODO("b/295800205")
}