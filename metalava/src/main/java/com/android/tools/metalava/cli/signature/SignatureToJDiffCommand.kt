/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.tools.metalava.cli.signature

import com.android.tools.metalava.ApiType
import com.android.tools.metalava.CodebaseComparator
import com.android.tools.metalava.ComparisonVisitor
import com.android.tools.metalava.DefaultAnnotationManager
import com.android.tools.metalava.JDiffXmlWriter
import com.android.tools.metalava.OptionsDelegate
import com.android.tools.metalava.cli.common.MetalavaSubCommand
import com.android.tools.metalava.cli.common.SignatureFileLoader
import com.android.tools.metalava.cli.common.existingFile
import com.android.tools.metalava.cli.common.newFile
import com.android.tools.metalava.cli.common.progressTracker
import com.android.tools.metalava.createReportFile
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassResolver
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.text.ReferenceResolver
import com.android.tools.metalava.model.text.ResolverContext
import com.android.tools.metalava.model.text.SourcePositionInfo
import com.android.tools.metalava.model.text.TextClassItem
import com.android.tools.metalava.model.text.TextCodebase
import com.android.tools.metalava.model.text.TextConstructorItem
import com.android.tools.metalava.model.text.TextFieldItem
import com.android.tools.metalava.model.text.TextMethodItem
import com.android.tools.metalava.model.text.TextPackageItem
import com.android.tools.metalava.model.text.TextPropertyItem
import com.android.tools.metalava.model.visitors.ApiVisitor
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import java.io.File

class SignatureToJDiffCommand :
    MetalavaSubCommand(
        help =
            """
                Convert an API signature file into a file in the JDiff XML format.
            """
                .trimIndent()
    ) {

    private val strip by
        option(
                help =
                    """
                        Determines whether duplicate inherited methods should be stripped from the
                        output or not.
                    """
                        .trimIndent()
            )
            .flag("--no-strip", default = false, defaultForHelp = "false")

    private val baseApiFile by
        option(
                "--base-api",
                metavar = "<base-api-file>",
                help =
                    """
                        Optional base API file. If provided then the output will only include API
                        items that are not in this file.
                    """
                        .trimIndent()
            )
            .existingFile()

    private val apiFile by
        argument(
                name = "<api-file>",
                help =
                    """
                        API signature file to convert to the JDiff XML format.
                    """
                        .trimIndent()
            )
            .existingFile()

    private val xmlFile by
        argument(
                name = "<xml-file>",
                help =
                    """
                        Output JDiff XML format file.
                    """
                        .trimIndent()
            )
            .newFile()

    override fun run() {
        // Make sure that none of the code called by this command accesses the global `options`
        // property.
        OptionsDelegate.disallowAccess()

        val annotationManager = DefaultAnnotationManager()
        val signatureApi = SignatureFileLoader.load(apiFile, annotationManager = annotationManager)

        val apiVisitorConfig = ApiVisitor.Config()
        val apiPredicateConfig = apiVisitorConfig.apiPredicateConfig
        val apiType = ApiType.ALL
        val apiEmit = apiType.getEmitFilter(apiPredicateConfig)
        val strip = strip
        val apiReference =
            if (strip) apiType.getEmitFilter(apiPredicateConfig)
            else apiType.getReferenceFilter(apiPredicateConfig)
        val baseFile = baseApiFile

        val outputApi =
            if (baseFile != null) {
                // Convert base on a diff
                val baseApi =
                    SignatureFileLoader.load(baseFile, annotationManager = annotationManager)
                computeDelta(baseFile, baseApi, signatureApi, apiVisitorConfig)
            } else {
                signatureApi
            }

        // See JDiff's XMLToAPI#nameAPI
        val apiName = xmlFile.nameWithoutExtension.replace(' ', '_')
        createReportFile(progressTracker, outputApi, xmlFile, "JDiff File") { printWriter ->
            JDiffXmlWriter(
                printWriter,
                apiEmit,
                apiReference,
                signatureApi.preFiltered && !strip,
                apiName,
                showUnannotated = false,
                ApiVisitor.Config(),
            )
        }
    }
}

