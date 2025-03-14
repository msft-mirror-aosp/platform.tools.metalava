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

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.metalava.model.AnnotationAttributeValue
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.Assertions
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.model.testsuite.value.ValueExample.Companion.NO_INITIAL_FIELD_VALUE
import com.android.tools.metalava.model.testsuite.value.ValueExample.Companion.valueExamples
import com.android.tools.metalava.testing.TestFileCache
import com.android.tools.metalava.testing.TestFileCacheRule
import com.android.tools.metalava.testing.cacheIn
import com.android.tools.metalava.testing.jarFromSources
import com.android.tools.metalava.testing.java
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.ClassRule
import org.junit.Test
import org.junit.runners.Parameterized

/**
 * Parameterized test that will run test cases against both jars and sources to check the behavior
 * of values.
 *
 * This only has a single test method which runs a list of [TestCase]s against a list of
 * [CodebaseProducer]s. A [TestCase] defines the test to run and the [CodebaseProducer] defines the
 * [Codebase] it will be run on, either jar or source based.
 *
 * [TestCase] is an abstract class with multiple implementations, each of which tests a different
 * value, e.g. annotation value, method default value, field value. This approach was taken instead
 * of having methods for each use of a value or separate classes because the values should be being
 * handled consistently irrespective of where they are being used, but currently they are not.
 * Having all the tests for them in the same place highlights the inconsistencies and makes it
 * easier to migrate to consistent handling of the values.
 *
 * Each [TestCase] runs against a [TestClass] which specifies the class name to use and the set of
 * [TestFile]s to use. The jar tests run against a jar constructed from all the distinct [TestFile]
 * used by all the [testCases]. The source tests run against just the set of sources needed by the
 * [TestClass].
 *
 * The set of files needed by a [TestClass] are encapsulated in a [TestClass.testFileSet] which
 * tracks dependencies.
 *
 * The set of tests to run is defined by a list of [ValueExample]s which provide the necessary
 * information to construct [TestClass]es and the [TestCase]s that run against them. Each
 * [ValueExample] is tested in all possible [ValueUseSite] (although some examples do not work on
 * some sites). The aim is to create an exhaustive set of tests that first map out the existing
 * inconsistencies and eventually ensure consistent behavior.
 */
class CommonParameterizedValueTest : BaseModelTest() {

    @Parameterized.Parameter(0) lateinit var codebaseProducer: CodebaseProducer

    @Parameterized.Parameter(1) lateinit var testCase: TestCase<*>

    /** Produces a [Codebase] to test and runs the test on it. */
    sealed class CodebaseProducer(val kind: ProducerKind) {
        /**
         * Produce a [Codebase] and run [test] on it.
         *
         * Run with [CommonParameterizedValueTest] as the receiver so it can access
         * [runSourceCodebaseTest].
         */
        abstract fun CommonParameterizedValueTest.runCodebaseProducerTest(testCase: TestCase<*>)

        protected fun CodebaseContext.runTestCase(testCase: TestCase<*>) {
            val codebaseProducerContext = CodebaseProducerContext(this, this@CodebaseProducer)
            codebaseProducerContext.apply { testCase.apply { checkCodebase() } }
        }

        final override fun toString() = kind.toString().lowercase()
    }

    /**
     * Base of classes that perform a specific test on a [Codebase] produced by [CodebaseProducer].
     */
    sealed class TestCase<T>(
        /** The name of the test case. */
        private val testCaseName: String,

        /**
         * The test class against which the test case will be run.
         *
         * Each subclass will check different aspects of this, e.g.
         * [AnnotationAttributeDefaultValueTestCase] assumes it as an annotation class and checks
         * the default values on method "attr", [FieldValueTestCase] assumes it is a class with a
         * field called "FIELD" and will check its value.
         */
        val testClass: TestClass,

        /** The expectations of the test case. */
        val expectation: Expectation<T>,
    ) : Assertions {
        abstract fun CodebaseProducerContext.checkCodebase()

        /**
         * Get the [ClassItem] to be tested.
         *
         * Runs with [CodebaseProducerContext] as the receiver so it can access
         * [CodebaseProducerContext.retrieveClass] to retrieve the [ClassItem].
         */
        protected fun CodebaseProducerContext.classForTestCase(): ClassItem {
            return retrieveClass("test.pkg.${testClass.className}")
        }

        final override fun toString() = "$testCaseName,${testClass.className}"
    }

