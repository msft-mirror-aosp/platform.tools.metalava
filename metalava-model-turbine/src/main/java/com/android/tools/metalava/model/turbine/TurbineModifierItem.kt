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
package com.android.tools.metalava.model.turbine

import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.ModifierList
import com.android.tools.metalava.model.MutableModifierList
import com.google.turbine.model.TurbineFlag

class TurbineModifierItem
internal constructor(
    codebase: Codebase,
    flags: Int = PACKAGE_PRIVATE,
    annotations: List<AnnotationItem>?
) :
    DefaultModifierList(codebase, flags, annotations?.toMutableList()),
    ModifierList,
    MutableModifierList {
    companion object {
        fun create(
            codebase: Codebase,
            flag: Int,
            annotations: List<AnnotationItem>?
        ): TurbineModifierItem {
            if (flag == 0) {
                // No Modifier. Default modifier is PACKAGE_PRIVATE in such case
                return TurbineModifierItem(codebase, annotations = annotations)
            }
            return TurbineModifierItem(codebase, computeFlag(flag), annotations)
        }

        /**
         * Given flag value corresponding to Turbine modifiers compute the equivalent flag in
         * Metalava.
         */
        private fun computeFlag(flag: Int): Int {
            var result = 0

            if (flag and TurbineFlag.ACC_STATIC != 0) {
                result = result or STATIC
            }
            if (flag and TurbineFlag.ACC_ABSTRACT != 0) {
                result = result or ABSTRACT
            }
            if (flag and TurbineFlag.ACC_FINAL != 0) {
                result = result or FINAL
            }
            if (flag and TurbineFlag.ACC_NATIVE != 0) {
                result = result or NATIVE
            }
            if (flag and TurbineFlag.ACC_SYNCHRONIZED != 0) {
                result = result or SYNCHRONIZED
            }
            if (flag and TurbineFlag.ACC_STRICT != 0) {
                result = result or STRICT_FP
            }
            if (flag and TurbineFlag.ACC_TRANSIENT != 0) {
                result = result or TRANSIENT
            }
            if (flag and TurbineFlag.ACC_VOLATILE != 0) {
                result = result or VOLATILE
            }
            if (flag and TurbineFlag.ACC_DEFAULT != 0) {
                result = result or DEFAULT
            }
            if (flag and TurbineFlag.ACC_SEALED != 0) {
                result = result or SEALED
            }

            // Visibility Modifiers
            if (flag and TurbineFlag.ACC_PUBLIC != 0) {
                result = result or PUBLIC
            }
            if (flag and TurbineFlag.ACC_PRIVATE != 0) {
                result = result or PRIVATE
            }
            if (flag and TurbineFlag.ACC_PROTECTED != 0) {
                result = result or PROTECTED
            }

            return result
        }
    }
}
