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

import com.android.tools.metalava.model.source.SourceModelProvider
import com.android.tools.metalava.model.testing.BaseModelProviderRunner
import com.android.tools.metalava.model.testing.CodebaseCreatorConfig

/**
 * A [BaseModelProviderRunner] that will retrieve [SourceModelProvider]s from the
 * [SourceModelProvider] and run the tests against them.
 */
class DriverTestRunner(clazz: Class<*>) :
    BaseModelProviderRunner<SourceModelProvider, DriverTest>(
        clazz,
        { getSourceModelProviders() },
        "source-model-provider-baseline.txt",
    ) {
    companion object {
        /**
         * The default provider; this is the tests that will be run automatically when running a
         * specific method in the IDE.
         */
        private const val DEFAULT_PROVIDER = "psi"

        fun getSourceModelProviders(): List<CodebaseCreatorConfig<SourceModelProvider>> {
            return SourceModelProvider.implementations.flatMap { provider ->
                // Only include the provider name in the test name if it is not the default one.
                val includeProviderNameInTestName = provider.providerName != DEFAULT_PROVIDER
                provider.modelOptionsList.map { modelOptions ->
                    CodebaseCreatorConfig(
                        creator = provider,
                        modelOptions = modelOptions,
                        includeProviderNameInTestName = includeProviderNameInTestName,
                    )
                }
            }
        }
    }
}
