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

package com.android.tools.metalava.apilevels;

import com.android.tools.metalava.model.Codebase;
import com.android.tools.metalava.SdkIdentifier;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Main class for command line command to convert the existing API XML/TXT files into diff-based
 * simple text files.
 */
public class ApiGenerator {
    public static boolean generate(@NotNull File[] apiLevels,
                                   int firstApiLevel,
                                   int currentApiLevel,
                                   @NotNull File outputFile,
                                   @Nullable Codebase codebase,
                                   @Nullable File sdkJarRoot,
                                   @Nullable File sdkFilterFile) throws IOException, IllegalArgumentException {
        if ((sdkJarRoot == null) != (sdkFilterFile == null)) {
            throw new IllegalArgumentException("sdkJarRoot and sdkFilterFile must both be null, or non-null");
        }

        AndroidJarReader reader = new AndroidJarReader(apiLevels, firstApiLevel, codebase);
        Api api = reader.getApi();
        Set<SdkIdentifier> sdkIdentifiers = Collections.emptySet();
        if (sdkJarRoot != null && sdkFilterFile != null) {
            sdkIdentifiers = processExtensionSdkApis(api, currentApiLevel + 1, sdkJarRoot, sdkFilterFile);
        }
        return createApiFile(outputFile, api, sdkIdentifiers);
    }

    /**
     * Modify the extension SDK API parts of an API as dictated by a filter.
     *
     *   - remove APIs not listed in the filter
     *   - assign APIs listed in the filter their corresponding extensions
     *
     * Some APIs only exist in extension SDKs and not in the Android SDK, but for backwards
     * compatibility with tools that expect the Android SDK to be the only SDK, metalava needs to
     * assign such APIs some Android SDK API level. The recommended value is current-api-level + 1,
     * which is what non-finalized APIs use.
     *
     * @param api the api to modify
     * @param apiLevelNotInAndroidSdk fallback API level for APIs not in the Android SDK
     * @param sdkJarRoot path to directory containing extension SDK jars (usually $ANDROID_ROOT/prebuilts/sdk/extensions)
     * @param filterPath: path to the filter file. @see ApiToExtensionsMap
     * @throws IOException if the filter file can not be read
     * @throws IllegalArgumentException if an error is detected in the filter file, or if no jar files were found
     */
    private static Set<SdkIdentifier> processExtensionSdkApis(
            @NotNull Api api,
            int apiLevelNotInAndroidSdk,
            @NotNull File sdkJarRoot,
            @NotNull File filterPath) throws IOException, IllegalArgumentException {
        String rules = new String(Files.readAllBytes(filterPath.toPath()));

        Map<String, List<VersionAndPath>> map = ExtensionSdkJarReader.Companion.findExtensionSdkJarFiles(sdkJarRoot);
        if (map.isEmpty()) {
            throw new IllegalArgumentException("no extension sdk jar files found in " + sdkJarRoot);
        }
        for (Map.Entry<String, List<VersionAndPath>> entry : map.entrySet()) {
            String mainlineModule = entry.getKey();
            ApiToExtensionsMap extensionsMap = ApiToExtensionsMap.Companion.fromXml(mainlineModule, rules);
            ExtensionSdkJarReader sdkReader = new ExtensionSdkJarReader(mainlineModule, entry.getValue());
            Api sdkApi = sdkReader.getApi();

            for (ApiClass sdkClass : sdkApi.getClasses()) {
                ApiClass clazz = api.findClass(sdkClass.getName());
                if (clazz == null) {
                    List<String> extensions = extensionsMap.getExtensions(sdkClass);
                    if (extensions.isEmpty()) {
                        continue;
                    }
                    clazz = api.addClass(sdkClass.getName(), apiLevelNotInAndroidSdk, sdkClass.isDeprecated());
                }

                String clazzSdksAttr = extensionsMap.calculateSdksAttr(
                    clazz.getSince() != apiLevelNotInAndroidSdk ? clazz.getSince() : null,
                    extensionsMap.getExtensions(clazz),
                    sdkClass.getSince());
                clazz.updateMainlineModule(mainlineModule);
                clazz.updateSdks(clazzSdksAttr);

                Iterator<ApiElement> iter = clazz.getFieldIterator();
                while (iter.hasNext()) {
                    ApiElement field = iter.next();
                    ApiElement sdkField = sdkClass.getField(field.getName());
                    if (sdkField != null) {
                        String sdks = extensionsMap.calculateSdksAttr(field.getSince(),
                            extensionsMap.getExtensions(clazz, field), sdkField.getSince());
                        field.updateSdks(sdks);
                    } else {
                        // TODO: this is a new field that was added in the current REL version. What to do?
                        // Introduce something equivalent to ARG_CURRENT_VERSION?
                    }
                }

                iter = clazz.getMethodIterator();
                while (iter.hasNext()) {
                    ApiElement method = iter.next();
                    ApiElement sdkMethod = sdkClass.getMethod(method.getName());
                    if (sdkMethod != null) {
                        String sdks = extensionsMap.calculateSdksAttr(method.getSince(),
                            extensionsMap.getExtensions(clazz, method), sdkMethod.getSince());
                        method.updateSdks(sdks);
                    } else {
                        // TOOD: this is a new method that was added in the current REL version. What to do?
                        // Introduce something equivalent to ARG_CURRENT_VERSION?
                    }
                }
            }
        }
        return ApiToExtensionsMap.Companion.fromXml("", rules).getSdkIdentifiers();
    }

    /**
     * Creates the simplified diff-based API level.
     *
     * @param outFile        the output file
     * @param api            the api to write
     * @param sdkIdentifiers SDKs referenced by the api
     */
    private static boolean createApiFile(@NotNull File outFile, @NotNull Api api, @NotNull Set<SdkIdentifier> sdkIdentifiers) {
        File parentFile = outFile.getParentFile();
        if (!parentFile.exists()) {
            boolean ok = parentFile.mkdirs();
            if (!ok) {
                System.err.println("Could not create directory " + parentFile);
                return false;
            }
        }
        try (PrintStream stream = new PrintStream(outFile, "UTF-8")) {
            stream.println("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
            api.print(stream, sdkIdentifiers);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }
}
