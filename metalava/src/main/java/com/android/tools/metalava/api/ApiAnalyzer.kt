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

package com.android.tools.metalava.api

import com.android.tools.metalava.manifest.Manifest
import com.android.tools.metalava.manifest.emptyManifest
import com.android.tools.metalava.model.ANDROIDX_REQUIRES_PERMISSION
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.BaseItemVisitor
import com.android.tools.metalava.model.BaseTypeVisitor
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ClassOrigin
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.FilterPredicate
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.ItemDocumentation
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.PackageList
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.RecordComponentItem
import com.android.tools.metalava.model.SUPPRESS_COMPATIBILITY_ANNOTATION_QUALIFIED
import com.android.tools.metalava.model.SelectableItem
import com.android.tools.metalava.model.TargetLanguageSet
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.doc.DocContentPredicate
import com.android.tools.metalava.model.source.SourceParser
import com.android.tools.metalava.model.source.doc.DocContentPredicates
import com.android.tools.metalava.model.value.asString
import com.android.tools.metalava.model.visitors.ApiPredicate
import com.android.tools.metalava.model.visitors.ApiVisitor
import com.android.tools.metalava.permission.getRequiresPermissionProxy
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Reporter
import java.io.File
import java.util.Locale

/**
 * The [ApiAnalyzer] is responsible for walking over the various classes and members and compute
 * visibility etc. of the APIs
 */
