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

package com.android.tools.metalava.model.visitors

import com.android.tools.metalava.model.api.surface.ApiVariant
import com.android.tools.metalava.model.api.surface.ApiVariantType

/**
 * Types of APIs that can be processed.
 *
 * This correlates closely with the [ApiVariantType] type except while that relates to individual
 * [ApiVariant]s this relates to the whole API.
 */
enum class ApiType {
    /** The core API, i.e. the core part used by apps. */
    CORE,

    /** Parts of the API that used to be in [CORE] but have since been removed. */
    REMOVED,
}
