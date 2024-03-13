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

import com.android.tools.metalava.ARG_ANDROID_JAR_PATTERN
import com.android.tools.metalava.ARG_CURRENT_CODENAME
import com.android.tools.metalava.ARG_CURRENT_VERSION
import com.android.tools.metalava.ARG_GENERATE_API_LEVELS
import com.android.tools.metalava.doc.getApiLookup
import com.android.tools.metalava.testing.java
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CorrectApiLevelForReleaseTest : ApiGeneratorIntegrationTestBase() {

    @Test
    fun `Correct API Level for release`() {
        val output = File.createTempFile("api-info", "xml")
        output.deleteOnExit()
        val outputPath = output.path

        check(
            extraArguments =
                arrayOf(
                    ARG_GENERATE_API_LEVELS,
                    outputPath,
                    ARG_ANDROID_JAR_PATTERN,
                    "${oldSdkJars.path}/android-%/android.jar",
                    ARG_ANDROID_JAR_PATTERN,
                    "${platformJars.path}/%/public/android.jar",
                    ARG_CURRENT_CODENAME,
                    "REL",
                    ARG_CURRENT_VERSION,
                    MAGIC_VERSION_STR // not real api level
                ),
            sourceFiles =
                arrayOf(
                    java(
                        """
                        package android.pkg;
                        public class MyTest {
                        }
                        """
                    )
                )
        )

        assertTrue(output.isFile)
        // Anything with a REL codename is in the current API level
        val xml = output.readText(Charsets.UTF_8)
        assertTrue(xml.contains("<class name=\"android/pkg/MyTest\" since=\"$MAGIC_VERSION_STR\""))
        val apiLookup = getApiLookup(output, temporaryFolder.newFolder())
        @Suppress("DEPRECATION")
        assertEquals(MAGIC_VERSION_INT, apiLookup.getClassVersion("android.pkg.MyTest"))
    }
}