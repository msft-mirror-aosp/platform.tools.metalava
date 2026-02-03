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

package com.android.tools.metalava.model.psi

import com.android.tools.metalava.model.SourceLanguage
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.KotlinLanguage

// This file contains extension functions and properties on PsiElement and related classes that are
// needed across multiple classes.

/** Get the [SourceLanguage] for this [PsiElement]. */
val PsiElement.sourceLanguage
    get() = if (isKotlin()) SourceLanguage.KOTLIN else SourceLanguage.JAVA

/** Check whether this [PsiElement] is Kotlin or not. */
fun PsiElement.isKotlin(): Boolean {
    return language === KotlinLanguage.INSTANCE
}

/**
 * Get the simple name of a named class or type parameter.
 *
 * A [PsiClass] is used to represent named classes, type parameters, anonymous and local classes.
 * So, its [PsiClass.getName] can sometimes be `null`. However, Metalava only gets the name for
 * named classes and type parameters which never return `null`. So, this extension property forces
 * it to be non-null.
 */
internal val PsiClass.simpleName
    get() = name!!

/**
 * Get the qualified name of a name class.
 *
 * A [PsiClass] is used to represent named classes, type parameters, anonymous and local classes.
 * So, its [PsiClass.getQualifiedName] can sometimes be `null`. However, Metalava only gets the
 * qualified name for name classes which never return `null`. So, this extension property forces it
 * to be non-null.
 */
internal val PsiClass.classQualifiedName
    get() = qualifiedName!!

/** Get the package name from [PsiClass], returning the empty string for an unqualified class. */
val PsiClass.packageName
    get(): String {
        var top: PsiClass? = this
        while (top?.containingClass != null) {
            top = top.containingClass
        }
        top ?: return ""

        val simpleName = top.simpleName
        val qualifiedName = top.classQualifiedName

        if (simpleName == qualifiedName) {
            return ""
        }

        return qualifiedName.substring(0, qualifiedName.length - 1 - simpleName.length)
    }
