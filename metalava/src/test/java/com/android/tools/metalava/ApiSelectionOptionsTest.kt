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

import com.android.tools.metalava.cli.common.BaseOptionGroupTest
import com.android.tools.metalava.cli.common.MetalavaCliException
import com.android.tools.metalava.config.ApiSurfaceConfig
import com.android.tools.metalava.config.ApiSurfacesConfig
import com.android.tools.metalava.model.ANDROID_SYSTEM_API
import com.android.tools.metalava.model.api.ApiSurfaceSelector
import com.android.tools.metalava.model.testing.api.assertState
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

val API_SELECTION_OPTIONS_HELP =
    """
Api Selection:

  Options that select which parts of the source files will be part of the generated API.

  --api-surface <surface>                    The API surface currently being generated. Must correspond to an
                                             <api-surface> element in a --config-file.
  --show-unannotated                         Include un-annotated public APIs in the signature file as well. (default:
                                             true if no --show*-annotation options specified)
  --show-annotation <annotation-filter>      Unhide any hidden elements that are also annotated with the given
                                             annotation.
  --show-single-annotation <annotation-filter>
                                             Like --show-annotation, but does not apply to members; these must also be
                                             explicitly annotated.
  --show-for-stub-purposes-annotation <annotation-filter>
                                             Like --show-annotation, but elements annotated with it are assumed to be
                                             "implicitly" included in the API surface, and they'll be included in
                                             certain kinds of output such as stubs, but not in others, such as the
                                             signature file and API lint.
  --hide-annotation <annotation-filter>      Treat any elements annotated with the given annotation as hidden.
  --exclude-annotation <annotation-classes>  A comma separated list of fully qualified names of annotation classes that
                                             must be stripped from metalava's outputs.
  --pass-through-annotation <annotation-classes>
                                             A comma separated list of fully qualified names of annotation classes that
                                             must be passed through unchanged.
  --suppress-compatibility-meta-annotation <meta-annotation-class>
                                             Suppress compatibility checks for any elements within the scope of an
                                             annotation which is itself annotated with the given
                                             `meta-annotation-class`.
  --typedefs-in-signatures [none|ref|inline]
                                             Whether to include typedef annotations in signature files.

                                             none (default) - will not include typedef annotations in signature.

                                             ref - will include just a reference to the typedef class, which is not
                                             itself part of the API and is not included as a class

                                             inline - will include the constants themselves into each usage site
    """
        .trimIndent()

