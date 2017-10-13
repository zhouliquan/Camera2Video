package com.example.android.camera2video;

import android.Manifest;
import android.app.Fragment;
import android.content.pm.PackageManager;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by zhouliquan on 17-9-27.
 */

public class AppendVideoFragment extends Fragment {

    private static final String SDCARD_PATH = Environment.getExternalStorageDirectory().getPath();
    private String[] VIDEOS = {
            SDCARD_PATH + "/0.mp4",
            SDCARD_PATH + "/1.mp4",
            SDCARD_PATH + "/2.mp4",
            SDCARD_PATH + "/3.mp4",
            SDCARD_PATH + "/4.mp4",
    };

    public void setVideoPaths(String[] strings) {
        VIDEOS = strings;
        for(String s : VIDEOS)
        Log.i(TAG, "setVideoPaths: " + s);
    }

    private static final String TAG = "zlq";
    Button combineVideoBtn;

    private String[] permissions = {
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
    };
    private static final int REQUEST_APPEND_PERMISSIONS = 201;


    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        getPermissons();
        //return inflater.inflate(R.layout.fragment_append, container, false);
        return inflater.inflate(R.layout.test, container, false);

    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        combineVideoBtn = (Button) view.findViewById(R.id.combine_video);
        combineVideoBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                combineVideo3();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_APPEND_PERMISSIONS:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "onRequestPermissionsResult: write granted");
                }
                if (grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "onRequestPermissionsResult: read granted");
                }
                break;
        }
    }

    /**
     * 获取所需权限
     */
    private void getPermissons() {
        ActivityCompat.requestPermissions(getActivity(), permissions, REQUEST_APPEND_PERMISSIONS);
    }

    MediaFormat mVideoFormat;
    MediaFormat mAudioFormat;
    private MediaMuxer mediaMuxer;
    private int mVideoTrackIndex;
    private int mAudioTrackIndex;
    private boolean isAudioTrack = false;
    private long offTime = 0;

    /**
     * 获取音视频格式
     *
     * @throws IOException
     */
    private void getFormats() throws IOException {
        MediaExtractor mediaExtractor = new MediaExtractor();
        mediaExtractor.setDataSource(VIDEOS[0]);
        int trackCount = mediaExtractor.getTrackCount();
        boolean readyVideo = false;
        boolean readyAudio = false;
        for (int i = 0; i < trackCount; i++) {
            if (readyVideo && readyAudio) {
                break;
            }
            MediaFormat mediaFormat = mediaExtractor.getTrackFormat(i);
            String mimeType = mediaFormat.getString(MediaFormat.KEY_MIME);
            if (mimeType.startsWith("video/")) {
                mVideoFormat = mediaFormat;
                readyVideo = true;
                continue;
            } else if (mimeType.startsWith("audio/")) {
                mAudioFormat = mediaFormat;
                readyAudio = true;
                continue;
            }
        }
        mediaExtractor.release();
    }

    private void writeData(ByteBuffer byteBuffer, MediaCodec.BufferInfo bufferInfo, String mediaPath) throws IOException {

        MediaExtractor mediaExtractor = new MediaExtractor();
        mediaExtractor.setDataSource(mediaPath);

        int videoTrackIndex = -1;
        int audioTrackIndex = -1;
        int trackCount = mediaExtractor.getTrackCount();
        boolean readyVideo = false;
        boolean readyAudio = false;
        for (int i = 0; i < trackCount; i++) {
            if (readyAudio && readyVideo) {
                break;
            }
            MediaFormat mediaFormat = mediaExtractor.getTrackFormat(i);
            String mimeType = mediaFormat.getString(MediaFormat.KEY_MIME);
            if (mimeType.startsWith("video/")) {
                videoTrackIndex = i;
                continue;
            } else if (mimeType.startsWith("audio/")) {
                audioTrackIndex = i;
                continue;
            }
        }

        mediaExtractor.selectTrack(videoTrackIndex);
        long tempOffTime = 0;
        while (true) {
            int readVideoSampleSize = mediaExtractor.readSampleData(byteBuffer, 0);
            if (readVideoSampleSize < 0) {
                break;
            }
            bufferInfo.size = readVideoSampleSize;
            tempOffTime = mediaExtractor.getSampleTime();
            bufferInfo.presentationTimeUs = offTime + tempOffTime;
            bufferInfo.offset = 0;
            bufferInfo.flags = mediaExtractor.getSampleFlags();
            mediaMuxer.writeSampleData(mVideoTrackIndex, byteBuffer, bufferInfo);
            mediaExtractor.advance();
        }

        mediaExtractor.unselectTrack(videoTrackIndex);
        mediaExtractor.selectTrack(audioTrackIndex);

        while (true) {
            int readAudioSampleSize = mediaExtractor.readSampleData(byteBuffer, 0);
            if (readAudioSampleSize < 0) {

                break;
            }

            bufferInfo.size = readAudioSampleSize;
            bufferInfo.presentationTimeUs = offTime + mediaExtractor.getSampleTime();
            bufferInfo.offset = 0;
            bufferInfo.flags = mediaExtractor.getSampleFlags();
            mediaMuxer.writeSampleData(mAudioTrackIndex, byteBuffer, bufferInfo);
            mediaExtractor.advance();
        }
        offTime += tempOffTime;

        mediaExtractor.release();
    }

    public void combineVideo3() {
        try {

            getFormats();
            Log.i(TAG, "video: " + mVideoFormat.toString() + "\naudio: " + mAudioFormat.toString());

            mediaMuxer = new MediaMuxer(SDCARD_PATH + "/output", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            mediaMuxer.setOrientationHint(90);
            mVideoTrackIndex = mediaMuxer.addTrack(mVideoFormat);
            mAudioTrackIndex = mediaMuxer.addTrack(mAudioFormat);
            mediaMuxer.start();

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            ByteBuffer byteBuffer = ByteBuffer.allocate(500 * 1024);
            for(String path : VIDEOS){
                writeData(byteBuffer, bufferInfo, path);
            }
//            writeData(byteBuffer, bufferInfo, VIDEOS[0]);
//            writeData(byteBuffer, bufferInfo, VIDEOS[1]);

            mediaMuxer.stop();
            mediaMuxer.release();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void combineVideo2() {
        try {

            getFormats();
            Log.i(TAG, "video: " + mVideoFormat.toString() + "\naudio: " + mAudioFormat.toString());

            mediaMuxer = new MediaMuxer(SDCARD_PATH + "/output", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            mVideoTrackIndex = mediaMuxer.addTrack(mVideoFormat);
            mAudioTrackIndex = mediaMuxer.addTrack(mAudioFormat);
            mediaMuxer.start();

            MediaCodec.BufferInfo videoBufferInfo = new MediaCodec.BufferInfo();
            MediaCodec.BufferInfo audioBufferInfo = new MediaCodec.BufferInfo();
            ByteBuffer byteBuffer = ByteBuffer.allocate(500 * 1024);

            MediaExtractor mediaExtractor = new MediaExtractor();
            mediaExtractor.setDataSource(VIDEOS[0]);

            int videoTrackIndex = -1;
            int audioTrackIndex = -1;
            int trackCount = mediaExtractor.getTrackCount();
            boolean readyVideo = false;
            boolean readyAudio = false;
            for (int i = 0; i < trackCount; i++) {
                if (readyAudio && readyVideo) {
                    break;
                }
                MediaFormat mediaFormat = mediaExtractor.getTrackFormat(i);
                String mimeType = mediaFormat.getString(MediaFormat.KEY_MIME);
                if (mimeType.startsWith("video/")) {
                    videoTrackIndex = i;
                    continue;
                } else if (mimeType.startsWith("audio/")) {
                    audioTrackIndex = i;
                    continue;
                }
            }

            mediaExtractor.selectTrack(videoTrackIndex);

            /*long sampleTime = 0;
            {
                mediaExtractor.readSampleData(byteBuffer, 0);
                if (mediaExtractor.getSampleFlags() == MediaExtractor.SAMPLE_FLAG_SYNC) {
                    mediaExtractor.advance();
                }
                mediaExtractor.readSampleData(byteBuffer, 0);
                long secondTime = mediaExtractor.getSampleTime();
                mediaExtractor.advance();
                long thirdTime = mediaExtractor.getSampleTime();
                sampleTime = Math.abs(thirdTime - secondTime);
            }
            mediaExtractor.unselectTrack(videoTrackIndex);*/
            mediaExtractor.selectTrack(videoTrackIndex);

            while (true) {
                int readVideoSampleSize = mediaExtractor.readSampleData(byteBuffer, 0);
                if (readVideoSampleSize < 0) {
                    break;
                }
                videoBufferInfo.size = readVideoSampleSize;
//                videoBufferInfo.presentationTimeUs += sampleTime;
                videoBufferInfo.presentationTimeUs = mediaExtractor.getSampleTime();
                videoBufferInfo.offset = 0;
                videoBufferInfo.flags = mediaExtractor.getSampleFlags();
//                mediaMuxer.writeSampleData(writeVideoTrackIndex, byteBuffer, videoBufferInfo);
                mediaMuxer.writeSampleData(mVideoTrackIndex, byteBuffer, videoBufferInfo);
                mediaExtractor.advance();
            }

            mediaExtractor.unselectTrack(videoTrackIndex);
            mediaExtractor.selectTrack(audioTrackIndex);
            long offTime = -1;
            while (true) {
//                int readAudioSampleSize = audioExtractor.readSampleData(byteBuffer, 0);
                int readAudioSampleSize = mediaExtractor.readSampleData(byteBuffer, 0);
                if (readAudioSampleSize < 0) {

                    break;
                }

                audioBufferInfo.size = readAudioSampleSize;
//                audioBufferInfo.presentationTimeUs += sampleTime;
                audioBufferInfo.presentationTimeUs = offTime = mediaExtractor.getSampleTime();
                audioBufferInfo.offset = 0;
                audioBufferInfo.flags = mediaExtractor.getSampleFlags();
//                mediaMuxer.writeSampleData(writeAudioTrackIndex, byteBuffer, audioBufferInfo);
                mediaMuxer.writeSampleData(mAudioTrackIndex, byteBuffer, audioBufferInfo);
//                audioExtractor.advance();
                mediaExtractor.advance();
            }

            //mediaMuxer.stop();
            //mediaMuxer.release();
            mediaExtractor.release();
//            audioExtractor.release();

/*
            MediaExtractor videoExtractor2 = new MediaExtractor();
            videoExtractor2.setDataSource(SDCARD_PATH + "/test.mp4");
            int videoTrackIndex2 = -1;
            int videoTrackCount2 = videoExtractor2.getTrackCount();
            int audioTrackIndex2 = -1;
            for (int i = 0; i < videoTrackCount2; i++) {
                MediaFormat mediaFormat = videoExtractor2.getTrackFormat(i);
//                mVideoFormat = mediaExtractor.getTrackFormat(i);
                String mimeType = mediaFormat.getString(MediaFormat.KEY_MIME);
                if (mimeType.startsWith("video/")) {
                    videoTrackIndex2 = i;
//                    break;
                    continue;
                } else if (mimeType.startsWith("audio/")) {
                    audioTrackIndex2 = i;
                    continue;
                }
            }

            videoExtractor2.selectTrack(videoTrackIndex);

            while (true) {
                int readVideoSampleSize = videoExtractor2.readSampleData(byteBuffer, 0);
                if (readVideoSampleSize < 0) {
                    break;
                }
                videoBufferInfo.size = readVideoSampleSize;
//                videoBufferInfo.presentationTimeUs += sampleTime;
                videoBufferInfo.presentationTimeUs = videoExtractor2.getSampleTime() + offTime;
                videoBufferInfo.offset = 0;
                videoBufferInfo.flags = videoExtractor2.getSampleFlags();
//                mediaMuxer.writeSampleData(writeVideoTrackIndex, byteBuffer, videoBufferInfo);
                mediaMuxer.writeSampleData(mVideoTrackIndex, byteBuffer, videoBufferInfo);
                videoExtractor2.advance();
            }

            videoExtractor2.unselectTrack(videoTrackIndex);
            videoExtractor2.selectTrack(audioTrackIndex);
            while (true) {
//                int readAudioSampleSize = audioExtractor.readSampleData(byteBuffer, 0);
                int readAudioSampleSize = videoExtractor2.readSampleData(byteBuffer, 0);
                if (readAudioSampleSize < 0) {

                    break;
                }

                audioBufferInfo.size = readAudioSampleSize;
//                audioBufferInfo.presentationTimeUs += sampleTime;
                audioBufferInfo.presentationTimeUs = videoExtractor2.getSampleTime() + offTime;
                audioBufferInfo.offset = 0;
                audioBufferInfo.flags = videoExtractor2.getSampleFlags();
//                mediaMuxer.writeSampleData(writeAudioTrackIndex, byteBuffer, audioBufferInfo);
                mediaMuxer.writeSampleData(mAudioTrackIndex, byteBuffer, audioBufferInfo);
//                audioExtractor.advance();
                videoExtractor2.advance();
            }
*/
            mediaMuxer.stop();
            mediaMuxer.release();
//            videoExtractor2.release();


        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}