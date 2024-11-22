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

import com.android.tools.metalava.SdkIdentifier

/** The set of available SDK extensions. */
class AvailableSdkExtensions(internal val sdkIdentifiers: Set<SdkIdentifier>) {
    /**
     * Retrieve the SDK extension appropriate for the [shortExtensionName], throwing an exception if
     * it could not be found.
     */
    fun retrieveSdkExtension(shortExtensionName: String): SdkIdentifier {
        return sdkIdentifiers.find { it.shortname == shortExtensionName }
            ?: throw IllegalStateException("unknown extension SDK \"$shortExtensionName\"")
    }
}
