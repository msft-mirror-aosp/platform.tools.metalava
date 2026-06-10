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

class StringPolicyAnnotationHandlerTest : DriverTest() {
    @Test
    fun `Test StringPolicyDefinition generates docs`() {
        check(
            sourceFiles =
                arrayOf(
                    ANDROID_MANIFEST_SOURCE,
                    POLICY_DEFINITION_SOURCE,
                    java(
                        """
                        package test.pkg;
                        import android.processor.devicepolicy.StringPolicyDefinition;
                        import android.processor.devicepolicy.PolicyDefinition;
                        import android.processor.devicepolicy.AllowedDpcTypes;
                        import android.processor.devicepolicy.AllowedRoles;
                        import static android.processor.devicepolicy.AllowedDpcTypes.ALLOWED;
                        import static android.processor.devicepolicy.AllowedDpcTypes.DISALLOWED;

                        @Retention(RetentionPolicy.SOURCE)
                        public class TestPolicy {
                            private static final int SCOPE_USER = 1;
                            private static final int SCOPE_DEVICE = 2;
                            private static final int RESOURCE_DEVICE_WIDE = 1;
                          /**
                           * A test policy for string policy definition with multiple scopes.
                           */
                            @StringPolicyDefinition(
                                base = @PolicyDefinition(
                                    allowedScopes = {SCOPE_USER},
                                    affectedResource = RESOURCE_DEVICE_WIDE,
                                    requiredPermission = android.Manifest.permission.TEST,
                                    requiredCrossUserPermission = android.Manifest.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS,
                                    allowedDpcTypes = @AllowedDpcTypes(
                                        deviceOwner = DISALLOWED,
                                        managedProfileOwnerOfOrganizationOwnedDevice = DISALLOWED,
                                        managedProfileOwnerOfPersonalOwnedDevice = DISALLOWED,
                                        profileOwnerOnUser0 = ALLOWED,
                                        fullUserProfileOwner = DISALLOWED
                                    ),
                                    allowedRoles = @AllowedRoles(
                                        deviceController = AllowedRoles.ALLOWED
                                    )
                                ),
                                emptyStringAllowed = true,
                                unprintableCharactersAllowed = true,
                                pureWhitespaceAllowed = true,
                                unstrippedStringAllowed = true,
                                maxLength = 100
                            )
                            public static final String POLICY_FIELD = "";
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
                         * A test policy for string policy definition with multiple scopes.
                         * <br>
                         * <p>Policy Type: String</p>
                         * <table>
                         *  <tr>
                         *    <th colspan="2">Policy details</th>
                         *  </tr>
                         *  <tr>
                         *    <td>Allowed Scopes</td>
                         *    <td>
                         *      <ul>
                         *        <li>User. Settable by:
                         *          <ul>
                         *            <li>Profile Owner on User 0</li>
                         *          </ul>
                         *        </li>
                         *      </ul>
                         *    </td>
                         *  </tr>
                         *  <tr>
                         *    <td>Affected Resource</td>
                         *    <td>Device Wide</td>
                         *  </tr>
                         *  <tr>
                         *    <td>Required Permission</td>
                         *    <td>{@link android.Manifest.permission#TEST android.permission.TEST}</td>
                         *  </tr>
                         *  <tr>
                         *    <td>Required Cross User Permission</td>
                         *    <td>{@link android.Manifest.permission#MANAGE_DEVICE_POLICY_ACROSS_USERS android.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS}</td>
                         *  </tr>
                         *  <tr>
                         *    <td>Allowed DPC Types</td>
                         *    <td>
                         *      <ul>
                         *        <li>Profile Owner on User 0</li>
                         *      </ul>
                         *    </td>
                         *  </tr>
                         *  <tr>
                         *    <td>Allowed Roles</td>
                         *    <td>This policy can be set by holders of the device controller role</td>
                         *  </tr>
                         *  <tr>
                         *    <td>Policy value</td>
                         *    <td>
                         *      <code>String</code> with the following restrictions:
                         *      <ul>
                         *        <li>Empty string: Allowed</li>
                         *        <li>Unprintable characters: Allowed</li>
                         *        <li>Pure whitespace: Allowed</li>
                         *        <li>Unstripped string: Allowed</li>
                         *        <li>Max Length: 100</li>
                         *      </ul>
                         *    </td>
                         *  </tr>
                         * </table>
                         */
                        public static final java.lang.String POLICY_FIELD = "";
                        }
                        """
                    )
                )
        )
    }