class ApiSelectionOptionsTest :
    BaseOptionGroupTest<ApiSelectionOptions>(API_SELECTION_OPTIONS_HELP) {
    override fun createOptions() = ApiSelectionOptions()

    @Test
    fun `Test no --show-unannotated no show annotations`() {
        runTest { assertThat(options.showUnannotated).isTrue() }
    }

    @Test
    fun `Test no --show-unannotated with --show-annotation`() {
        runTest(ARG_SHOW_ANNOTATION, "test.pkg.Show") {
            assertThat(options.showUnannotated).isFalse()
        }
    }

    /**
     * Run the test, providing an optional [ApiSelectionOptions] to
     * [ApiSelectionOptions.apiSurfacesConfigProvider].
     */
    private fun runTestWithConfig(
        vararg args: String,
        apiSurfacesConfig: ApiSurfacesConfig? =
            ApiSurfacesConfig(
                apiSurfaceList =
                    listOf(
                        ApiSurfaceConfig(name = "public"),
                        ApiSurfaceConfig(name = "system", extends = "public"),
                        ApiSurfaceConfig(name = "module-lib", extends = "system"),
                    )
            ),
        test: Result<ApiSelectionOptions>.() -> Unit,
    ) {
        val optionGroup =
            ApiSelectionOptions(
                apiSurfacesConfigProvider = { apiSurfacesConfig },
            )
        runTest(args = args, optionGroup = optionGroup, test = test)
    }

    /**
     * Run [body] and make sure that it throws a [MetalavaCliException] with the [expectedMessage].
     */
    private fun assertThrowsCliError(expectedMessage: String, body: () -> Unit) {
        val exception = assertThrows(MetalavaCliException::class.java) { body() }
        assertThat(exception.message).isEqualTo(expectedMessage)
    }

    @Test
    fun `Test --api-surface option no api-surfaces configuration`() {
        runTestWithConfig(
            ARG_API_SURFACE,
            "public",
            apiSurfacesConfig = null,
        ) {
            assertThrowsCliError(
                "--api-surface requires at least one <api-surface> to have been configured in a --config-file"
            ) {
                options.apiSurfaces
            }
        }
    }

    @Test
    fun `Test configuring API surfaces no --api-surface option`() {
        runTestWithConfig {
            // Configuration is ignored when no --api-surface is provided.
            options.apiSurfaces.assertBaseWasNotCreated()
        }
    }

    @Test
    fun `Test configuring API surfaces invalid --api-surface option`() {
        runTestWithConfig(
            ARG_API_SURFACE,
            "unknown",
        ) {
            val exception = assertThrows(IllegalStateException::class.java) { options.apiSurfaces }
            assertThat(exception.message)
                .isEqualTo(
                    "--api-surface (`unknown`) does not match an <api-surface> in a --config-file, expected one of `public`, `system`, `module-lib`"
                )
        }
    }

    @Test
    fun `Test configuring extending surface without --show-annotation option`() {
        runTestWithConfig(
            ARG_API_SURFACE,
            "system",
        ) {
            assertThrowsCliError(
                """Configuration of `<api-surface name="system">` is inconsistent with command line options because `system` extends public which requires that it not show unannotated items but --show-unannotated is true"""
            ) {
                options.apiSurfaces
            }
        }
    }

    @Test
    fun `Test configuring extending surface with --show-annotation option`() {
        runTestWithConfig(
            ARG_API_SURFACE,
            "system",
            ARG_SHOW_ANNOTATION,
            ANDROID_SYSTEM_API,
        ) {
            options.apiSurfaces.assertBaseWasCreated()
            assertThat(options.apiSurfaces.main.name).isEqualTo("system")
            assertThat(options.apiSurfaces.base?.name).isEqualTo("public")
        }
    }

    @Test
    fun `Test configuring non-extending surface with --show-annotation option`() {
        runTestWithConfig(
            ARG_API_SURFACE,
            "public",
            ARG_SHOW_ANNOTATION,
            ANDROID_SYSTEM_API,
        ) {
            assertThrowsCliError(
                """Configuration of `<api-surface name="public">` is inconsistent with command line options because `public` does not extend another surface which requires that it show unannotated items but --show-unannotated is false"""
            ) {
                options.apiSurfaces
            }
        }
    }

    @Test
    fun `Test configuring non-extending surface without --show-annotation option`() {
        runTestWithConfig(
            ARG_API_SURFACE,
            "public",
        ) {
            options.apiSurfaces.assertBaseWasNotCreated()
            assertThat(options.apiSurfaces.main.name).isEqualTo("public")
        }
    }

    /**
     * Check the state of the [ApiSurfaceSelector] created by [ApiSelectionOptions].
     *
     * @param expectedMatcherState see [assertState].
     * @param expectedShowUnannotated see [assertState].
     */
    private fun Result<ApiSelectionOptions>.checkApiSurfaceSelectorState(
        expectedMatcherState: String,
        expectedShowUnannotated: Boolean,
    ) {
        val selector = options.apiSurfaceSelector
        selector.assertState(
            expectedMatcherState = expectedMatcherState,
            expectedShowUnannotated = expectedShowUnannotated,
        )
    }

    @Test
    fun `Test configuring api surface rules - public`() {
        runTestWithConfig(
            ARG_API_SURFACE,
            "public",
            ARG_HIDE_ANNOTATION,
            "android.annotation.Hide",
        ) {
            checkApiSurfaceSelectorState(
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
            )
        }
    }

    @Test
    fun `Test configuring api surface rules - public plus other`() {
        runTestWithConfig(
            ARG_API_SURFACE,
            "public",
            ARG_HIDE_ANNOTATION,
            "android.annotation.Hide",
            ARG_SHOW_UNANNOTATED,
            ARG_SHOW_ANNOTATION,
            "android.annotation.OtherApi",
        ) {
            checkApiSurfaceSelectorState(
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
            )
        }
    }

    @Test
    fun `Test configuring api surface rules - system`() {
        runTestWithConfig(
            ARG_API_SURFACE,
            "system",
            ARG_HIDE_ANNOTATION,
            "android.annotation.Hide",
            ARG_SHOW_ANNOTATION,
            "android.annotation.SystemApi(client=android.annotation.SystemApi.Client.PRIVILEGED_APPS)",
        ) {
            checkApiSurfaceSelectorState(
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
            )
        }
    }

    @Test
    fun `Test configuring api surface rules - module-lib`() {
        runTestWithConfig(
            ARG_API_SURFACE,
            "module-lib",
            ARG_HIDE_ANNOTATION,
            "android.annotation.Hide",
            ARG_SHOW_FOR_STUB_PURPOSES_ANNOTATION,
            "android.annotation.SystemApi(client=android.annotation.SystemApi.Client.PRIVILEGED_APPS)",
            ARG_SHOW_ANNOTATION,
            "android.annotation.SystemApi(client=android.annotation.SystemApi.Client.MODULE_LIBRARIES)",
        ) {
            checkApiSurfaceSelectorState(
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
            )
        }
    }

    @Test
    fun `Test configuring api surface rules - single other`() {
        runTestWithConfig(
            ARG_API_SURFACE,
            "system",
            ARG_HIDE_ANNOTATION,
            "android.annotation.Hide",
            ARG_SHOW_SINGLE_ANNOTATION,
            "android.annotation.SystemApi(client=android.annotation.SystemApi.Client.PRIVILEGED_APPS)",
        ) {
            checkApiSurfaceSelectorState(
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
            )
        }
    }
}
