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

val SOURCE_OPTIONS_HELP =
    """
Sources:

  Options that control which source files will be processed.

  --common-source-path <path>                A : separated list of directories containing common source files (organized
                                             in a standard Java package hierarchy). Common source files are where
                                             platform-agnostic `expect` declarations for Kotlin multi-platform code as
                                             well as common business logic are defined.
  --source-path <path>                       A : separated list of directories containing source files (organized in a
                                             standard Java package hierarchy).
    """
        .trimIndent()

class SourceOptionsTest :
    BaseOptionGroupTest<SourceOptions>(
        SOURCE_OPTIONS_HELP,
    ) {

    override fun createOptions() = SourceOptions()
}
