package edu.nctu.minuku_2.service;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.app.Notification;
import android.content.Intent;
import android.os.IBinder;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.app.NotificationManager;
import android.support.v4.app.NotificationCompat;
import android.view.View;
import android.content.Context;
import android.annotation.TargetApi;
import android.os.Build;
import android.content.SharedPreferences;
import android.os.AsyncTask;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.res.Resources;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.net.Uri;

import edu.nctu.minuku.DBHelper.DBHelper;
import edu.nctu.minuku.manager.DBManager;
import edu.nctu.minuku_2.NotificationReceiver;
import org.json.JSONObject;
import org.json.JSONException;
import android.telephony.TelephonyManager;
import android.content.Context;

import java.util.Date;

import edu.nctu.minuku_2.R;
import edu.nctu.minuku_2.Receiver.WifiReceiver;

import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutionException;

/**
 * Created by kevchentw on 2018/1/20.
 */

public class NotificationListener extends NotificationListenerService {
    private static final String TAG = "NotificationListener";

    private NotificationManager mManager;
    private NotificationCompat.Builder mBuilder;
    private String deviceId;
    private String title;
    private String text;
    private String subText;
    private String tickerText;
    private String app;
    private Boolean send_form;



    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Notification bind");

        return super.onBind(intent);
    }

    private static final class ApplicationPackageNames {
        public static final String FACEBOOK_MESSENGER_PACK_NAME = "com.facebook.orca";
        public static final String LINE_PACK_NAME = "jp.naver.line.android";
    }

    public static final class InterceptedNotificationCode {
        public static final int FACEBOOK_MESSENGER_CODE = 1;
        public static final int LINE_CODE = 2;
        public static final int OTHER_NOTIFICATIONS_CODE = 3;
    }



    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        long unixTime = System.currentTimeMillis() / 1000L;

        SharedPreferences pref = getSharedPreferences("edu.nctu.minuku", MODE_PRIVATE);

        send_form = Boolean.FALSE;
        Log.d(TAG, "Notification received: "+sbn.getPackageName()+":"+sbn.getNotification().tickerText);

        Long last_form_done_time = getSharedPreferences("edu.nctu.minuku", MODE_PRIVATE)
                .getLong("last_form_done_time", 0);

        if(unixTime - last_form_done_time > 60*60){
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
                    new SendHttpRequestTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR).get();
                else
                    new SendHttpRequestTask().execute().get();

            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        }



        Notification notification = sbn.getNotification();

        ContentValues values = new ContentValues();



        try {
            title = notification.extras.get("android.title").toString();
        } catch (Exception e){
            title = "";
        }
        try {
            text = notification.extras.get("android.text").toString();
        } catch (Exception e){
            text = "";
        }

        try {
            subText = notification.extras.get("android.subText").toString();
        } catch (Exception e){
            subText = "";
        }

        try {
            tickerText = notification.tickerText.toString();
        } catch (Exception e){
            tickerText = "";
        }


        TelephonyManager telephonyManager;
        telephonyManager = (TelephonyManager) getSystemService(Context.
                TELEPHONY_SERVICE);

        try {
            deviceId = telephonyManager.getDeviceId();
        } catch (SecurityException e){
            deviceId = "null";
        }

        int notificationCode = matchNotificationCode(sbn);

        if(notificationCode != InterceptedNotificationCode.OTHER_NOTIFICATIONS_CODE){
            PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, edu.nctu.minuku_2.MainActivity.class), 0);


            if(sbn.getPackageName().equals(ApplicationPackageNames.FACEBOOK_MESSENGER_PACK_NAME)){
                app = "fb";
                if(title.contains("聊天大頭貼使用中") || title.isEmpty() || text.isEmpty() || text.contains("：")){
                    return;
                }
            }
            else if(sbn.getPackageName().equals(ApplicationPackageNames.LINE_PACK_NAME)){
                app = "line";
                if(title.contains("聊天大頭貼使用中") || title.isEmpty() || text.isEmpty() || !subText.isEmpty()){
                    return;
                }
            }



            JSONObject manJson = new JSONObject();
            try{
                manJson.put("app", app);
                manJson.put("title", title);
                manJson.put("text", text);
                manJson.put("created_at", unixTime*1000);
                manJson.put("user", deviceId);
            } catch (JSONException e){

            }

            Intent resultIntent = new Intent(Intent.ACTION_VIEW);
            resultIntent.setData(Uri.parse("http://ec2-18-220-229-235.us-east-2.compute.amazonaws.com:8000/notification/?data="+manJson.toString()));
            PendingIntent formIntent = PendingIntent.getActivity(this, 0, resultIntent, 0);

            Intent buttonIntent = new Intent(this, NotificationReceiver.class);
            buttonIntent.putExtra("notificationId", 0);
            PendingIntent btPendingIntent = PendingIntent.getBroadcast(this, 0, buttonIntent,0);


            mManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
            mBuilder = new NotificationCompat.Builder(this);



            Long last_form_notification_sent_time = getSharedPreferences("edu.nctu.minuku", MODE_PRIVATE)
                    .getLong("last_form_notification_sent_time", 1);

            Log.d("TAG", "unixTime" + Long.toString(unixTime));
            Log.d("TAG", "last_form_done_time" + Long.toString(last_form_done_time));
            Log.d("TAG", "DIFF" + Long.toString(unixTime - last_form_done_time));

            if((unixTime - last_form_notification_sent_time > 300) && (unixTime - last_form_done_time > 60*60)) {

                pref.edit()
                        .putLong("last_form_notification_sent_time", unixTime)
                        .apply();
                mBuilder.setSmallIcon(R.drawable.self_reflection)
                        .addAction(android.R.drawable.arrow_up_float, "填寫問卷", formIntent)
                        .addAction(android.R.drawable.arrow_down_float, "略過", btPendingIntent)
                        .setTicker("(" + app + ") " + tickerText)
                        .setContentTitle("請填寫問卷")
                        .setContentText("(" + app + ") " + tickerText)
                        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setWhen(System.currentTimeMillis()+5000)
//                    .setContentIntent(pi)
                        .setAutoCancel(true)
                        .setOngoing(true);
                send_form = Boolean.TRUE;
                mManager.notify(0, mBuilder.build());

                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    Thread.sleep(300*1000);
                                } catch (InterruptedException e) {
                                    Log.d(TAG, "sleep failure");
                                }

                                mManager.cancel(0);
                            }
                        }
                ).start();
            }
        }

        try {
            SQLiteDatabase db = DBManager.getInstance().openDatabase();
            values.put(DBHelper.TIME, new Date().getTime());
            values.put(DBHelper.title_col, title);
            values.put(DBHelper.n_text_col, text);
            values.put(DBHelper.subText_col, subText);
            values.put(DBHelper.tickerText_col, tickerText);
            values.put(DBHelper.app_col, sbn.getPackageName());
            values.put(DBHelper.sendForm_col, send_form);

            Log.d(TAG,"Save Notification");

            db.insert(DBHelper.notification_table, null, values);
        } catch (NullPointerException e) {
            e.printStackTrace();
        } finally {
            values.clear();
            DBManager.getInstance().closeDatabase();
        }

    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {}

    private int matchNotificationCode(StatusBarNotification sbn) {
        String packageName = sbn.getPackageName();

        if(packageName.equals(ApplicationPackageNames.FACEBOOK_MESSENGER_PACK_NAME)){
            return(InterceptedNotificationCode.FACEBOOK_MESSENGER_CODE);
        }
        else if(packageName.equals(ApplicationPackageNames.LINE_PACK_NAME)){
            return(InterceptedNotificationCode.LINE_CODE);
        }
        else{
            return(InterceptedNotificationCode.OTHER_NOTIFICATIONS_CODE);
        }
    }

    private class SendHttpRequestTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
            HttpURLConnection connection = null;
            Log.d(TAG, "SendHttpRequestTask");
            SharedPreferences pref = getSharedPreferences("edu.nctu.minuku", MODE_PRIVATE);
            Long created_at;
            try {
                URL url = new URL("http://ec2-18-220-229-235.us-east-2.compute.amazonaws.com:8000/last_form/?user=" + deviceId);
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
                    Log.d(TAG, "GET RESULT");
                    Log.d(TAG, response.toString());

                    try {
                        JSONObject obj = new JSONObject(response.toString());
                        created_at = obj.getLong("created_at");
                        pref.edit()
                                .putLong("last_form_done_time", created_at)
                                .apply();

                    }catch (JSONException e){

                    }


                    return response.toString();
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (null!= connection) {
                    connection.disconnect();
                }
            }
            return "";
        }

        @Override
        protected void onPostExecute(String result) {

        }
    }
}

