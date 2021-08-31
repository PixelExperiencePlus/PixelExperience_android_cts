/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.eventlib.events.deviceadminreceivers;

import static com.google.common.truth.Truth.assertThat;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;

import com.android.bedstead.nene.TestApis;
import com.android.eventlib.EventLogs;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DeviceAdminPasswordExpiringEventTest {

    private static final TestApis sTestApis = new TestApis();
    private static final Context sContext = sTestApis.context().instrumentedContext();
    private static final String STRING_VALUE = "Value";
    private static final String DIFFERENT_STRING_VALUE = "Value2";
    private static final Intent INTENT = new Intent();

    private static final String DEFAULT_DEVICE_ADMIN_RECEIVER_CLASS_NAME =
            TestDeviceAdminReceiver.class.getName();
    private static final String CUSTOM_DEVICE_ADMIN_RECEIVER_CLASS_NAME =
            "customDeviceAdminReceiver";
    private static final String DIFFERENT_CUSTOM_DEVICE_ADMIN_RECEIVER_CLASS_NAME =
            "customDeviceAdminReceiver2";
    private static final DeviceAdminReceiver DEVICE_ADMIN_RECEIVER = new TestDeviceAdminReceiver();
    private static final UserHandle USER_HANDLE = UserHandle.of(1);
    private static final UserHandle DIFFERENT_USER_HANDLE = UserHandle.of(2);

    private static class TestDeviceAdminReceiver extends DeviceAdminReceiver {
    }

    @Before
    public void setUp() {
        EventLogs.resetLogs();
    }

    @Test
    public void whereIntent_works() {
        Intent intent = new Intent(STRING_VALUE);
        DeviceAdminPasswordExpiringEvent.logger(DEVICE_ADMIN_RECEIVER, sContext, intent).log();

        EventLogs<DeviceAdminPasswordExpiringEvent> eventLogs =
                DeviceAdminPasswordExpiringEvent.queryPackage(sContext.getPackageName())
                        .whereIntent().action().isEqualTo(STRING_VALUE);

        assertThat(eventLogs.poll().intent()).isEqualTo(intent);
    }

    @Test
    public void whereIntent_skipsNonMatching() {
        Intent intent = new Intent(STRING_VALUE);
        Intent differentIntent = new Intent();
        differentIntent.setAction(DIFFERENT_STRING_VALUE);
        DeviceAdminPasswordExpiringEvent.logger(
                DEVICE_ADMIN_RECEIVER, sContext, differentIntent).log();
        DeviceAdminPasswordExpiringEvent.logger(DEVICE_ADMIN_RECEIVER, sContext, intent).log();

        EventLogs<DeviceAdminPasswordExpiringEvent> eventLogs =
                DeviceAdminPasswordExpiringEvent.queryPackage(sContext.getPackageName())
                        .whereIntent().action().isEqualTo(STRING_VALUE);

        assertThat(eventLogs.poll().intent()).isEqualTo(intent);
    }

    @Test
    public void whereDeviceAdminReceiver_customValueOnLogger_works() {
        DeviceAdminPasswordExpiringEvent.logger(DEVICE_ADMIN_RECEIVER, sContext, INTENT)
                .setDeviceAdminReceiver(CUSTOM_DEVICE_ADMIN_RECEIVER_CLASS_NAME)
                .log();

        EventLogs<DeviceAdminPasswordExpiringEvent> eventLogs =
                DeviceAdminPasswordExpiringEvent.queryPackage(sContext.getPackageName())
                        .whereDeviceAdminReceiver().broadcastReceiver().receiverClass().className()
                        .isEqualTo(CUSTOM_DEVICE_ADMIN_RECEIVER_CLASS_NAME);

        assertThat(eventLogs.poll().deviceAdminReceiver().className()).isEqualTo(
                CUSTOM_DEVICE_ADMIN_RECEIVER_CLASS_NAME);
    }

    @Test
    public void whereDeviceAdminReceiver_customValueOnLogger_skipsNonMatching() {
        DeviceAdminPasswordExpiringEvent.logger(DEVICE_ADMIN_RECEIVER, sContext, INTENT)
                .setDeviceAdminReceiver(DIFFERENT_CUSTOM_DEVICE_ADMIN_RECEIVER_CLASS_NAME)
                .log();
        DeviceAdminPasswordExpiringEvent.logger(DEVICE_ADMIN_RECEIVER, sContext, INTENT)
                .setDeviceAdminReceiver(CUSTOM_DEVICE_ADMIN_RECEIVER_CLASS_NAME)
                .log();

        EventLogs<DeviceAdminPasswordExpiringEvent> eventLogs =
                DeviceAdminPasswordExpiringEvent.queryPackage(sContext.getPackageName())
                        .whereDeviceAdminReceiver().broadcastReceiver().receiverClass().className()
                        .isEqualTo(CUSTOM_DEVICE_ADMIN_RECEIVER_CLASS_NAME);

        assertThat(eventLogs.poll().deviceAdminReceiver().className()).isEqualTo(
                CUSTOM_DEVICE_ADMIN_RECEIVER_CLASS_NAME);
    }

    @Test
    public void whereDeviceAdminReceiver_defaultValue_works() {
        DeviceAdminPasswordExpiringEvent.logger(DEVICE_ADMIN_RECEIVER, sContext, INTENT).log();

        EventLogs<DeviceAdminPasswordExpiringEvent> eventLogs =
                DeviceAdminPasswordExpiringEvent.queryPackage(sContext.getPackageName())
                        .whereDeviceAdminReceiver().broadcastReceiver().receiverClass().className()
                        .isEqualTo(DEFAULT_DEVICE_ADMIN_RECEIVER_CLASS_NAME);

        assertThat(eventLogs.poll().deviceAdminReceiver().className())
                .isEqualTo(DEFAULT_DEVICE_ADMIN_RECEIVER_CLASS_NAME);
    }

    @Test
    public void whereDeviceAdminReceiver_defaultValue_skipsNonMatching() {
        DeviceAdminPasswordExpiringEvent.logger(DEVICE_ADMIN_RECEIVER, sContext, INTENT)
                .setDeviceAdminReceiver(CUSTOM_DEVICE_ADMIN_RECEIVER_CLASS_NAME)
                .log();
        DeviceAdminPasswordExpiringEvent.logger(DEVICE_ADMIN_RECEIVER, sContext, INTENT)
                .log();

        EventLogs<DeviceAdminPasswordExpiringEvent> eventLogs =
                DeviceAdminPasswordExpiringEvent.queryPackage(sContext.getPackageName())
                        .whereDeviceAdminReceiver().broadcastReceiver().receiverClass().className()
                        .isEqualTo(DEFAULT_DEVICE_ADMIN_RECEIVER_CLASS_NAME);

        assertThat(eventLogs.poll().deviceAdminReceiver().className())
                .isEqualTo(DEFAULT_DEVICE_ADMIN_RECEIVER_CLASS_NAME);
    }

    @Test
    public void whereUserHandle_works() {
        DeviceAdminPasswordExpiringEvent.logger(DEVICE_ADMIN_RECEIVER, sContext, INTENT)
                .setUserHandle(USER_HANDLE)
                .log();

        EventLogs<DeviceAdminPasswordExpiringEvent> eventLogs =
                DeviceAdminPasswordExpiringEvent.queryPackage(sContext.getPackageName())
                        .whereUser().isEqualTo(USER_HANDLE);

        assertThat(eventLogs.poll().user()).isEqualTo(USER_HANDLE);
    }

    @Test
    public void whereUserHandle_skipsNonMatching() {
        DeviceAdminPasswordExpiringEvent.logger(DEVICE_ADMIN_RECEIVER, sContext, INTENT)
                .setUserHandle(DIFFERENT_USER_HANDLE)
                .log();
        DeviceAdminPasswordExpiringEvent.logger(DEVICE_ADMIN_RECEIVER, sContext, INTENT)
                .setUserHandle(USER_HANDLE)
                .log();

        EventLogs<DeviceAdminPasswordExpiringEvent> eventLogs =
                DeviceAdminPasswordExpiringEvent.queryPackage(sContext.getPackageName())
                        .whereUser().isEqualTo(USER_HANDLE);

        assertThat(eventLogs.poll().user()).isEqualTo(USER_HANDLE);
    }
}