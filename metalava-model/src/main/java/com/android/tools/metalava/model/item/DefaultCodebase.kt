/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.tools.metalava.model.item

import com.android.tools.metalava.model.AbstractCodebase
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.AnnotationManager
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.DefaultAnnotationItem
import com.android.tools.metalava.model.Item
import java.io.File

/**
 * Base class of [Codebase]s for the models that do not incorporate their underlying model, if any,
 * into their [Item] implementations.
 */
abstract class DefaultCodebase(
    location: File,
    description: String,
    preFiltered: Boolean,
    annotationManager: AnnotationManager,
) :
    AbstractCodebase(
        location,
        description,
        preFiltered,
        annotationManager,
    ) {

    final override fun createAnnotation(
        source: String,
        context: Item?,
    ): AnnotationItem? {
        return DefaultAnnotationItem.create(this, source)
    }
}
