/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.tools.metalava.model.source

import com.android.tools.metalava.model.ClassPathResolver
import com.android.tools.metalava.model.item.DefaultCodebase
import java.io.File

abstract class AbstractSourceParser : SourceParser {
    final override fun getClassPathResolver(classPath: List<File>): ClassPathResolver =
        loadCodebaseFromJars(classPath, "Codebase from classpath")

    /** Load a [DefaultCodebase] from a set of [jars]. */
    protected open fun loadCodebaseFromJars(
        jars: List<File>,
        description: String,
        sourceSet: SourceSet = SourceSet.empty(),
    ): DefaultCodebase {
        val codebase =
            parseSources(
                sourceSet = sourceSet,
                description = description,
                classPath = jars,
            ) ?: error("Could not create codebase from $jars")

        return codebase as DefaultCodebase
    }
}
