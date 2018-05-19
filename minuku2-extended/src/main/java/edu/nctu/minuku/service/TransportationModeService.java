package edu.nctu.minuku.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.location.DetectedActivity;
import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import edu.nctu.minuku.config.Constants;
import edu.nctu.minuku.manager.MinukuStreamManager;
import edu.nctu.minuku.model.DataRecord.ActivityRecognitionDataRecord;
import edu.nctu.minuku.model.DataRecord.TransportationModeDataRecord;
import edu.nctu.minuku.streamgenerator.ActivityRecognitionStreamGenerator;
import edu.nctu.minuku.streamgenerator.TransportationModeStreamGenerator;
import edu.nctu.minukucore.exception.StreamNotFoundException;

import static edu.nctu.minuku.streamgenerator.ActivityRecognitionStreamGenerator.getLocalRecordPool;

/**
 * Created by Lawrence on 2017/7/19.
 */

public class TransportationModeService extends Service {

    public String TAG = "TransportationModeService";

    public static TransportationModeStreamGenerator transportationModeStreamGenerator;

    /**ContextSourceType**/
    public static final int CONTEXT_SOURCE_TRANSPORTATION = 0;
    public static final int CONTEXT_SOURCE_DETECTION_STATE = 1;

    public static final String STRING_CONTEXT_SOURCE_TRANSPORTATION = "Transportation";
    public static final String STRING_CONTEXT_SOURCE_DETECTION_STATE = "DetectionState";

    /**Table Name**/
    public static final String RECORD_TABLE_NAME_TRANSPORTATION = "Record_Table_Transportation";

    public static final int STATE_INITIAL = -1;
    public static final int STATE_STATIC = 0;
    public static final int STATE_SUSPECTING_START = 1;
    public static final int STATE_CONFIRMED = 2;
    public static final int STATE_SUSPECTING_STOP = 3;

    //
    private static final float CONFIRM_START_ACTIVITY_THRESHOLD_IN_VEHICLE = (float) 0.6;
    private static final float CONFIRM_START_ACTIVITY_THRESHOLD_ON_FOOT = (float)0.6;
    private static final float CONFIRM_START_ACTIVITY_THRESHOLD_ON_BICYCLE =(float) 0.6;
    private static final float CONFIRM_STOP_ACTIVITY_THRESHOLD_IN_VEHICLE = (float)0.3; //0.2
    private static final float CONFIRM_STOP_ACTIVITY_THRESHOLD_ON_FOOT = (float)0.3; //0.1
    private static final float CONFIRM_STOP_ACTIVITY_THRESHOLD_ON_BICYCLE =(float) 0.3; //0.2

    public static final int CONFIRM_START_ACTIVITY_Needed_Confidence = 40;
    public static final int CONFIRM_STOP_ACTIVITY_Needed_Confidence = 40;
    public static final int CANCEL_SUSPECT_Threshold = 95;
    public static final int SWTICH_TO_NEW_ACTIVITY_Threshold = 95;

    /**label **/
    public static final String STRING_DETECTED_ACTIVITY_IN_VEHICLE = "in_vehicle";
    public static final String STRING_DETECTED_ACTIVITY_ON_FOOT = "on_foot";
    public static final String STRING_DETECTED_ACTIVITY_WALKING = "walking";
    public static final String STRING_DETECTED_ACTIVITY_RUNNING = "running";
    public static final String STRING_DETECTED_ACTIVITY_TILTING = "tilting";
    public static final String STRING_DETECTED_ACTIVITY_STILL = "still";
    public static final String STRING_DETECTED_ACTIVITY_ON_BICYCLE = "on_bicycle";
    public static final String STRING_DETECTED_ACTIVITY_UNKNOWN = "unknown";
    public static final String STRING_DETECTED_ACTIVITY_NA = "NA";

    public static final String TRANSPORTATION_MODE_NAME_IN_VEHICLE = STRING_DETECTED_ACTIVITY_IN_VEHICLE;
    public static final String TRANSPORTATION_MODE_NAME_ON_FOOT = STRING_DETECTED_ACTIVITY_ON_FOOT;
    public static final String TRANSPORTATION_MODE_NAME_ON_BICYCLE = STRING_DETECTED_ACTIVITY_ON_BICYCLE;
    public static final String TRANSPORTATION_MODE_NAME_NO_TRANSPORTATION = "static";
    public static final String TRANSPORTATION_MODE_NAME_NA = "NA";

    private static final long WINDOW_LENGTH_START_ACTIVITY_DEFAULT = 20 * Constants.MILLISECONDS_PER_SECOND;
    private static final long WINDOW_LENGTH_STOP_ACTIVITY_DEFAULT = 20 * Constants.MILLISECONDS_PER_SECOND;
    private static final long WINDOW_LENGTH_START_ACTIVITY_IN_VEHICLE = 20 * Constants.MILLISECONDS_PER_SECOND; //TODO origin為10s
    private static final long WINDOW_LENGTH_START_ACTIVITY_ON_FOOT = 10 * Constants.MILLISECONDS_PER_SECOND;
    private static final long WINDOW_LENGTH_START_ACTIVITY_ON_BICYCLE = 20 * Constants.MILLISECONDS_PER_SECOND;
    private static final long WINDOW_LENGTH_STOP_ACTIVITY_IN_VEHICLE = 150 * Constants.MILLISECONDS_PER_SECOND;
    private static final long WINDOW_LENGTH_STOP_ACTIVITY_ON_FOOT = 30 * Constants.MILLISECONDS_PER_SECOND; //TODO origin為60s
    private static final long WINDOW_LENGTH_STOP_ACTIVITY_ON_BICYCLE = 90 * Constants.MILLISECONDS_PER_SECOND;

    private static final long WINDOW_LENGTH_TRANSITION_START_ACTIVITY_IN_VEHICLE = 5 * Constants.MILLISECONDS_PER_SECOND;
    private static final long WINDOW_LENGTH_TRANSITION_START_ACTIVITY_ON_FOOT = 10 * Constants.MILLISECONDS_PER_SECOND;
    private static final long WINDOW_LENGTH_TRANSITION_START_ACTIVITY_ON_BICYCLE = 10 * Constants.MILLISECONDS_PER_SECOND;
    private static final long WINDOW_LENGTH_TRANSITION_STOP_ACTIVITY_IN_VEHICLE = 75 * Constants.MILLISECONDS_PER_SECOND;
    private static final long WINDOW_LENGTH_TRANSITION_STOP_ACTIVITY_ON_FOOT = 10 * Constants.MILLISECONDS_PER_SECOND;
    private static final long WINDOW_LENGTH_TRANSITION_STOP_ACTIVITY_ON_BICYCLE = 45 * Constants.MILLISECONDS_PER_SECOND;

