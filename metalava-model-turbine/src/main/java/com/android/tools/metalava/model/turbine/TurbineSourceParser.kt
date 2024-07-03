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

package com.android.tools.metalava.model.turbine

import com.android.tools.metalava.model.AnnotationManager
import com.android.tools.metalava.model.ClassResolver
import com.android.tools.metalava.model.source.SourceCodebase
import com.android.tools.metalava.model.source.SourceParser
import com.android.tools.metalava.model.source.SourceSet
import com.android.tools.metalava.model.source.utils.findPackage
import com.google.turbine.diag.SourceFile
import com.google.turbine.parse.Parser
import java.io.File

internal class TurbineSourceParser(
    private val annotationManager: AnnotationManager,
    private val allowReadingComments: Boolean
) : SourceParser {

    override fun getClassResolver(classPath: List<File>): ClassResolver {
        TODO("implement it")
    }

    /**
     * Returns a codebase initialized from the given Java source files, with the given description.
     */
    override fun parseSources(
        sourceSet: SourceSet,
        commonSourceSet: SourceSet,
        description: String,
        classPath: List<File>,
    ): TurbineBasedCodebase {
        val rootDir = sourceSet.sourcePath.firstOrNull() ?: File("").canonicalFile
        val codebase =
            TurbineBasedCodebase(rootDir, description, annotationManager, allowReadingComments)

        // Scan the files looking for package.html files and check to see if they have @hide
        // annotations.
        val hiddenPackages = identifyHiddenPackages(sourceSet.sources)

        val sourceFiles = getSourceFiles(sourceSet.sources)
        val units = sourceFiles.map { Parser.parse(it) }
        codebase.initialize(units, classPath, hiddenPackages)

        return codebase
    }

    private fun getSourceFiles(sources: List<File>): List<SourceFile> {
        return sources
            .filter { it.isFile && it.extension == "java" } // Ensure only Java files are included
            .map { SourceFile(it.path, it.readText()) }
    }

    override fun loadFromJar(apiJar: File): SourceCodebase {
        TODO("b/299044569 handle this")
    }

    /**
     * Identifies directories and packages that should be hidden based on the contents of
     * package.html files.
     */
    private fun identifyHiddenPackages(files: List<File>): Set<String> {
        val hiddenPackages = mutableSetOf<String>()
        files
            .filter { it.isFile && it.name == "package.html" }
            .forEach { file ->
                val content = file.readText()
                if (content.contains("@hide")) {
                    val packageName = findPackageName(file)
                    if (packageName != null) {
                        hiddenPackages.add(packageName)
                    }
                }
            }
        return hiddenPackages
    }

    /**
     * Attempts to find the package name by looking for any Java class files in the same directory,
     * if unsuccessful, it will guess based on the directory structure.
     */
    private fun findPackageName(file: File): String? {
        // First try to find a package name using the utility method which might analyze the java
        // file
        file.parentFile
            .listFiles()
            ?.filter { it.isFile && it.extension == "java" }
            ?.forEach { javaFile ->
                findPackage(javaFile)?.let {
                    return it
                }
            }

        // If no class file with package declaration was found, deduce from the directory structure
        return file.parentFile.absolutePath
            .split(File.separatorChar)
            .dropWhile { it != "java" }
            .drop(1)
            .joinToString(".")
    }
}
