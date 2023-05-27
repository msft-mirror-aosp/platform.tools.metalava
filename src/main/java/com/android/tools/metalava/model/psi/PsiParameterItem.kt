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

import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.model.psi.CodePrinter.Companion.constantToSource
import com.intellij.psi.LambdaUtil
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiEllipsisType
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiPrimitiveType
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.builtins.isFunctionOrKFunctionTypeWithAnySuspendability
import org.jetbrains.kotlin.load.java.sam.JavaSingleAbstractMethodUtils
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.resolve.typeBinding.createTypeBindingForReturnType
import org.jetbrains.kotlin.types.typeUtil.TypeNullability
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.kotlin.psi.UastKotlinPsiParameter

class PsiParameterItem(
    override val codebase: PsiBasedCodebase,
    private val psiParameter: PsiParameter,
    private val name: String,
    override val parameterIndex: Int,
    modifiers: PsiModifierItem,
    documentation: String,
    private val type: PsiTypeItem
) : PsiItem(
    codebase = codebase,
    modifiers = modifiers,
    documentation = documentation,
    element = psiParameter
),
    ParameterItem {
    lateinit var containingMethod: PsiMethodItem

    override var property: PsiPropertyItem? = null

    override fun name(): String = name

    override fun publicName(): String? {
        if (isKotlin(psiParameter)) {
            // Omit names of some special parameters in Kotlin. None of these parameters may be
            // set through Kotlin keyword arguments, so there's no need to track their names for
            // compatibility. This also helps avoid signature file churn if PSI or the compiler
            // change what name they're using for these parameters.

            // Receiver parameter of extension function
            if (isReceiver()) {
                return null
            }
            // Property setter parameter
            if (containingMethod.isKotlinProperty()) {
                return null
            }
            // Continuation parameter of suspend function
            if (containingMethod.modifiers.isSuspend() &&
                "kotlin.coroutines.Continuation" == type.asClass()?.qualifiedName() &&
                containingMethod.parameters().size - 1 == parameterIndex
            ) {
                return null
            }
            // UAST workaround: value parameter name for enum synthetic valueOf
            // TODO: won't need this after kotlinc 1.9
            if (containingMethod.isEnumSyntheticValueOf()) {
                return StandardNames.DEFAULT_VALUE_PARAMETER.identifier
            }
            return name
        } else {
            // Java: Look for @ParameterName annotation
            val annotation = modifiers.annotations().firstOrNull { it.isParameterName() }
            if (annotation != null) {
                return annotation.attributes.firstOrNull()?.value?.value()?.toString()
            }
        }

        return null
    }

    override fun hasDefaultValue(): Boolean = isDefaultValueKnown()

    override fun isDefaultValueKnown(): Boolean {
        return if (isKotlin(psiParameter)) {
            getKtParameter()?.hasDefaultValue() ?: false && defaultValue() != INVALID_VALUE
        } else {
            // Java: Look for @ParameterName annotation
            modifiers.annotations().any { it.isDefaultValue() }
        }
    }

    // Note receiver parameter used to be named $receiver in previous UAST versions, now it is $this$functionName
    private fun isReceiver(): Boolean = parameterIndex == 0 && name.startsWith("\$this\$")

    private fun getKtParameter(): KtParameter? {
        val ktParameters =
            ((containingMethod.psiMethod as? UMethod)?.sourcePsi as? KtFunction)?.valueParameters
                ?: return null

        // Perform matching based on parameter names, because indices won't work in the
        // presence of @JvmOverloads where UAST generates multiple permutations of the
        // method from the same KtParameters array.

        // Quick lookup first which usually works (lined up from the end to account
        // for receivers for extension methods etc)
        val rem = containingMethod.parameters().size - parameterIndex
        val index = ktParameters.size - rem
        if (index >= 0) {
            val parameter = ktParameters[index]
            if (parameter.name == name) {
                return parameter
            }
        }

        for (parameter in ktParameters) {
            if (parameter.name == name) {
                return parameter
            }
        }

        // Fallback to handle scenario where the real parameter names are hidden by
        // UAST (see UastKotlinPsiParameter which replaces parameter names to p$index)
        if (index >= 0) {
            val parameter = ktParameters[index]
            if (!isReceiver()) {
                return parameter
            }
        }

        return null
    }

    override val synthetic: Boolean get() = containingMethod.isEnumSyntheticMethod()

    private var defaultValue: String? = null

    override fun defaultValue(): String? {
        if (defaultValue == null) {
            defaultValue = computeDefaultValue()
        }
        return defaultValue
    }

    private fun computeDefaultValue(): String? {
        if (isKotlin(psiParameter)) {
            val ktParameter = getKtParameter() ?: return null
            if (ktParameter.hasDefaultValue()) {
                val defaultValue = ktParameter.defaultValue ?: return null
                if (defaultValue is KtConstantExpression) {
                    return defaultValue.text
                }

                val defaultExpression: UExpression = UastFacade.convertElement(
                    defaultValue, null,
                    UExpression::class.java
                ) as? UExpression ?: return INVALID_VALUE
                val constant = defaultExpression.evaluate()
                return if (constant != null && constant !is Pair<*, *>) {
                    constantToSource(constant)
                } else {
                    // Expression: Compute from UAST rather than just using the source text
                    // such that we can ensure references are fully qualified etc.
                    codebase.printer.toSourceString(defaultExpression)
                }
            }

            return INVALID_VALUE
        } else {
            // Java: Look for @ParameterName annotation
            val annotation = modifiers.annotations().firstOrNull { it.isDefaultValue() }
            if (annotation != null) {
                return annotation.attributes.firstOrNull()?.value?.value()?.toString()
            }
        }

        return null
    }

    override fun type(): TypeItem = type
    override fun containingMethod(): MethodItem = containingMethod

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        return other is ParameterItem && parameterIndex == other.parameterIndex && containingMethod == other.containingMethod()
    }

    override fun hashCode(): Int {
        return parameterIndex
    }

    override fun toString(): String = "parameter ${name()}"

    override fun isVarArgs(): Boolean {
        return psiParameter.isVarArgs || modifiers.isVarArg()
    }

    /**
     * Returns whether this parameter is SAM convertible or a Kotlin lambda. If this parameter is
     * the last parameter, it also means that it could be called in Kotlin using the trailing lambda
     * syntax.
     *
     * Specifically this will attempt to handle the follow cases:
     *
     * - Java SAM interface = true
     * - Kotlin SAM interface = false // Kotlin (non-fun) interfaces are not SAM convertible
     * - Kotlin fun interface = true
     * - Kotlin lambda = true
     * - Any other type = false
     */
    fun isSamCompatibleOrKotlinLambda(): Boolean {
        // Method is defined in Java source
        if (isJava()) {
            // Check the parameter type to see if it is defined in Kotlin or not.
            // Interfaces defined in Kotlin do not support SAM conversion, but `fun` interfaces do.
            // This is a best-effort check, since external dependencies (bytecode) won't appear to
            // be Kotlin, and won't have a `fun` modifier visible. To resolve this, we could parse
            // the kotlin.metadata annotation on the bytecode declaration (and special case
            // kotlin.jvm.functions.Function* since the actual Kotlin lambda type can always be used
            // with trailing lambda syntax), but in reality the amount of Java methods with a Kotlin
            // interface with a single abstract method from an external dependency should be
            // minimal, so just checking source will make this easier to maintain in the future.
            val cls = type.asClass()
            if (cls != null && cls.isKotlin()) {
                return cls.isInterface() && cls.modifiers.isFunctional()
            }
            // Note: this will return `true` if the interface is defined in Kotlin, hence why we
            // need the prior check as well
            return LambdaUtil.isFunctionalType(type.psiType)
            // Method is defined in Kotlin source
        } else {
            // For Kotlin declarations we can re-use the existing utilities for calculating whether
            // a type is SAM convertible or not, which should handle external dependencies better
            // and avoid any divergence from the actual compiler behaviour, if there are changes.
            val parameter = (psi() as? UParameter)?.sourcePsi as? KtParameter ?: return false
            val bindingContext = codebase.bindingContext(parameter)
            if (bindingContext != null) { // FE 1.0
                val type =
                    parameter.createTypeBindingForReturnType(bindingContext)?.type ?: return false
                // True if the type is a SAM type, or a fun interface
                val isSamType = JavaSingleAbstractMethodUtils.isSamType(type)
                // True if the type is a Kotlin lambda (suspend or not)
                val isFunctionalType = type.isFunctionOrKFunctionTypeWithAnySuspendability
                return isSamType || isFunctionalType
            } else { // Analysis API
                analyze(parameter) {
                    val ktType = parameter.getParameterSymbol().returnType
                    val isSamType = ktType.isFunctionalInterfaceType
                    val isFunctionalType =
                        ktType.isFunctionType || ktType.isSuspendFunctionType ||
                            ktType.isKFunctionType || ktType.isKSuspendFunctionType
                    return isSamType || isFunctionalType
                }
            }
        }
    }

    companion object {
        fun create(
            codebase: PsiBasedCodebase,
            psiParameter: PsiParameter,
            parameterIndex: Int
        ): PsiParameterItem {
            val name = psiParameter.name
            val commentText = "" // no javadocs on individual parameters
            val modifiers = createParameterModifiers(codebase, psiParameter, commentText)
            // UAST workaround: nullability/type of parameter for UMethod with fake LC PSI
            // See https://youtrack.jetbrains.com/issue/KTIJ-23837
            // We will be informed when the fix is ready, since use of [TypeNullability] will break
            // as per https://youtrack.jetbrains.com/issue/KTIJ-23603
            val workaroundPsiType =
                if (psiParameter is UParameter &&
                    psiParameter.sourcePsi is KtParameter &&
                    psiParameter.javaPsi is UastKotlinPsiParameter
                ) {
                    val ktParameter = psiParameter.sourcePsi as KtParameter
                    val nullability = codebase.uastResolveService?.nullability(ktParameter)
                    val psiType = codebase.uastResolveService
                        ?.getType(ktParameter, psiParameter.uastParent as? PsiModifierListOwner)
                        ?.let { psiType ->
                            // UAST workaround: retrieval of boxed primitive type, if nullable
                            // See https://youtrack.jetbrains.com/issue/KTIJ-23837
                            if (nullability == TypeNullability.NULLABLE &&
                                psiType is PsiPrimitiveType
                            ) {
                                psiType.getBoxedType(psiParameter)
                            } else {
                                psiType
                            }
                        }
                        ?: psiParameter.type
                    val annotationProvider =
                        when (nullability) {
                            TypeNullability.NOT_NULL -> codebase.getNonNullAnnotationProvider()
                            TypeNullability.NULLABLE -> codebase.getNullableAnnotationProvider()
                            else -> null
                        }
                    if (ktParameter.isVarArg && psiType is PsiArrayType) {
                        val annotatedType = if (annotationProvider != null) {
                            psiType.componentType.annotate(annotationProvider)
                        } else {
                            psiType.componentType
                        }
                        PsiEllipsisType(annotatedType, annotatedType.annotationProvider)
                    } else {
                        if (annotationProvider != null) {
                            psiType.annotate(annotationProvider)
                        } else {
                            psiType
                        }
                    }
                } else {
                    psiParameter.type
                }
            val type = codebase.getType(workaroundPsiType)
            val parameter = PsiParameterItem(
                codebase = codebase,
                psiParameter = psiParameter,
                name = name,
                parameterIndex = parameterIndex,
                documentation = commentText,
                modifiers = modifiers,
                type = type
            )
            parameter.modifiers.setOwner(parameter)
            return parameter
        }

        fun create(
            codebase: PsiBasedCodebase,
            original: PsiParameterItem
        ): PsiParameterItem {
            val parameter = PsiParameterItem(
                codebase = codebase,
                psiParameter = original.psiParameter,
                name = original.name,
                parameterIndex = original.parameterIndex,
                documentation = original.documentation,
                modifiers = PsiModifierItem.create(codebase, original.modifiers),
                type = PsiTypeItem.create(codebase, original.type)
            )
            parameter.modifiers.setOwner(parameter)
            return parameter
        }

        fun create(
            codebase: PsiBasedCodebase,
            original: List<ParameterItem>
        ): List<PsiParameterItem> {
            return original.map { create(codebase, it as PsiParameterItem) }
        }

        private fun createParameterModifiers(
            codebase: PsiBasedCodebase,
            psiParameter: PsiParameter,
            commentText: String
        ): PsiModifierItem {
            val modifiers = PsiModifierItem
                .create(codebase, psiParameter, commentText)
            // Method parameters don't have a visibility level; they are visible to anyone that can
            // call their method. However, Kotlin constructors sometimes appear to specify the
            // visibility of a constructor parameter by putting visibility inside the constructor
            // signature. This is really to indicate that the matching property should have the
            // mentioned visibility.
            // If the method parameter seems to specify a visibility level, we correct it back to
            // the default, here, to ensure we don't attempt to incorrectly emit this information
            // into a signature file.
            modifiers.setVisibilityLevel(VisibilityLevel.PACKAGE_PRIVATE)
            return modifiers
        }

        /**
         * Private marker return value from [#computeDefaultValue] signifying that the parameter
         * has a default value but we were unable to compute a suitable static string representation for it
         */
        private const val INVALID_VALUE = "__invalid_value__"
    }
}