/**
 * Create a [TextCodebase] that is a delta between [baseApi] and [signatureApi], i.e. it includes
 * all the [Item] that are in [signatureApi] but not in [baseApi].
 *
 * This is expected to be used where [signatureApi] is a super set of [baseApi] but that is not
 * enforced. If [baseApi] contains [Item]s which are not present in [signatureApi] then they will
 * not appear in the delta.
 *
 * [ClassItem]s are treated specially. If [signatureApi] and [baseApi] have [ClassItem]s with the
 * same name and [signatureApi]'s has members which are not present in [baseApi]'s then a
 * [ClassItem] containing the additional [signatureApi] members will appear in the delta, otherwise
 * it will not.
 *
 * @param baseFile the [Codebase.location] used for the resulting delta.
 * @param baseApi the base [Codebase] whose [Item]s will not appear in the delta.
 * @param signatureApi the extending [Codebase] whose [Item]s will appear in the delta as long as
 *   they are not part of [baseApi].
 */
private fun computeDelta(
    baseFile: File,
    baseApi: Codebase,
    signatureApi: Codebase,
    apiVisitorConfig: ApiVisitor.Config,
): TextCodebase {
    // Compute just the delta
    val delta = TextCodebase(baseFile, signatureApi.annotationManager)
    delta.description = "Delta between $baseApi and $signatureApi"

    CodebaseComparator(apiVisitorConfig = apiVisitorConfig)
        .compare(
            object : ComparisonVisitor() {
                override fun added(new: PackageItem) {
                    delta.addPackage(new as TextPackageItem)
                }

                override fun added(new: ClassItem) {
                    val pkg = getOrAddPackage(new.containingPackage().qualifiedName())
                    pkg.addClass(new as TextClassItem)
                }

                override fun added(new: ConstructorItem) {
                    val cls = getOrAddClass(new.containingClass())
                    cls.addConstructor(new as TextConstructorItem)
                }

                override fun added(new: MethodItem) {
                    val cls = getOrAddClass(new.containingClass())
                    cls.addMethod(new as TextMethodItem)
                }

                override fun added(new: FieldItem) {
                    val cls = getOrAddClass(new.containingClass())
                    cls.addField(new as TextFieldItem)
                }

                override fun added(new: PropertyItem) {
                    val cls = getOrAddClass(new.containingClass())
                    cls.addProperty(new as TextPropertyItem)
                }

                private fun getOrAddClass(fullClass: ClassItem): TextClassItem {
                    val cls = delta.findClass(fullClass.qualifiedName())
                    if (cls != null) {
                        return cls
                    }
                    val textClass = fullClass as TextClassItem
                    val newClass =
                        TextClassItem(
                            delta,
                            SourcePositionInfo.UNKNOWN,
                            textClass.modifiers,
                            textClass.isInterface(),
                            textClass.isEnum(),
                            textClass.isAnnotationType(),
                            textClass.qualifiedName,
                            textClass.qualifiedName,
                            textClass.name,
                            textClass.annotations,
                            textClass.typeParameterList
                        )
                    val pkg = getOrAddPackage(fullClass.containingPackage().qualifiedName())
                    pkg.addClass(newClass)
                    newClass.setContainingPackage(pkg)
                    delta.registerClass(newClass)
                    return newClass
                }

                private fun getOrAddPackage(pkgName: String): TextPackageItem {
                    val pkg = delta.findPackage(pkgName)
                    if (pkg != null) {
                        return pkg
                    }
                    val newPkg =
                        TextPackageItem(
                            delta,
                            pkgName,
                            DefaultModifierList(delta, DefaultModifierList.PUBLIC),
                            SourcePositionInfo.UNKNOWN
                        )
                    delta.addPackage(newPkg)
                    return newPkg
                }
            },
            baseApi,
            signatureApi,
            ApiType.ALL.getReferenceFilter(apiVisitorConfig.apiPredicateConfig)
        )

    // As the delta has not been created by the parser there is no parser provided
    // context to use so just use an empty context.
    val context =
        object : ResolverContext {
            override fun namesOfInterfaces(cl: TextClassItem): List<String>? = null

            override fun nameOfSuperClass(cl: TextClassItem): String? = null

            override val classResolver: ClassResolver? = null
        }

    // All this actually does is add in an appropriate super class depending on the class
    // type.
    ReferenceResolver.resolveReferences(context, delta)
    return delta
}