    //the frequency of requesting google activity from the google play service
    public static int ACTIVITY_RECOGNITION_DEFAULT_UPDATE_INTERVAL_IN_SECONDS = 5;

    public static long ACTIVITY_RECOGNITION_DEFAULT_UPDATE_INTERVAL =
            ACTIVITY_RECOGNITION_DEFAULT_UPDATE_INTERVAL_IN_SECONDS * Constants.MILLISECONDS_PER_SECOND;

    private static long sActivityRecognitionUpdateIntervalInSeconds = ACTIVITY_RECOGNITION_DEFAULT_UPDATE_INTERVAL_IN_SECONDS;

    private static long sActivityRecognitionUpdateIntervalInMilliseconds =
            sActivityRecognitionUpdateIntervalInSeconds * Constants.MILLISECONDS_PER_SECOND;

    /**Properties for Record**/
    public static final String RECORD_DATA_PROPERTY_TRANSPORTATION = "Transportation";


    public static final int NO_ACTIVITY_TYPE = -1;
    public static final int IN_VEHICLE = DetectedActivity.IN_VEHICLE;
    public static final int ON_FOOT = DetectedActivity.ON_FOOT;
    public static final int ON_BICYCLE = DetectedActivity.ON_BICYCLE;
    public static final int UNKNOWN = DetectedActivity.UNKNOWN;
    public static final int STILL = DetectedActivity.STILL;
    public static final int TILTING = DetectedActivity.TILTING;

    private static ArrayList<ActivityRecognitionDataRecord> mActivityRecognitionRecords;


    /**Constant **/
    private static int mSuspectedStartActivityType = NO_ACTIVITY_TYPE;
    private static int mSuspectedStopActivityType = NO_ACTIVITY_TYPE;
    private static int mConfirmedActivityType = NO_ACTIVITY_TYPE;// the initial value of activity is STILL.
    private static long mSuspectTime = 0;
    private static int mCurrentState = STATE_STATIC;


    private static final String PACKAGE_DIRECTORY_PATH="/Android/data/edu.nctu.minuku_2/";
    private CSVWriter csv_writer = null;

    private ActivityRecognitionDataRecord latest_activityRecognitionDataRecord;

    private static Context serviceInstance = null;
    private Context mContext;

    private ScheduledExecutorService mScheduledExecutorService;
    public static final int TransportationMode_REFRESH_FREQUENCY = 5; //1s, 1000ms
    public static final int BACKGROUND_RECORDING_INITIAL_DELAY = 0;
    private final int TransportationMode_ThreadSize = 1;

    private SharedPreferences sharedPrefs;

    public TransportationModeService(){}


    public void onDestroy(){
        super.onDestroy();

        Log.d(TAG, "onDestroy");

        ServiceDestroying_StoreToCSV_onDestroy(new Date().getTime(), "TransportationMode.csv");
        ServiceDestroying_StoreToCSV_onDestroy(new Date().getTime(), "TransportationState.csv");
        ServiceDestroying_StoreToCSV_onDestroy(new Date().getTime(),  "windowdata.csv");

        sharedPrefs.edit().putInt("CurrentState", mCurrentState).apply();
        sharedPrefs.edit().putInt("ConfirmedActivityType", mConfirmedActivityType).apply();

        mScheduledExecutorService.shutdown();
    }

    @Override
    public void onTaskRemoved(Intent intent){
        super.onTaskRemoved(intent);
        Log.d(TAG, "onTaskRemoved");

        ServiceDestroying_StoreToCSV_onTaskRemoved(new Date().getTime(), "TransportationMode.csv");
        ServiceDestroying_StoreToCSV_onTaskRemoved(new Date().getTime(), "TransportationState.csv");
        ServiceDestroying_StoreToCSV_onTaskRemoved(new Date().getTime(),  "windowdata.csv");

        sharedPrefs.edit().putInt("CurrentState", mCurrentState).apply();
        sharedPrefs.edit().putInt("ConfirmedActivityType", mConfirmedActivityType).apply();

        mScheduledExecutorService.shutdown();
    }

    @Override
    public void onLowMemory(){
        super.onLowMemory();

        ServiceDestroying_StoreToCSV_onLowMemory(new Date().getTime(), "TransportationMode.csv");
        ServiceDestroying_StoreToCSV_onLowMemory(new Date().getTime(), "TransportationState.csv");
        ServiceDestroying_StoreToCSV_onLowMemory(new Date().getTime(),  "windowdata.csv");

        sharedPrefs.edit().putInt("CurrentState", mCurrentState).apply();
        sharedPrefs.edit().putInt("ConfirmedActivityType", mConfirmedActivityType).apply();
    }

    public void onCreate(){
        super.onCreate();
        Log.d(TAG, "onCreate");

        serviceInstance = this;

        mContext = this;

        mScheduledExecutorService = Executors.newScheduledThreadPool(TransportationMode_ThreadSize);

        sharedPrefs = getSharedPreferences("edu.umich.minuku_2", MODE_PRIVATE);

        mCurrentState = sharedPrefs.getInt("CurrentState", STATE_STATIC);
        mConfirmedActivityType = sharedPrefs.getInt("ConfirmedActivityType", NO_ACTIVITY_TYPE);

    }

    public static boolean isServiceRunning() {
        return serviceInstance != null;
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Log.d(TAG, "[test service running] going to start the probe service, isServiceRunning:  " + isServiceRunning());

        startService();

        return START_REDELIVER_INTENT;
    }

    public void startService(){

        runMainThread();

    }

    private void runMainThread(){

        mScheduledExecutorService.scheduleAtFixedRate(
                TransportationModeRunnable,
                BACKGROUND_RECORDING_INITIAL_DELAY,
                TransportationMode_REFRESH_FREQUENCY,
                TimeUnit.SECONDS);
    }

