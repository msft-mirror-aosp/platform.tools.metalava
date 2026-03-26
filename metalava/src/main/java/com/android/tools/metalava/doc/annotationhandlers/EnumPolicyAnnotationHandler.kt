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
import com.android.tools.metalava.reporter.Reporter
import java.util.function.Predicate

/** Handles @android.processor.devicepolicy.EnumPolicyDefinition annotation. */
class EnumPolicyAnnotationHandler(
    codebase: Codebase,
    reporter: Reporter,
    filterReference: Predicate<SelectableItem>
) : BaseDevicePolicyAnnotationHandler(codebase, reporter, filterReference) {

    private val policyHandler =
        PolicyDefinitionAnnotationHandler(codebase, reporter, filterReference)

    /** Processes a policy annotation and returns a documentation string. */
    override fun processPolicyAnnotation(annotation: AnnotationItem, item: Item): String {
        val resolutionMechanismDoc = buildResolutionMechanismDoc(annotation, item)
        // TODO(b/492421367): handles the intDef field.
        val defaultValue =
            annotation.getIntAttribute("defaultValue").elseReportMissing(item, "defaultValue") ?: -1

        val basePolicyDefinition =
            annotation.getPolicyDefinitionAttribute("base").elseReportMissing(item, "base")
        val baseDocs =
            basePolicyDefinition?.let {
                policyHandler.processPolicyAnnotation(basePolicyDefinition, item)
            } ?: ""

        return buildString {
            append("\n<p>Policy Type: Enum</p>\n <ul>\n")
            append(baseDocs)
            append("   <li>Resolution Mechanism: $resolutionMechanismDoc</li>\n")
            // TODO(b/492421367): show the enum name rather than integer value.
            append("   <li>Default Enum policy value: $defaultValue</li>\n")
            append(" </ul>\n")
        }
    }

    private fun buildResolutionMechanismDoc(annotation: AnnotationItem, item: Item): String {
        val resolutionMechanismValue = annotation.findAttribute("resolutionMechanism")?.value
        val resolutionMechanismAnnotation =
            (resolutionMechanismValue as? AnnotationValue)?.annotationItem

        var resolutionMechanismDoc = ""

        if (resolutionMechanismAnnotation != null) {
            val custom = resolutionMechanismAnnotation.getBooleanAttribute("custom") ?: false
            val notCoexistable =
                resolutionMechanismAnnotation.getBooleanAttribute("notCoexistable") ?: false
            val mostRestrictiveValue =
                resolutionMechanismAnnotation.findAttribute("mostRestrictive")?.value
            val mostRestrictive = mostRestrictiveValue?.asFlatList() ?: emptyList()

            if (custom) {
                resolutionMechanismDoc = "custom"
            } else if (notCoexistable) {
                resolutionMechanismDoc = "not coexistable"
            } else if (mostRestrictive.isNotEmpty()) {
                resolutionMechanismDoc = "most restrictive: [${mostRestrictive.joinToString(", ")}]"
            } else {
                reportOnMissingFields("resolutionMechanism", item)
            }
        }

        return resolutionMechanismDoc
    }
}
