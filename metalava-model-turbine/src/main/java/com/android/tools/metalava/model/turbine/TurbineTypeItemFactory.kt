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

package com.android.tools.metalava.model.turbine

import com.android.tools.metalava.model.BoundsTypeItem
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.ReferenceTypeItem
import com.android.tools.metalava.model.TypeArgumentTypeItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeModifiers
import com.android.tools.metalava.model.TypeNullability
import com.android.tools.metalava.model.TypeParameterScope
import com.android.tools.metalava.model.TypeUse
import com.android.tools.metalava.model.type.DefaultTypeModifiers
import com.google.turbine.model.TurbineConstantTypeKind
import com.google.turbine.type.Type

/** Creates [TurbineTypeItem]s from [Type]s. */
internal class TurbineTypeItemFactory(
    private val codebase: TurbineBasedCodebase,
    private val initializer: TurbineCodebaseInitialiser,
    val typeParameterScope: TypeParameterScope,
) {

    fun nestedFactory(scope: TypeParameterScope): TurbineTypeItemFactory {
        return if (scope === typeParameterScope) this
        else TurbineTypeItemFactory(codebase, initializer, scope)
    }

    /** Create a [BoundsTypeItem]. */
    internal fun createBoundsTypeItem(type: Type) = createType(type, false) as BoundsTypeItem

    /**
     * Creates a [ClassTypeItem] that is suitable for use as a super type, e.g. in an `extends` or
     * `implements` list.
     */
    internal fun createSuperType(type: Type): ClassTypeItem =
        createType(type, false, TypeUse.SUPER_TYPE) as ClassTypeItem

    internal fun createType(
        type: Type,
        isVarArg: Boolean,
        typeUse: TypeUse = TypeUse.GENERAL,
    ): TypeItem {
        return when (val kind = type.tyKind()) {
            Type.TyKind.PRIM_TY -> {
                type as Type.PrimTy
                val annotations = initializer.createAnnotations(type.annos())
                // Primitives are always non-null.
                val modifiers = DefaultTypeModifiers.create(annotations, TypeNullability.NONNULL)
                when (type.primkind()) {
                    TurbineConstantTypeKind.BOOLEAN ->
                        TurbinePrimitiveTypeItem(modifiers, PrimitiveTypeItem.Primitive.BOOLEAN)
                    TurbineConstantTypeKind.BYTE ->
                        TurbinePrimitiveTypeItem(modifiers, PrimitiveTypeItem.Primitive.BYTE)
                    TurbineConstantTypeKind.CHAR ->
                        TurbinePrimitiveTypeItem(modifiers, PrimitiveTypeItem.Primitive.CHAR)
                    TurbineConstantTypeKind.DOUBLE ->
                        TurbinePrimitiveTypeItem(modifiers, PrimitiveTypeItem.Primitive.DOUBLE)
                    TurbineConstantTypeKind.FLOAT ->
                        TurbinePrimitiveTypeItem(modifiers, PrimitiveTypeItem.Primitive.FLOAT)
                    TurbineConstantTypeKind.INT ->
                        TurbinePrimitiveTypeItem(modifiers, PrimitiveTypeItem.Primitive.INT)
                    TurbineConstantTypeKind.LONG ->
                        TurbinePrimitiveTypeItem(modifiers, PrimitiveTypeItem.Primitive.LONG)
                    TurbineConstantTypeKind.SHORT ->
                        TurbinePrimitiveTypeItem(modifiers, PrimitiveTypeItem.Primitive.SHORT)
                    else ->
                        throw IllegalStateException("Invalid primitive type in API surface: $type")
                }
            }
            Type.TyKind.ARRAY_TY -> {
                createArrayType(type as Type.ArrayTy, isVarArg)
            }
            Type.TyKind.CLASS_TY -> {
                type as Type.ClassTy
                var outerClass: TurbineClassTypeItem? = null
                // A ClassTy is represented by list of SimpleClassTY each representing an inner
                // class. e.g. , Outer.Inner.Inner1 will be represented by three simple classes
                // Outer, Outer.Inner and Outer.Inner.Inner1
                for (simpleClass in type.classes()) {
                    // For all outer class types, set the nullability to non-null.
                    outerClass?.modifiers?.setNullability(TypeNullability.NONNULL)
                    outerClass = createSimpleClassType(simpleClass, outerClass, typeUse)
                }
                outerClass!!
            }
            Type.TyKind.TY_VAR -> {
                type as Type.TyVar
                val annotations = initializer.createAnnotations(type.annos())
                val modifiers = DefaultTypeModifiers.create(annotations)
                val typeParameter = typeParameterScope.getTypeParameter(type.sym().name())
                TurbineVariableTypeItem(modifiers, typeParameter)
            }
            Type.TyKind.WILD_TY -> {
                type as Type.WildTy
                val annotations = initializer.createAnnotations(type.annotations())
                // Wildcards themselves don't have a defined nullability.
                val modifiers = DefaultTypeModifiers.create(annotations, TypeNullability.UNDEFINED)
                when (type.boundKind()) {
                    Type.WildTy.BoundKind.UPPER -> {
                        val upperBound = createWildcardBound(type.bound())
                        TurbineWildcardTypeItem(modifiers, upperBound, null)
                    }
                    Type.WildTy.BoundKind.LOWER -> {
                        // LowerBounded types have java.lang.Object as upper bound
                        val upperBound = createWildcardBound(Type.ClassTy.OBJECT)
                        val lowerBound = createWildcardBound(type.bound())
                        TurbineWildcardTypeItem(modifiers, upperBound, lowerBound)
                    }
                    Type.WildTy.BoundKind.NONE -> {
                        // Unbounded types have java.lang.Object as upper bound
                        val upperBound = createWildcardBound(Type.ClassTy.OBJECT)
                        TurbineWildcardTypeItem(modifiers, upperBound, null)
                    }
                    else ->
                        throw IllegalStateException("Invalid wildcard type in API surface: $type")
                }
            }
            Type.TyKind.VOID_TY ->
                TurbinePrimitiveTypeItem(
                    // Primitives are always non-null.
                    DefaultTypeModifiers.create(emptyList(), TypeNullability.NONNULL),
                    PrimitiveTypeItem.Primitive.VOID
                )
            Type.TyKind.NONE_TY ->
                TurbinePrimitiveTypeItem(
                    // Primitives are always non-null.
                    DefaultTypeModifiers.create(emptyList(), TypeNullability.NONNULL),
                    PrimitiveTypeItem.Primitive.VOID
                )
            Type.TyKind.ERROR_TY -> {
                // This is case of unresolved superclass or implemented interface
                type as Type.ErrorTy
                TurbineClassTypeItem(
                    codebase,
                    DefaultTypeModifiers.create(emptyList(), TypeNullability.UNDEFINED),
                    type.name(),
                    emptyList(),
                    null,
                )
            }
            else -> throw IllegalStateException("Invalid type in API surface: $kind")
        }
    }

    private fun createWildcardBound(type: Type) = createType(type, false) as ReferenceTypeItem

    private fun createArrayType(type: Type.ArrayTy, isVarArg: Boolean): TypeItem {
        // For Turbine's ArrayTy, the annotations for multidimentional arrays comes out in reverse
        // order. This method attaches annotations in the correct order by applying them in reverse
        val modifierStack = ArrayDeque<TypeModifiers>()
        var curr: Type = type
        while (curr.tyKind() == Type.TyKind.ARRAY_TY) {
            curr as Type.ArrayTy
            val annotations = initializer.createAnnotations(curr.annos())
            modifierStack.addLast(DefaultTypeModifiers.create(annotations))
            curr = curr.elementType()
        }
        var componentType = createType(curr, false)
        while (modifierStack.isNotEmpty()) {
            val modifiers = modifierStack.removeFirst()
            if (modifierStack.isEmpty()) {
                // Outermost array. Should be called with correct value of isvararg
                componentType = createSimpleArrayType(modifiers, componentType, isVarArg)
            } else {
                componentType = createSimpleArrayType(modifiers, componentType, false)
            }
        }
        return componentType
    }

    private fun createSimpleArrayType(
        modifiers: TypeModifiers,
        componentType: TypeItem,
        isVarArg: Boolean
    ): TypeItem {
        return TurbineArrayTypeItem(modifiers, componentType, isVarArg)
    }

    private fun createSimpleClassType(
        type: Type.ClassTy.SimpleClassTy,
        outerClass: TurbineClassTypeItem?,
        typeUse: TypeUse = TypeUse.GENERAL,
    ): TurbineClassTypeItem {
        // Super types are always NONNULL.
        val nullability = if (typeUse == TypeUse.SUPER_TYPE) TypeNullability.NONNULL else null
        val annotations = initializer.createAnnotations(type.annos())
        val modifiers = DefaultTypeModifiers.create(annotations, nullability)
        val qualifiedName = initializer.getQualifiedName(type.sym().binaryName())
        val parameters = type.targs().map { createType(it, false) as TypeArgumentTypeItem }
        return TurbineClassTypeItem(codebase, modifiers, qualifiedName, parameters, outerClass)
    }
}
