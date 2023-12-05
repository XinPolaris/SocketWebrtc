package com.ryan.socketwebrtc;

import android.view.Choreographer;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Author: LiuHaoge
 * Date: 2023/5/25 9:47
 */
public class FpsMonitor {

    private static final String TAG = "FpsMonitor";
    private final MutableLiveData<Integer> data = new MutableLiveData<>();
    private static final long SECOND_IN_NANOS = 1000_000000L;
    private static final long UPDATE_FPS_DURATION_NANOS = SECOND_IN_NANOS / 4;
    private boolean logEnable = false;

    private static volatile FpsMonitor instance = null;

    public static FpsMonitor get() {
        if (instance == null) {
            synchronized (FpsMonitor.class) {
                if (instance == null) {
                    instance = new FpsMonitor();
                }
            }
        }
        return instance;
    }

    private long lastFPSUpdateNanos = 0L;
    private final ArrayList2<Long> frameTimeNanoList = new ArrayList2<>(100);


    private volatile boolean isStarted = false;
    private Choreographer.FrameCallback frameCallback = frameTimeNanos -> updateFPS(frameTimeNanos);


    private void updateFPS(long frameTimeNanos) {
        if (frameTimeNanos > 0) {
            frameTimeNanoList.add(frameTimeNanos);
            if (lastFPSUpdateNanos == 0) {
                lastFPSUpdateNanos = frameTimeNanoList.get(0);
            }
            if (frameTimeNanos - lastFPSUpdateNanos > UPDATE_FPS_DURATION_NANOS) {
                //删掉一秒钟之前的数据, 只使用一秒钟内的数据计算帧率
                deleteSmallerThan(frameTimeNanoList, frameTimeNanos - SECOND_IN_NANOS);
                int size = frameTimeNanoList.size();
                if (size > 1) {
                    long durationNanos = (frameTimeNanoList.get(size - 1) - frameTimeNanoList.get(0)) / (size - 1);
                    int fps = (int) (SECOND_IN_NANOS / durationNanos);
                    if (logEnable) {
                        Logger.i(TAG, String.valueOf(fps));
                    }
                    data.setValue(fps);
                }
                lastFPSUpdateNanos = frameTimeNanos;
            }
        } else {
            Choreographer.getInstance().removeFrameCallback(frameCallback);
            this.frameTimeNanoList.clear();
            lastFPSUpdateNanos = 0;
        }
        if (isStarted) {
            Choreographer.getInstance().postFrameCallback(frameCallback);
        } else {
            Choreographer.getInstance().removeFrameCallback(frameCallback);
        }
    }

    public LiveData<Integer> getData() {
        return data;
    }

    public synchronized void start() {
        isStarted = true;
        updateFPS(0);
    }

    public synchronized void stop() {
        isStarted = false;
    }

    public void setLogEnable(boolean logEnable) {
        this.logEnable = logEnable;
    }

    private static class ArrayList2<E> extends ArrayList<E> {

        public ArrayList2(int initialCapacity) {
            super(initialCapacity);
        }

        @Override
        public void removeRange(int fromIndex, int toIndex) {
            super.removeRange(fromIndex, toIndex);
        }
    }

    private static void deleteSmallerThan(ArrayList2<Long> list, long val) {
        int index = Collections.binarySearch(list, val);
        if (index < 0) {
            index = Math.min(-index - 1, list.size() - 1);
        }
        list.removeRange(0, index);
    }

}
