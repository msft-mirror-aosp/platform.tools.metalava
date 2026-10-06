/*
 * Copyright (C) 2026 The Android Open Source Project
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

import androidx.tracing.Tracer
import com.android.tools.metalava.model.source.AbstractEnvironment
import com.android.tools.metalava.model.source.Environment
import com.android.tools.metalava.model.source.SourceSet
import com.android.tools.metalava.reporter.Reporter
import java.io.File

/** Turbine implementation of [Environment]. */
internal class TurbineEnvironment(
    environmentManager: TurbineEnvironmentManager,
    rawSourceSet: SourceSet,
    rawClassPath: List<File>,
    projectDescription: File?,
    javaLanguageLevel: String,
    kotlinLanguageLevel: String,
    jdkHome: File?,
    reporter: Reporter,
    tracer: Tracer,
) :
    AbstractEnvironment<TurbineEnvironmentManager>(
        environmentManager = environmentManager,
        rawSourceSet = rawSourceSet,
        rawClassPath = rawClassPath,
        projectDescription = projectDescription,
        javaLanguageLevel = javaLanguageLevel,
        kotlinLanguageLevel = kotlinLanguageLevel,
        jdkHome = jdkHome,
        reporter = reporter,
        tracer = tracer,
    )
