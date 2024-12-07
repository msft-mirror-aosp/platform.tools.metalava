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

import com.android.tools.metalava.apilevels.ExtensionSdkJarReader.addVersionedExtensionApis
import java.io.File

/**
 * Main class for command line command to convert the existing API XML/TXT files into diff-based
 * simple text files.
 */
class ApiGenerator {
    /**
     * Generates an XML API version history file based on the API surfaces of the versions provided.
     *
     * @param config Configuration provided from command line options.
     */
    fun generateXml(config: GenerateXmlConfig): Boolean {
        val versionToJar = config.versionToJar
        val notFinalizedSdkVersion = config.notFinalizedSdkVersion

        val sdkExtensionsArguments = config.sdkExtensionsArguments

        // Create a list of VersionedApis that need to be incorporated into the Api history.
        val versionedApis = buildList {
            for ((sdkVersion, jar) in versionToJar) {
                val updater = ApiHistoryUpdater.forApiVersion(sdkVersion)
                add(VersionedJarApi(jar, updater))
            }

            // Add the optional VersionedApi for the current source Codebase.
            addAll(config.versionedApis)

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
        return createApiLevelsFile(config.outputFile, config.printer, api)
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
