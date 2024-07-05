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

package com.android.tools.metalava.model

import com.android.tools.metalava.reporter.BaselineKey
import com.android.tools.metalava.reporter.FileLocation
import com.android.tools.metalava.reporter.Reportable
import java.util.concurrent.atomic.AtomicInteger

/**
 * Represents a code element such as a package, a class, a method, a field, a parameter.
 *
 * This extra abstraction on top of PSI allows us to more model the API (and customize visibility,
 * which cannot always be done by looking at a particular piece of code and examining visibility
 * and @hide/@removed annotations: sometimes package private APIs are unhidden by being used in
 * public APIs for example.
 *
 * The abstraction also lets us back the model by an alternative implementation read from signature
 * files, to do compatibility checks.
 */
interface Item : Reportable {
    val codebase: Codebase

    /** Return the modifiers of this class */
    @MetalavaApi val modifiers: ModifierList

    /**
     * Whether this element was originally hidden with @hide/@Hide. The [hidden] property tracks
     * whether it is *actually* hidden, since elements can be unhidden via show annotations, etc.
     */
    val originallyHidden: Boolean

    /**
     * Whether this element has been hidden with @hide/@Hide (or after propagation, in some
     * containing class/pkg)
     */
    var hidden: Boolean

    /** Whether this element will be printed in the signature file */
    var emit: Boolean

    fun parent(): Item?

    /**
     * Recursive check to see if this item or any of its parents (containing class, containing
     * package) are hidden
     */
    fun hidden(): Boolean {
        return hidden || parent()?.hidden() ?: false
    }

    /**
     * Recursive check to see if compatibility checks should be suppressed for this item or any of
     * its parents (containing class, containing package).
     */
    fun isCompatibilitySuppressed(): Boolean {
        return hasSuppressCompatibilityMetaAnnotation() ||
            parent()?.isCompatibilitySuppressed() ?: false
    }

    /**
     * Whether this element has been removed with @removed/@Remove (or after propagation, in some
     * containing class)
     */
    var removed: Boolean

    /** True if this item has been marked deprecated. */
    val originallyDeprecated: Boolean

    /**
     * True if this item has been marked as deprecated or is a descendant of a non-package item that
     * has been marked as deprecated.
     */
    val effectivelyDeprecated: Boolean

    /** True if this element is only intended for documentation */
    var docOnly: Boolean

    /** True if this item is either hidden or removed */
    fun isHiddenOrRemoved(): Boolean = hidden || removed

    /** Visits this element using the given [visitor] */
    fun accept(visitor: ItemVisitor)

    /** Get a mutable version of modifiers for this item */
    fun mutableModifiers(): MutableModifierList

    /**
     * The javadoc/KDoc comment for this code element, if any. This is the original content of the
     * documentation, including lexical tokens to begin, continue and end the comment (such as /+*).
     * See [fullyQualifiedDocumentation] to look up the documentation with fully qualified
     * references to classes.
     */
    var documentation: ItemDocumentation

    /**
     * Looks up docs for the first instance of a specific javadoc tag having the (optionally)
     * provided value (e.g. parameter name).
     */
    fun findTagDocumentation(tag: String, value: String? = null): String?

    /**
     * A rank used for sorting. This allows signature files etc to sort similar items by a natural
     * order, if non-zero. (Even though in signature files the elements are normally sorted first
     * logically (constructors, then methods, then fields) and then alphabetically, this lets us
     * preserve the source ordering for example for overloaded methods of the same name, where it's
     * not clear that an alphabetical order (of each parameter?) would be preferable.)
     */
    val sortingRank: Int

    /**
     * Add the given text to the documentation.
     *
     * If the [tagSection] is null, add the comment to the initial text block of the description.
     * Otherwise if it is "@return", add the comment to the return value. Otherwise the [tagSection]
     * is taken to be the parameter name, and the comment added as parameter documentation for the
     * given parameter.
     */
    fun appendDocumentation(comment: String, tagSection: String? = null, append: Boolean = true)

