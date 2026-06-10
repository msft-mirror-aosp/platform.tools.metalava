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

import java.util.function.Predicate

/**
 * Type alias for [Predicate]s that are generally used to filter [SelectableItem]s that are defined
 * in the API, or can be referenced from the API.
 *
 * A null [FilterPredicate] should be treated as if it matched everything, i.e. was `{ true }`. It
 * can be used to optimize code paths.
 */
typealias FilterPredicate = Predicate<SelectableItem>

/**
 * Invoked this optional [FilterPredicate].
 *
 * If this [FilterPredicate] is `null` then this returns `true`, otherwise it returns the result of
 * invoking [Predicate.test] on [item].
 */
fun FilterPredicate?.testOrTrue(item: SelectableItem) = this?.test(item) ?: true

/**
 * Combine this [FilterPredicate] with an optional [other] to produce a [FilterPredicate] that is
 * the logical AND of the two [FilterPredicate]s.
 *
 * AND-ing anything with `true` has no effect. So, when [other] is `null` (which is equivalent to `{
 * true }`) this [FilterPredicate] will be returned.
 */
fun FilterPredicate.andNullable(other: FilterPredicate?): FilterPredicate =
    if (other == null) {
        this
    } else {
        and(other)
    }

/**
 * Combine this optional [FilterPredicate] with an optional [other] to produce a [FilterPredicate]
 * that is the logical AND of the two [FilterPredicate]s.
 *
 * If this is `null` then it returns [other], otherwise it calls [andNullable] on [other].
 */
fun FilterPredicate?.nullableAndNullable(other: FilterPredicate?) =
    this?.andNullable(other) ?: other
