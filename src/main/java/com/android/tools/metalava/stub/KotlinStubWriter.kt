/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.metalava.stub

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.MemberItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.psi.EXPAND_DOCUMENTATION
import com.android.tools.metalava.model.psi.trimDocIndent
import com.android.tools.metalava.model.visitors.ItemVisitor
import com.android.tools.metalava.options
import java.io.PrintWriter
import java.util.function.Predicate

class KotlinStubWriter(
    private val writer: PrintWriter,
    private val filterEmit: Predicate<Item>,
    private val filterReference: Predicate<Item>,
    private val generateAnnotations: Boolean = false,
    private val preFiltered: Boolean = true,
    private val docStubs: Boolean
) : ItemVisitor() {
    override fun visitClass(cls: ClassItem) {
        if (cls.isTopLevelClass()) {
            val qualifiedName = cls.containingPackage().qualifiedName()
            if (qualifiedName.isNotBlank()) {
                writer.println("package $qualifiedName")
                writer.println()
            }
            @Suppress("ConstantConditionIf")
            if (EXPAND_DOCUMENTATION) {
                val compilationUnit = cls.getCompilationUnit()
                compilationUnit?.getImportStatements(filterReference)?.let {
                    for (item in it) {
                        when (item) {
                            is PackageItem ->
                                writer.println("import ${item.qualifiedName()}.*")
                            is ClassItem ->
                                writer.println("import ${item.qualifiedName()}")
                            is MemberItem ->
                                writer.println("import static ${item.containingClass().qualifiedName()}.${item.name()}")
                        }
                    }
                    writer.println()
                }
            }
        }
        appendDocumentation(cls, writer)

        writer.println("@file:Suppress(\"ALL\")")

        when {
            cls.isAnnotationType() -> writer.print("annotation class")
            cls.isInterface() -> writer.print("interface")
            cls.isEnum() -> writer.print("enum class")
            else -> writer.print("class")
        }

        writer.print(" ")
        writer.print(cls.simpleName())

        writer.print(" {\n")
    }

    private fun appendDocumentation(item: Item, writer: PrintWriter) {
        if (options.includeDocumentationInStubs || docStubs) {
            val documentation = if (docStubs && EXPAND_DOCUMENTATION) {
                item.fullyQualifiedDocumentation()
            } else {
                item.documentation
            }
            if (documentation.isNotBlank()) {
                val trimmed = trimDocIndent(documentation)
                writer.println(trimmed)
                writer.println()
            }
        }
    }

    override fun afterVisitClass(cls: ClassItem) {
        writer.println("}\n\n")
    }
}