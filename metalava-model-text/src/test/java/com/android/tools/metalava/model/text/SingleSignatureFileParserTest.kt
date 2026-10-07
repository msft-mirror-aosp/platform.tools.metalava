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

package com.android.tools.metalava.model.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SingleSignatureFileParserTest {

    @Test
    fun testTypeParameterNames() {
        assertThat(SingleSignatureFileParser.extractTypeParameterBoundsStringList(null).toString())
            .isEqualTo("[]")
        assertThat(SingleSignatureFileParser.extractTypeParameterBoundsStringList("").toString())
            .isEqualTo("[]")
        assertThat(SingleSignatureFileParser.extractTypeParameterBoundsStringList("X").toString())
            .isEqualTo("[]")
        assertThat(
                SingleSignatureFileParser.extractTypeParameterBoundsStringList("DEF extends T")
                    .toString()
            )
            .isEqualTo("[T]")
        assertThat(
                SingleSignatureFileParser.extractTypeParameterBoundsStringList(
                        "T extends java.lang.Comparable<? super T>"
                    )
                    .toString()
            )
            .isEqualTo("[java.lang.Comparable<? super T>]")
        assertThat(
                SingleSignatureFileParser.extractTypeParameterBoundsStringList(
                        "T extends java.util.List<Number> & java.util.RandomAccess"
                    )
                    .toString()
            )
            .isEqualTo("[java.util.List<Number>, java.util.RandomAccess]")
    }
}
