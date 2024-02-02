/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.tools.metalava.model.source.utils

import com.android.tools.lint.checks.infrastructure.ClassName
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Reporter
import java.io.File
import java.nio.file.Files

const val PACKAGE_HTML = "package.html"
const val OVERVIEW_HTML = "overview.html"
const val DOT_JAVA = ".java"
const val DOT_KT = ".kt"

private fun skippableDirectory(file: File): Boolean =
    file.path.endsWith(".git") && file.name == ".git"

private fun addSourceFiles(reporter: Reporter, list: MutableList<File>, file: File) {
    if (file.isDirectory) {
        if (skippableDirectory(file)) {
            return
        }
        if (Files.isSymbolicLink(file.toPath())) {
            reporter.report(
                Issues.IGNORING_SYMLINK,
                file,
                "Ignoring symlink during source file discovery directory traversal"
            )
            return
        }
        val files = file.listFiles()
        if (files != null) {
            for (child in files) {
                addSourceFiles(reporter, list, child)
            }
        }
    } else if (file.isFile) {
        when {
            file.name.endsWith(DOT_JAVA) ||
                file.name.endsWith(DOT_KT) ||
                file.name.equals(PACKAGE_HTML) ||
                file.name.equals(OVERVIEW_HTML) -> list.add(file)
        }
    }
}

internal fun gatherSources(reporter: Reporter, sourcePath: List<File>): List<File> {
    val sources = mutableListOf<File>()
    for (file in sourcePath) {
        if (file.path.isBlank()) {
            // --source-path "" means don't search source path; use "." for pwd
            continue
        }
        addSourceFiles(reporter, sources, file.absoluteFile)
    }
    return sources.sortedWith(compareBy { it.name })
}

fun extractRoots(
    reporter: Reporter,
    sources: List<File>,
    sourceRoots: MutableList<File> = mutableListOf()
): List<File> {
    // Cache for each directory since computing root for a source file is expensive
    val dirToRootCache = mutableMapOf<String, File>()
    for (file in sources) {
        val parent = file.parentFile ?: continue
        val found = dirToRootCache[parent.path]
        if (found != null) {
            continue
        }

        val root = findRoot(reporter, file) ?: continue
        dirToRootCache[parent.path] = root

        if (!sourceRoots.contains(root)) {
            sourceRoots.add(root)
        }
    }

    return sourceRoots
}

/**
 * If given a full path to a Java or Kotlin source file, produces the path to the source root if
 * possible.
 */
private fun findRoot(reporter: Reporter, file: File): File? {
    val path = file.path
    if (path.endsWith(DOT_JAVA) || path.endsWith(DOT_KT)) {
        val pkg = findPackage(file) ?: return null
        val parent = file.parentFile ?: return null
        val endIndex = parent.path.length - pkg.length
        val before = path[endIndex - 1]
        if (before == '/' || before == '\\') {
            return File(path.substring(0, endIndex))
        } else {
            reporter.report(
                Issues.IO_ERROR,
                file,
                "Unable to determine the package name. " +
                    "This usually means that a source file was where the directory does not seem to match the package " +
                    "declaration; we expected the path $path to end with /${pkg.replace('.', '/') + '/' + file.name}"
            )
        }
    }

    return null
}

/** Finds the package of the given Java/Kotlin source file, if possible */
fun findPackage(file: File): String? {
    val source = file.readText(Charsets.UTF_8)
    return findPackage(source)
}

/** Finds the package of the given Java/Kotlin source code, if possible */
private fun findPackage(source: String): String? {
    return ClassName(source).packageName
}
