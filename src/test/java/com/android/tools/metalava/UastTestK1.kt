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
import org.junit.BeforeClass
import org.junit.Test

class UastTestK1 : UastTestBase() {
    companion object {
        @BeforeClass
        @JvmStatic
        fun classSetup() {
            System.setProperty(FIR_UAST_KEY, "false")
        }
    }

    @Test
    fun `Test Experimental and UseExperimental -- K1`() {
        `Test Experimental and UseExperimental`(isK2 = false)
    }

    @Test
    fun `renamed via @JvmName -- K1`() {
        `renamed via @JvmName`(
            api = """
                // Signature format: 4.0
                package test.pkg {
                  public final class ColorRamp {
                    ctor public ColorRamp(int[] colors, boolean interpolated);
                    method public int[] getColors();
                    method public boolean getInterpolated();
                    method public int[] getOtherColors();
                    method public boolean isInitiallyEnabled();
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
}
