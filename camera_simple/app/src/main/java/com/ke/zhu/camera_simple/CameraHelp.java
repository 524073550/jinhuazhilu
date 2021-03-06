package com.ke.zhu.camera_simple;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class CameraHelp {
    private String TAG = "CameraHelp";
    private TextureView txt_view;
    private CameraManager cameraManager;
    private String cameraId = "";
    private CameraCharacteristics cameraCharacteristics;
    private Integer cameraSensorOrientation;
    private int rotation;

    private int PREVIEW_MAX_WIDTH = 1280;
    private int PREVIEW_MAX_HEIGHT = 720;
    private int SAVE_MAX_WIDTH = 1280;
    private int SAVE_MAX_HEIGHT = 720;

    private boolean exchange;
    private Size savePicSizeList;
    private Size previewSizeList;
    private SurfaceTexture surfaceTexture;
    private ImageReader imageReader;
    private Handler cameraHandler;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;
    private CaptureRequest.Builder captureRequestBuilder;
    private boolean canExchangeCamera;
    private boolean canTakePic;
    private Integer cameraFacing = CameraCharacteristics.LENS_FACING_BACK;
    private Activity context;
    private CamerDataCallback camerDataCallback;
    public CameraHelp(TextureView textureView, Activity context) {
        this.txt_view = textureView;
        this.context = context;
        rotation = context.getWindow().getWindowManager().getDefaultDisplay().getRotation();
        HandlerThread cameraThread = new HandlerThread("camera_thread");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());
        textureView.setSurfaceTextureListener(surfaceTextureListener);
    }

    private TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int i, int i1) {
            //TODO:初始化相机
            try {
                surfaceTexture = surface;
                initCamera();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

        }
    };

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void initCamera() throws CameraAccessException {
        if (context == null){
            Log.e("camera", "context 不能为空");
            return;
        }
        //获取cameramanage
        cameraManager = (CameraManager) context.getSystemService(context.CAMERA_SERVICE);
        //获取相机idCameraDevice
        String[] cameraIdList = getCameraId();
        if (cameraIdList == null && cameraIdList.length == 0) {
            Log.e("camera", "camera初始化失败");
            return;
        }
        //打开指定相机id 默认打开后置摄像头
        for (String id : cameraIdList) {
            CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(id);
            Integer facing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
            if (facing == cameraFacing) {
                cameraId = id;
                this.cameraCharacteristics = cameraCharacteristics;
            }
        }
        cameraSensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        StreamConfigurationMap streamConfigurationMap = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] savePicSize = streamConfigurationMap.getOutputSizes(ImageFormat.JPEG);
        Size[] previewSize = streamConfigurationMap.getOutputSizes(SurfaceTexture.class);
        exchange = exchangeWidthAndHeight(rotation, cameraSensorOrientation);
        savePicSizeList = getBestSize(savePicSize, SAVE_MAX_WIDTH, SAVE_MAX_HEIGHT);
        previewSizeList = getBestSize(previewSize, PREVIEW_MAX_WIDTH, PREVIEW_MAX_HEIGHT);
        surfaceTexture.setDefaultBufferSize(previewSizeList.getWidth(), previewSizeList.getHeight());

        //当设置格式为JPEG时 onImageAvailableListener回调不会执行,当拍照是创建拍照的会话时才会回调
        imageReader = ImageReader.newInstance(previewSizeList.getWidth(), previewSizeList.getHeight(), ImageFormat.JPEG, 1);
        imageReader.setOnImageAvailableListener(onImageAvailableListener, cameraHandler);
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        cameraManager.openCamera(cameraId, stateCallback, cameraHandler);

    }


   private CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            canTakePic = true;
            createCaptureSession(camera);
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {

        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            canTakePic = false;
        }
    };

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void createCaptureSession(CameraDevice camera) {
        try {
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            Surface surface = new Surface(txt_view.getSurfaceTexture());
            captureRequestBuilder.addTarget(surface);
            captureRequestBuilder.addTarget(imageReader.getSurface());
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);    // 闪光灯
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE); // 自动对焦
            cameraDevice.createCaptureSession(Arrays.asList(surface, imageReader.getSurface()), createCaptureSession, cameraHandler);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private CameraCaptureSession.StateCallback createCaptureSession = new CameraCaptureSession.StateCallback() {
        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            cameraCaptureSession = session;
            try {
                session.setRepeatingRequest(captureRequestBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                }, cameraHandler);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {

        }
    };

    CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            canExchangeCamera = true;
            canTakePic = true;
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
        }
    };

    ImageReader.OnImageAvailableListener onImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @SuppressLint("NewApi")
        @RequiresApi(api = Build.VERSION_CODES.KITKAT)
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireNextImage();
            if (image == null) {
                return;
            }
            image.close();
        }
    };

    public void setCameraCallback(CamerDataCallback cameraCallback){
        this.camerDataCallback = camerDataCallback;
    }


    public interface CamerDataCallback{
        void cameraCallback(byte[] data);
    }


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private Size getBestSize(Size[] previewSize, int maxWidth, int maxHeight) {
        float aspectRatio = (float) maxWidth / maxHeight;
        List<Size> sizes = new ArrayList<>();
        for (Size size : previewSize) {
            if ((float) size.getWidth() / size.getHeight() == aspectRatio && size.getWidth() <= maxWidth && size.getHeight() <= maxHeight) {
                sizes.add(size);
            }
        }
        return Collections.max(sizes, new Comparator<Size>() {
            @Override
            public int compare(Size o1, Size o2) {
                return o1.getHeight() - o2.getHeight();
            }
        });
    }

    private boolean exchangeWidthAndHeight(int rotation, Integer cameraSensorOrientation) {
        boolean exchange = false;
        switch (rotation) {
            case Surface.ROTATION_0:
            case Surface.ROTATION_180:
                if (cameraSensorOrientation == 90 || cameraSensorOrientation == 270) {
                    exchange = true;
                }
                break;
            case Surface.ROTATION_90:
            case Surface.ROTATION_270:
                if (cameraSensorOrientation == 0 || cameraSensorOrientation == 180) {
                    exchange = true;
                }
                break;
        }

        return exchange;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private String[] getCameraId() {
        String[] cameraIdList = null;
        try {
            cameraIdList = cameraManager.getCameraIdList();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return cameraIdList;
    }


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void releaseCamera() {
        if (cameraCaptureSession != null) {
            cameraCaptureSession.close();
            cameraCaptureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void takPic() {
        if (cameraDevice == null) return;
        // 创建拍照需要的CaptureRequest.Builder
        final CaptureRequest.Builder captureRequestBuilder;
        try {
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            // 将imageReader的surface作为CaptureRequest.Builder的目标
            captureRequestBuilder.addTarget(imageReader.getSurface());
            // 自动对焦
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            // 自动曝光
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            // 获取手机方向
            int rotation = context.getWindowManager().getDefaultDisplay().getRotation();
            // 根据设备方向计算设置照片的方向
            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, cameraSensorOrientation);
            //拍照
            CaptureRequest mCaptureRequest = captureRequestBuilder.build();
            cameraCaptureSession.capture(mCaptureRequest, null, cameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

}
