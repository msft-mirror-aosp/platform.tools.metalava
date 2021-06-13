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