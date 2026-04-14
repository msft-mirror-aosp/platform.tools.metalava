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
import com.android.tools.metalava.model.value.AnnotationValue
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Reporter
import java.util.function.Predicate

/** Handles @android.processor.devicepolicy.ListOfStringPolicyDefinition annotation. */
class ListOfStringPolicyAnnotationHandler(
    codebase: Codebase,
    reporter: Reporter,
    filterReference: Predicate<SelectableItem>
) : BaseDevicePolicyAnnotationHandler(codebase, reporter, filterReference) {
    private val policyHandler =
        PolicyDefinitionAnnotationHandler(codebase, reporter, filterReference)

    /**
     * Processes the [ListOfStringPolicyDefinition] annotation and returns the documentation for the
     * policy.
     */
    override fun processPolicyAnnotation(annotation: AnnotationItem, item: Item): String {
        val emptyListAllowed = annotation.getBooleanAttribute("emptyListAllowed") ?: false
        val emptyStringAllowed = annotation.getBooleanAttribute("emptyStringAllowed") ?: false
        val unprintableCharactersAllowed =
            annotation.getBooleanAttribute("unprintableCharactersAllowed") ?: false

        val basePolicyDefinition =
            annotation.getPolicyDefinitionAttribute("base").elseReportMissing(item, "base")
        val baseDocs =
            basePolicyDefinition?.let {
                policyHandler.processPolicyAnnotation(basePolicyDefinition, item)
            } ?: ""

        return buildString {
            append("\n<p>Policy Type: List Of String</p>\n <ul>\n")
            append(baseDocs)
            buildListResolutionMechanismDoc(annotation, item)
            appendAllowed("Empty list", emptyListAllowed, leadingSpaces = "   ")
            appendAllowed("Empty string", emptyStringAllowed, leadingSpaces = "   ")
            appendAllowed(
                "Unprintable characters",
                unprintableCharactersAllowed,
                leadingSpaces = "   "
            )
            append(" </ul>\n")
        }
    }

    /**
     * Builds the documentation for the `resolutionMechanism` field of a
     * ListOfStringPolicyDefinition.
     *
     * @param annotation The ListOfStringPolicyDefinition annotation.
     * @param item The item to which the annotation is applied.
     */
    private fun StringBuilder.buildListResolutionMechanismDoc(
        annotation: AnnotationItem,
        item: Item,
        leadingSpaces: String = "   "
    ) {
        val resolutionMechanismValue = annotation.findAttribute("resolutionMechanism")?.value
        val resolutionMechanismAnnotation =
            (resolutionMechanismValue as? AnnotationValue)?.annotationItem

        if (resolutionMechanismAnnotation != null) {
            val custom = resolutionMechanismAnnotation.getBooleanAttribute("custom") ?: false
            val union = resolutionMechanismAnnotation.getBooleanAttribute("union") ?: false

            // TODO(b/492421367): Enrich the doc for resolution mechanism.
            if (custom) {
                append("$leadingSpaces<li>Resolution Mechanism: custom</li>\n")
            } else if (union) {
                append("$leadingSpaces<li>Resolution Mechanism: union</li>\n")
            } else {
                reporter.report(
                    Issues.INVALID_DEVICE_POLICY_ANNOTATION,
                    item,
                    "ListResolutionMechanism must have either 'custom' or 'union' set to true."
                )
            }
        } else {
            // Should not fall into this branch because resolutionMechanism is a required field.
            // Missing it will cause Java compiler errors.
            reportOnMissingFields("resolutionMechanism", item)
        }
    }
}
