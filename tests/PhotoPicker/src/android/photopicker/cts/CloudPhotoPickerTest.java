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

package android.photopicker.cts;

import static android.os.SystemProperties.getBoolean;
import static android.photopicker.cts.PickerProviderMediaGenerator.MediaGenerator;
import static android.photopicker.cts.PickerProviderMediaGenerator.setCloudProvider;
import static android.photopicker.cts.PickerProviderMediaGenerator.syncCloudProvider;
import static android.photopicker.cts.util.PhotoPickerAssertionsUtils.assertRedactedReadOnlyAccess;
import static android.photopicker.cts.util.PhotoPickerFilesUtils.createImages;
import static android.photopicker.cts.util.PhotoPickerFilesUtils.deleteMedia;
import static android.photopicker.cts.util.PhotoPickerUiUtils.findAddButton;
import static android.photopicker.cts.util.PhotoPickerUiUtils.findItemList;
import static android.provider.MediaStore.PickerMediaColumns;

import static com.google.common.truth.Truth.assertThat;

import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.photopicker.cts.cloudproviders.CloudProviderNoIntentFilter;
import android.photopicker.cts.cloudproviders.CloudProviderNoPermission;
import android.photopicker.cts.cloudproviders.CloudProviderPrimary;
import android.photopicker.cts.cloudproviders.CloudProviderSecondary;
import android.provider.MediaStore;
import android.util.Pair;

import androidx.test.filters.SdkSuppress;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.UiObject;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * Photo Picker Device only tests for common flows.
 */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 31, codeName = "S")
public class CloudPhotoPickerTest extends PhotoPickerBaseTest {
    private final List<Uri> mUriList = new ArrayList<>();
    private MediaGenerator mCloudPrimaryMediaGenerator;
    private MediaGenerator mCloudSecondaryMediaGenerator;

    private static final long IMAGE_SIZE_BYTES = 107684;

    private static final String VERSION_1 = "VERSION_1";
    private static final String VERSION_2 = "VERSION_2";

    private static final String CLOUD_ID1 = "CLOUD_ID1";
    private static final String CLOUD_ID2 = "CLOUD_ID2";

    @Before
    public void setUp() throws Exception {
        super.setUp();

        mCloudPrimaryMediaGenerator = PickerProviderMediaGenerator.getMediaGenerator(
                mContext, CloudProviderPrimary.AUTHORITY);
        mCloudSecondaryMediaGenerator = PickerProviderMediaGenerator.getMediaGenerator(
                mContext, CloudProviderSecondary.AUTHORITY);

        mCloudPrimaryMediaGenerator.resetAll();
        mCloudSecondaryMediaGenerator.resetAll();

        mCloudPrimaryMediaGenerator.setVersion(VERSION_1);
        mCloudSecondaryMediaGenerator.setVersion(VERSION_1);

        setCloudProvider(mContext, null);

        Assume.assumeTrue(getBoolean("sys.photopicker.pickerdb.enabled", true));
    }

    @After
    public void tearDown() throws Exception {
        for (Uri uri : mUriList) {
            deleteMedia(uri, mContext.getUserId());
        }
        mActivity.finish();
        mUriList.clear();
        setCloudProvider(mContext, null);
    }

    @Test
    public void testCloudOnlySync() throws Exception {
        initPrimaryCloudProviderWithImage(Pair.create(null, CLOUD_ID1));

        final ClipData clipData = fetchPickerMedia(1);
        final List<String> mediaIds = extractMediaIds(clipData, 1);

        assertThat(mediaIds).containsExactly(CLOUD_ID1);
    }

    @Test
    public void testCloudPlusLocalSyncWithoutDedupe() throws Exception {
        createImages(1, mContext.getUserId(), mUriList);
        initPrimaryCloudProviderWithImage(Pair.create(null, CLOUD_ID1));

        final ClipData clipData = fetchPickerMedia(2);
        final List<String> mediaIds = extractMediaIds(clipData, 2);

        assertThat(mediaIds).containsExactly(CLOUD_ID1, mUriList.get(0).getLastPathSegment());
    }

    @Test
    public void testCloudPlusLocalSyncWithDedupe() throws Exception {
        createImages(1, mContext.getUserId(), mUriList);
        initPrimaryCloudProviderWithImage(Pair.create(mUriList.get(0).getLastPathSegment(),
                        CLOUD_ID1));

        final ClipData clipData = fetchPickerMedia(1);
        final List<String> mediaIds = extractMediaIds(clipData, 1);

        containsExcept(mediaIds, mUriList.get(0).getLastPathSegment(), CLOUD_ID1);
    }

