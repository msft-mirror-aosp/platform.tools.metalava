/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.metalava.ApiPredicate
import com.android.tools.metalava.PackageFilter
import com.android.tools.metalava.model.BaseItemVisitor
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.PropertyItem
import java.util.function.Predicate

open class ApiVisitor(
    /**
     * Whether constructors should be visited as part of a [#visitMethod] call instead of just a
     * [#visitConstructor] call. Helps simplify visitors that don't care to distinguish between the
     * two cases. Defaults to true.
     */
    visitConstructorsAsMethods: Boolean = true,
    /**
     * Whether inner classes should be visited "inside" a class; when this property is true, inner
     * classes are visited before the [#afterVisitClass] method is called; when false, it's done
     * afterwards. Defaults to false.
     */
    nestInnerClasses: Boolean = false,

    /** Whether to include inherited fields too */
    val inlineInheritedFields: Boolean = true,

    /** Comparator to sort methods with. */
    val methodComparator: Comparator<MethodItem> = MethodItem.comparator,

    /** The filter to use to determine if we should emit an item */
    val filterEmit: Predicate<Item>,

    /** The filter to use to determine if we should emit a reference to an item */
    val filterReference: Predicate<Item>,

    /**
     * Whether this visitor should visit elements that have not been annotated with one of the
     * annotations passed in using the --show-annotation flag. This is normally true, but signature
     * files sometimes sets this to false so the signature file only contains the "diff" of the
     * annotated API relative to the base API.
     */
    val showUnannotated: Boolean = true,

    /** Configuration that may come from the command line. */
    config: Config,
) : BaseItemVisitor(visitConstructorsAsMethods, nestInnerClasses) {

    private val packageFilter: PackageFilter? = config.packageFilter

    /**
     * Contains configuration for [ApiVisitor] that can, or at least could, come from command line
     * options.
     */
    data class Config(
        val packageFilter: PackageFilter? = null,

        /** Configuration for any [ApiPredicate] instances this needs to create. */
        val apiPredicateConfig: ApiPredicate.Config = ApiPredicate.Config()
    )

    constructor(
        /**
         * Whether constructors should be visited as part of a [#visitMethod] call instead of just a
         * [#visitConstructor] call. Helps simplify visitors that don't care to distinguish between
         * the two cases. Defaults to true.
         */
        visitConstructorsAsMethods: Boolean = true,
        /**
         * Whether inner classes should be visited "inside" a class; when this property is true,
         * inner classes are visited before the [#afterVisitClass] method is called; when false,
         * it's done afterwards. Defaults to false.
         */
        nestInnerClasses: Boolean = false,

        /** Whether to ignore APIs with annotations in the --show-annotations list */
        ignoreShown: Boolean = true,

        /** Whether to match APIs marked for removal instead of the normal API */
        remove: Boolean = false,

        /** Comparator to sort methods with. */
        methodComparator: Comparator<MethodItem> = MethodItem.comparator,

        /**
         * The filter to use to determine if we should emit an item. If null, the default value is
         * an [ApiPredicate] based on the values of [remove], [includeApisForStubPurposes],
         * [config], and [ignoreShown].
         */
        filterEmit: Predicate<Item>? = null,

        /**
         * The filter to use to determine if we should emit a reference to an item. If null, the
         * default value is an [ApiPredicate] based on the values of [remove] and [config].
         */
        filterReference: Predicate<Item>? = null,

        /**
         * Whether to include "for stub purposes" APIs.
         *
         * See [ApiPredicate.includeOnlyForStubPurposes]
         */
        includeApisForStubPurposes: Boolean = true,

        /** Configuration that may come from the command line. */
        config: Config,
    ) : this(
        visitConstructorsAsMethods = visitConstructorsAsMethods,
        nestInnerClasses = nestInnerClasses,
        inlineInheritedFields = true,
        methodComparator = methodComparator,
        filterEmit = filterEmit
                ?: ApiPredicate(
                    matchRemoved = remove,
                    includeApisForStubPurposes = includeApisForStubPurposes,
                    config = config.apiPredicateConfig.copy(ignoreShown = ignoreShown),
                ),
        filterReference = filterReference
                ?: ApiPredicate(
                    ignoreRemoved = remove,
                    config = config.apiPredicateConfig.copy(ignoreShown = true),
                ),
        config = config,
    )

    // The API visitor lazily visits packages only when there's a match within at least one class;
    // this property keeps track of whether we've already visited the current package
    var visitingPackage = false

    /**
     * Visit a [List] of [ClassItem]s after sorting it into order using [ClassItem.classNameSorter].
     */
    private fun visitClassList(classes: List<ClassItem>) {
        classes.sortedWith(ClassItem.classNameSorter()).forEach { cls -> cls.accept(this) }
    }

    override fun visit(cls: ClassItem) {
        if (!include(cls)) {
            return
        }

        // We build up a separate data structure such that we can compute the sets of fields,
        // methods, etc. and check if any of them need to be emitted.
        val candidate = VisitCandidate(cls)
        candidate.accept()
    }

    /** Recursively flatten the class nesting. */
    private fun flattenClassNesting(cls: ClassItem): Sequence<ClassItem> =
        sequenceOf(cls) + cls.innerClasses().asSequence().flatMap { flattenClassNesting(it) }

    override fun visit(pkg: PackageItem) {
        if (!pkg.emit) {
            return
        }

        // Get the list of classes to visit directly. If nested classes are to appear as nested
        // then just visit the top level classes directly and then the inner classes will be visited
        // by their containing classes. Otherwise, flatten the nested classes and treat them all as
        // top level classes.
        val classesToVisitDirectly =
            pkg.topLevelClasses()
                .let { topLevelClasses ->
                    if (nestInnerClasses) topLevelClasses
                    else topLevelClasses.flatMap { flattenClassNesting(it) }
                }
                .toList()

        // For the API visitor packages are visited lazily; only when we encounter
        // an unfiltered item within the class
        visitClassList(classesToVisitDirectly)

        if (visitingPackage) {
            visitingPackage = false
            afterVisitPackage(pkg)
            afterVisitItem(pkg)
        }
    }

    /** @return Whether this class is generally one that we want to recurse into */
    open fun include(cls: ClassItem): Boolean {
        if (skip(cls)) {
            return false
        }
        if (packageFilter != null && !packageFilter.matches(cls.containingPackage())) {
            return false
        }

        return cls.emit
    }

    /**
     * @return Whether the given VisitCandidate's visitor should recurse into the given
     *   VisitCandidate's class
     */
    fun include(vc: VisitCandidate): Boolean {
        if (!include(vc.cls)) {
            return false
        }
        return shouldEmitClassBody(vc)
    }

    /**
     * @return Whether this class should be visited Note that if [include] returns true then we will
     *   still visit classes that are contained by this one
     */
    open fun shouldEmitClass(vc: VisitCandidate): Boolean {
        return shouldEmitClassBody(vc)
    }

    /**
     * @return Whether the body of this class (everything other than the inner classes) emits
     *   anything
     */
    private fun shouldEmitClassBody(vc: VisitCandidate): Boolean {
        return when {
            filterEmit.test(vc.cls) -> true
            vc.nonEmpty() -> filterReference.test(vc.cls)
            else -> false
        }
    }

    inner class VisitCandidate(val cls: ClassItem) {
        private val constructors by
            lazy(LazyThreadSafetyMode.NONE) {
                val clsConstructors = cls.constructors()
                if (clsConstructors.isEmpty()) {
                    emptyList()
                } else {
                    clsConstructors
                        .asSequence()
                        .filter { filterEmit.test(it) }
                        .sortedWith(methodComparator)
                        .toList()
                }
            }

        private val methods by
            lazy(LazyThreadSafetyMode.NONE) {
                val clsMethods = cls.methods()
                if (clsMethods.isEmpty()) {
                    emptyList()
                } else {
                    clsMethods
                        .asSequence()
                        .filter { filterEmit.test(it) }
                        .sortedWith(methodComparator)
                        .toList()
                }
            }

        private val fields by
            lazy(LazyThreadSafetyMode.NONE) {
                val fieldSequence =
                    if (inlineInheritedFields) {
                        cls.filteredFields(filterEmit, showUnannotated).asSequence()
                    } else {
                        cls.fields().asSequence().filter { filterEmit.test(it) }
                    }

                // Sort the fields so that enum constants come first.
                fieldSequence.sortedWith(FieldItem.comparatorEnumConstantFirst)
            }

        private val properties by
            lazy(LazyThreadSafetyMode.NONE) {
                val clsProperties = cls.properties()
                if (clsProperties.isEmpty()) {
                    emptyList()
                } else {
                    clsProperties
                        .asSequence()
                        .filter { filterEmit.test(it) }
                        .sortedWith(PropertyItem.comparator)
                        .toList()
                }
            }

        /** Whether the class body contains any Item's (other than inner Classes) */
        fun nonEmpty(): Boolean {
            return !(constructors.none() && methods.none() && fields.none() && properties.none())
        }

        fun accept() {
            if (!include(this)) {
                return
            }

            val emitThis = shouldEmitClass(this)
            if (emitThis) {
                if (!visitingPackage) {
                    visitingPackage = true
                    val pkg = cls.containingPackage()
                    visitItem(pkg)
                    visitPackage(pkg)
                }

                visitItem(cls)
                visitClass(cls)

                for (constructor in constructors) {
                    constructor.accept(this@ApiVisitor)
                }

                for (method in methods) {
                    method.accept(this@ApiVisitor)
                }

                for (property in properties) {
                    property.accept(this@ApiVisitor)
                }
                for (field in fields) {
                    field.accept(this@ApiVisitor)
                }
            }

            if (nestInnerClasses) { // otherwise done in visit(PackageItem)
                visitClassList(cls.innerClasses())
            }

            if (emitThis) {
                afterVisitClass(cls)
                afterVisitItem(cls)
            }
        }
    }
}
