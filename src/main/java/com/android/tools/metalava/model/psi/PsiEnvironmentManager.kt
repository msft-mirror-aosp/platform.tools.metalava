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

package com.android.tools.metalava.model.psi

import com.android.tools.lint.UastEnvironment
import com.android.tools.metalava.createProjectEnvironment
import com.android.tools.metalava.disposeUastEnvironment
import java.io.Closeable

/** Manages the [UastEnvironment] objects created when processing sources. */
class PsiEnvironmentManager : Closeable {

    /**
     * Determines whether the manager has been closed. Used to prevent creating new environments
     * after the manager has closed.
     */
    private var closed = false

    /**
     * Create a [UastEnvironment] with the supplied configuration.
     *
     * @throws IllegalStateException if this manager has been closed.
     */
    fun createEnvironment(config: UastEnvironment.Configuration): UastEnvironment {
        if (closed) {
            throw IllegalStateException("PsiEnvironmentManager is closed")
        }
        return createProjectEnvironment(config)
    }

    override fun close() {
        closed = true
        disposeUastEnvironment()
    }
}
