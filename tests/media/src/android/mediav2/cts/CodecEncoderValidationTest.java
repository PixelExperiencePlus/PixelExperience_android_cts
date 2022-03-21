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

package android.mediav2.cts;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaFormat;

import androidx.test.filters.LargeTest;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUVP010;
import static android.mediav2.cts.CodecTestBase.SupportClass.*;
import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class CodecEncoderValidationTest extends CodecEncoderTestBase {
    private static final String INPUT_AUDIO_FILE_HBD = "audio/sd_2ch_48kHz_f32le.raw";
    private static final String INPUT_VIDEO_FILE_HBD = "dpov_352x288_30fps_yuv420p16le.yuv";

    private final boolean mUseHBD;
    private final SupportClass mSupportRequirements;
    // Key: mediaType, Value: tolerance duration in ms
    private static final Map<String, Integer> toleranceMap = new HashMap<>();

    static {
        toleranceMap.put(MediaFormat.MIMETYPE_AUDIO_AAC, 20);
        toleranceMap.put(MediaFormat.MIMETYPE_AUDIO_OPUS, 10);
        toleranceMap.put(MediaFormat.MIMETYPE_AUDIO_AMR_NB, 10);
        toleranceMap.put(MediaFormat.MIMETYPE_AUDIO_AMR_WB, 20);
        toleranceMap.put(MediaFormat.MIMETYPE_AUDIO_FLAC, 0);
    }

    public CodecEncoderValidationTest(String encoder, String mediaType, int[] bitrates,
            int[] encoderInfo1, int[] encoderInfo2, boolean useHBD,
            SupportClass supportRequirements) {
        super(encoder, mediaType, bitrates, encoderInfo1, encoderInfo2);
        mUseHBD = useHBD;
        mSupportRequirements = supportRequirements;
    }

    @Parameterized.Parameters(name = "{index}({0})")
    public static Collection<Object[]> input() {
        final boolean isEncoder = true;
        final boolean needAudio = true;
        final boolean needVideo = true;
        final List<Object[]> defArgsList = Arrays.asList(new Object[][]{
                // Audio tests covering cdd sec 5.1.3
                // mediaType, arrays of bit-rates, sample rates, channel counts, useHBD,
                // SupportClass
                {MediaFormat.MIMETYPE_AUDIO_AAC, new int[]{64000, 128000}, new int[]{8000, 12000,
                        16000, 22050, 24000, 32000, 44100, 48000}, new int[]{1, 2}, false,
                        CODEC_ALL},
                {MediaFormat.MIMETYPE_AUDIO_OPUS, new int[]{64000, 128000}, new int[]{8000, 12000
                        , 16000, 24000, 48000}, new int[]{1, 2}, false, CODEC_ALL},
                {MediaFormat.MIMETYPE_AUDIO_AMR_NB, new int[]{4750, 5150, 5900, 6700, 7400, 7950,
                        10200, 12200}, new int[]{8000}, new int[]{1}, false, CODEC_ALL},
                {MediaFormat.MIMETYPE_AUDIO_AMR_WB, new int[]{6600, 8850, 12650, 14250, 15850,
                        18250, 19850, 23050, 23850}, new int[]{16000}, new int[]{1}, false,
                        CODEC_ALL},
                /* TODO(169310292) */
                {MediaFormat.MIMETYPE_AUDIO_FLAC, new int[]{/* 0, 1, 2, */ 3, 4, 5, 6, 7, 8},
                        new int[]{8000, 16000, 32000, 48000, 96000, 192000}, new int[]{1, 2},
                        false, CODEC_ALL},
                {MediaFormat.MIMETYPE_AUDIO_FLAC, new int[]{/* 0, 1, 2, */ 3, 4, 5, 6, 7, 8},
                        new int[]{8000, 16000, 32000, 48000, 96000, 192000}, new int[]{1, 2},
                        true, CODEC_ALL},

                // mediaType, arrays of bit-rates, width, height, useHBD, SupportClass
                {MediaFormat.MIMETYPE_VIDEO_H263, new int[]{32000, 64000}, new int[]{176},
                        new int[]{144}, false, CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_MPEG4, new int[]{32000, 64000}, new int[]{176},
                        new int[]{144}, false, CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_AVC, new int[]{256000}, new int[]{352, 480},
                        new int[]{240, 360}, false, CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, new int[]{256000}, new int[]{352, 480},
                        new int[]{240, 360}, false, CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_VP8, new int[]{256000}, new int[]{352, 480},
                        new int[]{240, 360}, false, CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_VP9, new int[]{256000}, new int[]{352, 480},
                        new int[]{240, 360}, false, CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_AV1, new int[]{256000}, new int[]{352, 480},
                        new int[]{240, 360}, false, CODEC_ALL},
        });
        return prepareParamList(defArgsList, isEncoder, needAudio, needVideo, false);
    }

    void encodeAndValidate(String inputFile) throws IOException, InterruptedException {
        if (!mIsAudio) {
            int colorFormat = mFormats.get(0).getInteger(MediaFormat.KEY_COLOR_FORMAT);
            Assume.assumeTrue(hasSupportForColorFormat(mCodecName, mMime, colorFormat));
        }
        checkFormatSupport(mCodecName, mMime, true, mFormats, null, mSupportRequirements);
        setUpSource(inputFile);
        mOutputBuff = new OutputManager();
        {
            mCodec = MediaCodec.createByCodecName(mCodecName);
            mSaveToMem = true;
            for (MediaFormat inpFormat : mFormats) {
                if (mIsAudio) {
                    mSampleRate = inpFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    mChannels = inpFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                } else {
                    mWidth = inpFormat.getInteger(MediaFormat.KEY_WIDTH);
                    mHeight = inpFormat.getInteger(MediaFormat.KEY_HEIGHT);
                }
                String log = String.format("format: %s \n codec: %s, file: %s :: ", inpFormat,
                        mCodecName, inputFile);
                mOutputBuff.reset();
                mInfoList.clear();
                configureCodec(inpFormat, false, true, true);
                mCodec.start();
                doWork(Integer.MAX_VALUE);
                queueEOS();
                waitForAllOutputs();
                if (mUseHBD && mIsAudio) {
                    assertEquals(AudioFormat.ENCODING_PCM_FLOAT,
                            mCodec.getOutputFormat().getInteger(MediaFormat.KEY_PCM_ENCODING));
                }
                mCodec.reset();
                assertFalse(log + "unexpected error", mAsyncHandle.hasSeenError());
                assertTrue(log + "no input sent", 0 != mInputCount);
                assertTrue(log + "no output received", 0 != mOutputCount);
                if (!mIsAudio) {
                    assertEquals(log + "input count != output count, act/exp: " + mOutputCount +
                            " / " + mInputCount, mInputCount, mOutputCount);
                } else {
                    assertTrue(log + " pts is not strictly increasing",
                            mOutputBuff.isPtsStrictlyIncreasing(mPrevOutputPts));
                }
                ArrayList<MediaFormat> fmts = new ArrayList<>();
                fmts.add(mOutFormat);
                ArrayList<String> listOfDecoders = selectCodecs(mMime, fmts, null, false);
                assertFalse("no suitable codecs found for mediaType: " + mMime,
                        listOfDecoders.isEmpty());
                CodecDecoderTestBase cdtb =
                        new CodecDecoderTestBase(listOfDecoders.get(0), mMime, null);
                cdtb.mOutputBuff = new OutputManager();
                cdtb.mSaveToMem = true;
                cdtb.mCodec = MediaCodec.createByCodecName(cdtb.mCodecName);
                cdtb.configureCodec(mOutFormat, false, true, false);
                cdtb.mCodec.start();
                cdtb.doWork(mOutputBuff.getBuffer(), mInfoList);
                cdtb.queueEOS();
                cdtb.waitForAllOutputs();
                if (mUseHBD && mIsAudio) {
                    assertEquals(AudioFormat.ENCODING_PCM_FLOAT,
                            cdtb.mOutFormat.getInteger(MediaFormat.KEY_PCM_ENCODING));
                }
                cdtb.mCodec.stop();
                cdtb.mCodec.release();
                ByteBuffer out = cdtb.mOutputBuff.getBuffer();
                if (isCodecLossless(mMime)) {
                    if (mUseHBD && mMime.equals(MediaFormat.MIMETYPE_AUDIO_FLAC)) {
                        CodecDecoderTest.verify(cdtb.mOutputBuff, inputFile, 3.446394f,
                                AudioFormat.ENCODING_PCM_FLOAT, -1L);
                    } else {
                        assertEquals(log + "identity test failed", out,
                                ByteBuffer.wrap(mInputData));
                    }
                }
                if (!mIsAudio) {
                    assertEquals(log + "input frames queued != output frames of decoder, " +
                                    "act/exp: " + mInputCount + " / " + cdtb.mOutputCount,
                            mInputCount, cdtb.mOutputCount);
                    assertTrue(cdtb.mOutputBuff.isOutPtsListIdenticalToInpPtsList(true));
                } else {
                    int tolerance = toleranceMap.get(mMime) * mSampleRate * mChannels *
                            mBytesPerSample / 1000;
                    assertTrue(log + "out bytes + tolerance < input bytes, act/exp: " +
                                    out.limit() + " + " + tolerance + " > " + mInputData.length,
                            mInputData.length <= out.limit() + tolerance);
                    assertTrue(cdtb.mOutputBuff.isPtsStrictlyIncreasing(mPrevOutputPts));
                }
            }
            mCodec.release();
        }
    }

    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testEncodeAndValidate() throws IOException, InterruptedException {
        setUpParams(Integer.MAX_VALUE);
        String inputFile = mInputFile;
        if (mUseHBD) {
            if (mIsAudio) {
                for (MediaFormat format : mFormats) {
                    format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_FLOAT);
                }
                mBytesPerSample = 4;
                inputFile = INPUT_AUDIO_FILE_HBD;
            } else {
                for (MediaFormat format : mFormats) {
                    format.setInteger(MediaFormat.KEY_COLOR_FORMAT, COLOR_FormatYUVP010);
                }
                mBytesPerSample = 2;
                inputFile = INPUT_VIDEO_FILE_HBD;
            }
        }
        encodeAndValidate(inputFile);
    }
}