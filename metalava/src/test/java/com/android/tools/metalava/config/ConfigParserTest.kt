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

package com.android.tools.metalava.config

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles.xml
import com.android.tools.metalava.reporter.BasicReporter
import com.android.tools.metalava.testing.TemporaryFolderOwner
import com.google.common.truth.Truth.assertThat
import java.io.StringWriter
import org.intellij.lang.annotations.Language
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Tests of the [ConfigParser]. */
class ConfigParserTest : TemporaryFolderOwner {
    @get:Rule override val temporaryFolder = TemporaryFolder()

    /**
     * Run the test.
     *
     * @param configFiles The config files to parse.
     * @param expectedIssues The expected issues.
     * @param body the body of the test which checks the state of the [Config] object which is made
     *   available as [TestContext.config].
     */
    private fun runTest(
        vararg configFiles: TestFile,
        expectedIssues: String = "",
        body: TestContext.() -> Unit = {},
    ) {
        val dir = temporaryFolder.newFolder()
        val writer = StringWriter()
        val reporter = BasicReporter(writer)
        val config =
            ConfigParser.parse(reporter, configFiles.map { it.indented().createFile(dir) }.toList())
        val output = cleanupString(writer.toString(), project = dir)
        assertThat(output.trimIndent()).isEqualTo(expectedIssues.trimIndent())
        val context = TestContext(config = config)
        context.body()
    }

    /** Context for the tests. */
    private data class TestContext(
        /** The created [Config] being tested. */
        val config: Config,
    )

    /**
     * Round trip [config], i.e. write it to XML, check it matches [xml], read it back in, check
     * that it matches [config].
     *
     * Writing configuration to XML is not something that Metalava needs at runtime, but it is
     * useful to test what is written as that is what can be read.
     */
    private fun roundTrip(config: Config, @Language("xml") xml: String) {
        val xmlMapper = ConfigParser.configXmlMapper()

        val writtenXml = xmlMapper.writeValueAsString(config)
        assertThat(writtenXml.trimEnd()).isEqualTo(xml.trimIndent())

        val readConfig = xmlMapper.readValue(writtenXml, Config::class.java)
        assertThat(readConfig).isEqualTo(config)
    }

    @Test
    fun `Empty config file`() {
        roundTrip(
            Config(),
            """
                <config xmlns="http://www.google.com/tools/metalava/config"/>
            """
        )
    }

    @Test
    fun `Config file with xsi schemaLocation`() {
        runTest(
            xml(
                "config.xml",
                """
                    <config xmlns="http://www.google.com/tools/metalava/config"
                            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                            xsi:schemaLocation="http://www.google.com/tools/metalava/config ../../../../../../resources/schemas/config.xsd"/>
                """,
            ),
        ) {
            assertThat(config).isEqualTo(Config())
        }
    }

    @Test
    fun `Invalid config file`() {
        runTest(
            xml(
                "config.xml",
                """
                    <invalid xmlns="http://www.google.com/tools/metalava/config"/>
                """,
            ),
            expectedIssues =
                "TESTROOT/config.xml:1: error: Problem parsing configuration file: cvc-elt.1.a: Cannot find the declaration of element 'invalid'. [ConfigFileProblem]",
        )
    }

    @Test
    fun `Multiple config files`() {
        runTest(
            xml(
                "config1.xml",
                """
                    <config xmlns="http://www.google.com/tools/metalava/config"/>
                """,
            ),
            xml(
                "config2.xml",
                """
                    <config xmlns="http://www.google.com/tools/metalava/config"/>
                """,
            ),
        ) {
            assertThat(config).isEqualTo(Config())
        }
    }
}
