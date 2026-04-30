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

package com.android.tools.metalava.lint

import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.testing.kotlin
import org.junit.Test

class SealedLintTest : DriverTest() {
    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `members in sealed class are not hidden abstract`() {
        check(
            expectedIssues = "",
            apiLint = "",
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                            package test.pkg

                            sealed class ModifierLocalMap() {
                                internal abstract operator fun <T> set(key: ModifierLocal<T>, value: T)
                                internal abstract operator fun <T> get(key: ModifierLocal<T>): T?
                                internal abstract operator fun contains(key: ModifierLocal<*>): Boolean
                            }
                        """
                    ),
                ),
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `No parameter ordering for sealed class constructor`() {
        check(
            expectedIssues = "",
            apiLint = "",
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                            package test.pkg

                            sealed class Foo(
                                default: Int = 0,
                                required: () -> Unit,
                            )
                        """
                    ),
                ),
        )
    }
}
