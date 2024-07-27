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

import com.android.tools.metalava.model.source.SourceSet
import com.android.tools.metalava.reporter.FileLocation
import java.io.File

/** Set of [PackageDoc] for every documented package defined in the source. */
class PackageDocs(
    val packages: MutableMap<String, MutablePackageDoc>,
) {
    operator fun get(packageName: String): PackageDoc {
        return packages[packageName] ?: PackageDoc.EMPTY
    }

    companion object {
        // It would usually be unsafe to use a mutable map in a shared object like this but this
        // is safe as the only use of this does not modify this map. This is also only temporary as
        // PackageDocs.packages will be changed to an immutable Map soon.
        // TODO(b/352480646): Change to emptyMap().
        val EMPTY: PackageDocs = PackageDocs(mutableMapOf())
    }
}

/** Package specific documentation. */
interface PackageDoc {
    val fileLocation: FileLocation
    val comment: String?
    val overview: String?

    companion object {
        val EMPTY =
            object : PackageDoc {
                override val fileLocation: FileLocation
                    get() = FileLocation.UNKNOWN

                override val comment
                    get() = null

                override val overview
                    get() = null
            }
    }
}

/** Mutable package specific documentation for use in [gatherPackageJavadoc]. */
data class MutablePackageDoc(
    val qualifiedName: String,
    override var fileLocation: FileLocation = FileLocation.UNKNOWN,
    override var comment: String? = null,
    override var overview: String? = null,
) : PackageDoc

/** The kinds of package documentation file. */
private enum class PackageDocumentationKind {
    PACKAGE {
        override fun update(packageDoc: MutablePackageDoc, contents: String, file: File) {
            packageDoc.comment = packageHtmlToJavadoc(contents)
            packageDoc.fileLocation = FileLocation.forFile(file)
        }
    },
    OVERVIEW {
        override fun update(packageDoc: MutablePackageDoc, contents: String, file: File) {
            packageDoc.overview = contents
        }
    };

    /** Update kind appropriate property in [packageDoc] with [contents]. */
    abstract fun update(packageDoc: MutablePackageDoc, contents: String, file: File)
}

fun gatherPackageJavadoc(sourceSet: SourceSet): PackageDocs {
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

        val packageDoc = packages.computeIfAbsent(pkg, ::MutablePackageDoc)

        val contents = file.readText(Charsets.UTF_8)
        documentationFile.update(packageDoc, contents, file)
    }

    return PackageDocs(packages)
}
