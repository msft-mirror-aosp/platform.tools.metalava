/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.metalava

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.DelegatedVisitor
import com.android.tools.metalava.model.ExceptionTypeItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.JAVA_LANG_ANNOTATION
import com.android.tools.metalava.model.JAVA_LANG_ENUM
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.psi.CodePrinter
import com.android.tools.metalava.model.visitors.ApiVisitor
import com.android.tools.metalava.model.visitors.FilteringApiVisitor
import com.android.utils.XmlUtils
import java.io.PrintWriter
import java.util.function.Predicate

/**
 * Writes out an XML format in the JDiff schema: See $ANDROID/external/jdiff/src/api.xsd (though
 * limited to the same subset as generated by Doclava; and using the same conventions for the
 * unspecified parts of the schema, such as what value to put in the deprecated string. It also uses
 * the same XML formatting.)
 *
 * Known differences: Doclava seems to skip enum fields. We don't do that. Doclava seems to skip
 * type parameters; we do the same.
 */
class JDiffXmlWriter(
    private val writer: PrintWriter,
    private val apiName: String? = null,
) : DelegatedVisitor {

    override fun visitCodebase(codebase: Codebase) {
        writer.print("<api")

        if (apiName != null) {
            // See JDiff's XMLToAPI#nameAPI
            writer.print(" name=\"")
            writer.print(apiName)
            writer.print("\"")
        }

        // Specify metalava schema used for metalava:enumConstant
        writer.print(" xmlns:metalava=\"http://www.android.com/metalava/\"")

        writer.println(">")
    }

    override fun afterVisitCodebase(codebase: Codebase) {
        writer.println("</api>")
    }

    override fun visitPackage(pkg: PackageItem) {
        // Note: we apparently don't write package annotations anywhere
        writer.println("<package name=\"${pkg.qualifiedName()}\"\n>")
    }

    override fun afterVisitPackage(pkg: PackageItem) {
        writer.println("</package>")
    }

    override fun visitClass(cls: ClassItem) {
        writer.print('<')
        // XML format does not seem to special case annotations or enums
        if (cls.isInterface()) {
            writer.print("interface")
        } else {
            writer.print("class")
        }
        writer.print(" name=\"")
        writer.print(cls.fullName())
        // Note - to match doclava we don't write out the type parameter list
        // (cls.typeParameterList()) in JDiff files!
        writer.print("\"")

        writeSuperClassAttribute(cls)

        val modifiers = cls.modifiers
        writer.print("\n abstract=\"")
        writer.print(modifiers.isAbstract())
        writer.print("\"\n static=\"")
        writer.print(modifiers.isStatic())
        writer.print("\"\n final=\"")
        writer.print(modifiers.isFinal())
        writer.print("\"\n deprecated=\"")
        writer.print(deprecation(cls))
        writer.print("\"\n visibility=\"")
        writer.print(modifiers.getVisibilityModifiers())
        writer.println("\"\n>")

        writeInterfaceList(cls)
    }

    fun deprecation(item: Item): String {
        return if (item.originallyDeprecated) {
            "deprecated"
        } else {
            "not deprecated"
        }
    }

    override fun afterVisitClass(cls: ClassItem) {
        writer.print("</")
        if (cls.isInterface()) {
            writer.print("interface")
        } else {
            writer.print("class")
        }
        writer.println(">")
    }

    override fun visitConstructor(constructor: ConstructorItem) {
        val modifiers = constructor.modifiers
        writer.print("<constructor name=\"")
        writer.print(constructor.containingClass().fullName())
        writer.print("\"\n type=\"")
        writer.print(constructor.containingClass().qualifiedName())
        writer.print("\"\n static=\"")
        writer.print(modifiers.isStatic())
        writer.print("\"\n final=\"")
        writer.print(modifiers.isFinal())
        writer.print("\"\n deprecated=\"")
        writer.print(deprecation(constructor))
        writer.print("\"\n visibility=\"")
        writer.print(modifiers.getVisibilityModifiers())
        writer.println("\"\n>")

        // Note - to match doclava we don't write out the type parameter list
        // (constructor.typeParameterList()) in JDiff files!

        writeParameterList(constructor)
        writeThrowsList(constructor)
        writer.println("</constructor>")
    }

    override fun visitField(field: FieldItem) {
        val modifiers = field.modifiers
        val initialValue = field.initialValue(true)
        val value =
            if (initialValue != null) {
                XmlUtils.toXmlAttributeValue(CodePrinter.constantToSource(initialValue))
            } else null

        writer.print("<field name=\"")
        writer.print(field.name())
        writer.print("\"\n type=\"")
        writer.print(XmlUtils.toXmlAttributeValue(formatType(field.type())))
        writer.print("\"\n transient=\"")
        writer.print(modifiers.isTransient())
        writer.print("\"\n volatile=\"")
        writer.print(modifiers.isVolatile())
        if (value != null) {
            writer.print("\"\n value=\"")
            writer.print(value)
        }

        writer.print("\"\n static=\"")
        writer.print(modifiers.isStatic())
        writer.print("\"\n final=\"")
        writer.print(modifiers.isFinal())
        writer.print("\"\n deprecated=\"")
        writer.print(deprecation(field))
        writer.print("\"\n visibility=\"")
        writer.print(modifiers.getVisibilityModifiers())
        writer.print("\"")
        if (field.isEnumConstant()) {
            // Metalava extension. JDiff doesn't support it.
            writer.print("\n metalava:enumConstant=\"true\"")
        }
        writer.println("\n>\n</field>")
    }

    override fun visitProperty(property: PropertyItem) {
        // Not supported by JDiff
    }

    override fun visitMethod(method: MethodItem) {
        val modifiers = method.modifiers

        // Note - to match doclava we don't write out the type parameter list
        // (method.typeParameterList()) in JDiff files!

        writer.print("<method name=\"")
        writer.print(method.name())
        method.returnType().let {
            writer.print("\"\n return=\"")
            writer.print(XmlUtils.toXmlAttributeValue(formatType(it)))
        }
        writer.print("\"\n abstract=\"")
        writer.print(modifiers.isAbstract())
        writer.print("\"\n native=\"")
        writer.print(modifiers.isNative())
        writer.print("\"\n synchronized=\"")
        writer.print(modifiers.isSynchronized())
        writer.print("\"\n static=\"")
        writer.print(modifiers.isStatic())
        writer.print("\"\n final=\"")
        writer.print(modifiers.isFinal())
        writer.print("\"\n deprecated=\"")
        writer.print(deprecation(method))
        writer.print("\"\n visibility=\"")
        writer.print(modifiers.getVisibilityModifiers())
        writer.println("\"\n>")

        writeParameterList(method)
        writeThrowsList(method)
        writer.println("</method>")
    }

    private fun writeSuperClassAttribute(cls: ClassItem) {
        val superClass = cls.superClassType()
        val superClassString =
            when {
                cls.isAnnotationType() -> JAVA_LANG_ANNOTATION
                superClass != null -> {
                    // doclava seems to include java.lang.Object for classes but not interfaces
                    if (!cls.isClass() && superClass.isJavaLangObject()) {
                        return
                    }
                    XmlUtils.toXmlAttributeValue(formatType(superClass.toTypeString()))
                }
                cls.isEnum() -> JAVA_LANG_ENUM
                else -> return
            }
        writer.print("\n extends=\"")
        writer.print(superClassString)
        writer.print("\"")
    }

    private fun writeInterfaceList(cls: ClassItem) {
        val interfaces = cls.interfaceTypes()
        if (interfaces.isNotEmpty()) {
            interfaces.forEach { item ->
                writer.print("<implements name=\"")
                val type = item.toTypeString()
                writer.print(XmlUtils.toXmlAttributeValue(formatType(type)))
                writer.println("\">\n</implements>")
            }
        }
    }

    private fun writeParameterList(method: MethodItem) {
        method.parameters().asSequence().forEach { parameter ->
            // NOTE: We report parameter name as "null" rather than the real name to match
            // doclava's behavior
            writer.print("<parameter name=\"null\" type=\"")
            writer.print(XmlUtils.toXmlAttributeValue(formatType(parameter.type())))
            writer.println("\">")
            writer.println("</parameter>")
        }
    }

    private fun formatType(type: TypeItem): String = formatType(type.toTypeString())

    private fun formatType(typeString: String): String {
        // In JDiff we always want to include spaces after commas; the API signature tests depend
        // on this.
        return typeString.replace(",", ", ").replace(",  ", ", ")
    }

    private fun writeThrowsList(method: MethodItem) {
        val throws = method.throwsTypes()
        if (throws.isNotEmpty()) {
            throws.sortedWith(ExceptionTypeItem.fullNameComparator).forEach { type ->
                writer.print("<exception name=\"")
                @Suppress("DEPRECATION") writer.print(type.fullName())
                writer.print("\" type=\"")
                writer.print(type.toTypeString())
                writer.println("\">")
                writer.println("</exception>")
            }
        }
    }

    /**
     * Create an [ApiVisitor] that will filter the [Item] to which is applied according to the
     * supplied parameters and in a manner appropriate for writing signatures, e.g. not nesting
     * classes. It will delegate any visitor calls that pass through its filter to this
     * [JDiffXmlWriter] instance.
     */
    fun createFilteringVisitor(
        filterEmit: Predicate<Item>,
        filterReference: Predicate<Item>,
        preFiltered: Boolean,
        showUnannotated: Boolean,
        filterSuperClassType: Boolean = true,
    ): ApiVisitor =
        FilteringApiVisitor(
            this,
            preserveClassNesting = false,
            inlineInheritedFields = true,
            interfaceListComparator = TypeItem.totalComparator,
            filterEmit = filterEmit,
            filterReference = filterReference,
            preFiltered = preFiltered,
            filterSuperClassType = filterSuperClassType,
            showUnannotated = showUnannotated,
            config = ApiVisitor.Config(),
        )
}
