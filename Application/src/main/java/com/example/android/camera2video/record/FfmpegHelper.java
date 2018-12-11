package com.example.android.camera2video.record;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.widget.Toast;

import com.example.android.camera2video.record.ffmpeg.FFmpegRecordUtils;
import com.example.android.camera2video.record.ffmpeg.OnImageAvailableListener;
import com.example.android.camera2video.record.ffmpeg.util.VideoFormatUtils;

import java.io.File;
import java.util.List;


public class FfmpegHelper implements IRecordHelper {
    private static final String TAG = "FfmpegHelper";
    private static FfmpegHelper ffmpegHelper;
    private Context context;
    private boolean mIsRecordingVideo;
    private Size mVideoSize;
    private ImageReader imageReader;
    FFmpegRecordUtils fFmpegRecordUtils;
    private String currentRecordPath = null;

    private FfmpegHelper(Context context) {
        this.context = context;
        fFmpegRecordUtils = new FFmpegRecordUtils(context);
    }

    public static FfmpegHelper getInstance(Context context) {
        if (ffmpegHelper == null) {
            synchronized (FfmpegHelper.class) {
                if (ffmpegHelper == null) {
                    ffmpegHelper = new FfmpegHelper(context);
                }
            }
        }
        return ffmpegHelper;
    }

    @Override
    public CaptureRequest.Builder setUpParams(List<Surface> surfaces, CameraDevice mCameraDevice, Handler mBackgroundHandler){
        if (imageReader == null) {
            imageReader = ImageReader.newInstance(mVideoSize.getWidth(), mVideoSize.getHeight(), ImageFormat.YUV_420_888, 1);
            imageReader.setOnImageAvailableListener(backAvailableListener, mBackgroundHandler);
        }
        fFmpegRecordUtils.setmVideoSize(mVideoSize);
        fFmpegRecordUtils.setRotation(1);
        CaptureRequest.Builder mRequest = null;
        try {
            mRequest = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        surfaces.add(imageReader.getSurface());
        mRequest.addTarget(imageReader.getSurface());
        return mRequest;
    }

    public void startRecord() {
        try {
            currentRecordPath = getVideoFilePath(context);
            fFmpegRecordUtils.startRecorderTask(currentRecordPath);
            mIsRecordingVideo = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stopRecordingVideo() {
        if (mIsRecordingVideo) {
            // UI
            mIsRecordingVideo = false;
            // Stop recording
            fFmpegRecordUtils.stopRecorderTask();
            Toast.makeText(context, "Video saved: " + currentRecordPath, Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Video saved: " + currentRecordPath);
        }
    }

    @Override
    public void setmSensorOrientation(Integer mSensorOrientation) {

    }

    public boolean ismIsRecordingVideo() {
        return mIsRecordingVideo;
    }

    public void setmVideoSize(Size mVideoSize) {
        this.mVideoSize = mVideoSize;
    }

    @Override
    public String getVideoFilePath(Context context) {
        final File dir = context.getExternalFilesDir(null);
        return (dir == null ? "" : (dir.getAbsolutePath() + "/"))
                + System.currentTimeMillis() + "_ffmpegHelper.mp4";
    }

    private OnImageAvailableListener backAvailableListener = new OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireLatestImage();
            byte[] data = VideoFormatUtils.getDataFromImage(image, 2);
            fFmpegRecordUtils.onCameraBack(data, 1);
            image.close();
        }
    };

    public interface OnPreviewFrameCallback {
        void onCameraBack(byte[][] bytes, int orientation);

        void onCameraBack(byte[] bytes, int orientation);
    }
}
