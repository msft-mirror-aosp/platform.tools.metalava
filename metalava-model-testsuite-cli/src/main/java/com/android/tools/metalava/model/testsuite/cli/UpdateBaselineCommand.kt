/*
 * Copyright (C) 2023 The Android Open Source Project
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

@file:JvmName("UpdateBaseline")

package com.android.tools.metalava.model.testsuite.cli

import com.android.tools.metalava.testing.BaselineFile
import com.android.tools.metalava.testing.MutableBaselineFile
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.Locator
import org.xml.sax.SAXParseException
import org.xml.sax.helpers.DefaultHandler

fun main(args: Array<String>) {
    val command = UpdateBaselineCommand()
    command.main(args)
}

class UpdateBaselineCommand :
    CliktCommand(
        printHelpOnEmptyArgs = true,
        help =
            """
                Update the src/test/resources/model-test-suite-baseline.txt file.
            """
                .trimIndent(),
    ) {

    private val testReportFiles by
        argument(
                name = "<test-report-files>",
                help =
                    """
                        Test report files generated by gradle when running the
                        `metalava-model-test-suite`.
                    """
                        .trimIndent(),
            )
            .file(mustExist = true, canBeFile = true, mustBeReadable = true)
            .multiple(required = true)

    private val baselineFile by
        option(
                metavar = "<file>",
                help =
                    """
                        Baseline file that is to be updated.
                    """
                        .trimIndent(),
            )
            .file(canBeFile = true, canBeDir = false)
            .required()

    override fun run() {
        // Read the existing expected failures from the baseline file.
        val baseline = BaselineFile.forFile(baselineFile)

        // Using the existing expected failures in the baseline as a start point process the
        // test reports to see if there are any other failing tests and if so then add them to
        // the set of failing tests.
        val failureCollector = FailureCollector(baseline)
        val saxParserFactory = SAXParserFactory.newInstance()
        val saxParser = saxParserFactory.newSAXParser()
        testReportFiles.forEach {
            try {
                saxParser.parse(it, failureCollector)
            } catch (e: SAXParseException) {
                throw IllegalStateException("Could not parse file $it", e)
            }
        }

        // Write the updated baseline file.
        baseline.write()
    }

    private class FailureCollector(private val baseline: MutableBaselineFile) : DefaultHandler() {

        private lateinit var locator: Locator

        private var currentTestCase: TestDescriptor? = null

        private var testResult: TestResult = TestResult.PASSED

        override fun setDocumentLocator(locator: Locator?) {
            this.locator = locator!!
        }

        private val location
            get() = "${locator.systemId}:${locator.lineNumber}"

        private fun fail(message: String): Nothing {
            throw IllegalStateException("$location: $message")
        }

        fun Attributes.requiredValue(name: String) =
            getValue(name) ?: fail("attribute '$name' is missing")

        override fun startElement(
            uri: String?,
            localName: String?,
            qName: String?,
            attributes: Attributes?
        ) {
            when (qName) {
                "testcase" -> {
                    attributes ?: fail("attributes not provided")
                    val name = attributes.requiredValue("name")
                    val className = attributes.requiredValue("classname")
                    currentTestCase = TestDescriptor(className, name)
                    testResult = TestResult.PASSED
                }
                "failure" -> {
                    testResult = TestResult.FAILED
                }
                "skipped" -> {
                    testResult = TestResult.SKIPPED
                }
            }
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            when (qName) {
                "testcase" -> {
                    val testDescriptor = currentTestCase!!
                    when (testResult) {
                        TestResult.FAILED ->
                            testDescriptor.let { (className, testName) ->
                                baseline.addExpectedFailure(className, testName)
                            }
                        TestResult.SKIPPED -> Unit
                        TestResult.PASSED ->
                            testDescriptor.let { (className, testName) ->
                                baseline.removeExpectedFailure(className, testName)
                            }
                    }

                    currentTestCase = null
                }
            }
        }
    }

    data class TestDescriptor(val className: String, val testName: String)

    enum class TestResult {
        FAILED,
        SKIPPED,
        PASSED
    }
}