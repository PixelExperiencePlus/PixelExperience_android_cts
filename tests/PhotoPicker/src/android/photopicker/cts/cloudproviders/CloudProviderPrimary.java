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

package android.photopicker.cts.cloudproviders;

import static android.photopicker.cts.PickerProviderMediaGenerator.MediaGenerator;
import static android.photopicker.cts.PickerProviderMediaGenerator.QueryExtras;
import static android.provider.CloudMediaProviderContract.MediaInfo;

import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.Point;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.photopicker.cts.PickerProviderMediaGenerator;
import android.provider.CloudMediaProvider;

import java.io.FileNotFoundException;

/**
 * Implements a cloud {@link CloudMediaProvider} interface over items generated with
 * {@link MediaGenerator}
 */
public class CloudProviderPrimary extends CloudMediaProvider {
    public static final String AUTHORITY = "android.photopicker.cts.cloudproviders.cloud_primary";

    private MediaGenerator mMediaGenerator;

    @Override
    public boolean onCreate() {
        mMediaGenerator = PickerProviderMediaGenerator.getMediaGenerator(getContext(), AUTHORITY);

        return true;
    }

    @Override
    public Cursor onQueryMedia(String mediaId) {
        throw new UnsupportedOperationException("onQueryMedia by id not supported");
    }

    @Override
    public Cursor onQueryMedia(Bundle extras) {
        final QueryExtras queryExtras = new QueryExtras(extras);

        return mMediaGenerator.getMedia(queryExtras.generation, queryExtras.albumId,
                queryExtras.mimeType, queryExtras.sizeBytes);
    }

    @Override
    public Cursor onQueryDeletedMedia(Bundle extras) {
        final QueryExtras queryExtras = new QueryExtras(extras);

        return mMediaGenerator.getDeletedMedia(queryExtras.generation);
    }

    @Override
    public Cursor onQueryAlbums(Bundle extras) {
        final QueryExtras queryExtras = new QueryExtras(extras);

        return mMediaGenerator.getAlbums(queryExtras.mimeType, queryExtras.sizeBytes);
    }

    @Override
    public AssetFileDescriptor onOpenThumbnail(String mediaId, Point size,
            CancellationSignal signal) throws FileNotFoundException {
        return new AssetFileDescriptor(mMediaGenerator.openMedia(mediaId), 0,
                AssetFileDescriptor.UNKNOWN_LENGTH);
    }

    @Override
    public ParcelFileDescriptor onOpenMedia(String mediaId, CancellationSignal signal)
            throws FileNotFoundException {
        return mMediaGenerator.openMedia(mediaId);
    }

    @Override
    public Bundle onGetMediaInfo(Bundle extras) {
        Bundle bundle = new Bundle();
        bundle.putString(MediaInfo.MEDIA_VERSION, mMediaGenerator.getVersion());
        bundle.putLong(MediaInfo.MEDIA_GENERATION, mMediaGenerator.getGeneration());
        bundle.putLong(MediaInfo.MEDIA_COUNT, mMediaGenerator.getCount());

        return bundle;
    }

    @Override
    public Bundle onGetAccountInfo(Bundle extras) {
        return mMediaGenerator.getAccountInfo();
    }
}