    @Test
    fun `Test StringPolicyDefinition with scope not settable by any DPC type generates docs`() {
        check(
            sourceFiles =
                arrayOf(
                    ANDROID_MANIFEST_SOURCE,
                    POLICY_DEFINITION_SOURCE,
                    java(
                        """
                        package test.pkg;
                        import android.processor.devicepolicy.StringPolicyDefinition;
                        import android.processor.devicepolicy.PolicyDefinition;
                        import android.processor.devicepolicy.AllowedDpcTypes;
                        import static android.processor.devicepolicy.AllowedDpcTypes.ALLOWED;
                        import static android.processor.devicepolicy.AllowedDpcTypes.DISALLOWED;

                        @Retention(RetentionPolicy.SOURCE)
                        public class TestPolicy {
                            private static final int SCOPE_USER = 1;
                            private static final int SCOPE_PARENT_USER = 3;
                            private static final int RESOURCE_DEVICE_WIDE = 1;
                          /**
                           * A test policy for string policy definition with a scope not settable by any DPC.
                           */
                            @StringPolicyDefinition(
                                base = @PolicyDefinition(
                                    allowedScopes = {SCOPE_USER, SCOPE_PARENT_USER},
                                    affectedResource = RESOURCE_DEVICE_WIDE,
                                    requiredPermission = android.Manifest.permission.TEST,
                                    requiredCrossUserPermission = android.Manifest.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS,
                                    allowedDpcTypes = @AllowedDpcTypes(
                                        deviceOwner = DISALLOWED,
                                        managedProfileOwnerOfOrganizationOwnedDevice = DISALLOWED,
                                        managedProfileOwnerOfPersonalOwnedDevice = DISALLOWED,
                                        profileOwnerOnUser0 = ALLOWED,
                                        fullUserProfileOwner = DISALLOWED
                                    )
                                ),
                                emptyStringAllowed = true,
                                unprintableCharactersAllowed = true,
                                maxLength = 100
                            )
                            public static final String POLICY_FIELD = "";
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
                         * A test policy for string policy definition with a scope not settable by any DPC.
                         * <br>
                         * <p>Policy Type: String</p>
                         * <table>
                         *  <tr>
                         *    <th colspan="2">Policy details</th>
                         *  </tr>
                         *  <tr>
                         *    <td>Allowed Scopes</td>
                         *    <td>
                         *      <ul>
                         *        <li>User. Settable by:
                         *          <ul>
                         *            <li>Profile Owner on User 0</li>
                         *          </ul>
                         *        </li>
                         *        <li>Parent User. Not settable by any DPC type.</li>
                         *      </ul>
                         *    </td>
                         *  </tr>
                         *  <tr>
                         *    <td>Affected Resource</td>
                         *    <td>Device Wide</td>
                         *  </tr>
                         *  <tr>
                         *    <td>Required Permission</td>
                         *    <td>{@link android.Manifest.permission#TEST android.permission.TEST}</td>
                         *  </tr>
                         *  <tr>
                         *    <td>Required Cross User Permission</td>
                         *    <td>{@link android.Manifest.permission#MANAGE_DEVICE_POLICY_ACROSS_USERS android.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS}</td>
                         *  </tr>
                         *  <tr>
                         *    <td>Allowed DPC Types</td>
                         *    <td>
                         *      <ul>
                         *        <li>Profile Owner on User 0</li>
                         *      </ul>
                         *    </td>
                         *  </tr>
                         *  <tr>
                         *    <td>Policy value</td>
                         *    <td>
                         *      <code>String</code> with the following restrictions:
                         *      <ul>
                         *        <li>Empty string: Allowed</li>
                         *        <li>Unprintable characters: Allowed</li>
                         *        <li>Pure whitespace: Not allowed</li>
                         *        <li>Unstripped string: Not allowed</li>
                         *        <li>Max Length: 100</li>
                         *      </ul>
                         *    </td>
                         *  </tr>
                         * </table>
                         */
                        public static final java.lang.String POLICY_FIELD = "";
                        }
                        """
                    )
                )
        )
    }
}
