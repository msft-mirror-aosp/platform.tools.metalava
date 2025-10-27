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

package com.android.tools.metalava.model.testsuite.scope

import com.android.tools.metalava.model.Assertions
import com.android.tools.metalava.model.ReferencableItem
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.scope.ReferencableNameScope
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.model.testsuite.value.ValueExample
import com.android.tools.metalava.testing.EntryPoint
import com.android.tools.metalava.testing.EntryPointCallerRule
import com.android.tools.metalava.testing.EntryPointCallerTracker
import com.android.tools.metalava.testing.java
import kotlin.test.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runners.Parameterized

/** Common tests for [ReferencableNameScope] implementations. */
class CommonParameterizedReferencableNameScopeTest : BaseModelTest() {

    @Parameterized.Parameter(0) lateinit var params: TestParams

    /**
     * Will try and rewrite the stack trace of any test failures to refer to the location where the
     * [ValueExample] that is currently being tested was created.
     */
    @get:Rule val entryPointCallerRule = EntryPointCallerRule { params.entryPointCallerTracker }

    data class TestParams
    @EntryPoint
    constructor(
        val name: String,
        /** Getter for the [ReferencableNameScope] from which the name will be resolved. */
        val scopeGetter: CodebaseContext.() -> ReferencableNameScope,
        /** The name to resolve. */
        val referencableName: String,
        /**
         * Getter for the expected [ReferencableItem] to which [referencableName] name will be
         * resolved.
         */
        val expectedItemGetter: CodebaseContext.() -> ReferencableItem?,
    ) {
        /**
         * Record the stack trace of the creation of this which can be used to provide a stack trace
         * to the creator of this instance in the event of a test failure.
         */
        val entryPointCallerTracker = EntryPointCallerTracker()

        override fun toString(): String {
            return name
        }
    }

    companion object : Assertions {
        private val params =
            listOf(
                // PackageItem related tests.
                TestParams(
                    name = "PackageItem - implicit String",
                    scopeGetter = { codebase.assertResolvedPackage("java.lang") },
                    referencableName = "String",
                    expectedItemGetter = { codebase.assertResolvedClass("java.lang.String") }
                ),
                TestParams(
                    name = "PackageItem - not relative package",
                    scopeGetter = { codebase.assertResolvedPackage("java.lang") },
                    referencableName = "annotation",
                    expectedItemGetter = { null }
                ),
                TestParams(
                    name = "PackageItem - absolute class",
                    scopeGetter = { codebase.assertResolvedPackage("java.lang") },
                    referencableName = "java.io.IOException",
                    expectedItemGetter = { codebase.assertResolvedClass("java.io.IOException") }
                ),
                TestParams(
                    name = "PackageItem - absolute package",
                    scopeGetter = { codebase.assertResolvedPackage("java.lang") },
                    referencableName = "java.util",
                    expectedItemGetter = { codebase.assertResolvedPackage("java.util") }
                ),
            )

        @JvmStatic @Parameterized.Parameters fun params() = params
    }

    @RequiresCapabilities(Capability.JAVA)
    @Test
    fun `Test resolve`() {
        runCodebaseTest(
            inputSet(
                java(
                    """
                        package test.pkg;
                        public class Test {
                        }
                    """
                ),
            ),
        ) {
            val scopeGetter = params.scopeGetter
            val scope = scopeGetter()

            val resolvedItem = scope.resolveReferencableItem(params.referencableName)

            // Defer getting the expected Item until after resolving to ensure that resolving works
            // even on packages and classes that are not in the sources which have not yet been
            // resolved.
            val expectedItemGetter = params.expectedItemGetter
            val expectedItem = expectedItemGetter()

            assertSame(expectedItem, resolvedItem)
        }
    }
}
