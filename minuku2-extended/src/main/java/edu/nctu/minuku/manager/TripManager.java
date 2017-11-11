package edu.nctu.minuku.manager;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

import edu.nctu.minuku.DBHelper.DBHelper;
import edu.nctu.minuku.config.Constants;
import edu.nctu.minuku.model.DataRecord.LocationDataRecord;

/**
 * Created by Lawrence on 2017/8/11.
 */

public class TripManager {

    private final static String TAG = "TripManager";

    private static int sessionid_Static;

    private static int sessionid_unStatic;
    private static int trip_size;

    private String sessionid;
    private String transportation;
    private String lasttime_transportation;

    private ArrayList<LocationDataRecord> LocationToTrip;

    private Context context;

    private static TripManager instance;

    private SharedPreferences sharedPrefs;
    private static SharedPreferences.Editor editor;

//    private int testing_count;

    public TripManager() {

        sessionid = "0";
        sessionid_unStatic = 0;
        sessionid_Static = 96;
        transportation = "NA";
        lasttime_transportation = "NA";

//        testing_count = 0;
    }

    public TripManager(Context context) {

        this.context = context;

        sharedPrefs = context.getSharedPreferences("edu.umich.minuku_2",Context.MODE_PRIVATE);
        editor = context.getSharedPreferences("edu.umich.minuku_2", Context.MODE_PRIVATE).edit();

        sessionid = "0";
//        sessionid_unStatic = 0;

        sessionid_Static = sharedPrefs.getInt("sessionid_Static", 97);
        sessionid_unStatic = sharedPrefs.getInt("sessionid_unStatic",0);
        trip_size = sharedPrefs.getInt("trip_size",0);

        transportation = "NA";
//        lasttime_transportation = "NA";

        lasttime_transportation = sharedPrefs.getString("","NA");

//        testing_count = 0;
    }

