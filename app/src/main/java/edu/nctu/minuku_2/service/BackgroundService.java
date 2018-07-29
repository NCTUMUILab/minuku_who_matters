/*
 * Copyright (c) 2016.
 *
 * DReflect and Minuku Libraries by Shriti Raj (shritir@umich.edu) and Neeraj Kumar(neerajk@uci.edu) is licensed under a Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International License.
 * Based on a work at https://github.com/Shriti-UCI/Minuku-2.
 *
 *
 * You are free to (only if you meet the terms mentioned below) :
 *
 * Share — copy and redistribute the material in any medium or format
 * Adapt — remix, transform, and build upon the material
 *
 * The licensor cannot revoke these freedoms as long as you follow the license terms.
 *
 * Under the following terms:
 *
 * Attribution — You must give appropriate credit, provide a link to the license, and indicate if changes were made. You may do so in any reasonable manner, but not in any way that suggests the licensor endorses you or your use.
 * NonCommercial — You may not use the material for commercial purposes.
 * ShareAlike — If you remix, transform, or build upon the material, you must distribute your contributions under the same license as the original.
 * No additional restrictions — You may not apply legal terms or technological measures that legally restrict others from doing anything the license permits.
 */

package edu.nctu.minuku_2.service;


import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.IBinder;
import android.telephony.TelephonyManager;

import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.ContentViewEvent;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import edu.nctu.minuku.config.Constants;
import edu.nctu.minuku.logger.Log;
import edu.nctu.minuku.manager.MinukuStreamManager;
import edu.nctu.minuku.manager.TripManager;
import edu.nctu.minuku_2.ResultActivity;
import edu.nctu.minuku_2.manager.InstanceManager;
import edu.nctu.minuku_2.Receiver.WifiReceiver;
import com.amplitude.api.Amplitude;
import com.amplitude.api.Identify;
import edu.nctu.minuku_2.BuildConfig;


public class BackgroundService extends Service {

    private static final String TAG = "BackgroundService";

    final static String CONNECTIVITY_ACTION = "android.net.conn.CONNECTIVITY_CHANGE";
    WifiReceiver mWifiReceiver;
    IntentFilter intentFilter;
    String deviceId;

    MinukuStreamManager streamManager;
    NotificationManager mNotificationManager;
    private ScheduledExecutorService mScheduledExecutorService;

    private boolean mRunning;

    public BackgroundService() {
        super();
        streamManager = MinukuStreamManager.getInstance();
//        mNotificationManager = new NotificationManager();
        mScheduledExecutorService = Executors.newScheduledThreadPool(3);

        intentFilter = new IntentFilter();
        intentFilter.addAction(CONNECTIVITY_ACTION);
        mWifiReceiver = new WifiReceiver();

    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
        TelephonyManager telephonyManager;
        telephonyManager = (TelephonyManager) getSystemService(Context.
                TELEPHONY_SERVICE);

        try {
            deviceId = telephonyManager.getDeviceId();
        } catch (SecurityException e){
            deviceId = "null";
        }
//        Amplitude.getInstance().initialize(this, "357d2125a984bc280669e6229646816c").enableForegroundTracking(getApplication());
//        Identify identify = new Identify().set("DEVICE_ID", deviceId);
//        Amplitude.getInstance().identify(identify);
//        Amplitude.getInstance().logEvent("INIT_BACKGROUND_SERVICE");

        //make the WifiReceiver start sending data to the server.
        registerReceiver(mWifiReceiver, intentFilter);
        mRunning = false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        Log.d(TAG, "onStartCommand");
        Answers.getInstance().logContentView(new ContentViewEvent().putContentName("start background service").putCustomAttribute("device_id", Constants.DEVICE_ID));

        if (!mRunning) {

            //we use an alarm to keep the service awake
            AlarmManager alarm = (AlarmManager)getSystemService(ALARM_SERVICE);
            alarm.set(
                    AlarmManager.RTC_WAKEUP,     //
                    System.currentTimeMillis() + Constants.PROMPT_SERVICE_REPEAT_MILLISECONDS,
                    PendingIntent.getService(this, 0, new Intent(this, BackgroundService.class), 0)
            );

            Log.d(TAG, "test AR service start test combine the service is restarted! the service is not running");

            mRunning = true;
            // do something

            runMainThread();

            if(!InstanceManager.isInitialized()) {
                InstanceManager.getInstance(this);
//                SessionManager.getInstance(this);
                TripManager.getInstance(this);
            }

            /**read test file**/
//            FileHelper fileHelper = FileHelper.getInstance(getApplicationContext());
//            FileHelper.readTestFile();

        }
        else {
            Log.d(TAG, "test AR service start test combine the service is restarted! the service is running");

        }

        //TODO Keep eye on the service is working or not.
        return START_STICKY;
    }