    @Test
    public void testDeleteCloudMedia() throws Exception {
        initPrimaryCloudProviderWithImage(Pair.create(null, CLOUD_ID1),
                Pair.create(null, CLOUD_ID2));

        ClipData clipData = fetchPickerMedia(2);
        List<String> mediaIds = extractMediaIds(clipData, 2);

        assertThat(mediaIds).containsExactly(CLOUD_ID1, CLOUD_ID2);

        mCloudPrimaryMediaGenerator.deleteMedia(/* localId */ null, CLOUD_ID1,
                /* trackDeleted */ true);
        syncCloudProvider(mContext);

        clipData = fetchPickerMedia(2);
        mediaIds = extractMediaIds(clipData, 1);

        containsExcept(mediaIds, CLOUD_ID2, CLOUD_ID1);
    }

    @Test
    public void testVersionChange() throws Exception {
        initPrimaryCloudProviderWithImage(Pair.create(null, CLOUD_ID1),
                Pair.create(null, CLOUD_ID2));

        ClipData clipData = fetchPickerMedia(2);
        List<String> mediaIds = extractMediaIds(clipData, 2);

        assertThat(mediaIds).containsExactly(CLOUD_ID1, CLOUD_ID2);

        mCloudPrimaryMediaGenerator.deleteMedia(/* localId */ null, CLOUD_ID1,
                /* trackDeleted */ false);
        syncCloudProvider(mContext);

        clipData = fetchPickerMedia(2);
        mediaIds = extractMediaIds(clipData, 2);

        assertThat(mediaIds).containsExactly(CLOUD_ID1, CLOUD_ID2);

        mCloudPrimaryMediaGenerator.setVersion(VERSION_2);
        syncCloudProvider(mContext);

        clipData = fetchPickerMedia(2);
        mediaIds = extractMediaIds(clipData, 1);

        containsExcept(mediaIds, CLOUD_ID2, CLOUD_ID1);
    }

    @Test
    public void testProviderSwitchSuccess() throws Exception {
        setCloudProvider(mContext, CloudProviderPrimary.AUTHORITY);
        assertThat(MediaStore.getCloudProvider(mContext.getContentResolver()))
                .isEqualTo(CloudProviderPrimary.AUTHORITY);

        addImage(mCloudPrimaryMediaGenerator, /* localId */ null, CLOUD_ID1);
        addImage(mCloudSecondaryMediaGenerator, /* localId */ null, CLOUD_ID2);

        syncCloudProvider(mContext);

        ClipData clipData = fetchPickerMedia(2);
        List<String> mediaIds = extractMediaIds(clipData, 1);

        containsExcept(mediaIds, CLOUD_ID1, CLOUD_ID2);

        setCloudProvider(mContext, CloudProviderSecondary.AUTHORITY);
        assertThat(MediaStore.getCloudProvider(mContext.getContentResolver()))
                .isEqualTo(CloudProviderSecondary.AUTHORITY);

        clipData = fetchPickerMedia(2);
        mediaIds = extractMediaIds(clipData, 1);

        containsExcept(mediaIds, CLOUD_ID2, CLOUD_ID1);
    }

    @Test
    public void testProviderSwitchFailure() throws Exception {
        setCloudProvider(mContext, CloudProviderNoIntentFilter.AUTHORITY);
        assertThat(MediaStore.getCloudProvider(mContext.getContentResolver()))
                .isEqualTo(null);

        setCloudProvider(mContext, CloudProviderNoPermission.AUTHORITY);
        assertThat(MediaStore.getCloudProvider(mContext.getContentResolver()))
                .isEqualTo(null);
    }

