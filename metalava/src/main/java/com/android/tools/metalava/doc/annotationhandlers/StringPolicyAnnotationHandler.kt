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
import com.android.tools.metalava.reporter.Reporter
import java.util.function.Predicate

/** Handles @android.processor.devicepolicy.StringPolicyDefinition annotation. */
class StringPolicyAnnotationHandler(
    codebase: Codebase,
    reporter: Reporter,
    filterReference: Predicate<SelectableItem>
) : BaseDevicePolicyAnnotationHandler(codebase, reporter, filterReference) {
    private val policyHandler =
        PolicyDefinitionAnnotationHandler(codebase, reporter, filterReference)

    /**
     * Processes the [StringPolicyDefinition] annotation and returns the documentation for the
     * policy.
     */
    override fun processPolicyAnnotation(annotation: AnnotationItem, item: Item): String {
        val emptyStringAllowed = annotation.getBooleanAttribute("emptyStringAllowed") ?: false

        val unprintableCharactersAllowed =
            annotation.getBooleanAttribute("unprintableCharactersAllowed") ?: false
        val maxLength = annotation.getIntAttribute("maxLength") ?: Integer.MAX_VALUE

        val basePolicyDefinition =
            annotation.getPolicyDefinitionAttribute("base").elseReportMissing(item, "base")
        val baseDocs =
            basePolicyDefinition?.let {
                policyHandler.processPolicyAnnotation(basePolicyDefinition, item)
            } ?: ""

        return buildString {
            append("\n<p>Policy Type: String</p>\n <ul>\n")
            append(baseDocs)
            append(
                "   <li>Empty string: ${if (emptyStringAllowed) "Allowed" else "Not allowed"}</li>\n"
            )
            append(
                "   <li>Unprintable characters: ${if (unprintableCharactersAllowed) "Allowed" else "Not allowed"}</li>\n"
            )
            append(
                "   <li>Max Length: ${if (maxLength == Integer.MAX_VALUE) "No limit" else maxLength}</li>\n"
            )
            append(" </ul>\n")
        }
    }
}
