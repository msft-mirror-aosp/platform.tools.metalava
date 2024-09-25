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

package com.android.tools.metalava.model

import com.android.tools.metalava.model.snapshot.CodebaseSnapshotTaker
import com.android.tools.metalava.model.snapshot.EmittableDelegatingVisitor

/**
 * Encapsulates [codebase] to visit and a [factory] that if given a [DelegatedVisitor] will return
 * an [ItemVisitor] that can be used to visit some fragment of the [codebase].
 *
 * @param factory a factory for creating an [ItemVisitor] that delegates to a [DelegatedVisitor].
 *   The [ItemVisitor] is used to determine which parts of [codebase] are considered to be defined
 *   within and emitted from this fragment.
 */
class CodebaseFragment(
    val codebase: Codebase,
    private val factory: (DelegatedVisitor) -> ItemVisitor,
) {
    /**
     * Create an [ItemVisitor] that will visit this fragment and delegate its contents to
     * [delegate].
     */
    fun createVisitor(delegate: DelegatedVisitor) = factory(delegate)

    /**
     * Take a snapshot of this [CodebaseFragment] and return a new [CodebaseFragment].
     *
     * @param referenceVisitorFactory a factory for creating an [ItemVisitor] that delegates to a
     *   [DelegatedVisitor]. The [ItemVisitor] is used to determine which parts of [codebase] will
     *   be referenced from within but not emitted from the snapshot.
     */
    fun snapshotIncludingRevertedItems(
        referenceVisitorFactory: (DelegatedVisitor) -> ItemVisitor,
    ): CodebaseFragment {
        val snapshot =
            CodebaseSnapshotTaker.takeSnapshot(
                codebase,
                definitionVisitorFactory = factory,
                referenceVisitorFactory = referenceVisitorFactory
            )
        return CodebaseFragment(snapshot, ::EmittableDelegatingVisitor)
    }

    /** Visit this fragment, delegating to [delegate]. */
    fun accept(delegate: DelegatedVisitor) {
        val visitor = createVisitor(delegate)
        codebase.accept(visitor)
    }
}
