/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.metalava.model.type

import com.android.tools.metalava.model.PrimitiveTypeItem.Primitive
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeNullability
import com.android.tools.metalava.model.TypeParameterScope
import com.android.tools.metalava.model.testing.arrayTypeItem
import com.android.tools.metalava.model.testing.classTypeItem
import com.android.tools.metalava.model.testing.primitiveTypeForKind
import com.android.tools.metalava.model.testing.stringType
import com.android.tools.metalava.model.testing.value.annotationItem
import com.android.tools.metalava.testing.EntryPoint
import com.android.tools.metalava.testing.EntryPointCallerRule
import com.android.tools.metalava.testing.EntryPointCallerTracker
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ParameterizedTypeItemParserTest {

    @Parameterized.Parameter(0) lateinit var parserProvider: TypeItemParserProvider<TestCase>

    @Parameterized.Parameter(1) lateinit var testCase: TestCase

    /**
     * Will try and rewrite the stack trace of any test failures to refer to the location where the
     * [TestCase] that is currently being tested was created.
     */
    @get:Rule val entryPointCallerRule = EntryPointCallerRule { testCase.entryPointCallerTracker }

    data class TestCase
    @EntryPoint
    constructor(
        val typeString: String,
        val expectedType: TypeItem,
        val typeParameterScope: TypeParameterScope = TypeParameterScope.empty,
        val kotlinStyleNulls: Boolean = false,
        val contextNullability: ContextNullability = ContextNullability.none,
        val unqualifiedClassHandler: UnqualifiedClassHandler =
            UnqualifiedClassHandler.PREFIX_WITH_JAVA_LANG_OR_REPORT_ERROR,
        val expectedIssues: String = "",
        val description: String? = null,
    ) {
        /**
         * Record the stack trace of the creation of this which can be used to provide a stack trace
         * to the creator of this instance in the event of a test failure.
         */
        val entryPointCallerTracker = EntryPointCallerTracker()

        override fun toString(): String =
            if (description != null) "$typeString ($description)" else typeString
    }

    companion object {
        private val annoA = annotationItem("A")
        private val annoB = annotationItem("B")
        private val annoC = annotationItem("C")
        private val nonNullAnno = annotationItem("NonNull")

        private val classTypeCases =
            listOf(
                TestCase(
                    typeString = "String",
                    expectedType = stringType(nullability = TypeNullability.PLATFORM),
                ),
            )

        private val arrayTypeCases =
            listOf(
                TestCase(
                    typeString = "String[]",
                    expectedType =
                        arrayTypeItem(
                            stringType(nullability = TypeNullability.PLATFORM),
                            nullability = TypeNullability.PLATFORM,
                        ),
                ),
                TestCase(
                    typeString = "String...",
                    expectedType =
                        arrayTypeItem(
                            stringType(nullability = TypeNullability.PLATFORM),
                            isVarargs = true,
                            nullability = TypeNullability.PLATFORM,
                        ),
                ),
            )

        private val mapEntryType =
            classTypeItem(
                "java.util.Map.Entry",
                arguments =
                    listOf(
                        classTypeItem("a.A", nullability = TypeNullability.PLATFORM),
                        classTypeItem("b.B", nullability = TypeNullability.PLATFORM),
                    ),
                outerClassType =
                    classTypeItem("java.util.Map", nullability = TypeNullability.NONNULL),
                nullability = TypeNullability.NONNULL,
                annotations = listOf(nonNullAnno),
            )

        private val annotatedTypeCases =
            listOf(
                TestCase(
                    typeString = "@A @B test.pkg.Foo",
                    expectedType =
                        classTypeItem(
                            "test.pkg.Foo",
                            nullability = TypeNullability.PLATFORM,
                            annotations = listOf(annoA, annoB),
                        ),
                ),
                TestCase(
                    typeString = "java.lang.annotation.@NonNull Annotation",
                    expectedType =
                        classTypeItem(
                            "java.lang.annotation.Annotation",
                            nullability = TypeNullability.NONNULL,
                            annotations = listOf(nonNullAnno),
                        ),
                ),
                TestCase(
                    typeString = "java.util.Map.@NonNull Entry<a.A,b.B>",
                    expectedType = mapEntryType,
                ),
                TestCase(
                    typeString = "java.util.@NonNull Set<java.util.Map.@NonNull Entry<a.A,b.B>>",
                    expectedType =
                        classTypeItem(
                            "java.util.Set",
                            arguments = listOf(mapEntryType),
                            nullability = TypeNullability.NONNULL,
                            annotations = listOf(nonNullAnno),
                        ),
                ),
                TestCase(
                    typeString = "test.pkg.@A @B Foo @B @C []",
                    expectedType =
                        arrayTypeItem(
                            classTypeItem(
                                "test.pkg.Foo",
                                nullability = TypeNullability.PLATFORM,
                                annotations = listOf(annoA, annoB),
                            ),
                            nullability = TypeNullability.PLATFORM,
                            annotations = listOf(annoB, annoC),
                        ),
                ),
                TestCase(
                    typeString = "java.lang.annotation.@NonNull Annotation @NonNull []",
                    expectedType =
                        arrayTypeItem(
                            classTypeItem(
                                "java.lang.annotation.Annotation",
                                nullability = TypeNullability.NONNULL,
                                annotations = listOf(nonNullAnno),
                            ),
                            nullability = TypeNullability.NONNULL,
                            annotations = listOf(nonNullAnno),
                        ),
                ),
                TestCase(
                    typeString = "char @NonNull []",
                    expectedType =
                        arrayTypeItem(
                            primitiveTypeForKind(Primitive.CHAR),
                            nullability = TypeNullability.NONNULL,
                            annotations = listOf(nonNullAnno),
                        ),
                ),
            )

        private val allTestCases = classTypeCases + arrayTypeCases + annotatedTypeCases

        private val parserProviders =
            listOf(
                TypeItemParserProvider(
                    "Legacy",
                    ::LegacyTypeItemParser,
                    allTestCases,
                ),
            )

        @JvmStatic
        @Parameterized.Parameters(name = "{0}: {1}")
        fun params() = parserProviders.toTestParameters()
    }

    @Test
    fun `Test obtainTypeFromString`() {
        val errorReporter = CollatingErrorReporter()
        val typeParser =
            parserProvider.createParser(
                unqualifiedClassHandler = testCase.unqualifiedClassHandler,
                kotlinStyleNulls = testCase.kotlinStyleNulls,
                errorReporter = errorReporter,
            )
        val actual =
            typeParser.obtainTypeFromString(
                testCase.typeString,
                testCase.typeParameterScope,
                testCase.contextNullability,
            )
        assertEquals(testCase.expectedType, actual)
        assertEquals(testCase.expectedIssues, errorReporter.toString())
    }
}