    val isPublic: Boolean
    val isProtected: Boolean
    val isInternal: Boolean
    val isPackagePrivate: Boolean
    val isPrivate: Boolean

    // make sure these are implemented so we can place in maps:
    override fun equals(other: Any?): Boolean

    override fun hashCode(): Int

    /** Calls [toStringForItem]. */
    override fun toString(): String

    /** Provides a string representation of the item, suitable for use while debugging. */
    fun toStringForItem(): String

    /**
     * Whether this item was loaded from the classpath (e.g. jar dependencies) rather than be
     * declared as source
     */
    fun isFromClassPath(): Boolean = false

    /** Is this element declared in Java (rather than Kotlin) ? */
    fun isJava(): Boolean = true

    /** Is this element declared in Kotlin (rather than Java) ? */
    fun isKotlin() = !isJava()

    /** Determines whether this item will be shown as part of the API or not. */
    val showability: Showability

    /**
     * Returns true if this item has any show annotations.
     *
     * See [Showability.show]
     */
    fun hasShowAnnotation(): Boolean = showability.show()

    /** Returns true if this modifier list contains any hide annotations */
    fun hasHideAnnotation(): Boolean =
        modifiers.codebase.annotationManager.hasHideAnnotations(modifiers)

    fun hasSuppressCompatibilityMetaAnnotation(): Boolean =
        modifiers.hasSuppressCompatibilityMetaAnnotations()

    fun sourceFile(): SourceFile? {
        var curr: Item? = this
        while (curr != null) {
            if (curr is ClassItem && curr.isTopLevelClass()) {
                return curr.getSourceFile()
            }
            curr = curr.parent()
        }

        return null
    }

    override val fileLocation: FileLocation
        get() = FileLocation.UNKNOWN

    /**
     * Returns the [documentation], but with fully qualified links (except for the same package, and
     * when turning a relative reference into a fully qualified reference, use the javadoc syntax
     * for continuing to display the relative text, e.g. instead of {@link java.util.List}, use
     * {@link java.util.List List}.
     */
    fun fullyQualifiedDocumentation(): String = documentation.text

    /** Expands the given documentation comment in the current name context */
    fun fullyQualifiedDocumentation(documentation: String): String = documentation

    /**
     * Produces a user visible description of this item, including a label such as "class" or
     * "field"
     */
    fun describe(capitalize: Boolean = false) = describe(this, capitalize)

    /** Returns the package that contains this item. */
    fun containingPackage(): PackageItem?

    /** Returns the class that contains this item. */
    fun containingClass(): ClassItem?

    /**
     * Returns the associated type, if any.
     *
     * i.e.
     * * For a field, property or parameter, this is the type of the variable.
     * * For a method, it's the return type.
     * * For classes it's the declared class type, i.e. a class type using the type parameter types
     *   as the type arguments.
     * * For type parameters it's a [VariableTypeItem] reference the type parameter.
     * * For packages and files, it's null.
     */
    fun type(): TypeItem?

    /**
     * Set the type of this.
     *
     * The [type] parameter must be of the same concrete type as returned by the [Item.type] method.
     */
    fun setType(type: TypeItem)

    /**
     * Find the [Item] in [codebase] that corresponds to this item, or `null` if there is no such
     * item.
     *
     * If [superMethods] is true and this is a [MethodItem] then the returned [MethodItem], if any,
     * could be in a [ClassItem] that does not correspond to the [MethodItem.containingClass], it
     * could be from a super class or super interface. e.g. if the [codebase] contains something
     * like:
     * ```
     *     public class Super {
     *         public void method() {...}
     *     }
     *     public class Foo extends Super {}
     * ```
     *
     * And this is called on `Foo.method()` then:
     * * if [superMethods] is false this will return `null`.
     * * if [superMethods] is true and [duplicate] is false, then this will return `Super.method()`.
     * * if both [superMethods] and [duplicate] are true then this will return a duplicate of
     *   `Super.method()` that has been added to `Foo` so it will be essentially `Foo.method()`.
     *
     * @param codebase the [Codebase] to search for a corresponding item.
     * @param superMethods if true and this is a [MethodItem] then this method will search for super
     *   methods. If this is a [ParameterItem] then the value of this parameter will be passed to
     *   the [findCorrespondingItemIn] call which is used to find the [MethodItem] corresponding to
     *   the [ParameterItem.containingMethod].
     * @param duplicate if true, and this is a [MemberItem] (or [ParameterItem]) then the returned
     *   [Item], if any, will be in the [ClassItem] that corresponds to the [Item.containingClass].
     *   This should be `true` if the returned [Item] is going to be compared to the original [Item]
     *   as the [Item.containingClass] can affect that comparison, e.g. the meaning of certain
     *   modifiers.
     */
    fun findCorrespondingItemIn(
        codebase: Codebase,
        superMethods: Boolean = false,
        duplicate: Boolean = false,
    ): Item?