    /**
     * A test class to run against.
     *
     * @param className the name of the test class (in the `test.pkg` package).
     * @param testFileSet the complete set of [TestFile]s needed to compile the test class.
     */
    data class TestClass(val className: String, val testFileSet: Set<TestFile>) {
        /** Return a new instance of this adding [dependency] to [testFileSet]. */
        fun dependsOn(dependency: TestFile) = copy(testFileSet = testFileSet + dependency)

        /** Return a new instance of this adding [dependency] to [testFileSet]. */
        fun dependsOn(dependency: TestClass) =
            copy(testFileSet = testFileSet + dependency.testFileSet)
    }

    companion object {
        /** Create a [TestFileCache] whose lifespan encompasses all the tests in this class. */
        @ClassRule @JvmField val testFileCacheRule = TestFileCacheRule()

        private fun TestFile.asTestClass(className: String): TestClass {
            val cached = cacheIn(testFileCacheRule)
            return TestClass(className, setOf(cached))
        }

        private val testEnumClass =
            java(
                    """
                        package test.pkg;
                        public enum TestEnum {
                            DEFAULT,
                            VALUE1,
                        }
                    """
                )
                .cacheIn(testFileCacheRule)

        private val testConstantsClass =
            java(
                    """
                        package test.pkg;
                        public interface Constants {
                            String STRING_CONSTANT = "constant";
                        }
                    """
                )
                .cacheIn(testFileCacheRule)

        private val otherAnnotationClass =
            java(
                    """
                        package test.pkg;

                        public @interface OtherAnnotation {
                            Class<?> classType() default void.class;
                            TestEnum enumType() default TestEnum.DEFAULT;
                            int intType() default -1;
                            String stringType() default "default";
                            String[] stringArrayType() default {};
                        }
                    """
                )
                .asTestClass("OtherAnnotation")
                .dependsOn(testEnumClass)

        /** Names of constant types used in [ValueExample.javaType]. */
        private val constantTypeNames = buildSet {
            for (kind in PrimitiveTypeItem.Primitive.entries) {
                add(kind.primitiveName)
            }
            add("String")
        }

        /** The set of [TestCase]s to run in each [CodebaseProducer] in [codebaseProducers]. */
        private val testCases = buildList {
            // Verify that all the ValueExamples have distinct names.
            val duplicates =
                valueExamples
                    .groupingBy { it.name }
                    .eachCount()
                    .filter { it.value > 1 }
                    .map { it.key }
            if (duplicates.isNotEmpty()) {
                error("Duplicate value examples: $duplicates")
            }

            // Construct [TestCase]s from [ValueExample].
            for (valueExample in valueExamples) {
                val attributeName = "attr"

                // If suitable add a test for [ValueUseSite.ATTRIBUTE_DEFAULT_VALUE].
                if (ValueUseSite.ATTRIBUTE_DEFAULT_VALUE in valueExample.suitableFor) {
                    // Create a [TestClass] for an annotation class that has an attribute for this
                    // [ValueExample], setting its expression as the default.
                    val annotationWithDefaults =
                        valueExample.generateAnnotationClass(
                            "AnnotationWithDefaults",
                            attributeName,
                            withDefaults = true,
                        )

                    add(
                        AnnotationAttributeDefaultValueTestCase(
                            annotationWithDefaults,
                            attributeName,
                            expectation = valueExample.expectedLegacySource,
                        )
                    )
                }

                // If suitable add a test for [ValueUseSite.ATTRIBUTE_VALUE].
                if (ValueUseSite.ATTRIBUTE_VALUE in valueExample.suitableFor) {
                    // Create a [TestClass] for an annotation class that has an attribute for this
                    // [ValueExample] but does not set a default. Used by the following class for
                    // checking annotation attribute values.
                    val annotationWithoutDefaults =
                        valueExample.generateAnnotationClass(
                            "AnnotationWithoutDefaults",
                            attributeName,
                            withDefaults = false,
                        )

                    // Create a [TestClass] that is annotated with `AnnotationWithoutDefaults`
                    // which uses this [ValueExample]'s expression.
                    val annotationTestClass =
                        valueExample.generateAnnotatedTestClass(
                            "AnnotationTestClass",
                            attributeName,
                            annotationWithoutDefaults
                        )

                    add(
                        AnnotationAttributeValueToSourceTestCase(
                            annotationTestClass,
                            annotationWithoutDefaults,
                            attributeName,
                            expectation = valueExample.expectedLegacySource,
                        )
                    )

                    add(
                        AnnotationItemToSourceTestCase(
                            annotationTestClass,
                            annotationWithoutDefaults,
                            expectation = valueExample.expectedLegacySource,
                        )
                    )
                }

                // If suitable add a test for [ValueUseSite.FIELD_VALUE].
                if (ValueUseSite.FIELD_VALUE in valueExample.suitableFor) {
                    val fieldName = "FIELD"

                    // Create a [TestClass] that has a field for each suitable [ValueExample].
                    val fieldTestClass =
                        valueExample.generateFieldTestClass(
                            "FieldTestClass",
                            fieldName,
                        )

                    // If the type is suitable for use in a constant field then assume the field is
                    // constant.
                    val isConstant = valueExample.javaType in constantTypeNames

                    add(
                        FieldValueTestCase(
                            fieldTestClass,
                            fieldName,
                            expectation = valueExample.expectedLegacySource,
                            isConstant,
                        )
                    )

                    add(
                        FieldWriteValueWithSemicolonTestCase(
                            fieldTestClass,
                            fieldName,
                            expectation = valueExample.expectedLegacySource,
                            isConstant,
                        )
                    )
                }
            }
        }

        /** Append all the imports provided by this list to [buffer]. */
        private fun ValueExample.appendImportsTo(buffer: StringBuilder) {
            for (javaImport in javaImports) {
                buffer.append("import ")
                buffer.append(javaImport)
                buffer.append(";\n")
            }
        }

        /**
         * Create an annotation [TestClass] for this [ValueExample].
         *
         * @param classNamePrefix the prefix of the class.
         * @param attributeName the name of the annotation attribute.
         * @param withDefaults true if defaults should be added, false otherwise.
         */
        private fun ValueExample.generateAnnotationClass(
            classNamePrefix: String,
            attributeName: String,
            withDefaults: Boolean
        ): TestClass {
            val className = "${classNamePrefix}_$classSuffix"
            return java(
                    buildString {
                        append("package test.pkg;\n")
                        appendImportsTo(this)
                        append("public @interface $className {\n")
                        append("    ")
                        append(javaType)
                        append(" ")
                        append(attributeName)
                        append("()")
                        if (withDefaults) {
                            append(" default ")
                            append(javaExpression)
                        }
                        append(";\n")
                        append("}\n")
                    }
                )
                .asTestClass(className)
                .dependsOn(otherAnnotationClass)
                .dependsOn(testConstantsClass)
        }

        /**
         * Create a normal [TestClass] annotated with [annotationTestClass].
         *
         * The annotation uses the appropriate values from this [ValueExample].
         *
         * @param classNamePrefix the prefix of the class.
         * @param annotationTestClass the annotation [TestClass] to annotate the class with.
         */
        private fun ValueExample.generateAnnotatedTestClass(
            classNamePrefix: String,
            attributeName: String,
            annotationTestClass: TestClass,
        ): TestClass {
            val className = "${classNamePrefix}_$classSuffix"
            return java(
                    buildString {
                        append("package test.pkg;\n")
                        appendImportsTo(this)
                        append("@")
                        append(annotationTestClass.className)
                        append("(")
                        append(attributeName)
                        append(" = ")
                        append(javaExpression)
                        append(")")
                        append("public class $className {}\n")
                    }
                )
                .asTestClass(className)
                .dependsOn(annotationTestClass)
        }

        /**
         * Create a [TestClass] containing a "constant" field for this [ValueExample].
         *
         * @param classNamePrefix the prefix of the class.
         */
        private fun ValueExample.generateFieldTestClass(
            classNamePrefix: String,
            fieldName: String,
        ): TestClass {
            val className = "${classNamePrefix}_$classSuffix"
            return java(
                    buildString {
                        append("package test.pkg;\n")
                        appendImportsTo(this)
                        append("public class $className {\n")
                        append("    public static final ")
                        append(javaType)
                        append(" ")
                        append(fieldName)
                        append(" = ")
                        append(javaExpression)
                        append(";\n")
                        append("}\n")
                    }
                )
                .asTestClass(className)
                .dependsOn(otherAnnotationClass)
        }

        /** The jar includes all the distinct [TestFile]s used by [testCases]. */
        private val sourcesForJar = buildSet {
            for (testCase in testCases) {
                addAll(testCase.testClass.testFileSet)
            }
        }

        /** Jar file containing compiled versions of [sourcesForJar]. */
        private val testJarFile =
            jarFromSources(
                    "binary-class.jar",
                    *sourcesForJar.toTypedArray(),
                )
                // Cache to reuse in all of this class' tests.
                .cacheIn(testFileCacheRule)

        /** The list of [CodebaseProducer]s for which all the [testCases] will be run. */
        private val codebaseProducers =
            listOf(
                JarCodebaseProducer(testJarFile),
                SourceCodebaseProducer,
            )

        /**
         * The list of test parameters.
         *
         * The cross product of all [testCases] and all the [codebaseProducers].
         */
        @JvmStatic
        @Parameterized.Parameters
        fun params() =
            codebaseProducers.flatMap { codebaseProducer ->
                testCases.map { testCase -> arrayOf(codebaseProducer, testCase) }
            }
    }

