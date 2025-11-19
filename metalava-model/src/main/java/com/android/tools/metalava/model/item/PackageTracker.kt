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

import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.ItemDocumentation
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.PackageList
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.utils.extractPossiblyEmptyQualifierName
import com.android.tools.metalava.reporter.FileLocation
import java.util.HashMap

private const val PACKAGE_ESTIMATE = 500

data class PackageInfo(
    /**
     * Location of the `package-info.java`, `package-info.class` or `package.html` file from which
     * this information was obtained.
     *
     * Is [FileLocation.UNKNOWN] for packages which do not have one of the above package files.
     */
    val fileLocation: FileLocation = FileLocation.UNKNOWN,

    /** The list of annotations, if any, applied to the package. */
    val annotations: List<AnnotationItem>,

    /**
     * Factory for creating an [ItemDocumentation] instance containing the package level document.
     *
     * This factory will be invoked when creating the associated [PackageItem].
     *
     * If specified this is used for [PackageItem.documentation].
     */
    val commentFactory: ItemDocumentationFactory? = null,

    /**
     * The `overview.html` file.
     *
     * If specified this is used for [PackageItem.overviewDocumentation].
     */
    val overview: ResourceFile? = null,
) {
    companion object {
        val EMPTY =
            PackageInfo(
                annotations = emptyList(),
            )
    }
}

class PackageTracker(
    private val assembler: CodebaseAssembler,
) {
    /** Map from package name to [PackageItem] of all packages in this. */
    private val packagesByName = HashMap<String, PackageItem>(PACKAGE_ESTIMATE)

    val size
        get() = packagesByName.size

    fun getPackages(): PackageList {
        val list = packagesByName.values.toMutableList()
        list.sortWith(PackageItem.comparator)
        return PackageList(list)
    }

    fun findPackage(pkgName: String): PackageItem? {
        return packagesByName[pkgName]
    }

    /**
     * Searches for the package with [packageName] in this tracker and if not found creates the
     * corresponding [PackageItem], supply additional information from [packageDocs] and adds the
     * newly created [PackageItem] to this tracker.
     *
     * If the [PackageItem] exists and [PackageDocs] contains [PackageDoc.modifiers] for the package
     * then make sure that the existing [PackageItem] has the same [PackageItem.modifiers], if not
     * throw an exception.
     *
     * @param packageName the name of the package to create.
     * @param packageDocs provides additional information needed for creating a package.
     * @return the [PackageItem] that was found or created.
     */
    fun findOrCreatePackage(
        packageName: String,
        packageDocs: PackageDocs = PackageDocs.EMPTY,
    ): PackageItem {
        // Check to see if the package already exists, if it does then return it.
        findPackage(packageName)?.let { existing ->
            return existing
        }

        // Get the `PackageDoc`, if any, to use for creating this package.
        val packageDoc = packageDocs[packageName]

        val packageInfo =
            PackageInfo(
                fileLocation = packageDoc.fileLocation,
                annotations = assembler.createPackageAnnotations(packageName),
                commentFactory = packageDoc.commentFactory,
                overview = packageDoc.overview,
            )

        return createPackage(
            packageName,
            packageDocs,
            packageInfo,
        )
    }

    /** Create [PackageItem] for [packageName] using additional information from [packageDocs]. */
    fun createPackage(
        packageName: String,
        packageDocs: PackageDocs,
        packageInfo: PackageInfo,
    ): PackageItem {
        // Unless this is the root package, it has a containing package so get that before creating
        // this package, so it can be passed into the `packageItemFactory`.
        val containingPackageName = getContainingPackageName(packageName)
        val containingPackage =
            if (containingPackageName == null) null
            else findOrCreatePackage(containingPackageName, packageDocs)

        val packageItem = assembler.createPackageItem(packageName, packageInfo, containingPackage)

        // The packageItemFactory may provide its own modifiers so check to make sure that they are
        // public.
        if (packageItem.modifiers.getVisibilityLevel() != VisibilityLevel.PUBLIC)
            error("Package $packageItem is not public")

        addPackage(packageItem)

        return packageItem
    }

    /**
     * Gets the name of [packageName]'s containing package or `null` if [packageName] is `""`, i.e.
     * the root package.
     */
    private fun getContainingPackageName(packageName: String): String? =
        if (packageName == "") null else packageName.extractPossiblyEmptyQualifierName()

    /** Add the package to this. */
    private fun addPackage(packageItem: PackageItem) {
        packagesByName[packageItem.qualifiedName()] = packageItem
    }

    /**
     * Create and track [PackageItem]s for every entry in [packageDocs] and make sure there is a
     * root package.
     */
    fun createInitialPackages(packageDocs: PackageDocs) {
        // Create packages for all the documentation packages.
        for (packageName in packageDocs.packageNames) {
            findOrCreatePackage(packageName, packageDocs)
        }

        // Make sure that there is a root package.
        findOrCreatePackage("", packageDocs)
    }
}