    /**
     * Get the set of suppressed issues for this [Item].
     *
     * These are the values supplied to any of the [SUPPRESS_ANNOTATIONS] on this item. It DOES not
     * include suppressed issues from the [parent].
     */
    override fun suppressedIssues(): Set<String>

    /** The [BaselineKey] for this. */
    override val baselineKey
        get() = BaselineKey.forElementId(baselineElementId())

    /**
     * Get the baseline element id from which [baselineKey] is constructed.
     *
     * See [BaselineKey.forElementId] for more details.
     */
    fun baselineElementId(): String

    companion object {
        fun describe(item: Item, capitalize: Boolean = false): String {
            return when (item) {
                is PackageItem -> describe(item, capitalize = capitalize)
                is ClassItem -> describe(item, capitalize = capitalize)
                is FieldItem -> describe(item, capitalize = capitalize)
                is MethodItem ->
                    describe(
                        item,
                        includeParameterNames = false,
                        includeParameterTypes = true,
                        capitalize = capitalize
                    )
                is ParameterItem ->
                    describe(
                        item,
                        includeParameterNames = true,
                        includeParameterTypes = true,
                        capitalize = capitalize
                    )
                else -> item.toString()
            }
        }

        fun describe(
            item: MethodItem,
            includeParameterNames: Boolean = false,
            includeParameterTypes: Boolean = false,
            includeReturnValue: Boolean = false,
            capitalize: Boolean = false
        ): String {
            val builder = StringBuilder()
            if (item.isConstructor()) {
                builder.append(if (capitalize) "Constructor" else "constructor")
            } else {
                builder.append(if (capitalize) "Method" else "method")
            }
            builder.append(' ')
            if (includeReturnValue && !item.isConstructor()) {
                builder.append(item.returnType().toSimpleType())
                builder.append(' ')
            }
            appendMethodSignature(builder, item, includeParameterNames, includeParameterTypes)
            return builder.toString()
        }

        fun describe(
            item: ParameterItem,
            includeParameterNames: Boolean = false,
            includeParameterTypes: Boolean = false,
            capitalize: Boolean = false
        ): String {
            val builder = StringBuilder()
            builder.append(if (capitalize) "Parameter" else "parameter")
            builder.append(' ')
            builder.append(item.name())
            builder.append(" in ")
            val method = item.containingMethod()
            appendMethodSignature(builder, method, includeParameterNames, includeParameterTypes)
            return builder.toString()
        }

        private fun appendMethodSignature(
            builder: StringBuilder,
            item: MethodItem,
            includeParameterNames: Boolean,
            includeParameterTypes: Boolean
        ) {
            builder.append(item.containingClass().qualifiedName())
            if (!item.isConstructor()) {
                builder.append('.')
                builder.append(item.name())
            }
            if (includeParameterNames || includeParameterTypes) {
                builder.append('(')
                var first = true
                for (parameter in item.parameters()) {
                    if (first) {
                        first = false
                    } else {
                        builder.append(',')
                        if (includeParameterNames && includeParameterTypes) {
                            builder.append(' ')
                        }
                    }
                    if (includeParameterTypes) {
                        builder.append(parameter.type().toSimpleType())
                        if (includeParameterNames) {
                            builder.append(' ')
                        }
                    }
                    if (includeParameterNames) {
                        builder.append(parameter.publicName() ?: parameter.name())
                    }
                }
                builder.append(')')
            }
        }

        private fun describe(item: FieldItem, capitalize: Boolean = false): String {
            return if (item.isEnumConstant()) {
                "${if (capitalize) "Enum" else "enum"} constant ${item.containingClass().qualifiedName()}.${item.name()}"
            } else {
                "${if (capitalize) "Field" else "field"} ${item.containingClass().qualifiedName()}.${item.name()}"
            }
        }

        private fun describe(item: ClassItem, capitalize: Boolean = false): String {
            return "${if (capitalize) "Class" else "class"} ${item.qualifiedName()}"
        }

        private fun describe(item: PackageItem, capitalize: Boolean = false): String {
            return "${if (capitalize) "Package" else "package"} ${item.qualifiedName()}"
        }
    }
}