    @Test
    public void testUriAccess() throws Exception {
        initPrimaryCloudProviderWithImage(Pair.create(null, CLOUD_ID1));

        final ClipData clipData = fetchPickerMedia(1);
        final List<String> mediaIds = extractMediaIds(clipData, 1);

        assertThat(mediaIds).containsExactly(CLOUD_ID1);

        final ContentResolver resolver = mContext.getContentResolver();
        String expectedDisplayName = CLOUD_ID1 + ".jpg";

        try (Cursor c = resolver.query(clipData.getItemAt(0).getUri(), null, null, null)) {
            assertThat(c).isNotNull();
            assertThat(c.moveToFirst()).isTrue();

            assertThat(c.getString(c.getColumnIndex(PickerMediaColumns.MIME_TYPE)))
                    .isEqualTo("image/jpeg");
            assertThat(c.getString(c.getColumnIndex(PickerMediaColumns.DISPLAY_NAME)))
                    .isEqualTo(expectedDisplayName);
            assertThat(c.getLong(c.getColumnIndex(PickerMediaColumns.SIZE)))
                    .isEqualTo(IMAGE_SIZE_BYTES);
            assertThat(c.getLong(c.getColumnIndex(PickerMediaColumns.DURATION_MILLIS)))
                    .isEqualTo(0);
            assertThat(c.getLong(c.getColumnIndex(PickerMediaColumns.DATE_TAKEN)))
                    .isGreaterThan(0);

            final File file = new File(c.getString(c.getColumnIndex(PickerMediaColumns.DATA)));
            assertThat(file.getPath().endsWith(expectedDisplayName)).isTrue();
            assertThat(file.length()).isEqualTo(IMAGE_SIZE_BYTES);
        }

        assertRedactedReadOnlyAccess(clipData.getItemAt(0).getUri());
    }

    @Ignore("b/215187981: For some reason, it hits a timeout and crashes the other tests on cf")
    @Test
    public void testCloudEventNotification() throws Exception {
        // Cloud provider isn't set
        assertThat(MediaStore.getCloudProvider(mContext.getContentResolver()))
                .isEqualTo(null);
        addImage(mCloudPrimaryMediaGenerator, /* localId */ null, CLOUD_ID1);

        // Notification fails because the calling cloud provider isn't enabled
        assertThat(MediaStore.notifyCloudEvent(mContext.getContentResolver()))
                .isFalse();

        // Sleep because the notification API throttles requests with a 1s delay
        Thread.sleep(1500);

        ClipData clipData = fetchPickerMedia(1);
        List<String> mediaIds = extractMediaIds(clipData, 1);

        assertThat(mediaIds).containsNoneIn(Collections.singletonList(CLOUD_ID1));

        // Now set the cloud provider and verify that notification succeeds
        setCloudProvider(mContext, CloudProviderPrimary.AUTHORITY);
        assertThat(MediaStore.getCloudProvider(mContext.getContentResolver()))
                .isEqualTo(CloudProviderPrimary.AUTHORITY);
        assertThat(MediaStore.notifyCloudEvent(mContext.getContentResolver()))
                .isTrue();

        // Sleep because the notification API throttles requests with a 1s delay
        Thread.sleep(1500);

        clipData = fetchPickerMedia(1);
        mediaIds = extractMediaIds(clipData, 1);

        assertThat(mediaIds).containsExactly(CLOUD_ID1);
    }

    private List<String> extractMediaIds(ClipData clipData, int minCount) {
        final int count = clipData.getItemCount();
        assertThat(count).isAtLeast(minCount);

        final List<String> mediaIds = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            mediaIds.add(clipData.getItemAt(i).getUri().getLastPathSegment());
        }

        return mediaIds;
    }

    private ClipData fetchPickerMedia(int maxCount) throws Exception {
        final Intent intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        // TODO(b/205291616): Replace 100 with MediaStore.getPickImagesMaxLimit()
        intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, 100);
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        final List<UiObject> itemList = findItemList(maxCount);
        for (int i = 0; i < itemList.size(); i++) {
            final UiObject item = itemList.get(i);
            item.click();
            mDevice.waitForIdle();
        }

        final UiObject addButton = findAddButton();
        addButton.click();
        mDevice.waitForIdle();

        return mActivity.getResult().data.getClipData();
    }

    private void initPrimaryCloudProviderWithImage(Pair<String, String>... mediaPairs)
            throws Exception {
        setCloudProvider(mContext, CloudProviderPrimary.AUTHORITY);
        assertThat(MediaStore.getCloudProvider(mContext.getContentResolver()))
                .isEqualTo(CloudProviderPrimary.AUTHORITY);

        for (Pair<String, String> pair: mediaPairs) {
            addImage(mCloudPrimaryMediaGenerator, pair.first, pair.second);
        }

        syncCloudProvider(mContext);
    }

    private void addImage(MediaGenerator generator, String localId, String cloudId)
            throws Exception {
        generator.addMedia(localId, cloudId, /* albumId */ null, "image/jpeg",
                /* mimeTypeExtension */ 0, IMAGE_SIZE_BYTES, /* isFavorite */ false,
                R.raw.lg_g4_iso_800_jpg);
    }

    private static void containsExcept(List<String> mediaIds, String contained,
            String notContained) {
        assertThat(mediaIds).contains(contained);
        assertThat(mediaIds).containsNoneIn(Collections.singletonList(notContained));
    }
}