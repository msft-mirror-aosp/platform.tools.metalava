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
package com.android.tools.metalava.apilevels

/**
 * Represents an API element, e.g. class, method or field.
 *
 * @param name the name of the API element
 * @param version an API version for which the API element existed, or -1 if the class does not yet
 *   exist in the Android SDK (only in extension SDKs)
 * @param deprecated whether the API element was deprecated in the API version in question
 */
open class ApiElement(
    val name: String,
    version: Int,
    deprecated: Boolean = false,
) : Comparable<ApiElement> {

    /**
     * The Android API level of this ApiElement. i.e. The Android platform SDK version this API was
     * first introduced in.
     */
    var since = version
        private set

    /**
     * The extension version of this ApiElement. i.e. The Android extension SDK version this API was
     * first introduced in.
     */
    var sinceExtension = NEVER
        private set

    /**
     * The SDKs and their versions this API was first introduced in.
     *
     * The value is a comma-separated list of &lt;int&gt;:&lt;int&gt; values, where the first
     * &lt;int&gt; is the integer ID of an SDK, and the second &lt;int&gt; the version of that SDK,
     * in which this API first appeared.
     *
     * This field is a super-set of mSince, and if non-null/non-empty, should be preferred.
     */
    var sdks: String? = null
        private set

    var mainlineModule: String? = null
        private set

    /** The optional API level this element was deprecated in. */
    var deprecatedIn = if (deprecated) version else null
        private set

    var lastPresentIn = version
        private set

    /**
     * Checks if this API element was introduced not later than another API element.
     *
     * @param other the API element to compare to
     * @return true if this API element was introduced not later than `other`
     */
    fun introducedNotLaterThan(other: ApiElement?): Boolean {
        return since <= other!!.since
    }

    /**
     * Updates the API element with information for a specific API version.
     *
     * @param version an API version for which the API element existed
     * @param deprecated whether the API element was deprecated in the API version in question
     */
    fun update(version: Int, deprecated: Boolean) {
        assert(version > 0)
        if (since > version) {
            since = version
        }
        if (lastPresentIn < version) {
            lastPresentIn = version
        }
        val deprecatedVersion = deprecatedIn
        if (deprecated) {
            // If it was not previously deprecated or was deprecated in a later version than this
            // one then deprecate it in this version.
            if (deprecatedVersion == null || deprecatedVersion > version) {
                deprecatedIn = version
            }
        } else {
            // If it was previously deprecated and was deprecated in an earlier version than this
            // one then treat it as being undeprecated.
            if (deprecatedVersion != null && deprecatedVersion < version) {
                deprecatedIn = null
            }
        }
    }

    /**
     * Updates the API element with information for a specific API version.
     *
     * @param version an API version for which the API element existed
     */
    fun update(version: Int) {
        update(version, deprecatedIn != null)
    }

    /**
     * Analogous to update(), but for extensions sdk versions.
     *
     * @param version an extension SDK version for which the API element existed
     */
    fun updateExtension(version: Int) {
        assert(version > 0)
        if (sinceExtension > version) {
            sinceExtension = version
        }
    }

    fun updateSdks(sdks: String?) {
        this.sdks = sdks
    }

    fun updateMainlineModule(module: String?) {
        mainlineModule = module
    }

    override fun compareTo(other: ApiElement): Int {
        return name.compareTo(other.name)
    }

    companion object {
        const val NEVER = Int.MAX_VALUE
    }
}
