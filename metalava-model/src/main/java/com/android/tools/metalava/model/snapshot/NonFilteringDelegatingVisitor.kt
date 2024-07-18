/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.tools.metalava.model.snapshot

import com.android.tools.metalava.model.BaseItemVisitor
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.DelegatedVisitor

/**
 * A [BaseItemVisitor] that will delegate to [delegate].
 *
 * Always preserves class nesting while visiting.
 */
class NonFilteringDelegatingVisitor(private val delegate: DelegatedVisitor) :
    BaseItemVisitor(preserveClassNesting = true) {
    override fun visitCodebase(codebase: Codebase) {
        delegate.visitCodebase(codebase)
    }

    override fun afterVisitCodebase(codebase: Codebase) {
        delegate.afterVisitCodebase(codebase)
    }
}