    Runnable TransportationModeRunnable = new Runnable() {
        @Override
        public void run() {

            Log.d(TAG, "TransportationModeRunnable");

            try {
                transportationModeStreamGenerator = (TransportationModeStreamGenerator) MinukuStreamManager.getInstance().getStreamGeneratorFor(TransportationModeDataRecord.class);
            }catch(StreamNotFoundException e){
                Log.e(TAG,"transportationModeStreamGenerator haven't created yet.");
            }

            if(MinukuStreamManager.getInstance().getActivityRecognitionDataRecord()!=null){

                ActivityRecognitionDataRecord record = MinukuStreamManager.getInstance().getActivityRecognitionDataRecord();//activityRecognitionStreamGenerator.getLastSavedRecord();

                if (record!=null) {

                    //getting latest Transportation based on the incoming record
                    examineTransportation(record);

                    Log.d("ARService", "[test replay] test trip: after examine transportation the current activity is  is " + getConfirmedActvitiyString() + " the status is " + getCurrentState());

                    // showTransportation(getConfirmedActivityString());

                    try {
                        transportationModeStreamGenerator.setTransportationModeDataRecord(getConfirmedActvitiyString());

                    }catch(Exception e){
                        e.printStackTrace();
                    }
                }

                sharedPrefs.edit().putInt("CurrentState", mCurrentState).apply();
                sharedPrefs.edit().putInt("ConfirmedActivityType", mConfirmedActivityType).apply();

                //write transportation mode record
                TransportationMode_StoreToCSV(new Date().getTime(), latest_activityRecognitionDataRecord, getConfirmedActvitiyString(), mCurrentState);

                if(record.getMostProbableActivity().getConfidence()!=999){ //conf == 999 means it didn't receive anything from AR
                    latest_activityRecognitionDataRecord = record;

                    latest_activityRecognitionDataRecord.getProbableActivities();

                }
            }
            else
                Log.e(TAG, "TransportationMode's Stream might not start working yet.");

        }
    };

    public static String getTimeString(long time){

        SimpleDateFormat sdf_now = new SimpleDateFormat(Constants.DATE_FORMAT_NOW_SLASH);
        String currentTimeString = sdf_now.format(time);

        return currentTimeString;
    }

    private void showTransportation(String transportation){

        Log.e(TAG,"showTransportation");

        String local_transportation = transportation;

        String NotificationText = "Current Transportation Mode: " + local_transportation;


        NotificationManager mNotificationManager =
                (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);//Context.
        Notification.BigTextStyle bigTextStyle = new Notification.BigTextStyle();
        bigTextStyle.setBigContentTitle("DMS");
        bigTextStyle.bigText(NotificationText);

        Notification note = null;

        note = new Notification.Builder(mContext)
                .setContentTitle(Constants.APP_NAME)
                .setContentText(NotificationText)
                .setStyle(bigTextStyle)
                .setAutoCancel(true)
                .build();

        // using the same tag and Id causes the new notification to replace an existing one
        mNotificationManager.notify(7, note); //String.valueOf(System.currentTimeMillis()),
        note.flags = Notification.FLAG_AUTO_CANCEL;

    }

