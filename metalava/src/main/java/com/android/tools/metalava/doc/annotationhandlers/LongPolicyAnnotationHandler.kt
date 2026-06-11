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
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.annotation.binding.bindTo
import com.android.tools.metalava.reporter.Issues

/** Handles @android.processor.devicepolicy.LongPolicyDefinition annotation. */
class LongPolicyAnnotationHandler(
    context: DevicePolicyContext,
) : BaseDevicePolicyAnnotationHandler(context) {
    /** Processes the [LongPolicyDefinitionProxy] and returns the documentation for the policy. */
    override fun processPolicyAnnotation(annotation: AnnotationItem, item: Item): String {
        val proxy = annotation.bindTo<LongPolicyDefinitionProxy>(item)
        return proxy?.generateDocs() ?: ""
    }
}

/**
 * Proxy class bound to an instance of the `android.processor.devicepolicy.LongPolicyDefinition`
 * annotation class.
 *
 * @see bindTo
 */
data class LongPolicyDefinitionProxy(
    val base: PolicyDefinitionProxy,
    val minValue: Long,
    val maxValue: Long,
    val resolutionMechanism: LongResolutionMechanismProxy,
) {
    fun generateDocs() = buildString {
        val tableEntries = buildList {
            addAll(base.getTableEntries())
            val resolutionMechanismDoc = resolutionMechanism.generateDocs()
            if (resolutionMechanismDoc.isNotEmpty()) {
                add(Pair("Conflict resolution mechanism", resolutionMechanismDoc))
            }
            val policyValueValidations = buildList {
                add(
                    Pair(
                        "Min Value",
                        if (minValue == Long.MIN_VALUE) "No limit" else minValue.toString()
                    )
                )
                add(
                    Pair(
                        "Max Value",
                        if (maxValue == Long.MAX_VALUE) "No limit" else maxValue.toString()
                    )
                )
            }
            add(Pair("Policy value", renderPolicyValue("Long", policyValueValidations)))
        }

        append("\n<p>Policy Type: Long</p>\n")
        append(renderTable(tableEntries))
    }
}

/**
 * Proxy class bound to an instance of the `android.processor.devicepolicy.LongResolutionMechanism`
 * annotation class.
 *
 * @see bindTo
 */
data class LongResolutionMechanismProxy(
    val item: Item,
    val custom: Boolean,
    val notCoexistable: Boolean,
) {
    fun generateDocs() =
        if (custom) {
            ""
        } else if (notCoexistable) {
            "This policy can not be set by multiple admins at the same time. When multiple values are set, the resulting behavior is undefined and is monitored to avoid widespread usage."
        } else {
            item.codebase.reporter.report(
                Issues.INVALID_DEVICE_POLICY_ANNOTATION,
                item,
                "LongResolutionMechanism must have either 'custom' or 'notCoexistable' set to true."
            )
            ""
        }
}
