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

package com.android.tools.metalava.model.source

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassOrigin
import com.android.tools.metalava.model.ClassPathResolver
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.JavaConstants
import com.android.tools.metalava.model.SkeletonClassItem
import com.android.tools.metalava.model.item.DefaultCodebase
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Reporter
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile
import kotlin.collections.iterator

abstract class AbstractSourceParser(protected val reporter: Reporter) : SourceParser {

    final override fun getClassPathResolver(classPath: List<File>): ClassPathResolver =
        loadCodebaseFromJars(
            classPath,
            "Codebase from classpath",
            includeKotlinInCodebase = true,
        )

    /** Load a [DefaultCodebase] from a set of [jars]. */
    protected open fun loadCodebaseFromJars(
        jars: List<File>,
        description: String,
        includeKotlinInCodebase: Boolean,
    ): DefaultCodebase {
        val inputs =
            SourceParser.Inputs(
                sourceSet = SourceSet.empty(),
                description = description,
                classPath = jars,
                includeKotlinInCodebase = includeKotlinInCodebase,
            )

        val codebase = parseSources(inputs) ?: error("Could not create codebase from $jars")

        return codebase as DefaultCodebase
    }

    override fun loadFromJar(apiJar: File, classPath: List<File>): Codebase {
        val jars = buildList {
            add(apiJar)
            addAll(classPath)
        }
        val codebase =
            loadCodebaseFromJars(
                jars,
                "Codebase loaded from $apiJar",
                includeKotlinInCodebase = false,
            )
        initializeFromJar(codebase, apiJar)
        return codebase
    }

    /**
     * Initialize [codebase] by making sure that all classes in [jarFile] are resolved and are
     * treated as if they were added from sources.
     */
    internal fun initializeFromJar(codebase: DefaultCodebase, jarFile: File) {
        // Extract the list of class names from the jar file.
        val classNames = buildList {
            try {
                ZipFile(jarFile).use { jar ->
                    for (entry in jar.entries().iterator()) {
                        val fileName = entry.name
                        if (fileName.contains("$")) {
                            // skip inner classes
                            continue
                        }
                        if (!fileName.endsWith(JavaConstants.DOT_CLASS)) {
                            // skip entries that are not .class files.
                            continue
                        }

                        val qualifiedName =
                            fileName.removeSuffix(JavaConstants.DOT_CLASS).replace('/', '.')
                        if (qualifiedName.endsWith(".package-info")) {
                            // skip package-info files.
                            continue
                        }

                        add(qualifiedName)
                    }
                }
            } catch (e: IOException) {
                codebase.reporter.report(Issues.IO_ERROR, jarFile, e.message ?: e.toString())
            }
        }

        // Iterate over all the top level classes found in the jar file.
        for (className in classNames) {
            val classItem =
                codebase.resolveClass(className) ?: error("Could not resolve $className")

            // Make sure it is modifiable.
            classItem as SkeletonClassItem

            // Treat the jar classes as if they were specified on the command line.
            classItem.origin = ClassOrigin.COMMAND_LINE

            // Make sure that the containing package is being emitted.
            classItem.containingPackage().emit = true

            // Make sure that the class and any nested classes are emitted.
            classItem.markAsEmittable()

            // Add it to the list of top level classes.
            codebase.addTopLevelClassFromSource(classItem)
        }
    }

    /**
     * Mark this [ClassItem] and all its nested classes as being emittable, just like a class loaded
     * from sources would be.
     */
    private fun ClassItem.markAsEmittable() {
        emit = true
        nestedClasses().forEach { it.markAsEmittable() }
    }

    /**
     * Override to ensure that [inputs] are correctly prepared for [processInputs].
     *
     * Preparation includes replacing [Inputs.sourceSet] with the result of calling
     * [SourceSet.extractRoots] on it, and making [Inputs.classPath], absolute files.
     */
    final override fun parseSources(inputs: SourceParser.Inputs): Codebase? {
        val absoluteInputs =
            inputs.copy(
                sourceSet = inputs.sourceSet.extractRoots(reporter),
                classPath = inputs.classPath.map { it.absoluteFile },
            )

        return processInputs(absoluteInputs)?.also { codebase ->

            // Determine sealed class exhaustivity.
            codebase.determineIfInaccessibleClassesMakeSuperClassesNonExhaustive()
        }
    }

    /** Process the [inputs] to produce a [Codebase], if possible. */
    protected abstract fun processInputs(inputs: SourceParser.Inputs): Codebase?

    /**
     * Determine if sealed classes make super classes non-exhaustive.
     *
     * Instances of sealed classes can be matched using `when` statements. If all the subclasses of
     * a sealed class are available to API consumers, then new subclasses can't be added to the
     * sealed class because doing so would be a breaking change (clients' `when` statements would no
     * longer be exhaustive). In this case, we label the sealed class as exhaustive. If there is an
     * inaccessible class that extends a sealed class, however, then the sealed class is not
     * exhaustive. For more details, see b/447143803
     */
    private fun Codebase.determineIfInaccessibleClassesMakeSuperClassesNonExhaustive() {
        val allClasses = getTopLevelClassesFromSource()
        allClasses.forEach { classItem -> sealedClassExhaustivityHelper(classItem, false) }
    }

    /**
     * Recursively traverses the inner classes of [classItem] to determine if any sealed super
     * classes should be marked as non-exhaustive.
     *
     * A sealed class is considered non-exhaustive if it has at least one inaccessible subclass.
     *
     * @param classItem The current [ClassItem] being checked.
     * @param parentWasNotVisible True if any containing class of [classItem] was not visible.
     */
    private fun Codebase.sealedClassExhaustivityHelper(
        classItem: ClassItem,
        parentWasNotVisible: Boolean,
    ) {
        // If a ClassItem already exists for this psiClass, use its modifiers. Otherwise, create
        // new ones.
        val modifiers = classItem.modifiers
        val curClassNotVisible =
            modifiers.annotations().any { it.showability.hide() } ||
                !modifiers.hasApiVisibilityOrShowAnnotation

        if (curClassNotVisible || parentWasNotVisible) {
            val superClassName = classItem.superClassType()?.qualifiedName
            if (superClassName != null) {
                findClass(superClassName)?.mutateModifiers { setExhaustive(false) }
            }
            classItem
                .interfaceTypes()
                .map { it.qualifiedName }
                .forEach { name -> findClass(name)?.mutateModifiers { setExhaustive(false) } }
        }

        classItem.nestedClasses().forEach { nestedClass ->
            sealedClassExhaustivityHelper(
                nestedClass,
                parentWasNotVisible || curClassNotVisible,
            )
        }
    }
}
