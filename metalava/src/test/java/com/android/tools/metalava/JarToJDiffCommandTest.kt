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

import com.android.tools.metalava.cli.common.BaseCommandTest
import java.io.File
import kotlin.test.assertEquals
import org.junit.Assert
import org.junit.Test

class JarToJDiffCommandTest : BaseCommandTest<JarToJDiffCommand>({ JarToJDiffCommand() }) {

    @Test
    fun `Test help`() {
        commandTest {
            args += listOf("jar-to-jdiff")

            expectedStdout =
                """
Aborting: Usage: metalava jar-to-jdiff [options] <jar-file> <xml-file>

  Convert a jar file into a file in the JDiff XML format.

  This is intended for use by the coverage team to extract information needed to determine test coverage of the API from
  the stubs jars. Any other use is unsupported.

Options:
  -h, -?, --help                             Show this message and exit

Arguments:
  <jar-file>                                 Jar file to convert to the JDiff XML format.
  <xml-file>                                 Output JDiff XML format file.
                """
                    .trimIndent()
        }
    }

    @Test
    fun `Test convert android 30`() {
        // Get the location of an android.jar in the prebuilts/sdk files generated by the build.
        val prebuiltsSdkDir = File(System.getenv("METALAVA_TEST_PREBUILTS_SDK_ROOT"))
        if (!prebuiltsSdkDir.isDirectory) {
            Assert.fail("test prebuilts not found: $prebuiltsSdkDir")
        }
        val androidJar = prebuiltsSdkDir.resolve("30/public/android.jar")

        val expectedXml =
            """
<api xmlns:metalava="http://www.android.com/metalava/">
<package name="android.test"
>
<class name="ClassAddedInApi30"
 extends="java.lang.Object"
 abstract="false"
 static="false"
 final="false"
 deprecated="not deprecated"
 visibility="public"
>
<method name="methodAddedInApi30"
 return="void"
 abstract="false"
 native="false"
 synchronized="false"
 static="false"
 final="false"
 deprecated="not deprecated"
 visibility="public"
>
</method>
</class>
</package>
<package name="java.lang"
>
<class name="Object"
 abstract="false"
 static="false"
 final="false"
 deprecated="not deprecated"
 visibility="public"
>
<constructor name="Object"
 type="java.lang.Object"
 static="false"
 final="false"
 deprecated="not deprecated"
 visibility="public"
>
</constructor>
</class>
</package>
</api>
            """
                .trimIndent()

        commandTest {
            // Create a unique folder to allow multiple configs to be run in the same test.
            val folder = folder("jdiff")

            args += "jar-to-jdiff"
            args += androidJar.path

            val xmlFile = outputFile("api.xml", parentDir = folder)
            args += xmlFile.path

            // Verify that the generate file is correct.
            verify { assertEquals(expectedXml.trimIndent(), xmlFile.readText().trim()) }
        }
    }
}
