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
import edu.nctu.minuku_2.Receiver.SnoozeReceiver;
import org.json.JSONObject;
import org.json.JSONException;
import android.telephony.TelephonyManager;
import android.content.Context;

import java.net.URLEncoder;
import java.util.Date;
import java.util.Calendar;
import java.text.SimpleDateFormat;

import edu.nctu.minuku_2.R;
import edu.nctu.minuku_2.Receiver.WifiReceiver;

import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutionException;
import edu.nctu.minuku.streamgenerator.LocationStreamGenerator;
import com.amplitude.api.Amplitude;
import com.amplitude.api.Identify;
import android.os.Handler;
import java.util.UUID;
import android.provider.Settings;
import android.text.TextUtils;

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
    private String last_title;
    private Boolean skip_form;

    private SharedPreferences sharedPrefs;



    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Notification bind");

        return super.onBind(intent);
    }

    private static final class ApplicationPackageNames {
        public static final String FACEBOOK_MESSENGER_PACK_NAME = "com.facebook.orca";
        public static final String MESSENGER_LITE_PACK_NAME = "com.facebook.mlite";
        public static final String LINE_PACK_NAME = "jp.naver.line.android";
        public static final String LINE2_PACK_NAME = "jp.naver.line.androie";
    }

    public static final class InterceptedNotificationCode {
        public static final int FACEBOOK_MESSENGER_CODE = 1;
        public static final int MESSENGER_LITE_CODE = 4;
        public static final int LINE_CODE = 2;
        public static final int OTHER_NOTIFICATIONS_CODE = 3;
    }



    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Amplitude.getInstance().logEvent("GET_NOTIFICATION");

        long unixTime = System.currentTimeMillis() / 1000L;

        sharedPrefs = getSharedPreferences(getString(R.string.sharedPreference), MODE_PRIVATE);
        SharedPreferences pref = getSharedPreferences("edu.nctu.minuku", MODE_PRIVATE);

        send_form = Boolean.FALSE;
        skip_form = Boolean.FALSE;
        Log.d(TAG, "Notification received: "+sbn.getPackageName()+":"+sbn.getNotification().tickerText);

        Long last_form_done_time = getSharedPreferences("edu.nctu.minuku", MODE_PRIVATE)
                .getLong("last_form_done_time", 0                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        );



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



        for (String key : notification.extras.keySet()) {
            Object value = notification.extras.get(key);
            try {
                Log.d(TAG, String.format("%s %s (%s)", key,
                        value.toString(), value.getClass().getName()));
            } catch (Exception e) {

            }

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

        try {
            SQLiteDatabase db = DBManager.getInstance().openDatabase();
            values.put(DBHelper.TIME, new Date().getTime());
            values.put(DBHelper.title_col, title);
            values.put(DBHelper.n_text_col, text);
            values.put(DBHelper.subText_col, subText);
            values.put(DBHelper.tickerText_col, tickerText);
            values.put(DBHelper.app_col, sbn.getPackageName());
            values.put(DBHelper.sendForm_col, Boolean.FALSE);
            values.put(DBHelper.longitude_col, (float)LocationStreamGenerator.longitude.get());
            values.put(DBHelper.latitude_col, (float)LocationStreamGenerator.latitude.get());
            Log.d(TAG, values.toString());
            Log.d(TAG,"Save Notification_ALL");

            db.insert(DBHelper.notification_table, null, values);
            Amplitude.getInstance().logEvent("Notification_ALL");
        } catch (NullPointerException e) {
            e.printStackTrace();
            Amplitude.getInstance().logEvent("Notification_ALL_FAILED");
        } finally {
            values.clear();
            DBManager.getInstance().closeDatabase();
        }

        if(notificationCode != InterceptedNotificationCode.OTHER_NOTIFICATIONS_CODE){
            PendingIntent pi = PendingIntent.getActivity(this, UUID.randomUUID().hashCode(), new Intent(this, edu.nctu.minuku_2.MainActivity.class), 0);


            if(sbn.getPackageName().equals(ApplicationPackageNames.FACEBOOK_MESSENGER_PACK_NAME) || sbn.getPackageName().equals(ApplicationPackageNames.MESSENGER_LITE_PACK_NAME)){
                app = "fb";
                if(sbn.getPackageName().equals(ApplicationPackageNames.MESSENGER_LITE_PACK_NAME)){
                    app = "fb_lite";
                }
                if(tickerText.contains("對話中有新訊息") || title.contains("聊天大頭貼使用中") || tickerText.contains("傳送") || tickerText.contains("You missed a call from") || tickerText.contains("你錯過了") || tickerText.contains("sent") || tickerText.contains("reacted") || tickerText.contains("貼圖") || tickerText.contains("送出") ||  tickerText.contains("Wi-Fi") || tickerText.isEmpty() || title.isEmpty() || text.isEmpty() || text.contains("：") || text.contains(":")){
                    skip_form = Boolean.TRUE;
                    if(text.startsWith(title) && text.contains(":")){
                        skip_form = Boolean.FALSE;
                    }
                }
            }
            else if(sbn.getPackageName().equals(ApplicationPackageNames.LINE_PACK_NAME)){
                app = "line";
                if(tickerText.contains("You have a new message") || title.contains(" - ") || text.contains("邀請您加入") || title.contains("LINE") || tickerText.contains("LINE") || text.contains("LINE")  || tickerText.contains("貼圖") ||  tickerText.contains("LINE") || tickerText.contains("您有新訊息")  || tickerText.contains("傳送了") || tickerText.contains("記事本") || tickerText.contains("已建立")|| tickerText.contains("added a note") || tickerText.contains("sent") ||  tickerText.contains("語音訊息") ||  tickerText.contains("Wi-Fi") || tickerText.isEmpty() || title.isEmpty() || text.isEmpty() || !subText.isEmpty()){
                    skip_form = Boolean.TRUE;
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

            Calendar c = Calendar.getInstance();
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String formattedDate = df.format(c.getTime());

            Intent resultIntent = new Intent(Intent.ACTION_VIEW);
            try {
                resultIntent.setData(Uri.parse("https://nctucommunication.qualtrics.com/jfe/form/SV_78KPI6cbgtRFHp3?app="+app+"&title="+ URLEncoder.encode(title, "UTF-8") +"&text="+URLEncoder.encode(text, "UTF-8")+"&created_at="+unixTime*1000+"&user="+deviceId+"&time="+URLEncoder.encode(formattedDate, "UTF-8")));
            } catch (java.io.UnsupportedEncodingException e){
                resultIntent.setData(Uri.parse("https://nctucommunication.qualtrics.com/jfe/form/SV_78KPI6cbgtRFHp3?app="+app+"&title="+title+"&text="+text+"&created_at="+unixTime*1000+"&user="+deviceId+"&time="+formattedDate));
            }

            Intent notificationIntent = new Intent(getApplicationContext(),  edu.nctu.minuku_2.ResultActivity.class);
            try{
                notificationIntent.putExtra("URL", "https://nctucommunication.qualtrics.com/jfe/form/SV_78KPI6cbgtRFHp3?app="+app+"&title="+ URLEncoder.encode(title, "UTF-8") +"&text="+URLEncoder.encode(text, "UTF-8")+"&created_at="+unixTime*1000+"&user="+deviceId+"&time="+URLEncoder.encode(formattedDate, "UTF-8"));

            } catch (java.io.UnsupportedEncodingException e){
                notificationIntent.putExtra("URL", "https://nctucommunication.qualtrics.com/jfe/form/SV_78KPI6cbgtRFHp3?app="+app+"&title="+title+"&text="+text+"&created_at="+unixTime*1000+"&user="+deviceId+"&time="+formattedDate);
            }
            notificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TASK);

            PendingIntent formIntent = PendingIntent.getActivity(this, UUID.randomUUID().hashCode(), notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            Intent snoozeIntent = new Intent(this, SnoozeReceiver.class);
            snoozeIntent.setAction("ACTION_SNOOZE");

            PendingIntent btPendingIntent = PendingIntent.getBroadcast(this, UUID.randomUUID().hashCode(), snoozeIntent,0);


            mManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
            mBuilder = new NotificationCompat.Builder(this);



            Long last_form_notification_sent_time = getSharedPreferences("edu.nctu.minuku", MODE_PRIVATE)
                    .getLong("last_form_notification_sent_time", 1);


            last_title = sharedPrefs.getString("last_title", "");
            Log.d(TAG, "START CHECK");
            Log.d(TAG, "title" + title);
            Log.d(TAG, "last_title" + last_title);
            Log.d(TAG, "compare: " + Boolean.toString(!title.equals(last_title)));
            Log.d(TAG, "unixTime" + Long.toString(unixTime));
            Log.d(TAG, "last_form_done_time" + Long.toString(last_form_done_time));
            Log.d(TAG, "DIFF" + Long.toString(unixTime - last_form_done_time));
            Log.d(TAG, "last_form_notification_sent_time" + Long.toString(last_form_notification_sent_time));
            Log.d(TAG, "DIFF" + Long.toString(unixTime - last_form_notification_sent_time));

            JSONObject amplitudeJson = new JSONObject();
            try{
                amplitudeJson.put("app", app);
                amplitudeJson.put("title", title);
                amplitudeJson.put("text", text);
                amplitudeJson.put("created_at", unixTime*1000);
                amplitudeJson.put("user", deviceId);
                amplitudeJson.put("last_title", last_title);
                amplitudeJson.put("last_form_done_time", last_form_done_time);
                amplitudeJson.put("last_form_notification_sent_time", last_form_notification_sent_time);

            } catch (JSONException e){

            }

            isAccessibilityEnabled();

            Amplitude.getInstance().logEvent("READY_TO_SEND_FORM", amplitudeJson);
            if(!skip_form && !title.equals(last_title) && (unixTime - last_form_notification_sent_time > 600) && (unixTime - last_form_done_time > 45*60)) {
                try {
                    SQLiteDatabase db = DBManager.getInstance().openDatabase();
                    values.put(DBHelper.TIME, new Date().getTime());
                    values.put(DBHelper.title_col, title);
                    values.put(DBHelper.n_text_col, text);
                    values.put(DBHelper.subText_col, subText);
                    values.put(DBHelper.tickerText_col, tickerText);
                    values.put(DBHelper.app_col, sbn.getPackageName());
                    values.put(DBHelper.sendForm_col, Boolean.TRUE);
                    values.put(DBHelper.longitude_col, (float)LocationStreamGenerator.longitude.get());
                    values.put(DBHelper.latitude_col, (float)LocationStreamGenerator.latitude.get());
                    Log.d(TAG, values.toString());
                    Log.d(TAG,"Save Notification WITH_FORM");

                    db.insert(DBHelper.notification_table, null, values);
                    Amplitude.getInstance().logEvent("SAVE_NOTIFICATION_WITH_FORM");
                } catch (NullPointerException e) {
                    e.printStackTrace();
                    Amplitude.getInstance().logEvent("SAVE_NOTIFICATION_FAILED");
                } finally {
                    values.clear();
                    DBManager.getInstance().closeDatabase();
                }
                Amplitude.getInstance().logEvent("SUCCESS_SEND_FORM");
                pref.edit()
                        .putLong("last_form_notification_sent_time", unixTime)
                        .apply();
                mBuilder.setSmallIcon(R.drawable.self_reflection)
                        .addAction(android.R.drawable.arrow_down_float, "略過", btPendingIntent)
                        .setTicker("(" + app + ") " + tickerText)
                        .setContentTitle("請填寫問卷")
                        .setContentText("(" + app + ") " + tickerText)
                        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setWhen(System.currentTimeMillis()+5000)
                        .setContentIntent(formIntent)
                        .setAutoCancel(true)
                        .setOngoing(true);
                send_form = Boolean.TRUE;
                mManager.notify(0, mBuilder.build());

                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    Thread.sleep(600*1000);
                                } catch (InterruptedException e) {
                                    Log.d(TAG, "sleep failure");
                                }

                                mManager.cancel(0);
                            }
                        }
                ).start();

                Handler h = new Handler();
                long delayInMilliseconds = 600*1000;
                h.postDelayed(new Runnable() {
                    public void run() {
                        mManager.cancel(0);
                    }
                }, delayInMilliseconds);
            }
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
        else if(packageName.equals(ApplicationPackageNames.LINE2_PACK_NAME)){
            return(InterceptedNotificationCode.LINE_CODE);
        }
        else if(packageName.equals(ApplicationPackageNames.MESSENGER_LITE_PACK_NAME)){
            return(InterceptedNotificationCode.MESSENGER_LITE_CODE);
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
                URL url = new URL("http://who.nctu.me:8000/last_form/?user=" + deviceId);
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

    public boolean isAccessibilityEnabled(){
        int accessibilityEnabled = 0;
        final String service = "edu.nctu.minuku_2/edu.nctu.minuku.service.MobileAccessibilityService";

        boolean accessibilityFound = false;
        try {
            accessibilityEnabled = Settings.Secure.getInt(this.getContentResolver(),android.provider.Settings.Secure.ACCESSIBILITY_ENABLED);
            Log.d(TAG, "ACCESSIBILITY: " + accessibilityEnabled);
        } catch (Exception e) {
            Log.d(TAG, "Error finding setting, default accessibility to not found: " + e.getMessage());
        }

        TextUtils.SimpleStringSplitter mStringColonSplitter = new TextUtils.SimpleStringSplitter(':');

        if (accessibilityEnabled==1){
            Log.d(TAG, "***ACCESSIBILIY IS ENABLED***: ");


            String settingValue = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            Log.d(TAG, "Setting: " + settingValue);
            if (settingValue != null) {
                TextUtils.SimpleStringSplitter splitter = mStringColonSplitter;
                splitter.setString(settingValue);
                while (splitter.hasNext()) {
                    String accessabilityService = splitter.next();
                    Log.d(TAG, "Setting: " + accessabilityService);
                    if (accessabilityService.equalsIgnoreCase(service)){
                        Log.d(TAG, "We've found the correct setting - accessibility is switched on!");
                        mManager.cancel(1);
                        return true;
                    }
                }
            }

            Log.d(TAG, "***END***");
        }
        else{
            Log.d(TAG, "***ACCESSIBILIY IS DISABLED***");
            mBuilder.setSmallIcon(R.drawable.self_reflection)
                    .setTicker("權限有誤，請重新獲取權限")
                    .setContentTitle("權限有誤，請重新獲取權限")
                    .setContentText("進入App，點選「獲取權限」")
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setWhen(System.currentTimeMillis())
                    .setAutoCancel(true)
                    .setOngoing(true);
            mManager.notify(1, mBuilder.build());

        }
        return accessibilityFound;
    }
}
