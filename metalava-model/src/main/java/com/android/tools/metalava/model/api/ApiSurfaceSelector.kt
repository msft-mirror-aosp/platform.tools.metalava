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

package com.android.tools.metalava.model.api

import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.ShowOrHide
import com.android.tools.metalava.model.Showability
import com.android.tools.metalava.model.api.SurfaceSelectionRule.Effect
import com.android.tools.metalava.model.api.surface.ApiSurface
import com.android.tools.metalava.model.api.surface.ApiSurfaces

/** Helps determine to which api surface a [SelectableItem] belongs. */
class ApiSurfaceSelector(
    apiSurfaceRules: ApiSurfaceRules = ApiSurfaceRules(),
) {
    /** True if unannotated items should be included in the main [ApiSurface]. */
    val showUnannotated: Boolean

    /** True if this has annotations that include a [SelectableItem] in the stubs only. */
    val hasAnyShowForStubPurposesAnnotations: Boolean

    /** True if this has any annotations that can hide a [SelectableItem] from the public API. */
    val hasAnyHideAnnotations: Boolean

    /**
     * Associates an annotation pattern, e.g. `--show-annotation android.annotation.TestApi` with
     * its [Result].
     */
    internal val matcher: AnnotationMatcher<Result>

    init {
        var unannotatedSurface: ApiSurface? = null
        var hasShowForStubs = false
        var hasHideAnnotations = false

        val matcherRules = buildList {
            fun addMatcherRule(
                surface: ApiSurface,
                annotated: SelectAnnotated,
                showability: Showability,
            ) {
                add(
                    AnnotationMatcher.Rule(
                        annotated.annotationPattern,
                        Result(
                            showability,
                            surface,
                            annotated.effect == Effect.SHOW,
                            annotated.recursive,
                        )
                    )
                )
            }
            val apiSurfaces = apiSurfaceRules.apiSurfaces

            val main = apiSurfaces.main
            val all = apiSurfaces.all
            val narrowest = all.first()

            for (surface in all) {
                val isNarrowest = surface === narrowest
                val rules = apiSurfaceRules[surface.name] ?: continue
                for (rule in rules) {
                    if (rule is SelectUnannotated) {
                        require(isNarrowest) {
                            "unannotated rule is only allowed on narrowest surface $narrowest but was found on $surface"
                        }
                        unannotatedSurface = surface
                    } else if (rule is SelectAnnotated) {
                        val effect = rule.effect
                        if (effect == Effect.HIDE) {
                            require(isNarrowest) {
                                "hide rules are only allowed on narrowest surface $narrowest but $rule was found on $surface"
                            }
                            hasHideAnnotations = true
                            addMatcherRule(surface, rule, HIDE)
                        } else if (effect == Effect.SHOW) {
                            if (surface.isMain) {
                                if (rule.recursive) {
                                    addMatcherRule(surface, rule, SHOW)
                                } else {
                                    addMatcherRule(surface, rule, SHOW_SINGLE)
                                }
                            } else {
                                // TODO(b/508331653): Remove this restriction which only exists due
                                //  to limitations in the Showability mechanism. Once that has been
                                //  replaced with the ApiSurface mechanism it should be possible to
                                //  remove this.
                                require(rule.recursive) {
                                    "non-recursive rules are only allowed on main surface $main but was found on $surface"
                                }
                                hasShowForStubs = true
                                addMatcherRule(surface, rule, SHOW_FOR_STUBS)
                            }
                        }
                    }
                }
            }
        }

        // Sort the rules.
        val sortedRules = matcherRules.sortedWith(comparator)

        matcher = AnnotationMatcher.createFromRules(sortedRules)

        // showUnannotated only affects the main surface.
        showUnannotated = unannotatedSurface?.isMain == true

        hasAnyHideAnnotations = hasHideAnnotations
        hasAnyShowForStubPurposesAnnotations = hasShowForStubs
    }

    /** The qualified names of all annotations that can affect API surface selection. */
    val annotationNames = matcher.annotationNames

    /**
     * Compute the [Showability] for [annotationItem], returns `null` if [annotationItem] does not
     * affect API selection.
     */
    fun showability(annotationItem: AnnotationItem) =
        matcher.matchResult(annotationItem)?.showability

    /** Result of matching an [AnnotationItem] against the set of rules. */
    internal data class Result(
        /** The [Showability] of the [AnnotationItem]. */
        val showability: Showability,

        /** The [ApiSurface] to which the annotation applies. */
        val surface: ApiSurface,

        /**
         * True if an item annotated with the annotation is shown in the [surface], false if it is
         * hidden from the [surface].
         */
        val show: Boolean,

        /**
         * True if [show] applies to the contents of the annotated item, false if it only applies to
         * the item itself.
         */
        val recursive: Boolean,
    ) {
        override fun toString() = showability.toString()
    }

    companion object {
        /**
         * The annotation will cause the annotated item (and any enclosed items unless overridden by
         * a closer annotation) to be shown.
         */
        private val SHOW =
            Showability(
                name = "SHOW",
                show = ShowOrHide.SHOW,
                recursive = ShowOrHide.SHOW,
                forStubsOnly = ShowOrHide.NO_EFFECT,
            )

        /**
         * The annotation will cause the annotated item (and any enclosed items unless overridden by
         * a closer annotation) to be shown in the stubs only.
         */
        private val SHOW_FOR_STUBS =
            Showability(
                name = "SHOW_FOR_STUBS",
                show = ShowOrHide.NO_EFFECT,
                recursive = ShowOrHide.NO_EFFECT,
                forStubsOnly = ShowOrHide.SHOW,
            )

        /** The annotation will cause the annotated item (but not enclosed items) to be shown. */
        private val SHOW_SINGLE =
            Showability(
                name = "SHOW_SINGLE",
                show = ShowOrHide.SHOW,
                recursive = ShowOrHide.NO_EFFECT,
                forStubsOnly = ShowOrHide.NO_EFFECT,
            )

        /**
         * The annotation will cause the annotated item (and any enclosed items unless overridden by
         * a closer annotation) to not be shown.
         */
        private val HIDE =
            Showability(
                name = "HIDE",
                show = ShowOrHide.HIDE,
                recursive = ShowOrHide.HIDE,
                forStubsOnly = ShowOrHide.NO_EFFECT,
            )

        /**
         * Comparator that sorts [AnnotationMatcher.Rule] by
         * [AnnotationMatcher.Rule.annotationPattern].
         */
        private val patternComparator: Comparator<AnnotationMatcher.Rule<Result>> =
            Comparator.comparing { it.annotationPattern }

        /**
         * Comparator that sors [AnnotationMatcher.Rule] by [Showability] then reverse order of
         * [patternComparator]
         */
        private val comparator =
            // First sort so that HIDE is last. That is because SHOW overrides HIDE.
            Comparator.comparing<AnnotationMatcher.Rule<Result>, Boolean> { !it.result.show }
                // Then make sure that a rule that matches the main surface comes before any other
                // surface.
                .thenComparing { !it.result.surface.isMain }
                // Then make sure that recursive rules come before non-recursive rules.
                .thenComparing { !it.result.recursive }
                // Finally, sort from longest (most specific pattern) to shortest. That is because a
                // longer more specific pattern should be matched before a shorter, less specific
                // one.
                .thenDescending(patternComparator)
    }
}

