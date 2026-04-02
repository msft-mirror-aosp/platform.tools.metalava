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
    val attributeName: String,
) {
    DEVICE_OWNER(
        description = "Device Owner",
        attributeName = "deviceOwner",
    ),
    MANAGED_PROFILE_OWNER_OF_ORGANIZATION_OWNED_DEVICE(
        description = "Managed Profile Owner (Of Organization Owned Device)",
        attributeName = "managedProfileOwnerOfOrganizationOwnedDevice",
    ),
    MANAGED_PROFILE_OWNER_OF_PERSONAL_OWNED_DEVICE(
        description = "Managed Profile Owner (Of Personally Owned Device)",
        attributeName = "managedProfileOwnerOfPersonalOwnedDevice",
    ),
    UNAFFILIATED_FULL_USER_PROFILE_OWNER(
        description = "Unaffiliated Full User Profile Owner",
        attributeName = "unaffiliatedFullUserProfileOwner",
    ),
    FINANCED_DEVICE_OWNER(
        description = "Financed Device Owner",
        attributeName = "financedDeviceOwner",
    ),
    PROFILE_OWNER_ON_USER_0(
        description = "Profile Owner on User 0",
        attributeName = "profileOwnerOnUser0",
    ),
    AFFILIATED_FULL_USER_PROFILE_OWNER(
        description = "Affiliated Full User Profile Owner",
        attributeName = "affiliatedFullUserProfileOwner",
    ),
}

/**
 * Proxy class bound to an instance of the `android.processor.devicepolicy.PolicyDefinition`
 * annotation class.
 *
 * @see bindTo
 */
class PolicyDefinitionProxy(
    /** The item on which this was annotated. */
    private val item: Item,
    private val allowedScopes: List<Int>,
    private val affectedResource: Int,
    private val requiredPermission: String?,
    private val requiredCrossUserPermission: String?,
    /**
     * The `AllowedDpcTypes` [AnnotationItem] that will be converted into a [List] of [String]
     * names.
     */
    allowedDpcTypes: AnnotationItem,
) {
    private val codebase = item.codebase
    private val reporter = codebase.reporter

    /** Convert the allowedDpcTypes [AnnotationItem] to a [List] of [String] names. */
    private val allowedDpcTypes = allowedDpcTypes.extractAllowedDpcTypes()

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

    /** Generates documentation for the base policy definition. */
    fun generateDocs() = buildString {
        allowedScopes
            .takeIf { it.isNotEmpty() }
            ?.let { scopes ->
                append("   <li>Allowed Scopes:\n    <ul>\n")
                scopes.joinTo(this, separator = "") { "       <li>${getScopeName(it)}</li>\n" }
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

        allowedDpcTypes
            .takeIf { it.isNotEmpty() }
            ?.let { dpcTypes ->
                append("   <li>Allowed DPC Types: \n    <ul>\n")
                dpcTypes.joinTo(this, separator = "") { "       <li>$it</li>\n" }
                append("     </ul>\n   </li>\n")
            }
    }

    companion object {
        private const val DPC_ANNOTATION_ALLOWED = 1

        /** Extracts allowed DPC values from the annotation. */
        private fun AnnotationItem.extractAllowedDpcTypes() =
            AllowedDpcType.entries.mapNotNull {
                if (getIntAttribute(it.attributeName) == DPC_ANNOTATION_ALLOWED) {
                    it.description
                } else {
                    null
                }
            }

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
