package cn.houyidg.camera2video.record;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.os.Handler;
import android.util.Size;
import android.view.Surface;

import java.io.IOException;
import java.util.List;

public interface IRecordHelper {
    CaptureRequest.Builder setUpParams(List<Surface> surfaces, CameraDevice mCameraDevice, Handler mBackgroundHandler);

    void setmSensorOrientation(Integer mSensorOrientation);

    boolean ismIsRecordingVideo();

    void setmVideoSize(Size mVideoSize);

    String getVideoFilePath(Context context);

    void stopRecordingVideo();

    void startRecord();

}