class ApiAnalyzer(
    private val sourceParser: SourceParser,
    /** The code to analyze */
    private val codebase: Codebase,
    private val reporter: Reporter,
    private val config: Config,
) {

    data class Config(
        val manifest: Manifest = emptyManifest,

        /** Packages to exclude/hide */
        val hidePackages: List<String> = emptyList(),

        /**
         * Packages that we should skip generating even if not hidden; typically only used by tests
         */
        val skipEmitPackages: List<String> = emptyList(),

        /**
         * External annotation files that contain non-inclusion annotations which will appear in the
         * generated API.
         *
         * These will be merged into the codebase.
         */
        val mergeQualifierAnnotations: List<File> = emptyList(),

        /**
         * External annotation files that contain annotations which affect inclusion of items in the
         * API.
         *
         * These will be merged into the codebase.
         */
        val mergeInclusionAnnotations: List<File> = emptyList(),

        /** The API surface name. */
        val apiSurface: String? = null,

        /** Configuration for any [ApiPredicate] instances this needs to create. */
        val apiPredicateConfig: ApiPredicate.Config = ApiPredicate.Config(),

        /** Configuration for [AnnotationsMerger] instances this needs to create. */
        val annotationsMergerConfig: AnnotationsMerger.Config = AnnotationsMerger.Config(),

        /** Determines whether it is necessary to perform the [Issues.UNHIDDEN_SYSTEM_API] check. */
        val needUnhiddenSystemApiCheck: Boolean = true,
    )

    /** All packages in the API */
    private val packages: PackageList = codebase.getPackages()

    fun computeApi() {
        if (codebase.trustedApi()) {
            // The codebase is already an API; no consistency checks to be performed
            return
        }

        skipEmitPackages()
        // Suppress kotlin file facade classes with no public api
        hideEmptyKotlinFileFacadeClasses()

        // Propagate visibility down into individual elements -- if a class is hidden,
        // then the methods and fields are hidden etc
        propagateHiddenRemovedAndDocOnly()
    }

    fun handleFileFacadeClassesAndExperimentalPackages(filterEmit: FilterPredicate) {
        // For classes auto-generated by the Kotlin compiler from a group of top-level functions,
        // if all the top-level functions are marked as experimental then the generated class
        // should also be marked as experimental. For more information, see b/408977387
        addExperimentalAnnotationsToGeneratedClassesIfAllTopLevelItemsExperimental(filterEmit)

        // Mark package as compatibility suppressed if all member classes and child packages
        // are experimental. This is a fix for b/404795417
        suppressPackageIfAllChildrenAreExperimental(filterEmit)
    }

    private fun suppressPackageIfAllChildrenAreExperimental(filterEmit: FilterPredicate) {
        // Sorting the packages in descending order by name length ensures that child
        // packages are processed first, which allows processing packages from bottom-up
        // and prevents the need for recursion and more complicated logic
        packages.packages
            .sortedByDescending { it.qualifiedName().length }
            .forEach { pkg ->
                if (packageContainsOnlyExperimentalItems(pkg, filterEmit)) {
                    val newSuppressCompatibilityAnnotation =
                        AnnotationItem.createMarkerAnnotation(
                            codebase,
                            SUPPRESS_COMPATIBILITY_ANNOTATION_QUALIFIED,
                        )
                    pkg.mutateModifiers { this.addAnnotation(newSuppressCompatibilityAnnotation) }
                }
            }
    }

    /**
     * Mark a package as compatibility suppressed if all contained classes and packages are also
     * compatibility suppressed.
     */
    private fun packageContainsOnlyExperimentalItems(
        pkg: PackageItem,
        filterEmit: FilterPredicate,
    ): Boolean {
        val classesToExamine = pkg.topLevelClasses().filter { filterEmit.test(it) }
        val areAllClassesExperimental =
            classesToExamine.all { topLevelClass -> topLevelClass.isCompatibilitySuppressed() }

        val packagesToExamine = pkg.childPackages().filter { filterEmit.test(it) }
        val areAllSubPackagesExperimental =
            packagesToExamine.all { childPackage -> childPackage.isCompatibilitySuppressed() }

        return (areAllClassesExperimental && areAllSubPackagesExperimental) &&
            !(classesToExamine.isEmpty() && packagesToExamine.isEmpty())
    }

    private fun addExperimentalAnnotationsToGeneratedClassesIfAllTopLevelItemsExperimental(
        filterEmit: FilterPredicate
    ) {
        // make sure that all the methods, properties, and fields in a class are marked with a
        // suppress compatibility annotation, and that the class itself doesn't have any suppress
        // compatibility annotations
        codebase.getTopLevelClassesFromSource().forEach { cls ->
            if (
                cls.isFileFacade &&
                    cls.modifiers.annotations().none { it.isSuppressCompatibilityAnnotation() } &&
                    cls.emit &&
                    allEmittableItemsHaveExperimentalAnnotations(cls.methods(), filterEmit) &&
                    allEmittableItemsHaveExperimentalAnnotations(cls.fields(), filterEmit) &&
                    allEmittableItemsHaveExperimentalAnnotations(cls.properties(), filterEmit)
            ) {
                // add a suppress compatibility annotation to the class

                val newSuppressCompatibilityAnnotation =
                    AnnotationItem.createMarkerAnnotation(
                        codebase,
                        SUPPRESS_COMPATIBILITY_ANNOTATION_QUALIFIED,
                    )
                cls.mutateModifiers { this.addAnnotation(newSuppressCompatibilityAnnotation) }
            }
        }
    }

    private fun allEmittableItemsHaveExperimentalAnnotations(
        items: List<SelectableItem>,
        filterEmit: FilterPredicate
    ): Boolean {
        return items
            .filter { filterEmit.test(it) }
            .all { item ->
                item.modifiers.annotations().any { it.isSuppressCompatibilityAnnotation() }
            }
    }

    /**
     * Inherit hidden aspects of the API.
     *
     * @param filterEmit determines which [SelectableItem] are part of the target API surface.
     * @param filterReference determines which [SelectableItem]s are part of the target API surface
     *   or any API surface it extends.
     */
    fun inheritHiddenAspects(
        filterEmit: FilterPredicate,
        filterReference: FilterPredicate,
    ) {
        // When analyzing libraries we may discover some new classes during traversal; these aren't
        // part of the API but may be super classes or interfaces; these will then be added into the
        // package class lists, which could trigger a concurrent modification, so create a snapshot
        // of the class list and iterate over it:
        val allClasses = packages.allClasses().toList()

        // Visit all the concrete classes checking to see whether it should inherit any hidden
        // methods or interfaces.
        val visited = mutableSetOf<ClassItem>()
        for (classItem in allClasses) {
            // If it is not a class, i.e. an interface, etc., then ignore it.
            if (!classItem.isClass()) continue

            inheritHiddenInterfacesAndConcreteMethods(
                classItem,
                filterEmit,
                filterReference,
                visited,
            )
        }
    }

    /**
     * For [ClassItem], inherit hidden interfaces and concrete methods that implement public
     * interface methods from any of its hidden super types.
     */
    private fun inheritHiddenInterfacesAndConcreteMethods(
        cls: ClassItem,
        filterEmit: FilterPredicate,
        filterReference: FilterPredicate,
        visited: MutableSet<ClassItem>,
    ) {
        // If already visited this class then ignore it. Otherwise, remember that this was visited.
        if (cls in visited) return
        visited += cls

        // If it has no super class then ignore it.
        val superClass = cls.superClass() ?: return

        // If the class is not going to be emitted then do not inherit any methods into it.
        if (!filterEmit.test(cls)) return

        // Make sure that the super class has inherited the methods and interfaces.
        inheritHiddenInterfacesAndConcreteMethods(superClass, filterEmit, filterReference, visited)

        val allSuperClasses = cls.allSuperClasses()
        val hiddenSuperClasses =
            allSuperClasses.filter { !filterReference.test(it) && !it.isJavaLangObject() }

        if (hiddenSuperClasses.none()) { // not missing any implementation methods
            return
        }

        inheritConcreteMethodsFromHiddenClasses(
            cls,
            hiddenSuperClasses,
            allSuperClasses,
            filterEmit,
            filterReference
        )
        inheritInterfacesFromHiddenSuperClasses(cls, hiddenSuperClasses, filterReference)
    }

    /**
     * Add any interfaces in the API (as determined by [filterReference]) from [hiddenSuperClasses]
     * to [cls].
     *
     * e.g. if `PublicClass` class extends `HiddenClass` and `HiddenClass` implements
     * `PublicInterface` then it will make `PublicClass` implement `PublicInterface`.
     */
    private fun inheritInterfacesFromHiddenSuperClasses(
        cls: ClassItem,
        hiddenSuperClasses: Sequence<ClassItem>,
        filterReference: FilterPredicate
    ) {
        // Keep track of the interface types. If any new interfaces are added then this will be
        // stored in [cls].
        var interfaceTypes: MutableList<ClassTypeItem>? = null

        // Keep track of the interface classes that have been added. It avoids adding the same
        // interface type multiple times.
        var interfaceTypeClasses: MutableList<ClassItem>? = null

        // Iterate over all the hidden super classes.
        for (hiddenSuperClass in hiddenSuperClasses) {
            // For each hidden super class iterate over its interfaces.
            for (interfaceType in hiddenSuperClass.interfaceTypes()) {
                // For each interface type resolve it, ignoring it if it cannot be resolved.
                val interfaceClass = interfaceType.resolveClass(codebase) ?: continue

                // Ignore interfaces that are not part of the API.
                if (!filterReference.test(interfaceClass)) continue

                // Initialize the collections of interface type and classes, if needed.
                if (interfaceTypes == null) {
                    interfaceTypes = cls.interfaceTypes().toMutableList()
                    interfaceTypeClasses =
                        interfaceTypes.mapNotNull { it.resolveClass(codebase) }.toMutableList()

                    // Store the mutable list of interface types in the class. Changes to the list
                    // will affect the class.
                    cls.setInterfaceTypes(interfaceTypes)
                }

                // If the interface class has already been added then ignore it.
                if (interfaceTypeClasses!!.any { it == interfaceClass }) {
                    continue
                }

                // Track that the interface class has been seen.
                interfaceTypeClasses.add(interfaceClass)

                // If necessary rewrite a generic interface type to use the correct type parameters.
                // e.g. given:
                //     public class StringContainer extends HiddenContainer<String> { ... }
                //     @Hide public interface HiddenContainer<T> implements Container<T> { ... }
                //     public interface Container<T> { ... }
                //
                // Then this will result in:
                //     public class StringContainer
                //         extends HiddenContainer<String>
                //         implements Container>String> { ... }
                //
                if (interfaceClass.hasTypeVariables()) {
                    val mapping = cls.mapTypeVariables(hiddenSuperClass)
                    if (mapping.isNotEmpty()) {
                        val mappedType = interfaceType.convertType(mapping)
                        interfaceTypes.add(mappedType)
                        continue
                    }
                }

                // Add the interface type to the list owned by the class.
                interfaceTypes.add(interfaceType)
            }
        }
    }

    /**
     * Inherit concrete method implementations of public interface methods that are implemented in
     * hidden classes and so will not otherwise be included in the API.
     */
    private fun inheritConcreteMethodsFromHiddenClasses(
        cls: ClassItem,
        hiddenSuperClasses: Sequence<ClassItem>,
        superClasses: Sequence<ClassItem>,
        filterEmit: FilterPredicate,
        filterReference: FilterPredicate
    ) {
        // Also generate stubs for any methods we would have inherited from abstract parents
        // All methods from super classes that (1) aren't overridden in this class already, and
        // (2) are overriding some method that is in a public interface accessible from this class.
        val interfaces = cls.allInterfaceTypes(filterReference).toSet()

        // Note that we can't just call method.superMethods() to and see whether any of their
        // containing classes are among our target APIs because it's possible that the super class
        // doesn't actually implement the interface, but still provides a matching signature for the
        // interface. Instead, we'll look through all of our interface methods and look for
        // potential overrides.
        val inheritableMethods = MethodItemSet()
        for (interfaceType in interfaces) {
            val interfaceClass = interfaceType.resolveClass(codebase) ?: continue
            for (method in interfaceClass.methods()) {
                inheritableMethods.add(method)
            }
        }

        // Also add in any abstract methods from public super classes
        val publicSuperClasses =
            superClasses.filter { filterEmit.test(it) && !it.isJavaLangObject() }
        for (superClass in publicSuperClasses) {
            for (method in superClass.methods()) {
                if (!method.modifiers.isAbstract() || !method.modifiers.isPublicOrProtected()) {
                    continue
                }
                inheritableMethods.add(method)
            }
        }

        // Also add in any concrete public methods from hidden super classes
        for (superClass in hiddenSuperClasses) {
            // Determine if there is a non-hidden class between the superClass and this class.
            // If non-hidden classes are found, don't include the methods for this hiddenSuperClass,
            // as it will already have been included in a previous super class
            val includeHiddenSuperClassMethods =
                !cls.allSuperClasses()
                    // Search from this class up to, but not including the superClass.
                    .takeWhile { currentClass -> currentClass != superClass }
                    // Find any class that is not hidden.
                    .any { currentClass -> !hiddenSuperClasses.contains(currentClass) }

            if (!includeHiddenSuperClassMethods) {
                continue
            }

            for (method in superClass.methods()) {
                if (method.modifiers.isAbstract() || !method.modifiers.isPublic()) {
                    continue
                }

                if (method.hasHiddenType(filterReference)) {
                    continue
                }

                inheritableMethods.add(method)
            }
        }

        // Find all methods that are inherited from these classes into our class (making sure that
        // we don't have duplicates, e.g. a method defined by one inherited class and then
        // overridden by another closer one). map from method name to super methods overriding our
        // interfaces
        val inheritedMethods = MethodItemSet()

        for (superClass in hiddenSuperClasses) {
            for (method in superClass.methods()) {
                val modifiers = method.modifiers
                if (!modifiers.isPrivate() && !modifiers.isAbstract()) {
                    if (inheritableMethods.containsMatchingMethod(method)) {
                        inheritedMethods.add(method)
                    }
                }
            }
        }

        // Remove any methods that are overriding any of our existing methods
        for (method in cls.methods()) {
            inheritedMethods.removeMatchingMethods(method)
        }

        // Next remove any overrides among the remaining super methods (e.g. one method from a
        // hidden parent is overriding another method from a more distant hidden parent).
        inheritedMethods.values.forEach { methods ->
            if (methods.size >= 2) {
                for (candidate in ArrayList(methods)) {
                    for (superMethod in candidate.allSuperMethods()) {
                        methods.remove(superMethod)
                    }
                }
            }
        }

        // Add all the existing methods in the class to the set of existing methods.
        val existingMethods = MethodItemSet()
        for (method in cls.methods()) {
            existingMethods.add(method)
        }

        // We're now left with concrete methods in hidden parents that are implementing methods in
        // public interfaces that are listed in this class. Create stubs for them:
        inheritedMethods.values.flatten().forEach {
            // Copy the method from the hidden class that is not part of the API into the class that
            // is part of the API.
            val method = it.duplicate(cls)
            /* Insert comment marker: This is useful for debugging purposes but doesn't
               belong in the stub
            method.documentation = "// Inlined stub from hidden parent class ${it.containingClass().qualifiedName()}\n" +
                    method.documentation
             */

            // If we already have an override of this method, do not add it to the methods list
            if (existingMethods.containsMatchingMethod(method)) {
                return@forEach
            }

            val runtimeDesc = it.internalDesc()
            val stubDesc = method.internalDesc()
            if (filterEmit.test(method) && runtimeDesc != stubDesc) {
                // This is problematic primarily for the platform where we use stubs, and the
                // generated method in the android.jar won't actually exist at runtime.
                // While we don't use stubs in AndroidX, this can still cause compat issues because
                // the current.txt (which will show the equivalent of stubDesc) won't actually match
                // the ABI of the library (because call sites will reference runtimeDesc).
                reporter.report(
                    Issues.INHERIT_CHANGES_SIGNATURE,
                    it,
                    "Explicitly override $it in $cls, or hide it in ${it.containingClass()};" +
                        " it cannot be implicitly inherited as API from the hidden super class" +
                        " because that would change its erased signature from $runtimeDesc to" +
                        " $stubDesc, and cause failures at runtime.",
                )
            }

            cls.addMethod(method)

            // Make sure that the same method is not added from multiple super classes.
            existingMethods.add(method)
        }
    }

    /** Apply package filters listed in [Config.skipEmitPackages] */
    private fun skipEmitPackages() {
        for (pkgName in config.skipEmitPackages) {
            val pkg = codebase.findPackage(pkgName) ?: continue
            pkg.emit = false
        }
    }

    /** If a file facade class has no public members, don't add it to the api */
    private fun hideEmptyKotlinFileFacadeClasses() {
        codebase.getPackages().allClasses().forEach { cls ->
            if (
                cls.isFileFacade &&
                    // a facade class needs to be emitted if it has any top-level fun/prop to emit
                    cls.members().none { member ->
                        // a member needs to be emitted if
                        //  1) it isn't hidden;
                        //  2) it is either public or has a show annotation;
                        !member.hidden && (member.isPublic || member.hasShowAnnotation())
                    }
            ) {
                cls.emit = false
            }
        }
    }

    /**
     * Merge in external qualifier annotations (i.e. ones intended to be included in the API written
     * from all configured sources).
     */
    fun mergeExternalQualifierAnnotations() {
        val mergeQualifierAnnotations = config.mergeQualifierAnnotations
        if (mergeQualifierAnnotations.isNotEmpty()) {
            AnnotationsMerger(sourceParser, codebase, reporter, config.annotationsMergerConfig)
                .mergeQualifierAnnotationsFromFiles(mergeQualifierAnnotations)
        }
    }

    /** Merge in external show/hide annotations from all configured sources */
    fun mergeExternalInclusionAnnotations() {
        val mergeInclusionAnnotations = config.mergeInclusionAnnotations
        if (mergeInclusionAnnotations.isNotEmpty()) {
            AnnotationsMerger(sourceParser, codebase, reporter, config.annotationsMergerConfig)
                .mergeInclusionAnnotationsFromFiles(mergeInclusionAnnotations)
        }
    }

    /**
     * Propagate the hidden flag down into individual elements -- if a class is hidden, then the
     * methods and fields are hidden etc
     */
    private fun propagateHiddenRemovedAndDocOnly() {
        // Create a visitor to propagate hidden and docOnly from the containing package onto the top
        // level classes and then propagate them, and removed status, down onto the nested classes
        // and members.
        val visitor =
            object :
                BaseItemVisitor(
                    preserveClassNesting = true,
                    // Only SelectableItems can have variantSelectors.
                    visitParameterItems = false,
                    // RecordComponentItems need to be checked to see if they are hidden.
                    visitRecordComponentItems = true,
                ) {
                override fun visitSelectableItem(item: SelectableItem) {
                    item.variantSelectors.inheritInto()
                }

                override fun visitRecordComponentItem(component: RecordComponentItem) {
                    val codebase = component.codebase
                    val hasHideAnnotations =
                        codebase.annotationManager.hasHideAnnotations(component.modifiers)
                    if (hasHideAnnotations) {
                        codebase.reporter.report(
                            Issues.HIDING_RECORD_COMPONENT,
                            component,
                            "Cannot hide ${component.describe()} as record components are an indivisible part of a record class"
                        )
                    }
                }
            }

        codebase.accept(visitor)
    }

    private fun checkSystemPermissions(method: MethodItem) {
        val annotation = method.modifiers.findAnnotation(ANDROIDX_REQUIRES_PERMISSION)
        var hasAnnotation = false

        val requiresPermissionProxy = annotation?.getRequiresPermissionProxy(method)
        if (requiresPermissionProxy != null) {
            hasAnnotation = true
            val values = requiresPermissionProxy.permissionValues
            val any = requiresPermissionProxy.any

            val system = ArrayList<String>()
            val nonSystem = ArrayList<String>()
            val missing = ArrayList<String>()
            for (value in values) {
                val permission = value.asString() ?: continue
                val level = config.manifest.getPermissionLevel(permission)
                if (level == null) {
                    if (any) {
                        missing.add(permission)
                        continue
                    }

                    reporter.report(
                        Issues.REQUIRES_SYSTEM_PERMISSION,
                        method,
                        "Permission '$permission' is not defined by manifest ${config.manifest}."
                    )
                    continue
                }
                if (
                    level.contains("normal") ||
                        level.contains("dangerous") ||
                        level.contains("ephemeral")
                ) {
                    nonSystem.add(permission)
                } else {
                    system.add(permission)
                }
            }
            if (any && missing.size == values.size) {
                reporter.report(
                    Issues.REQUIRES_SYSTEM_PERMISSION,
                    method,
                    "None of the permissions ${missing.joinToString()} are defined by manifest " +
                        "${config.manifest}."
                )
            }

            if (system.isEmpty() && nonSystem.isEmpty()) {
                hasAnnotation = false
            } else if (any && nonSystem.isNotEmpty() || !any && system.isEmpty()) {
                reporter.report(
                    Issues.REQUIRES_SYSTEM_PERMISSION,
                    method,
                    "Method '" +
                        method.name() +
                        "' must be protected with a system permission; it currently" +
                        " allows non-system callers holding " +
                        nonSystem.toString()
                )
            }
        }

        if (!hasAnnotation) {
            reporter.report(
                Issues.REQUIRES_SYSTEM_PERMISSION,
                method,
                "Method '" + method.name() + "' must be protected with a system permission."
            )
        }
    }

    fun performChecks() {
        if (codebase.trustedApi()) {
            // The codebase is already an API; no consistency checks to be performed
            return
        }

        val checkSystemPermissions =
            !reporter.isSuppressed(Issues.REQUIRES_SYSTEM_PERMISSION) &&
                config.apiSurface == "system" &&
                !config.manifest.isEmpty()

        // Only check for hidden show annotations if it is needed and it is not suppressed.
        val checkHiddenShowAnnotations =
            config.needUnhiddenSystemApiCheck && !reporter.isSuppressed(Issues.UNHIDDEN_SYSTEM_API)

        codebase.accept(
            object :
                ApiVisitor(
                    apiPredicateConfig = config.apiPredicateConfig,
                    // Don't run checks on elements that only exist in bytecode.
                    targetLanguages = TargetLanguageSet.SOURCE,
                ) {
                override fun visitParameter(parameter: ParameterItem) {
                    checkTypeReferencesHidden(parameter, parameter.type())
                }

                /**
                 * Visit all [SelectableItem]s, i.e. all [Item]s apart from [ParameterItem]s.
                 *
                 * None of the checks in this apply to [ParameterItem]. The deprecation checks do
                 * not apply as there is no way to provide an `@deprecation` tag in Javadoc for
                 * parameters. The unhidden showability annotation check ('UnhiddemSystemApi`) does
                 * not apply as you cannot annotate a [ParameterItem] with a showability annotation.
                 */
                override fun visitSelectableItem(item: SelectableItem) {
                    if (
                        item.originallyDeprecated &&
                            !item.documentationContainsDeprecated() &&
                            // Don't warn about this in Kotlin; the Kotlin deprecation annotation
                            // includes deprecation
                            // messages (unlike java.lang.Deprecated which has no attributes).
                            // Instead, these
                            // are added to the documentation by the [DocAnalyzer].
                            !item.isKotlin()
                    ) {
                        reporter.report(
                            Issues.DEPRECATION_MISMATCH,
                            item,
                            "${item.toString().capitalize()}: @Deprecated annotation (present) and @deprecated doc tag (not present) do not match"
                        )
                        // TODO: Check opposite (doc tag but no annotation)
                    }

                    if (checkHiddenShowAnnotations) {
                        checkEnsureShowAnnotationsAreExplicitlyHidden(item)
                    } else {
                        checkEnsureShowAnnotationsAreNotExplicitlyHidden(item)
                    }
                }

                override fun visitClass(cls: ClassItem) {
                    if (checkSystemPermissions) {
                        // Look for Android @SystemApi exposed outside the normal SDK; we require
                        // that they're protected with a system permission.
                        // Also flag @SystemApi apis not annotated with @hide.

                        // This class is a system service if it's annotated with @SystemService,
                        // or if it's android.content.pm.PackageManager
                        if (
                            cls.modifiers.isAnnotatedWith("android.annotation.SystemService") ||
                                cls.qualifiedName() == "android.content.pm.PackageManager"
                        ) {
                            // Check permissions on system services
                            for (method in cls.filteredMethods(filterEmit)) {
                                checkSystemPermissions(method)
                            }
                        }
                    }
                }

                override fun visitField(field: FieldItem) {
                    checkTypeReferencesHidden(field, field.type())
                }

                override fun visitProperty(property: PropertyItem) {
                    checkTypeReferencesHidden(property, property.type())
                }

                override fun visitMethod(method: MethodItem) {
                    checkTypeReferencesHidden(
                        method,
                        method.returnType()
                    ) // returnType is nullable only for constructors
                }

                /** Check that the type doesn't refer to any hidden classes. */
                private fun checkTypeReferencesHidden(item: Item, type: TypeItem) {
                    type.accept(
                        object : BaseTypeVisitor() {
                            override fun visitClassType(classType: ClassTypeItem) {
                                val cls = classType.resolveClass(codebase) ?: return
                                if (
                                    !filterReference.test(cls) &&
                                        cls.origin != ClassOrigin.CLASS_PATH
                                ) {
                                    reporter.report(
                                        Issues.HIDDEN_TYPE_PARAMETER,
                                        item,
                                        "${item.toString().capitalize()} references hidden type $classType."
                                    )
                                }
                            }
                        }
                    )
                }
            }
        )
    }

    /**
     * Check to make sure that [item] does not have show annotations without being explicitly
     * hidden.
     *
     * This is not called when the API surfaces are defined in the configuration file as that
     * provides enough information to automatically hide items from a related but untracked surface.
     */
    private fun checkEnsureShowAnnotationsAreExplicitlyHidden(item: SelectableItem) {
        if (
            item.hasShowAnnotation() &&
                !item.originallyHidden &&
                !item.showability.showNonRecursive()
        ) {
            item.modifiers
                .annotations()
                // Find the first show annotation. Just because item.hasShowAnnotation() is true
                // does not mean that there must be one show annotation as a revert annotation could
                // be treated as a show annotation on one item and a hide annotation on another but
                // is neither a show nor hide annotation.
                .firstOrNull(AnnotationItem::isShowAnnotation)
                ?.let { annotation ->
                    val annotationName = annotation.qualifiedName
                    reporter.report(
                        Issues.UNHIDDEN_SYSTEM_API,
                        item,
                        "@$annotationName APIs must also be marked @hide: ${item.describe()}"
                    )
                }
        }
    }

    /**
     * Check to make sure that [item] does not have show annotations without being explicitly
     * hidden.
     */
    private fun checkEnsureShowAnnotationsAreNotExplicitlyHidden(item: SelectableItem) {
        if (
            item.hasShowAnnotation() &&
                // Only check for @hide doc tag. Testing for annotations would complicate this
                // because
                // it would be necessary to differentiate between
                item.documentation?.isHidden == true &&
                !item.showability.showNonRecursive()
        ) {
            item.modifiers
                .annotations()
                // Find the first show annotation. Just because item.hasShowAnnotation() is true
                // does not mean that there must be one show annotation as a revert annotation could
                // be treated as a show annotation on one item and a hide annotation on another but
                // is neither a show nor hide annotation.
                .firstOrNull(AnnotationItem::isShowAnnotation)
                ?.let { annotation ->
                    val annotationName = annotation.qualifiedName
                    reporter.report(
                        Issues.HIDDEN_SHOW_ANNOTATION,
                        item,
                        "@$annotationName APIs must not be marked @hide: ${item.describe()}"
                    )
                }
        }
    }

    fun handleStripping() {
        val notStrippable = ApiContents.computeContents(codebase, config.apiPredicateConfig)

        // complain about anything that looks includeable but is not supposed to
        // be written, e.g. hidden things
        for (cl in notStrippable) {
            if (!cl.isHiddenOrRemoved()) {
                val publiclyConstructable =
                    !cl.modifiers.isSealed() && cl.constructors().any { it.isApiCandidate() }
                for (m in
                // Don't run checks on elements that only exist in bytecode.
                cl.methods().filter { it.targetLanguages != TargetLanguageSet.BYTECODE_ONLY }) {
                    if (!m.isApiCandidate()) {
                        if (publiclyConstructable && m.modifiers.isAbstract()) {
                            reporter.report(
                                Issues.HIDDEN_ABSTRACT_METHOD,
                                m,
                                "${m.name()} cannot be hidden and abstract when " +
                                    "${cl.simpleName()} has a visible constructor, in case a " +
                                    "third-party attempts to subclass it."
                            )
                        }
                        continue
                    }
                    if (m.isHiddenOrRemoved()) {
                        reporter.report(
                            Issues.UNAVAILABLE_SYMBOL,
                            m,
                            "Reference to unavailable method " + m.name()
                        )
                    } else if (m.originallyDeprecated) {
                        // don't bother reporting deprecated methods unless they are public and
                        // explicitly marked as deprecated.
                        reporter.report(
                            Issues.DEPRECATED,
                            m,
                            "Method " + cl.qualifiedName() + "." + m.name() + " is deprecated"
                        )
                    }

                    checkTypeReferencesHiddenOrDeprecated(m.returnType(), m, cl, "Return type")
                    for (p in m.parameters()) {
                        checkTypeReferencesHiddenOrDeprecated(p.type(), m, cl, "Parameter")
                    }
                }

                if (!cl.effectivelyDeprecated) {
                    val s = cl.superClass()
                    if (s?.effectivelyDeprecated == true) {
                        reporter.report(
                            Issues.EXTENDS_DEPRECATED,
                            cl,
                            "Extending deprecated super class $s from ${cl.qualifiedName()}: this class should also be deprecated"
                        )
                    }

                    for (t in cl.interfaceTypes()) {
                        if (t.resolveClass(codebase)?.effectivelyDeprecated == true) {
                            reporter.report(
                                Issues.EXTENDS_DEPRECATED,
                                cl,
                                "Implementing interface of deprecated type $t in ${cl.qualifiedName()}: this class should also be deprecated"
                            )
                        }
                    }
                }
            } else if (cl.originallyDeprecated) {
                // not hidden, but deprecated
                reporter.report(Issues.DEPRECATED, cl, "Class ${cl.qualifiedName()} is deprecated")
            }
        }
    }

    /**
     * Checks if the type (method parameter or return type) references a hidden or deprecated class.
     */
    private fun checkTypeReferencesHiddenOrDeprecated(
        type: TypeItem,
        containingMethod: MethodItem,
        containingClass: ClassItem,
        usage: String
    ) {
        if (!containingMethod.effectivelyDeprecated) {
            type.accept(
                object : BaseTypeVisitor() {
                    override fun visitClassType(classType: ClassTypeItem) {
                        if (classType.resolveClass(codebase)?.effectivelyDeprecated == true) {
                            reporter.report(
                                Issues.REFERENCES_DEPRECATED,
                                containingMethod,
                                "$usage references deprecated type $classType in ${containingClass.qualifiedName()}.${containingMethod.name()}(): this method should also be deprecated"
                            )
                        }
                    }
                }
            )
        }

        val hiddenClasses = findHiddenClasses(type)
        val typeClassName = (type as? ClassTypeItem)?.qualifiedName
        for (hiddenClass in hiddenClasses) {
            if (hiddenClass.origin == ClassOrigin.CLASS_PATH) continue
            if (hiddenClass.qualifiedName() == typeClassName) {
                // The type itself is hidden
                reporter.report(
                    Issues.UNAVAILABLE_SYMBOL,
                    containingMethod,
                    "$usage of unavailable type $type in ${containingClass.qualifiedName()}.${containingMethod.name()}()"
                )
            } else {
                // The type contains a hidden type
                reporter.report(
                    Issues.HIDDEN_TYPE_PARAMETER,
                    containingMethod,
                    "$usage uses type parameter of unavailable type $type in ${containingClass.qualifiedName()}.${containingMethod.name()}()"
                )
            }
        }
    }

    /**
     * Find references to hidden classes.
     *
     * This finds hidden classes that are used by public parts of the API in order to ensure the API
     * is self-consistent and does not reference classes that are not included in the stubs. Any
     * such references cause an error to be reported.
     *
     * A reference to an imported class is not treated as an error, even though imported classes are
     * hidden from the stub generation. That is because imported classes are, by definition,
     * excluded from the set of classes for which stubs are required.
     *
     * @param ti the type information to examine for references to hidden classes.
     * @return all references to hidden classes referenced by the type
     */
    private fun findHiddenClasses(ti: TypeItem): Set<ClassItem> {
        val hiddenClasses = mutableSetOf<ClassItem>()
        ti.accept(
            object : BaseTypeVisitor() {
                override fun visitClassType(classType: ClassTypeItem) {
                    val asClass = classType.resolveClass(codebase) ?: return
                    if (asClass.isHiddenOrRemoved()) {
                        hiddenClasses.add(asClass)
                    }
                }
            }
        )
        return hiddenClasses
    }
}