    /**
     * Produce a [Codebase] from [TestCase.testClass]'s [TestClass.testFileSet] and then run
     * [TestCase] against it.
     */
    private object SourceCodebaseProducer : CodebaseProducer(ProducerKind.SOURCE) {
        override fun CommonParameterizedValueTest.runCodebaseProducerTest(testCase: TestCase<*>) {
            val sources = testCase.testClass.testFileSet
            runSourceCodebaseTest(inputSet(sources.toList())) { runTestCase(testCase) }
        }
    }

    /** Produce a [Codebase] from [testJarFile]. */
    private class JarCodebaseProducer(private val testJarFile: TestFile) :
        CodebaseProducer(ProducerKind.JAR) {
        override fun CommonParameterizedValueTest.runCodebaseProducerTest(testCase: TestCase<*>) {
            runSourceCodebaseTest(
                // Unused class, present simply to force the test to be run against models that
                // support Java.
                java(
                    """
                        package test.pkg;
                        public class Foo {}
                    """
                ),
                testFixture =
                    TestFixture(
                        additionalClassPath = listOf(testJarFile.createFile(temporaryFolder.root))
                    ),
            ) {
                runTestCase(testCase)
            }
        }
    }

    /** Augment [BaseModelTest.CodebaseContext] with [codebaseProducer]. */
    class CodebaseProducerContext(
        delegate: CodebaseContext,
        val codebaseProducer: CodebaseProducer,
    ) : CodebaseContext by delegate {
        /**
         * Get the class from the [Codebase].
         *
         * Resolves it if it does not exist so that it will work for both source classes and jar
         * classes.
         */
        fun retrieveClass(qualifiedName: String): ClassItem {
            return codebase.resolveClass(qualifiedName)
                ?: error("Expected $qualifiedName to be defined")
        }
    }

