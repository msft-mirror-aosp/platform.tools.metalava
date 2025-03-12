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
import com.android.tools.metalava.model.Assertions
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.model.testsuite.BaseModelTest
import com.android.tools.metalava.testing.TestFileCache
import com.android.tools.metalava.testing.TestFileCacheRule
import com.android.tools.metalava.testing.cacheIn
import com.android.tools.metalava.testing.jarFromSources
import com.android.tools.metalava.testing.java
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
 */
class CommonParameterizedValueTest : BaseModelTest() {

    @Parameterized.Parameter(0) lateinit var codebaseProducer: CodebaseProducer

    @Parameterized.Parameter(1) lateinit var testCase: TestCase

    /** Produces a [Codebase] to test and runs the test on it. */
    sealed class CodebaseProducer {
        /**
         * Produce a [Codebase] and run [test] on it.
         *
         * Run with [CommonParameterizedValueTest] as the receiver so it can access
         * [runSourceCodebaseTest].
         */
        abstract fun CommonParameterizedValueTest.runCodebaseProducerTest(testCase: TestCase)

        protected fun CodebaseContext.runTestCase(testCase: TestCase) {
            val codebaseProducerContext = CodebaseProducerContext(this, this@CodebaseProducer)
            codebaseProducerContext.apply { testCase.apply { checkCodebase() } }
        }
    }

