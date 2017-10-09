package com.example.android.camera2video;

import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.content.pm.PackageManager;
import android.hardware.camera2.CaptureRequest;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.text.LoginFilter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by zhouliquan on 17-9-27.
 */

public class AppendVideoFragment extends Fragment implements View.OnClickListener{

    private static final String TAG = "zlq";
    private Button btnAppend;

    private String[] permissions = {
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
    };
    private static final int REQUEST_APPEND_PERMISSIONS = 201;

    private MediaExtractor mediaExtractor;
    private MediaMuxer mediaMuxer;
    private int mVideoTrackIndex;


    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        getPermissons();
        return inflater.inflate(R.layout.fragment_append, container, false);

    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        btnAppend = (Button)view.findViewById(R.id.btn_append);
        btnAppend.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        final Activity activity = getActivity();
        switch (view.getId()){
            case R.id.btn_append:
                Log.i(TAG, "onClick: ");
                Toast.makeText(activity, "append", Toast.LENGTH_SHORT).show();
                exactorMedia();
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
            case REQUEST_APPEND_PERMISSIONS:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    Log.i(TAG, "onRequestPermissionsResult: write granted");
                } 
                if (grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "onRequestPermissionsResult: read granted");
                }
                break;
        }
    }

    private void getPermissons() {
        ActivityCompat.requestPermissions(getActivity(), permissions, REQUEST_APPEND_PERMISSIONS);
    }

    public void exactorMedia() {

        File file = null;
        try {
            file = new File(Environment.getExternalStorageDirectory().getCanonicalFile() + "/test1.mp4");
            Log.i(TAG, "exactorMedia: " + file.getAbsolutePath());
            if(!file.canRead()) {
                Log.i(TAG, "exactorMedia: file can't read");
                return;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        mediaExtractor = new MediaExtractor();
        try {
            mediaExtractor.setDataSource(file.getCanonicalPath());

            // 获取视频信道
            int trackCount = mediaExtractor.getTrackCount();
            for(int i = 0; i < trackCount; i++) {
                MediaFormat trackFormat = mediaExtractor.getTrackFormat(i);
                String mimeType = trackFormat.getString(MediaFormat.KEY_MIME);
                // 取出视频信号
                if(mimeType.startsWith("video")) {
                    mVideoTrackIndex = i;
                }
            }

            mediaExtractor.selectTrack(mVideoTrackIndex);
            MediaFormat mediaFormat = mediaExtractor.getTrackFormat(mVideoTrackIndex);

            // 创建混合器
            mediaMuxer = new MediaMuxer(Environment.getExternalStorageDirectory().getCanonicalPath() + "/app.mp4", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int trackIndex = mediaMuxer.addTrack(mediaFormat);
            ByteBuffer byteBuffer = ByteBuffer.allocate(1024*500);
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            mediaMuxer.start();

            // 帧间隔时间
            long videoSampleTime;
            mediaExtractor.readSampleData(byteBuffer, 0);
            if(mediaExtractor.getSampleFlags() == MediaExtractor.SAMPLE_FLAG_SYNC) {
                mediaExtractor.advance();
            }
            mediaExtractor.readSampleData(byteBuffer, 0);
            long firstVideoPTS = mediaExtractor.getSampleTime();
            mediaExtractor.advance();
            mediaExtractor.readSampleData(byteBuffer, 0);
            long secondVideoPTS = mediaExtractor.getSampleTime();
            videoSampleTime = Math.abs(secondVideoPTS - firstVideoPTS);
            Log.i(TAG, "videoSampleTime is " + videoSampleTime);


            // 重新切换此信道，不然上面跳过了3帧， 造成前面的帧数模糊
            mediaExtractor.unselectTrack(trackIndex);
            mediaExtractor.selectTrack(trackIndex);

            while(true) {
                // 读取帧之间的数据
                int readSampleSize = mediaExtractor.readSampleData(byteBuffer, 0);
                if(readSampleSize < 0) {
                    break;
                }
                mediaExtractor.advance();
                bufferInfo.size = readSampleSize;
                bufferInfo.offset = 0;
                switch (mediaExtractor.getSampleFlags()) {
                    case MediaCodec.BUFFER_FLAG_KEY_FRAME:
                        bufferInfo.flags = mediaExtractor.getSampleFlags();
                }

            }

        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    public void appendVideo() {

    }
}
