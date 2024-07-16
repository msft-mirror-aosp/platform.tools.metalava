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

package com.android.tools.metalava.model.psi

import com.android.tools.metalava.model.CallableBody
import com.android.tools.metalava.model.ClassItem
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UThrowExpression
import org.jetbrains.uast.UTryExpression
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.visitor.AbstractUastVisitor

class PsiCallableBody(private val callable: PsiCallableItem) : CallableBody {

    /**
     * Access [codebase] on demand as [callable] is not properly initialized during initialization
     * of this class.
     */
    private val codebase
        get() = callable.codebase

    /**
     * Access [psiMethod] on demand as [callable] is not properly initialized during initialization
     * of this class.
     */
    private val psiMethod
        get() = callable.psiMethod

    override fun findThrownExceptions(): Set<ClassItem> {
        if (!callable.isKotlin()) {
            return emptySet()
        }

        val exceptions = mutableSetOf<ClassItem>()

        val method = psiMethod as? UMethod ?: return emptySet()
        method.accept(
            object : AbstractUastVisitor() {
                override fun visitThrowExpression(node: UThrowExpression): Boolean {
                    val type = node.thrownExpression.getExpressionType()
                    if (type != null) {
                        val typeItemFactory = codebase.globalTypeItemFactory.from(callable)
                        val exceptionClass = typeItemFactory.getType(type).asClass()
                        if (exceptionClass != null && !isCaught(exceptionClass, node)) {
                            exceptions.add(exceptionClass)
                        }
                    }
                    return super.visitThrowExpression(node)
                }

                private fun isCaught(exceptionClass: ClassItem, node: UThrowExpression): Boolean {
                    var current: UElement = node
                    while (true) {
                        val tryExpression =
                            current.getParentOfType<UTryExpression>(
                                UTryExpression::class.java,
                                true,
                                UMethod::class.java
                            )
                                ?: return false

                        for (catchClause in tryExpression.catchClauses) {
                            for (type in catchClause.types) {
                                val qualifiedName = type.canonicalText
                                if (exceptionClass.extends(qualifiedName)) {
                                    return true
                                }
                            }
                        }

                        current = tryExpression
                    }
                }
            }
        )

        return exceptions
    }
}
