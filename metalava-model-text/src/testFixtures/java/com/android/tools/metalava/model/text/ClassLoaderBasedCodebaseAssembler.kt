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

package com.android.tools.metalava.model.text

import com.android.tools.metalava.model.ApiVariantSelectors
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassKind
import com.android.tools.metalava.model.ClassOrigin
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.ItemDocumentation
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.SourceLanguage
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.createImmutableModifiers
import com.android.tools.metalava.model.item.CodebaseAssembler
import com.android.tools.metalava.model.item.DefaultCodebase
import com.android.tools.metalava.model.item.DefaultCodebaseAssembler
import com.android.tools.metalava.model.item.DefaultCodebaseFactory
import com.android.tools.metalava.model.item.DefaultItemFactory
import com.android.tools.metalava.model.item.PackageDocs
import com.android.tools.metalava.model.utils.splitIntoOptionalQualifierAndSimpleName
import com.android.tools.metalava.reporter.FileLocation
import java.io.File
import java.net.URLClassLoader

/**
 * A [CodebaseAssembler] that will use information from a list of jars accessed through a
 * [URLClassLoader] to populate a [Codebase].
 *
 * At the moment it only supports populating [ClassItem]s (without any members or annotations).
 */
internal class ClassLoaderBasedCodebaseAssembler(
    jars: List<File>,
    codebaseFactory: DefaultCodebaseFactory,
) : DefaultCodebaseAssembler() {
    internal val codebase = codebaseFactory(this)

    /** Creates [Item] instances for this. */
    override val itemFactory =
        DefaultItemFactory(
            codebase = codebase,
            // Class files do not contain information about whether an item was originally
            // created from Java or Kotlin.
            defaultSourceLanguage = SourceLanguage.UNKNOWN,
            // Class files do not have any information about API surfaces so they can use
            // the same immutable ApiVariantSelectors.
            defaultVariantSelectorsFactory = ApiVariantSelectors.IMMUTABLE_FACTORY,
        )

    private val classLoader by
        lazy(LazyThreadSafetyMode.NONE) {
            val urls = jars.map { it.toURI().toURL() }.toTypedArray()
            URLClassLoader(urls, null)
        }

    internal fun initialize() {
        // Make sure that it has a root package.
        codebase.packageTracker.createInitialPackages(PackageDocs.EMPTY)
    }

    override fun emptyPackageDocumentationFactory() = ItemDocumentation.NONE_FACTORY

    override fun createPackageFromUnderlyingModel(qualifiedName: String): PackageItem? {
        TODO("Not yet implemented")
    }

    /**
     * Search for the class called [qualifiedName].
     *
     * This will attempt to find the class using [qualifiedName], returning it if found. Otherwise,
     * it will replace the last `.` with a `$` just in case it was for a nested class and search for
     * that name. It will repeat that until it finds the class or runs out of `.`s to replace.
     */
    private fun findClassInClassLoader(qualifiedName: String): Class<*>? {
        var binaryName = qualifiedName
        do {
            try {
                return classLoader.loadClass(binaryName)
            } catch (e: ClassNotFoundException) {
                // If the class could not be found then maybe it was a nested class so replace the
                // last '.' in the name with a $ and try again. If there is no '.' then return.
                val (before, after) = binaryName.splitIntoOptionalQualifierAndSimpleName()
                if (before == null) {
                    return null
                } else {
                    binaryName = "$before\$$after"
                }
            }
        } while (true)
    }

    override fun createClassFromUnderlyingModel(qualifiedName: String): ClassItem? {
        val cls = findClassInClassLoader(qualifiedName) ?: return null
        val packageName = cls.`package`.name

        val packageItem = codebase.findOrCreatePackage(packageName)
        return itemFactory.createClassItem(
            fileLocation = FileLocation.UNKNOWN,
            modifiers = createImmutableModifiers(VisibilityLevel.PACKAGE_PRIVATE),
            classKind = ClassKind.CLASS,
            containingClass = null,
            containingPackage = packageItem,
            qualifiedName = cls.canonicalName,
            typeParameterList = TypeParameterList.NONE,
            origin = ClassOrigin.CLASS_PATH,
            superClassType = null,
            interfaceTypes = emptyList(),
        )
    }

    companion object {
        /** Create a [ClassLoaderBasedCodebaseAssembler]. */
        fun createAssembler(
            jars: List<File>,
            codebaseConfig: Codebase.Config
        ): ClassLoaderBasedCodebaseAssembler {
            val location = jars.first()

            val assembler =
                ClassLoaderBasedCodebaseAssembler(
                    jars,
                    codebaseFactory = { assembler ->
                        DefaultCodebase(
                            location = location,
                            description = "Codebase for resolving classes in $location for tests",
                            preFiltered = true,
                            config = codebaseConfig,
                            trustedApi = true,
                            supportsDocumentation = false,
                            assembler = assembler,
                        )
                    },
                )
            assembler.initialize()

            return assembler
        }
    }
}
