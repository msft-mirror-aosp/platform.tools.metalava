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

package com.android.tools.metalava.model.annotation.binding

import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.annotation.AnnotationDefaults
import com.android.tools.metalava.reporter.Issues
import kotlin.reflect.KClass
import kotlin.reflect.KFunction

/**
 * Binder that can create an instance of [kClass] from an [AnnotationItem] that uses the
 * [annotationDefaults].
 */
internal class AnnotationBinding<T : Any>(
    private val kClass: KClass<T>,
    private val annotationDefaults: AnnotationDefaults?,
) : AnnotationBindingFactory<T> {
    /**
     * The [KFunction] representing the constructor that will be used to create the instance of
     * [kClass].
     */
    private val bindingConstructor = kClass.selectBindingConstructor()

    override fun createInstanceFrom(
        annotationItem: AnnotationItem,
        item: Item,
    ): T? {
        // Construct an instance passing in parameters by name. While great care is taken to avoid
        // it this could throw an exception if there is some inconsistency between the annotation
        // definition/use and the binding constructor parameters. If the exception could only be
        // caused by errors within Metalava then it would be acceptable to have it abort processing
        // but this could possibly be caused by changes to sources too. Aborting processing in that
        // case would not be helpful so this catches it and reports it, returning `null` in the
        // process.
        return try {
            bindingConstructor.callBy(emptyMap())
        } catch (e: Exception) {
            item.codebase.reporter.report(
                Issues.INVALID_ANNOTATION_BINDING,
                item,
                "internal error: could not bind ${kClass.qualifiedName} to ${annotationItem.qualifiedName}: ${e.message}",
                location = annotationItem.fileLocation
            )
            null
        }
    }

    companion object {
        /**
         * Select the binding constructor to use on this [KClass].
         *
         * If [KClass] only has a single constructor that is returned. Otherwise, the constructor
         * that is annotated with [BindingConstructor] is returned.
         */
        private fun <T : Any> KClass<T>.selectBindingConstructor(): KFunction<T> {
            val constructors = constructors
            return when (constructors.size) {
                0 ->
                    error(
                        "Cannot create an instance of ${this.qualifiedName} as it has no constructors"
                    )
                1 -> constructors.single()
                else -> {
                    val annotated =
                        constructors.filter { constructor ->
                            constructor.annotations.any { annotation ->
                                annotation is BindingConstructor
                            }
                        }
                    when (annotated.size) {
                        0 ->
                            error(
                                "Found multiple constructors in ${this.qualifiedName}; please annotate one with @BindingConstructor"
                            )
                        1 -> annotated.single()
                        else ->
                            error(
                                "Found multiple constructors in ${this.qualifiedName} that are annotated with @BindingConstructor, please annotate only one"
                            )
                    }
                }
            }
        }
    }
}
