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

package com.android.tools.metalava.config

import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.metalava.DriverTest
import org.junit.Test

class ConfigTest : DriverTest() {
    @Test
    fun `Empty config file`() {
        check(
            configFiles =
                arrayOf(
                    TestFiles.xml(
                        "config.xml",
                        """
                            <config xmlns="http://www.google.com/tools/metalava/config"/>
                        """,
                    )
                ),
        )
    }

    @Test
    fun `Invalid config file`() {
        check(
            configFiles =
                arrayOf(
                    TestFiles.xml(
                        "config.xml",
                        """
                            <invalid xmlns="http://www.google.com/tools/metalava/config"/>
                        """,
                    )
                ),
            expectedIssues =
                "config.xml:1: error: Problem parsing configuration file: cvc-elt.1.a: Cannot find the declaration of element 'invalid'. [ConfigFileProblem]",
        )
    }
}