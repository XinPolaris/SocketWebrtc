package org.webrtc;

import android.util.Log;

import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.LinkedHashSet;

public class FiksVideoEncoderFactory implements VideoEncoderFactory {
    private static final String TAG = "FiksVideoEncoderFactory";
    private final VideoEncoderFactory hardwareVideoEncoderFactory;
    private final VideoEncoderFactory softwareVideoEncoderFactory = new SoftwareVideoEncoderFactory();

    public FiksVideoEncoderFactory(EglBase.Context eglContext, boolean enableIntelVp8Encoder, boolean enableH264HighProfile) {
        this.hardwareVideoEncoderFactory = new FiksHardwareVideoEncoderFactory(eglContext, enableIntelVp8Encoder, enableH264HighProfile);
    }

    FiksVideoEncoderFactory(VideoEncoderFactory hardwareVideoEncoderFactory) {
        this.hardwareVideoEncoderFactory = hardwareVideoEncoderFactory;
    }

    @Nullable
    public VideoEncoder createEncoder(VideoCodecInfo info) {
        VideoEncoder softwareEncoder = this.softwareVideoEncoderFactory.createEncoder(info);
        VideoEncoder hardwareEncoder = this.hardwareVideoEncoderFactory.createEncoder(info);
        Log.d(TAG, "createEncoder: softwareEncoder->" + softwareEncoder);
        Log.d(TAG, "createEncoder: hardwareEncoder->" + hardwareEncoder);
        if (hardwareEncoder != null && softwareEncoder != null) {
            return new VideoEncoderFallback(softwareEncoder, hardwareEncoder);
        } else {
            return hardwareEncoder != null ? hardwareEncoder : softwareEncoder;
        }
    }

    public VideoCodecInfo[] getSupportedCodecs() {
        LinkedHashSet<VideoCodecInfo> supportedCodecInfos = new LinkedHashSet();
//        supportedCodecInfos.addAll(Arrays.asList(this.softwareVideoEncoderFactory.getSupportedCodecs()));
        supportedCodecInfos.addAll(Arrays.asList(this.hardwareVideoEncoderFactory.getSupportedCodecs()));
        return (VideoCodecInfo[]) supportedCodecInfos.toArray(new VideoCodecInfo[supportedCodecInfos.size()]);
    }
}