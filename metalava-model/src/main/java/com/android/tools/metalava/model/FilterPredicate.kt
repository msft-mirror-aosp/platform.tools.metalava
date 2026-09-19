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
 * A [Predicate] that is generally used to filter [SelectableItem]s that are defined in the API, or
 * can be referenced from the API.
 *
 * A null [FilterPredicate] should be treated as if it matched everything, i.e. was `{ true }`. It
 * can be used to optimize code paths.
 */
abstract class FilterPredicate : Predicate<SelectableItem> {
    /**
     * Returns a composed [FilterPredicate] that represents a short-circuiting logical AND of this
     * predicate and [other].
     */
    fun and(other: FilterPredicate): FilterPredicate = andPredicates(this, other)

    /**
     * Returns a composed [FilterPredicate] that represents a short-circuiting logical OR of this
     * predicate and [other].
     */
    fun or(other: FilterPredicate): FilterPredicate = orPredicates(this, other)
}

/**
 * Invoked this optional [FilterPredicate].
 *
 * If this [FilterPredicate] is `null` then this returns `true`, otherwise it returns the result of
 * invoking [Predicate.test] on [item].
 */
fun FilterPredicate?.testOrTrue(item: SelectableItem) = this?.test(item) ?: true

/**
 * [FilterPredicate] that only returns true for items that have [SelectableItem.emit] set to true.
 */
object EmittedOnlyPredicate : FilterPredicate() {
    override fun test(t: SelectableItem) = t.emit
}

/** [FilterPredicate] that matches everything. */
object MatchAllPredicate : FilterPredicate() {
    override fun test(t: SelectableItem) = true
}

/** [FilterPredicate] that matches nothing. */
object MatchNonePredicate : FilterPredicate() {
    override fun test(t: SelectableItem) = false
}

/** [FilterPredicate] that matches if all [predicates] match. */
private class AndPredicate(private val predicates: List<FilterPredicate>) : FilterPredicate() {
    override fun test(t: SelectableItem) = predicates.all { it.test(t) }
}

/** [FilterPredicate] that matches if any [predicates] match. */
private class OrPredicate(private val predicates: List<FilterPredicate>) : FilterPredicate() {
    override fun test(t: SelectableItem) = predicates.any { it.test(t) }
}

/**
 * Returns a composed [FilterPredicate] that represents a short-circuiting logical AND of all
 * [predicates].
 *
 * If no predicates are provided, the returned predicate will match everything.
 */
fun andPredicates(vararg predicates: FilterPredicate): FilterPredicate =
    AndPredicate(predicates.toList())

/**
 * Returns a composed [FilterPredicate] that represents a short-circuiting logical OR of all
 * [predicates].
 *
 * If no predicates are provided, the returned predicate will match nothing.
 */
fun orPredicates(vararg predicates: FilterPredicate): FilterPredicate =
    OrPredicate(predicates.toList())
