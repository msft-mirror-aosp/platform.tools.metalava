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

package com.android.tools.metalava.model.testsuite

import com.android.tools.metalava.model.api.ApiSurfaceRules
import com.android.tools.metalava.model.api.SurfaceSelectionRule
import com.android.tools.metalava.model.api.surface.ApiSurfaces
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.provider.InputFormat
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.model.testing.SupportedInputFormats
import com.android.tools.metalava.testing.java
import kotlin.test.assertEquals
import org.junit.Test

/** Common tests for verifying the behavior of configured doc-only annotations. */
@RequiresCapabilities(Capability.API_VARIANT_SELECTORS)
@SupportedInputFormats(InputFormat.JAVA)
class CommonDocOnlyTest : BaseModelTest() {

    @Test
    fun `Test class annotated with configured doc-only annotation is marked as docOnly`() {
        val apiSurfaces = ApiSurfaces.create()
        val rulesByName = mapOf("public" to listOf(SurfaceSelectionRule.unannotated))
        val variantRules =
            listOf(
                SurfaceSelectionRule.createAnnotationRule(
                    "test.pkg.DocOnly",
                    effect = SurfaceSelectionRule.Effect.DOC_ONLY,
                ),
            )
        val apiSurfaceRules = ApiSurfaceRules(apiSurfaces, rulesByName, variantRules)

        runCodebaseTest(
            java(
                """
                    package test.pkg;

                    import java.lang.annotation.Retention;
                    import java.lang.annotation.RetentionPolicy;

                    @Retention(RetentionPolicy.SOURCE)
                    @interface DocOnly {}

                    @DocOnly
                    public class Foo {
                        public void method() {}
                    }
                """
            ),
            testFixture = TestFixture(apiSurfaceRules = apiSurfaceRules),
        ) {
            val fooClass = codebase.assertClass("test.pkg.Foo")
            assertEquals(true, fooClass.variantSelectors.docOnly, message = "Foo should be docOnly")
        }
    }
}
