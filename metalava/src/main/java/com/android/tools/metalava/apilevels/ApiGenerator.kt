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
        val lastApiVersion = ApiVersion.fromLevel(apiLevels.size - 1)
        val firstApiLevel = config.firstApiLevel
        val currentSdkVersion = config.currentSdkVersion
        val notFinalizedSdkVersion = currentSdkVersion + 1
        val versionedApis =
            (firstApiLevel until apiLevels.size).map { apiLevel ->
                val jar = apiLevels[apiLevel]
                val sdkVersion = ApiVersion.fromLevel(apiLevel)
                val updater = ApiHistoryUpdater.forApiVersion(sdkVersion)
                VersionedJarApi(jar, updater)
            }
        val api = createApiFromVersionedApis(versionedApis)
        val isDeveloperPreviewBuild = config.isDeveloperPreviewBuild

        // Compute the version to use for the current codebase.
        val codebaseSdkVersion =
            when {
                // The current codebase is a developer preview so use the next, in the process of
                // being finalized version.
                isDeveloperPreviewBuild -> notFinalizedSdkVersion

                // There is no prebuilt, finalized jar matching the current API level so use the
                // current codebase for the current API version.
                lastApiVersion < currentSdkVersion -> currentSdkVersion

                // Else do not include the current codebase.
                else -> null
            }

        // Get a list of all versions, including the codebase version, if necessary.
        val allVersions = buildList {
            (firstApiLevel until apiLevels.size).mapTo(this) { ApiVersion.fromLevel(it) }
            if (codebaseSdkVersion != null) add(codebaseSdkVersion)
        }

        if (codebaseSdkVersion != null) {
            addApisFromCodebase(api, codebaseSdkVersion, codebaseFragment, true)
        }
        val sdkExtensionsArguments = config.sdkExtensionsArguments
        if (sdkExtensionsArguments != null) {
            val versionedExtensionApis =
                createVersionedExtensionApis(
                    notFinalizedSdkVersion,
                    sdkExtensionsArguments.sdkExtJarRoot,
                    sdkExtensionsArguments.sdkExtensionInfo,
                )

            // Apply the list of VersionedApis to Api.
            for (versionedApi in versionedExtensionApis) {
                versionedApi.updateApi(api)
            }

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
     * Find the extension jars and versions for all modules, and create a list of [VersionedApi]s
     * from them.
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
    private fun createVersionedExtensionApis(
        versionNotInAndroidSdk: ApiVersion,
        sdkJarRoot: File,
        sdkExtensionInfo: SdkExtensionInfo,
    ): List<VersionedApi> {
        val map = findExtensionSdkJarFiles(sdkJarRoot)
        require(map.isNotEmpty()) { "no extension sdk jar files found in $sdkJarRoot" }

        // Create a list of VersionedApis.
        val versionedExtensionApis = mutableListOf<VersionedApi>()

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
                versionedExtensionApis.add(VersionedJarApi(path, updater))
            }
        }

        return versionedExtensionApis.toList()
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
