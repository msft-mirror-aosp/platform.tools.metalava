/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.tools.metalava.model.psi.kotlin

import com.android.SdkConstants
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.TargetLanguageSet
import com.android.tools.metalava.model.item.DefaultClassItem
import com.android.tools.metalava.model.psi.PsiBasedCodebase
import com.android.tools.metalava.model.psi.PsiConstructorItem
import com.android.tools.metalava.model.psi.PsiMethodItem
import com.android.tools.metalava.model.psi.psiParameters
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.search.GlobalSearchScope
import java.io.File
import java.util.zip.ZipFile

/**
 * Functionality for loading APIs from jar files compiled from Kotlin source code.
 *
 * First, the jar file needs to be processed by [listClassesInJar] to track all the qualified names
 * of classes present in the jar. Then, [loadPsiFromProject] will search for the class names from
 * the jar in a psi project to add APIs to the [codebase].
 */
internal class KotlinBytecodeApis(val codebase: PsiBasedCodebase) {
    /** Class names to process. Populated by [listClassesInJar] and used by [loadPsiFromProject]. */
    private val qualifiedClassNames = mutableListOf<String>()

    /** Processes the [jarFile] to save the qualified names of all classes in the jar. */
    fun listClassesInJar(jarFile: File) {
        ZipFile(jarFile).use { jar ->
            for (entry in jar.entries().iterator()) {
                val fileName = entry.name
                if (
                    !fileName.endsWith(SdkConstants.DOT_CLASS) ||
                        fileName.endsWith("package-info.class")
                ) {
                    // skip entries that are not .class files.
                    continue
                }

                val qualifiedName =
                    fileName
                        .removeSuffix(SdkConstants.DOT_CLASS)
                        .replace('/', '.')
                        .replace('$', '.')
                qualifiedClassNames.add(qualifiedName)
            }
        }
    }

    /**
     * Uses the [project] to load the psi for all classes previously found by [listClassesInJar],
     * and adds members to the [codebase].
     *
     * This will not add any classes to the [codebase], but for any existing classes, it will add
     * any callables which are not already present in the class item in the codebase.
     */
    fun loadPsiFromProject(project: Project) {
        val facade = JavaPsiFacade.getInstance(project)
        val scope = GlobalSearchScope.allScope(project)

        for (qualifiedName in qualifiedClassNames) {
            val psiClass = facade.findClass(qualifiedName, scope) ?: continue
            val classItem = codebase.findClass(qualifiedName) as? DefaultClassItem ?: continue
            addMethodsToClass(psiClass, classItem)
        }
    }

    /** Adds to the [classItem] the methods from the [psiClass] which are not already present. */
    private fun addMethodsToClass(psiClass: PsiClass, classItem: DefaultClassItem) {
        val classTypeItemFactory = codebase.globalTypeItemFactory.from(classItem)
        for (psiMethod in psiClass.methods) {
            // Skip processing certain methods based on name.
            if (skipTracking(psiMethod.name)) continue
            // Only process visible APIs. Internal APIs will have the public modifier, which can
            // be corrected later using the Kotlin metadata.
            if (
                !psiMethod.modifierList.hasModifierProperty(PsiModifier.PUBLIC) &&
                    !psiMethod.modifierList.hasModifierProperty(PsiModifier.PROTECTED)
            )
                continue

            // Don't re-add methods which are already present: find the items which might have
            // the same signature of this one, to compare by erased signature.
            val potentialMatches =
                erasedSignaturesOfPotentialMatchingCallables(psiMethod, classItem)
            // Right now, it would be complicated to get the real erased signature of the item
            // because that involves replacing variable types with their bounds. Get an
            // approximation by just dropping type arguments, to enable exiting early before
            // creating a codebase item if there's a definite match.
            // It would be nice to use the ClassUtil.getAsmMethodSignature helper here, but it
            // drops type variables completely.
            val semiErasedSignature =
                psiMethod.psiParameters.joinToString { it.type.canonicalText.dropTypeArguments() }
            // Check if there's a signature match (technically, it would be possible to find a
            // false match here if a type variable that is in semiErasedSignature had the same
            // name as a primitive type used in one of the potential matches, but that shouldn't
            // be allowed).
            if (potentialMatches.any { it == semiErasedSignature }) {
                continue
            }

            // Create the item.
            val callableItem =
                if (psiMethod.isConstructor) {
                    PsiConstructorItem.create(
                        codebase,
                        classItem,
                        psiMethod,
                        classTypeItemFactory,
                        targetLanguages = TargetLanguageSet.BYTECODE_ONLY,
                    )
                } else {
                    PsiMethodItem.create(
                            codebase,
                            classItem,
                            psiMethod,
                            classTypeItemFactory,
                            targetLanguages = TargetLanguageSet.BYTECODE_ONLY,
                        )
                        .takeUnless {
                            // Skip enum synthetic methods since we don't track those.
                            it.isEnumSyntheticMethod()
                        }
                } ?: continue

            // Double check that there isn't already a callable with the same signature. The
            // previous check didn't replace variable types with their bounds, so now that it is
            // easy to do that, make sure there isn't a matching signature.
            if (potentialMatches.isNotEmpty()) {
                val erasedSignature =
                    callableItem.parameters().joinToString { it.type().toErasedTypeString() }
                if (
                    erasedSignature != semiErasedSignature &&
                        potentialMatches.any { it == erasedSignature }
                )
                    continue
            }

            // Add the constructed callable.
            when (callableItem) {
                is ConstructorItem -> classItem.addConstructor(callableItem)
                is MethodItem -> classItem.addMethod(callableItem)
            }
        }
    }

    /**
     * Whether an item with the given [methodName] should not be included in API tracking
     *
     * Value classes have equals, toString, and hashCode `-impl` methods which we don't track
     * because they are common to all value classes.
     *
     * The `$` is used for mangled names of internal elements (which are not @PublishedApi), and
     * delegate and lambda generated elements used by the class itself but not external callers, so
     * they don't need to be tracked.
     */
    private fun skipTracking(methodName: String) =
        methodName == "equals-impl" ||
            methodName == "equals-impl0" ||
            methodName == "toString-impl" ||
            methodName == "hashCode-impl" ||
            methodName.contains('$')

    /** Removes type arguments (anything between "<" and ">") from the type string. */
    private fun String.dropTypeArguments(): String =
        substringBefore("<") + substringAfterLast(">", "")

    /**
     * Finds callables of the [classItem] that might have the same signature as the [psiMethod]
     * (those that have the same name and parameter count), and return their erased signatures.
     */
    private fun erasedSignaturesOfPotentialMatchingCallables(
        psiMethod: PsiMethod,
        classItem: ClassItem
    ): List<String> {
        val callables =
            if (psiMethod.isConstructor) {
                classItem.constructors()
            } else {
                classItem.methods()
            }
        return callables
            .filter { callable ->
                callable.name() == psiMethod.name &&
                    callable.parameters().size == psiMethod.parameters.size
            }
            .map { callable ->
                callable.parameters().joinToString { it.type().toErasedTypeString() }
            }
    }
}
