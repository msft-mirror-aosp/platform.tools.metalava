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

package com.android.tools.metalava.apilevels

import kotlin.test.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PatternNodeTest {

    fun PatternNode.assertStructure(expected: String) {
        assertEquals(expected.trimIndent(), dump().trimIndent())
    }

    @Test
    fun `Invalid no API placeholder`() {
        val patterns =
            listOf(
                "prebuilts/sdk/3/public/android.jar",
            )

        val exception =
            assertThrows(IllegalStateException::class.java) { PatternNode.parsePatterns(patterns) }
        assertEquals(
            "Pattern 'prebuilts/sdk/3/public/android.jar' does not contain '%'",
            exception.message
        )
    }

    @Test
    fun `Invalid multiple API placeholders`() {
        val patterns =
            listOf(
                "prebuilts/sdk/%/public/android-%.jar",
            )

        val exception =
            assertThrows(IllegalStateException::class.java) { PatternNode.parsePatterns(patterns) }
        assertEquals(
            "Pattern 'prebuilts/sdk/%/public/android-%.jar' contains more than one '%'",
            exception.message
        )
    }

    @Test
    fun `Parse common Android public patterns`() {
        val patterns =
            listOf(
                "prebuilts/sdk/%/public/android.jar",
                // This pattern never matches, but it is provided by Soong as it treats all
                // directories as being the same structure as prebuilts/sdk.
                "prebuilts/tools/common/api-versions/%/public/android.jar",
                // The following are fallbacks which are always added.
                "prebuilts/tools/common/api-versions/android-%/android.jar",
                "prebuilts/sdk/%/public/android.jar",
            )

        val patternNode = PatternNode.parsePatterns(patterns)
        patternNode.assertStructure(
            """
                <root>
                  prebuilts/
                    sdk/
                      (\d+)/
                        public/
                          android.jar
                    tools/
                      common/
                        api-versions/
                          (\d+)/
                            public/
                              android.jar
                          \Qandroid-\E(\d+)/
                            android.jar
            """
        )
    }

    @Test
    fun `Parse common Android system patterns`() {
        val patterns =
            listOf(
                "prebuilts/sdk/%/system/android.jar",
                // This pattern never matches, but it is provided by Soong as it treats all
                // directories as being the same structure as prebuilts/sdk.
                "prebuilts/tools/common/api-versions/%/system/android.jar",
                "prebuilts/sdk/%/public/android.jar",
                // This pattern never matches, but it is provided by Soong as it treats all
                // directories as being the same structure as prebuilts/sdk.
                "prebuilts/tools/common/api-versions/%/public/android.jar",
                // The following are fallbacks which are always added.
                "prebuilts/tools/common/api-versions/android-%/android.jar",
                "prebuilts/sdk/%/public/android.jar",
            )

        val patternNode = PatternNode.parsePatterns(patterns)
        patternNode.assertStructure(
            """
                <root>
                  prebuilts/
                    sdk/
                      (\d+)/
                        system/
                          android.jar
                        public/
                          android.jar
                    tools/
                      common/
                        api-versions/
                          (\d+)/
                            system/
                              android.jar
                            public/
                              android.jar
                          \Qandroid-\E(\d+)/
                            android.jar
            """
        )
    }

    @Test
    fun `Parse common Android module-lib patterns`() {
        val patterns =
            listOf(
                "prebuilts/sdk/%/module-lib/android.jar",
                // This pattern never matches, but it is provided by Soong as it treats all
                // directories as being the same structure as prebuilts/sdk.
                "prebuilts/tools/common/api-versions/%/module-lib/android.jar",
                "prebuilts/sdk/%/system/android.jar",
                // This pattern never matches, but it is provided by Soong as it treats all
                // directories as being the same structure as prebuilts/sdk.
                "prebuilts/tools/common/api-versions/%/system/android.jar",
                "prebuilts/sdk/%/public/android.jar",
                // This pattern never matches, but it is provided by Soong as it treats all
                // directories as being the same structure as prebuilts/sdk.
                "prebuilts/tools/common/api-versions/%/public/android.jar",
                // The following are fallbacks which are always added.
                "prebuilts/tools/common/api-versions/android-%/android.jar",
                "prebuilts/sdk/%/public/android.jar",
            )

        val patternNode = PatternNode.parsePatterns(patterns)
        patternNode.assertStructure(
            """
                <root>
                  prebuilts/
                    sdk/
                      (\d+)/
                        module-lib/
                          android.jar
                        system/
                          android.jar
                        public/
                          android.jar
                    tools/
                      common/
                        api-versions/
                          (\d+)/
                            module-lib/
                              android.jar
                            system/
                              android.jar
                            public/
                              android.jar
                          \Qandroid-\E(\d+)/
                            android.jar
            """
        )
    }
}
