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

package com.android.tools.metalava.testing

import com.android.tools.metalava.run
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import org.junit.Assert
import org.junit.Rule
import org.junit.rules.ErrorCollector
import org.junit.rules.TemporaryFolder

/**
 * Base class for command related tests.
 *
 * Tests that need to run command tests must extend this and call [commandTest] to configure the
 * test.
 */
abstract class BaseCommandTest {

    /**
     * Collects errors during the running of the test and reports them at the end.
     *
     * That allows a test to report multiple failures rather than just stopping at the first
     * failure. This should be used sparingly. In particular, it must not be used to create test
     * methods that perform multiple distinct tests. Those should be split apart into separate
     * tests.
     */
    @get:Rule val errorCollector = ErrorCollector()

    /** Provides access to temporary files. */
    @get:Rule val temporaryFolder = TemporaryFolder()

    /**
     * Type safe builder for configuring and running a command related test.
     *
     * This creates an instance of [CommandTestConfig], passes it to lambda expression for
     * modification and then calls [CommandTestConfig.runTest].
     */
    fun commandTest(init: CommandTestConfig.() -> Unit) {
        val config = CommandTestConfig(this)
        config.init()

        config.runTest()
    }
}

/**
 * Contains configuration for a test that uses `Driver.`[run]
 *
 * It is expected that the basic capabilities provided by this class will be extended to add the
 * capabilities needed by each test. e.g.
 * * Tests for a specific sub-command could add extension functions to specify the different options
 *   and arguments.
 * * Extension functions could also be added for groups of options that are common to a number of
 *   different sub-commands.
 */
class CommandTestConfig(private val test: BaseCommandTest) {

    /**
     * The args that will be passed to `Driver.`[run].
     *
     * This is a val rather than a var to force any builder extension to append to them rather than
     * replace then. That should result in builder extensions that can be more easily combined into
     * a single test.
     */
    val args = mutableListOf<String>()

    /**
     * The expected output, defaults to an empty string.
     *
     * This will be checked after running the test.
     */
    var expectedStdout: String = ""

    /**
     * The expected output, defaults to an empty string.
     *
     * This will be checked after running the test.
     */
    var expectedStderr: String = ""

    /** The list of lambdas that are invoked after the command has been run. */
    val verifiers = mutableListOf<() -> Unit>()

    /** Create a temporary folder. */
    fun folder(): File = test.temporaryFolder.newFolder()

    /**
     * Add a lambda function verifier that will check some result of the test to the list of
     * verifiers that will be invoked after the command has been run.
     *
     * All failures reported by the verifiers are collated and reported at the end so each verifier
     * must be standalone and not rely on the result of a preceding verifier.
     *
     * @param position the optional position in the list, by default they are added at the end.
     * @param verifier the lambda function that performs the check.
     */
    fun verify(position: Int = verifiers.size, verifier: () -> Unit) {
        verifiers.add(position, verifier)
    }

    /**
     * Wrap an assertion to convert it to a non-fatal check that is reported at the end of the test.
     *
     * e.g. the following will report all the assertion failures at the end of the test.
     *
     *     check {
     *         assertEquals("foo", "bar")
     *     }
     *     check {
     *         assertEquals("bill", "ted")h
     *     }
     *
     * This should be used sparingly. In particular, it must not be used to create test methods that
     * perform multiple distinct tests. Those should be split apart into separate tests.
     */
    fun check(body: () -> Unit) {
        try {
            body()
        } catch (e: Throwable) {
            if (e is AssertionError || e is Exception) {
                test.errorCollector.addError(e)
            } else {
                throw e
            }
        }
    }

    /** Run the test defined by the configuration. */
    fun runTest() {
        val stdout = StringWriter()
        val stderr = StringWriter()

        // Runs Driver.run as that is the entrypoint that makes the test as realistic as possible.
        run(
            originalArgs = args.toTypedArray(),
            stdout = PrintWriter(stdout),
            stderr = PrintWriter(stderr)
        )

        // Add checks of the expected stderr and stdout at the head of the list of verifiers.
        verify(0) { Assert.assertEquals(expectedStderr, stderr.toString()) }
        verify(1) { Assert.assertEquals(expectedStdout, stdout.toString()) }

        // Invoke all the verifiers.
        for (verifier in verifiers) {
            // A failing verifier will not break the
            check { verifier() }
        }
    }
}
