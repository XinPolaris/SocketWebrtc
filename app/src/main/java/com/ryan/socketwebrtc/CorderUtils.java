package com.ryan.socketwebrtc;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import android.util.Log;

/**
 * Created by HuangXin on 2023/7/31.
 */
public class CorderUtils {
    private static final String TAG = "CorderUtils";

    public static void print() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            MediaCodecList list = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
            MediaCodecInfo[] supportCodes = list.getCodecInfos();
            Log.i(TAG, "解码器列表：");
            for (MediaCodecInfo codec : supportCodes) {
                if (!codec.isEncoder()) {
                    String name = codec.getName();
                    if (name.startsWith("OMX.google")) {
                        Log.i(TAG, "软解->" + name);
                    }
                }
            }
            for (MediaCodecInfo codec : supportCodes) {
                if (!codec.isEncoder()) {
                    String name = codec.getName();
                    if (!name.startsWith("OMX.google")) {
                        Log.i(TAG, "硬解->" + name);
                    }
                }
            }
            Log.i(TAG, "编码器列表：");
            for (MediaCodecInfo codec : supportCodes) {
                if (codec.isEncoder()) {
                    String name = codec.getName();
                    if (name.startsWith("OMX.google")) {
                        Log.i(TAG, "软编->" + name);
                    }
                }
            }
            for (MediaCodecInfo codec : supportCodes) {
                if (codec.isEncoder()) {
                    String name = codec.getName();
                    if (!name.startsWith("OMX.google")) {
                        Log.i(TAG, "硬编->" + name);
                    }
                }
            }
        }
    }
}
