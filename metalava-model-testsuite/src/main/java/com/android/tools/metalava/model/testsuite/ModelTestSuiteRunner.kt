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
import java.util.ServiceLoader
import kotlin.test.fail
import org.junit.runners.Parameterized
import org.junit.runners.model.TestClass

/**
 * A special [CustomizableParameterizedRunner] for use with the model test suite tests.
 *
 * This provides the list of [ModelProviderTestInfo] constructed from the [ModelSuiteRunner]
 * accessible through the [ServiceLoader]. If the test provides its own arguments using
 * [Parameterized.Parameters] then this will compute the cross produce of those arguments with the
 * [ModelProviderTestInfo]. That will ensure that every set of arguments provided by the test clas s
 * will be run with every [ModelSuiteRunner] available.
 */
class ModelTestSuiteRunner(clazz: Class<*>) :
    CustomizableParameterizedRunner(
        clazz,
        ::getModelSuiteRunners,
    ) {

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
