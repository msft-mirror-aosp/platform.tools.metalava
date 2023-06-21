/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.tools.metalava.manifest

import com.android.SdkConstants
import com.android.tools.metalava.Issues
import com.android.tools.metalava.model.MinSdkVersion
import com.android.tools.metalava.model.SetMinSdkVersion
import com.android.tools.metalava.model.UnsetMinSdkVersion
import com.android.tools.metalava.model.parseDocument
import com.android.tools.metalava.reporter
import com.android.utils.XmlUtils
import java.io.File

/**
 * An empty manifest. This is safe to share as while it is not strictly immutable it only mutates
 * the object when lazily initializing [Manifest.minSdkVersion].
 */
val emptyManifest: Manifest by lazy { Manifest(null) }

/** Provides access to information from an `AndroidManifest.xml` file. */
class Manifest(private val manifest: File?) {
    private var permissions: Map<String, String>? = null
    private var minSdkVersion: MinSdkVersion? = null

    /** Check whether the manifest is empty or not. */
    fun isEmpty(): Boolean {
        return manifest == null
    }

    /**
     * Returns the permission level of the named permission, if specified in the manifest. This
     * method should only be called if the codebase has been configured with a manifest
     */
    fun getPermissionLevel(name: String): String? {
        if (permissions == null) {
            assert(manifest != null) {
                "This method should only be called when a manifest has been configured on the codebase"
            }
            try {
                val map = HashMap<String, String>(600)
                val doc = parseDocument(manifest?.readText(Charsets.UTF_8) ?: "", true)
                var current =
                    XmlUtils.getFirstSubTagByName(doc.documentElement, SdkConstants.TAG_PERMISSION)
                while (current != null) {
                    val permissionName =
                        current.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                    val protectionLevel =
                        current.getAttributeNS(SdkConstants.ANDROID_URI, "protectionLevel")
                    map[permissionName] = protectionLevel
                    current = XmlUtils.getNextTagByName(current, SdkConstants.TAG_PERMISSION)
                }
                permissions = map
            } catch (error: Throwable) {
                reporter.report(
                    Issues.PARSE_ERROR,
                    manifest,
                    "Failed to parse $manifest: ${error.message}"
                )
                permissions = emptyMap()
            }
        }

        return permissions!![name]
    }

    fun getMinSdkVersion(): MinSdkVersion {
        if (minSdkVersion == null) {
            if (manifest == null) {
                minSdkVersion = UnsetMinSdkVersion
                return minSdkVersion!!
            }
            minSdkVersion =
                try {
                    val doc = parseDocument(manifest.readText(Charsets.UTF_8), true)
                    val usesSdk =
                        XmlUtils.getFirstSubTagByName(
                            doc.documentElement,
                            SdkConstants.TAG_USES_SDK
                        )
                    if (usesSdk == null) {
                        UnsetMinSdkVersion
                    } else {
                        val value =
                            usesSdk.getAttributeNS(
                                SdkConstants.ANDROID_URI,
                                SdkConstants.ATTR_MIN_SDK_VERSION
                            )
                        if (value.isEmpty()) UnsetMinSdkVersion else SetMinSdkVersion(value.toInt())
                    }
                } catch (error: Throwable) {
                    reporter.report(
                        Issues.PARSE_ERROR,
                        manifest,
                        "Failed to parse $manifest: ${error.message}"
                    )
                    UnsetMinSdkVersion
                }
        }
        return minSdkVersion!!
    }

    override fun toString(): String {
        return manifest.toString()
    }
}
