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

import com.android.tools.metalava.model.CallableItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.ExceptionTypeItem
import com.android.tools.metalava.model.ItemDocumentationFactory
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.type.MethodFingerprint
import com.android.tools.metalava.reporter.FileLocation
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import org.jetbrains.kotlin.name.JvmStandardClassIds
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UThrowExpression
import org.jetbrains.uast.UTryExpression
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.visitor.AbstractUastVisitor

/**
 * A lamda that given a [CallableItem] will create a list of [ParameterItem]s for it.
 *
 * This is called from within the constructor of the [ParameterItem.containingCallable] and can only
 * access the [CallableItem.name] (to identify callables that have special nullability rules) and
 * store a reference to it in [ParameterItem.containingCallable]. In particularly, it must not
 * access [CallableItem.parameters] as that will not yet have been initialized when this is called.
 */
internal typealias ParameterItemsFactory = (PsiCallableItem) -> List<PsiParameterItem>

abstract class PsiCallableItem(
    codebase: PsiBasedCodebase,
    val psiMethod: PsiMethod,
    fileLocation: FileLocation = PsiFileLocation(psiMethod),
    // Takes ClassItem as this may be duplicated from a PsiBasedCodebase on the classpath into a
    // TextClassItem.
    containingClass: ClassItem,
    name: String,
    modifiers: DefaultModifierList,
    documentationFactory: ItemDocumentationFactory,
    protected var returnType: TypeItem,
    parameterItemsFactory: ParameterItemsFactory,
    override val typeParameterList: TypeParameterList,
    protected val throwsTypes: List<ExceptionTypeItem>
) :
    PsiMemberItem(
        codebase = codebase,
        modifiers = modifiers,
        documentationFactory = documentationFactory,
        element = psiMethod,
        fileLocation = fileLocation,
        containingClass = containingClass,
        name = name,
    ),
    CallableItem {

    /**
     * Create the [ParameterItem] list during initialization of this method to allow them to contain
     * an immutable reference to this object.
     *
     * The leaking of `this` to `parameterItemsFactory` is ok as implementations follow the rules
     * explained in the documentation of [ParameterItemsFactory].
     */
    @Suppress("LeakingThis") protected val parameters = parameterItemsFactory(this)

    override fun returnType(): TypeItem = returnType

    override fun setType(type: TypeItem) {
        returnType = type
    }

    override fun parameters(): List<PsiParameterItem> = parameters

    override fun psi() = psiMethod

    override fun throwsTypes() = throwsTypes

    override fun findThrownExceptions(): Set<ClassItem> {
        val method = psiMethod as? UMethod ?: return emptySet()
        if (!isKotlin()) {
            return emptySet()
        }

        val exceptions = mutableSetOf<ClassItem>()

        method.accept(
            object : AbstractUastVisitor() {
                override fun visitThrowExpression(node: UThrowExpression): Boolean {
                    val type = node.thrownExpression.getExpressionType()
                    if (type != null) {
                        val typeItemFactory =
                            codebase.globalTypeItemFactory.from(this@PsiCallableItem)
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

    internal fun areAllParametersOptional(): Boolean {
        for (param in parameters) {
            if (!param.hasDefaultValue()) {
                return false
            }
        }
        return true
    }

    override fun shouldExpandOverloads(): Boolean {
        val ktFunction = (psiMethod as? UMethod)?.sourcePsi as? KtFunction ?: return false
        return modifiers.isActual() &&
            psiMethod.hasAnnotation(JvmStandardClassIds.JVM_OVERLOADS_FQ_NAME.asString()) &&
            // It is /technically/ invalid to have actual functions with default values, but
            // some places suppress the compiler error, so we should handle it here too.
            ktFunction.valueParameters.none { it.hasDefaultValue() } &&
            parameters.any { it.hasDefaultValue() }
    }

    companion object {
        internal fun parameterList(
            containingCallable: PsiCallableItem,
            enclosingTypeItemFactory: PsiTypeItemFactory,
        ): List<PsiParameterItem> {
            val psiParameters = containingCallable.psiMethod.psiParameters
            val fingerprint = MethodFingerprint(containingCallable.name, psiParameters.size)
            return psiParameters.mapIndexed { index, parameter ->
                PsiParameterItem.create(
                    containingCallable,
                    fingerprint,
                    parameter,
                    index,
                    enclosingTypeItemFactory
                )
            }
        }

        internal fun throwsTypes(
            psiMethod: PsiMethod,
            enclosingTypeItemFactory: PsiTypeItemFactory,
        ): List<ExceptionTypeItem> {
            val throwsClassTypes = psiMethod.throwsList.referencedTypes
            if (throwsClassTypes.isEmpty()) {
                return emptyList()
            }

            return throwsClassTypes
                // Convert the PsiType to an ExceptionTypeItem and wrap it in a ThrowableType.
                .map { psiType -> enclosingTypeItemFactory.getExceptionType(PsiTypeInfo(psiType)) }
                // We're sorting the names here even though outputs typically do their own sorting,
                // since for example the MethodItem.sameSignature check wants to do an
                // element-by-element comparison to see if the signature matches, and that should
                // match overrides even if they specify their elements in different orders.
                .sortedWith(ExceptionTypeItem.fullNameComparator)
        }
    }
}

/** Get the [PsiParameter]s for a [PsiMethod]. */
val PsiMethod.psiParameters: List<PsiParameter>
    get() = if (this is UMethod) uastParameters else parameterList.parameters.toList()
