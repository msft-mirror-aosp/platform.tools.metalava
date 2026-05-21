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

package com.android.tools.metalava

import com.android.tools.metalava.testing.java
import org.junit.Test

class OverlappingApiSurfacesTest : DriverTest() {
    private fun checkOverlap(
        apiSurface: KnownApiSurface,
        expectedIssues: String,
    ) {
        check(
            apiSurface = apiSurface,
            expectedIssues = expectedIssues,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package test.pkg;
                            import test.annotation.ModuleApi;
                            import test.annotation.SystemApi;
                            import test.annotation.TestApi;
                            public class Test {
                                // System and module overlap because module extends system.
                                @SystemApi
                                @ModuleApi
                                public void systemAndModuleLib() {}

                                // Test and module do not overlap because while they both extend
                                // system, neither of them extends the other.
                                @TestApi
                                @ModuleApi
                                public void systemAndModuleLib() {}

                                // System and test overlap because test extends system.
                                @TestApi
                                @SystemApi
                                public void testAndSystem() {}
                            }
                        """
                    ),
                ),
        )
    }

    @Test
    fun `Test overlap - system`() {
        checkOverlap(
            apiSurface = KnownApiSurface.TEST_SYSTEM_API_SURFACE,
            // When targeting the system API the module and test annotations are treated as hide
            // annotations so it cannot detect any overlap with them.
            expectedIssues = "",
        )
    }

    @Test
    fun `Test overlap - module`() {
        checkOverlap(
            apiSurface = KnownApiSurface.TEST_MODULE_API_SURFACE,
            // The module and test APIs do not extend each other so they cannot overlap.
            // TODO(b/514715467): Should detect an overlap between module and system
            expectedIssues =
                """
                """,
        )
    }

    @Test
    fun `Test overlap - test`() {
        checkOverlap(
            apiSurface = KnownApiSurface.TEST_API_SURFACE,
            // The module and test APIs do not extend each other so they cannot overlap.
            // TODO(b/514715467): Should detect an overlap between test and system
            expectedIssues =
                """
                """,
        )
    }
}