private fun String.capitalize(): String {
    return this.replaceFirstChar {
        if (it.isLowerCase()) {
            it.titlecase(Locale.getDefault())
        } else {
            it.toString()
        }
    }
}

/**
 * Whether documentation for the [Item] has the `@deprecated` tag -- for inherited methods, this
 * also looks at any inherited documentation.
 */
private fun SelectableItem.documentationContainsDeprecated(): Boolean {
    val documentation = this.documentation ?: return false
    if (documentation.hasBlockTagOfType("deprecated")) return true
    if (this !is MethodItem) return false
    if (!documentation.requiresSourceComment() || documentation.containsInheritDocTag()) {
        return superMethods().any { it.documentationContainsDeprecated() }
    }
    return false
}

/** Check for an `inheritDoc`. */
private fun ItemDocumentation.containsInheritDocTag(): Boolean =
    check(CONTAINS_INHERIT_DOC_TAG_PREDICATE)

/**
 * A [DocContentPredicate] that will check for the presence of `{@inheritDoc}` in the documentation.
 */
private val CONTAINS_INHERIT_DOC_TAG_PREDICATE =
    DocContentPredicates.containsInlineTag("inheritDoc")

/**
 * A set of [MethodItem]s.
 *
 * This is implemented as a [MutableMap] from the [MethodItem.name] to the list of [MethodItem]s
 * with that name.
 */
private typealias MethodItemSet = HashMap<String, MutableList<MethodItem>>

/**
 * Add a method to the set.
 *
 * This does not check to see if the [MethodItem] exists already so it is possible that it will
 * contain duplicate methods.
 */
private fun MethodItemSet.add(method: MethodItem) {
    val name = method.name()
    val list = computeIfAbsent(name) { mutableListOf() }
    list.add(method)
}

/**
 * Check to see whether the set contains a method that matches [method] as determined by
 * [MethodItem.matches].
 */
private fun MethodItemSet.containsMatchingMethod(method: MethodItem): Boolean {
    val name = method.name()
    val list = this[name] ?: return false
    for (existing in list) {
        if (method.matches(existing)) {
            return true
        }
    }
    return false
}

/** Remove any method that matches [method] as determined by [MethodItem.matches]. */
private fun MethodItemSet.removeMatchingMethods(method: MethodItem) {
    val name = method.name()
    val list = this[name] ?: return
    val iterator = list.listIterator()
    while (iterator.hasNext()) {
        val existing = iterator.next()
        if (method.matches(existing)) {
            iterator.remove()
        }
    }
}
