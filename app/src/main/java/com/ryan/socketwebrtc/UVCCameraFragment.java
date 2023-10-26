package com.ryan.socketwebrtc;

import android.content.DialogInterface;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;

import com.hsj.camera.CameraView;
import com.hsj.camera.IFrameCallback;
import com.hsj.camera.IRender;
import com.hsj.camera.ISurfaceCallback;
import com.hsj.camera.UsbCameraManager;
import com.hsj.camera.V4L2Camera;
import com.ryan.socketwebrtc.databinding.FragmentImportStreamingUvcBinding;

import java.nio.ByteBuffer;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Created by HuangXin on 2023/4/17.
 */
public class UVCCameraFragment extends Fragment implements ISurfaceCallback {
    public UVCCallback callBack;
    private static final String TAG = "UVCCameraFragment";
    private FragmentImportStreamingUvcBinding binding;
    DebugTool debugTool;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentImportStreamingUvcBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        CameraView cameraView = binding.cameraView;
        this.render = cameraView.getRender(CameraView.COMMON);
        this.render.setSurfaceCallback(this);
        cameraView.surfaceCallback = new CameraView.SurfaceCallback() {
            @Override
            public void onSurfaceCreated(GL10 gl, EGLConfig config) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        initCamera();
                    }
                });
            }
        };
        binding.itemCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSingleChoiceDialog();
            }
        });
        debugTool = new DebugTool(binding.debugInfo);
    }
    // CameraAPI
    private V4L2Camera camera;
    // IRender
    private IRender render;
    private Surface surface;
    private int[][] supportFrameSize;
    static int curFrameSizeIndex = 6;
    static int[] curFrameSize;

    private void initCamera() {
        create();
        start();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (render != null) {
            render.onRender(true);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (render != null) {
            render.onRender(false);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        stop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        destroy();
    }

//==========================================Menu====================================================


//===========================================Camera=================================================

    private void create() {
        if (this.camera == null) {
            List<UsbDevice> deviceList = UsbCameraManager.getUsbCameraDevices(requireContext());
            if (deviceList.size() == 0) {
                showToast("未识别到摄像头");
                return;
            }
            V4L2Camera camera = UsbCameraManager.createUsbCamera(deviceList.get(0));
            if (camera != null) {
                supportFrameSize = camera.getSupportFrameSize();
                if (supportFrameSize == null || supportFrameSize.length == 0) {
                    showToast("Get support preview size failed.");
                } else {
                    curFrameSize = supportFrameSize[curFrameSizeIndex];
                    final int width = curFrameSize[0];
                    final int height = curFrameSize[1];
                    Log.i(TAG, "width=" + width + ", height=" + height);
                    ConstraintLayout.LayoutParams layoutParams = (ConstraintLayout.LayoutParams) binding.cameraView.getLayoutParams();
                    layoutParams.dimensionRatio = width + ":" + height;
                    binding.cameraView.setLayoutParams(layoutParams);
                    camera.setFrameSize(width, height, V4L2Camera.FRAME_FORMAT_MJPEG);
                    this.camera = camera;
                }
            } else {
                Log.e(TAG, "create camera: fail");
                showToast("摄像头创建失败");
            }
        }
    }

    private void start() {
        if (this.camera != null) {
            if (surface != null) this.camera.setPreview(surface);
            this.camera.setFrameCallback(frameCallback);
            this.camera.start();
        } else {
            showToast("Camera have not create");
        }
    }

    private final IFrameCallback frameCallback = new IFrameCallback() {

        @Override
        public void onFrame(ByteBuffer data) {
            Log.i(TAG, "onFrame: ");
            debugTool.onDataCallback(data, 0, curFrameSize[0], curFrameSize[1]);
            callBack.onFrame(data, curFrameSize[0], curFrameSize[1]);
        }
    };

    private void stop() {
        if (this.camera != null) {
            this.camera.stop();
        }
    }

    private void destroy() {
        if (this.camera != null) {
            this.camera.destroy();
            this.camera = null;
        }
    }

//=============================================Other================================================

    private void showSingleChoiceDialog() {
        if (supportFrameSize != null) {
            String[] items = new String[supportFrameSize.length];
            for (int i = 0; i < supportFrameSize.length; ++i) {
                items[i] = "" + supportFrameSize[i][0] + " x " + supportFrameSize[i][1];
            }
            AlertDialog.Builder ad = new AlertDialog.Builder(getContext());
            ad.setTitle("Select Size");
            ad.setSingleChoiceItems(items, curFrameSizeIndex, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    curFrameSizeIndex = which;
                }
            });
            ad.setPositiveButton("确定", (dialog, which) -> {
                callBack.onSizeChange();
            });
            ad.show();
        }
    }

    @Override
    public void onSurface(Surface surface) {
        if (surface == null) stop();
        this.surface = surface;
    }

    private void showToast(String msg) {
        Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }

    private void runOnUiThread(Runnable runnable) {
        binding.getRoot().post(runnable);
    }

    public interface UVCCallback {
        void onFrame(ByteBuffer buffer, int width, int height);

        void onSizeChange();
    }
}
