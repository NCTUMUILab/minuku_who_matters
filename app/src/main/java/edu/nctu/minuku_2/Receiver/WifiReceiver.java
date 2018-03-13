package edu.nctu.minuku_2.Receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.telephony.TelephonyManager;

import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.ContentViewEvent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.ExecutionException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import edu.nctu.minuku.DBHelper.DBHelper;
import edu.nctu.minuku.config.Constants;
import edu.nctu.minuku.dao.AccessibilityDataRecordDAO;
import edu.nctu.minuku.logger.Log;
import edu.nctu.minuku.manager.DBManager;
import edu.nctu.minuku.model.DataRecord.ActivityRecognitionDataRecord;
import edu.nctu.minuku.streamgenerator.ConnectivityStreamGenerator;
import edu.nctu.minuku.manager.MinukuDAOManager;
import edu.nctu.minuku_2.R;

import java.lang.Long;
import com.amplitude.api.Amplitude;
/**
 * Created by Lawrence on 2017/8/16.
 */

public class WifiReceiver extends BroadcastReceiver {

    private final String TAG = "WifiReceiver";
    private Context mcontext;
    private String device_id;
    private Integer UPDATE_FREQUENCY_MIN = 30;

    private Handler mDumpThread, mTripThread;

    private SharedPreferences sharedPrefs;

    private Runnable runnable = null, trip_runnable = null;

    private int year,month,day,hour,min;

    private long latestUpdatedTime = -9999;
    private long nowTime = -9999;
    private long startTime = -9999;
    private long endTime = -9999;

    public static final int HTTP_TIMEOUT = 10000; // millisecond
    public static final int SOCKET_TIMEOUT = 20000; // millisecond

    private boolean noDataFlag1 = false;
    private boolean noDataFlag2 = false;
    private boolean noDataFlag3 = false;
    private boolean noDataFlag4 = false;
    private boolean noDataFlag5 = false;
    private boolean noDataFlag6 = false;
    private boolean noDataFlag7 = false;

    private static final String PACKAGE_DIRECTORY_PATH="/Android/data/edu.nctu.minuku_2/";

    private static final String postDumpUrl = "http://ec2-18-220-229-235.us-east-2.compute.amazonaws.com:8000/upload/";//&action=insert, search
    private static final String postTripUrl = "http://192.168.10.93:5000/request?collection=trip";//&action=insert, search

    public static int mainThreadUpdateFrequencyInSeconds = 1800;
    public static long mainThreadUpdateFrequencyInMilliseconds = mainThreadUpdateFrequencyInSeconds * Constants.MILLISECONDS_PER_SECOND;


    @Override
    public void onReceive(Context context, Intent intent) {
        Amplitude.getInstance().logEvent("WIFI_ONRECEIVED");



        Log.d(TAG, "onReceive");

        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

        //get timzone //prevent the issue when the user start the app in wifi available environment.
        TimeZone tz = TimeZone.getDefault();
        Calendar cal = Calendar.getInstance(tz);
        int mYear = cal.get(Calendar.YEAR);
        int mMonth = cal.get(Calendar.MONTH)+1;
        int mDay = cal.get(Calendar.DAY_OF_MONTH);

        mDay++; //start the task tomorrow.

        sharedPrefs = context.getSharedPreferences("edu.nctu.minuku", context.MODE_PRIVATE);

        year = sharedPrefs.getInt("StartYear", mYear);
        month = sharedPrefs.getInt("StartMonth", mMonth);
        day = sharedPrefs.getInt("StartDay", mDay);

        TelephonyManager telephonyManager;
        telephonyManager = (TelephonyManager) context.getSystemService(Context.
                TELEPHONY_SERVICE);

        try {
            device_id = telephonyManager.getDeviceId();
        } catch (SecurityException e){
            device_id = "null";
        }
        Answers.getInstance().logContentView(new ContentViewEvent().putContentName("wifireceiver receive").putCustomAttribute("device_id", device_id));


        Constants.USER_ID = sharedPrefs.getString("userid","NA");
        Constants.GROUP_NUM = sharedPrefs.getString("groupNum","NA");

        hour = sharedPrefs.getInt("StartHour", 0);
        min = sharedPrefs.getInt("StartMin",0);

//        Log.d(TAG, "year : "+ year+" month : "+ month+" day : "+ day+" hour : "+ hour+" min : "+ min);

        if (activeNetwork != null) {
            // connected to the internet
            if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {
                // connected to wifi
                Log.d(TAG,"INTERNETEVENT: Wifi activeNetwork");
                Amplitude.getInstance().logEvent("WIFI_GET_WIFI_START_THREAD");

                //do the work here.
                MakingJsonDumpDataMainThread();
//                MakingJsonTripDataMainThread();

            } else if (activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE) {
                //we might no need to use this.
                // connected to the mobile provider's data plan
                Log.d(TAG, "INTERNETEVENT: MOBILE activeNetwork" ) ;
                if(runnable!=null){
                    Log.d(TAG, "INTERNETEVENT: KILLTHREAD" ) ;
                    mDumpThread.removeCallbacks(runnable);

                }
            }
        } else {
            // not connected to the internet
            Log.d(TAG, "INTERNETEVENT: no Network" ) ;
            Amplitude.getInstance().logEvent("WIFI_NO_WIFI");

            Answers.getInstance().logContentView(new ContentViewEvent().putContentName("wifireceiver no wifi").putCustomAttribute("device_id", Constants.DEVICE_ID));

            if(runnable!=null) {
                Log.d(TAG, "INTERNETEVENT: KILLTHREAD" ) ;
                mDumpThread.removeCallbacks(runnable);
            }
        }

    }

    public void MakingJsonDumpDataMainThread(){

        Log.d(TAG, "MakingJsonDumpDataMainThread") ;
        Answers.getInstance().logContentView(new ContentViewEvent().putContentName("MakingJsonDumpDataMainThread").putCustomAttribute("device_id", Constants.DEVICE_ID));


        mDumpThread = new Handler();

        runnable = new Runnable() {

            @Override
            public void run() {
                Log.d(TAG, "MakingJsonDumpDataMainThread!!") ;
                if(ConnectivityStreamGenerator.mIsWifiConnected) {
                    Log.d(TAG, "MakingJsonDumpDataMainThread-wifi") ;
                    MakingJsonDumpData();
                }


                mDumpThread.postDelayed(this, mainThreadUpdateFrequencyInMilliseconds);

            }
        };

        mDumpThread.post(runnable);
    }

