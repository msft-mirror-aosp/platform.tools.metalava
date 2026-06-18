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

/** Handles @android.processor.devicepolicy.StringPolicyDefinition annotation. */
class StringPolicyAnnotationHandler(
    context: DevicePolicyContext,
) : BaseDevicePolicyAnnotationHandler(context) {

    /** Processes the [StringPolicyDefinitionProxy] and returns the documentation for the policy. */
    override fun processPolicyAnnotation(annotation: AnnotationItem, item: Item): String {
        val proxy = annotation.bindTo<StringPolicyDefinitionProxy>(item)
        return proxy?.generateDocs() ?: ""
    }
}

/**
 * Proxy class bound to an instance of the `android.processor.devicepolicy.StringPolicyDefinition`
 * annotation class.
 *
 * @see bindTo
 */
data class StringPolicyDefinitionProxy(
    val base: PolicyDefinitionProxy,
    val emptyStringAllowed: Boolean,
    val unprintableCharactersAllowed: Boolean,
    val pureWhitespaceAllowed: Boolean,
    val unstrippedStringAllowed: Boolean,
    val maxLength: Int,
) {
    fun generateDocs() = buildString {
        val tableEntries = buildList {
            addAll(base.getTableEntries())
            val policyValueValidations = buildList {
                if (maxLength != Integer.MAX_VALUE) add("Length max $maxLength characters")
                if (!emptyStringAllowed) add("No empty string allowed")
                if (!unprintableCharactersAllowed) add("No unprintable characters allowed")
                if (!pureWhitespaceAllowed) add("No pure whitespace allowed")
                if (!unstrippedStringAllowed) add("No unstripped string allowed")
            }
            add(Pair("Policy value", renderPolicyValue("String", policyValueValidations)))
        }

        append("\n<p>Policy Type: String</p>\n")
        append(renderTable(tableEntries))
    }
}
