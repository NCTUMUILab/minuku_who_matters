package edu.nctu.minuku.streamgenerator;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;

import edu.nctu.minuku.config.Constants;
import edu.nctu.minuku.dao.RingerDataRecordDAO;
import edu.nctu.minuku.manager.MinukuDAOManager;
import edu.nctu.minuku.manager.MinukuStreamManager;
import edu.nctu.minuku.model.DataRecord.RingerDataRecord;
import edu.nctu.minuku.stream.RingerStream;
import edu.nctu.minukucore.dao.DAOException;
import edu.nctu.minukucore.exception.StreamAlreadyExistsException;
import edu.nctu.minukucore.exception.StreamNotFoundException;
import edu.nctu.minukucore.stream.Stream;

/**
 * Created by Lawrence on 2017/8/22.
 */

public class RingerStreamGenerator extends AndroidStreamGenerator<RingerDataRecord> {

    private String TAG = "RingerStreamGenerator";
    private RingerStream mStream;
    private RingerDataRecordDAO mDAO;

    //audio and ringer
    public static final String RINGER_MODE_NORMAL = "Normal";
    public static final String RINGER_MODE_VIBRATE = "Silent";
    public static final String RINGER_MODE_SILENT = "Vibrate";

    public static final String MODE_CURRENT = "Current";
    public static final String MODE_INVALID = "Invalid";
    public static final String MODE_IN_CALL = "InCall";
    public static final String MODE_IN_COMMUNICATION = "InCommunicaiton";
    public static final String MODE_NORMAL = "Normal";
    public static final String MODE_RINGTONE = "Ringtone";

    //after api 23
    public static AudioDeviceInfo[] mAllAudioDevices;

    private String mRingerMode = "NA";
    private String mAudioMode = "NA";
    private int mStreamVolumeMusic = -9999;
    private int mStreamVolumeNotification = -9999;
    private int mStreamVolumeRing = -9999;
    private int mStreamVolumeVoicecall = -9999;
    private int mStreamVolumeSystem = -9999;
//    private static int mStreamVolumeDTMF = -9999;

    private static AudioManager mAudioManager;

    public static int mainThreadUpdateFrequencyInSeconds = 10;
    public static long mainThreadUpdateFrequencyInMilliseconds = mainThreadUpdateFrequencyInSeconds *Constants.MILLISECONDS_PER_SECOND;

    private Context mContext;

    private static Handler mMainThread;

    public RingerStreamGenerator (Context applicationContext) {
        super(applicationContext);

        mContext = applicationContext;

        this.mStream = new RingerStream(Constants.DEFAULT_QUEUE_SIZE);
        this.mDAO = MinukuDAOManager.getInstance().getDaoFor(RingerDataRecord.class);

        mAudioManager = (AudioManager)mContext.getSystemService(mContext.AUDIO_SERVICE);

        this.register();
    }
    @Override
    public void register() {
        Log.d(TAG, "Registring with StreamManage");

        try {
            MinukuStreamManager.getInstance().register(mStream, RingerDataRecord.class, this);
        } catch (StreamNotFoundException streamNotFoundException) {
            Log.e(TAG, "One of the streams on which" +
                    "RingerDataRecord/RingerStream depends in not found.");
        } catch (StreamAlreadyExistsException streamAlreadyExsistsException) {
            Log.e(TAG, "Another stream which provides" +
                    " RingerDataRecord/RingerStream is already registered.");
        }
    }

    @Override
    public Stream<RingerDataRecord> generateNewStream() {
        return mStream;
    }

    @Override
    public boolean updateStream() {

        Log.d(TAG, "updateStream called");
        //TODO get service data
        RingerDataRecord ringerDataRecord = new RingerDataRecord(mRingerMode,mAudioMode,mStreamVolumeMusic
                ,mStreamVolumeNotification,mStreamVolumeRing,mStreamVolumeVoicecall,mStreamVolumeSystem);
        mStream.add(ringerDataRecord);
        Log.d(TAG, "Ringer to be sent to event bus" + ringerDataRecord);
        // also post an event.
        EventBus.getDefault().post(ringerDataRecord);
        try {
            mDAO.add(ringerDataRecord);
            mDAO.query_counting();
        } catch (DAOException e) {
            e.printStackTrace();
            return false;
        }catch (NullPointerException e){ //Sometimes no data is normal
            e.printStackTrace();
            return false;
        }

        return true;
    }

    @Override
    public long getUpdateFrequency() {
        return 1;
    } //call updateStream everyminute

    @Override
    public void sendStateChangeEvent() {

    }

    @Override
    public void onStreamRegistration() {
//        new Threading().start();
        Log.e(TAG,"onStreamRegistration");

        runPhoneStatusMainThread();

    }

    public void runPhoneStatusMainThread(){

        Log.d(TAG, "runPhoneStatusMainThread") ;

        mMainThread = new Handler();

        Runnable runnable = new Runnable() {
            @Override
            public void run() {

                getAudioRingerUpdate();

                mMainThread.postDelayed(this, mainThreadUpdateFrequencyInMilliseconds);

            }
        };

        mMainThread.post(runnable);
    }