    /**
     * Base of classes that perform a specific test on a [Codebase] produced by [CodebaseProducer].
     */
    sealed class TestCase(
        /**
         * The test class against which the test case will be run.
         *
         * Each subclass will check different aspects of this, e.g.
         * [AnnotationAttributeDefaultValueTestCase] assumes it as an annotation class and checks
         * the default values on method called [memberName], [FieldValueTestCase] assumes it is a
         * class with a field called [memberName] and will check its value.
         */
        val testClass: TestClass,

        /** The name of the class member whose value is to be retrieved. */
        val memberName: String,

        /** The expectations of the test case. */
        val expectation: ProducerAwareExpectation,
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

        /** Builder for a list of [TestCase]s. */
        class TestCasesBuilder(private val testCases: MutableList<TestCase>) {
            /** Build [TestCase]s for [testClass] by invoking [body]. */
            fun forTestClass(testClass: TestClass, body: ClassTestCaseBuilder.() -> Unit) {
                val builder = ClassTestCaseBuilder(testClass)
                builder.body()
            }

            /** Builder for [TestCase]s for [testClass]. */
            inner class ClassTestCaseBuilder(private val testClass: TestClass) {
                /**
                 * Build [TestCase]s for annotation attribute named [attributeName] on [testClass].
                 */
                fun forAttribute(attributeName: String, body: AttributeTestCaseBuilder.() -> Unit) {
                    val builder = AttributeTestCaseBuilder(attributeName)
                    builder.body()
                }

                /**
                 * Builder for [TestCase]s for annotation attribute name [attributeName] on
                 * [testClass].
                 */
                inner class AttributeTestCaseBuilder(private val attributeName: String) {
                    /**
                     * Add test case for [AnnotationAttributeValue.toSource].
                     *
                     * @param forJar The expected result when retrieved from a jar file.
                     * @param forSource The expected result when retrieved from source files.
                     */
                    fun forToSource(forJar: String, forSource: String = forJar) {
                        testCases.add(
                            AnnotationAttributeValueToSourceTestCase(
                                testClass,
                                attributeName,
                                expectation = ProducerAwareExpectation(forJar, forSource)
                            )
                        )
                    }

                    /**
                     * Add test case for [AnnotationAttributeValue.toSource].
                     *
                     * @param forJar The expected result when retrieved from a jar file.
                     * @param forSource The expected result when retrieved from source files.
                     */
                    fun forDefaultValue(forJar: String, forSource: String = forJar) {
                        testCases.add(
                            AnnotationAttributeDefaultValueTestCase(
                                testClass,
                                attributeName,
                                expectation = ProducerAwareExpectation(forJar, forSource)
                            )
                        )
                    }
                }

                /** Build [TestCase]s for field named [fieldName] on [testClass]. */
                fun forField(fieldName: String, body: FieldTestCaseBuilder.() -> Unit) {
                    val builder = FieldTestCaseBuilder(fieldName)
                    builder.body()
                }

                /** Builder for [TestCase]s for field [fieldName] on [testClass]. */
                inner class FieldTestCaseBuilder(private val fieldName: String) {
                    /**
                     * Add test case for [FieldItem.fieldValue].
                     *
                     * @param forJar The expected result when retrieved from a jar file.
                     * @param forSource The expected result when retrieved from source files.
                     */
                    fun forFieldValue(forJar: String, forSource: String = forJar) {
                        testCases.add(
                            FieldValueTestCase(
                                testClass,
                                fieldName,
                                expectation = ProducerAwareExpectation(forJar, forSource)
                            )
                        )
                    }
                }
            }
        }

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

        private val testAnnotationClass =
            java(
                    """
                        package test.pkg;

                        public @interface TestAnnotation {
                            OtherAnnotation anAnnotationType() default @OtherAnnotation;
                            Class<?> classType() default void.class;
                            TestEnum enumType() default TestEnum.DEFAULT;
                            int intType() default -1;
                            String stringType() default "default";
                            String[] stringArrayType() default {};
                        }
                    """
                )
                .asTestClass("TestAnnotation")
                .dependsOn(otherAnnotationClass)

        /** Build list of [TestCase]s. */
        private fun buildTestCases(body: TestCasesBuilder.() -> Unit): List<TestCase> {
            val testCases = mutableListOf<TestCase>()
            val builder = TestCasesBuilder(testCases)
            builder.body()
            return testCases
        }

        /** The set of [TestCase]s to run in each [CodebaseProducer] in [codebaseProducers]. */
        private val testCases = buildTestCases {
            // Tests for checking basic values for different types.
            forTestClass(
                java(
                        """
                            package test.pkg;
                            import java.util.List;
                            @TestAnnotation(
                                anAnnotationType = @OtherAnnotation(intType = 1),
                                classType = List.class,
                                enumType = TestEnum.VALUE1,
                                intType = 2,
                                stringType = "string",
                                stringArrayType = {"string1", "string2"}
                            )
                            public class Basic {}
                        """
                    )
                    .asTestClass("Basic")
                    .dependsOn(testAnnotationClass)
            ) {
                forAttribute("anAnnotationType") {
                    forToSource(
                        forJar = "@test.pkg.OtherAnnotation(intType = 1)",
                        forSource = "@OtherAnnotation(intType = 1)",
                    )
                }
                forAttribute("classType") {
                    forToSource(
                        forJar = "java.util.List.class",
                        forSource = "List.class",
                    )
                }
                forAttribute("enumType") {
                    forToSource(
                        forJar = "test.pkg.TestEnum.VALUE1",
                        forSource = "TestEnum.VALUE1",
                    )
                }
                forAttribute("intType") {
                    forToSource(
                        forJar = "2",
                    )
                }
                forAttribute("stringArrayType") {
                    forToSource(
                        forJar = "{\"string1\", \"string2\"}",
                    )
                }
                forAttribute("stringType") {
                    forToSource(
                        forJar = "\"string\"",
                    )
                }
            }

            // Check passing a single value to an array type.
            forTestClass(
                java(
                        """
                            package test.pkg;
                            import java.util.List;
                            @TestAnnotation(
                                stringArrayType = "string"
                            )
                            public class ArraySingleValue {}
                        """
                    )
                    .asTestClass("ArraySingleValue")
                    .dependsOn(testAnnotationClass)
            ) {
                forAttribute("stringArrayType") {
                    forToSource(
                        forJar = "{\"string\"}",
                        forSource = "\"string\"",
                    )
                }
            }

            // Check using constants fields to specify annotation values.
            forTestClass(
                java(
                        """
                            package test.pkg;
                            import java.util.List;
                            @TestAnnotation(
                                stringType = UsingConstants.STRING_CONSTANT
                            )
                            public class UsingConstants {
                                public static final String STRING_CONSTANT = "constant";
                            }
                        """
                    )
                    .asTestClass("UsingConstants")
                    .dependsOn(testAnnotationClass)
            ) {
                forAttribute("stringType") {
                    forToSource(
                        forJar = "\"constant\"",
                        forSource = "UsingConstants.STRING_CONSTANT",
                    )
                }
            }

            // Check default values on the TestAnnotation class.
            forTestClass(testAnnotationClass) {
                forAttribute("anAnnotationType") {
                    forDefaultValue(
                        forJar = "@test.pkg.OtherAnnotation",
                    )
                }
                forAttribute("classType") {
                    forDefaultValue(
                        forJar = "void.class",
                    )
                }
                forAttribute("enumType") {
                    forDefaultValue(
                        forJar = "test.pkg.TestEnum.DEFAULT",
                    )
                }
                forAttribute("stringArrayType") {
                    forDefaultValue(
                        forJar = "{}",
                    )
                }
                forAttribute("stringType") {
                    forDefaultValue(
                        forJar = "\"default\"",
                    )
                }
            }

            // Test the behavior of some attribute default special cases.
            forTestClass(
                java(
                        """
                            package test.pkg;
                            public @interface AttributeDefaults {
                                // Use a default value that is not wrapped in an array.
                                String[] stringArrayWithStringDefault() default "default";

                                int intTypeWithFieldConstant() default DEFAULT_INT;

                                int DEFAULT_INT = 9;
                            }
                        """
                    )
                    .asTestClass("AttributeDefaults")
            ) {
                // Verify that an annotation attribute of string array type can be a single string
                // not wrapped in an array.
                forAttribute("stringArrayWithStringDefault") {
                    forDefaultValue(
                        // The jar representation is always wrapped in an array.
                        forJar = "{\"default\"}",
                        // The source representation is a single string.
                        forSource = "\"default\"",
                    )
                }

                // Verify that an annotation attribute of int type can use a field reference as the
                // default.
                forAttribute("intTypeWithFieldConstant") {
                    forDefaultValue(
                        // The jar representation is always a literal constant.
                        forJar = "9",
                        // The source representation is the field reference.
                        forSource = "test.pkg.AttributeDefaults.DEFAULT_INT",
                    )
                }
            }

            // Tests for field values.
            forTestClass(
                java(
                        """
                            package test.pkg;
                            public class FieldClass {
                                public static final String STRING_FIELD = "string";
                            }
                        """
                    )
                    .asTestClass("FieldClass")
            ) {
                forField("STRING_FIELD") {
                    forFieldValue(
                        forJar = "string",
                    )
                }
            }
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

    /** A set of expectations, one for each [CodebaseProducer] in [codebaseProducers]. */
    data class ProducerAwareExpectation(
        val forJar: String,
        val forSource: String,
    ) {
        /** Get the expectation for [codebaseProducer]. */
        fun expectationFor(codebaseProducer: CodebaseProducer) =
            when (codebaseProducer) {
                is JarCodebaseProducer -> forJar
                is SourceCodebaseProducer -> forSource
            }
    }

    /**
     * Produce a [Codebase] from [TestCase.testClass]'s [TestClass.testFileSet] and then run
     * [TestCase] against it.
     */
    private object SourceCodebaseProducer : CodebaseProducer() {
        override fun CommonParameterizedValueTest.runCodebaseProducerTest(testCase: TestCase) {
            val sources = testCase.testClass.testFileSet
            runSourceCodebaseTest(inputSet(sources.toList())) { runTestCase(testCase) }
        }

        override fun toString() = "source"
    }

    /** Produce a [Codebase] from [testJarFile]. */
    private class JarCodebaseProducer(private val testJarFile: TestFile) : CodebaseProducer() {
        override fun CommonParameterizedValueTest.runCodebaseProducerTest(testCase: TestCase) {
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

        override fun toString() = "jar"
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
     * @param testClass a [TestClass] with an `@TestAnnotation` on it.
     * @param attributeName the name of the annotation attribute.
     * @param expectation expected results of calling [AnnotationAttributeValue.toSource].
     */
    private class AnnotationAttributeValueToSourceTestCase(
        testClass: TestClass,
        attributeName: String,
        expectation: ProducerAwareExpectation,
    ) : TestCase(testClass, attributeName, expectation) {
        override fun CodebaseProducerContext.checkCodebase() {
            val testClass = classForTestCase()
            val annotation = testClass.assertAnnotation("test.pkg.TestAnnotation")
            val annotationAttribute = annotation.assertAttribute(memberName)
            val expected = expectation.expectationFor(codebaseProducer)
            assertEquals(expected, annotationAttribute.value.toSource())
        }

        override fun toString() =
            "AnnotationAttributeValue.toSource(),${testClass.className}.$memberName"
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
        attributeName: String,
        expectation: ProducerAwareExpectation,
    ) : TestCase(testClass, attributeName, expectation) {
        override fun CodebaseProducerContext.checkCodebase() {
            val annotationClass = classForTestCase()
            val annotationMethod = annotationClass.assertMethod(memberName, "")
            val expected = expectation.expectationFor(codebaseProducer)
            assertEquals(expected, annotationMethod.defaultValue())
        }

        override fun toString() = "MethodItem.defaultValue(),${testClass.className}.$memberName"
    }

    /**
     * Test a field value, i.e. [FieldItem.fieldValue], for the class's field.
     *
     * @param testClass the name of a class with fields.
     * @param fieldName the name of the field whose value is to be checked.
     * @param expectation expected results of calling [FieldItem.fieldValue].
     */
    private class FieldValueTestCase(
        testClass: TestClass,
        fieldName: String,
        expectation: ProducerAwareExpectation,
    ) : TestCase(testClass, fieldName, expectation) {
        override fun CodebaseProducerContext.checkCodebase() {
            val testClass = classForTestCase()
            val field = testClass.assertField(memberName)
            val fieldValue = assertNotNull(field.fieldValue, "No field value")
            val expected = expectation.expectationFor(codebaseProducer)
            assertEquals(expected, fieldValue.initialValue(true).toString())
        }

        override fun toString() = "FieldItem.fieldValue,${testClass.className}.$memberName"
    }

    @RequiresCapabilities(Capability.JAVA)
    @Test
    fun test() {
        codebaseProducer.apply {
            this@CommonParameterizedValueTest.runCodebaseProducerTest(testCase)
        }
    }
}