/** Associates a list of [SurfaceSelectionRule]s with each [ApiSurface] in [ApiSurfaces.all]. */
class ApiSurfaceRules(
    internal val apiSurfaces: ApiSurfaces = ApiSurfaces.DEFAULT,
    private val byName: Map<String, List<SurfaceSelectionRule>> = emptyMap(),
) {
    operator fun get(surfaceName: String) = byName[surfaceName]

    override fun toString() = buildString {
        append("ApiSurfaceRules(\n")
        for ((surface, rules) in byName) {
            append("    ")
            append(surface)
            append(" -> {\n")
            for (rule in rules) {
                append("        ")
                append(rule)
                append("\n")
            }
            append("    }\n")
        }
        append(")")
    }
}

/** A rule for selecting items to belong to an [ApiSurface]. */
sealed interface SurfaceSelectionRule {
    /** The effect that a [SurfaceSelectionRule] created by [createAnnotationRule] will have. */
    enum class Effect {
        /** The annotated item will be included in the [ApiSurface]. */
        SHOW,

        /**
         * The annotated item will be excluded from the [ApiSurface].
         *
         * This must only be used on the narrowest [ApiSurface].
         */
        HIDE,
    }

    companion object {
        /**
         * Include unannotated items.
         *
         * Must only be specified on the narrowest [ApiSurface].
         */
        val unannotated: SurfaceSelectionRule = SelectUnannotated()

        /**
         * Create a rule that will show/hide items that are annotated with an annotation that
         * matches [annotationPattern].
         *
         * @param annotationPattern the pattern that the annotation must match.
         * @param effect the effect of this rule on the annotated item.
         * @param recursive if `true` then enclosed items will also be included in the [ApiSurface].
         */
        fun createAnnotationRule(
            annotationPattern: String,
            effect: Effect = Effect.SHOW,
            recursive: Boolean = true,
        ): SurfaceSelectionRule = SelectAnnotated(annotationPattern, effect, recursive)
    }
}

/**
 * A [SurfaceSelectionRule] that will include unannotated items in the [ApiSurface].
 *
 * Must only be specified on the narrowest [ApiSurface].
 */
private class SelectUnannotated : SurfaceSelectionRule {
    override fun toString() = "SelectUnannotated"
}

/**
 * A [SurfaceSelectionRule] that will include/exclude annotated items in/from the [ApiSurface].
 *
 * @param annotationPattern the pattern that the annotation must match.
 * @param effect the effect of this rule on the annotated item.
 * @param recursive if `true` then enclosed items will also be included in the [ApiSurface].
 */
private data class SelectAnnotated(
    val annotationPattern: String,
    val effect: Effect,
    val recursive: Boolean,
) : SurfaceSelectionRule
