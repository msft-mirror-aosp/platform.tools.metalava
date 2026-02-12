/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.tools.metalava.model.testsuite.classitem

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.TypeArgumentTypeItem
import com.android.tools.metalava.model.TypeParameterBindings
import com.android.tools.metalava.model.TypeParameterItem
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.model.testsuite.InputSet
import com.android.tools.metalava.model.testsuite.InputSetFactory
import com.android.tools.metalava.testing.EntryPoint
import com.android.tools.metalava.testing.EntryPointCallerRule
import com.android.tools.metalava.testing.EntryPointCallerTracker
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import com.android.tools.metalava.testing.signature
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runners.Parameterized

class CommonParameterizedMapTypeVariablesTest : BaseModelTest() {

    @Parameterized.Parameter(0) lateinit var params: TestParams

    /**
     * Will try and rewrite the stack trace of any test failures to refer to the location where the
     * [TestParams] that is currently being tested was created.
     */
    @get:Rule val entryPointCallerRule = EntryPointCallerRule { params.entryPointCallerTracker }

    class TestParams
    @EntryPoint
    constructor(
        val name: String? = null,
        val inputSets: Array<InputSet>,
        val descendantClass: String,
        val ancestorClass: String,
        val expectedBindingsBuilder: BindingsBuilder.() -> Unit,
    ) {
        /**
         * Record the stack trace of the creation of this which can be used to provide a stack trace
         * to the creator of this instance in the event of a test failure.
         */
        val entryPointCallerTracker = EntryPointCallerTracker()

        override fun toString() =
            name ?: "$descendantClass to $ancestorClass".replace("test.pkg.", "")
    }

    /**
     * Builder for [TypeParameterBindings].
     *
     * Used by [TestParams.expectedBindingsBuilder].
     */
    data class BindingsBuilder(
        val descendantClass: ClassItem,
        val ancestorClass: ClassItem,
    ) {
        /** Map being built. */
        private val mutableMap = mutableMapOf<TypeParameterItem, TypeArgumentTypeItem>()

        /**
         * Add an expected binding of the type parameter called [this] in [ancestorClass]'s
         * [ClassItem.typeParameterList] to the type parameter called [descendantName] in
         * [descendantClass]'s [ClassItem.typeParameterList].
         */
        infix fun String.shouldBeBoundTo(descendantName: String) =
            mutableMap.put(
                ancestorClass.typeParameterList[this],
                descendantClass.typeParameterList[descendantName].type()
            )

        /**
         * Find the [TypeParameterItem] called [name] in this list, or null if no such
         * [TypeParameterItem] exists.
         */
        private operator fun TypeParameterList.get(name: String) = single { it.name() == name }

        /** Get the built [TypeParameterBindings]. */
        internal fun bindings(): TypeParameterBindings = mutableMap.toMap()
    }

    companion object : InputSetFactory {
        private val childParentInputSets =
            arrayOf(
                inputSet(
                    java(
                        """
                            package test.pkg;
                            public class Parent<M, N> {}
                        """
                    ),
                    java(
                        """
                            package test.pkg;
                            public class Child<X, Y> extends Parent<X, Y> {}
                        """
                    ),
                ),
                inputSet(
                    signature(
                        """
                            // Signature format: 5.0
                            package test.pkg {
                              public class Child<X, Y> extends test.pkg.Parent<X,Y> {
                              }
                              public class Parent<M, N> {
                              }
                            }
                        """
                    ),
                ),
                inputSet(
                    kotlin(
                        """
                            package test.pkg
                            open class Parent<M, N>
                            class Child<X, Y> : Parent<X, Y>()
                        """
                    ),
                ),
            )

        private val params =
            listOf(
                // Child / Parent tests
                TestParams(
                    inputSets = childParentInputSets,
                    descendantClass = "test.pkg.Child",
                    ancestorClass = "test.pkg.Parent",
                    expectedBindingsBuilder = {
                        "M" shouldBeBoundTo "X"
                        "N" shouldBeBoundTo "Y"
                    },
                ),
                TestParams(
                    name = "invalid parent to child",
                    inputSets = childParentInputSets,
                    descendantClass = "test.pkg.Parent",
                    ancestorClass = "test.pkg.Child",
                    expectedBindingsBuilder = {},
                ),
                TestParams(
                    name = "invalid child to child",
                    inputSets = childParentInputSets,
                    descendantClass = "test.pkg.Child",
                    ancestorClass = "test.pkg.Child",
                    expectedBindingsBuilder = {},
                ),
            )

        @JvmStatic @Parameterized.Parameters(name = "{0}") fun params() = params
    }

    @Test
    fun `Test mapTypeVariables`() {
        runCodebaseTest(
            *params.inputSets,
        ) {
            val ancestorClass = codebase.assertClass(params.ancestorClass)
            val descendantClass = codebase.assertClass(params.descendantClass)

            val expectedBindingsBuilder = params.expectedBindingsBuilder
            val builderContext =
                BindingsBuilder(
                    ancestorClass = ancestorClass,
                    descendantClass = descendantClass,
                )

            builderContext.expectedBindingsBuilder()
            val expectedBindings = builderContext.bindings()
            val actualBindings = descendantClass.mapTypeVariables(ancestorClass)
            assertEquals(expectedBindings, actualBindings)
        }
    }
}
