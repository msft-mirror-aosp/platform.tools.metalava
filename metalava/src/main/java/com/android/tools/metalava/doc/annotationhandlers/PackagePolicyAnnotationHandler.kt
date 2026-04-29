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
import com.android.tools.metalava.reporter.Reporter
import java.util.function.Predicate

/** Handles @android.processor.devicepolicy.PackagePolicyDefinition annotation. */
class PackagePolicyAnnotationHandler(
    codebase: Codebase,
    reporter: Reporter,
    filterReference: Predicate<SelectableItem>
) : BaseDevicePolicyAnnotationHandler(codebase, reporter, filterReference) {
    /**
     * Processes the [PackagePolicyDefinitionProxy] and returns the documentation for the policy.
     */
    override fun processPolicyAnnotation(annotation: AnnotationItem, item: Item): String {
        val proxy = annotation.bindTo<PackagePolicyDefinitionProxy>(item)
        return proxy?.generateDocs() ?: ""
    }
}

/**
 * Proxy class bound to an instance of the `android.processor.devicepolicy.PackagePolicyDefinition`
 * annotation class.
 *
 * @see bindTo
 */
data class PackagePolicyDefinitionProxy(
    val base: PolicyDefinitionProxy,
) {
    fun generateDocs() = buildString {
        append("\n<p>Policy Type: Package</p>\n <ul>\n")
        append(base.generateDocs())
        append(" </ul>\n")
    }
}
