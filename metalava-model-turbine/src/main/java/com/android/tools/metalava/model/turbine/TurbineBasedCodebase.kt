/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.metalava.model.turbine

import com.android.tools.metalava.model.AnnotationManager
import com.android.tools.metalava.model.CLASS_ESTIMATE
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.item.DefaultCodebase
import com.android.tools.metalava.model.source.SourceCodebase
import com.google.turbine.tree.Tree.CompUnit
import java.io.File

internal open class TurbineBasedCodebase(
    location: File,
    description: String = "Unknown",
    annotationManager: AnnotationManager,
    val allowReadingComments: Boolean,
) :
    DefaultCodebase(
        location = location,
        description = description,
        preFiltered = false,
        annotationManager = annotationManager,
        trustedApi = false,
        supportsDocumentation = true,
    ),
    SourceCodebase {

    /**
     * Map from class name to class item. Classes are added via [registerClass] while initialising
     * the codebase
     */
    private lateinit var classMap: MutableMap<String, ClassItem>

    /**
     * A list of the top-level classes declared in the codebase's source (rather than on its
     * classpath).
     */
    private lateinit var topLevelClassesFromSource: MutableList<ClassItem>

    private lateinit var initializer: TurbineCodebaseInitialiser

    override fun findClass(className: String): ClassItem? {
        return classMap[className]
    }

    override fun resolveClass(className: String) = findOrCreateClass(className)

    fun findOrCreateClass(className: String): ClassItem? {
        return initializer.findOrCreateClass(className)
    }

    override fun getTopLevelClassesFromSource(): List<ClassItem> {
        return topLevelClassesFromSource
    }

    fun registerClass(classItem: ClassItem) {
        val qualifiedName = classItem.qualifiedName()
        val existing = classMap.put(qualifiedName, classItem)
        if (existing != null) {
            error(
                "Attempted to register $qualifiedName twice; once from ${existing.fileLocation.path} and this one from ${classItem.fileLocation.path}"
            )
        }

        if (!classItem.isNestedClass()) {
            topLevelClassesFromSource.add(classItem)
        }

        addClass(classItem)
    }

    fun initialize(
        units: List<CompUnit>,
        classpath: List<File>,
        packageHtmlByPackageName: Map<String, File>,
    ) {
        topLevelClassesFromSource = ArrayList(CLASS_ESTIMATE)
        classMap = HashMap(CLASS_ESTIMATE)
        initializer = TurbineCodebaseInitialiser(units, this, classpath)
        initializer.initialize(packageHtmlByPackageName)
    }
}
