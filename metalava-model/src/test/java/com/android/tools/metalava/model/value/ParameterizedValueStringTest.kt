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

import com.android.tools.metalava.model.testing.value.arrayValueFromAny
import com.android.tools.metalava.model.testing.value.literalValue
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Tests for [Value.toValueString]. */
@RunWith(Parameterized::class)
class ParameterizedValueStringTest {
    @Parameterized.Parameter(0) lateinit var testCase: TestCase

    class TestCase(
        /** The name of the test. */
        private val name: String,

        /** The value to test. */
        val value: Value,

        /**
         * The expected value returned from `Value.toValueString()`, i.e. with
         * [ValueStringConfiguration.DEFAULT].
         */
        val expectedDefaultString: String,
    ) {
        override fun toString() = name
    }

    companion object {
        /** Create a [TestCase] for [value] with the [expectedDefaultString]. */
        private fun testCase(
            valueLabel: String? = null,
            value: Value,
            expectedDefaultString: String
        ) =
            TestCase(
                "${value.kind},${valueLabel ?: value.toValueString()}",
                value,
                expectedDefaultString,
            )

        private val testCases =
            listOf(
                // ********************************* Arrays *********************************
                testCase(
                    value = arrayValueFromAny(),
                    expectedDefaultString = "{}",
                ),
                testCase(
                    valueLabel = "single integer",
                    value = arrayValueFromAny(1),
                    expectedDefaultString = "{1}",
                ),
                testCase(
                    valueLabel = "single string",
                    value = arrayValueFromAny("single"),
                    expectedDefaultString = "{\"single\"}",
                ),
                testCase(
                    valueLabel = "integers",
                    value = arrayValueFromAny(1, 2, 3),
                    expectedDefaultString = "{1, 2, 3}",
                ),
                testCase(
                    valueLabel = "strings",
                    value = arrayValueFromAny("first", "second", "third"),
                    expectedDefaultString = "{\"first\", \"second\", \"third\"}",
                ),
                // ********************************* Booleans *********************************
                testCase(
                    value = literalValue(true),
                    expectedDefaultString = "true",
                ),
                testCase(
                    value = literalValue(false),
                    expectedDefaultString = "false",
                ),
                // ********************************* Bytes *********************************
                testCase(
                    value = literalValue(0.toByte()),
                    expectedDefaultString = "0",
                ),
                testCase(
                    value = literalValue(Byte.MAX_VALUE),
                    expectedDefaultString = "127",
                ),
                testCase(
                    value = literalValue(Byte.MIN_VALUE),
                    expectedDefaultString = "-128",
                ),
                // ********************************* Chars *********************************
                testCase(
                    value = literalValue('a'),
                    expectedDefaultString = "'a'",
                ),
                testCase(
                    value = literalValue('\t'),
                    expectedDefaultString = "'\\t'",
                ),
                testCase(
                    value = literalValue('\n'),
                    expectedDefaultString = "'\\n'",
                ),
                testCase(
                    value = literalValue('\u1245'),
                    expectedDefaultString = "'\\u1245'",
                ),
                // ********************************* Doubles *********************************
                testCase(
                    value = literalValue(0.0),
                    expectedDefaultString = "0.0",
                ),
                testCase(
                    value = literalValue(Double.MAX_VALUE),
                    expectedDefaultString = "1.7976931348623157E308",
                ),
                testCase(
                    value = literalValue(Double.MIN_VALUE),
                    expectedDefaultString = "4.9E-324",
                ),
                testCase(
                    value = literalValue(Double.NaN),
                    expectedDefaultString = "NaN",
                ),
                testCase(
                    value = literalValue(Double.NEGATIVE_INFINITY),
                    expectedDefaultString = "-Infinity",
                ),
                testCase(
                    value = literalValue(Double.POSITIVE_INFINITY),
                    expectedDefaultString = "Infinity",
                ),
                // ********************************* Floats *********************************
                testCase(
                    value = literalValue(0.0f),
                    expectedDefaultString = "0.0f",
                ),
                testCase(
                    value = literalValue(Float.MAX_VALUE),
                    expectedDefaultString = "3.4028235E38f",
                ),
                testCase(
                    value = literalValue(Float.MIN_VALUE),
                    expectedDefaultString = "1.4E-45f",
                ),
                testCase(
                    value = literalValue(Float.NaN),
                    expectedDefaultString = "NaN",
                ),
                testCase(
                    value = literalValue(Float.NEGATIVE_INFINITY),
                    expectedDefaultString = "-Infinity",
                ),
                testCase(
                    value = literalValue(Float.POSITIVE_INFINITY),
                    expectedDefaultString = "Infinity",
                ),
                // ********************************* Ints *********************************
                testCase(
                    value = literalValue(0),
                    expectedDefaultString = "0",
                ),
                testCase(
                    value = literalValue(Int.MAX_VALUE),
                    expectedDefaultString = "2147483647",
                ),
                testCase(
                    value = literalValue(Int.MIN_VALUE),
                    expectedDefaultString = "-2147483648",
                ),
                // ********************************* Longs *********************************
                testCase(
                    value = literalValue(0L),
                    expectedDefaultString = "0L",
                ),
                testCase(
                    value = literalValue(Long.MAX_VALUE),
                    expectedDefaultString = "9223372036854775807L",
                ),
                testCase(
                    value = literalValue(Long.MIN_VALUE),
                    expectedDefaultString = "-9223372036854775808L",
                ),
                // ********************************* Shorts *********************************
                testCase(
                    value = literalValue(0.toShort()),
                    expectedDefaultString = "0",
                ),
                testCase(
                    value = literalValue(Short.MAX_VALUE),
                    expectedDefaultString = "32767",
                ),
                testCase(
                    value = literalValue(Short.MIN_VALUE),
                    expectedDefaultString = "-32768",
                ),
                // ********************************* Strings *********************************
                testCase(
                    value = literalValue("string"),
                    expectedDefaultString = "\"string\"",
                ),
                testCase(
                    value = literalValue("str\ting\n"),
                    expectedDefaultString = "\"str\\ting\\n\"",
                ),
                testCase(
                    value = literalValue("str\u89EFing"),
                    expectedDefaultString = "\"str\\u89efing\"",
                ),
            )

        /** Supply the list of creation tests as the parameters for this test class. */
        @JvmStatic @Parameterized.Parameters(name = "{0}") fun params() = testCases
    }

    @Test
    fun `toValueString test`() {
        assertEquals(testCase.expectedDefaultString, testCase.value.toValueString())
    }
}
