/*
 *  Copyright 2016 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package com.ryan.socketwebrtc;

import android.content.Context;
import android.os.SystemClock;

import org.webrtc.CapturerObserver;
import org.webrtc.Logging;
import org.webrtc.NV12Buffer;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoFrame;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class V4L2Capturer implements VideoCapturer {
    private interface VideoReader {
        VideoFrame getNextFrame();

        void close();
    }

    /**
     * Read video data from file for the .y4m container.
     */
    @SuppressWarnings("StringSplitter")
    private static class VideoFrameReader implements VideoReader {

        private final LinkedBlockingQueue<ByteBuffer> frameQueue = new LinkedBlockingQueue<>(10);
        private int frameWidth;
        private int frameHeight;

        public VideoFrameReader() throws IOException {
        }

        @Override
        public VideoFrame getNextFrame() {
            if (frameQueue.peek() != null) {
                ByteBuffer byteBuffer = frameQueue.poll();
//                byte[] arr = new byte[byteBuffer.remaining()];
//                byteBuffer.get(arr);
//                NV21Buffer buffer = new NV21Buffer(arr, frameWidth, frameHeight, null);
                NV12Buffer buffer = new NV12Buffer(frameWidth, frameHeight, frameWidth, frameHeight, byteBuffer, null);
                long timestampNS = TimeUnit.MILLISECONDS.toNanos(SystemClock.elapsedRealtime());
                return new VideoFrame(buffer, 0, timestampNS);
            }
            return null;
        }

        @Override
        public void close() {
            frameQueue.clear();
        }

        public void onReceiveFrame(ByteBuffer frame, int width, int height) {
            if (frameWidth != width || frameHeight != height) {
                frameQueue.clear();
            }
            frameWidth = width;
            frameHeight = height;
            frameQueue.offer(frame);
        }
    }

    private final static String TAG = "FileVideoCapturer";
    private final VideoFrameReader videoReader;
    private CapturerObserver capturerObserver;
    private final Timer timer = new Timer();

    private final TimerTask tickTask = new TimerTask() {
        @Override
        public void run() {
            tick();
        }
    };

    public V4L2Capturer() throws IOException {
        try {
            videoReader = new VideoFrameReader();
        } catch (IOException e) {
            Logging.d(TAG, "Could not open video file: ");
            throw e;
        }
    }

    public void tick() {
        VideoFrame videoFrame = videoReader.getNextFrame();
        if (videoFrame != null) {
            capturerObserver.onFrameCaptured(videoFrame);
            videoFrame.release();
        }
    }

    @Override
    public void initialize(SurfaceTextureHelper surfaceTextureHelper, Context applicationContext,
                           CapturerObserver capturerObserver) {
        this.capturerObserver = capturerObserver;
    }

    @Override
    public void startCapture(int width, int height, int framerate) {
        timer.schedule(tickTask, 0, 1000 / framerate);
    }

    @Override
    public void stopCapture() throws InterruptedException {
        timer.cancel();
    }

    @Override
    public void changeCaptureFormat(int width, int height, int framerate) {
        // Empty on purpose
    }

    @Override
    public void dispose() {
        videoReader.close();
    }

    @Override
    public boolean isScreencast() {
        return false;
    }

    public void onReceiveFrame(ByteBuffer frame, int width, int height) {
        videoReader.onReceiveFrame(frame, width, height);
    }
}