    private void getAudioRingerUpdate() {
        if (mAudioManager.getRingerMode()==AudioManager.RINGER_MODE_NORMAL)
            mRingerMode = RINGER_MODE_NORMAL;
        else if (mAudioManager.getRingerMode()==AudioManager.RINGER_MODE_VIBRATE)
            mRingerMode = RINGER_MODE_VIBRATE;
        else if (mAudioManager.getRingerMode()==AudioManager.RINGER_MODE_SILENT)
            mRingerMode = RINGER_MODE_SILENT;

        int mode = mAudioManager.getMode();
//        Log.d(LOG_TAG, "[getAudioRingerUpdate] ringer mode: " + mRingerMode + " mode: " + mode);

        mStreamVolumeMusic= mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        mStreamVolumeNotification= mAudioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION);
        mStreamVolumeRing= mAudioManager.getStreamVolume(AudioManager.STREAM_RING);
        mStreamVolumeVoicecall = mAudioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL);
        mStreamVolumeSystem= mAudioManager.getStreamVolume(AudioManager.STREAM_SYSTEM);

        mAudioMode = getAudioMode(mAudioManager.getMode());

        Log.d(TAG,"mRingerMode : "+ mRingerMode +" mAudioMode : "+ mAudioMode+" mStreamVolumeMusic : "+ mStreamVolumeMusic
                +" mStreamVolumeNotification : "+ mStreamVolumeNotification+" mStreamVolumeRing : "+ mStreamVolumeRing
                +" mStreamVolumeVoicecall : "+ mStreamVolumeVoicecall +" mStreamVolumeSystem : "+ mStreamVolumeSystem);

        //android 6
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            mAllAudioDevices = mAudioManager.getDevices(AudioManager.GET_DEVICES_ALL);
        }


        mAudioManager.getStreamVolume(AudioManager.STREAM_ALARM);

    }

    public String getAudioMode(int mode) {

        if (mode==AudioManager.MODE_CURRENT)
            return MODE_CURRENT;
        else if (mode==AudioManager.MODE_IN_CALL)
            return MODE_IN_CALL;
        else if (mode==AudioManager.MODE_IN_COMMUNICATION)
            return MODE_IN_COMMUNICATION;
        else if (mode==AudioManager.MODE_INVALID)
            return MODE_INVALID;

        else if (mode==AudioManager.MODE_NORMAL)
            return MODE_NORMAL;

        else if (mode==AudioManager.MODE_RINGTONE)
            return MODE_RINGTONE;
        else
            return "NA";
    }

    @Override
    public void offer(RingerDataRecord ringerdataRecord) {
        mStream.add(ringerdataRecord);
    }

    /*public class Threading extends Thread{
        public void run() {
            Log.d(TAG, "thread is running");
            while (true) {
                try {
                    AudioManager audioManager = (AudioManager) mApplicationContext.getSystemService(Context.AUDIO_SERVICE);
                    int RingerMode = audioManager.getRingerMode();
                    int AudioMode = audioManager.getMode();
                    int StreamVolumeRing = audioManager.getStreamVolume(AudioManager.STREAM_RING);
                    int StreamVolumeMusic = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                    int StreamVolumeNotification = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION);
                    int StreamVolumeVoicecall = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL);
                    int StreamVolumeSystem = audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM);

                    switch (RingerMode) {
                        case AudioManager.RINGER_MODE_SILENT:
                            Log.d("RingerMode", "Silent mode");
                            break;
                        case AudioManager.RINGER_MODE_VIBRATE:
                            Log.d("RingerMode", "Vibrate mode");
                            break;
                        case AudioManager.RINGER_MODE_NORMAL:
                            Log.d("RingerMode", "Normal mode");
                            break;
                    }
                    switch (AudioMode) {
                        case AudioManager.MODE_NORMAL:
                            Log.d("AudioMode", "Normal mode");
                            break;
                        case AudioManager.MODE_RINGTONE:
                            Log.d("AudioMode", "Ring Tone");
                            break;
                        case AudioManager.MODE_IN_CALL:
                            Log.d("AudioMode", "In call");
                            break;
                        case AudioManager.MODE_IN_COMMUNICATION:
                            Log.d("AudioMode", "In Communication");
                            break;
                    }

                    Log.d("StreamVolumeRing", String.valueOf(StreamVolumeRing));
                    Log.d("StreamVolumeMusic", String.valueOf(StreamVolumeMusic));
                    Log.d("StreamVolumeNotification", String.valueOf(StreamVolumeNotification));
                    Log.d("StreamVolumeVoicecall", String.valueOf(StreamVolumeVoicecall));
                    Log.d("StreamVolumeSystem", String.valueOf(StreamVolumeSystem));
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    try {
                        // 10* 1000 = run thread every 10 sec
                        Thread.sleep(10 * 1000);
                    } catch (Exception e) {
                    }
                }
            }
        }
    }*/
}