    public static TripManager getInstance() {
        if(TripManager.instance == null) {
            try {
//                TripManager.instance = new TripManager();
                Log.d(TAG,"getInstance without context.");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return TripManager.instance;
    }

    public static TripManager getInstance(Context context) {
        if(TripManager.instance == null) {
            try {
                TripManager.instance = new TripManager(context);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return TripManager.instance;
    }

    public void setTrip(LocationDataRecord entity) {

        Log.d(TAG,"lasttime_transportation "+lasttime_transportation);
        Log.d(TAG,"sessionid_unStatic "+sessionid_unStatic);
        Log.d(TAG,"sessionid_Static" + sessionid_Static);
        //setting sessionid
        try {

            if (MinukuStreamManager.getInstance().getTransportationModeDataRecord() != null) {
                transportation = MinukuStreamManager.getInstance().getTransportationModeDataRecord().getConfirmedActivityType();

                Log.d(TAG, "transportation : " + transportation);

            }
        } catch (Exception e) {
            Log.e(TAG, "No TransportationMode, yet.");
            e.printStackTrace();
        }

        /*//for testing
        if(testing_count%5 != 0)
            transportation = "on_foot";
        testing_count++;*/

        if (!transportation.equals("NA")) {

            if (!transportation.equals("static")) {

                if (!transportation.equals(lasttime_transportation)) {
                    sessionid_unStatic++;
                }
                sessionid = sessionid + ", " + String.valueOf(sessionid_unStatic); // sessionid: "0, 1"

            }/*else{

                if (!transportation.equals(lasttime_transportation)) {
                    sessionid_Static ++;
                }

                sessionid = sessionid + ", " + String.valueOf((char)sessionid_Static); // sessionid: "0, 1"

                Log.d(TAG,"sessionid" + sessionid);

            }*/

            LocationDataRecord record = new LocationDataRecord(
                    sessionid,
                    entity.getLatitude(),
                    entity.getLongitude(),
                    entity.getAccuracy());

            Log.d(TAG, String.valueOf(record.getID()) + "," +
                    record.getCreationTime() + "," +
                    record.getSessionid() + "," +
                    record.getLatitude() + "," +
                    record.getLongitude() + "," +
                    record.getAccuracy());

//            LocationToTrip.add(record);

            // store to DB
            ContentValues values = new ContentValues();

            try {
                SQLiteDatabase db = DBManager.getInstance().openDatabase();

                values.put(DBHelper.TIME, record.getCreationTime());
                values.put(DBHelper.sessionid_col, record.getSessionid());
                values.put(DBHelper.latitude_col, record.getLatitude());
                values.put(DBHelper.longitude_col, record.getLongitude());
                values.put(DBHelper.Accuracy_col, record.getAccuracy());


                db.insert(DBHelper.trip_table, null, values);
            } catch (NullPointerException e) {
                e.printStackTrace();
            } finally {
                values.clear();
                DBManager.getInstance().closeDatabase(); // Closing database connection
            }

            sessionid = "0";

            lasttime_transportation = transportation;

            editor.putString("lasttime_transportation",lasttime_transportation);
            editor.putInt("sessionid_unStatic",sessionid_unStatic);
            editor.putInt("sessionid_Static",sessionid_Static);

            editor.commit();


        }
    }

    public int getSessionidForTripSize(){
        return sessionid_unStatic;
    }

    public static ArrayList<String> getTripDatafromSQLite() {
        ArrayList<String> times = new ArrayList<String>();

        //setting today date.
        Calendar cal = Calendar.getInstance();
        Date date = new Date();
        cal.setTime(date);
        int Year = cal.get(Calendar.YEAR);
        int Month = cal.get(Calendar.MONTH)+1;
        int Day = cal.get(Calendar.DAY_OF_MONTH);

        long startTime = -999;
        long endTime = -999;

        startTime = getSpecialTimeInMillis(makingDataFormat(Year, Month, Day));
        endTime = getSpecialTimeInMillis(makingDataFormat(Year, Month, Day+1));

//        for(int i=1;i<=sessionid_unStatic;i++){
        for(int i=sessionid_unStatic;i>=1;i--){
            try {
                SQLiteDatabase db = DBManager.getInstance().openDatabase();
                Cursor tripCursor = db.rawQuery("SELECT * FROM " + DBHelper.trip_table + " WHERE "+ DBHelper.sessionid_col+ " ='0, "+ i + "'"
                        +" AND "+DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime+"' ", null);
                Log.d(TAG,"SELECT * FROM " + DBHelper.trip_table + " WHERE "+ DBHelper.sessionid_col+ " ='0, "+ i + "'"
                        +" AND "+DBHelper.TIME+" BETWEEN"+" '"+startTime+"' "+"AND"+" '"+endTime+"' ");
                int rows = tripCursor.getCount();

                if(rows!=0){
                    /*
                    //TODO filter <100m trip
                    float dist = 0;
                    float[] results = new float[1];
                    tripCursor.moveToFirst();
                    float lat = tripCursor.getFloat(3);
                    float lng = tripCursor.getFloat(4);
                    tripCursor.moveToNext();
                    do{
                        float lat2 = tripCursor.getFloat(3);
                        float lng2 = tripCursor.getFloat(4);
                        Location.distanceBetween(lat,lng,lat2,lng2,results);
                        dist += results[0];
                        lat = lat2;
                        lng = lng2;
                    }while (tripCursor.moveToNext());

                    Log.d(TAG, "dist : "+ dist);
                    if(dist<100)
                        continue;*/

                    tripCursor.moveToFirst();
                    String firstTime = tripCursor.getString(1);
//                    String firstTime = getmillisecondToDateWithTime(Long.valueOf(tripCursor.getString(1))); //tripCursor.getString(1);
                    tripCursor.moveToLast();
                    String lastTime = tripCursor.getString(1);
//                    String lastTime = getmillisecondToDateWithTime(Long.valueOf(tripCursor.getString(1))); //tripCursor.getString(1);

                    //turn into the lookable format.
                    String firstTimeString = getmillisecondToDateWithTime(Long.valueOf(firstTime));
                    String lastTimeString = getmillisecondToDateWithTime(Long.valueOf(lastTime));

                    Log.d(TAG, firstTimeString+"-"+lastTimeString);

                    times.add(firstTimeString+"-"+lastTimeString);

                }else
                    Log.d(TAG, "rows==0");


            }catch (Exception e){
                e.printStackTrace();
            }

        }

        trip_size = times.size();
        editor.putInt("trip_size",trip_size);

        return times;
    }

    public int getTrip_size(){
        return trip_size;
    }

    public ArrayList<LatLng> getTripLocToDrawOnMap(int position) {
        ArrayList<LatLng> latLngs = new ArrayList<LatLng>();
        try {
            SQLiteDatabase db = DBManager.getInstance().openDatabase();
            Cursor tripCursor = db.rawQuery("SELECT * FROM " + DBHelper.trip_table + " WHERE "+ DBHelper.sessionid_col+ " ='0, "+ (position) + "'", null); //cause pos start from 0.
            Log.d(TAG,"SELECT * FROM " + DBHelper.trip_table + " WHERE "+ DBHelper.sessionid_col+ " ='0, "+ (position) + "'");
            int rows = tripCursor.getCount();

            if(rows!=0){
                tripCursor.moveToFirst();
                for(int i=0;i<rows;i++) {
                    Float lat = tripCursor.getFloat(3);
                    Float lng = tripCursor.getFloat(4);

                    Log.d(TAG,"lat"+String.valueOf(lat)+", lng"+String.valueOf(lng));

                    LatLng latLng = new LatLng(lat,lng);

                    latLngs.add(latLng);

                    tripCursor.moveToNext();
                }
            }

        }catch (Exception e){
            e.printStackTrace();
        }

        return latLngs;

    }

    private static String addZero(int date){
        if(date<10)
            return String.valueOf("0"+date);
        else
            return String.valueOf(date);
    }

    public static long getSpecialTimeInMillis(String givenDateFormat){
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

    public static String makingDataFormat(int year,int month,int date){
        String dataformat= "";

//        dataformat = addZero(year)+"-"+addZero(month)+"-"+addZero(date)+" "+addZero(hour)+":"+addZero(min)+":00";
        dataformat = addZero(year)+"/"+addZero(month)+"/"+addZero(date)+" "+"00:00:00";
        edu.nctu.minuku.logger.Log.d(TAG,"dataformat : " + dataformat);

        return dataformat;
    }

    public static String getmillisecondToDateWithTime(long timeStamp){

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timeStamp);

        int mYear = calendar.get(Calendar.YEAR);
        int mMonth = calendar.get(Calendar.MONTH)+1;
        int mDay = calendar.get(Calendar.DAY_OF_MONTH);
        int mhour = calendar.get(Calendar.HOUR_OF_DAY);
        int mMin = calendar.get(Calendar.MINUTE);
        int mSec = calendar.get(Calendar.SECOND);
        int ampm = calendar.get(Calendar.AM_PM);

//        return addZero(mhour)+":"+addZero(mMin)+" "+ampm;
        return addZero(mYear)+"/"+addZero(mMonth)+"/"+addZero(mDay)+" "+addZero(mhour)+":"+addZero(mMin)+":"+addZero(mSec);

    }
}