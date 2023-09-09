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

import com.android.tools.metalava.model.AnnotationManager
import com.android.tools.metalava.model.ClassResolver
import com.android.tools.metalava.model.source.SourceCodebase
import com.android.tools.metalava.model.source.SourceParser
import java.io.File

internal class TurbineSourceParser(private val annotationManager: AnnotationManager) :
    SourceParser {

    override fun getClassResolver(classPath: List<File>): ClassResolver {
        TODO("implement it")
    }

    /**
     * Returns a codebase initialized from the given Java source files, with the given description.
     */
    override fun parseSources(
        sources: List<File>,
        description: String,
        sourcePath: List<File>,
        classPath: List<File>,
    ): TurbineBasedCodebase {
        val rootDir = sourcePath.firstOrNull() ?: File("").canonicalFile
        val codebase = TurbineBasedCodebase(rootDir, description, annotationManager)
        return codebase
    }

    override fun loadFromJar(apiJar: File, preFiltered: Boolean): SourceCodebase {
        TODO("b/299044569 handle this")
    }
}
