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

package com.android.tools.metalava.model.testsuite.value

import com.android.tools.metalava.model.testsuite.value.TestClassCreator.Companion.ATTRIBUTE_NAME
import com.android.tools.metalava.model.testsuite.value.ValueUseSite.ATTRIBUTE_VALUE
import com.android.tools.metalava.testing.TestFileCache
import com.android.tools.metalava.testing.TestFileCacheRule
import kotlin.test.assertEquals
import org.junit.ClassRule
import org.junit.runners.Parameterized

/** Run parameterized tests for [ATTRIBUTE_VALUE]. */
class CommonParameterizedAttributeValueTest :
    BaseCommonParameterizedValueTest(testFileCacheRule.cache, testJarFile) {
    companion object : BaseCompanion(ATTRIBUTE_VALUE) {
        /** Create a [TestFileCache] whose lifespan encompasses all the tests in this class. */
        @ClassRule @JvmField val testFileCacheRule = TestFileCacheRule()

        /** Supply the list of test cases as the parameters for this test class. */
        @JvmStatic @Parameterized.Parameters fun params() = testParameters
    }

    override fun TestCaseContext.runTestCase() {
        val annotation = testClassItem.modifiers.annotations().first()
        val annotationAttribute = annotation.assertAttribute(ATTRIBUTE_NAME)

        // Get the expected value.
        val expected =
            expectation.expectationFor(
                producerKind,
                ATTRIBUTE_VALUE,
                codebase,
            )
        assertEquals(expected, annotationAttribute.value.toSource())
    }
}