    /**
     * Test [AnnotationAttributeValue.toSource] method.
     *
     * @param testClass a [TestClass] annotated with an [annotationTestClass]
     * @param annotationTestClass the annotation [TestClass].
     * @param attributeName the name of the annotation attribute.
     * @param expectation expected results of calling [AnnotationAttributeValue.toSource].
     */
    private class AnnotationAttributeValueToSourceTestCase(
        testClass: TestClass,
        private val annotationTestClass: TestClass,
        private val attributeName: String,
        expectation: Expectation<String>,
    ) :
        TestCase<String>(
            "AnnotationAttributeValue.toSource()",
            testClass,
            expectation,
        ) {
        override fun CodebaseProducerContext.checkCodebase() {
            val testClass = classForTestCase()
            val annotation = testClass.assertAnnotation("test.pkg.${annotationTestClass.className}")
            val annotationAttribute = annotation.assertAttribute(attributeName)

            // Get the expected value.
            val expected =
                expectation.expectationFor(
                    codebaseProducer.kind,
                    ValueUseSite.ATTRIBUTE_VALUE,
                    codebase,
                )
            assertEquals(expected, annotationAttribute.value.toSource())
        }
    }

    /**
     * Test [AnnotationItem.toSource] method.
     *
     * @param testClass a [TestClass] annotated with an [annotationTestClass]
     * @param annotationTestClass the annotation [TestClass].
     * @param expectation expected results of calling [AnnotationItem.toSource].
     */
    private class AnnotationItemToSourceTestCase(
        testClass: TestClass,
        private val annotationTestClass: TestClass,
        expectation: Expectation<String>,
    ) :
        TestCase<String>(
            "AnnotationItem.toSource()",
            testClass,
            expectation,
        ) {
        override fun CodebaseProducerContext.checkCodebase() {
            val testClass = classForTestCase()
            val annotation = testClass.assertAnnotation("test.pkg.${annotationTestClass.className}")

            // Get the expected value.
            val expected =
                expectation.expectationFor(
                    codebaseProducer.kind,
                    ValueUseSite.ANNOTATION_TO_SOURCE,
                    codebase,
                )

            val wholeAnnotation = annotation.toSource()
            // Extract the value from the whole annotation.
            val actual = wholeAnnotation.substringAfter("=").substringBeforeLast(")")
            assertEquals(expected, actual)
        }
    }