    private void runMainThread(){
        Log.d(TAG, "runMainThread updateStreamManagerRunnable");
//        Amplitude.getInstance().logEvent("CREATE_SCHEDULED_updateStreamManagerRunnable");
        mScheduledExecutorService.scheduleAtFixedRate(
                updateStreamManagerRunnable,
                0,
                5,
                TimeUnit.SECONDS);
        mScheduledExecutorService.scheduleAtFixedRate(
                updateStateRunnable,
                0,
                60,
                TimeUnit.SECONDS);
    }

    Runnable updateStateRunnable = new Runnable() {
        @Override
        public void run() {

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
                    new BackgroundService.SendHttpRequestTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR).get();
                else
                    new BackgroundService.SendHttpRequestTask().execute().get();

            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }

            // state_accessibility state_main state_esm_done state_esm_create state_bootcomplete  state_notification_listen state_notification_sent_esm state_wifi_upload

        }
    };

    Runnable updateStreamManagerRunnable = new Runnable() {
        @Override
        public void run() {
            Answers.getInstance().logContentView(new ContentViewEvent().putContentName("updateStreamGenerators").putCustomAttribute("device_id", Constants.DEVICE_ID));
            Log.d(TAG, "updateStreamGenerators");
            streamManager.updateStreamGenerators();
//            Amplitude.getInstance().logEvent("UPLOAD_updateStreamManagerRunnable");
            SharedPreferences pref = getSharedPreferences("edu.nctu.minuku", MODE_PRIVATE);
            pref.edit()
                    .putLong("state_stream", System.currentTimeMillis() / 1000L)
                    .apply();
        }
    };



    @Override
    public void onDestroy() {
        Log.d(TAG, "Destroying service. Your state might be lost!");
//        Amplitude.getInstance().logEvent("DESTROY_BACKGROUND_SERVICE");

        unregisterReceiver(mWifiReceiver);
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }


    private class SendHttpRequestTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
            HttpURLConnection connection = null;
            android.util.Log.d(TAG, "SendHttpRequestTask");
            SharedPreferences pref = getSharedPreferences("edu.nctu.minuku", MODE_PRIVATE);
            Long created_at;
            // state_accessibility state_main state_esm_done state_esm_create state_bootcomplete  state_notification_listen state_notification_sent_esm state_wifi_upload
            long state_accessibility = pref.getLong("state_accessibility", 0);
            long state_main = pref.getLong("state_main", 0);
            long state_esm_done = pref.getLong("state_esm_done", 0);
            long state_esm_create = pref.getLong("state_esm_create", 0);
            long state_bootcomplete = pref.getLong("state_bootcomplete", 0);
            long state_notification_listen = pref.getLong("state_notification_listen", 0);
            long state_notification_sent_esm = pref.getLong("state_notification_sent_esm", 0);
            long state_wifi_upload = pref.getLong("state_wifi_upload", 0);
            long state_stream = pref.getLong("state_stream", 0);
            long state_gps = pref.getLong("state_gps", 0);
            String state_version =  BuildConfig.VERSION_NAME;


            try {
                URL url = new URL("http://who.nctu.me:8000/setState/?deviceId=" + deviceId + "&state_version=" + state_version + "&state_gps=" + state_gps +  "&state_accessibility="+state_accessibility+"&state_main="+state_main+"&state_esm_done="+state_esm_done+"&state_esm_create="+state_esm_create+"&state_bootcomplete="+state_bootcomplete+"&state_notification_listen="+state_notification_listen+"&state_notification_sent_esm="+state_notification_sent_esm+"&state_wifi_upload="+state_wifi_upload+"&state_stream="+state_stream);

                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");//设置访问方式为“GET”
                connection.setConnectTimeout(8000);//设置连接服务器超时时间为8秒
                connection.setReadTimeout(8000);//设置读取服务器数据超时时间为8秒

                if (HttpURLConnection.HTTP_OK == connection.getResponseCode()) {
                    //从服务器获取响应并把响应数据转为字符串打印
                    InputStream in = connection.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(in));

                    StringBuilder response = new StringBuilder();
                    String line;
                    while (null != (line = reader.readLine())) {
                        response.append(line);
                    }
                    android.util.Log.d(TAG, "UPLOAD STATE");
                    android.util.Log.d(TAG, response.toString());

                    return response.toString();
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (null != connection) {
                    connection.disconnect();
                }
            }
            return "";
        }
    }
}


