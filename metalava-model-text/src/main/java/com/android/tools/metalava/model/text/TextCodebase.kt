/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.metalava.model.text

import com.android.tools.metalava.model.AnnotationManager
import com.android.tools.metalava.model.ClassResolver
import com.android.tools.metalava.model.item.CodebaseAssemblerFactory
import com.android.tools.metalava.model.item.DefaultCodebase
import java.io.File

// Copy of ApiInfo in doclava1 (converted to Kotlin + some cleanup to make it work with metalava's
// data structures.
// (Converted to Kotlin such that I can inherit behavior via interfaces, in particular Codebase.)
internal class TextCodebase(
    location: File,
    annotationManager: AnnotationManager,
    internal val classResolver: ClassResolver?,
    assemblerFactory: CodebaseAssemblerFactory = { codebase ->
        TextCodebaseAssembler(codebase as TextCodebase)
    },
) :
    DefaultCodebase(
        location = location,
        description = "Codebase",
        preFiltered = true,
        annotationManager = annotationManager,
        trustedApi = true,
        supportsDocumentation = false,
        assemblerFactory = assemblerFactory
    ) {

    init {
        (assembler as TextCodebaseAssembler).initialize()
    }
}
