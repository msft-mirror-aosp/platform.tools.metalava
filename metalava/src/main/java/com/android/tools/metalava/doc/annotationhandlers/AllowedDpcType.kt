/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.tools.metalava.doc.annotationhandlers

enum class AllowedDpcType(
    val description: String,
    val isAllowed: AllowedDpcTypesProxy.() -> Boolean,
) {
    DEVICE_OWNER(
        description = "Device Owner",
        isAllowed = { deviceOwner == AllowedDpcTypesProxy.DPC_ANNOTATION_ALLOWED },
    ),
    MANAGED_PROFILE_OWNER_OF_ORGANIZATION_OWNED_DEVICE(
        description = "Managed Profile Owner (Of Organization Owned Device)",
        isAllowed = {
            managedProfileOwnerOfOrganizationOwnedDevice ==
                AllowedDpcTypesProxy.DPC_ANNOTATION_ALLOWED
        },
    ),
    MANAGED_PROFILE_OWNER_OF_PERSONAL_OWNED_DEVICE(
        description = "Managed Profile Owner (Of Personally Owned Device)",
        isAllowed = {
            managedProfileOwnerOfPersonalOwnedDevice == AllowedDpcTypesProxy.DPC_ANNOTATION_ALLOWED
        },
    ),
    UNAFFILIATED_FULL_USER_PROFILE_OWNER(
        description = "Unaffiliated Full User Profile Owner",
        isAllowed = { fullUserProfileOwner == AllowedDpcTypesProxy.DPC_ANNOTATION_ALLOWED },
    ),
    FINANCED_DEVICE_OWNER(
        description = "Financed Device Owner",
        isAllowed = { financedDeviceOwner == AllowedDpcTypesProxy.DPC_ANNOTATION_ALLOWED },
    ),
    PROFILE_OWNER_ON_USER_0(
        description = "Profile Owner on User 0",
        isAllowed = { profileOwnerOnUser0 == AllowedDpcTypesProxy.DPC_ANNOTATION_ALLOWED },
    ),
    AFFILIATED_FULL_USER_PROFILE_OWNER(
        description = "Affiliated Full User Profile Owner",
        isAllowed = {
            fullUserProfileOwner == AllowedDpcTypesProxy.DPC_ANNOTATION_ALLOWED ||
                fullUserProfileOwner == AllowedDpcTypesProxy.DPC_ANNOTATION_ALLOWED_WHEN_AFFILIATED
        },
    ),
}

class AllowedDpcTypesProxy(
    val deviceOwner: Int,
    val managedProfileOwnerOfOrganizationOwnedDevice: Int,
    val managedProfileOwnerOfPersonalOwnedDevice: Int,
    val fullUserProfileOwner: Int,
    val financedDeviceOwner: Int,
    val profileOwnerOnUser0: Int,
) {
    fun generateDocs(): String {
        val dpcTypes =
            AllowedDpcType.entries.mapNotNull {
                if (it.isAllowed(this)) {
                    it.description
                } else {
                    null
                }
            }

        if (dpcTypes.isEmpty()) return ""

        return buildString {
            append("   <li>Allowed DPC Types: \n    <ul>\n")
            dpcTypes.joinTo(this, separator = "") { "       <li>$it</li>\n" }
            append("     </ul>\n   </li>\n")
        }
    }

    companion object {
        const val DPC_ANNOTATION_ALLOWED = 1
        const val DPC_ANNOTATION_DISALLOWED = 2
        const val DPC_ANNOTATION_ALLOWED_WHEN_AFFILIATED = 3
    }
}

class AllowedRolesProxy(
    val deviceController: Int,
) {
    fun generateDocs(): String {
        if (deviceController == ROLE_ANNOTATION_ALLOWED) {
            // TODO(b/477491703): add code link to "android.app.role.DEVICE_CONTROLLER"
            return "   <li>This policy can be set by holders of the device controller role</li>\n"
        }
        return ""
    }

    companion object {
        const val ROLE_ANNOTATION_ALLOWED = 1
        const val ROLE_ANNOTATION_DISALLOWED = 2
    }
}
