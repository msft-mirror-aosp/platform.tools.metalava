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

import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.DelegatedVisitor
import com.android.tools.metalava.model.item.DefaultCodebase

/** Constructs a [Codebase] by taking a snapshot of another [Codebase] that is being visited. */
class CodebaseSnapshotTaker : DelegatedVisitor {
    /**
     * The [Codebase] that is under construction.
     *
     * Initialized in [visitCodebase].
     */
    private lateinit var codebase: Codebase

    override fun visitCodebase(codebase: Codebase) {
        this.codebase =
            DefaultCodebase(
                location = codebase.location,
                description = "snapshot of ${codebase.description}",
                preFiltered = true,
                annotationManager = codebase.annotationManager,
                trustedApi = true,
                // Supports documentation if the copied codebase does.
                supportsDocumentation = codebase.supportsDocumentation(),
            )
    }

    companion object {
        /** Take a snapshot of [codebase]. */
        fun takeSnapshot(codebase: Codebase): Codebase {
            // Create a snapshot taker that will construct the snapshot.
            val taker = CodebaseSnapshotTaker()

            // Wrap it in a visitor and visit the codebase.
            val visitor = NonFilteringDelegatingVisitor(taker)
            codebase.accept(visitor)

            // Return the constructed snapshot.
            return taker.codebase
        }
    }
}