    /**
     * Test an annotation attribute's default value, i.e. [MethodItem.defaultValue], for the
     * annotation's class's method.
     *
     * @param testClass the name of an annotation [TestClass].
     * @param attributeName the name of the annotation attribute.
     * @param expectation expected results of calling [MethodItem.defaultValue].
     */
    private class AnnotationAttributeDefaultValueTestCase(
        testClass: TestClass,
        private val attributeName: String,
        expectation: Expectation<String>,
    ) :
        TestCase<String>(
            "MethodItem.defaultValue()",
            testClass,
            expectation,
        ) {
        override fun CodebaseProducerContext.checkCodebase() {
            val annotationClass = classForTestCase()
            val annotationMethod = annotationClass.assertMethod(attributeName, "")

            // Get the expected value.
            val expected =
                expectation.expectationFor(
                    codebaseProducer.kind,
                    ValueUseSite.ATTRIBUTE_DEFAULT_VALUE,
                    codebase,
                )
            assertEquals(expected, annotationMethod.defaultValue())
        }
    }

    /**
     * Test a field value, i.e. [FieldItem.fieldValue], for the class's field.
     *
     * @param testClass the name of a class with the field.
     * @param fieldName the name of the field whose value is to be checked.
     * @param expectation expected results of calling [FieldItem.fieldValue].
     */
    private class FieldValueTestCase(
        testClass: TestClass,
        private val fieldName: String,
        expectation: Expectation<String>,
        private val isConstant: Boolean,
    ) :
        TestCase<String>(
            "FieldItem.fieldValue",
            testClass,
            expectation,
        ) {
        override fun CodebaseProducerContext.checkCodebase() {
            val testClass = classForTestCase()
            val field = testClass.assertField(fieldName)
            val fieldValue = assertNotNull(field.fieldValue, "No field value")

            // If this is a constant then get the expectation, otherwise, expect it to have no
            // value.
            val expected =
                if (isConstant)
                    expectation.expectationFor(
                        codebaseProducer.kind,
                        ValueUseSite.FIELD_VALUE,
                        codebase,
                    )
                else NO_INITIAL_FIELD_VALUE

            val actual = fieldValue.initialValue(true)?.toString() ?: NO_INITIAL_FIELD_VALUE
            assertEquals(expected, actual)
        }
    }

    /**
     * Test writing a field value, i.e. [FieldItem.writeValueWithSemicolon], for the class's field.
     *
     * @param testClass the name of a class with the field.
     * @param fieldName the name of the field whose value is to be checked.
     * @param expectation expected results of calling [FieldItem.fieldValue].
     */
    private class FieldWriteValueWithSemicolonTestCase(
        testClass: TestClass,
        private val fieldName: String,
        expectation: Expectation<String>,
        private val isConstant: Boolean,
    ) :
        TestCase<String>(
            "FieldItem.writeWithSemicolon",
            testClass,
            expectation,
        ) {
        override fun CodebaseProducerContext.checkCodebase() {
            val testClass = classForTestCase()
            val field = testClass.assertField(fieldName)

            // If this is a constant then get the expectation, otherwise, expect it to have no
            // value.
            val expected =
                if (isConstant)
                    expectation.expectationFor(
                        codebaseProducer.kind,
                        ValueUseSite.FIELD_WRITE_WITH_SEMICOLON,
                        codebase,
                    )
                else NO_INITIAL_FIELD_VALUE

            // Print the field with semicolon.
            val stringWriter = StringWriter()
            PrintWriter(stringWriter).use { writer -> field.writeValueWithSemicolon(writer) }
            val withSemicolon = stringWriter.toString()

            // Extract the value from the " = ...; // ...." string.
            val actual =
                if (withSemicolon == ";") NO_INITIAL_FIELD_VALUE
                else withSemicolon.substringAfter(" = ").substringBefore(";")

            assertEquals(expected, actual)
        }
    }

    @RequiresCapabilities(Capability.JAVA)
    @Test
    fun test() {
        codebaseProducer.apply {
            this@CommonParameterizedValueTest.runCodebaseProducerTest(testCase)
        }
    }
}
