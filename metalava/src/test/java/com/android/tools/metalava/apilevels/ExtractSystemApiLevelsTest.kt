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

package com.android.tools.metalava.apilevels

import com.android.tools.metalava.ARG_ANDROID_JAR_PATTERN
import com.android.tools.metalava.ARG_CURRENT_VERSION
import com.android.tools.metalava.ARG_FIRST_VERSION
import com.android.tools.metalava.ARG_GENERATE_API_LEVELS
import com.android.tools.metalava.ARG_SDK_INFO_FILE
import com.android.tools.metalava.ARG_SDK_JAR_ROOT
import com.android.tools.metalava.doc.getApiLookup
import com.android.tools.metalava.doc.minApiLevel
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtractSystemApiLevelsTest : ApiGeneratorIntegrationTestBase() {
    @Test
    fun `Extract System API`() {
        // These are the wrong jar paths but this test doesn't actually care what the
        // content of the jar files, just checking the logic of starting the database
        // at some higher number than 1
        val androidJarPattern = "${platformJars.path}/%/public/android.jar"

        val filter = File.createTempFile("filter", "txt")
        filter.deleteOnExit()
        filter.writeText(
            """
                <sdk-extensions-info>
                <!-- SDK definitions -->
                <sdk shortname="R" name="R Extensions" id="30" reference="android/os/Build${'$'}VERSION_CODES${'$'}R" />
                <sdk shortname="S" name="S Extensions" id="31" reference="android/os/Build${'$'}VERSION_CODES${'$'}S" />
                <sdk shortname="T" name="T Extensions" id="33" reference="android/os/Build${'$'}VERSION_CODES${'$'}T" />

                <!-- Rules -->
                <symbol jar="art.module.public.api" pattern="*" sdks="R" />
                <symbol jar="conscrypt.module.intra.core.api " pattern="" sdks="R" />
                <symbol jar="conscrypt.module.platform.api" pattern="*" sdks="R" />
                <symbol jar="conscrypt.module.public.api" pattern="*" sdks="R" />
                <symbol jar="framework-mediaprovider" pattern="*" sdks="R" />
                <symbol jar="framework-mediaprovider" pattern="android.provider.MediaStore#canManageMedia" sdks="T" />
                <symbol jar="framework-permission-s" pattern="*" sdks="R" />
                <symbol jar="framework-permission" pattern="*" sdks="R" />
                <symbol jar="framework-sdkextensions" pattern="*" sdks="R" />
                <symbol jar="framework-scheduling" pattern="*" sdks="R" />
                <symbol jar="framework-statsd" pattern="*" sdks="R" />
                <symbol jar="framework-tethering" pattern="*" sdks="R" />
                <symbol jar="legacy.art.module.platform.api" pattern="*" sdks="R" />
                <symbol jar="service-media-s" pattern="*" sdks="R" />
                <symbol jar="service-permission" pattern="*" sdks="R" />

                <!-- use framework-permissions-s to test the order of multiple SDKs is respected -->
                <symbol jar="android.net.ipsec.ike" pattern="android.net.eap.EapAkaInfo" sdks="R,S,T" />
                <symbol jar="android.net.ipsec.ike" pattern="android.net.eap.EapInfo" sdks="T,S,R" />
                <symbol jar="android.net.ipsec.ike" pattern="*" sdks="R" />

                <!-- framework-connectivity: only android.net.CaptivePortal should have the 'sdks' attribute -->
                <symbol jar="framework-connectivity" pattern="android.net.CaptivePortal" sdks="R" />

                <!-- framework-media explicitly omitted: nothing in this module should have the 'sdks' attribute -->
                </sdk-extensions-info>
            """
                .trimIndent()
        )

        val output = File.createTempFile("api-info", "xml")
        output.deleteOnExit()
        val outputPath = output.path

        check(
            extraArguments =
                arrayOf(
                    ARG_GENERATE_API_LEVELS,
                    outputPath,
                    ARG_ANDROID_JAR_PATTERN,
                    androidJarPattern,
                    ARG_SDK_JAR_ROOT,
                    "$extensionSdkJars",
                    ARG_SDK_INFO_FILE,
                    filter.path,
                    ARG_FIRST_VERSION,
                    "21",
                    ARG_CURRENT_VERSION,
                    "33"
                )
        )

        assertTrue(output.isFile)
        val xml = output.readText(Charsets.UTF_8)
        assertTrue(xml.contains("<api version=\"3\" min=\"21\">"))
        assertTrue(
            xml.contains(
                "<sdk id=\"30\" shortname=\"R\" name=\"R Extensions\" reference=\"android/os/Build\$VERSION_CODES\$R\"/>"
            )
        )
        assertTrue(
            xml.contains(
                "<sdk id=\"31\" shortname=\"S\" name=\"S Extensions\" reference=\"android/os/Build\$VERSION_CODES\$S\"/>"
            )
        )
        assertTrue(
            xml.contains(
                "<sdk id=\"33\" shortname=\"T\" name=\"T Extensions\" reference=\"android/os/Build\$VERSION_CODES\$T\"/>"
            )
        )
        assertTrue(xml.contains("<class name=\"android/Manifest\" since=\"21\">"))
        assertTrue(xml.contains("<field name=\"showWhenLocked\" since=\"27\"/>"))

        // top level class marked as since=21 and R=1, implemented in the framework-mediaprovider
        // mainline module
        assertTrue(
            xml.contains(
                "<class name=\"android/provider/MediaStore\" module=\"framework-mediaprovider\" since=\"21\" sdks=\"30:1,0:21\">"
            )
        )

        // method with identical sdks attribute as containing class: sdks attribute should be
        // omitted
        assertTrue(xml.contains("<method name=\"getMediaScannerUri()Landroid/net/Uri;\"/>"))

        // method with different sdks attribute than containing class
        assertTrue(
            xml.contains(
                "<method name=\"canManageMedia(Landroid/content/Context;)Z\" since=\"31\" sdks=\"33:1,0:31\"/>"
            )
        )

        val apiLookup = getApiLookup(output)
        @Suppress("DEPRECATION") apiLookup.getClassVersion("android.v")
        // This field was added in API level 5, but when we're starting the count higher
        // (as in the system API), the first introduced API level is the one we use
        @Suppress("DEPRECATION")
        (assertEquals(
            21,
            apiLookup.getFieldVersion("android.Manifest\$permission", "AUTHENTICATE_ACCOUNTS")
        ))

        @Suppress("DEPRECATION")
        val methodVersion =
            apiLookup.getMethodVersion("android/icu/util/CopticCalendar", "computeTime", "()")
        assertEquals(24, methodVersion)

        // The filter says 'framework-permission-s             *    R' so RoleManager should exist
        // and should have a module/sdks attributes
        assertTrue(apiLookup.containsClass("android/app/role/RoleManager"))
        assertTrue(
            xml.contains(
                "<method name=\"canManageMedia(Landroid/content/Context;)Z\" since=\"31\" sdks=\"33:1,0:31\"/>"
            )
        )

        // The filter doesn't mention framework-media, so no class in that module should have a
        // module/sdks attributes
        assertTrue(xml.contains("<class name=\"android/media/MediaFeature\" since=\"31\">"))

        // The filter only defines a single API in framework-connectivity: verify that only that API
        // has the module/sdks attributes
        assertTrue(
            xml.contains(
                "<class name=\"android/net/CaptivePortal\" module=\"framework-connectivity\" since=\"23\" sdks=\"30:1,0:23\">"
            )
        )
        assertTrue(
            xml.contains("<class name=\"android/net/ConnectivityDiagnosticsManager\" since=\"30\">")
        )

        // The order of the SDKs should be respected
        // android.net.eap.EapAkaInfo    R S T -> 0,30,31,33
        assertTrue(
            xml.contains(
                "<class name=\"android/net/eap/EapAkaInfo\" module=\"android.net.ipsec.ike\" since=\"33\" sdks=\"30:3,31:3,33:3,0:33\">"
            )
        )
        // android.net.eap.EapInfo       T S R -> 0,33,31,30
        assertTrue(
            xml.contains(
                "<class name=\"android/net/eap/EapInfo\" module=\"android.net.ipsec.ike\" since=\"33\" sdks=\"33:3,31:3,30:3,0:33\">"
            )
        )

        // Verify historical backfill
        assertEquals(30, apiLookup.getClassVersions("android/os/ext/SdkExtensions").minApiLevel())
        assertEquals(
            30,
            apiLookup
                .getMethodVersions("android/os/ext/SdkExtensions", "getExtensionVersion", "(I)I")
                .minApiLevel()
        )
        assertEquals(
            31,
            apiLookup
                .getMethodVersions(
                    "android/os/ext/SdkExtensions",
                    "getAllExtensionVersions",
                    "()Ljava/util/Map;"
                )
                .minApiLevel()
        )

        // Verify there's no extension versions listed for SdkExtensions
        val sdkExtClassLine =
            xml.lines().first { it.contains("<class name=\"android/os/ext/SdkExtensions\"") }
        assertFalse(sdkExtClassLine.contains("sdks="))
    }
}