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

import com.android.tools.metalava.model.IssueConfiguration

enum class ReleaseType(val flagName: String, private val displayName: String = flagName) {
    RELEASED("released", "released") {
        /**
         * Customization of the severities to apply when doing compatibility checking against the
         * previously released stable version of the API.
         */
        override fun getIssueConfiguration(): IssueConfiguration {
            return super.getIssueConfiguration().apply {
                error(Issues.ADDED_ABSTRACT_METHOD)
                hide(Issues.ADDED_CLASS)
                hide(Issues.ADDED_FIELD)
                hide(Issues.ADDED_FINAL_UNINSTANTIABLE)
                hide(Issues.ADDED_INTERFACE)
                hide(Issues.ADDED_METHOD)
                hide(Issues.ADDED_PACKAGE)
                hide(Issues.CHANGED_DEPRECATED)
                hide(Issues.CHANGED_SYNCHRONIZED)
                hide(Issues.REMOVED_FINAL)
            }
        }
    };

    /** Returns the error configuration to use for the given release type */
    open fun getIssueConfiguration(): IssueConfiguration {
        return IssueConfiguration().apply {
            error(Issues.ADDED_FINAL)
            error(Issues.CHANGED_STATIC)
            error(Issues.CHANGED_SUPERCLASS)
            error(Issues.CHANGED_TRANSIENT)
            error(Issues.CHANGED_TYPE)
            error(Issues.CHANGED_VALUE)
            error(Issues.CHANGED_VOLATILE)
            error(Issues.REMOVED_CLASS)
            error(Issues.REMOVED_FIELD)
            error(Issues.REMOVED_INTERFACE)
            error(Issues.REMOVED_METHOD)
            error(Issues.REMOVED_PACKAGE)
            error(Issues.ADDED_REIFIED)
        }
    }

    override fun toString(): String = displayName
}
