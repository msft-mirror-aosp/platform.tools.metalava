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

import com.android.tools.metalava.model.DefaultItem
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.source.utils.LazyDelegate
import com.android.tools.metalava.reporter.FileLocation

internal abstract class TurbineItem(
    final override val codebase: TurbineBasedCodebase,
    fileLocation: FileLocation,
    modifiers: DefaultModifierList,
    documentation: String,
) :
    DefaultItem(
        fileLocation = fileLocation,
        modifiers = modifiers,
        documentation = documentation,
    ) {

    final override val originallyHidden by
        lazy(LazyThreadSafetyMode.NONE) {
            documentation.contains('@') &&
                (documentation.contains("@hide") ||
                    documentation.contains("@pending") ||
                    // KDoc:
                    documentation.contains("@suppress")) || hasHideAnnotation()
        }

    final override var hidden: Boolean by LazyDelegate { originallyHidden && !hasShowAnnotation() }

    final override fun appendDocumentation(comment: String, tagSection: String?, append: Boolean) {
        TODO("b/295800205")
    }

    final override fun findTagDocumentation(tag: String, value: String?): String? {
        TODO("b/295800205")
    }
}
