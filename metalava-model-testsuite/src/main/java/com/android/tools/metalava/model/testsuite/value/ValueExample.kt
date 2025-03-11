/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.tools.metalava.model.testsuite.value

import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.testsuite.value.CommonParameterizedValueTest.CodebaseProducer

/**
 * Encapsulates information about a value example.
 *
 * This will be useful for a number of different tests around values.
 */
data class ValueExample(
    /**
     * The name of the example.
     *
     * This is used both in the type name and as the attribute name in an annotation class so it
     * must be unique across all [ValueExample]s.
     */
    val name: String,

    /** The java type. */
    val javaType: String,

    /** The java expression for the value. */
    val javaExpression: String,

    /** The optional java imports. */
    val javaImports: List<String> = emptyList(),

    /** The set of [ValueUseSite]s in which this example will be tested; defaults to all of them. */
    val suitableFor: Set<ValueUseSite> = allValueUseSites,

    /**
     * The legacy string representation of [javaExpression].
     *
     * This may differ by [ProducerKind] and [ValueUseSite].
     */
    val expectedLegacySource: Expectation<String>,
) {
    /** The name of the annotation attribute that this example will use in any generated classes. */
    val attributeName = name.replace(" ", "_") + "_attr"

    /** The name of the field that this example will use in any generated classes. */
    val fieldName = attributeName.uppercase()

    companion object {
        /** A special value used for fields for whom [FieldItem.initialValue] returns `null`. */
        internal const val NO_INITIAL_FIELD_VALUE = "NO INITIAL FIELD VALUE"
        /**
         * The list of [ValueExample]s that will be tested across [CodebaseProducer] and
         * [ValueUseSite]s.
         */
        internal val valueExamples =
            listOf(
                // Check an annotation literal.
                ValueExample(
                    name = "annotation",
                    javaType = "OtherAnnotation",
                    javaExpression = "@OtherAnnotation(intType = 1)",
                    expectedLegacySource =
                        expectations {
                            common = "@test.pkg.OtherAnnotation(intType = 1)"
                            source { common = "@OtherAnnotation(intType = 1)" }
                            // TODO(b/354633349): Missing attributes.
                            attributeDefaultValue = "@test.pkg.OtherAnnotation"
                        },
                    // Annotation literals cannot be used in fields.
                    suitableFor = allValueUseSitesExceptFields,
                ),
                // Check a class literal.
                ValueExample(
                    name = "class",
                    javaType = "Class<?>",
                    javaExpression = "List.class",
                    javaImports = listOf("java.util.List"),
                    expectedLegacySource =
                        expectations {
                            common = "java.util.List.class"
                            source {
                                // TODO(b/354633349): Fully qualified is better.
                                common = "List.class"
                                attributeDefaultValue = "java.util.List.class"
                            }
                            // TODO(b/354633349): Try and make this consistent.
                            fieldValue = NO_INITIAL_FIELD_VALUE
                        },
                ),
                // Check an enum literal.
                ValueExample(
                    name = "enum",
                    javaType = "TestEnum",
                    javaExpression = "TestEnum.VALUE1",
                    expectedLegacySource =
                        expectations {
                            common = "test.pkg.TestEnum.VALUE1"
                            source {
                                // TODO(b/354633349): Fully qualified is better.
                                attributeValue = "TestEnum.VALUE1"
                            }
                            // TODO(b/354633349): Try and make this consistent.
                            fieldValue = NO_INITIAL_FIELD_VALUE
                        },
                ),
                // Check a simple int.
                ValueExample(
                    name = "int",
                    javaType = "int",
                    javaExpression = "17",
                    expectedLegacySource = expectations { common = "17" },
                ),
                // Check an int with a unary plus.
                ValueExample(
                    name = "int positive",
                    javaType = "int",
                    javaExpression = "+17",
                    expectedLegacySource =
                        expectations {
                            common = "17"

                            source {
                                // TODO(b/354633349): The leading + is unnecessary.
                                attributeValue = "+17"
                            }
                        },
                ),
                // Check an int with a unary minus.
                ValueExample(
                    name = "int negative",
                    javaType = "int",
                    javaExpression = "-17",
                    expectedLegacySource = expectations { common = "-17" },
                ),
                // Check a simple string.
                ValueExample(
                    name = "String",
                    javaType = "String",
                    javaExpression = "\"string\"",
                    expectedLegacySource =
                        expectations {
                            common = "\"string\""
                            // TODO(b/354633349): Should have surrounding quotes.
                            fieldValue = "string"
                        },
                ),
                // Check a simple string array.
                ValueExample(
                    name = "String array",
                    javaType = "String[]",
                    javaExpression = "{\"string1\", \"string2\"}",
                    expectedLegacySource =
                        expectations {
                            common = "{\"string1\", \"string2\"}"

                            // This is ok as an array is not a constant.
                            fieldValue = NO_INITIAL_FIELD_VALUE
                        },
                ),
                // Check passing a single value to an array type.
                ValueExample(
                    name = "String array with single string",
                    javaType = "String[]",
                    javaExpression = "\"string\"",
                    suitableFor = allValueUseSitesExceptFields,
                    expectedLegacySource =
                        expectations {
                            common = "\"string\""

                            jar { common = "{\"string\"}" }
                        },
                ),
                ValueExample(
                    name = "String using constant",
                    javaType = "String",
                    javaExpression = "Constants.STRING_CONSTANT",
                    expectedLegacySource =
                        expectations {
                            common = "\"constant\""

                            jar {
                                // TODO(b/354633349): Should have surrounding quotes.
                                fieldValue = "constant"
                            }

                            source {
                                common = "test.pkg.Constants.STRING_CONSTANT"
                                // TODO(b/354633349): Fully qualified is better.
                                attributeValue = "Constants.STRING_CONSTANT"
                                // TODO(b/354633349): Should at least be the constant string, if not
                                //   a field reference.
                                fieldValue = NO_INITIAL_FIELD_VALUE
                            }
                        },
                )
            )
    }
}
