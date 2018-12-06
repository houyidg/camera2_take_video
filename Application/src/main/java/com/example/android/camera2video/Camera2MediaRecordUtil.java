package com.example.android.camera2video;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.WindowManager;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Camera2MediaRecordUtil {
    private static final int SENSOR_ORIENTATION_DEFAULT_DEGREES = 90;
    private static final int SENSOR_ORIENTATION_INVERSE_DEGREES = 270;
    private static final SparseIntArray DEFAULT_ORIENTATIONS = new SparseIntArray();
    private static final SparseIntArray INVERSE_ORIENTATIONS = new SparseIntArray();

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

    private static Camera2MediaRecordUtil camera2MediaRecordUtil;
    Context context;

    private Camera2MediaRecordUtil(Context context) {
        this.context = context;
    }

    public static Camera2MediaRecordUtil getInstance(Context context) {
        if (camera2MediaRecordUtil == null) {
            synchronized (Camera2MediaRecordUtil.class) {
                if (camera2MediaRecordUtil == null) {
                    camera2MediaRecordUtil = new Camera2MediaRecordUtil(context);
                }
            }
        }
        return camera2MediaRecordUtil;
    }

    private Camera2MediaRecordUtil() {
    }

    private MediaRecorder mMediaRecorder;
    private boolean mIsRecordingVideo;
    private Size mVideoSize;
    private Integer mSensorOrientation;

    public Integer getmSensorOrientation() {
        return mSensorOrientation;
    }

    public void setmSensorOrientation(Integer mSensorOrientation) {
        this.mSensorOrientation = mSensorOrientation;
    }

    public boolean ismIsRecordingVideo() {
        return mIsRecordingVideo;
    }

    public void setmIsRecordingVideo(boolean mIsRecordingVideo) {
        this.mIsRecordingVideo = mIsRecordingVideo;
    }

    public Size getmVideoSize() {
        return mVideoSize;
    }

    public void setmVideoSize(Size mVideoSize) {
        this.mVideoSize = mVideoSize;
    }


    public void stopRecordingVideo() {
        // UI
        mIsRecordingVideo = false;
        // Stop recording
        mMediaRecorder.stop();
        mMediaRecorder.reset();
    }

    public void setUpMediaRecorder() throws IOException {
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setOutputFile(getVideoFilePath(context));

        mMediaRecorder.setVideoEncodingBitRate(10000000);
        mMediaRecorder.setVideoFrameRate(30);
        mMediaRecorder.setVideoSize(mVideoSize.getWidth(), mVideoSize.getHeight());
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        int rotation = windowManager.getDefaultDisplay().getRotation();
        switch (mSensorOrientation) {
            case SENSOR_ORIENTATION_DEFAULT_DEGREES:
                mMediaRecorder.setOrientationHint(DEFAULT_ORIENTATIONS.get(rotation));
                break;
            case SENSOR_ORIENTATION_INVERSE_DEGREES:
                mMediaRecorder.setOrientationHint(INVERSE_ORIENTATIONS.get(rotation));
                break;
        }
        mMediaRecorder.prepare();
    }

    public static String getDataPath() {
        return Environment.getDataDirectory().getAbsolutePath();
    }

    private File getMp4File() {
        String basePath = getDataPath();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd_hh-mm-ss");
        String format = simpleDateFormat.format(new Date());
        return new File(basePath, format + ".mp4");
    }

    private String getVideoFilePath(Context context) {
        final File dir = context.getExternalFilesDir(null);
        return (dir == null ? "" : (dir.getAbsolutePath() + "/"))
                + System.currentTimeMillis() + ".mp4";
    }

    public void closeMediaRecorder() {
        Log.d("closeMediaRecorder", "tryAcquire");
        if (null != mMediaRecorder) {
            mMediaRecorder.release();
            mMediaRecorder = null;
        }
    }

    public MediaRecorder getmMediaRecorder() {
        return mMediaRecorder;
    }

    public void setmMediaRecorder(MediaRecorder mMediaRecorder) {
        this.mMediaRecorder = mMediaRecorder;
    }
}
