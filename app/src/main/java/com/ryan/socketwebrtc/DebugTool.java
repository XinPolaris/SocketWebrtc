package com.ryan.socketwebrtc;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;

import java.nio.ByteBuffer;


/**
 * Created by HuangXin on 2023/6/27.
 */
class DebugTool {
    private static final String TAG = "DebugTool";
    private final TextView textView;
    private final long startTime = System.currentTimeMillis();
    private long recordCount;
    private long frameCount;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public DebugTool(TextView textView) {
        this.textView = textView;
    }

    @SuppressLint("SetTextI18n")
    public void onDataCallback(ByteBuffer data, int dataType, int width, int height) {
        frameCount++;
        long count = (System.currentTimeMillis() - startTime) / 4000;
        if (count != recordCount) {
            recordCount = count;
            int fps = Math.round(frameCount / 4f);
            String msg = width + "x" + height + " " + fps + " fps";
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    textView.setText(msg);
                }
            });
            Log.i(TAG, "onDataCallback: " + msg);
            frameCount = 0;
        }
    }
}
