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

package android.cloudsearch.cts;

import android.app.cloudsearch.SearchRequest;
import android.app.cloudsearch.SearchResponse;
import android.service.cloudsearch.CloudSearchService;
import android.util.Log;

import java.util.List;
import java.util.concurrent.CountDownLatch;

public class CtsCloudSearchService extends CloudSearchService {

    private static final boolean DEBUG = true;
    public static final String MY_PACKAGE = "android.cloudsearch.cts";
    public static final String SERVICE_NAME = MY_PACKAGE + "/."
            + CtsCloudSearchService.class.getSimpleName();
    private static final String TAG =
            "CloudSearchManagerTest[" + CtsCloudSearchService.class.getSimpleName() + "]";

    private static Watcher sWatcher;

    @Override
    public void onCreate() {
        super.onCreate();
        if (DEBUG) Log.d(TAG, "onCreate");
        sWatcher.created.countDown();
    }

    @Override
    public void onSearch(SearchRequest request) {
        sWatcher.queried.countDown();
        if (request.getQuery().equals("Successful")) {
            sWatcher.succeeded.countDown();
            returnResults(request.getRequestId(),
                    CloudSearchTestUtils.getSearchResponse(SearchResponse.SEARCH_STATUS_OK));
        } else if (request.getQuery().equals("Unsuccessful")) {
            sWatcher.failed.countDown();
            returnResults(request.getRequestId(), CloudSearchTestUtils.getSearchResponse(
                    SearchResponse.SEARCH_STATUS_NO_INTERNET));

        } else {
            // Count down a search failure once and a search success once
            sWatcher.failed.countDown();
            returnResults(request.getRequestId(), CloudSearchTestUtils.getSearchResponse(
                    SearchResponse.SEARCH_STATUS_NO_INTERNET));
            sWatcher.succeeded.countDown();
            returnResults(request.getRequestId(),
                    CloudSearchTestUtils.getSearchResponse(SearchResponse.SEARCH_STATUS_OK));
        }
    }


    public static Watcher setWatcher() {
        if (DEBUG) {
            Log.d(TAG, "----------------------------------------------");
            Log.d(TAG, " setWatcher");
        }
        if (sWatcher != null) {
            throw new IllegalStateException("Set watcher with watcher already set");
        }
        sWatcher = new Watcher();
        return sWatcher;
    }

    public static void clearWatcher() {
        if (DEBUG) Log.d(TAG, "clearWatcher");
        sWatcher = null;
    }

    public static final class Watcher {
        public CountDownLatch created = new CountDownLatch(1);
        public CountDownLatch destroyed = new CountDownLatch(1);
        public CountDownLatch queried = new CountDownLatch(1);
        public CountDownLatch succeeded = new CountDownLatch(2);
        public CountDownLatch failed = new CountDownLatch(2);

        public List<SearchResponse> mSmartspaceTargets;

        public void setTargets(List<SearchResponse> targets) {
            mSmartspaceTargets = targets;
        }
    }
}