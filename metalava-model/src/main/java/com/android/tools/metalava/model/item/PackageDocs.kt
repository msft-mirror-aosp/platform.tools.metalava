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

package com.android.tools.metalava.model.item

import com.android.tools.metalava.reporter.FileLocation

/** Set of [PackageDoc] for every documented package defined in the source. */
class PackageDocs(
    private val packages: Map<String, PackageDoc>,
) : Iterable<Map.Entry<String, PackageDoc>> {

    operator fun get(packageName: String): PackageDoc {
        return packages[packageName] ?: PackageDoc.EMPTY
    }

    override fun iterator() = packages.entries.iterator()

    companion object {
        val EMPTY: PackageDocs = PackageDocs(emptyMap())
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