    public void MakingJsonDumpData(){

        Log.d(TAG, "MakingJsonDumpData");
//        if(!getOldestDataTime()){
//            return;
//        }

        Amplitude.getInstance().logEvent("WIFI_START_DUMP_DATA");
        while(getOldestDataTime()) {

            JSONObject data = new JSONObject();

            try {

                data.put("device_id", device_id);


                data.put("startTime", String.valueOf(startTime));
                data.put("endTime", String.valueOf(endTime));
                data.put("startTimeString", getTimeString(startTime));
                data.put("endTimeString", getTimeString(endTime));
            } catch (JSONException e) {
                e.printStackTrace();
            }

            storeTransporatation(data);
            storeLocation(data);
            storeActivityRecognition(data);
            storeRinger(data);
            storeConnectivity(data);
            storeBattery(data);
            storeAppUsage(data);

            storeTelephony(data);
            storeSensor(data);
            storeAccessibility(data);
            storeNotification(data);

//            Log.d(TAG, "final data : " + data.toString());


            String curr = getDateCurrentTimeZone(new Date().getTime());

            //TODO upload to MongoDB
            /*new HttpAsyncPostJsonTask().execute(postDumpUrl,
                    data.toString(),
                    "Dump",
                    curr);*/
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
                    new HttpAsyncPostJsonTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,
                            postDumpUrl,
                            data.toString(),
                            "Dump",
                            curr).get();
                else
                    new HttpAsyncPostJsonTask().execute(
                            postDumpUrl,
                            data.toString(),
                            "Dump",
                            curr).get();

            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        }

    }

    public void MakingJsonTripDataMainThread(){
        mTripThread = new Handler();

        trip_runnable = new Runnable() {

            @Override
            public void run() {

                JSONObject data = storeTrip();
                String curr =  getDateCurrentTimeZone(new Date().getTime());

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
                        new HttpAsyncPostJsonTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,
                                postTripUrl,
                                data.toString(),
                                "Trip",
                                curr).get();
                    else
                        new HttpAsyncPostJsonTask().execute(
                                postTripUrl,
                                data.toString(),
                                "Trip",
                                curr).get();

                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }

                mTripThread.postDelayed(this, mainThreadUpdateFrequencyInMilliseconds);
            }
        };

        mTripThread.post(trip_runnable);

    }

    //use HTTPAsyncTask to poHttpAsyncPostJsonTaskst data
    private class HttpAsyncPostJsonTask extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... params) {

            String result=null;
            String url = params[0];
            String data = params[1];
            String dataType = params[2];
            String lastSyncTime = params[3];

            postJSON(url, data, dataType, lastSyncTime);

            return result;
        }
        // onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(String result) {
            Log.d(TAG, "get http post result" + result);
        }

    }

    public HostnameVerifier DO_NOT_VERIFY = new HostnameVerifier() {

        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    };

    public String postJSON (String address, String json, String dataType, String lastSyncTime) {

        Log.d(TAG, "[postJSON] testbackend post data to " + address);

        InputStream inputStream = null;
        String result = "";

        try {

            URL url = new URL(address);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            Log.d(TAG, "[postJSON] testbackend connecting to " + address);

            if (url.getProtocol().toLowerCase().equals("https")) {
                Log.d(TAG, "[postJSON] [using https]");
                trustAllHosts();
                HttpsURLConnection https = (HttpsURLConnection) url.openConnection();
                https.setHostnameVerifier(DO_NOT_VERIFY);
                conn = https;
            } else {
                conn = (HttpURLConnection) url.openConnection();
            }


            SSLContext sc;
            sc = SSLContext.getInstance("TLS");
            sc.init(null, null, new java.security.SecureRandom());

            conn.setReadTimeout(HTTP_TIMEOUT);
            conn.setConnectTimeout(SOCKET_TIMEOUT);
            conn.setRequestMethod("POST");
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type","application/json");
            conn.connect();

            OutputStreamWriter wr= new OutputStreamWriter(conn.getOutputStream());
            wr.write(json);
            wr.close();

            Log.d(TAG, "Post:\t" + dataType + "\t" + "for lastSyncTime:" + lastSyncTime);

            int responseCode = conn.getResponseCode();

            if(responseCode >= 400)
                inputStream = conn.getErrorStream();
            else
                inputStream = conn.getInputStream();

            result = convertInputStreamToString(inputStream);

            Log.d(TAG, "[postJSON] the result response code is " + responseCode);
            Log.d(TAG, "[postJSON] the result is " + result);
            Amplitude.getInstance().logEvent("WIFI_START_UPLOAD_SUCCESS");

            try {
                JSONObject obj = new JSONObject(result);
                Long r_startTime = obj.getLong("startTime");
                Long r_endTime = obj.getLong("endTime");

            deleteTransporatation(r_startTime, r_endTime);
            deleteLocation(r_startTime, r_endTime);
            deleteActivityRecognition(r_startTime, r_endTime);
            deleteRinger(r_startTime, r_endTime);
            deleteConnectivity(r_startTime, r_endTime);
            deleteBattery(r_startTime, r_endTime);
            deleteAppUsage(r_startTime, r_endTime);
            deleteTelephony(r_startTime, r_endTime);
            deleteSensor(r_startTime, r_endTime);
            deleteAccessibility(r_startTime, r_endTime);
            deleteNotification(r_startTime, r_endTime);


            }catch (JSONException e){

            }




        }
        catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (ProtocolException e) {
            e.printStackTrace();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return  result;
    }

    /** process result **/
    private String convertInputStreamToString(InputStream inputStream) throws IOException{

        BufferedReader bufferedReader = new BufferedReader( new InputStreamReader(inputStream));
        String line = "";
        String result = "";
        while((line = bufferedReader.readLine()) != null){
//            Log.d(LOG_TAG, "[syncWithRemoteDatabase] " + line);
            result += line;
        }

        inputStream.close();
        return result;

    }

    /***
     * trust all hsot....
     */
    private void trustAllHosts() {

        X509TrustManager easyTrustManager = new X509TrustManager() {

            public void checkClientTrusted(
                    X509Certificate[] chain,
                    String authType) throws CertificateException {
                // Oh, I am easy!
            }

            public void checkServerTrusted(
                    X509Certificate[] chain,
                    String authType) throws CertificateException {
                // Oh, I am easy!
            }

            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }


        };

        // Create a trust manager that does not validate certificate chains
        TrustManager[] trustAllCerts = new TrustManager[] {easyTrustManager};

        // Install the all-trusting trust manager
        try {
            SSLContext sc = SSLContext.getInstance("TLS");

            sc.init(null, trustAllCerts, new java.security.SecureRandom());

            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Boolean getOldestDataTime(){
        Log.d(TAG, "getOldestDataTime");
        Long minDateTime = (long) 0;
        String v;
        Long i;
        SQLiteDatabase db = DBManager.getInstance().openDatabase();
        try {
            Cursor c = db.rawQuery("SELECT "+ DBHelper.TIME +" FROM "+ DBHelper.ringer_table + " LIMIT 1", null);
            c.moveToFirst();
            v = c.getString(0);
            i = Long.parseLong(v);
        } catch (Exception e){
            return Boolean.FALSE;
        }

        if (System.currentTimeMillis() - i > UPDATE_FREQUENCY_MIN * 60 * 1000){
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(i);
//            Log.d(TAG, "before: "+  String.valueOf(calendar.getTimeInMillis()));
//            calendar.set(Calendar.MINUTE, 0);
//            Log.d(TAG, "after: "+  String.valueOf(calendar.getTimeInMillis()));
//
            startTime = calendar.getTimeInMillis();
            endTime = calendar.getTimeInMillis()+(UPDATE_FREQUENCY_MIN * 60 * 1000);
            return Boolean.TRUE;
        }
        else{
//            Log.d(TAG, "getOldestDataTime in one hour");
            return Boolean.FALSE;
        }
    }

    private JSONObject storeTrip(){

        Log.d(TAG, "storeTrip");

        JSONObject tripJson = new JSONObject();

        try {

            SQLiteDatabase db = DBManager.getInstance().openDatabase();
            Cursor tripCursor = db.rawQuery("SELECT * FROM "+DBHelper.annotate_table, null); //cause pos start from 0.
//            Log.d(TAG,"SELECT * FROM "+DBHelper.annotate_table); //+" WHERE "+DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime+"' "

            int rows = tripCursor.getCount();

//            Log.d(TAG, "rows : "+rows);

            if(rows!=0){
                tripCursor.moveToFirst();
                for(int i=0;i<rows;i++) {

                    String _id = tripCursor.getString(0);

                    JSONObject dataJson = new JSONObject();

                    String startTime = tripCursor.getString(1);
                    String endTime = tripCursor.getString(2);
                    String startTimeString = tripCursor.getString(3);
                    String endTimeString = tripCursor.getString(4);
                    String sessionid = tripCursor.getString(5);

                    JSONObject annotation_Json = new JSONObject();

                    String activity = tripCursor.getString(6);
                    String annotation_Goal = tripCursor.getString(7);
                    String annotation_SpecialEvent = tripCursor.getString(8);

//                    Log.d(TAG,"_id : "+_id+" startTime : "+startTime+" endTime : "+endTime+" sessionid : "+sessionid);
//                    Log.d(TAG,"activity : "+activity+" annotation_Goal : "+annotation_Goal+" annotation_SpecialEvent : "+annotation_SpecialEvent);

                    annotation_Json.put("activity", activity);
                    annotation_Json.put("annotation_Goal", annotation_Goal);
                    annotation_Json.put("annotation_SpecialEvent", annotation_SpecialEvent);

                    dataJson.put("startTime", startTime);
                    dataJson.put("endTime", endTime);
                    dataJson.put("startTimeString", startTimeString);
                    dataJson.put("endTimeString", endTimeString);
                    dataJson.put("sessionid", sessionid);
                    dataJson.put("annotation", annotation_Json);

                    tripJson.put(_id, dataJson);

                    tripCursor.moveToNext();
                }

//                Log.d(TAG,"tripJson : "+ tripJson.toString());

            }

        }catch (JSONException e){
            e.printStackTrace();
        }catch(NullPointerException e){
            e.printStackTrace();
        }

        return tripJson;
    }

    private void storeTransporatation(JSONObject data){

        Log.d(TAG, "storeTransporatation");

        try {

            JSONObject transportationAndtimestampsJson = new JSONObject();

            JSONArray transportations = new JSONArray();
            JSONArray timestamps = new JSONArray();

            SQLiteDatabase db = DBManager.getInstance().openDatabase();
            Cursor transCursor = db.rawQuery("SELECT * FROM "+DBHelper.transportationMode_table+" WHERE "+DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime+"' ", null); //cause pos start from 0.
//            Log.d(TAG,"SELECT * FROM "+DBHelper.transportationMode_table+" WHERE "+DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime+"' ");

            int rows = transCursor.getCount();

//            Log.d(TAG, "rows : "+rows);

            if(rows!=0){
                transCursor.moveToFirst();
                for(int i=0;i<rows;i++) {
                    String timestamp = transCursor.getString(1);
                    String transportation = transCursor.getString(2);

                    Log.d(TAG,"transportation : "+transportation+" timestamp : "+timestamp);

                    transportations.put(transportation);
                    timestamps.put(timestamp);

                    transCursor.moveToNext();
                }

                transportationAndtimestampsJson.put("Transportation",transportations);
                transportationAndtimestampsJson.put("timestamps",timestamps);

                data.put("TransportationMode",transportationAndtimestampsJson);

            }else
                noDataFlag1 = true;

        }catch (JSONException e){
            e.printStackTrace();
        }catch(NullPointerException e){
            e.printStackTrace();
        }

        Log.d(TAG,"data : "+ data.toString());

    }

    private void deleteTransporatation(Long startTime, Long endTime) {
        Log.d(TAG, "deleteTransporatation");
        Log.d(TAG, DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime + "'");
        SQLiteDatabase db = DBManager.getInstance().openDatabase();
        db.delete(DBHelper.transportationMode_table, DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime + "'", null);
    }


    private void storeLocation(JSONObject data){

//        Log.d(TAG, "storeLocation");

        try {

            JSONObject locationAndtimestampsJson = new JSONObject();

            JSONArray accuracys = new JSONArray();
            JSONArray longtitudes = new JSONArray();
            JSONArray latitudes = new JSONArray();
            JSONArray timestamps = new JSONArray();
            JSONArray Provider_cols = new JSONArray();

            SQLiteDatabase db = DBManager.getInstance().openDatabase();
            Cursor transCursor = db.rawQuery("SELECT * FROM "+DBHelper.location_table+" WHERE "+DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime+"' ", null); //cause pos start from 0.
//            Log.d(TAG,"SELECT * FROM "+DBHelper.location_table+" WHERE "+DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime+"' ");

            int rows = transCursor.getCount();

            Log.d(TAG, "rows : "+rows);

            if(rows!=0){
                transCursor.moveToFirst();
                for(int i=0;i<rows;i++) {
                    String timestamp = transCursor.getString(1);
                    String latitude = transCursor.getString(2);
                    String longtitude = transCursor.getString(3);
                    String accuracy = transCursor.getString(4);
                    String Provider_col = transCursor.getString(8);


//                    Log.d(TAG,"timestamp : "+timestamp+" latitude : "+latitude+" longtitude : "+longtitude+" accuracy : "+accuracy);

                    accuracys.put(accuracy);
                    longtitudes.put(longtitude);
                    latitudes.put(latitude);
                    timestamps.put(timestamp);

                    transCursor.moveToNext();
                }

                locationAndtimestampsJson.put("Accuracy",accuracys);
                locationAndtimestampsJson.put("Longtitudes",longtitudes);
                locationAndtimestampsJson.put("Latitudes",latitudes);
                locationAndtimestampsJson.put("timestamps",timestamps);
                locationAndtimestampsJson.put("Provider_cols",Provider_cols);

                data.put("Location",locationAndtimestampsJson);

            }else
                noDataFlag2 = true;

        }catch (JSONException e){
            e.printStackTrace();
        }catch(NullPointerException e){
            e.printStackTrace();
        }

        Log.d(TAG,"data : "+ data.toString());

    }

    private void deleteLocation(Long startTime, Long endTime) {
//        Log.d(TAG, "deleteLocation");
//        Log.d(TAG, DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime + "'");
        SQLiteDatabase db = DBManager.getInstance().openDatabase();
        db.delete(DBHelper.location_table, DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime + "'", null);
    }

    private void storeActivityRecognition(JSONObject data){

//        Log.d(TAG, "storeActivityRecognition");

        try {

            JSONObject arAndtimestampsJson = new JSONObject();

            JSONArray mostProbableActivityz = new JSONArray();
            JSONArray probableActivitiesz = new JSONArray();
            JSONArray timestamps = new JSONArray();

            SQLiteDatabase db = DBManager.getInstance().openDatabase();
            Cursor transCursor = db.rawQuery("SELECT * FROM "+DBHelper.activityRecognition_table+" WHERE "+DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime+"' ", null); //cause pos start from 0.
//            Log.d(TAG,"SELECT * FROM "+DBHelper.activityRecognition_table+" WHERE "+DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime+"' ");

            int rows = transCursor.getCount();

//            Log.d(TAG, "rows : "+rows);

            if(rows!=0){
                transCursor.moveToFirst();
                for(int i=0;i<rows;i++) {
                    String timestamp = transCursor.getString(1);
                    String mostProbableActivity = transCursor.getString(2);
                    String probableActivities = transCursor.getString(3);

//                    Log.d(TAG,"timestamp : "+timestamp+" mostProbableActivity : "+mostProbableActivity+" probableActivities : "+probableActivities);

                    mostProbableActivityz.put(mostProbableActivity);
                    probableActivitiesz.put(probableActivities);
                    timestamps.put(timestamp);

                    transCursor.moveToNext();
                }

                arAndtimestampsJson.put("MostProbableActivity",mostProbableActivityz);
                arAndtimestampsJson.put("ProbableActivities",probableActivitiesz);
                arAndtimestampsJson.put("timestamps",timestamps);

                data.put("ActivityRecognition",arAndtimestampsJson);

            }else
                noDataFlag3 = true;

        }catch (JSONException e){
            e.printStackTrace();
        }catch(NullPointerException e){
            e.printStackTrace();
        }

        Log.d(TAG,"data : "+ data.toString());

    }

    private void deleteActivityRecognition(Long startTime, Long endTime) {
//        Log.d(TAG, "deleteActivityRecognition");
//        Log.d(TAG, DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime + "'");
        SQLiteDatabase db = DBManager.getInstance().openDatabase();
        db.delete(DBHelper.activityRecognition_table, DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime + "'", null);
    }

    private void storeRinger(JSONObject data){

        Log.d(TAG, "storeRinger");

        try {

            JSONObject ringerAndtimestampsJson = new JSONObject();

            JSONArray StreamVolumeSystems = new JSONArray();
            JSONArray StreamVolumeVoicecalls = new JSONArray();
            JSONArray StreamVolumeRings = new JSONArray();
            JSONArray StreamVolumeNotifications = new JSONArray();
            JSONArray StreamVolumeMusics = new JSONArray();
            JSONArray AudioModes = new JSONArray();
            JSONArray RingerModes = new JSONArray();
            JSONArray timestamps = new JSONArray();

            SQLiteDatabase db = DBManager.getInstance().openDatabase();
            Cursor transCursor = db.rawQuery("SELECT * FROM "+DBHelper.ringer_table+" WHERE "+DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime+"' ", null); //cause pos start from 0.
//            Log.d(TAG,"SELECT * FROM "+DBHelper.ringer_table+" WHERE "+DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime+"' ");

            int rows = transCursor.getCount();

//            Log.d(TAG, "rows : "+rows);

            if(rows!=0){
                transCursor.moveToFirst();
                for(int i=0;i<rows;i++) {
                    String timestamp = transCursor.getString(1);
                    String RingerMode = transCursor.getString(2);
                    String AudioMode = transCursor.getString(3);
                    String StreamVolumeMusic = transCursor.getString(4);
                    String StreamVolumeNotification = transCursor.getString(5);
                    String StreamVolumeRing = transCursor.getString(6);
                    String StreamVolumeVoicecall = transCursor.getString(7);
                    String StreamVolumeSystem = transCursor.getString(8);

                    Log.d(TAG,"timestamp : "+timestamp+" RingerMode : "+RingerMode+" AudioMode : "+AudioMode+
                            " StreamVolumeMusic : "+StreamVolumeMusic+" StreamVolumeNotification : "+StreamVolumeNotification
                            +" StreamVolumeRing : "+StreamVolumeRing +" StreamVolumeVoicecall : "+StreamVolumeVoicecall
                            +" StreamVolumeSystem : "+StreamVolumeSystem);

                    StreamVolumeSystems.put(StreamVolumeSystem);
                    StreamVolumeVoicecalls.put(StreamVolumeVoicecall);
                    StreamVolumeRings.put(StreamVolumeRing);
                    StreamVolumeNotifications.put(StreamVolumeNotification);
                    StreamVolumeMusics.put(StreamVolumeMusic);
                    AudioModes.put(AudioMode);
                    RingerModes.put(RingerMode);
                    timestamps.put(timestamp);

                    transCursor.moveToNext();
                }

                ringerAndtimestampsJson.put("RingerMode",RingerModes);
                ringerAndtimestampsJson.put("AudioMode",AudioModes);
                ringerAndtimestampsJson.put("StreamVolumeMusic",StreamVolumeMusics);
                ringerAndtimestampsJson.put("StreamVolumeNotification",StreamVolumeNotifications);
                ringerAndtimestampsJson.put("StreamVolumeRing",StreamVolumeRings);
                ringerAndtimestampsJson.put("StreamVolumeVoicecall",StreamVolumeVoicecalls);
                ringerAndtimestampsJson.put("StreamVolumeSystem",StreamVolumeSystems);
                ringerAndtimestampsJson.put("timestamps",timestamps);

                data.put("Ringer",ringerAndtimestampsJson);

            }else
                noDataFlag4 = true;

        }catch (JSONException e){
            e.printStackTrace();
        }catch(NullPointerException e){
            e.printStackTrace();
        }

        Log.d(TAG,"data : "+ data.toString());

    }

    private void deleteRinger(Long startTime, Long endTime) {
//        Log.d(TAG, "deleteRinger");
//        Log.d(TAG, DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime + "'");
        SQLiteDatabase db = DBManager.getInstance().openDatabase();
        db.delete(DBHelper.ringer_table, DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime + "'", null);
    }

    private void storeConnectivity(JSONObject data){

//        Log.d(TAG, "storeConnectivity");

        try {

            JSONObject connectivityAndtimestampsJson = new JSONObject();

            JSONArray IsMobileConnecteds = new JSONArray();
            JSONArray IsWifiConnecteds = new JSONArray();
            JSONArray IsMobileAvailables = new JSONArray();
            JSONArray IsWifiAvailables = new JSONArray();
            JSONArray IsConnecteds = new JSONArray();
            JSONArray IsNetworkAvailables = new JSONArray();
            JSONArray NetworkTypes = new JSONArray();
            JSONArray timestamps = new JSONArray();

            SQLiteDatabase db = DBManager.getInstance().openDatabase();
            Cursor transCursor = db.rawQuery("SELECT * FROM "+DBHelper.connectivity_table+" WHERE "+DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime+"' ", null); //cause pos start from 0.
//            Log.d(TAG,"SELECT * FROM "+DBHelper.connectivity_table+" WHERE "+DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime+"' ");

            int rows = transCursor.getCount();

//            Log.d(TAG, "rows : "+rows);

            if(rows!=0){
                transCursor.moveToFirst();
                for(int i=0;i<rows;i++) {
                    String timestamp = transCursor.getString(1);
                    String NetworkType = transCursor.getString(2);
                    String IsNetworkAvailable = transCursor.getString(3);
                    String IsConnected = transCursor.getString(4);
                    String IsWifiAvailable = transCursor.getString(5);
                    String IsMobileAvailable = transCursor.getString(6);
                    String IsWifiConnected = transCursor.getString(7);
                    String IsMobileConnected = transCursor.getString(8);
//
//                    Log.d(TAG,"timestamp : "+timestamp+" NetworkType : "+NetworkType+" IsNetworkAvailable : "+IsNetworkAvailable
//                            +" IsConnected : "+IsConnected+" IsWifiAvailable : "+IsWifiAvailable
//                            +" IsMobileAvailable : "+IsMobileAvailable +" IsWifiConnected : "+IsWifiConnected
//                            +" IsMobileConnected : "+IsMobileConnected);

                    IsMobileConnecteds.put(IsMobileConnected);
                    IsWifiConnecteds.put(IsWifiConnected);
                    IsMobileAvailables.put(IsMobileAvailable);
                    IsWifiAvailables.put(IsWifiAvailable);
                    IsConnecteds.put(IsConnected);
                    IsNetworkAvailables.put(IsNetworkAvailable);
                    NetworkTypes.put(NetworkType);
                    timestamps.put(timestamp);

                    transCursor.moveToNext();
                }

                connectivityAndtimestampsJson.put("NetworkType",NetworkTypes);
                connectivityAndtimestampsJson.put("IsNetworkAvailable",IsNetworkAvailables);
                connectivityAndtimestampsJson.put("IsConnected",IsConnecteds);
                connectivityAndtimestampsJson.put("IsWifiAvailable",IsWifiAvailables);
                connectivityAndtimestampsJson.put("IsMobileAvailable",IsMobileAvailables);
                connectivityAndtimestampsJson.put("IsWifiConnected",IsWifiConnecteds);
                connectivityAndtimestampsJson.put("IsMobileConnected",IsMobileConnecteds);
                connectivityAndtimestampsJson.put("timestamps",timestamps);

                data.put("Connectivity",connectivityAndtimestampsJson);

            }else
                noDataFlag5 = true;

        }catch (JSONException e){
            e.printStackTrace();
        }catch(NullPointerException e){
            e.printStackTrace();
        }

        Log.d(TAG,"data : "+ data.toString());

    }

    private void deleteConnectivity(Long startTime, Long endTime) {
//        Log.d(TAG, "deleteConnectivity");
//        Log.d(TAG, DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime + "'");
        SQLiteDatabase db = DBManager.getInstance().openDatabase();
        db.delete(DBHelper.connectivity_table, DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime + "'", null);
    }

    private void storeBattery(JSONObject data){

        Log.d(TAG, "storeBattery");

        try {

            JSONObject batteryAndtimestampsJson = new JSONObject();

            JSONArray BatteryLevels = new JSONArray();
            JSONArray BatteryPercentages = new JSONArray();
            JSONArray BatteryChargingStates = new JSONArray();
            JSONArray isChargings = new JSONArray();
            JSONArray timestamps = new JSONArray();

            SQLiteDatabase db = DBManager.getInstance().openDatabase();
            Cursor transCursor = db.rawQuery("SELECT * FROM "+DBHelper.battery_table+" WHERE "+DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime+"' ", null); //cause pos start from 0.
//            Log.d(TAG,"SELECT * FROM "+DBHelper.battery_table+" WHERE "+DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime+"' ");

            int rows = transCursor.getCount();

//            Log.d(TAG, "rows : "+rows);

            if(rows!=0){
                transCursor.moveToFirst();
                for(int i=0;i<rows;i++) {
                    String timestamp = transCursor.getString(1);
                    String BatteryLevel = transCursor.getString(2);
                    String BatteryPercentage = transCursor.getString(3);
                    String BatteryChargingState = transCursor.getString(4);
                    String isCharging = transCursor.getString(5);

//                    Log.d(TAG,"timestamp : "+timestamp+" BatteryLevel : "+BatteryLevel+" BatteryPercentage : "+
//                            BatteryPercentage+" BatteryChargingState : "+BatteryChargingState+" isCharging : "+isCharging);

                    BatteryLevels.put(BatteryLevel);
                    BatteryPercentages.put(BatteryPercentage);
                    BatteryChargingStates.put(BatteryChargingState);
                    isChargings.put(isCharging);
                    timestamps.put(timestamp);

                    transCursor.moveToNext();
                }

                batteryAndtimestampsJson.put("BatteryLevel",BatteryLevels);
                batteryAndtimestampsJson.put("BatteryPercentage",BatteryPercentages);
                batteryAndtimestampsJson.put("BatteryChargingState",BatteryChargingStates);
                batteryAndtimestampsJson.put("isCharging",isChargings);
                batteryAndtimestampsJson.put("timestamps",timestamps);

                data.put("Battery",batteryAndtimestampsJson);

            }else
                noDataFlag6 = true;

        }catch (JSONException e){
            e.printStackTrace();
        }catch(NullPointerException e){
            e.printStackTrace();
        }

//        Log.d(TAG,"data : "+ data.toString());

    }

    private void deleteBattery(Long startTime, Long endTime) {
//        Log.d(TAG, "deleteBattery");
//        Log.d(TAG, DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime + "'");
        SQLiteDatabase db = DBManager.getInstance().openDatabase();
        db.delete(DBHelper.battery_table, DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime + "'", null);
    }

    private void storeAppUsage(JSONObject data){

        Log.d(TAG, "storeAppUsage");

        try {

            JSONObject appUsageAndtimestampsJson = new JSONObject();

            JSONArray ScreenStatusz = new JSONArray();
            JSONArray Latest_Used_Apps = new JSONArray();
            JSONArray Latest_Foreground_Activitys = new JSONArray();
            JSONArray timestamps = new JSONArray();

            SQLiteDatabase db = DBManager.getInstance().openDatabase();
            Cursor transCursor = db.rawQuery("SELECT * FROM "+DBHelper.appUsage_table+" WHERE "+DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime+"' ", null); //cause pos start from 0.
            Log.d(TAG,"SELECT * FROM "+DBHelper.appUsage_table+" WHERE "+DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime+"' ");

            int rows = transCursor.getCount();

//            Log.d(TAG, "rows : "+rows);

            if(rows!=0){
                transCursor.moveToFirst();
                for(int i=0;i<rows;i++) {
                    String timestamp = transCursor.getString(1);
                    String ScreenStatus = transCursor.getString(2);
                    String Latest_Used_App = transCursor.getString(3);
                    String Latest_Foreground_Activity = transCursor.getString(4);

//                    Log.d(TAG,"timestamp : "+timestamp+" ScreenStatus : "+ScreenStatus+" Latest_Used_App : "+Latest_Used_App+" Latest_Foreground_Activity : "+Latest_Foreground_Activity);

                    ScreenStatusz.put(ScreenStatus);
                    Latest_Used_Apps.put(Latest_Used_App);
                    Latest_Foreground_Activitys.put(Latest_Foreground_Activity);
                    timestamps.put(timestamp);

                    transCursor.moveToNext();
                }

                appUsageAndtimestampsJson.put("ScreenStatus",ScreenStatusz);
                appUsageAndtimestampsJson.put("Latest_Used_App",Latest_Used_Apps);
//                appUsageAndtimestampsJson.put("Latest_Foreground_Activity",Latest_Foreground_Activitys);
                appUsageAndtimestampsJson.put("timestamps",timestamps);

                data.put("AppUsage",appUsageAndtimestampsJson);

            }else
                noDataFlag7 = true;

        }catch (JSONException e){
            e.printStackTrace();
        }catch(NullPointerException e){
            e.printStackTrace();
        }

//        Log.d(TAG,"data : "+ data.toString());

    }

    private void deleteAppUsage(Long startTime, Long endTime) {
//        Log.d(TAG, "deleteAppUsage");
//        Log.d(TAG, DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime + "'");
        SQLiteDatabase db = DBManager.getInstance().openDatabase();
        db.delete(DBHelper.appUsage_table, DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime + "'", null);
    }

    private void storeTelephony(JSONObject data){

//        Log.d(TAG, "storeTelephony");

        try {

            JSONObject telephonyAndtimestampsJson = new JSONObject();

            JSONArray NetworkOperatorNames = new JSONArray();
            JSONArray CallStates = new JSONArray();
            JSONArray PhoneSignalTypes = new JSONArray();
            JSONArray GsmSignalStrengths = new JSONArray();
            JSONArray LTESignalStrengths = new JSONArray();
            JSONArray CdmaSignalStrengthLevels = new JSONArray();
            JSONArray timestamps = new JSONArray();

            SQLiteDatabase db = DBManager.getInstance().openDatabase();
            Cursor transCursor = db.rawQuery("SELECT * FROM "+DBHelper.sensor_table+" WHERE "+DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime+"' ", null); //cause pos start from 0.
//            Log.d(TAG,"SELECT * FROM "+DBHelper.sensor_table+" WHERE "+DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime+"' ");

            int rows = transCursor.getCount();

//            Log.d(TAG, "rows : "+rows);

            if(rows!=0){
                transCursor.moveToFirst();
                for(int i=0;i<rows;i++) {
                    String timestamp = transCursor.getString(1);
                    String NetworkOperatorName = transCursor.getString(2);
                    String CallState = transCursor.getString(3);
                    String PhoneSignalType = transCursor.getString(4);
                    String GsmSignalStrength = transCursor.getString(5);
                    String LTESignalStrength = transCursor.getString(6);
                    String CdmaSignalStrengthLevel = transCursor.getString(7);

//                    Log.d(TAG,"timestamp : "+timestamp+" NetworkOperatorName : "+NetworkOperatorName+" CallState : "+CallState+" PhoneSignalType : "+PhoneSignalType+" GsmSignalStrength : "+GsmSignalStrength+" LTESignalStrength : "+LTESignalStrength+" CdmaSignalStrengthLevel : "+CdmaSignalStrengthLevel );


                    NetworkOperatorNames.put(NetworkOperatorName);
                    CallStates.put(CallState);
                    PhoneSignalTypes.put(PhoneSignalType);
                    GsmSignalStrengths.put(GsmSignalStrength);
                    LTESignalStrengths.put(LTESignalStrength);
                    CdmaSignalStrengthLevels.put(CdmaSignalStrengthLevel);
                    timestamps.put(timestamp);

                    transCursor.moveToNext();
                }

                telephonyAndtimestampsJson.put("NetworkOperatorName",NetworkOperatorNames);
                telephonyAndtimestampsJson.put("CallState",CallStates);
                telephonyAndtimestampsJson.put("PhoneSignalType",PhoneSignalTypes);
                telephonyAndtimestampsJson.put("GsmSignalStrength",GsmSignalStrengths);
                telephonyAndtimestampsJson.put("LTESignalStrength",LTESignalStrengths);
                telephonyAndtimestampsJson.put("CdmaSignalStrengthLevel",CdmaSignalStrengthLevels);
                telephonyAndtimestampsJson.put("timestamp",timestamps);

                data.put("telephony",telephonyAndtimestampsJson);

            }else
                noDataFlag7 = true;

        }catch (JSONException e){
            e.printStackTrace();
        }catch(NullPointerException e){
            e.printStackTrace();
        }

//        Log.d(TAG,"data : "+ data.toString());

    }

    private void deleteTelephony(Long startTime, Long endTime) {
//        Log.d(TAG, "deleteAppUsage");
//        Log.d(TAG, DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime + "'");
        SQLiteDatabase db = DBManager.getInstance().openDatabase();
        db.delete(DBHelper.telephony_table, DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime + "'", null);
    }

    private void storeSensor(JSONObject data){

//        Log.d(TAG, "storeSensor");

        try {

            JSONObject sensorAndtimestampsJson = new JSONObject();

            JSONArray ACCELEROMETERs = new JSONArray();
            JSONArray GYROSCOPEs = new JSONArray();
            JSONArray GRAVITYs = new JSONArray();
            JSONArray LINEAR_ACCELERATIONs = new JSONArray();
            JSONArray ROTATION_VECTORs = new JSONArray();
            JSONArray PROXIMITYs = new JSONArray();
            JSONArray MAGNETIC_FIELDs = new JSONArray();
            JSONArray LIGHTs = new JSONArray();
            JSONArray PRESSUREs = new JSONArray();
            JSONArray RELATIVE_HUMIDITYs = new JSONArray();
            JSONArray AMBIENT_TEMPERATUREs = new JSONArray();
            JSONArray timestamps = new JSONArray();

            SQLiteDatabase db = DBManager.getInstance().openDatabase();
            Cursor transCursor = db.rawQuery("SELECT * FROM "+DBHelper.sensor_table+" WHERE "+DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime+"' ", null); //cause pos start from 0.
//            Log.d(TAG,"SELECT * FROM "+DBHelper.sensor_table+" WHERE "+DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime+"' ");

            int rows = transCursor.getCount();

//            Log.d(TAG, "rows : "+rows);

            if(rows!=0){
                transCursor.moveToFirst();
                for(int i=0;i<rows;i++) {
                    String timestamp = transCursor.getString(1);
                    String ACCELEROMETER = transCursor.getString(2);
                    String GYROSCOPE = transCursor.getString(3);
                    String GRAVITY = transCursor.getString(4);
                    String LINEAR_ACCELERATION = transCursor.getString(5);
                    String ROTATION_VECTOR = transCursor.getString(6);
                    String PROXIMITY = transCursor.getString(7);
                    String MAGNETIC_FIELD = transCursor.getString(8);
                    String LIGHT = transCursor.getString(9);
                    String PRESSURE = transCursor.getString(10);
                    String RELATIVE_HUMIDITY = transCursor.getString(11);
                    String AMBIENT_TEMPERATURE = transCursor.getString(12);


//
//                    Log.d(TAG,"timestamp : "+timestamp+" ACCELEROMETER : "+ACCELEROMETER+
//                            " GYROSCOPE : "+GYROSCOPE+" LINEAR_ACCELERATION : "+LINEAR_ACCELERATION+
//                            " ROTATION_VECTOR : " +ROTATION_VECTOR+" PROXIMITY : "+PROXIMITY+" MAGNETIC_FIELD : " +MAGNETIC_FIELD +
//                            " LIGHT : " +LIGHT+" PRESSURE : "+PRESSURE+" RELATIVE_HUMIDITY : " +RELATIVE_HUMIDITY+
//                            " AMBIENT_TEMPERATURE : " +AMBIENT_TEMPERATURE
//                    );


                    ACCELEROMETERs.put(ACCELEROMETER);
                    GYROSCOPEs.put(GYROSCOPE);
                    GRAVITYs.put(GRAVITY);
                    LINEAR_ACCELERATIONs.put(LINEAR_ACCELERATION);
                    ROTATION_VECTORs.put(ROTATION_VECTOR);
                    PROXIMITYs.put(PROXIMITY);
                    MAGNETIC_FIELDs.put(MAGNETIC_FIELD);
                    LIGHTs.put(LIGHT);
                    PRESSUREs.put(PRESSURE);
                    RELATIVE_HUMIDITYs.put(RELATIVE_HUMIDITY);
                    AMBIENT_TEMPERATUREs.put(AMBIENT_TEMPERATURE);
                    timestamps.put(timestamp);

                    transCursor.moveToNext();
                }

                sensorAndtimestampsJson.put("ACCELEROMETER",ACCELEROMETERs);
                sensorAndtimestampsJson.put("GYROSCOPE",GYROSCOPEs);
                sensorAndtimestampsJson.put("LINEAR_ACCELERATION",LINEAR_ACCELERATIONs);
                sensorAndtimestampsJson.put("ROTATION_VECTOR",ROTATION_VECTORs);
                sensorAndtimestampsJson.put("PROXIMITY",PROXIMITYs);
                sensorAndtimestampsJson.put("MAGNETIC_FIELD",MAGNETIC_FIELDs);
                sensorAndtimestampsJson.put("LIGHT",LIGHTs);
                sensorAndtimestampsJson.put("PRESSURE",PRESSUREs);
                sensorAndtimestampsJson.put("RELATIVE_HUMIDITY",RELATIVE_HUMIDITYs);
                sensorAndtimestampsJson.put("AMBIENT_TEMPERATURE",AMBIENT_TEMPERATUREs);
                sensorAndtimestampsJson.put("timestamp",timestamps);

                data.put("sensor",sensorAndtimestampsJson);

            }else
                noDataFlag7 = true;

        }catch (JSONException e){
            e.printStackTrace();
        }catch(NullPointerException e){
            e.printStackTrace();
        }

//        Log.d(TAG,"data : "+ data.toString());

    }

    private void deleteSensor(Long startTime, Long endTime) {
//        Log.d(TAG, "deleteAppUsage");
//        Log.d(TAG, DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime + "'");
        SQLiteDatabase db = DBManager.getInstance().openDatabase();
        db.delete(DBHelper.sensor_table, DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime + "'", null);
    }

    private void storeAccessibility(JSONObject data){

        Log.d(TAG, "storeAppUsage");

        try {

            JSONObject appUsageAndtimestampsJson = new JSONObject();

            JSONArray packs = new JSONArray();
            JSONArray texts = new JSONArray();
            JSONArray types = new JSONArray();
            JSONArray extras = new JSONArray();
            JSONArray timestamps = new JSONArray();

            SQLiteDatabase db = DBManager.getInstance().openDatabase();
            Cursor transCursor = db.rawQuery("SELECT * FROM "+DBHelper.accessibility_table+" WHERE "+DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime+"' ", null); //cause pos start from 0.
//            Log.d(TAG,"SELECT * FROM "+DBHelper.accessibility_table+" WHERE "+DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime+"' ");

            int rows = transCursor.getCount();
            JSONObject a_data = new JSONObject();
            Amplitude.getInstance().logEvent("storeAccessibility", a_data.put("row", rows));

//            Log.d(TAG, "rows : "+rows);

            if(rows!=0){
                transCursor.moveToFirst();
                for(int i=0;i<rows;i++) {
                    String timestamp = transCursor.getString(1);
                    String pack = transCursor.getString(2);
                    String text = transCursor.getString(3);
                    String type = transCursor.getString(4);
                    String extra = transCursor.getString(5);

//                    Log.d(TAG,"timestamp : "+timestamp+" pack : "+pack+" text : "+text+" type : "+type+" extra : "+extra);

                    packs.put(pack);
                    texts.put(text);
                    types.put(type);
                    extras.put(extra);

                    timestamps.put(timestamp);

                    transCursor.moveToNext();
                }

                appUsageAndtimestampsJson.put("pack",packs);
                appUsageAndtimestampsJson.put("text",texts);
                appUsageAndtimestampsJson.put("type",types);
                appUsageAndtimestampsJson.put("extra",extras);
//                appUsageAndtimestampsJson.put("Latest_Foreground_Activity",Latest_Foreground_Activitys);
                appUsageAndtimestampsJson.put("timestamps",timestamps);

                data.put("Accessibility",appUsageAndtimestampsJson);

            }else
                noDataFlag7 = true;

        }catch (JSONException e){
            e.printStackTrace();
        }catch(NullPointerException e){
            e.printStackTrace();
        }

        Log.d(TAG,"data : "+ data.toString());

    }

    private void deleteAccessibility(Long startTime, Long endTime) {
        Log.d(TAG, "deleteAppUsage");
        Log.d(TAG, DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime + "'");
        SQLiteDatabase db = DBManager.getInstance().openDatabase();
        db.delete(DBHelper.appUsage_table, DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime + "'", null);
    }

    private void storeNotification(JSONObject data){

        Log.d(TAG, "storeNotification");

        try {

            JSONObject appUsageAndtimestampsJson = new JSONObject();

            JSONArray title_cols = new JSONArray();
            JSONArray n_text_cols = new JSONArray();
            JSONArray subText_cols = new JSONArray();
            JSONArray tickerText_cols = new JSONArray();
            JSONArray app_cols = new JSONArray();
            JSONArray sendForm_cols = new JSONArray();
            JSONArray longitude_cols = new JSONArray();
            JSONArray latitude_cols = new JSONArray();
            JSONArray timestamps = new JSONArray();

            SQLiteDatabase db = DBManager.getInstance().openDatabase();
            Cursor transCursor = db.rawQuery("SELECT * FROM "+DBHelper.notification_table+" WHERE "+DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime+"' ", null); //cause pos start from 0.
//            Log.d(TAG,"SELECT * FROM "+DBHelper.accessibility_table+" WHERE "+DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime+"' ");

            int rows = transCursor.getCount();

            Log.d(TAG, "rows : "+rows);

            if(rows!=0){
                transCursor.moveToFirst();
                for(int i=0;i<rows;i++) {
                    String timestamp = transCursor.getString(1);
                    String title_col = transCursor.getString(2);
                    String n_text_col = transCursor.getString(3);
                    String subText_col = transCursor.getString(4);
                    String tickerText_col = transCursor.getString(5);
                    String app_col = transCursor.getString(6);
                    String sendForm_col = transCursor.getString(7);
                    String longitude_col = transCursor.getString(8);
                    String latitude_col = transCursor.getString(9);

//                    Log.d(TAG,"timestamp : "+timestamp+" pack : "+pack+" text : "+text+" type : "+type+" extra : "+extra);

                    title_cols.put(title_col);
                    n_text_cols.put(n_text_col);
                    subText_cols.put(subText_col);
                    tickerText_cols.put(tickerText_col);
                    app_cols.put(app_col);
                    sendForm_cols.put(sendForm_col);
                    longitude_cols.put(longitude_col);
                    latitude_cols.put(latitude_col);

                    timestamps.put(timestamp);

                    transCursor.moveToNext();
                }

                appUsageAndtimestampsJson.put("title_cols",title_cols);
                appUsageAndtimestampsJson.put("n_text_cols",n_text_cols);
                appUsageAndtimestampsJson.put("subText_cols",subText_cols);
                appUsageAndtimestampsJson.put("tickerText_cols",tickerText_cols);
                appUsageAndtimestampsJson.put("app_cols",app_cols);
                appUsageAndtimestampsJson.put("sendForm_cols",sendForm_cols);
                appUsageAndtimestampsJson.put("longitude_cols",longitude_cols);
                appUsageAndtimestampsJson.put("latitude_cols",latitude_cols);
//                appUsageAndtimestampsJson.put("Latest_Foreground_Activity",Latest_Foreground_Activitys);
                appUsageAndtimestampsJson.put("timestamps",timestamps);

                data.put("Notification",appUsageAndtimestampsJson);

            }else
                noDataFlag7 = true;

        }catch (JSONException e){
            e.printStackTrace();
        }catch(NullPointerException e){
            e.printStackTrace();
        }

        Log.d(TAG,"data : "+ data.toString());

    }

    private void deleteNotification(Long startTime, Long endTime) {
        Log.d(TAG, "deleteNotification");
        Log.d(TAG, DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime + "'");
        SQLiteDatabase db = DBManager.getInstance().openDatabase();
        db.delete(DBHelper.notification_table, DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime + "'", null);
    }

    private long getSpecialTimeInMillis(String givenDateFormat){
        SimpleDateFormat sdf = new SimpleDateFormat(Constants.DATE_FORMAT_NOW);
        long timeInMilliseconds = 0;
        try {
            Date mDate = sdf.parse(givenDateFormat);
            timeInMilliseconds = mDate.getTime();
            Log.d(TAG,"Date in milli :: " + timeInMilliseconds);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return timeInMilliseconds;
    }

    private long getSpecialTimeInMillis(int year,int month,int date,int hour,int min){
//        TimeZone tz = TimeZone.getDefault(); tz
        Calendar cal = Calendar.getInstance();
//        cal.set(year,month,date,hour,min,0);
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.MONTH, month);
        cal.set(Calendar.DAY_OF_MONTH, date);
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, min);
        cal.set(Calendar.SECOND, 0);

        long t = cal.getTimeInMillis();

        return t;
    }

    private void storeTripToLocalFolder(JSONObject completedJson){
        Log.d(TAG, "storeTripToLocalFolder");

        String sFileName = "Trip_"+getTimeString(startTime)+"_"+getTimeString(endTime)+".json";

        Log.d(TAG, "sFileName : "+ sFileName);

        try {
            File root = new File(Environment.getExternalStorageDirectory() + PACKAGE_DIRECTORY_PATH);
            if (!root.exists()) {
                root.mkdirs();
            }

            Log.d(TAG, "root : " + root);

            FileWriter fileWriter = new FileWriter(root+sFileName, true);
            fileWriter.write(completedJson.toString());
            fileWriter.close();
        } catch(IOException e) {
            e.printStackTrace();
        }

    }

    private void storeToLocalFolder(JSONObject completedJson){
        Log.d(TAG, "storeToLocalFolder");

        String sFileName = "Dump_"+getTimeString(startTime)+"_"+getTimeString(endTime)+".json";

        Log.d(TAG, "sFileName : "+ sFileName);

        try {
            File root = new File(Environment.getExternalStorageDirectory() + PACKAGE_DIRECTORY_PATH);
            if (!root.exists()) {
                root.mkdirs();
            }

            Log.d(TAG, "root : " + root);

            FileWriter fileWriter = new FileWriter(root+sFileName, true);
            fileWriter.write(completedJson.toString());
            fileWriter.close();

        } catch(IOException e) {
            e.printStackTrace();
        }

    }
    //TODO remember the format is different from the normal one.
    public static String getTimeString(long time){

        SimpleDateFormat sdf_now = new SimpleDateFormat(Constants.DATE_FORMAT_for_storing);
        String currentTimeString = sdf_now.format(time);

        return currentTimeString;
    }

    public String makingDataFormat(int year,int month,int date,int hour,int min){
        String dataformat= "";

        dataformat = addZero(year)+"/"+addZero(month)+"/"+addZero(date)+" "+addZero(hour)+":"+addZero(min)+":00";
        Log.d(TAG,"dataformat : " + dataformat);

        return dataformat;
    }

    public String getDateCurrentTimeZone(long timestamp) {
        try{
            Calendar calendar = Calendar.getInstance();
            TimeZone tz = TimeZone.getDefault();
            calendar.setTimeInMillis(timestamp);
            calendar.add(Calendar.MILLISECOND, tz.getOffset(calendar.getTimeInMillis()));
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date currenTimeZone = (Date) calendar.getTime();
            return sdf.format(currenTimeZone);
        }catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    private String getmillisecondToDateWithTime(long timeStamp){

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timeStamp);

        int mYear = calendar.get(Calendar.YEAR);
        int mMonth = calendar.get(Calendar.MONTH)+1;
        int mDay = calendar.get(Calendar.DAY_OF_MONTH);
        int mhour = calendar.get(Calendar.HOUR_OF_DAY);
        int mMin = calendar.get(Calendar.MINUTE);
        int mSec = calendar.get(Calendar.SECOND);

        return addZero(mYear)+"/"+addZero(mMonth)+"/"+addZero(mDay)+" "+addZero(mhour)+":"+addZero(mMin)+":"+addZero(mSec);

    }

    private String addZero(int date){
        if(date<10)
            return String.valueOf("0"+date);
        else
            return String.valueOf(date);
    }

    /**get the current time in milliseconds**/
    private long getCurrentTimeInMillis(){
        //get timzone
        TimeZone tz = TimeZone.getDefault();
        Calendar cal = Calendar.getInstance(tz);
        //get the date of now: the first month is Jan:0
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH) + 1;
        int day = cal.get(Calendar.DAY_OF_MONTH);
        int Hour = cal.get(Calendar.HOUR);
        int Min = cal.get(Calendar.MINUTE);

        long t = getSpecialTimeInMillis(year,month,day,Hour,Min);
        return t;
    }


}