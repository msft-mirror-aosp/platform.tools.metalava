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
import com.android.tools.metalava.testing.java
import org.junit.Test

class StringPolicyAnnotationHandlerTest : DriverTest() {
    private val ANDROID_MANIFEST_SOURCE =
        java(
            """
            package android;

            public final class Manifest {
                public static final class permission {
                    public static final String TEST = "android.permission.TEST";
                    public static final String ENUM_TEST = "android.permission.ENUM_TEST";
                    public static final String BOOLEAN_TEST = "android.permission.BOOLEAN_TEST";
                    public static final String INTEGER_TEST = "android.permission.INTEGER_TEST";
                    public static final String LONG_TEST = "android.permission.LONG_TEST";
                    public static final String LIST_TEST = "android.permission.LIST_TEST";
                }
            }
            """
        )

    private val POLICY_DEFINITION_SOURCE =
        java(
            """
            package android.processor.devicepolicy;

            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;

            @Retention(RetentionPolicy.SOURCE)
            public @interface PolicyDefinition {
                int[] allowedScopes() default {};
                int affectedResource() default 0;
                String requiredPermission() default "";
                String requiredCrossUserPermission() default "";
                AllowedDpcTypes allowedDpcTypes() default @AllowedDpcTypes();
            }

            @Retention(RetentionPolicy.SOURCE)
            public @interface AllowedDpcTypes {
                int deviceOwner() default 2;
                int managedProfileOwnerOfOrganizationOwnedDevice() default 2;
                int managedProfileOwnerOfPersonalOwnedDevice() default 2;
                int unaffiliatedFullUserProfileOwner() default 2;
                int financedDeviceOwner() default 2;
                int profileOwnerOnUser0() default 2;
                int affiliatedFullUserProfileOwner() default 3;
            }

            @Retention(RetentionPolicy.SOURCE)
            public @interface StringPolicyDefinition {
                PolicyDefinition base();
                boolean emptyStringAllowed() default false;
                boolean unprintableCharactersAllowed() default false;
                int maxLength() default Integer.MAX_VALUE;
            }
            """
        )

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

                        @Retention(RetentionPolicy.SOURCE)
                        public class TestPolicy {
                            private static final int SCOPE_USER = 1;
                            private static final int RESOURCE_DEVICE_WIDE = 1;
                            private static final int DEFAULT_VALUE = 1;
                          /**
                           * A test policy for string policy definition.
                           *
                           * Some other human handwritten comments.
                           */
                            @StringPolicyDefinition(
                                base = @PolicyDefinition(
                                    allowedScopes = {SCOPE_USER},
                                    affectedResource = RESOURCE_DEVICE_WIDE,
                                    requiredPermission = "android.permission.TEST"
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
            stubFiles =
                arrayOf(
                    java(
                        """
                        package test.pkg;
                        @SuppressWarnings({"unchecked", "deprecation", "all"})
                        public class TestPolicy {
                        public TestPolicy() { throw new RuntimeException("Stub!"); }
                        /**
                         * A test policy for string policy definition.
                         *
                         * Some other human handwritten comments.
                         */
                        public static final java.lang.String POLICY_FIELD = "";
                        }
                        """
                    )
                )
        )
    }
}
