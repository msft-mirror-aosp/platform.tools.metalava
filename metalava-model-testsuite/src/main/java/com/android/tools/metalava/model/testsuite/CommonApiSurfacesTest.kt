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

package com.android.tools.metalava.model.testsuite

import com.android.tools.metalava.model.api.surface.ApiSurfaces
import com.android.tools.metalava.testing.java
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class CommonApiSurfacesTest : BaseModelTest() {

    @Test
    fun `Test Codebase apiSurfaces default`() {
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Test {
                        ctor public Test();
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public class Test {
                        public Test() {}
                    }
                """
            ),
        ) {
            val apiSurfaces = codebase.apiSurfaces
            assertEquals("main", apiSurfaces.main.name, "main name")
            assertNull(apiSurfaces.base, "base not expected")
        }
    }

    @Test
    fun `Test Codebase apiSurfaces with base`() {
        val fixtureApiSurfaces = ApiSurfaces.create(needsBase = true)
        runCodebaseTest(
            signature(
                """
                    // Signature format: 2.0
                    package test.pkg {
                      public class Test {
                        ctor public Test();
                      }
                    }
                """
            ),
            java(
                """
                    package test.pkg;

                    public class Test {
                        public Test() {}
                    }
                """
            ),
            testFixture =
                TestFixture(
                    apiSurfaces = fixtureApiSurfaces,
                ),
        ) {
            val apiSurfaces = codebase.apiSurfaces
            assertEquals("main", apiSurfaces.main.name, "main name")
            assertEquals("base", apiSurfaces.base?.name, "base name")
        }
    }
}
