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

package com.android.tools.metalava.cli.common

import com.android.SdkConstants
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.option
import java.io.File

const val ARG_COMMON_SOURCE_PATH = "--common-source-path"
const val ARG_SOURCE_PATH = "--source-path"

/** The name of the group, can be used in help text to refer to the options in this group. */
const val SOURCE_OPTIONS_GROUP = "Sources"

class SourceOptions :
    OptionGroup(
        name = SOURCE_OPTIONS_GROUP,
        help =
            """
            Options that control which source files will be processed.
        """
                .trimIndent()
    ) {

    private val commonSourcePathString by
        option(
            ARG_COMMON_SOURCE_PATH,
            metavar = "<path>",
            help =
                """
                    A ${File.pathSeparator} separated list of directories containing common source
                    files (organized in a standard Java package hierarchy). Common source files
                    are where platform-agnostic `expect` declarations for Kotlin multi-platform code
                    as well as common business logic are defined.
                """
                    .trimIndent(),
        )

    private val sourcePathString by
        option(
            ARG_SOURCE_PATH,
            metavar = "<path>",
            help =
                """
                    A ${File.pathSeparator} separated list of directories containing source
                    files (organized in a standard Java package hierarchy).
                """
                    .trimIndent(),
        )

    internal val commonSourcePath by
        lazy(LazyThreadSafetyMode.NONE) {
            getSourcePath(ARG_COMMON_SOURCE_PATH, commonSourcePathString)
        }

    internal val sourcePath by
        lazy(LazyThreadSafetyMode.NONE) { getSourcePath(ARG_SOURCE_PATH, sourcePathString) }

    private fun getSourcePath(argName: String, path: String?) =
        if (path == null) {
            emptyList()
        } else if (path.isBlank()) {
            // Don't compute absolute path; we want to skip this file later on.
            // For current directory one should use ".", not "".
            listOf(File(""))
        } else {
            path.split(File.pathSeparator).map {
                if (it.endsWith(SdkConstants.DOT_JAVA)) {
                    throw MetalavaCliException(
                        "$argName should point to a source root directory, not a source file ($it)"
                    )
                }

                stringToExistingDir(it)
            }
        }
}