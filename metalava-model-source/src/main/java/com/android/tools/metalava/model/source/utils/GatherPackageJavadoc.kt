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

import com.android.tools.metalava.model.item.MutablePackageDoc
import com.android.tools.metalava.model.item.PackageDocs
import com.android.tools.metalava.model.item.ResourceFile
import com.android.tools.metalava.model.source.SourceSet
import com.android.tools.metalava.model.source.toItemDocumentationFactory
import com.android.tools.metalava.reporter.FileLocation
import java.io.File

/** The kinds of package documentation file. */
private enum class PackageDocumentationKind {
    PACKAGE {
        override fun update(packageDoc: MutablePackageDoc, file: File) {
            val contents = file.readText(Charsets.UTF_8)
            packageDoc.commentFactory = packageHtmlToJavadoc(contents).toItemDocumentationFactory()
            packageDoc.fileLocation = FileLocation.forFile(file)
        }
    },
    OVERVIEW {
        override fun update(packageDoc: MutablePackageDoc, file: File) {
            packageDoc.overview = ResourceFile(file)
        }
    };

    /** Update kind appropriate property in [packageDoc] with [file]. */
    abstract fun update(packageDoc: MutablePackageDoc, file: File)
}

/**
 * Gather javadoc related to packages from the [sourceSet].
 *
 * This will look for `package.html` and `overview.html` files within the source set and then map
 * that back to a package. It will first check to see if there is a java class in the same directory
 * and if so then extract the package name from that otherwise it will construct one from the
 * directory, which may be wrong.
 *
 * @param sourceSet the sources to search for `package.html` and `overview.html` files.
 * @param packageNameFilter a lambda that given a package name will return `true` if it is a valid
 *   package and `false` otherwise. This is used to filter out any packages incorrectly inferred
 *   from `package.html` files.
 */
fun gatherPackageJavadoc(
    sourceSet: SourceSet,
    packageNameFilter: (String) -> Boolean,
): PackageDocs {
    val packages = mutableMapOf<String, MutablePackageDoc>()
    val sortedSourceRoots = sourceSet.sourcePath.sortedBy { -it.name.length }
    for (file in sourceSet.sources) {
        val documentationFile =
            when (file.name) {
                PACKAGE_HTML -> {
                    PackageDocumentationKind.PACKAGE
                }
                OVERVIEW_HTML -> {
                    PackageDocumentationKind.OVERVIEW
                }
                else -> continue
            }

        // Figure out the package: if there is a java file in the same directory, get the package
        // name from the java file. Otherwise, guess from the directory path + source roots.
        // NOTE: This causes metalava to read files other than the ones explicitly passed to it.
        var pkg =
            file.parentFile
                ?.listFiles()
                ?.filter { it.name.endsWith(DOT_JAVA) }
                ?.asSequence()
                ?.mapNotNull { findPackage(it) }
                ?.firstOrNull()
        if (pkg == null) {
            // Strip the longest prefix source root.
            val prefix = sortedSourceRoots.firstOrNull { file.startsWith(it) }?.path ?: ""
            pkg = file.parentFile.path.substring(prefix.length).trim('/').replace("/", ".")
        }

        // If the package name is invalid then skip it.
        if (!packageNameFilter(pkg)) continue

        val packageDoc = packages.computeIfAbsent(pkg, ::MutablePackageDoc)

        documentationFile.update(packageDoc, file)
    }

    return PackageDocs(packages)
}
