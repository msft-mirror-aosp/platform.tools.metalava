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

package com.android.tools.metalava.model.testsuite

import com.android.tools.metalava.model.junit4.CustomizableParameterizedRunner
import com.android.tools.metalava.model.testsuite.ModelProviderAwareTest.ModelProviderTestInfo
import com.android.tools.metalava.testing.BaselineTestRule
import java.util.ServiceLoader
import kotlin.test.fail
import org.junit.runner.Runner
import org.junit.runners.Parameterized
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.model.Statement
import org.junit.runners.model.TestClass
import org.junit.runners.parameterized.BlockJUnit4ClassRunnerWithParameters
import org.junit.runners.parameterized.ParametersRunnerFactory
import org.junit.runners.parameterized.TestWithParameters

/**
 * A special [CustomizableParameterizedRunner] for use with the model test suite tests.
 *
 * This provides the list of [ModelProviderTestInfo] constructed from the [ModelSuiteRunner]
 * accessible through the [ServiceLoader]. If the test provides its own arguments using
 * [Parameterized.Parameters] then this will compute the cross produce of those arguments with the
 * [ModelProviderTestInfo]. That will ensure that every set of arguments provided by the test clas s
 * will be run with every [ModelSuiteRunner] available.
 *
 * The [ModelProviderTestInfo] is injected into the test through
 * [ModelProviderAwareTest.modelProviderTestInfo] and not through a field annotated with
 * [Parameterized.Parameter]. That means that switching a class that is already [Parameterized] to
 * use this instead does not affect any existing [Parameterized.Parameter] fields.
 */
class ModelTestSuiteRunner(clazz: Class<*>) :
    CustomizableParameterizedRunner(
        clazz,
        ::getModelSuiteRunners,
        InstanceRunnerFactory::class,
    ) {

    init {
        val awareTestClass = ModelProviderAwareTest::class.java
        if (!awareTestClass.isAssignableFrom(clazz)) {
            error("Class ${clazz.name} does not implement ${awareTestClass.name}")
        }
    }

    /** [ParametersRunnerFactory] for creating [Runner]s for a set of arguments. */
    class InstanceRunnerFactory : ParametersRunnerFactory {
        /**
         * Create a runner for the [TestWithParameters].
         *
         * The [TestWithParameters.parameters] contains at least one argument and the first argument
         * will be the [ModelProviderTestInfo] provided by [ModelTestSuiteRunner]. This extracts
         * that from the list and injects it into the test object (which must implement
         * [ModelProviderAwareTest]).
         */
        override fun createRunnerForTestWithParameters(test: TestWithParameters): Runner {
            val arguments = test.parameters
            val modelProviderTestInfo = arguments[0] as ModelProviderTestInfo

            val newTest = TestWithParameters(test.name, test.testClass, arguments.drop(1))
            return InstanceRunner(modelProviderTestInfo, newTest)
        }
    }

    /**
     * Run a test that must implement [ModelProviderAwareTest] after setting its
     * [ModelProviderAwareTest.modelProviderTestInfo] property.
     */
    private class InstanceRunner(
        private val modelProviderTestInfo: ModelProviderTestInfo,
        test: TestWithParameters
    ) : BlockJUnit4ClassRunnerWithParameters(test) {
        override fun createTest(): Any {
            val testInstance = super.createTest() as ModelProviderAwareTest
            testInstance.modelProviderTestInfo = modelProviderTestInfo
            return testInstance
        }

        /**
         * Override [methodInvoker] to allow the [Statement] it returns to be wrapped by a
         * [BaselineTestRule] to take into account known issues listed in a baseline file.
         */
        override fun methodInvoker(method: FrameworkMethod, test: Any): Statement {
            val statement = super.methodInvoker(method, test)
            val baselineTestRule =
                BaselineTestRule(
                    modelProviderTestInfo.runner.toString(),
                    ModelTestSuiteBaseline.RESOURCE_PATH
                )
            return baselineTestRule.apply(statement, describeChild(method))
        }
    }

    companion object {
        private fun getModelSuiteRunners(
            @Suppress("UNUSED_PARAMETER") testClass: TestClass,
            additionalArguments: List<Array<Any>>?,
        ): TestArguments {
            val loader = ServiceLoader.load(ModelSuiteRunner::class.java)
            val modelSuiteRunners = loader.toList()
            if (modelSuiteRunners.isEmpty()) {
                fail("No runners found")
            }
            val modelProviderTestInfoList =
                modelSuiteRunners.flatMap { runner ->
                    runner.testConfigurations.map {
                        ModelProviderTestInfo(
                            runner,
                            it.inputFormat,
                            it.modelOptions,
                        )
                    }
                }
            return if (additionalArguments == null) {
                // No additional arguments were provided so just return this set.
                TestArguments("{0}", modelProviderTestInfoList)
            } else {
                val combined = crossProduct(modelProviderTestInfoList, additionalArguments)
                TestArguments("{0},{1}", combined)
            }
        }

        /** Compute the cross product of the supplied [data1] and the [data2]. */
        private fun crossProduct(
            data1: Iterable<Any>,
            data2: Iterable<Array<Any>>
        ): List<Array<Any>> =
            data1.flatMap { p1 ->
                data2.map { p2 ->
                    // [data2] is an array that is assumed to have a single parameter within it but
                    // could have none or multiple. Either way just spread the contents into the
                    // combined array.
                    arrayOf(p1, *p2)
                }
            }
    }
}
