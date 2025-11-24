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

package com.android.tools.metalava.model.multiplatform

import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.PackageItem

/**
 * A value which differs between source sets of a multiplatform project. This is a mapping from the
 * name of a source set to the value in that source set.
 */
typealias SourceSetDependent<V> = Map<String, V>

/**
 * Models a Kotlin multiplatform project (see https://kotlinlang.org/docs/multiplatform.html).
 *
 * There is a [Codebase] for each source set of the multiplatform project.
 */
class MultiplatformCodebase(sourceSetToCodebase: SourceSetDependent<Codebase>) :
    MultiplatformElement<Codebase>(sourceSetToCodebase) {
    /** A list of all the packages which exist in any source set of the codebase. */
    val packages: List<MultiplatformPackageItem> =
        aggregateChildren(
            childAccessor = { getPackages().packages },
            childIdentifier = { qualifiedName() },
            multiplatformChildCreator = { qualifiedName, sourceSetToPackage ->
                MultiplatformPackageItem(qualifiedName, sourceSetToPackage)
            },
        )

    /**
     * Searches for the package with [qualifiedName]. If the package exists in any source set,
     * returns a [MultiplatformPackageItem]. If it does not exist in any source sets, returns null.
     */
    fun findPackage(qualifiedName: String): MultiplatformPackageItem? {
        return packages.singleOrNull { it.qualifiedName == qualifiedName }
    }
}

/**
 * A wrapper for a [SourceSetDependent] map of some element. Provides common functionality for
 * different parts of a multiplatform model.
 *
 * The key of [sourceSetToElement] is nullable. If the value is null for some source set, that means
 * the element does not exist in that source set.
 */
sealed class MultiplatformElement<E>(protected val sourceSetToElement: SourceSetDependent<E?>) {
    /** The source sets which this element exists in. */
    val sourceSets: Set<String> =
        sourceSetToElement.keys.filter { sourceSetToElement[it] != null }.toSet()

    /**
     * Computes a list of the [MultiplatformElement] children with type [C] of this element. For
     * instance, this can be used to list all packages in a codebase, all classes in a package, etc.
     *
     * @param childAccessor Lists the children for the element of one source set.
     * @param childIdentifier Returns an identifier for a child of type [C] in order to collect
     *   children with the same signature from different source sets into a [MultiplatformElement].
     * @param multiplatformChildCreator Creates a [MultiplatformElement] for a child from an
     *   identifier and a mapping of source set to value of the child in that source set.
     */
    protected fun <C, M : MultiplatformElement<C>, I> aggregateChildren(
        childAccessor: E.() -> List<C>,
        childIdentifier: C.() -> I,
        multiplatformChildCreator: (I, SourceSetDependent<C?>) -> M,
    ): List<M> {
        // Create a mapping from source set to the children that exist in that source set.
        val sourceSetToChildren =
            sourceSetToElement.mapValues { (_, parent) -> parent?.childAccessor() ?: emptyList() }
        val allChildIdentifiers =
            sourceSetToChildren.values
                .flatMap { childList -> childList.map { child -> childIdentifier(child) } }
                .toSet()
        // For each child identifier, find the value of the child in all source sets, and create a
        // MultiplatformElement for it.
        return allChildIdentifiers.map { childIdentifier ->
            val sourceSetToChild =
                sourceSetToChildren.mapValues { (_, children) ->
                    children.singleOrNull { child -> childIdentifier(child) == childIdentifier }
                }
            multiplatformChildCreator(childIdentifier, sourceSetToChild)
        }
    }
}

/** Wrapper for common functionality of [MultiplatformElement] with [Item] element types. */
sealed class MultiplatformItem<I : Item>(sourceSetToItem: SourceSetDependent<I?>) :
    MultiplatformElement<I>(sourceSetToItem)

/** A package named [qualifiedName] in a [MultiplatformCodebase]. */
class MultiplatformPackageItem(
    val qualifiedName: String,
    sourceSetToItem: SourceSetDependent<PackageItem?>,
) : MultiplatformItem<PackageItem>(sourceSetToItem) {
    override fun toString(): String {
        return "multiplatform package $qualifiedName"
    }
}
