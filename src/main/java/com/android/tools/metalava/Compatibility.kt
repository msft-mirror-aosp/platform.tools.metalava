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

package com.android.tools.metalava

const val COMPAT_MODE_BY_DEFAULT = false

/**
 * The old API generator code had a number of quirks. Initially we want to simulate these
 * quirks to produce compatible signature files and APIs, but we want to track what these quirks
 * are and be able to turn them off eventually. This class offers more fine grained control
 * of these compatibility behaviors such that we can enable/disable them selectively
 */
var compatibility: Compatibility = Compatibility()

class Compatibility(
    /** Whether compatibility is generally on */
    val compat: Boolean = COMPAT_MODE_BY_DEFAULT
) {
    /** Whether to include instance methods in annotation classes for the annotation properties */
    var skipAnnotationInstanceMethods: Boolean = compat

    /**
     * In this signature
     *        public <X extends Throwable> T orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
     *  doclava1 would treat this as "throws Throwable" instead of "throws X". This variable turns on
     *  this compat behavior.
     * */
    var useErasureInThrows: Boolean = compat

    /**
     * Whether throws classes in methods should be filtered. This should definitely
     * be the case, but doclava1 doesn't. Note that this only applies to signature
     * files, not stub files.
     */
    var filterThrowsClasses: Boolean = !compat

    /**
     * Doclava1 omits type parameters in interfaces (in signature files, not in stubs)
     */
    var omitTypeParametersInInterfaces: Boolean = compat

    /**
     * Whether to include parameter names in the signature file
     */
    var parameterNames: Boolean = !compat

    /**
     * *Some* signatures for doclava1 wrote "<?>" as "<? extends java.lang.Object>",
     * which is equivalent. Metalava does not do that. This flags ensures that the
     * signature files look like the old ones for the specific methods which did this.
     */
    var includeExtendsObjectInWildcard = compat

    /** Whether we should omit common packages such as java.lang.* and kotlin.* from signature output */
    var omitCommonPackages = !compat

    /**
     * If an overriding method differs from its super method only by final or deprecated
     * and the containing class is final or deprecated, skip it in the signature file
     */
    var hideDifferenceImplicit = !compat

    /**
     * The -new_api flag in API check (which generates an XML diff of the differences
     * between two APIs) in doclava was ignoring fields. This flag controls whether
     * we do the same.
     */
    var includeFieldsInApiDiff = !compat

    // Other examples: sometimes we sort by qualified name, sometimes by full name
}