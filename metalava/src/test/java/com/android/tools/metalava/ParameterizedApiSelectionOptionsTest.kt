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

package com.android.tools.metalava

import com.android.tools.metalava.cli.common.BaseOptionGroupTest
import com.android.tools.metalava.config.AnnotationRuleConfig
import com.android.tools.metalava.config.ApiSurfaceConfig
import com.android.tools.metalava.config.ApiSurfacesConfig
import com.android.tools.metalava.config.EffectConfig
import com.android.tools.metalava.config.SelectionCriteriaConfig
import com.android.tools.metalava.model.api.ApiSurfaceRules
import com.android.tools.metalava.model.api.ApiSurfaceSelector
import com.android.tools.metalava.model.testing.api.assertState
import kotlin.test.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Tests for the creation of [ApiSurfaceSelector] and related objects. */
@RunWith(Parameterized::class)
class ParameterizedApiSelectionOptionsTest :
    BaseOptionGroupTest<ApiSelectionOptions>(API_SELECTION_OPTIONS_HELP) {

    /** Parameterized by [SurfaceRuleSource]. */
    @Parameterized.Parameter(0) lateinit var surfaceRuleSource: SurfaceRuleSource

    /**
     * Enumeration of the source of the API surface rule information.
     *
     * @param useOptions use options to define the rules.
     * @param useConfig use [ApiSurfacesConfig] to define the rules.
     */
    enum class SurfaceRuleSource(
        val useOptions: Boolean,
        val useConfig: Boolean,
    ) {
        /** Run the test providing surface rules using command line options only. */
        OPTIONS_ONLY(
            useOptions = true,
            useConfig = false,
        ),

        /** Run the test providing surface rules using [ApiSurfacesConfig] only. */
        CONFIG_ONLY(
            useOptions = false,
            useConfig = true,
        ),
    }

    companion object {
        /** Supply the list of [SurfaceRuleSource] entries as the parameters for this test class. */
        @JvmStatic @Parameterized.Parameters(name = "{0}") fun params() = SurfaceRuleSource.entries
    }

    override fun createOptions() = ApiSelectionOptions()

    /**
     * Check the state of the [ApiSurfaceSelector] created by [ApiSelectionOptions].
     *
     * @param ruleOptions the command line options that specify the API selection rules. If set to
     *   `null` then this test will not be run for [SurfaceRuleSource.OPTIONS_ONLY].
     * @param apiSurfacesConfigWithRules the [ApiSurfaceConfig] to use.
     * @param expectedMatcherState see [assertState].
     * @param expectedShowUnannotated see [assertState].
     * @param expectedOptionSurfaceRules the expected option [ApiSurfaceRules] passed to
     *   [ApiSurfaceSelector]. If set to `null` then this test will not be run for
     *   [SurfaceRuleSource.OPTIONS_ONLY].
     * @param expectedConfigSurfaceRules the expected config [ApiSurfaceRules] passed to
     *   [ApiSurfaceSelector].
     */
    private fun checkApiSurfaceSelectorState(
        surface: String,
        ruleOptions: List<String>?,
        apiSurfacesConfigWithRules: ApiSurfacesConfig,
        expectedMatcherState: String,
        expectedShowUnannotated: Boolean,
        expectedConfigUnannotatedSurfaceName: String?,
        expectedOptionUnannotatedSurfaceName: String? = expectedConfigUnannotatedSurfaceName,
        expectedOptionSurfaceRules: String?,
        expectedConfigSurfaceRules: String,
    ) {
        // Ignore the test if it could not be replicated with options.
        if (surfaceRuleSource.useOptions) {
            assumeTrue(ruleOptions != null && expectedOptionSurfaceRules != null)
        }

        val apiSurfacesConfig =
            if (surfaceRuleSource.useConfig) {
                apiSurfacesConfigWithRules
            } else {
                null
            }

        val optionGroup =
            ApiSelectionOptions(
                apiSurfacesConfigProvider = { apiSurfacesConfig },
            )
        val combinedArgs = buildList {
            if (surfaceRuleSource.useConfig) {
                add(ARG_API_SURFACE)
                add(surface)
            }
            if (surfaceRuleSource.useOptions) {
                addAll(ruleOptions!!)
            }
        }
        runTest(args = combinedArgs.toTypedArray(), optionGroup = optionGroup) {
            val selector = options.apiSurfaceSelector
            val unannotatedSurfaceName =
                if (surfaceRuleSource.useOptions) {
                    expectedOptionUnannotatedSurfaceName
                } else {
                    expectedConfigUnannotatedSurfaceName
                }
            selector.assertState(
                expectedMatcherState = expectedMatcherState,
                expectedShowUnannotated = expectedShowUnannotated,
                expectedUnannotatedSurfaceName = unannotatedSurfaceName,
            )

            if (surfaceRuleSource.useOptions) {
                val rules = options.createApiSurfaceRulesFromOptions()
                assertEquals(
                    expectedOptionSurfaceRules!!.trimIndent(),
                    rules.toString(),
                    message = "ApiSurfaceRules from options"
                )
            }

            if (surfaceRuleSource.useConfig) {
                val rules = options.createApiSurfaceRulesFromConfig()
                assertEquals(
                    expectedConfigSurfaceRules.trimIndent(),
                    rules.toString(),
                    message = "ApiSurfaceRules from config"
                )
            }
        }
    }

    @Test
    fun `Test configuring api surface rules - public`() {
        checkApiSurfaceSelectorState(
            surface = "public",
            ruleOptions =
                listOf(
                    ARG_HIDE_ANNOTATION,
                    "android.annotation.Hide",
                ),
            apiSurfacesConfigWithRules =
                ApiSurfacesConfig(
                    apiSurfaceList =
                        listOf(
                            ApiSurfaceConfig(
                                name = "public",
                                selectionCriteria =
                                    SelectionCriteriaConfig(
                                        unannotated = EffectConfig.SHOW,
                                        annotationRules =
                                            listOf(
                                                AnnotationRuleConfig(
                                                    pattern = "android.annotation.Hide",
                                                    effect = EffectConfig.HIDE,
                                                ),
                                            ),
                                    ),
                            ),
                        ),
                ),
            expectedMatcherState =
                """
                    AnnotationMatcher(
                        android.annotation.Hide -> {
                            Entry(
                                result: HIDE
                            )
                        }
                    )
                """,
            expectedShowUnannotated = true,
            expectedOptionUnannotatedSurfaceName = "main",
            expectedOptionSurfaceRules =
                """
                    ApiSurfaceRules(
                        main -> {
                            SelectUnannotated
                            SelectAnnotated(annotationPattern=android.annotation.Hide, effect=HIDE, recursive=true)
                        }
                    )
                """,
            expectedConfigUnannotatedSurfaceName = "public",
            expectedConfigSurfaceRules =
                """
                    ApiSurfaceRules(
                        public -> {
                            SelectUnannotated
                            SelectAnnotated(annotationPattern=android.annotation.Hide, effect=HIDE, recursive=true)
                        }
                    )
                """,
        )
    }

    @Test
    fun `Test configuring api surface rules - public plus other`() {
        checkApiSurfaceSelectorState(
            surface = "public",
            ruleOptions =
                listOf(
                    ARG_HIDE_ANNOTATION,
                    "android.annotation.Hide",
                    ARG_SHOW_UNANNOTATED,
                    ARG_SHOW_ANNOTATION,
                    "android.annotation.OtherApi",
                ),
            apiSurfacesConfigWithRules =
                ApiSurfacesConfig(
                    apiSurfaceList =
                        listOf(
                            ApiSurfaceConfig(
                                name = "public",
                                selectionCriteria =
                                    SelectionCriteriaConfig(
                                        unannotated = EffectConfig.SHOW,
                                        annotationRules =
                                            listOf(
                                                AnnotationRuleConfig(
                                                    pattern = "android.annotation.Hide",
                                                    effect = EffectConfig.HIDE,
                                                ),
                                                AnnotationRuleConfig(
                                                    pattern = "android.annotation.OtherApi",
                                                ),
                                            ),
                                    ),
                            ),
                        ),
                ),
            expectedMatcherState =
                """
                    AnnotationMatcher(
                        android.annotation.Hide -> {
                            Entry(
                                result: HIDE
                            )
                        }
                        android.annotation.OtherApi -> {
                            Entry(
                                result: SHOW
                            )
                        }
                    )
                """,
            expectedShowUnannotated = true,
            expectedOptionUnannotatedSurfaceName = "main",
            expectedOptionSurfaceRules =
                """
                    ApiSurfaceRules(
                        main -> {
                            SelectUnannotated
                            SelectAnnotated(annotationPattern=android.annotation.Hide, effect=HIDE, recursive=true)
                            SelectAnnotated(annotationPattern=android.annotation.OtherApi, effect=SHOW, recursive=true)
                        }
                    )
                """,
            expectedConfigUnannotatedSurfaceName = "public",
            expectedConfigSurfaceRules =
                """
                    ApiSurfaceRules(
                        public -> {
                            SelectUnannotated
                            SelectAnnotated(annotationPattern=android.annotation.Hide, effect=HIDE, recursive=true)
                            SelectAnnotated(annotationPattern=android.annotation.OtherApi, effect=SHOW, recursive=true)
                        }
                    )
                """,
        )
    }

    @Test
    fun `Test configuring api surface rules - system`() {
        checkApiSurfaceSelectorState(
            surface = "system",
            ruleOptions =
                listOf(
                    ARG_HIDE_ANNOTATION,
                    "android.annotation.Hide",
                    ARG_SHOW_ANNOTATION,
                    "android.annotation.SystemApi(client=android.annotation.SystemApi.Client.PRIVILEGED_APPS)",
                ),
            apiSurfacesConfigWithRules =
                ApiSurfacesConfig(
                    apiSurfaceList =
                        listOf(
                            ApiSurfaceConfig(
                                name = "public",
                                selectionCriteria =
                                    SelectionCriteriaConfig(
                                        unannotated = EffectConfig.SHOW,
                                        annotationRules =
                                            listOf(
                                                AnnotationRuleConfig(
                                                    pattern = "android.annotation.Hide",
                                                    effect = EffectConfig.HIDE,
                                                ),
                                            ),
                                    ),
                            ),
                            ApiSurfaceConfig(
                                name = "system",
                                extends = "public",
                                selectionCriteria =
                                    SelectionCriteriaConfig(
                                        annotationRules =
                                            listOf(
                                                AnnotationRuleConfig(
                                                    pattern =
                                                        "android.annotation.SystemApi(client=android.annotation.SystemApi.Client.PRIVILEGED_APPS)",
                                                ),
                                            ),
                                    ),
                            ),
                        ),
                ),
            expectedMatcherState =
                """
                    AnnotationMatcher(
                        android.annotation.Hide -> {
                            Entry(
                                result: HIDE
                            )
                        }
                        android.annotation.SystemApi -> {
                            Entry(
                                client=android.annotation.SystemApi.Client.PRIVILEGED_APPS
                                result: SHOW
                            )
                        }
                    )
                """,
            expectedShowUnannotated = false,
            expectedOptionUnannotatedSurfaceName = "base",
            expectedOptionSurfaceRules =
                """
                    ApiSurfaceRules(
                        base -> {
                            SelectUnannotated
                            SelectAnnotated(annotationPattern=android.annotation.Hide, effect=HIDE, recursive=true)
                        }
                        main -> {
                            SelectAnnotated(annotationPattern=android.annotation.SystemApi(client=android.annotation.SystemApi.Client.PRIVILEGED_APPS), effect=SHOW, recursive=true)
                        }
                    )
                """,
            expectedConfigUnannotatedSurfaceName = "public",
            expectedConfigSurfaceRules =
                """
                    ApiSurfaceRules(
                        public -> {
                            SelectUnannotated
                            SelectAnnotated(annotationPattern=android.annotation.Hide, effect=HIDE, recursive=true)
                        }
                        system -> {
                            SelectAnnotated(annotationPattern=android.annotation.SystemApi(client=android.annotation.SystemApi.Client.PRIVILEGED_APPS), effect=SHOW, recursive=true)
                        }
                    )
                """,
        )
    }

    @Test
    fun `Test configuring api surface rules - module-lib`() {
        checkApiSurfaceSelectorState(
            surface = "module-lib",
            // Is not supported using command line options.
            ruleOptions = null,
            apiSurfacesConfigWithRules =
                ApiSurfacesConfig(
                    apiSurfaceList =
                        listOf(
                            ApiSurfaceConfig(
                                name = "public",
                                selectionCriteria =
                                    SelectionCriteriaConfig(
                                        unannotated = EffectConfig.SHOW,
                                        annotationRules =
                                            listOf(
                                                AnnotationRuleConfig(
                                                    pattern = "android.annotation.Hide",
                                                    effect = EffectConfig.HIDE,
                                                ),
                                            ),
                                    ),
                            ),
                            ApiSurfaceConfig(
                                name = "system",
                                extends = "public",
                                selectionCriteria =
                                    SelectionCriteriaConfig(
                                        annotationRules =
                                            listOf(
                                                AnnotationRuleConfig(
                                                    pattern =
                                                        "android.annotation.SystemApi(client=android.annotation.SystemApi.Client.PRIVILEGED_APPS)",
                                                ),
                                            ),
                                    ),
                            ),
                            ApiSurfaceConfig(
                                name = "module-lib",
                                extends = "system",
                                selectionCriteria =
                                    SelectionCriteriaConfig(
                                        annotationRules =
                                            listOf(
                                                AnnotationRuleConfig(
                                                    pattern =
                                                        "android.annotation.SystemApi(client=android.annotation.SystemApi.Client.MODULE_LIBRARIES)",
                                                ),
                                            ),
                                    ),
                            ),
                        ),
                ),
            expectedMatcherState =
                """
                    AnnotationMatcher(
                        android.annotation.Hide -> {
                            Entry(
                                result: HIDE
                            )
                        }
                        android.annotation.SystemApi -> {
                            Entry(
                                client=android.annotation.SystemApi.Client.MODULE_LIBRARIES
                                result: SHOW
                            )
                            Entry(
                                client=android.annotation.SystemApi.Client.PRIVILEGED_APPS
                                result: SHOW_FOR_STUBS
                            )
                        }
                    )
                """,
            expectedShowUnannotated = false,
            // Is not supported using command line options.
            expectedOptionSurfaceRules = null,
            expectedConfigUnannotatedSurfaceName = "public",
            expectedConfigSurfaceRules =
                """
                    ApiSurfaceRules(
                        public -> {
                            SelectUnannotated
                            SelectAnnotated(annotationPattern=android.annotation.Hide, effect=HIDE, recursive=true)
                        }
                        system -> {
                            SelectAnnotated(annotationPattern=android.annotation.SystemApi(client=android.annotation.SystemApi.Client.PRIVILEGED_APPS), effect=SHOW, recursive=true)
                        }
                        module-lib -> {
                            SelectAnnotated(annotationPattern=android.annotation.SystemApi(client=android.annotation.SystemApi.Client.MODULE_LIBRARIES), effect=SHOW, recursive=true)
                        }
                    )
                """,
        )
    }

    @Test
    fun `Test configuring api surface rules - non-recursive`() {
        checkApiSurfaceSelectorState(
            surface = "system",
            // Is not supported using command line options.
            ruleOptions = null,
            apiSurfacesConfigWithRules =
                ApiSurfacesConfig(
                    apiSurfaceList =
                        listOf(
                            ApiSurfaceConfig(
                                name = "public",
                                selectionCriteria =
                                    SelectionCriteriaConfig(
                                        unannotated = EffectConfig.SHOW,
                                        annotationRules =
                                            listOf(
                                                AnnotationRuleConfig(
                                                    pattern = "android.annotation.Hide",
                                                    effect = EffectConfig.HIDE,
                                                ),
                                            ),
                                    ),
                            ),
                            ApiSurfaceConfig(
                                name = "system",
                                extends = "public",
                                selectionCriteria =
                                    SelectionCriteriaConfig(
                                        annotationRules =
                                            listOf(
                                                AnnotationRuleConfig(
                                                    pattern =
                                                        "android.annotation.SystemApi(client=android.annotation.SystemApi.Client.PRIVILEGED_APPS)",
                                                    recursive = false,
                                                ),
                                            ),
                                    ),
                            ),
                        ),
                ),
            expectedMatcherState =
                """
                    AnnotationMatcher(
                        android.annotation.Hide -> {
                            Entry(
                                result: HIDE
                            )
                        }
                        android.annotation.SystemApi -> {
                            Entry(
                                client=android.annotation.SystemApi.Client.PRIVILEGED_APPS
                                result: SHOW_SINGLE
                            )
                        }
                    )
                """,
            expectedShowUnannotated = false,
            // Is not supported using command line options.
            expectedOptionSurfaceRules = null,
            expectedConfigUnannotatedSurfaceName = "public",
            expectedConfigSurfaceRules =
                """
                    ApiSurfaceRules(
                        public -> {
                            SelectUnannotated
                            SelectAnnotated(annotationPattern=android.annotation.Hide, effect=HIDE, recursive=true)
                        }
                        system -> {
                            SelectAnnotated(annotationPattern=android.annotation.SystemApi(client=android.annotation.SystemApi.Client.PRIVILEGED_APPS), effect=SHOW, recursive=false)
                        }
                    )
                """,
        )
    }
}
