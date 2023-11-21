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

import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.TypeStringConfiguration
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.model.testsuite.TestParameters
import com.android.tools.metalava.testing.java
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class CommonTypeStringTest(combinedParameters: CombinedParameters) :
    BaseModelTest(combinedParameters.baseParameters) {

    data class TypeStringParameters(
        val name: String,
        val sourceType: String = name,
        val typeStringConfiguration: TypeStringConfiguration = TypeStringConfiguration(),
        val expectedTypeString: String = sourceType,
        val typeParameters: String? = null,
    ) {
        override fun toString(): String {
            return name
        }

        companion object {
            fun forDefaultAndKotlinNulls(
                name: String,
                sourceType: String = name,
                expectedDefaultTypeString: String = sourceType,
                expectedKotlinNullsTypeString: String = sourceType,
                typeParameters: String? = null
            ): List<TypeStringParameters> {
                return fromConfigurations(
                    name = name,
                    sourceType = sourceType,
                    configs =
                        listOf(
                            ConfigurationTestCase(
                                name = "default",
                                configuration = TypeStringConfiguration(),
                                expectedTypeString = expectedDefaultTypeString
                            ),
                            ConfigurationTestCase(
                                name = "kotlin nulls",
                                configuration = TypeStringConfiguration(kotlinStyleNulls = true),
                                expectedTypeString = expectedKotlinNullsTypeString
                            )
                        ),
                    typeParameters = typeParameters
                )
            }

            fun fromConfigurations(
                name: String,
                sourceType: String,
                configs: List<ConfigurationTestCase>,
                typeParameters: String? = null
            ): List<TypeStringParameters> {
                return configs.map {
                    TypeStringParameters(
                        name = "$name - ${it.name}",
                        sourceType = sourceType,
                        typeStringConfiguration = it.configuration,
                        expectedTypeString = it.expectedTypeString,
                        typeParameters = typeParameters
                    )
                }
            }
        }
    }

    data class ConfigurationTestCase(
        val name: String,
        val configuration: TypeStringConfiguration,
        val expectedTypeString: String
    )

    data class CombinedParameters(
        val baseParameters: TestParameters,
        val typeStringParameters: TypeStringParameters,
    ) {
        override fun toString(): String {
            return "$baseParameters,$typeStringParameters"
        }
    }

    private val parameters = combinedParameters.typeStringParameters

    private fun javaTestFiles() =
        java(
            """
                package test.pkg;
                public class Foo {
                    public ${parameters.typeParameters.orEmpty()} void foo(${parameters.sourceType} arg) {}
                }
            """
        )

    private fun signatureTestFile() =
        signature(
            """
                // Signature format: 5.0
                // - kotlin-name-type-order=yes
                // - include-type-use-annotations=yes
                package test.pkg {
                  public class Foo {
                    ctor public Foo();
                    method public ${parameters.typeParameters.orEmpty()} foo(_: ${parameters.sourceType}): void;
                  }
                }
            """
        )

    @Test
    fun `Type string`() {
        runCodebaseTest(javaTestFiles(), signatureTestFile()) { codebase ->
            val method = codebase.assertClass("test.pkg.Foo").methods().single()
            val param = method.parameters().single()
            val type = param.type()
            val typeString =
                type.toTypeString(
                    annotations = parameters.typeStringConfiguration.annotations,
                    kotlinStyleNulls = parameters.typeStringConfiguration.kotlinStyleNulls,
                    filter = parameters.typeStringConfiguration.filter,
                    context = param
                )
            assertThat(typeString).isEqualTo(parameters.expectedTypeString)
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun combinedTestParameters(): Iterable<CombinedParameters> {
            return testParameters().flatMap { baseParameters ->
                testCases.map { CombinedParameters(baseParameters, it) }
            }
        }

        private val testCases =
            // Test primitives besides void (the test setup puts the type in parameter position, and
            // void can't be a parameter type).
            PrimitiveTypeItem.Primitive.values()
                .filter { it != PrimitiveTypeItem.Primitive.VOID }
                .map { TypeStringParameters(name = it.primitiveName) } +
                // Test additional types
                TypeStringParameters.forDefaultAndKotlinNulls(
                    name = "string",
                    sourceType = "String",
                    expectedDefaultTypeString = "java.lang.String",
                    expectedKotlinNullsTypeString = "java.lang.String!"
                ) +
                TypeStringParameters.forDefaultAndKotlinNulls(
                    name = "number",
                    sourceType = "Number",
                    expectedDefaultTypeString = "java.lang.Number",
                    expectedKotlinNullsTypeString = "java.lang.Number!"
                ) +
                TypeStringParameters.forDefaultAndKotlinNulls(
                    name = "int array",
                    sourceType = "int[]",
                    expectedKotlinNullsTypeString = "int[]!"
                ) +
                TypeStringParameters.forDefaultAndKotlinNulls(
                    name = "string array",
                    sourceType = "String[]",
                    expectedDefaultTypeString = "java.lang.String[]",
                    expectedKotlinNullsTypeString = "java.lang.String![]!"
                ) +
                TypeStringParameters.forDefaultAndKotlinNulls(
                    name = "int varargs",
                    sourceType = "int..."
                ) +
                TypeStringParameters.forDefaultAndKotlinNulls(
                    name = "string varargs",
                    sourceType = "String...",
                    expectedDefaultTypeString = "java.lang.String...",
                    expectedKotlinNullsTypeString = "java.lang.String!..."
                ) +
                TypeStringParameters.forDefaultAndKotlinNulls(
                    name = "string list",
                    sourceType = "java.util.List<java.lang.String>",
                    expectedKotlinNullsTypeString = "java.util.List<java.lang.String!>!"
                ) +
                TypeStringParameters.forDefaultAndKotlinNulls(
                    name = "extends string list",
                    sourceType = "java.util.List<? extends java.lang.String>",
                    expectedKotlinNullsTypeString = "java.util.List<? extends java.lang.String>!"
                ) +
                TypeStringParameters.forDefaultAndKotlinNulls(
                    name = "T",
                    expectedKotlinNullsTypeString = "T!",
                    typeParameters = "<T>"
                )
    }
}
