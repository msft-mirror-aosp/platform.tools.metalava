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

import com.android.tools.metalava.testing.java
import org.junit.Test

class DuplicateClassTest : DriverTest() {

    @Test
    fun `Test duplicate classes`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;

                            public class Foo {}
                        """
                    ),
                    java(
                        "src2/test/pkg/Foo.java",
                        """
                            package test.pkg;

                            public class Foo {}
                        """
                    )
                ),
            expectedIssues =
                """
                    src2/test/pkg/Foo.java:3: warning: Ignoring this duplicate definition of test.pkg.Foo; previous definition was loaded from TESTROOT/src/test/pkg/Foo.java [DuplicateSourceClass]
                """,
        )
    }
}
