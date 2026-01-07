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

package com.android.tools.metalava.model.scope

import com.android.tools.metalava.model.ReferencableItem

/**
 * Classification of names used by the Java compiler, specifically with regard to those that are
 * used in https://docs.oracle.com/javase/specs/jls/se21/html/jls-6.html#jls-6.5.2.
 *
 * This is used to provide contextual information about the name based on where it is used. e.g.
 * * in `@see <reference>`, the `<reference>` can refer to any [ReferencableItem] so is classified
 *   as [NameClassification.AMBIGUOUS].
 */
enum class NameClassification() {
    /** The name is ambiguous and could refer to any [ReferencableItem]. */
    AMBIGUOUS,
}
