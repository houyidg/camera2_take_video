package cn.houyidg.camera2video.record.ffmpeg;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.util.Size;
import android.view.View;

import cn.houyidg.camera2video.record.FfmpegHelper;
import cn.houyidg.camera2video.record.ffmpeg.data.FrameToRecord;
import cn.houyidg.camera2video.record.ffmpeg.data.RecordFragment;

import org.bytedeco.javacpp.avcodec;
import org.bytedeco.javacpp.avutil;
import org.bytedeco.javacv.FFmpegFrameFilter;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameFilter;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.LinkedBlockingQueue;

import static java.lang.Thread.State.WAITING;

public class FFmpegRecordUtils implements View.OnClickListener, FfmpegHelper.OnPreviewFrameCallback {
    private static final String LOG_TAG = "FFmpegRecordUtils";

    private FFmpegFrameRecorder mFrameRecorder;
    private VideoRecordThread mVideoRecordThread;
    //    private AudioRecordThread mAudioRecordThread;
    private volatile boolean mRecording = false;
    private File mVideo;
    private LinkedBlockingQueue<FrameToRecord> mFrameToRecordQueue;
    private LinkedBlockingQueue<FrameToRecord> mRecycledFrameQueue;
    private int mFrameToRecordCount;
    private int mFrameRecordedCount;
    private long mTotalProcessFrameTime;
    private Stack<RecordFragment> mRecordFragments;

    private int sampleAudioRateInHz = 44100;
    /* The sides of width and height are based on camera orientation.
    That is, the preview size is the size before it is rotated. */
    //    private int mPreviewWidth = PREFERRED_PREVIEW_WIDTH;
    //    private int mPreviewHeight = PREFERRED_PREVIEW_HEIGHT;
    // Output video size
    private int frameRate = 30;
    private int frameDepth = Frame.DEPTH_UBYTE;
    private int frameChannels = 2;
    private int rotation;
    private Context context;
    private Size mVideoSize;

    public Size getmVideoSize() {
        return mVideoSize;
    }

    public void setmVideoSize(Size mVideoSize) {
        this.mVideoSize = mVideoSize;
    }

    public FFmpegRecordUtils(Context context) {
        this.context = context;
        // At most buffer 10 Frame
        mFrameToRecordQueue = new LinkedBlockingQueue<>(10);
        // At most recycle 2 Frame
        mRecycledFrameQueue = new LinkedBlockingQueue<>(2);
        mRecordFragments = new Stack<>();
    }

    protected void onDestroy() {
        stopRecorder();
        releaseRecorder(true);
    }

    @Override
    public void onClick(View v) {
        int i = v.getId();
        //        if (i == R.id.btn_resume_or_pause) {
        //            if (mRecording) {
        //                pauseRecording();
        //            } else {
        //                resumeRecording();
        //            }
        //        } else if (i == R.id.btn_done) {
        //            pauseRecording();
        //            new FinishRecordingTask().execute();
        //        }
    }

    public void setRotation(int rotation) {
        this.rotation = rotation;
    }

    public void stopRecorderTask() {
        pauseRecording();
        stopRecording();
        stopRecorder();
        releaseRecorder(false);
    }

    public void startRecorderTask(String currentRecordPath) {
        resumeRecording();
        if (mFrameRecorder == null) {
            initRecorder(currentRecordPath);
            startRecorder();
        }
        startRecording();
    }

    private void initRecorder(String currentRecordPath) {
        Log.i(LOG_TAG, "init mFrameRecorder");
        mVideo = new File(currentRecordPath);
        Log.i(LOG_TAG, "Output Video: " + mVideo);

        mFrameRecorder = new FFmpegFrameRecorder(mVideo, mVideoSize.getWidth(), mVideoSize.getHeight());
        mFrameRecorder.setFormat("mp4");
        mFrameRecorder.setSampleRate(sampleAudioRateInHz);
        mFrameRecorder.setFrameRate(frameRate);
        mFrameRecorder.setVideoQuality(100);
        mFrameRecorder.setVideoBitrate(2000000);
        // Use H264
        mFrameRecorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
        // See: https://trac.ffmpeg.org/wiki/Encode/H.264#crf
        /*
         * The range of the quantizer scale is 0-51: where 0 is lossless, 23 is default, and 51 is worst possible. A lower value is a higher quality and a subjectively sane range is 18-28. Consider 18 to be visually lossless or nearly so: it should look the same or nearly the same as the input but it isn't technically lossless.
         * The range is exponential, so increasing the CRF value +6 is roughly half the bitrate while -6 is roughly twice the bitrate. General usage is to choose the highest CRF value that still provides an acceptable quality. If the output looks good, then try a higher value and if it looks bad then choose a lower value.
         */
        mFrameRecorder.setVideoOption("crf", "12");
        mFrameRecorder.setVideoOption("preset", "superfast");
        mFrameRecorder.setVideoOption("tune", "zerolatency");

        Log.i(LOG_TAG, "mFrameRecorder initialize success");
    }

    private void releaseRecorder(boolean deleteFile) {
        if (mFrameRecorder != null) {
            try {
                mFrameRecorder.release();
            } catch (FFmpegFrameRecorder.Exception e) {
                e.printStackTrace();
            }
            mFrameRecorder = null;

            if (deleteFile) {
                mVideo.delete();
            }
        }
    }

    private void startRecorder() {
        try {
            mFrameRecorder.start();
        } catch (FFmpegFrameRecorder.Exception e) {
            e.printStackTrace();
        }
    }

    private void stopRecorder() {
        if (mFrameRecorder != null) {
            try {
                mFrameRecorder.stop();
            } catch (FFmpegFrameRecorder.Exception e) {
                e.printStackTrace();
            }
        }
        mRecordFragments.clear();
    }

