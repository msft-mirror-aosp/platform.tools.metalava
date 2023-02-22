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

package com.android.tools.metalava

import com.android.tools.lint.FIR_UAST_KEY
import com.android.tools.lint.UastEnvironment
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test

class UastTestK2 : UastTestBase() {
    companion object {
        private var lastKey: String? = null

        @BeforeClass
        @JvmStatic
        fun classSetup() {
            lastKey = System.getProperty(FIR_UAST_KEY, "false")
            System.setProperty(FIR_UAST_KEY, "true")
        }

        @AfterClass
        @JvmStatic
        fun classTeardown() {
            lastKey?.let {
                System.setProperty(FIR_UAST_KEY, it)
            }
            lastKey = null
            UastEnvironment.disposeApplicationEnvironment()
        }
    }

    @Test
    fun `Kotlin language level -- K2`() {
        `Kotlin language level`(isK2 = true)
    }

    @Test
    fun `Known nullness -- K2`() {
        `Known nullness`(isK2 = true)
    }

    @Test
    fun `Test Experimental and UseExperimental -- K2`() {
        `Test Experimental and UseExperimental`(isK2 = true)
    }

    @Test
    fun `renamed via @JvmName -- K2`() {
        // NB: getInterpolated -> isInterpolated
        `renamed via @JvmName`(
            api = """
                // Signature format: 4.0
                package test.pkg {
                  public final class ColorRamp {
                    ctor public ColorRamp(int[] colors, boolean interpolated);
                    method public int[] getColors();
                    method public int[] getOtherColors();
                    method public boolean isInitiallyEnabled();
                    method public boolean isInterpolated();
                    method public void updateOtherColors(int[]);
                    property public final int[] colors;
                    property public final boolean initiallyEnabled;
                    property public final boolean interpolated;
                    property public final int[] otherColors;
                  }
                }
            """
        )
    }

    @Test
    fun `Kotlin Reified Methods -- K2`() {
        `Kotlin Reified Methods`(isK2 = true)
    }

    @Test
    fun `Nullness in reified signatures -- K2`() {
        `Nullness in reified signatures`(isK2 = true)
    }

    @Test
    fun `Annotations aren't dropped when DeprecationLevel is HIDDEN -- K2`() {
        `Annotations aren't dropped when DeprecationLevel is HIDDEN`(isK2 = true)
    }
}
