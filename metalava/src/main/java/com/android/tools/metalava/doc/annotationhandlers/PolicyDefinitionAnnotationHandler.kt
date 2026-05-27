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

import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.annotation.binding.bindTo
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Reporter
import java.util.function.Predicate

/** Handles {@link android.processor.devicepolicy.PolicyDefinition} annotation. */
class PolicyDefinitionAnnotationHandler(
    codebase: Codebase,
    reporter: Reporter,
    filterReference: Predicate<SelectableItem>
) : BaseDevicePolicyAnnotationHandler(codebase, reporter, filterReference) {

    /** Processes a policy annotation and returns a documentation string. */
    override fun processPolicyAnnotation(annotation: AnnotationItem, item: Item): String {
        val proxy = annotation.bindTo<PolicyDefinitionProxy>(item)
        return proxy?.generateDocs() ?: ""
    }
}

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

/**
 * Proxy class bound to an instance of the `android.processor.devicepolicy.PolicyDefinition`
 * annotation class.
 *
 * @see bindTo
 */
class PolicyDefinitionProxy(
    /** The item on which this was annotated. */
    val item: Item,
    private val allowedScopes: List<Int>,
    private val affectedResource: Int,
    private val requiredPermission: String?,
    private val requiredCrossUserPermission: String?,
    private val allowedDpcTypes: AllowedDpcTypesProxy,
    private val allowedRoles: AllowedRolesProxy,
) {
    private val codebase = item.codebase
    private val reporter = codebase.reporter

    init {
        // Validate the properties.
        if (allowedScopes.isEmpty()) {
            reporter.report(
                Issues.INVALID_DEVICE_POLICY_ANNOTATION,
                item,
                "'allowedScopes' is empty on $item: Must provide at least one scope"
            )
        }
    }

    /** Resolves a permission code link to a format suitable for documentation. */
    private fun resolvePermissionCodeLink(value: String, item: Item): String {
        val permissionClass = codebase.findClass("android.Manifest.permission")
        if (permissionClass == null) {
            reporter.report(
                Issues.INVALID_DEVICE_POLICY_ANNOTATION,
                item,
                "Cannot find permission field for $value required by $item (may be hidden or removed)"
            )
            return value
        }
        val fieldName = value.substringAfterLast(".").uppercase()
        val field = permissionClass.findField(fieldName)
        if (field is FieldItem) {
            return "{@link ${field.containingClass().qualifiedName()}#${field.name()} $value}"
        }
        reporter.report(
            Issues.INVALID_DEVICE_POLICY_ANNOTATION,
            item,
            "Cannot find permission field for $value required by $item (may be hidden or removed)"
        )
        return value
    }

    /**
     * Which DPCs have which cross-user permission granted. Scraped from
     * frameworks/base/services/devicepolicy/java/com/android/server/devicepolicy/PermissionChecker.java
     * Keep it in sync with:
     * cts/tools/cts-policy-tests-generator/src/com/android/cts/policytestsgenerator/AppliedByGenerator.kt
     */
    private fun getDpcTypesWithCrossUserPermission(crossUserPermission: String) =
        when (crossUserPermission) {
            "" -> AllowedDpcType.entries.toList()
            "android.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS" ->
                listOf(
                    AllowedDpcType.DEVICE_OWNER,
                    AllowedDpcType.FINANCED_DEVICE_OWNER,
                    AllowedDpcType.MANAGED_PROFILE_OWNER_OF_ORGANIZATION_OWNED_DEVICE,
                )
            "android.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS_FULL" ->
                listOf(
                    AllowedDpcType.DEVICE_OWNER,
                    AllowedDpcType.FINANCED_DEVICE_OWNER,
                )
            "android.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS_SECURITY_CRITICAL" ->
                listOf(
                    AllowedDpcType.DEVICE_OWNER,
                    AllowedDpcType.FINANCED_DEVICE_OWNER,
                    AllowedDpcType.MANAGED_PROFILE_OWNER_OF_ORGANIZATION_OWNED_DEVICE,
                    AllowedDpcType.PROFILE_OWNER_ON_USER_0,
                    AllowedDpcType.MANAGED_PROFILE_OWNER_OF_PERSONAL_OWNED_DEVICE,
                    AllowedDpcType.UNAFFILIATED_FULL_USER_PROFILE_OWNER,
                    AllowedDpcType.AFFILIATED_FULL_USER_PROFILE_OWNER,
                )
            else -> {
                reporter.report(
                    Issues.INVALID_DEVICE_POLICY_ANNOTATION,
                    item,
                    "Unknown cross-user permission: $crossUserPermission"
                )
                emptyList()
            }
        }

    private fun getAllowedDPCTypesForScope(
        crossUserPermission: String,
        scope: Int
    ): List<AllowedDpcType> {
        val allowedDpcs = AllowedDpcType.entries.filter { it.isAllowed(allowedDpcTypes) }
        return if (scope == PolicyScope.USER.id) {
            allowedDpcs
        } else if (scope == PolicyScope.DEVICE.id || scope == PolicyScope.PARENT_USER.id) {
            val crossUserDpcs = getDpcTypesWithCrossUserPermission(crossUserPermission)
            allowedDpcs.filter { it in crossUserDpcs }
        } else {
            reporter.report(Issues.INVALID_DEVICE_POLICY_ANNOTATION, item, "Invalid scope: $scope")
            emptyList()
        }
    }

    /** Generates documentation for the base policy definition. */
    fun generateDocs() = buildString {
        allowedScopes
            .takeIf { it.isNotEmpty() }
            ?.let { scopes ->
                append("   <li>Allowed Scopes:\n    <ul>\n")
                scopes.joinTo(this, separator = "") { scope ->
                    val dpcTypes =
                        getAllowedDPCTypesForScope(requiredCrossUserPermission ?: "", scope)
                    buildString {
                        if (dpcTypes.isEmpty()) {
                            append(
                                "       <li>${getScopeName(scope)}. Not settable by any DPC type.</li>\n"
                            )
                        } else {
                            append("       <li>${getScopeName(scope)}. Settable by:\n")
                            append("         <ul>\n")
                            dpcTypes.forEach { append("           <li>${it.description}</li>\n") }
                            append("         </ul>\n")
                            append("       </li>\n")
                        }
                    }
                }
                append("     </ul>\n   </li>\n")
            }

        affectedResource.let { append("   <li>Affected Resource: ${getResourceName(it)}</li>\n") }
        requiredPermission?.let { permission ->
            append(
                "   <li>Required Permission: ${resolvePermissionCodeLink(permission, item)}</li>\n"
            )
        }
        requiredCrossUserPermission?.let { permission ->
            append(
                "   <li>Required Cross User Permission: ${
                    resolvePermissionCodeLink(
                        permission,
                        item
                    )
                }</li>\n"
            )
        }

        append(allowedDpcTypes.generateDocs())
        append(allowedRoles.generateDocs())
    }

    companion object {
        /** Converts scope ID from [allowedScopes] to a human-readable name. */
        private fun getScopeName(scope: Int) =
            PolicyScope.fromId(scope)?.scopeName ?: scope.toString()

        /** Converts resource type from [affectedResource] to a human-readable name. */
        private fun getResourceName(resource: Int) =
            PolicyResource.fromId(resource)?.resourceName ?: resource.toString()
    }
}

enum class PolicyScope(val scopeName: String, val id: Int) {
    USER("User", 1),
    DEVICE("Device", 2),
    PARENT_USER("Parent User", 3);

    companion object {
        fun fromId(id: Int): PolicyScope? = entries.firstOrNull { it.id == id }
    }
}

enum class PolicyResource(val resourceName: String, val id: Int) {
    DEVICE_WIDE("Device Wide", 1),
    PER_USER("Per User", 2);

    companion object {
        fun fromId(id: Int): PolicyResource? = entries.firstOrNull { it.id == id }
    }
}
