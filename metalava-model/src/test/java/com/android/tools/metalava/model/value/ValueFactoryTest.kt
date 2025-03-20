/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.metalava.model.value

import com.android.tools.metalava.model.testing.stringType
import kotlin.test.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Tests [ValueFactory] behavior that is not covered by the other parameterized tests that test
 * different aspects of this.
 */
class ValueFactoryTest {
    @Test
    fun `createLiteralValue - no type item - invalid underlyingValue`() {
        val exception =
            assertThrows(IllegalStateException::class.java) {
                Value.createLiteralValue(null, ValueFactoryTest::class.java)
            }

        assertEquals(
            "Underlying value 'class com.android.tools.metalava.model.value.ValueFactoryTest' of class java.lang.Class is not supported",
            exception.message
        )
    }

    @Test
    fun `createLiteralValue - string type item - not string`() {
        val exception =
            assertThrows(IllegalStateException::class.java) {
                Value.createLiteralValue(stringType(), ValueFactoryTest::class.java)
            }

        assertEquals(
            "Incompatible type 'java.lang.String', for underlying value `class com.android.tools.metalava.model.value.ValueFactoryTest` of class java.lang.Class",
            exception.message
        )
    }

    @Test
    fun `createLiteralValue - string type item - string`() {
        val value = Value.createLiteralValue(stringType(), "string")
        assertEquals(DefaultStringValue("string"), value)
        assertEquals("string", value.underlyingValue)
    }

    @Test
    fun `createLiteralValue - no type item - string`() {
        val value = Value.createLiteralValue(null, "string")
        assertEquals(DefaultStringValue("string"), value)
        assertEquals("string", value.underlyingValue)
    }
}
