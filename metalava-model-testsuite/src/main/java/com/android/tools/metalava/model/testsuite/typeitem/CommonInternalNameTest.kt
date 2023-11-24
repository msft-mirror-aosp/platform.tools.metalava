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

package com.android.tools.metalava.model.testsuite.typeitem

import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.testsuite.BaseModelTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameter

@RunWith(Parameterized::class)
class CommonInternalNameTest : BaseModelTest() {

    @Parameter(1) lateinit var params: TestParams

    data class TestParams(
        val javaType: String,
        val internalName: String,
    ) {
        fun isVarargs() = javaType.endsWith("...")

        /** Get the [TypeItem] from the method item. */
        fun getTypeItem(methodItem: MethodItem): TypeItem {
            return if (isVarargs()) {
                methodItem.parameters().single().type()
            } else {
                methodItem.returnType()
            }
        }

        override fun toString() = javaType
    }

    companion object {
        private val params =
            listOf(
                TestParams(
                    javaType = "boolean",
                    internalName = "Z",
                ),
                TestParams(
                    javaType = "byte",
                    internalName = "B",
                ),
                TestParams(
                    javaType = "char",
                    internalName = "C",
                ),
                TestParams(
                    javaType = "double",
                    internalName = "D",
                ),
                TestParams(
                    javaType = "float",
                    internalName = "F",
                ),
                TestParams(
                    javaType = "int",
                    internalName = "I",
                ),
                TestParams(
                    javaType = "int[]",
                    internalName = "[I",
                ),
                TestParams(
                    javaType = "int[][]",
                    internalName = "[[I",
                ),
                TestParams(
                    javaType = "int...",
                    internalName = "[I",
                ),
                TestParams(
                    javaType = "long",
                    internalName = "J",
                ),
                TestParams(
                    javaType = "short",
                    internalName = "S",
                ),
                TestParams(
                    javaType = "void",
                    internalName = "V",
                ),
                TestParams(
                    javaType = "java.lang.Number",
                    internalName = "Ljava/lang/Number;",
                ),
                TestParams(
                    javaType = "java.lang.Number[]",
                    internalName = "[Ljava/lang/Number;",
                ),
                TestParams(
                    javaType = "java.lang.Number...",
                    internalName = "[Ljava/lang/Number;",
                ),
                TestParams(
                    javaType = "java.util.Map.Entry<java.lang.String,java.lang.Number>",
                    internalName = "Ljava/util/Map\$Entry;",
                ),
                TestParams(
                    javaType = "pkg.UnknownClass",
                    internalName = "Lpkg/UnknownClass;",
                ),
                TestParams(
                    javaType = "pkg.UnknownClass.Inner",
                    internalName = "Lpkg/UnknownClass\$Inner;",
                ),
                TestParams(
                    javaType = "java.util.List<java.lang.Number>",
                    internalName = "Ljava/util/List;",
                ),
                TestParams(
                    javaType = "java.util.List<java.lang.Number>[]",
                    internalName = "[Ljava/util/List;",
                ),
            )

        @JvmStatic
        @Parameterized.Parameters(name = "{0},{1}")
        fun data(): Collection<Array<Any>> {
            return crossProduct(params)
        }
    }

    @Test
    fun test() {
        // If the type is void then it can only be used as a return type but if it ends with `...`
        // then it can only be used as a parameter type so choose were the type will be used and
        // how it will be accessed.
        val (returnType, parameterType) =
            if (params.isVarargs()) {
                Pair("void", params.javaType)
            } else {
                Pair(params.javaType, "int")
            }

        runCodebaseTest(
            signature(
                """
                // Signature format: 2.0
                package test.pkg {
                    public interface Foo {
                        method public $returnType method($parameterType p);
                    }
                }
                """
            ),
        ) { codebase ->
            val methodItem = codebase.assertClass("test.pkg.Foo").methods().single()
            val typeItem = params.getTypeItem(methodItem)
            assertEquals(params.internalName, typeItem.internalName())
        }
    }
}