abstract class DefaultItem(
    final override val fileLocation: FileLocation,
    final override val modifiers: DefaultModifierList,
    final override var documentation: ItemDocumentation,
    variantSelectorsFactory: ApiVariantSelectorsFactory = ApiVariantSelectors.IMMUTABLE_FACTORY,
) : Item {

    init {
        @Suppress("LeakingThis")
        modifiers.owner = this
    }

    /**
     * Create a [ApiVariantSelectors] appropriate for this [Item].
     *
     * The leaking of `this` is safe as the implementations do not do access anything that has not
     * been initialized.
     */
    private val variantSelectors = @Suppress("LeakingThis") variantSelectorsFactory(this)

    /**
     * Manually delegate to [ApiVariantSelectors.originallyHidden] as property delegates are
     * expensive.
     */
    override val originallyHidden
        get() = variantSelectors.originallyHidden

    /** Manually delegate to [ApiVariantSelectors.hidden] as property delegates are expensive. */
    override var hidden
        get() = variantSelectors.hidden
        set(value) {
            variantSelectors.hidden = value
        }

    final override var docOnly = documentation.contains("@doconly")
    final override var removed = documentation.contains("@removed")

    final override val sortingRank: Int = nextRank.getAndIncrement()

    final override val originallyDeprecated
        // Delegate to the [ModifierList.isDeprecated] method so that changes to that will affect
        // the value of this and [Item.effectivelyDeprecated] which delegates to this.
        get() = modifiers.isDeprecated()

    final override fun mutableModifiers(): MutableModifierList = modifiers

    final override val isPublic: Boolean
        get() = modifiers.isPublic()

    final override val isProtected: Boolean
        get() = modifiers.isProtected()

    final override val isInternal: Boolean
        get() = modifiers.getVisibilityLevel() == VisibilityLevel.INTERNAL

    final override val isPackagePrivate: Boolean
        get() = modifiers.isPackagePrivate()

    final override val isPrivate: Boolean
        get() = modifiers.isPrivate()

    final override var emit = true

    companion object {
        private var nextRank = AtomicInteger()
    }

    final override val showability: Showability by lazy {
        codebase.annotationManager.getShowabilityForItem(this)
    }

    final override fun suppressedIssues(): Set<String> {
        return buildSet {
            for (annotation in modifiers.annotations()) {
                val annotationName = annotation.qualifiedName
                if (annotationName in SUPPRESS_ANNOTATIONS) {
                    for (attribute in annotation.attributes) {
                        // Assumption that all annotations in SUPPRESS_ANNOTATIONS only have
                        // one attribute such as value/names that is varargs of String
                        val value = attribute.value
                        if (value is AnnotationArrayAttributeValue) {
                            // Example: @SuppressLint({"RequiresFeature", "AllUpper"})
                            for (innerValue in value.values) {
                                innerValue.value()?.toString()?.let { add(it) }
                            }
                        } else {
                            // Example: @SuppressLint("RequiresFeature")
                            value.value()?.toString()?.let { add(it) }
                        }
                    }
                }
            }
        }
    }

    final override fun toString() = toStringForItem()
}
