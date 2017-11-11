package edu.nctu.minuku_2.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import edu.nctu.minuku.config.Constants;
import edu.nctu.minuku.streamgenerator.ActivityRecognitionStreamGenerator;
import edu.nctu.minuku.streamgenerator.TransportationModeStreamGenerator;
import edu.nctu.minuku_2.R;

/**
 * Created by Lawrence on 2017/10/9.
 */

public class ExpSampleMethodService extends Service {

    private final String TAG = "ExpSampleMethodService";

    private static final String PACKAGE_DIRECTORY_PATH="/Android/data/edu.nctu.minuku_2/";

    private Context mContext;

    private ScheduledExecutorService mScheduledExecutorService;

    private int esm_notifyID = 4;
    private int check_notifyID = 1;

    private CSVWriter csv_writer = null;

    private final String logfile = "ExpSampleMethodDataRecord";

    private String NotificationText = "";

    public final int REFRESH_FREQUENCY = 10; //1s, 1000ms
    public final int BACKGROUND_RECORDING_INITIAL_DELAY = 0;

    private String lastConfirmedActivityType = "NA";

    public ExpSampleMethodService(){
        super();
        Log.d(TAG, "ExpSampleMethodService is constructed");
        mScheduledExecutorService = Executors.newScheduledThreadPool(REFRESH_FREQUENCY);

        mContext = this;

        createCSV();

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        mScheduledExecutorService.scheduleAtFixedRate(
                ESMRunnable,
                BACKGROUND_RECORDING_INITIAL_DELAY,
                REFRESH_FREQUENCY,
                TimeUnit.SECONDS);

        return START_STICKY;
    }

    Runnable ESMRunnable = new Runnable() {
        @Override
        public void run() {

            try{
                String currentConfirmedActivityType = TransportationModeStreamGenerator.toOtherClassDataRecord.getConfirmedActivityType();
                if(!lastConfirmedActivityType.equals(currentConfirmedActivityType))
                    ESMSurveying();

                StoreToCSV(TransportationModeStreamGenerator.toOtherClassDataRecord.getCreationTime(),
                        lastConfirmedActivityType,
                        currentConfirmedActivityType);

                lastConfirmedActivityType = currentConfirmedActivityType;

            }catch (Exception e){
                e.printStackTrace();
            }

            checkInNoti();
        }
    };

    private void checkInNoti(){
        try {
//            String local_transportation = TransportationModeStreamGenerator.toCheckFamiliarOrNotTransportationModeDataRecord.getConfirmedActivityType();

            String local_transportation = lastConfirmedActivityType;
            NotificationText = "Current Transportation Mode: " + local_transportation
                    + "\r\n" + "ActivityRecognition : " + ActivityRecognitionStreamGenerator.getActivityNameFromType(ActivityRecognitionStreamGenerator.sMostProbableActivity.getType())
            ;

            NotificationManager mNotificationManager =
                    (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);//Context.
            Notification.BigTextStyle bigTextStyle = new Notification.BigTextStyle();
            bigTextStyle.setBigContentTitle("ESM");
            bigTextStyle.bigText(NotificationText);

            Notification note = null;
            note = new Notification.Builder(mContext)
                    .setContentTitle(Constants.APP_NAME)
                    .setContentText(NotificationText)
//                .setContentIntent(pending)
                    .setStyle(bigTextStyle)
                    .setSmallIcon(R.drawable.self_reflection)
                    .setAutoCancel(true)
                    .build();

            mNotificationManager.notify(check_notifyID, note);
            note.flags = Notification.FLAG_AUTO_CANCEL;
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    private void ESMSurveying(){
        String notiText = "ExpSampleMethod";

        Log.d(TAG,"ESMSurveying");

        //TODO make a pendingIntent to record.xml
//        Intent resultIntent = new Intent(CheckFamiliarOrNotService.this, linkListohio.class);
//        PendingIntent pending = PendingIntent.getActivity(CheckFamiliarOrNotService.this, 0, resultIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        NotificationManager mNotificationManager =
                (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);//Context.
        Notification.BigTextStyle bigTextStyle = new Notification.BigTextStyle();
        bigTextStyle.setBigContentTitle("ESM");
        bigTextStyle.bigText(notiText);

        Notification note = new Notification.Builder(mContext)
                .setContentTitle(Constants.APP_NAME)
                .setContentText(notiText)
//                .setContentIntent(pending)
                .setStyle(bigTextStyle)
                .setSmallIcon(R.drawable.self_reflection)
                .setAutoCancel(true)
                .build();

        // using the same tag and Id causes the new notification to replace an existing one
        mNotificationManager.notify(esm_notifyID, note); //String.valueOf(System.currentTimeMillis()),
        note.flags = Notification.FLAG_AUTO_CANCEL;
    }

    public void StoreToCSV(long timestamp, String lastConfirmedActivityType, String currentConfirmedActivityType){

        Log.d(TAG,"StoreToCSV");

        String sFileName = logfile+".csv";

        try{
            File root = new File(Environment.getExternalStorageDirectory() + PACKAGE_DIRECTORY_PATH);
            if (!root.exists()) {
                root.mkdirs();
            }

            Log.d(TAG, "root : " + root);

            csv_writer = new CSVWriter(new FileWriter(Environment.getExternalStorageDirectory()+PACKAGE_DIRECTORY_PATH+sFileName,true));

            List<String[]> data = new ArrayList<String[]>();

//            data.add(new String[]{"timestamp","timeString","Latitude","Longitude","Accuracy"});
            String timeString = getTimeString(timestamp);

            data.add(new String[]{timeString,lastConfirmedActivityType,currentConfirmedActivityType});

            csv_writer.writeAll(data);

            csv_writer.close();

        }catch (IOException e){
            e.printStackTrace();
        }
    }

    public void createCSV(){
        String sFileName = logfile+".csv";

        try{
            File root = new File(Environment.getExternalStorageDirectory() + PACKAGE_DIRECTORY_PATH);
            if (!root.exists()) {
                root.mkdirs();
            }

            csv_writer = new CSVWriter(new FileWriter(Environment.getExternalStorageDirectory()+PACKAGE_DIRECTORY_PATH+sFileName,true));

            List<String[]> data = new ArrayList<String[]>();

            data.add(new String[]{"timestamp","timeString","Latitude","Longitude","Accuracy"});

            csv_writer.writeAll(data);

            csv_writer.close();

        }catch (IOException e){
            e.printStackTrace();
        }
    }

    public String getTimeString(long time){
        SimpleDateFormat sdf_now = new SimpleDateFormat(Constants.DATE_FORMAT_NOW);
        String currentTimeString = sdf_now.format(time);

        return currentTimeString;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Destroying service.");
    }
}
