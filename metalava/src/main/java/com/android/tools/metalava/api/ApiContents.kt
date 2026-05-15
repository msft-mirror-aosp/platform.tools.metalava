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

package com.android.tools.metalava.api

import com.android.tools.metalava.model.BaseTypeVisitor
import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassOrigin
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.FilterPredicate
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.TargetLanguageSet
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.VariableTypeItem
import com.android.tools.metalava.model.visitors.ApiPredicate
import com.android.tools.metalava.reporter.Issues

/** Determines all the [ClassItem]s that are part of the API. */
internal class ApiContents(
    private val codebase: Codebase,
    private val apiPredicateConfig: ApiPredicate.Config,
) {

    private val reporter = codebase.reporter

    /**
     * The set of [ClassItem]s that are part of the API.
     *
     * Populated by [computeTransitiveClosure].
     */
    private val notStrippable = HashSet<ClassItem>(5000)

    /** The filter that determines which [SelectableItem]s are included in the API. */
    private val filter = FilterPredicate { selectableItem ->
        ApiPredicate(config = apiPredicateConfig.copy(ignoreShown = true)).test(selectableItem) &&
            // Don't consider references from elements that only exist in bytecode.
            selectableItem.targetLanguages != TargetLanguageSet.BYTECODE_ONLY
    }

    /**
     * Computes the transitive closure of the API surface.
     *
     * Starts with the set of all top level classes that are usable outside the package/module in
     * which they are defined, currently marked as emitted and not hidden. It then proceeds to find
     * any class referenced from those classes, directly or indirectly. Returning the set of all
     * classes that were visited.
     */
    // TODO: Switch to visitor iteration
    private fun computeTransitiveClosure(): Set<ClassItem> {
        // If a class is public or protected, not hidden, not imported and marked as included,
        // then we can't strip it
        val allTopLevelClasses = codebase.getPackages().allTopLevelClasses().toList()
        allTopLevelClasses
            .filter { it.isApiCandidate() && it.emit && !it.hidden() }
            .forEach { cantStripThis(it, it, "self") }
        return notStrippable
    }

    private fun cantStripThis(
        cl: ClassItem,
        from: Item,
        usage: String,
    ) {
        if (cl.origin == ClassOrigin.CLASS_PATH) {
            return
        }

        if (cl.isHiddenOrRemoved() || cl.isPackagePrivate && !cl.isApiCandidate()) {
            reporter.report(
                Issues.REFERENCES_HIDDEN,
                from,
                "Class ${cl.qualifiedName()} is ${if (cl.isHiddenOrRemoved()) "hidden" else "not public"} but was referenced ($usage) from public ${from.describe()}"
            )
        }

        if (!notStrippable.add(cl)) {
            // slight optimization: if it already contains cl, it already contains
            // all of cl's parents
            return
        }

        // can't strip any public fields or their generics
        for (field in cl.fields()) {
            if (!filter.test(field)) {
                continue
            }
            cantStripThis(field.type(), field, "in field type")
        }
        // can't strip any of the type's generics
        cantStripThis(cl.typeParameterList, cl)
        // can't strip any of the annotation elements
        // cantStripThis(cl.annotationElements(), notStrippable);
        // take care of methods
        cantStripThis(cl.methods())
        cantStripThis(cl.constructors())
        // blow the outer class open if this is an inner class
        val containingClass = cl.containingClass()
        if (containingClass != null) {
            cantStripThis(containingClass, cl, "as containing class")
        }
        // all visible inner classes will be included in stubs
        cl.nestedClasses()
            .filter { it.isApiCandidate() }
            .forEach { cantStripThis(it, cl, "as nested class") }
        // blow open super class and interfaces
        // TODO: Consider using val superClass = cl.filteredSuperclass(filter)
        val allSuperItems = cl.allInterfaces().toMutableSet()
        val directSuperItems = cl.interfaceTypes().map { it.qualifiedName }.toMutableSet()
        cl.superClass()?.let { superClass ->
            allSuperItems.add(superClass)
            directSuperItems.add(superClass.qualifiedName())
        }

        for (superItem in allSuperItems) {
            // allInterfaces includes cl itself if cl is an interface
            if (superItem.isHiddenOrRemoved() && superItem != cl) {
                // cl is a public class declared as extending a hidden superclass or implementing
                // a hidden interface.
                // this is not a desired practice, but it's happened, so we deal
                // with it by finding the first super class which passes checkLevel for purposes of
                // generating the doc & stub information, and proceeding normally.
                if (
                    // Make sure the parent element is either the superclass or an interface
                    // that cl is implementing directly (as opposed to indirectly via parent class)
                    superItem.qualifiedName() in directSuperItems
                ) {
                    reporter.report(
                        Issues.HIDDEN_SUPERCLASS,
                        cl,
                        "Public class " +
                            cl.qualifiedName() +
                            " stripped of unavailable superclass " +
                            superItem.qualifiedName()
                    )
                }
            } else {
                if (superItem.isPrivate && superItem.origin != ClassOrigin.CLASS_PATH) {
                    reporter.report(
                        Issues.PRIVATE_SUPERCLASS,
                        cl,
                        "Public class " +
                            cl.qualifiedName() +
                            " extends private class " +
                            superItem.qualifiedName()
                    )
                }
            }
        }
    }

    private fun cantStripThis(callables: List<CallableItem>) {
        // for each callable, blow open the parameters, throws and return types. also blow open
        // their generics
        for (callable in callables) {
            if (!filter.test(callable)) {
                continue
            }
            cantStripThis(callable.typeParameterList, callable)
            for (parameter in callable.parameters()) {
                cantStripThis(parameter.type(), parameter, "in parameter type")
            }
            for (thrown in callable.throwsTypes()) {
                if (thrown is VariableTypeItem) continue
                val classItem = thrown.asErasedClass(codebase) ?: continue
                cantStripThis(classItem, callable, "as exception")
            }
            cantStripThis(callable.returnType(), callable, "in return type")
        }
    }

    private fun cantStripThis(
        typeParameterList: TypeParameterList,
        context: Item,
    ) {
        for (typeParameter in typeParameterList) {
            for (bound in typeParameter.typeBounds()) {
                cantStripThis(bound, context, "as type parameter")
            }
        }
    }

    private fun cantStripThis(
        type: TypeItem,
        context: Item,
        usage: String,
    ) {
        type.accept(
            object : BaseTypeVisitor() {
                override fun visitClassType(classType: ClassTypeItem) {
                    val asClass = classType.resolveClass(codebase) ?: return
                    cantStripThis(asClass, context, usage)
                }
            }
        )
    }

    companion object {
        /** Compute the set of [ClassItem]s that are in the API. */
        fun computeContents(
            codebase: Codebase,
            apiPredicateConfig: ApiPredicate.Config,
        ): Set<ClassItem> {
            val apiContents = ApiContents(codebase, apiPredicateConfig)
            return apiContents.computeTransitiveClosure()
        }
    }
}

/** Returns true if this item is public or protected and so a candidate for inclusion in an API. */
internal fun SelectableItem.isApiCandidate() =
    !isHiddenOrRemoved() && (modifiers.isPublic() || modifiers.isProtected())
