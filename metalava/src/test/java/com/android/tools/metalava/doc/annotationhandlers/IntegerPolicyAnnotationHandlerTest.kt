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

package com.android.tools.metalava.doc.annotationhandlers

import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.doc.annotationhandlers.PolicyDefinitionAnnotationTestFiles.ANDROID_MANIFEST_SOURCE
import com.android.tools.metalava.doc.annotationhandlers.PolicyDefinitionAnnotationTestFiles.POLICY_DEFINITION_SOURCE
import com.android.tools.metalava.testing.java
import org.junit.Test

class IntegerPolicyAnnotationHandlerTest : DriverTest() {

    @Test
    fun `Test IntegerPolicyDefinition generates docs`() {
        check(
            sourceFiles =
                arrayOf(
                    POLICY_DEFINITION_SOURCE,
                    ANDROID_MANIFEST_SOURCE,
                    java(
                        """
                        package test.pkg;
                        import android.processor.devicepolicy.IntegerPolicyDefinition;
                        import android.processor.devicepolicy.IntegerResolutionMechanism;
                        import android.processor.devicepolicy.PolicyDefinition;
                        import android.processor.devicepolicy.AllowedDpcTypes;
                        import static android.processor.devicepolicy.AllowedDpcTypes.ALLOWED;

                        @Retention(RetentionPolicy.SOURCE)
                        public class TestPolicy {
                            private static final int SCOPE_USER = 1;
                            private static final int RESOURCE_DEVICE_WIDE = 1;
                            private static final int DEFAULT_VALUE = 1;
                          /**
                           * A test policy for integer policy definition.
                           *
                           * Some other human handwritten comments.
                           */
                            @IntegerPolicyDefinition(
                                base = @PolicyDefinition(
                                    allowedScopes = {SCOPE_USER},
                                    affectedResource = RESOURCE_DEVICE_WIDE,
                                    requiredPermission = "android.permission.TEST",
                                    allowedDpcTypes = @AllowedDpcTypes(
                                        deviceOwner = ALLOWED,
                                        managedProfileOwnerOfOrganizationOwnedDevice = ALLOWED,
                                        managedProfileOwnerOfPersonalOwnedDevice = ALLOWED,
                                        profileOwnerOnUser0 = ALLOWED,
                                        fullUserProfileOwner = ALLOWED
                                    )
                                ),
                                minValue = 10,
                                maxValue = 100,
                                resolutionMechanism = @IntegerResolutionMechanism(custom = true)
                            )
                            public static final int POLICY_FIELD = 1;
                        }
                        """
                    )
                ),
            checkCompilation = true,
            docStubs = true,
            expectedStubFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;
                        @SuppressWarnings({"unchecked", "deprecation", "all"})
                        public class TestPolicy {
                        public TestPolicy() { throw new RuntimeException("Stub!"); }
                        /**
                         * A test policy for integer policy definition.
                         *
                         * Some other human handwritten comments.
                         * <br>
                         * <p>Policy Type: Integer</p>
                         * <ul>
                         *   <li>Allowed Scopes:
                         *    <ul>
                         *       <li>User</li>
                         *     </ul>
                         *   </li>
                         *   <li>Affected Resource: Device Wide</li>
                         *   <li>Required Permission: {@link android.Manifest.permission#TEST android.permission.TEST}</li>
                         *   <li>Allowed DPC Types:
                         *    <ul>
                         *       <li>Device Owner</li>
                         *       <li>Managed Profile Owner (Of Organization Owned Device)</li>
                         *       <li>Managed Profile Owner (Of Personally Owned Device)</li>
                         *       <li>Unaffiliated Full User Profile Owner</li>
                         *       <li>Profile Owner on User 0</li>
                         *       <li>Affiliated Full User Profile Owner</li>
                         *     </ul>
                         *   </li>
                         *   <li>Resolution Mechanism: custom</li>
                         *   <li>Min Value: 10</li>
                         *   <li>Max Value: 100</li>
                         * </ul>
                         */
                        public static final int POLICY_FIELD = 1;
                        }
                        """
                    )
                )
        )
    }
}
