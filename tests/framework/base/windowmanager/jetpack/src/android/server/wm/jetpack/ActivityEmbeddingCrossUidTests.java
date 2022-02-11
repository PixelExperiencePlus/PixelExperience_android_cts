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

package android.server.wm.jetpack;

import static android.server.wm.jetpack.second.Components.SECOND_ACTIVITY;
import static android.server.wm.jetpack.second.Components.SECOND_ACTIVITY_UNKNOWN_EMBEDDING_CERTS;
import static android.server.wm.jetpack.second.Components.SECOND_UNTRUSTED_EMBEDDING_ACTIVITY;
import static android.server.wm.jetpack.signed.Components.SIGNED_EMBEDDING_ACTIVITY;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.DEFAULT_SPLIT_RATIO;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.EMBEDDED_ACTIVITY_ID;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.startActivityCrossUidInSplit;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.startActivityCrossUidInSplit_expectFail;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.waitForResumed;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.ActivityManager;
import android.server.wm.NestedShellPermission;
import android.server.wm.jetpack.utils.ActivityEmbeddingTestBase;
import android.server.wm.jetpack.utils.TestActivityWithId;
import android.server.wm.jetpack.utils.TestConfigChangeHandlingActivity;
import android.util.Pair;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.window.extensions.embedding.SplitPairRule;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.function.Predicate;

/**
 * Tests for the {@link androidx.window.extensions} implementation provided on the device (and only
 * if one is available) for the Activity Embedding functionality. Specifically tests activity
 * launch scenarios across UIDs.
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerJetpackTestCases:ActivityEmbeddingCrossUidTests
 */
@RunWith(AndroidJUnit4.class)
public class ActivityEmbeddingCrossUidTests extends ActivityEmbeddingTestBase {

    @Override
    @After
    public void tearDown() {
        super.tearDown();
        ActivityManager am = mContext.getSystemService(ActivityManager.class);
        NestedShellPermission.run(() -> am.forceStopPackage("android.server.wm.jetpack.second"));
        NestedShellPermission.run(() -> am.forceStopPackage("android.server.wm.jetpack.signed"));
    }

    /**
     * Tests that embedding an activity across UIDs is not allowed.
     */
    @Test
    public void testCrossUidActivityEmbeddingIsNotAllowed() {
        Activity primaryActivity = startActivityNewTask(TestConfigChangeHandlingActivity.class);

        // Only the primary activity can be in a split with another activity
        final Predicate<Pair<Activity, Activity>> activityActivityPredicate =
                activityActivityPair -> primaryActivity.equals(activityActivityPair.first);

        SplitPairRule splitPairRule = new SplitPairRule.Builder(
                activityActivityPredicate, activityIntentPair -> true /* activityIntentPredicate */,
                parentWindowMetrics -> true /* parentWindowMetricsPredicate */)
                .setSplitRatio(DEFAULT_SPLIT_RATIO).build();
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // Launch an activity from a different UID and verify that it is not split with the primary
        // activity.
        startActivityCrossUidInSplit_expectFail(primaryActivity, SECOND_ACTIVITY,
                mSplitInfoConsumer);
    }

    /**
     * Tests that embedding an activity across UIDs is not allowed if an activity requires a
     * permission that the host doesn't have.
     */
    @Test
    public void testCrossUidActivityEmbeddingIsNotAllowedWithoutPermission() {
        Activity primaryActivity = startActivityNewTask(TestConfigChangeHandlingActivity.class);

        // Only the primary activity can be in a split with another activity
        final Predicate<Pair<Activity, Activity>> activityActivityPredicate =
                activityActivityPair -> primaryActivity.equals(activityActivityPair.first);

        SplitPairRule splitPairRule = new SplitPairRule.Builder(
                activityActivityPredicate, activityIntentPair -> true /* activityIntentPredicate */,
                parentWindowMetrics -> true /* parentWindowMetricsPredicate */)
                .setSplitRatio(DEFAULT_SPLIT_RATIO).build();
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // Launch an activity from a different UID and verify that it is not split with the primary
        // activity.
        startActivityCrossUidInSplit_expectFail(primaryActivity,
                SECOND_ACTIVITY_UNKNOWN_EMBEDDING_CERTS, mSplitInfoConsumer);
    }

    /**
     * Tests that embedding an activity across UIDs is allowed if an activity requires a
     * permission that the host has.
     */
    @Test
    public void testCrossUidActivityEmbeddingIsAllowedWithPermission() {
        // Start an activity that will attempt to embed TestActivityKnownEmbeddingCerts
        startActivityNewTask(SIGNED_EMBEDDING_ACTIVITY);

        assertTrue(waitForResumed(EMBEDDED_ACTIVITY_ID));
        TestActivityWithId embeddedActivity = getResumedActivityById(EMBEDDED_ACTIVITY_ID);
        assertNotNull(embeddedActivity);
        assertTrue(mActivityEmbeddingComponent.isActivityEmbedded(embeddedActivity));
    }

    /**
     * Tests that embedding an activity across UIDs is allowed if the app has opted in to allow
     * untrusted embedding.
     */
    @Test
    public void testUntrustedCrossUidActivityEmbeddingIsAllowedWithOptIn() {
        Activity primaryActivity = startActivityNewTask(TestConfigChangeHandlingActivity.class);

        // Only the primary activity can be in a split with another activity
        final Predicate<Pair<Activity, Activity>> activityActivityPredicate =
                activityActivityPair -> primaryActivity.equals(activityActivityPair.first);

        SplitPairRule splitPairRule = new SplitPairRule.Builder(
                activityActivityPredicate, activityIntentPair -> true /* activityIntentPredicate */,
                parentWindowMetrics -> true /* parentWindowMetricsPredicate */)
                .setSplitRatio(DEFAULT_SPLIT_RATIO).build();
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // Launch an activity from a different UID and verify that it is split with the primary
        // activity.
        startActivityCrossUidInSplit(primaryActivity, SECOND_UNTRUSTED_EMBEDDING_ACTIVITY,
                splitPairRule, mSplitInfoConsumer, "id", true /* verify */);
    }
}