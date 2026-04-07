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
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.annotation.binding.bindTo
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Reporter
import java.util.function.Predicate

/** Handles @android.processor.devicepolicy.IntegerPolicyDefinition annotation. */
class IntegerPolicyAnnotationHandler(
    codebase: Codebase,
    reporter: Reporter,
    filterReference: Predicate<SelectableItem>
) : BaseDevicePolicyAnnotationHandler(codebase, reporter, filterReference) {

    /**
     * Processes the [IntegerPolicyDefinitionProxy] and returns the documentation for the policy.
     */
    override fun processPolicyAnnotation(annotation: AnnotationItem, item: Item): String {
        val proxy = annotation.bindTo<IntegerPolicyDefinitionProxy>(item) ?: return ""
        val minValue = proxy.minValue
        val maxValue = proxy.maxValue

        val resolutionMechanismDoc = buildIntegerResolutionMechanismDoc(proxy, item)

        return buildString {
            append("\n<p>Policy Type: Integer</p>\n <ul>\n")
            append(proxy.base.generateDocs())
            append("   <li>Resolution Mechanism: $resolutionMechanismDoc</li>\n")
            append(
                "   <li>Min Value: ${if (minValue == Integer.MIN_VALUE) "No limit" else minValue}</li>\n"
            )
            append(
                "   <li>Max Value: ${if (maxValue == Integer.MAX_VALUE) "No limit" else maxValue}</li>\n"
            )
            append(" </ul>\n")
        }
    }

    private fun buildIntegerResolutionMechanismDoc(
        integerPolicyDefinitionProxy: IntegerPolicyDefinitionProxy,
        item: Item
    ): String {
        val resolutionMechanismProxy = integerPolicyDefinitionProxy.resolutionMechanism

        val custom = resolutionMechanismProxy.custom
        val notCoexistable = resolutionMechanismProxy.notCoexistable

        return if (custom) {
            "custom"
        } else if (notCoexistable) {
            "notCoexistable"
        } else {
            reporter.report(
                Issues.INVALID_DEVICE_POLICY_ANNOTATION,
                item,
                "IntegerResolutionMechanism must have either 'custom' or 'notCoexistable' set to true."
            )
            ""
        }
    }
}

/**
 * Proxy class bound to an instance of the `android.processor.devicepolicy.IntegerPolicyDefinition`
 * annotation class.
 *
 * @see bindTo
 */
data class IntegerPolicyDefinitionProxy(
    val base: PolicyDefinitionProxy,
    val minValue: Int,
    val maxValue: Int,
    val resolutionMechanism: IntegerResolutionMechanismProxy,
)

/**
 * Proxy class bound to an instance of the
 * `android.processor.devicepolicy.IntegerResolutionMechanism` annotation class.
 *
 * @see bindTo
 */
data class IntegerResolutionMechanismProxy(
    val custom: Boolean,
    val notCoexistable: Boolean,
)