    public void ServiceDestroying_StoreToCSV_onDestroy(long timestamp, String sFileName){
        Log.d(TAG,"ServiceDestroying_StoreToCSV_onDestroy");

//        String sFileName = "..._.csv";

        try{
            File root = new File(Environment.getExternalStorageDirectory() + Constants.PACKAGE_DIRECTORY_PATH);
            if (!root.exists()) {
                root.mkdirs();
            }

            Log.d(TAG, "root : " + root);

            csv_writer = new CSVWriter(new FileWriter(Environment.getExternalStorageDirectory()+Constants.PACKAGE_DIRECTORY_PATH+sFileName,true));

            List<String[]> data = new ArrayList<String[]>();

            String timeString = getTimeString(timestamp);

            data.add(new String[]{String.valueOf(timestamp), timeString, "Service killed.(onDestroy)"});

            csv_writer.writeAll(data);

            csv_writer.close();

        }catch (IOException e){
            e.printStackTrace();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public void ServiceDestroying_StoreToCSV_onTaskRemoved(long timestamp, String sFileName){
        Log.d(TAG,"ServiceDestroying_StoreToCSV_onTaskRemoved");

//        String sFileName = "..._.csv";

        try{
            File root = new File(Environment.getExternalStorageDirectory() + Constants.PACKAGE_DIRECTORY_PATH);
            if (!root.exists()) {
                root.mkdirs();
            }

            Log.d(TAG, "root : " + root);

            csv_writer = new CSVWriter(new FileWriter(Environment.getExternalStorageDirectory()+Constants.PACKAGE_DIRECTORY_PATH+sFileName,true));

            List<String[]> data = new ArrayList<String[]>();

            String timeString = getTimeString(timestamp);

            data.add(new String[]{String.valueOf(timestamp), timeString, "Service killed.(onTaskRemoved)"});

            csv_writer.writeAll(data);

            csv_writer.close();

        }catch (IOException e){
            e.printStackTrace();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public void ServiceDestroying_StoreToCSV_onLowMemory(long timestamp, String sFileName){
        Log.d(TAG,"ServiceDestroying_StoreToCSV_onLowMemory");

//        String sFileName = "..._.csv";

        try{
            File root = new File(Environment.getExternalStorageDirectory() + Constants.PACKAGE_DIRECTORY_PATH);
            if (!root.exists()) {
                root.mkdirs();
            }

            Log.d(TAG, "root : " + root);

            csv_writer = new CSVWriter(new FileWriter(Environment.getExternalStorageDirectory()+Constants.PACKAGE_DIRECTORY_PATH+sFileName,true));

            List<String[]> data = new ArrayList<String[]>();

            String timeString = getTimeString(timestamp);

            data.add(new String[]{String.valueOf(timestamp), timeString, "Service killed.(onLowMemory)"});

            csv_writer.writeAll(data);

            csv_writer.close();

        }catch (IOException e){
            e.printStackTrace();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public void TransportationMode_StoreToCSV(long timestamp, ActivityRecognitionDataRecord latest_AR, String transportation, int currentstate){
        Log.d(TAG,"TransportationMode_StoreToCSV");

        String sFileName = "TransportationMode.csv";


        //get location record
        float lat=0;
        float lng = 0;
        float accuracy = 0;


        if (MinukuStreamManager.getInstance().getLocationDataRecord()!=null) {
            lat = MinukuStreamManager.getInstance().getLocationDataRecord().getLatitude();
            lng = MinukuStreamManager.getInstance().getLocationDataRecord().getLongitude();
            accuracy = MinukuStreamManager.getInstance().getLocationDataRecord().getAccuracy();
        }


        Boolean TransportationModefirstOrNot = sharedPrefs.getBoolean("TransportationModefirstOrNot", true);

        try{
            File root = new File(Environment.getExternalStorageDirectory() + Constants.PACKAGE_DIRECTORY_PATH);
            if (!root.exists()) {
                root.mkdirs();
            }

            Log.d(TAG, "root : " + root);

            csv_writer = new CSVWriter(new FileWriter(Environment.getExternalStorageDirectory()+Constants.PACKAGE_DIRECTORY_PATH+sFileName,true));

            List<String[]> data = new ArrayList<String[]>();

            String timeString = getTimeString(timestamp);

            String state = "";

            if (mCurrentState == 0)
                state = "STATE_STATIC";
            else if (mCurrentState == 1){
                state = "STATE_SUSPECTING_START";
            }
            else if (mCurrentState == 2){
                state = "STATE_CONFIRMED";
            }
            else if (mCurrentState == 3){
                state = "STATE_SUSPECTING_STOP";
            }

            String rec_AR_String = "";
            String latest_AR_String = "";

            if (latest_AR!=null){
                for (int i=0; i<latest_AR.getProbableActivities().size(); i++){

                    if (i!=0){
                        latest_AR_String+=Constants.ACTIVITY_DELIMITER;
                    }
                    DetectedActivity activity =  latest_AR.getProbableActivities().get(i);
                    latest_AR_String += ActivityRecognitionStreamGenerator.getActivityNameFromType(activity.getType());
                    latest_AR_String += Constants.ACTIVITY_CONFIDENCE_CONNECTOR;
                    latest_AR_String += activity.getConfidence();

                }
            }

            if(TransportationModefirstOrNot) {
                data.add(new String[]{"timestamp", "timeString", "received_AR", "latest_AR", "transportation", "state", "lat", "lng", "accuracy"});
                sharedPrefs.edit().putBoolean("TransportationModefirstOrNot", false).apply();
            }

            //write transportation mode
            data.add(new String[]{String.valueOf(timestamp), timeString, rec_AR_String, latest_AR_String, transportation, state, String.valueOf(lat), String.valueOf(lng), String.valueOf(accuracy)});

            csv_writer.writeAll(data);

            csv_writer.close();

        }catch (Exception e){
            e.printStackTrace();
            android.util.Log.e(TAG, "exception", e);
        }
    }

    public void TransportationState_StoreToCSV(long timestamp, String state, String activitySofar){
        Log.d(TAG,"TransportationState_StoreToCSV");

        String sFileName = "TransportationState.csv"; //Static.csv

        try{
            File root = new File(Environment.getExternalStorageDirectory() + Constants.PACKAGE_DIRECTORY_PATH);
            if (!root.exists()) {
                root.mkdirs();
            }

            Log.d(TAG, "root : " + root);

            csv_writer = new CSVWriter(new FileWriter(Environment.getExternalStorageDirectory()+Constants.PACKAGE_DIRECTORY_PATH+sFileName,true));

            List<String[]> data = new ArrayList<String[]>();

            String timeString = getTimeString(timestamp);

            data.add(new String[]{String.valueOf(timestamp), timeString, state, String.valueOf(activitySofar)});

            csv_writer.writeAll(data);

            csv_writer.close();

        }catch (IOException e){
            e.printStackTrace();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public int examineTransportation(ActivityRecognitionDataRecord activityRecognitionDataRecord){

        //if there's no existing activity type, we need to get activity from the shared preference
        if (mConfirmedActivityType == NO_ACTIVITY_TYPE){
            mConfirmedActivityType = sharedPrefs.getInt("ConfirmedActivityType", NO_ACTIVITY_TYPE);
        }

        if(mCurrentState == STATE_INITIAL){
            mCurrentState = sharedPrefs.getInt("CurrentState", STATE_INITIAL);
        }


        if(activityRecognitionDataRecord.getProbableActivities()==null || activityRecognitionDataRecord.getProbableActivities().isEmpty()){
            return -1;
        }

        List<DetectedActivity> probableActivities = activityRecognitionDataRecord.getProbableActivities();


        Log.d(TAG, "[test replay] examine the incoming record.....for transportation " + activityRecognitionDataRecord.getDetectedtime()  +" : "+ activityRecognitionDataRecord.getProbableActivities().toString());

        long detectionTime = activityRecognitionDataRecord.getCreationTime();

        //if in the static state or initial state, we try to suspect new activity
        if (getCurrentState()==STATE_STATIC || getCurrentState()==STATE_INITIAL) {

            //if the detected activity is vehicle, bike or on foot, then we suspect the activity from now
            if (probableActivities.get(0).getType()== DetectedActivity.ON_BICYCLE ||
                    probableActivities.get(0).getType()== DetectedActivity.IN_VEHICLE ||
                    probableActivities.get(0).getType()== DetectedActivity.ON_FOOT) {

                //set current state to suspect stop
                setCurrentState(STATE_SUSPECTING_START);

                //set suspected Activity type
                setSuspectedStartActivityType(probableActivities.get(0).getType());

                //set suspect time
                setSuspectTime(detectionTime);

                TransportationState_StoreToCSV(new Date().getTime(), "STATE_SUSPECTING_START", getConfirmedActvitiyString());
            }

        }
        else if (getCurrentState()==STATE_SUSPECTING_START) {

            //
            if (probableActivities.get(0).getType() == getSuspectedStopActivityType() &&
                    probableActivities.get(0).getConfidence() >= CANCEL_SUSPECT_Threshold) {

                //back to static, cancel the suspection
                setCurrentState(STATE_CONFIRMED);

                setSuspectedStartActivityType(NO_ACTIVITY_TYPE);

                TransportationState_StoreToCSV(new Date().getTime(), "STATE_CONFIRMED", getConfirmedActvitiyString());

                return getConfirmedActivityType();
            }

            Log.d(TAG,"[test replay] in Suspect start, the suspected AR is " +getActivityNameFromType(getSuspectedStartActivityType()) );
            boolean isTimeToConfirm = checkTimeElapseOfLatestActivityFromSuspectPoint(detectionTime, getSuspectTime(), getWindowLengh(getSuspectedStartActivityType(), getCurrentState()) );

            StoreToCSV(isTimeToConfirm, detectionTime);

            if (isTimeToConfirm) {

                long startTime = detectionTime - getWindowLengh(getSuspectedStartActivityType(), getCurrentState());
                long endTime = detectionTime;
                boolean isNewTransportationModeConfirmed = confirmStartPossibleTransportation(getSuspectedStartActivityType(), getWindowData(startTime, endTime),
                        getWindowLengh(getSuspectedStartActivityType(), getCurrentState()));

                if (isNewTransportationModeConfirmed) {

                    //change the state to Confirmed
                    setCurrentState(STATE_CONFIRMED);
                    //set confirmed activity type
                    setConfirmedActivityType(getSuspectedStartActivityType());
                    //no suspect
                    setSuspectedStartActivityType(NO_ACTIVITY_TYPE);

                    //set the suspect time so that other class can access it.(Trip_startTime is when we think the transportation starts)
                    setSuspectTime(startTime);

                    TransportationState_StoreToCSV(new Date().getTime(), "STATE_CONFIRMED", getConfirmedActvitiyString());

                    return getConfirmedActivityType();
                }
                //if the suspection is wrong, back to the static state
                else {

                    //change the state to Confirmed
                    setCurrentState(STATE_STATIC);
                    //set confirmed activity type
                    setConfirmedActivityType(NO_ACTIVITY_TYPE);

                    setSuspectTime(0);

                    TransportationState_StoreToCSV(new Date().getTime(), "STATE_STATIC", getConfirmedActvitiyString());

                    return getConfirmedActivityType();

                }
            }
        }
        //if in the confirmed state, we suspect whether users exit the activity
        else if (getCurrentState()==STATE_CONFIRMED) {

            Log.d(TAG,"[test replay] in confirm, the confirm AR is " + getActivityNameFromType(getConfirmedActivityType()));
            /** if the detected activity is vehicle, bike or on foot, then we suspect the activity from now **/

            //if the latest activity is not the currently confirmed activity nor tilting nor unkown
            if (probableActivities.get(0).getType() != getConfirmedActivityType() &&
                    probableActivities.get(0).getType() != DetectedActivity.TILTING &&
                    probableActivities.get(0).getType() != DetectedActivity.UNKNOWN) {

                //set current state to suspect stop
                setCurrentState(STATE_SUSPECTING_STOP);
                //set suspected Activity type to the confirmed activity type
                setSuspectedStopActivityType(getConfirmedActivityType());
                //set suspect time
                setSuspectTime(detectionTime);

                TransportationState_StoreToCSV(new Date().getTime(), "STATE_SUSPECTING_STOP", getConfirmedActvitiyString());

            }
        }
        else if (getCurrentState()==STATE_SUSPECTING_STOP) {

            boolean isTimeToConfirm = checkTimeElapseOfLatestActivityFromSuspectPoint(detectionTime, getSuspectTime(),
                    getWindowLengh(getSuspectedStopActivityType(),
                            getCurrentState())
            );

            //if we see the original transportation label with a high confidence level, we cancel the suspect stop
            if (probableActivities.get(0).getType() == getSuspectedStopActivityType() &&
                    probableActivities.get(0).getConfidence() >= CANCEL_SUSPECT_Threshold) {

                //back to static, cancel the suspection
                setCurrentState(STATE_CONFIRMED);

                setSuspectedStartActivityType(NO_ACTIVITY_TYPE);

                TransportationState_StoreToCSV(new Date().getTime(), "STATE_CONFIRMED", getConfirmedActvitiyString());

                return getConfirmedActivityType();
            }


            //the incoming label is not the confirmed transportation mode with a high confidence, so we need to check labels in a window time
            else {


                if (isTimeToConfirm) {
                    long startTime =detectionTime -
                            getWindowLengh(getSuspectedStartActivityType(),
                                    getCurrentState())
                            ;
                    long endTime = detectionTime;
                    boolean isExitingTransportationMode =
                            confirmStopPossibleTransportation(getSuspectedStopActivityType(), getWindowData(startTime, endTime),
                                    getWindowLengh(getSuspectedStartActivityType(), getCurrentState()));


                    if (isExitingTransportationMode) {

                        //back to static
                        setCurrentState(STATE_STATIC);

                        setConfirmedActivityType(NO_ACTIVITY_TYPE);

                        setSuspectedStopActivityType(NO_ACTIVITY_TYPE);

                        //set the suspect time so that other class can access it.(Trip_startTime is when we think the transportation starts)
                        setSuspectTime(startTime);

                        TransportationState_StoreToCSV(new Date().getTime(), "STATE_STATIC", getConfirmedActvitiyString());

                    }
                    //not exiting the confirmed activity
                    else {
                        //back to static, cancel the suspection
                        setCurrentState(STATE_CONFIRMED);

                        setSuspectedStartActivityType(NO_ACTIVITY_TYPE);

                        TransportationState_StoreToCSV(new Date().getTime(), "STATE_CONFIRMED", getConfirmedActvitiyString());

                    }

                    setSuspectTime(0);
                }
            }


            /**
             * switch activity
             */

            //or directly enter suspecting activity: if the current record is other type of transportation mode
            if (probableActivities.get(0).getType() != getSuspectedStopActivityType() &&
                    probableActivities.get(0).getType()!=DetectedActivity.TILTING &&
                    probableActivities.get(0).getType()!=DetectedActivity.STILL &&
                    probableActivities.get(0).getType()!=DetectedActivity.UNKNOWN &&
                    //NA = 9
                    probableActivities.get(0).getType()!=9) {

                //if the new Activity having the confidence over 95, changing to state confirm
                if(probableActivities.get(0).getConfidence() > SWTICH_TO_NEW_ACTIVITY_Threshold){

                    //change the state to Confirmed
                    setCurrentState(STATE_CONFIRMED);
                    //set confirmed activity type
                    setConfirmedActivityType(probableActivities.get(0).getType());
                    //no suspect
                    setSuspectedStartActivityType(NO_ACTIVITY_TYPE);

                    //set the suspect time so that other class can access it.(Trip_startTime is when we think the transportation starts)
                    setSuspectTime(detectionTime);

                    TransportationState_StoreToCSV(new Date().getTime(), "STATE_CONFIRMED", getConfirmedActvitiyString());

                    return getConfirmedActivityType();
                }

                //no label with high confidence, we need to check labels in a window time
                else {

                    isTimeToConfirm = checkTimeElapseOfLatestActivityFromSuspectPoint(
                            detectionTime,
                            getSuspectTime(),
                            getWindowLengh(probableActivities.get(0).getType(),
                                    STATE_SUSPECTING_START) );

                    if (isTimeToConfirm) {
                        long startTime = detectionTime -
                                getWindowLengh(probableActivities.get(0).getType(),
                                        STATE_SUSPECTING_START) ;
                        long endTime = detectionTime;
                        boolean isActuallyStartingAnotherActivity = changeSuspectingTransportation(
                                probableActivities.get(0).getType(),
                                getWindowData(startTime, endTime));

                        if (isActuallyStartingAnotherActivity) {

                            //back to static
                            setCurrentState(STATE_SUSPECTING_START);

                            setSuspectedStopActivityType(NO_ACTIVITY_TYPE);

                            setSuspectedStartActivityType(probableActivities.get(0).getType());

                            //start suspecting new activity
                            setSuspectTime(detectionTime);

                            TransportationState_StoreToCSV(new Date().getTime(), "STATE_SUSPECTING_START", getConfirmedActvitiyString());

                        }
                    }
                }

            }
        }

        return getConfirmedActivityType();

    }

    public void StoreToCSV(boolean isTimeToConfirm, long detectionTime){

        String sFileName = "isTimeToConfirm.csv";

        try{
            File root = new File(Environment.getExternalStorageDirectory() + PACKAGE_DIRECTORY_PATH);
            if (!root.exists()) {
                root.mkdirs();
            }

            CSVWriter csv_writer = new CSVWriter(new FileWriter(Environment.getExternalStorageDirectory()+PACKAGE_DIRECTORY_PATH+sFileName,true));

            List<String[]> data = new ArrayList<String[]>();

            String timeString = getTimeString(new Date().getTime());

            data.add(new String[]{String.valueOf(new Date().getTime()), timeString, String.valueOf(isTimeToConfirm), String.valueOf(detectionTime)});

            csv_writer.writeAll(data);

            csv_writer.close();

        }catch (IOException e){
            e.printStackTrace();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public static int getConfirmedActivityType() {
        return mConfirmedActivityType;
    }

    public void setConfirmedActivityType(int confirmedActivityType) {
        mConfirmedActivityType = confirmedActivityType;
    }

    public static int getSuspectedStartActivityType() {
        return mSuspectedStartActivityType;
    }

    public static long getSuspectTime() {
        return mSuspectTime;
    }

    public static int getCurrentState() {
        return mCurrentState;
    }

    public static int getSuspectedStopActivityType() {
        return mSuspectedStopActivityType;
    }

    public static void setSuspectedStopActivityType(int suspectedStopActivityType) {
        mSuspectedStopActivityType = suspectedStopActivityType;
    }

    public static void setCurrentState(int state) {
        mCurrentState = state;
    }

    public static void setSuspectedStartActivityType(int suspectedStartActivityType) {
        mSuspectedStartActivityType = suspectedStartActivityType;
    }

    public static void setSuspectTime(long suspectTime) {
        mSuspectTime = suspectTime;
    }

    public static String getConfirmedActvitiyString() {
        return getActivityNameFromType(mConfirmedActivityType);
    }

    public static boolean checkTimeElapseOfLatestActivityFromSuspectPoint( long lastestActivityTime, long suspectTime, long windowLenth) {

        boolean flag = (lastestActivityTime - suspectTime > windowLenth); //(lastestActivityTime - suspectTime)*1000

        StoreToCSV(new Date().getTime(), lastestActivityTime, suspectTime, lastestActivityTime - suspectTime ,windowLenth, flag);

        if (flag)
            //wait for long enough
            return true;
        else
            //still need to wait
            return false;
    }

    public static void StoreToCSV(long timestamp, long lastestActivityTime, long suspectTime, long lastestActivityTime_suspectTime,long windowLenth, boolean flag){

        String sFileName = "checkTimeToConfirm.csv";

        try{
            File root = new File(Environment.getExternalStorageDirectory() + PACKAGE_DIRECTORY_PATH);
            if (!root.exists()) {
                root.mkdirs();
            }

            CSVWriter csv_writer = new CSVWriter(new FileWriter(Environment.getExternalStorageDirectory()+PACKAGE_DIRECTORY_PATH+sFileName,true));

            List<String[]> data = new ArrayList<String[]>();

            String timeString = getTimeString(timestamp);

            data.add(new String[]{String.valueOf(timestamp), timeString, String.valueOf(lastestActivityTime), String.valueOf(suspectTime), String.valueOf(lastestActivityTime_suspectTime), String.valueOf(windowLenth), String.valueOf(flag)});

            csv_writer.writeAll(data);

            csv_writer.close();

        }catch (IOException e){
            e.printStackTrace();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public static long getTransitionWindowLength (int activityType, int state) {

        if (state==STATE_SUSPECTING_START) {

            switch (activityType) {
                case DetectedActivity.IN_VEHICLE:
                    return WINDOW_LENGTH_TRANSITION_START_ACTIVITY_IN_VEHICLE;
                case DetectedActivity.ON_FOOT:
                    return WINDOW_LENGTH_TRANSITION_START_ACTIVITY_ON_FOOT;
                case DetectedActivity.ON_BICYCLE:
                    return WINDOW_LENGTH_TRANSITION_START_ACTIVITY_ON_BICYCLE;
                default:
                    return WINDOW_LENGTH_START_ACTIVITY_DEFAULT;

            }
        }
        else if (state==STATE_SUSPECTING_STOP) {

            switch (activityType) {
                case DetectedActivity.IN_VEHICLE:
                    return WINDOW_LENGTH_TRANSITION_STOP_ACTIVITY_IN_VEHICLE;
                case DetectedActivity.ON_FOOT:
                    return WINDOW_LENGTH_TRANSITION_STOP_ACTIVITY_ON_FOOT;
                case DetectedActivity.ON_BICYCLE:
                    return WINDOW_LENGTH_TRANSITION_STOP_ACTIVITY_ON_BICYCLE;
                default:
                    return WINDOW_LENGTH_STOP_ACTIVITY_DEFAULT;

            }

        }else {
            return WINDOW_LENGTH_STOP_ACTIVITY_DEFAULT;
        }

    }

    public static long getWindowLengh (int activityType, int state) {

        if (state==STATE_SUSPECTING_START) {

            switch (activityType) {
                case DetectedActivity.IN_VEHICLE:
                    return WINDOW_LENGTH_START_ACTIVITY_IN_VEHICLE;
                case DetectedActivity.ON_FOOT:
                    return WINDOW_LENGTH_START_ACTIVITY_ON_FOOT;
                case DetectedActivity.ON_BICYCLE:
                    return WINDOW_LENGTH_START_ACTIVITY_ON_BICYCLE;
                default:
                    return WINDOW_LENGTH_START_ACTIVITY_DEFAULT;

            }
        }
        else if (state==STATE_SUSPECTING_STOP) {

            switch (activityType) {
                case DetectedActivity.IN_VEHICLE:
                    return WINDOW_LENGTH_STOP_ACTIVITY_IN_VEHICLE;
                case DetectedActivity.ON_FOOT:
                    return WINDOW_LENGTH_STOP_ACTIVITY_ON_FOOT;
                case DetectedActivity.ON_BICYCLE:
                    return WINDOW_LENGTH_STOP_ACTIVITY_ON_BICYCLE;
                default:
                    return WINDOW_LENGTH_STOP_ACTIVITY_DEFAULT;

            }

        }else {
            return WINDOW_LENGTH_STOP_ACTIVITY_DEFAULT;
        }

    }


    private boolean confirmStartPossibleTransportation(int activityType, ArrayList<ActivityRecognitionDataRecord> windowData, long windowLength) {

        float threshold = getConfirmStartThreshold(activityType);

        /** check if in the window data the number of the possible activity exceeds the threshold**/

        //get number of targeted data
        int count = 0;
        int inRecentCount = 0;

        for (int i=0; i<windowData.size(); i++) {

            List<DetectedActivity> detectedActivities = windowData.get(i).getProbableActivities();

            //in the recent 6 there are more than 3
            if (i >= windowData.size()-5) {

                if (detectedActivities.get(0).getType()==activityType ) {
                    inRecentCount +=1;
                }
            }

//            if (detectedActivities.get(0).getType()==activityType ) {
//                count +=1;
//            }

            for (int activityIndex = 0; activityIndex<detectedActivities.size(); activityIndex++) {

                //if probable activities contain the target activity, we count! (not simply see the most probable one)

                if (detectedActivities.get(activityIndex).getType()==activityType
                    //turned into getting the first two labels
                    //also, we only care about the label which is much confidence to
                    //prevent the low confidence ones would affect the result
//                        && detectedActivities.get(activityIndex).getConfidence() >= CONFIRM_START_ACTIVITY_Needed_Confidence
                        ){
                    count +=1;
                    break;
                }

                //only consider the first two labels
                if(activityIndex >= 1){
                    break;
                }
            }


        }

        if (windowData.size()!=0) {

            float percentage = (float)count/windowData.size();

            StoreToCSV(new Date().getTime(), String.valueOf(percentage), "start", windowData, threshold, windowLength);

            //if the percentage > threshold
            if ( threshold <= percentage || inRecentCount >= 2)
                return true;
            else
                return false;

        }
        else{


            StoreToCSV(new Date().getTime(), "no float", "start",new ArrayList<ActivityRecognitionDataRecord>(), threshold, windowLength);

            //if there's no data in the windowdata, we should not confirm the possible activity
            return false;
        }
    }

    public void StoreToCSV(long timestamp, String percentage, String startstop, ArrayList<ActivityRecognitionDataRecord> windowData, float threshold, long windowLength){

        String sFileName = "windowdata.csv";

        try{
            File root = new File(Environment.getExternalStorageDirectory() + PACKAGE_DIRECTORY_PATH);
            if (!root.exists()) {
                root.mkdirs();
            }

            CSVWriter csv_writer = new CSVWriter(new FileWriter(Environment.getExternalStorageDirectory()+PACKAGE_DIRECTORY_PATH+sFileName,true));

            SharedPreferences sharedPrefs = mContext.getSharedPreferences("edu.umich.minuku_2", mContext.MODE_PRIVATE);
            Boolean startwindowdataOrNot = sharedPrefs.getBoolean("startwindowdataOrNot", true);

            if(startwindowdataOrNot) {
                List<String[]> title = new ArrayList<String[]>();

                title.add(new String[]{"timestamp", "timeString", "percentage", "startstop", "windowData", "threshold", "windowLength"});

                csv_writer.writeAll(title);

                sharedPrefs.edit().putBoolean("startwindowdataOrNot", false).apply();

            }

            List<String[]> data = new ArrayList<String[]>();

            String timeString = getTimeString(timestamp);

            data.add(new String[]{String.valueOf(timestamp), timeString, percentage, startstop,String.valueOf(windowData), String.valueOf(threshold), String.valueOf(windowLength)});

            csv_writer.writeAll(data);

            csv_writer.close();

        }catch (IOException e){
            e.printStackTrace();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private static float getConfirmStartThreshold(int activityType) {

        switch (activityType) {
            case DetectedActivity.IN_VEHICLE:
                return CONFIRM_START_ACTIVITY_THRESHOLD_IN_VEHICLE;
            case DetectedActivity.ON_FOOT:
                return CONFIRM_START_ACTIVITY_THRESHOLD_ON_FOOT;
            case DetectedActivity.ON_BICYCLE:
                return CONFIRM_START_ACTIVITY_THRESHOLD_ON_BICYCLE;
            default:
                return (float) 0.5;
        }
    }

    private ArrayList<ActivityRecognitionDataRecord> getWindowData(long startTime, long endTime) {

        ArrayList<ActivityRecognitionDataRecord> windowData = new ArrayList<ActivityRecognitionDataRecord>();

        //TODO: get activity records from the database
        //windowData = DataHandler.getActivityRecognitionRecordsBetweenTimes(Trip_startTime, Trip_endTime);

        ///for testing: get data from the testData

        ArrayList<ActivityRecognitionDataRecord> recordPool = getLocalRecordPool();

//        Log.d(LOG_TAG, " examineTransportation you find " + recordPool.size() + " records in the activity recognition pool");

        for (int i=0; i<recordPool.size(); i++) {

            ActivityRecognitionDataRecord record = (ActivityRecognitionDataRecord) recordPool.get(i);

            //       Log.d(LOG_TAG, " record.getTimestamp() " + record.getTimestamp() +
            //             " windwo Trip_startTime " + Trip_startTime + " windwo Trip_endTime " + Trip_endTime);


            if (record.getTimestamp() >= startTime && record.getTimestamp() <= endTime)
                windowData.add(record);
        }

        return windowData;
    }

    private boolean confirmStopPossibleTransportation(int activityType, ArrayList<ActivityRecognitionDataRecord> windowData, long windowLength) {

        float threshold = getConfirmStopThreshold(activityType);

        /** check if in the window data the number of the possible activity exceeds the threshold**/

        //get number of targeted data
        int count = 0;
        int inRecentCount = 0;
        for (int i=0; i<windowData.size(); i++) {

            List<DetectedActivity> detectedActivities = windowData.get(i).getProbableActivities();

            //in the recent 6 there are more than 3
            if (i >= windowData.size()-5) {
                if (detectedActivities.get(0).getType()==activityType ) {
                    inRecentCount +=1;
                }
            }

            for (int activityIndex = 0; activityIndex<detectedActivities.size(); activityIndex++) {

                //if probable activities contain the target activity, we count! (not simply see the most probable one)

                if (detectedActivities.get(activityIndex).getType()==activityType
                    //only consider the first two labels
                    //also, we only care about the label which is much confidence to
                    //prevent the low confidence ones would affect the result
                    //&& detectedActivities.get(activityIndex).getConfidence() >= CONFIRM_STOP_ACTIVITY_Needed_Confidence
                        ){
                    count +=1;
                    break;
                }

                //only consider the first two labels
                if(activityIndex >= 1){
                    break;
                }
            }

        }

        float percentage = (float)count/windowData.size();

        if (windowData.size()!=0) {
            //if the percentage > threshold
            StoreToCSV(new Date().getTime(), "stop", String.valueOf(percentage), windowData, threshold, windowLength);

            if ( threshold >= percentage && inRecentCount <= 2)

                return true;
            else
                return false;

        }
        else{
            StoreToCSV(new Date().getTime(), "no float", "stop", new ArrayList<ActivityRecognitionDataRecord>(), threshold, windowLength);

            //if there's no data in the windowdata, we should not confirm the possible activity
            return false;
        }
    }

    private static boolean changeSuspectingTransportation(int activityType, ArrayList<ActivityRecognitionDataRecord> windowData) {

        float threshold = getConfirmStartThreshold(activityType);

        /** check if in the window data the number of the possible activity exceeds the threshold**/

        int inRecentCount = 0;

        for (int i=windowData.size()-1; i>=0; i--) {

            List<DetectedActivity> detectedActivities = windowData.get(i).getProbableActivities();

            //in the recent 6 there are more than 3
            if (i >= windowData.size()-3) {
                if (detectedActivities.get(0).getType()==activityType ) {
                    inRecentCount +=1;
                }
            }


        }

        if (windowData.size()!=0) {

            //if the percentage > threshold
//            Log.d(LOG_TAG, "[changeSuspectingTransportation] examineTransportation changing transportation recentCount " +inRecentCount + " within " + windowData.size()  + "  data");


            if ( inRecentCount >= 2)
                return true;
            else
                return false;

        }
        else
            //if there's no data in the windowdata, we should not confirm the possible activity
            return false;

    }

    public static void addActivityRecognitionRecord(ActivityRecognitionDataRecord record) {
        getActivityRecognitionRecords().add(record);
    }

    public static ArrayList<ActivityRecognitionDataRecord> getActivityRecognitionRecords() {

        if (mActivityRecognitionRecords==null){
            mActivityRecognitionRecords = new ArrayList<ActivityRecognitionDataRecord>();
        }
        return mActivityRecognitionRecords;

    }


    private static float getConfirmStopThreshold(int activityType) {

        switch (activityType) {
            case DetectedActivity.IN_VEHICLE:
                return CONFIRM_STOP_ACTIVITY_THRESHOLD_IN_VEHICLE;
            case DetectedActivity.ON_FOOT:
                return CONFIRM_STOP_ACTIVITY_THRESHOLD_ON_FOOT;
            case DetectedActivity.ON_BICYCLE:
                return CONFIRM_STOP_ACTIVITY_THRESHOLD_ON_BICYCLE;
            default:
                return (float) 0.5;

        }
    }

    /**
     * Map detected activity types to strings
     */
    public static String getActivityNameFromType(int activityType) {
        switch(activityType) {
            case DetectedActivity.IN_VEHICLE:
                return ActivityRecognitionStreamGenerator.STRING_DETECTED_ACTIVITY_IN_VEHICLE;
            case DetectedActivity.ON_BICYCLE:
                return ActivityRecognitionStreamGenerator.STRING_DETECTED_ACTIVITY_ON_BICYCLE;
            case DetectedActivity.ON_FOOT:
                return ActivityRecognitionStreamGenerator.STRING_DETECTED_ACTIVITY_ON_FOOT;
            case DetectedActivity.STILL:
                return ActivityRecognitionStreamGenerator.STRING_DETECTED_ACTIVITY_STILL;
            case DetectedActivity.RUNNING:
                return ActivityRecognitionStreamGenerator.STRING_DETECTED_ACTIVITY_RUNNING;
            case DetectedActivity.WALKING:
                return ActivityRecognitionStreamGenerator.STRING_DETECTED_ACTIVITY_WALKING;
            case DetectedActivity.UNKNOWN:
                return ActivityRecognitionStreamGenerator.STRING_DETECTED_ACTIVITY_UNKNOWN;
            case DetectedActivity.TILTING:
                return ActivityRecognitionStreamGenerator.STRING_DETECTED_ACTIVITY_TILTING;
            case NO_ACTIVITY_TYPE:
                return TRANSPORTATION_MODE_NAME_NO_TRANSPORTATION;
        }
        return TRANSPORTATION_MODE_NAME_NO_TRANSPORTATION;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG,"onBind");

        return null;
    }
}

