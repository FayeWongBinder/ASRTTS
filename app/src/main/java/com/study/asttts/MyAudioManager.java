package com.study.asttts;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @作者 zwh
 * @创建日期 2019/6/1 20:49
 */
public class MyAudioManager {
    private final static String TAG = "MyAudioManger->";

    /**
     * 录音数队列
     */
    private ThreadPoolExecutor mExecutor = new ThreadPoolExecutor(6,
            6,
            60,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>());

    private AudioTrack mAudioTrack;
    private AudioRecord mAudioRecord;
    private byte[] mAudioData;
    /*默认数据*/
    private int mSampleRateInHZ = 48000; //采样率
    private int mAudioFormat = AudioFormat.ENCODING_PCM_16BIT;  //位数
    private int mChannelConfig = AudioFormat.CHANNEL_IN_MONO;   //录制声道
    private int mOutChannelConfig = AudioFormat.CHANNEL_OUT_MONO;//播放声道
    /**
     * 缓冲区大小  根据采样率 通道 位数量化参数决定
     */
    private int mRecorderBufferSize;
    private int mTrackbufferSize;

    private volatile boolean isRecording = false;//录音状态标识
    //本地SD卡文件路径
    private final String sdcardFilePath = Environment.getExternalStorageDirectory().getPath() + "/AudioRecord/";
    private String dirFileName;//文件名字

    private MyAudioManager() {
    }

    private static MyAudioManager instance;

    public static MyAudioManager getInstance() {
        if (instance == null)
            instance = new MyAudioManager();
        return instance;
    }

    public void init() {
        mRecorderBufferSize = AudioRecord.getMinBufferSize(mSampleRateInHZ, mChannelConfig, mAudioFormat);
        mAudioData = new byte[mRecorderBufferSize];
        mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, mSampleRateInHZ, mChannelConfig, mAudioFormat, mRecorderBufferSize);

        mTrackbufferSize = AudioTrack.getMinBufferSize(mSampleRateInHZ, mOutChannelConfig, mAudioFormat);// 获得音频缓冲区大小
        mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, mSampleRateInHZ, mOutChannelConfig, mAudioFormat, mRecorderBufferSize * 2
                , AudioTrack.MODE_STREAM);
    }

    public void onStartRecording() {
        if (isRecording) {
            Log.e(TAG, "-----已开始");
            return;
        }
        dirFileName = System.currentTimeMillis() + "ASR_Test";
        final File tmpFile = createFile(dirFileName + ".pcm");
        final File tmpOutFile = createFile(dirFileName + ".wav");
        isRecording = true;
        mAudioRecord.startRecording();
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    FileOutputStream outputStream = new FileOutputStream(tmpFile.getAbsoluteFile());
                    while (isRecording) {
                        int readSize = mAudioRecord.read(mAudioData, 0, mAudioData.length);
                        Log.e(TAG, "run: ------>" + readSize);
                        outputStream.write(mAudioData);
                    }
                    outputStream.close();
                    pcmToWave(tmpFile.getAbsolutePath(), tmpOutFile.getAbsolutePath());
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void onClose() {
        if (!isRecording) {
            Log.e(TAG, "已结束");
            return;
        }
        isRecording = false;
        mAudioRecord.stop();

    }

    public void onPlay() {

        mTrackbufferSize = AudioTrack.getMinBufferSize(mSampleRateInHZ, mOutChannelConfig, mAudioFormat);// 获得音频缓冲区大小
        mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, mSampleRateInHZ, mOutChannelConfig, mAudioFormat, mTrackbufferSize, AudioTrack.MODE_STREAM);
        mAudioTrack.play();

        final File file = new File(sdcardFilePath, dirFileName + ".pcm");
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    FileInputStream fileInputStream = new FileInputStream(file);
                    byte[] tempBuffer = new byte[mTrackbufferSize];
                    while (fileInputStream.available() > 0) {
                        int readCount = fileInputStream.read(tempBuffer);
                        if (readCount == AudioTrack.ERROR_INVALID_OPERATION ||
                                readCount == AudioTrack.ERROR_BAD_VALUE) {
                            continue;
                        }
                        if (readCount != 0 && readCount != -1) {
                            mAudioTrack.write(tempBuffer, 0, readCount);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        });
    }


    private File createFile(String name) {

        File file = new File(sdcardFilePath);
        if (!file.exists()) {
            file.mkdirs();
        }
        String filePath = sdcardFilePath + name;
        File objFile = new File(filePath);
        if (!objFile.exists()) {
            try {
                objFile.createNewFile();
                return objFile;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return null;
    }

    /**
     * 这里得到可播放的音频文件
     *
     * @param inFileName
     * @param outFileName
     */

    private void pcmToWave(String inFileName, String outFileName) {
        FileInputStream in = null;
        FileOutputStream out = null;
        long totalAudioLen = 0;
        long longSampleRate = mSampleRateInHZ;
        long totalDataLen = totalAudioLen + 36;
        int channels = 2;
        long byteRate = 16 * longSampleRate * channels / 8;

        byte[] data = new byte[mRecorderBufferSize];
        try {
            in = new FileInputStream(inFileName);
            out = new FileOutputStream(outFileName);

            totalAudioLen = in.getChannel().size();
            totalDataLen = totalAudioLen + 36;
            writeWaveFileHeader(out, totalAudioLen, totalDataLen, longSampleRate, channels, byteRate);
            while (in.read(data) != -1) {
                out.write(data);
            }

            in.close();
            out.close();
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    /*
  任何一种文件在头部添加相应的头文件才能够确定的表示这种文件的格式，
   */
    private void writeWaveFileHeader(FileOutputStream out, long totalAudioLen, long totalDataLen, long longSampleRate,
                                     int channels, long byteRate) throws IOException {
        byte[] header = new byte[44];
        header[0] = 'R'; // RIFF
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);//数据大小
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';//WAVE
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        //FMT Chunk
        header[12] = 'f'; // 'fmt '
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';//过渡字节
        //数据大小
        header[16] = 16; // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        //编码方式 10H为PCM编码格式
        header[20] = 1; // format = 1
        header[21] = 0;
        //通道数
        header[22] = (byte) channels;
        header[23] = 0;
        //采样率，每个通道的播放速度
        header[24] = (byte) (longSampleRate & 0xff);
        header[25] = (byte) ((longSampleRate >> 8) & 0xff);
        header[26] = (byte) ((longSampleRate >> 16) & 0xff);
        header[27] = (byte) ((longSampleRate >> 24) & 0xff);
        //音频数据传送速率,采样率*通道数*采样深度/8
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        // 确定系统一次要处理多少个这样字节的数据，确定缓冲区，通道数*采样位数
        header[32] = (byte) (1 * 16 / 8);
        header[33] = 0;
        //每个样本的数据位数
        header[34] = 16;
        header[35] = 0;
        //Data chunk
        header[36] = 'd';//data
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);
        out.write(header, 0, 44);
    }
}
