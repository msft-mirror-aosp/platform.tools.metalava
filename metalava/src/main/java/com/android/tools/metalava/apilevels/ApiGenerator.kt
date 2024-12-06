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
package com.android.tools.metalava.apilevels

import com.android.tools.metalava.apilevels.ExtensionSdkJarReader.Companion.findExtensionSdkJarFiles
import com.android.tools.metalava.model.CodebaseFragment
import java.io.File

/**
 * Main class for command line command to convert the existing API XML/TXT files into diff-based
 * simple text files.
 */
class ApiGenerator {
    /**
     * Generates an XML API version history file based on the API surfaces of the versions provided.
     *
     * @param codebaseFragment A [CodebaseFragment] representing the current API surface.
     * @param config Configuration provided from command line options.
     */
    fun generateXml(
        codebaseFragment: CodebaseFragment,
        config: GenerateXmlConfig,
    ): Boolean {
        val apiLevels = config.apiLevels
        val firstApiLevel = config.firstApiLevel
        // Get the last API version that was added, or null if there are no valid historical
        // versions.
        val lastApiVersion =
            if (apiLevels.size <= firstApiLevel) null else ApiVersion.fromLevel(apiLevels.size - 1)
        val currentSdkVersion = config.currentSdkVersion
        val notFinalizedSdkVersion = currentSdkVersion + 1

        // Compute the version to use for the current codebase.
        val codebaseSdkVersion =
            when {
                // The current codebase is a developer preview so use the next, in the process of
                // being finalized version.
                config.isDeveloperPreviewBuild -> notFinalizedSdkVersion

                // If no historical versions were provided or the last historical version is less
                // than the current version then use the current version as the version of the
                // codebase.
                lastApiVersion == null || lastApiVersion < currentSdkVersion -> currentSdkVersion

                // Else do not include the current codebase.
                else -> null
            }

        // Get a list of all versions, including the codebase version, if necessary.
        val allVersions = buildList {
            (firstApiLevel until apiLevels.size).mapTo(this) { ApiVersion.fromLevel(it) }
            if (codebaseSdkVersion != null) add(codebaseSdkVersion)
        }

        val sdkExtensionsArguments = config.sdkExtensionsArguments

        // Create a list of VersionedApis that need to be incorporated into the Api history.
        val versionedApis = buildList {
            for (apiLevel in firstApiLevel until apiLevels.size) {
                val jar = apiLevels[apiLevel]
                val sdkVersion = ApiVersion.fromLevel(apiLevel)
                val updater = ApiHistoryUpdater.forApiVersion(sdkVersion)
                add(VersionedJarApi(jar, updater))
            }
            if (codebaseSdkVersion != null)
                add(
                    VersionedSourceApi(
                        codebaseFragment,
                        codebaseSdkVersion,
                        useInternalNames = true,
                    )
                )

            // Add any VersionedApis for SDK extensions. These must be added after all VersionedApis
            // for SDK versions as their behavior depends on whether an API was defined in an SDK
            // version.
            if (sdkExtensionsArguments != null) {
                addVersionedExtensionApis(
                    this,
                    notFinalizedSdkVersion,
                    sdkExtensionsArguments.sdkExtJarRoot,
                    sdkExtensionsArguments.sdkExtensionInfo,
                )
            }
        }

        val api = createApiFromVersionedApis(versionedApis)

        // If necessary, update the sdks properties.
        if (sdkExtensionsArguments != null) {
            updateSdksAttributes(
                api,
                notFinalizedSdkVersion,
                sdkExtensionsArguments.sdkExtensionInfo,
            )
        }
        api.backfillHistoricalFixes()
        api.clean()
        if (config.removeMissingClasses) {
            api.removeMissingClasses()
        } else {
            api.verifyNoMissingClasses()
        }
        val availableSdkExtensions =
            sdkExtensionsArguments?.sdkExtensionInfo?.availableSdkExtensions
        val printer = ApiXmlPrinter(availableSdkExtensions, allVersions)
        return createApiLevelsFile(config.outputFile, printer, api)
    }

    /**
     * Creates an [Api] from a list of [VersionedApi]s.
     *
     * @param versionedApis A list of [VersionedApi]s, one for each version of the API, in order
     *   from oldest to newest API version.
     */
    private fun createApiFromVersionedApis(versionedApis: List<VersionedApi>): Api {
        val api = Api()
        for (versionedApi in versionedApis) {
            versionedApi.updateApi(api)
        }
        return api
    }

    /**
     * Generates an API version history file based on the API surfaces of the versions provided.
     *
     * @param config Configuration provided from command line options.
     */
    fun generateFromVersionedApis(
        config: GenerateApiVersionsFromVersionedApisConfig,
    ) {
        val api = createApiFromVersionedApis(config.versionedApis)
        api.clean()
        createApiLevelsFile(config.outputFile, config.printer, api)
    }

