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
import com.android.tools.metalava.config.AnnotationRule
import com.android.tools.metalava.config.ApiSurfaceConfig
import com.android.tools.metalava.config.ApiSurfacesConfig
import com.android.tools.metalava.config.SelectionCriteria
import com.android.tools.metalava.config.SelectionCriteriaEffect
import com.android.tools.metalava.model.api.ApiSurfaceRules
import com.android.tools.metalava.model.api.ApiSurfaceSelector
import com.android.tools.metalava.model.testing.api.assertState
import kotlin.test.assertEquals
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

        /**
         * Run the test providing surface rules using command line options and [ApiSurfaceConfig]
         * too.
         */
        OPTIONS_AND_CONFIG(
            useOptions = true,
            useConfig = false,
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
     * @param ruleOptions the command line options that specify the API selection rules.
     * @param apiSurfacesConfigWithRules the [ApiSurfaceConfig] to use.
     * @param expectedMatcherState see [assertState].
     * @param expectedShowUnannotated see [assertState].
     * @param expectedSurfaceRules the expected [ApiSurfaceRules] passed to [ApiSurfaceSelector].
     */
    private fun checkApiSurfaceSelectorState(
        surface: String,
        ruleOptions: List<String>,
        apiSurfacesConfigWithRules: ApiSurfacesConfig,
        expectedMatcherState: String,
        expectedShowUnannotated: Boolean,
        expectedSurfaceRules: String,
    ) {
        val apiSurfacesConfig =
            if (surfaceRuleSource.useConfig) {
                apiSurfacesConfigWithRules
            } else {
                ApiSurfacesConfig(
                    apiSurfaceList =
                        listOf(
                            ApiSurfaceConfig(name = "public"),
                            ApiSurfaceConfig(name = "system", extends = "public"),
                            ApiSurfaceConfig(name = "module-lib", extends = "system"),
                        )
                )
            }

        val optionGroup =
            ApiSelectionOptions(
                apiSurfacesConfigProvider = { apiSurfacesConfig },
            )
        val combinedArgs = buildList {
            add(ARG_API_SURFACE)
            add(surface)
            if (surfaceRuleSource.useOptions) {
                addAll(ruleOptions)
            }
        }
        runTest(args = combinedArgs.toTypedArray(), optionGroup = optionGroup) {
            val selector = options.apiSurfaceSelector
            selector.assertState(
                expectedMatcherState = expectedMatcherState,
                expectedShowUnannotated = expectedShowUnannotated,
            )

            if (surfaceRuleSource.useOptions) {
                val rules = options.createApiSurfaceRulesFromOptions()
                assertEquals(
                    expectedSurfaceRules.trimIndent(),
                    rules.toString(),
                    message = "ApiSurfaceRules from options"
                )
            }

            if (surfaceRuleSource.useConfig) {
                val rules = options.createApiSurfaceRulesFromConfig()
                assertEquals(
                    expectedSurfaceRules.trimIndent(),
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
                                    SelectionCriteria(
                                        unannotated = SelectionCriteriaEffect.SHOW,
                                        annotationRules =
                                            listOf(
                                                AnnotationRule(
                                                    pattern = "android.annotation.Hide",
                                                    effect = SelectionCriteriaEffect.HIDE,
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
                                result: Showability(show=HIDE, recursive=HIDE, forStubsOnly=NO_EFFECT, revertItem=null)
                            )
                        }
                    )
                """,
            expectedShowUnannotated = true,
            expectedSurfaceRules =
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
                                    SelectionCriteria(
                                        unannotated = SelectionCriteriaEffect.SHOW,
                                        annotationRules =
                                            listOf(
                                                AnnotationRule(
                                                    pattern = "android.annotation.Hide",
                                                    effect = SelectionCriteriaEffect.HIDE,
                                                ),
                                                AnnotationRule(
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
                                result: Showability(show=HIDE, recursive=HIDE, forStubsOnly=NO_EFFECT, revertItem=null)
                            )
                        }
                        android.annotation.OtherApi -> {
                            Entry(
                                result: Showability(show=SHOW, recursive=SHOW, forStubsOnly=NO_EFFECT, revertItem=null)
                            )
                        }
                    )
                """,
            expectedShowUnannotated = true,
            expectedSurfaceRules =
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
                                    SelectionCriteria(
                                        unannotated = SelectionCriteriaEffect.SHOW,
                                        annotationRules =
                                            listOf(
                                                AnnotationRule(
                                                    pattern = "android.annotation.Hide",
                                                    effect = SelectionCriteriaEffect.HIDE,
                                                ),
                                            ),
                                    ),
                            ),
                            ApiSurfaceConfig(
                                name = "system",
                                extends = "public",
                                selectionCriteria =
                                    SelectionCriteria(
                                        annotationRules =
                                            listOf(
                                                AnnotationRule(
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
                                result: Showability(show=HIDE, recursive=HIDE, forStubsOnly=NO_EFFECT, revertItem=null)
                            )
                        }
                        android.annotation.SystemApi -> {
                            Entry(
                                client=android.annotation.SystemApi.Client.PRIVILEGED_APPS
                                result: Showability(show=SHOW, recursive=SHOW, forStubsOnly=NO_EFFECT, revertItem=null)
                            )
                        }
                    )
                """,
            expectedShowUnannotated = false,
            expectedSurfaceRules =
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
            ruleOptions =
                listOf(
                    ARG_HIDE_ANNOTATION,
                    "android.annotation.Hide",
                    ARG_SHOW_FOR_STUB_PURPOSES_ANNOTATION,
                    "android.annotation.SystemApi(client=android.annotation.SystemApi.Client.PRIVILEGED_APPS)",
                    ARG_SHOW_ANNOTATION,
                    "android.annotation.SystemApi(client=android.annotation.SystemApi.Client.MODULE_LIBRARIES)",
                ),
            apiSurfacesConfigWithRules =
                ApiSurfacesConfig(
                    apiSurfaceList =
                        listOf(
                            ApiSurfaceConfig(
                                name = "public",
                                selectionCriteria =
                                    SelectionCriteria(
                                        unannotated = SelectionCriteriaEffect.SHOW,
                                        annotationRules =
                                            listOf(
                                                AnnotationRule(
                                                    pattern = "android.annotation.Hide",
                                                    effect = SelectionCriteriaEffect.HIDE,
                                                ),
                                            ),
                                    ),
                            ),
                            ApiSurfaceConfig(
                                name = "system",
                                extends = "public",
                                selectionCriteria =
                                    SelectionCriteria(
                                        annotationRules =
                                            listOf(
                                                AnnotationRule(
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
                                    SelectionCriteria(
                                        annotationRules =
                                            listOf(
                                                AnnotationRule(
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
                                result: Showability(show=HIDE, recursive=HIDE, forStubsOnly=NO_EFFECT, revertItem=null)
                            )
                        }
                        android.annotation.SystemApi -> {
                            Entry(
                                client=android.annotation.SystemApi.Client.PRIVILEGED_APPS
                                result: Showability(show=NO_EFFECT, recursive=NO_EFFECT, forStubsOnly=SHOW, revertItem=null)
                            )
                            Entry(
                                client=android.annotation.SystemApi.Client.MODULE_LIBRARIES
                                result: Showability(show=SHOW, recursive=SHOW, forStubsOnly=NO_EFFECT, revertItem=null)
                            )
                        }
                    )
                """,
            expectedShowUnannotated = false,
            expectedSurfaceRules =
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
    fun `Test configuring api surface rules - single other`() {
        checkApiSurfaceSelectorState(
            surface = "system",
            ruleOptions =
                listOf(
                    ARG_HIDE_ANNOTATION,
                    "android.annotation.Hide",
                    ARG_SHOW_SINGLE_ANNOTATION,
                    "android.annotation.SystemApi(client=android.annotation.SystemApi.Client.PRIVILEGED_APPS)",
                ),
            apiSurfacesConfigWithRules =
                ApiSurfacesConfig(
                    apiSurfaceList =
                        listOf(
                            ApiSurfaceConfig(
                                name = "public",
                                selectionCriteria =
                                    SelectionCriteria(
                                        unannotated = SelectionCriteriaEffect.SHOW,
                                        annotationRules =
                                            listOf(
                                                AnnotationRule(
                                                    pattern = "android.annotation.Hide",
                                                    effect = SelectionCriteriaEffect.HIDE,
                                                ),
                                            ),
                                    ),
                            ),
                            ApiSurfaceConfig(
                                name = "system",
                                extends = "public",
                                selectionCriteria =
                                    SelectionCriteria(
                                        annotationRules =
                                            listOf(
                                                AnnotationRule(
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
                                result: Showability(show=HIDE, recursive=HIDE, forStubsOnly=NO_EFFECT, revertItem=null)
                            )
                        }
                        android.annotation.SystemApi -> {
                            Entry(
                                client=android.annotation.SystemApi.Client.PRIVILEGED_APPS
                                result: Showability(show=SHOW, recursive=NO_EFFECT, forStubsOnly=NO_EFFECT, revertItem=null)
                            )
                        }
                    )
                """,
            expectedShowUnannotated = false,
            expectedSurfaceRules =
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
