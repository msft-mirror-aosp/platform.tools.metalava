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
                        import android.processor.devicepolicy.AllowedRoles;
                        import static android.processor.devicepolicy.AllowedDpcTypes.ALLOWED;
                        import static android.Manifest.permission.TEST;
                        import static android.Manifest.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS;

                        @Retention(RetentionPolicy.SOURCE)
                        public class TestPolicy {
                            private static final int SCOPE_USER = 1;
                            private static final int SCOPE_DEVICE = 2;
                            private static final int RESOURCE_DEVICE_WIDE = 1;
                          /**
                           * A test policy for integer policy definition with multiple scopes.
                           */
                            @IntegerPolicyDefinition(
                                base = @PolicyDefinition(
                                    allowedScopes = {SCOPE_USER, SCOPE_DEVICE},
                                    affectedResource = RESOURCE_DEVICE_WIDE,
                                    requiredPermission = TEST,
                                    requiredCrossUserPermission = MANAGE_DEVICE_POLICY_ACROSS_USERS,
                                    allowedDpcTypes = @AllowedDpcTypes(
                                        deviceOwner = ALLOWED,
                                        managedProfileOwnerOfOrganizationOwnedDevice = ALLOWED,
                                        managedProfileOwnerOfPersonalOwnedDevice = ALLOWED,
                                        profileOwnerOnUser0 = ALLOWED,
                                        fullUserProfileOwner = ALLOWED
                                    ),
                                    allowedRoles = @AllowedRoles(
                                        deviceController = AllowedRoles.ALLOWED
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
                         * A test policy for integer policy definition with multiple scopes.
                         * <br>
                         * <p>Policy Type: Integer</p>
                         * <table>
                         *  <tr>
                         *    <th colspan="2">Policy details</th>
                         *  </tr>
                         *  <tr>
                         *    <td>Settable by</td>
                         *    <td>
                         *      <p>This policy can be set with scope <code>User</code> by anyone holding {@link android.Manifest.permission#TEST android.permission.TEST}, or the following DPC types:
                         *      <ul>
                         *          <li>Device Owner</li>
                         *          <li>Managed Profile Owner (Of Organization Owned Device)</li>
                         *          <li>Managed Profile Owner (Of Personally Owned Device)</li>
                         *          <li>Unaffiliated Full User Profile Owner</li>
                         *          <li>Profile Owner on User 0</li>
                         *          <li>Affiliated Full User Profile Owner</li>
                         *      </ul>
                         *      </p>
                         *      <p>In addition, this policy can be set with scope <code>Device</code> by anyone holding {@link android.Manifest.permission#TEST android.permission.TEST} and {@link android.Manifest.permission#MANAGE_DEVICE_POLICY_ACROSS_USERS android.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS}, or the following DPC types:
                         *      <ul>
                         *          <li>Device Owner</li>
                         *          <li>Managed Profile Owner (Of Organization Owned Device)</li>
                         *      </ul>
                         *      </p>
                         *    </td>
                         *  </tr>
                         *  <tr>
                         *    <td>Affected Resource</td>
                         *    <td>Device Wide</td>
                         *  </tr>
                         *  <tr>
                         *    <td>Resolution Mechanism</td>
                         *    <td>custom</td>
                         *  </tr>
                         *  <tr>
                         *    <td>Policy value</td>
                         *    <td>
                         *      <code>Integer</code> with the following restrictions:
                         *      <ul>
                         *        <li>Min Value: 10</li>
                         *        <li>Max Value: 100</li>
                         *      </ul>
                         *    </td>
                         *  </tr>
                         * </table>
                         * See also: {@link android.app.admin.DevicePolicyManager#setPolicy DevicePolicyManager.setPolicy}, {@link android.app.admin.DevicePolicyManager#getPolicy DevicePolicyManager.getPolicy}
                         */
                        public static final int POLICY_FIELD = 1;
                        }
                        """
                    )
                )
        )
    }
}
