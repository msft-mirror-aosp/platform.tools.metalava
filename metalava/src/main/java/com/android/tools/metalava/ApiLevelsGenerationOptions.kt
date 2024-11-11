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

package com.android.tools.metalava

import com.android.tools.metalava.cli.common.newFile
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.option
import java.io.File

const val ARG_GENERATE_API_LEVELS = "--generate-api-levels"

class ApiLevelsGenerationOptions :
    OptionGroup(
        name = "Api Levels Generation",
        help =
            """
                Options controlling the API levels file, e.g. `api-versions.xml` file.
            """
                .trimIndent()
    ) {
    /** API level XML file to generate. */
    val generateApiLevelXml: File? by
        option(
                ARG_GENERATE_API_LEVELS,
                metavar = "<xmlfile>",
                help =
                    """
                        Reads android.jar SDK files and generates an XML file recording the API
                        level for each class, method and field
                    """
                        .trimIndent(),
            )
            .newFile()
}
