/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.tools.metalava.model.source.doc

/**
 * Provides contextual information from the surrounding model for use when processing a
 * [DocComment].
 *
 * This purposely does not include [DocumentationIssueReporter] as there are multiple instances of
 * that created at different levels within the [DocComment] whereas this applies to the whole
 * [DocComment].
 */
internal interface DocCommentContext