    private void startRecording() {
        mVideoRecordThread = new VideoRecordThread();
        mVideoRecordThread.start();
    }

    private void stopRecording() {
        if (mVideoRecordThread != null) {
            if (mVideoRecordThread.isRunning()) {
                mVideoRecordThread.stopRunning();
            }
        }
        try {
            if (mVideoRecordThread != null) {
                mVideoRecordThread.join();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mVideoRecordThread = null;

        mFrameToRecordQueue.clear();
        mRecycledFrameQueue.clear();
    }

    private void resumeRecording() {
        if (!mRecording) {
            RecordFragment recordFragment = new RecordFragment();
            recordFragment.setStartTimestamp(System.currentTimeMillis());
            mRecordFragments.push(recordFragment);
            mRecording = true;
        }
    }

    private void pauseRecording() {
        if (mRecording) {
            mRecordFragments.peek().setEndTimestamp(System.currentTimeMillis());
            mRecording = false;
        }
    }

    private long calculateTotalRecordedTime(Stack<RecordFragment> recordFragments) {
        long recordedTime = 0;
        for (RecordFragment recordFragment : recordFragments) {
            recordedTime += recordFragment.getDuration();
        }
        return recordedTime;
    }

    @Override
    public void onCameraBack(byte[][] bytes, int orientation) {

    }

    @Override
    public void onCameraBack(byte[] data, int orientation) {
        // get video data
        Log.e(LOG_TAG, "onCameraBack: mRecording:" + mRecording + ",data:" + data.length);
        if (mRecording) {
            // pop the current record fragment when calculate total recorded time
            RecordFragment curFragment = mRecordFragments.pop();
            long recordedTime = calculateTotalRecordedTime(mRecordFragments);
            // push it back after calculation
            mRecordFragments.push(curFragment);
            long curRecordedTime = System.currentTimeMillis() - curFragment.getStartTimestamp() + recordedTime;

            long timestamp = 1000 * curRecordedTime;
            Frame frame;
            FrameToRecord frameToRecord = mRecycledFrameQueue.poll();
            if (frameToRecord != null) {
                frame = frameToRecord.getFrame();
                frameToRecord.setTimestamp(timestamp);
            } else {
                frame = new Frame(mVideoSize.getWidth(), mVideoSize.getHeight(), frameDepth, frameChannels);
                frameToRecord = new FrameToRecord(timestamp, frame);
            }
            ((ByteBuffer) frame.image[0].position(0)).put(data);

            if (mFrameToRecordQueue.offer(frameToRecord)) {
                mFrameToRecordCount++;
            }
        }
    }

    class RunningThread extends Thread {
        boolean isRunning;

        public boolean isRunning() {
            return isRunning;
        }

        public void stopRunning() {
            this.isRunning = false;
        }
    }

    class VideoRecordThread extends RunningThread {
        @Override
        public void run() {
            List<String> filters = new ArrayList<>();
            // Transpose
            String transpose ="transpose=1";
            String hflip = null;//"hflip";
            String vflip = null;//"vflip";

            // transpose
            if (transpose != null) {
                filters.add(transpose);
            }
            // horizontal flip
            if (hflip != null) {
                filters.add(hflip);
            }
            // vertical flip
            if (vflip != null) {
                filters.add(vflip);
            }
            String join = TextUtils.join(",", filters);
            Log.e(LOG_TAG, "run: join" + join);
            FFmpegFrameFilter frameFilter = new FFmpegFrameFilter(join, mVideoSize.getWidth(), mVideoSize.getHeight());
            frameFilter.setPixelFormat(avutil.AV_PIX_FMT_NV21);
            frameFilter.setFrameRate(frameRate);
            frameFilter.setSampleRate(sampleAudioRateInHz);
            try {
                frameFilter.start();
            } catch (FrameFilter.Exception e) {
                e.printStackTrace();
            }

            isRunning = true;
            FrameToRecord recordedFrame;

            while (isRunning || !mFrameToRecordQueue.isEmpty()) {
                try {
                    recordedFrame = mFrameToRecordQueue.take();
                } catch (InterruptedException ie) {
                    ie.printStackTrace();
                    try {
                        frameFilter.stop();
                    } catch (FrameFilter.Exception e) {
                        e.printStackTrace();
                    }
                    break;
                }

                if (mFrameRecorder != null) {
                    long timestamp = recordedFrame.getTimestamp();
                    if (timestamp > mFrameRecorder.getTimestamp()) {
                        mFrameRecorder.setTimestamp(timestamp);
                    }
                    long startTime = System.currentTimeMillis();
                    //                    Frame filteredFrame = recordedFrame.getFrame();
                    Frame filteredFrame = null;
                    try {
                        frameFilter.push(recordedFrame.getFrame());
                        filteredFrame = frameFilter.pull();
                    } catch (FrameFilter.Exception e) {
                        e.printStackTrace();
                    }
                    try {
                        mFrameRecorder.record(filteredFrame);
                    } catch (FFmpegFrameRecorder.Exception e) {
                        e.printStackTrace();
                    }
                    long endTime = System.currentTimeMillis();
                    long processTime = endTime - startTime;
                    mTotalProcessFrameTime += processTime;
                    Log.d(LOG_TAG, "This frame process time: " + processTime + "ms");
                    long totalAvg = mTotalProcessFrameTime / ++mFrameRecordedCount;
                    Log.d(LOG_TAG, "Avg frame process time: " + totalAvg + "ms");
                }
                Log.d(LOG_TAG, mFrameRecordedCount + " / " + mFrameToRecordCount);
                mRecycledFrameQueue.offer(recordedFrame);
            }
        }

        public void stopRunning() {
            super.stopRunning();
            if (getState() == WAITING) {
                interrupt();
            }
        }
    }
}
