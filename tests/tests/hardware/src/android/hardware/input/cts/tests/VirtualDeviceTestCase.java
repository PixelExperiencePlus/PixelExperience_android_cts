/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.hardware.input.cts.tests;

import static org.junit.Assert.fail;

import android.app.ActivityOptions;
import android.companion.AssociationInfo;
import android.companion.CompanionDeviceManager;
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceParams;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.display.VirtualDisplay;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Surface;

import androidx.annotation.Nullable;
import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.Rule;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public abstract class VirtualDeviceTestCase extends InputTestCase {

    private static final int ARBITRARY_SURFACE_TEX_ID = 1;

    static final int DISPLAY_WIDTH = 100;
    static final int DISPLAY_HEIGHT = 100;

    // Uses:
    // Manifest.permission.CREATE_VIRTUAL_DEVICE,
    // Manifest.permission.ADD_TRUSTED_DISPLAY
    // These cannot be specified as part of the call as ADD_TRUSTED_DISPLAY is hidden and therefore
    // not visible to CTS.
    @Rule
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            InstrumentationRegistry.getInstrumentation().getUiAutomation());

    private final CountDownLatch mLatch = new CountDownLatch(1);
    private final InputManager.InputDeviceListener mInputDeviceListener =
            new InputManager.InputDeviceListener() {
                @Override
                public void onInputDeviceAdded(int deviceId) {
                    mLatch.countDown();
                }

                @Override
                public void onInputDeviceRemoved(int deviceId) {
                }

                @Override
                public void onInputDeviceChanged(int deviceId) {
                }
            };

    VirtualDeviceManager.VirtualDevice mVirtualDevice;
    VirtualDisplay mVirtualDisplay;

    @Override
    void onBeforeLaunchActivity() {
        final Context context = InstrumentationRegistry.getTargetContext();
        final String packageName = context.getPackageName();
        associateCompanionDevice(packageName);
        AssociationInfo associationInfo = null;
        for (AssociationInfo ai : context.getSystemService(CompanionDeviceManager.class)
                .getMyAssociations()) {
            if (packageName.equals(ai.getPackageName())) {
                associationInfo = ai;
                break;
            }
        }
        if (associationInfo == null) {
            fail("Could not create association for test");
            return;
        }
        final VirtualDeviceManager virtualDeviceManager =
                context.getSystemService(VirtualDeviceManager.class);
        mVirtualDevice = virtualDeviceManager.createVirtualDevice(associationInfo.getId(),
                new VirtualDeviceParams.Builder().build());
        mVirtualDisplay = mVirtualDevice.createVirtualDisplay(
                /* width= */ DISPLAY_WIDTH,
                /* height= */ DISPLAY_HEIGHT,
                /* dpi= */ 50,
                /* surface= */ new Surface(new SurfaceTexture(ARBITRARY_SURFACE_TEX_ID)),
                /* flags= */ 0,
                /* handler= */ null,
                /* callback= */ null);
    }

    @Override
    void onSetUp() {
        InstrumentationRegistry.getTargetContext().getSystemService(InputManager.class)
                .registerInputDeviceListener(mInputDeviceListener,
                        new Handler(Looper.getMainLooper()));
        onSetUpVirtualInputDevice();
        try {
            mLatch.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            fail("Virtual input device setup was interrupted");
        }
        // Wait for everything to settle. Like see in InputHidTestCase, registered input devices
        // don't always seem to produce events right away. Adding a bit of slack here decreases
        // the flake rate.
        SystemClock.sleep(1000L);
    }

    abstract void onSetUpVirtualInputDevice();

    abstract void onTearDownVirtualInputDevice();

    @Override
    void onTearDown() {
        onTearDownVirtualInputDevice();
        mTestActivity.finish();
        mVirtualDisplay.release();
        mVirtualDevice.close();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> InstrumentationRegistry.getTargetContext().getSystemService(
                                InputManager.class)
                        .unregisterInputDeviceListener(mInputDeviceListener));
        disassociateCompanionDevice();
    }

    @Override
    @Nullable Bundle getActivityOptions() {
        return ActivityOptions.makeBasic()
                .setLaunchDisplayId(mVirtualDisplay.getDisplay().getDisplayId())
                .toBundle();
    }

    private void associateCompanionDevice(String packageName) {
        // Associate this package for user 0 with a zeroed-out MAC address (not used in this test)
        SystemUtil.runShellCommand(
                String.format("cmd companiondevice associate 0 %s 00:00:00:00:00:00", packageName));
    }

    private void disassociateCompanionDevice() {
        SystemUtil.runShellCommand("cmd companiondevice disassociate 0 "
                + InstrumentationRegistry.getTargetContext().getPackageName()
                + " 00:00:00:00:00:00");
    }
}