/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.metalava.cli.common.BaseOptionGroupTest

val OPTIONS_HELP =
    """
Miscellaneous:

  Miscellaneous options.

  --nullability-errors-non-fatal             Specifies that errors encountered during validation of nullability
                                             annotations should not be treated as errors. They will be written out to
                                             the file specified in --nullability-warnings-txt instead.
  --nullability-warnings-txt <file>          Specifies where to write warnings encountered during validation of
                                             nullability annotations. (Does not trigger validation by itself.)
  --validate-nullability-from-merged-stubs   Triggers validation of nullability annotations for any class where
                                             --merge-qualifier-annotations includes a Java stub file.
  --validate-nullability-from-list <file>    Triggers validation of nullability annotations for any class listed in the
                                             named file (one top-level class per line, # prefix for comment line).
  --proguard <file>                          Write a ProGuard keep file for the API.
  --sdk-values <dir>                         Write SDK values files to the given directory.
  --extract-annotations <zipfile>            Extracts source annotations from the source files and writes them into the
                                             given zip file.
  --manifest <file>                          A manifest file, used to check permissions to cross check APIs and retrieve
                                             min_sdk_version. (default: no manifest)
    """
        .trimIndent()

class OptionsTest :
    BaseOptionGroupTest<Options>(
        OPTIONS_HELP,
    ) {

    override fun createOptions() = Options()
}
