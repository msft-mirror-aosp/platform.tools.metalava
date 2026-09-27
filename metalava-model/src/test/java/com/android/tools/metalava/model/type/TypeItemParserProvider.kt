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

package com.android.tools.metalava.model.type

import com.android.tools.metalava.model.AnnotationContext

/**
 * Provides a [TypeItemParser] implementation and the list of [testCases] supported by it for
 * parameterized tests.
 */
class TypeItemParserProvider<T>(
    private val name: String,
    private val factory:
        (
            AnnotationContext,
            UnqualifiedClassHandler,
            Boolean,
            TypeItemParserErrorReporter,
        ) -> TypeItemParser,
    val testCases: List<T>,
) {
    fun createParser(
        annotationContext: AnnotationContext = AnnotationContext.DEFAULT_RESOLVE_NULL,
        unqualifiedClassHandler: UnqualifiedClassHandler =
            UnqualifiedClassHandler.PREFIX_WITH_JAVA_LANG_OR_REPORT_ERROR,
        kotlinStyleNulls: Boolean = false,
        errorReporter: TypeItemParserErrorReporter = TypeItemParserErrorReporter.THROWING,
    ): TypeItemParser =
        factory(
            annotationContext,
            unqualifiedClassHandler,
            kotlinStyleNulls,
            errorReporter,
        )

    override fun toString(): String = name
}

/**
 * Flattens a list of [TypeItemParserProvider]s and their supported
 * [TypeItemParserProvider.testCases] into `(provider, testCase)` parameter arrays for JUnit
 * [org.junit.runners.Parameterized].
 */
fun <T> List<TypeItemParserProvider<T>>.toTestParameters(): List<Array<Any>> = flatMap { provider ->
    provider.testCases.map { testCase -> arrayOf(provider as Any, testCase as Any) }
}
