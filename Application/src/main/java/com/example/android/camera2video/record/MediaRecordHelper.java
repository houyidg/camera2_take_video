package com.example.android.camera2video.record;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.media.MediaRecorder;
import android.os.Handler;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class MediaRecordHelper implements IRecordHelper {
    private static final int SENSOR_ORIENTATION_DEFAULT_DEGREES = 90;
    private static final int SENSOR_ORIENTATION_INVERSE_DEGREES = 270;
    private static final SparseIntArray DEFAULT_ORIENTATIONS = new SparseIntArray();
    private static final SparseIntArray INVERSE_ORIENTATIONS = new SparseIntArray();
    private static final String TAG = "MediaRecordHelper";

    static {
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_0, 90);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_90, 0);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_180, 270);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    static {
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_0, 270);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_90, 180);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_180, 90);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_270, 0);
    }

    private static IRecordHelper iRecordHelper;
    private Context context;
    private MediaRecorder mMediaRecorder;
    private boolean mIsRecordingVideo;
    private Size mVideoSize;
    private Integer mSensorOrientation;
    private String currentRecordPath = null;

    private MediaRecordHelper(Context context) {
        this.context = context;
        this.mMediaRecorder = new MediaRecorder();
    }

    public static IRecordHelper getInstance(Context context) {
        if (iRecordHelper == null) {
            synchronized (MediaRecordHelper.class) {
                if (iRecordHelper == null) {
                    iRecordHelper = new MediaRecordHelper(context);
                }
            }
        }
        return iRecordHelper;
    }


    @Override
    public CaptureRequest.Builder setUpParams(List<Surface> surfaces, CameraDevice mCameraDevice, Handler mBackgroundHandler) {
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setOutputFile(currentRecordPath = getVideoFilePath(context));
        mMediaRecorder.setVideoEncodingBitRate(10000000);
        mMediaRecorder.setVideoFrameRate(30);
        mMediaRecorder.setVideoSize(mVideoSize.getWidth(), mVideoSize.getHeight());
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        int rotation = windowManager.getDefaultDisplay().getRotation();
        switch (mSensorOrientation) {
            case SENSOR_ORIENTATION_DEFAULT_DEGREES:
                mMediaRecorder.setOrientationHint(INVERSE_ORIENTATIONS.get(rotation));

                break;
            case SENSOR_ORIENTATION_INVERSE_DEGREES:
                mMediaRecorder.setOrientationHint(INVERSE_ORIENTATIONS.get(rotation));
                break;
        }
        try {
            mMediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }

        CaptureRequest.Builder mPreviewBuilder = null;
        try {
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        // Set up Surface for the MediaRecorder
        Surface recorderSurface = mMediaRecorder.getSurface();
        surfaces.add(recorderSurface);
        mPreviewBuilder.addTarget(recorderSurface);

        return mPreviewBuilder;
    }

    @Override
    public void setmSensorOrientation(Integer mSensorOrientation) {
        this.mSensorOrientation = mSensorOrientation;
    }

    @Override
    public boolean ismIsRecordingVideo() {
        return mIsRecordingVideo;
    }

    @Override
    public void setmVideoSize(Size mVideoSize) {
        this.mVideoSize = mVideoSize;
    }

    @Override
    public String getVideoFilePath(Context context) {
        final File dir = context.getExternalFilesDir(null);
        return (dir == null ? "" : (dir.getAbsolutePath() + "/"))
                + System.currentTimeMillis() + "_mediaRecord.mp4";
    }

    @Override
    public void stopRecordingVideo() {
        // UI
        mIsRecordingVideo = false;
        // Stop recording
        mMediaRecorder.stop();
        mMediaRecorder.reset();
        Toast.makeText(context, "Video saved: " + currentRecordPath, Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Video saved: " + currentRecordPath);
    }

    @Override
    public void startRecord() {
        if (mMediaRecorder != null) {
            mMediaRecorder.start();
            mIsRecordingVideo = true;
        }
    }

}
