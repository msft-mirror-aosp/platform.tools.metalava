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

package com.android.tools.metalava.cli.common

import com.android.tools.metalava.model.AnnotationManager
import com.android.tools.metalava.model.ClassResolver
import com.android.tools.metalava.model.text.ApiFile
import com.android.tools.metalava.model.text.ApiParseException
import com.android.tools.metalava.model.text.TextCodebase
import java.io.File

/**
 * Helper object to load signature files and rethrow any [ApiParseException] as a
 * [MetalavaCliException].
 */
object SignatureFileLoader {
    fun load(
        file: File,
        classResolver: ClassResolver? = null,
        annotationManager: AnnotationManager,
    ): TextCodebase {
        return loadFiles(listOf(file), classResolver, annotationManager)
    }

    fun loadFiles(
        files: List<File>,
        classResolver: ClassResolver? = null,
        annotationManager: AnnotationManager,
    ): TextCodebase {
        require(files.isNotEmpty()) { "files must not be empty" }

        try {
            return ApiFile.parseApi(files, classResolver, annotationManager)
        } catch (ex: ApiParseException) {
            throw MetalavaCliException("Unable to parse signature file: ${ex.message}")
        }
    }
}
