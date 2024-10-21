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

package com.android.tools.metalava.model.testsuite.classitem

import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.kotlin
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Test

class CommonValueClassTest : BaseModelTest() {
    @Test
    fun `Constructor with optional value`() {
        runCodebaseTest(
            kotlin(
                """
                package test.pkg
                @JvmInline
                value class ValueClass(val value: Int = 0)
            """
            )
        ) {
            val valueClass = codebase.assertClass("test.pkg.ValueClass")
            assertEquals(valueClass.constructors().size, 1, "Expected exactly one constructor")
            assertNotNull(valueClass.primaryConstructor, "Expected a primary constructor")

            val primaryConstructor = valueClass.constructors().single()
            assertTrue(primaryConstructor.isPrimary, "Expected a primary constructor")
            val param = primaryConstructor.parameters().single()
            assertTrue(param.hasDefaultValue(), "Expected a default value")
            assertEquals(param.defaultValue.value(), "0", "Expected a default value of 0")
        }
    }
}
