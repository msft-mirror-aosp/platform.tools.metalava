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

package com.android.tools.metalava.model

import java.io.File

/**
 * Represents a complete unit of code -- typically in the form of a set of source trees, but also
 * potentially backed by .jar files or even signature files
 */
interface Codebase {
    /** Description of what this codebase is (useful during debugging) */
    var description: String

    /**
     * The location of the API. Could point to a signature file, or a directory root for source
     * files, or a jar file, etc.
     */
    var location: File

    /** The manager of annotations within this codebase. */
    val annotationManager: AnnotationManager

    /** The packages in the codebase (may include packages that are not included in the API) */
    fun getPackages(): PackageList

    /**
     * The package documentation, if any - this returns overview.html files for each package that
     * provided one. Not all codebases provide this.
     */
    fun getPackageDocs(): PackageDocs?

    /** The rough size of the codebase (package count) */
    fun size(): Int

    /** Returns a class identified by fully qualified name, if in the codebase */
    fun findClass(className: String): ClassItem?

    /** Returns a package identified by fully qualified name, if in the codebase */
    fun findPackage(pkgName: String): PackageItem?

    /** Returns true if this codebase supports documentation. */
    fun supportsDocumentation(): Boolean

    /**
     * Returns true if this codebase corresponds to an already trusted API (e.g. is read in from
     * something like an existing signature file); in that case, signature checks etc will not be
     * performed.
     */
    fun trustedApi(): Boolean

    fun accept(visitor: ItemVisitor) {
        getPackages().accept(visitor)
    }

    fun acceptTypes(visitor: TypeVisitor) {
        getPackages().acceptTypes(visitor)
    }

    /** Creates an annotation item for the given (fully qualified) Java source */
    fun createAnnotation(
        source: String,
        context: Item? = null,
    ): AnnotationItem

    /** Clear the [Item.tag] fields (prior to iteration like DFS) */
    fun clearTags() {
        getPackages().packages.forEach { pkg ->
            pkg.allClasses().forEach { cls -> cls.tag = false }
        }
    }

    /** Reports that the given operation is unsupported for this codebase type */
    fun unsupported(desc: String? = null): Nothing

    /** Discards this model */
    fun dispose() {
        description += " [disposed]"
    }

    /** If this codebase was filtered from another codebase, this points to the original */
    var original: Codebase?

    /** If true, this codebase has already been filtered */
    val preFiltered: Boolean

    fun isEmpty(): Boolean {
        return getPackages().packages.isEmpty()
    }
}

sealed class MinSdkVersion

data class SetMinSdkVersion(val value: Int) : MinSdkVersion()

object UnsetMinSdkVersion : MinSdkVersion()

abstract class DefaultCodebase(
    override var location: File,
    override val annotationManager: AnnotationManager,
) : Codebase {
    override var original: Codebase? = null
    @Suppress("LeakingThis") override var preFiltered: Boolean = original != null

    override fun getPackageDocs(): PackageDocs? = null

    override fun unsupported(desc: String?): Nothing {
        error(
            desc
                ?: "This operation is not available on this type of codebase (${this.javaClass.simpleName})"
        )
    }
}
