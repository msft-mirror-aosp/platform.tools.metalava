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

package com.android.tools.metalava.lint

import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.androidxNullableSource
import com.android.tools.metalava.testing.java
import org.junit.Test

class HeavyBitSetTest : DriverTest() {
    @Test
    fun `Check no usages of heavy BitSet`() {
        check(
            apiLint = "", // enabled
            expectedIssues =
                """
                    src/android/pkg/MyClass.java:7: error: Type must not be heavy BitSet (field android.pkg.MyClass.bitset) [HeavyBitSet]
                    src/android/pkg/MyClass.java:9: error: Type must not be heavy BitSet (method android.pkg.MyClass.reverse(java.util.BitSet)) [HeavyBitSet]
                    src/android/pkg/MyClass.java:9: error: Type must not be heavy BitSet (parameter bitset in android.pkg.MyClass.reverse(java.util.BitSet bitset)) [HeavyBitSet]
                """,
            expectedFail = DefaultLintErrorMessage,
            sourceFiles =
                arrayOf(
                    java(
                        """
                            package android.pkg;
                            import androidx.annotation.Nullable;
                            import java.util.BitSet;

                            public class MyClass {
                                @Nullable
                                public final BitSet bitset;
                                @Nullable
                                public BitSet reverse(@Nullable BitSet bitset) { return null; }
                            }
                        """
                    ),
                    androidxNullableSource,
                ),
        )
    }
}