    /**
     * Find the extension jars and versions for all modules, wrap in a [VersionedApi] and add them
     * to [list].
     *
     * Some APIs only exist in extension SDKs and not in the Android SDK, but for backwards
     * compatibility with tools that expect the Android SDK to be the only SDK, metalava needs to
     * assign such APIs some Android SDK API version. This uses [versionNotInAndroidSdk].
     *
     * @param versionNotInAndroidSdk fallback API level for APIs not in the Android SDK
     * @param sdkJarRoot path to directory containing extension SDK jars (usually
     *   $ANDROID_ROOT/prebuilts/sdk/extensions)
     * @param sdkExtensionInfo the [SdkExtensionInfo] read from sdk-extension-info.xml file.
     */
    private fun addVersionedExtensionApis(
        list: MutableList<VersionedApi>,
        versionNotInAndroidSdk: ApiVersion,
        sdkJarRoot: File,
        sdkExtensionInfo: SdkExtensionInfo,
    ) {
        val map = findExtensionSdkJarFiles(sdkJarRoot)
        require(map.isNotEmpty()) { "no extension sdk jar files found in $sdkJarRoot" }

        // Iterate over the mainline modules and their different versions.
        for ((mainlineModule, value) in map) {
            // Get the extensions information for the mainline module. If no information exists for
            // a particular module then the module is ignored.
            val moduleMap = sdkExtensionInfo.extensionsMapForJarOrEmpty(mainlineModule)
            if (moduleMap.isEmpty())
                continue // TODO(b/259115852): remove this (though it is an optimization too).
            for ((level, path) in value) {
                val extVersion = ExtVersion.fromLevel(level)
                val updater =
                    ApiHistoryUpdater.forExtVersion(
                        versionNotInAndroidSdk,
                        extVersion,
                        mainlineModule,
                    )
                list.add(VersionedJarApi(path, updater))
            }
        }
    }

    /**
     * Traverses [api] updating the [ApiElement.sdks] properties to list the appropriate extensions.
     *
     * Some APIs only exist in extension SDKs and not in the Android SDK, but for backwards
     * compatibility with tools that expect the Android SDK to be the only SDK, metalava needs to
     * assign such APIs some Android SDK API version. This uses [versionNotInAndroidSdk].
     *
     * @param api the api to modify
     * @param versionNotInAndroidSdk fallback API level for APIs not in the Android SDK
     * @param sdkExtensionInfo the [SdkExtensionInfo] read from sdk-extension-info.xml file.
     */
    private fun updateSdksAttributes(
        api: Api,
        versionNotInAndroidSdk: ApiVersion,
        sdkExtensionInfo: SdkExtensionInfo,
    ) {
        for (clazz in api.classes) {
            val module = clazz.mainlineModule ?: continue

            // Get the extensions information for the mainline module. If no information exists for
            // a particular module then the returned information is empty but can still be used to
            // calculate sdks attribute.
            val extensionsMap = sdkExtensionInfo.extensionsMapForJarOrEmpty(module)

            /** Update the sdks on each [ApiElement] in [elements]. */
            fun updateSdks(elements: Collection<ApiElement>) {
                for (element in elements) {
                    val sdks =
                        extensionsMap.calculateSdksAttr(
                            element.since,
                            versionNotInAndroidSdk,
                            extensionsMap.getExtensions(clazz, element),
                            element.sinceExtension
                        )
                    element.updateSdks(sdks)
                }
            }

            val sdks =
                extensionsMap.calculateSdksAttr(
                    clazz.since,
                    versionNotInAndroidSdk,
                    extensionsMap.getExtensions(clazz),
                    clazz.sinceExtension
                )
            clazz.updateSdks(sdks)

            updateSdks(clazz.superClasses)
            updateSdks(clazz.interfaces)
            updateSdks(clazz.fields)
            updateSdks(clazz.methods)
        }
    }

    /**
     * Creates a file containing the [api].
     *
     * @param outFile the output file
     * @param printer the [ApiPrinter] to use to write the file.
     * @param api the api to write
     */
    private fun createApiLevelsFile(
        outFile: File,
        printer: ApiPrinter,
        api: Api,
    ): Boolean {
        val parentFile = outFile.parentFile
        if (!parentFile.exists()) {
            val ok = parentFile.mkdirs()
            if (!ok) {
                System.err.println("Could not create directory $parentFile")
                return false
            }
        }
        try {
            outFile.printWriter().use { writer -> printer.print(api, writer) }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    data class SdkExtensionsArguments(
        val sdkExtJarRoot: File,
        private val sdkExtInfoFile: File,
    ) {
        /** [SdkExtensionInfo] loaded on demand from [sdkExtInfoFile]. */
        val sdkExtensionInfo by
            lazy(LazyThreadSafetyMode.NONE) { SdkExtensionInfo.fromXml(sdkExtInfoFile.readText()) }
    }
}
