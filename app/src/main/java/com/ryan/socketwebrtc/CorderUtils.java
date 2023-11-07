package com.ryan.socketwebrtc;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.util.Log;

/**
 * Created by HuangXin on 2023/7/31.
 */
public class CorderUtils {
    private static final String TAG = "MediaCodec";

    public static void print() {
        MediaCodecList list = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        MediaCodecInfo[] supportCodes = list.getCodecInfos();
        for (MediaCodecInfo codecInfo : supportCodes) {
            String[] types = codecInfo.getSupportedTypes();
            for (String type : types) {
                Log.i("MediaCodecList, name->", codecInfo.getName() + ", type->" + type);
            }
        }
    }
}
