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

package com.android.tools.metalava.model.visitors

import com.android.tools.metalava.model.FilterPredicate
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.SelectableItem

/**
 * A [FilterPredicate] that will match a [SelectableItem] if [wrapped] matches it, or it is a
 * [MethodItem] and [wrapped] matches any of its super methods.
 *
 * In other words this will match any [SelectableItem] that is matched by [wrapped] and any
 * [MethodItem] that overrides a method which is matched by [wrapped].
 */
class MatchOverridingMethodPredicate(private val wrapped: FilterPredicate) : FilterPredicate {

    override fun test(item: SelectableItem): Boolean {
        return when {
            wrapped.test(item) -> true
            item is MethodItem -> item.findPredicateSuperMethod(wrapped) != null
            else -> false
        }
    }
}
