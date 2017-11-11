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

package edu.nctu.minuku.dao;


import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.google.android.gms.maps.model.LatLng;
import com.google.common.util.concurrent.SettableFuture;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Future;

import edu.nctu.minuku.DBHelper.DBHelper;
import edu.nctu.minuku.config.Constants;
import edu.nctu.minuku.config.UserPreferences;
import edu.nctu.minuku.logger.Log;
import edu.nctu.minuku.manager.DBManager;
import edu.nctu.minuku.model.DataRecord.LocationDataRecord;
import edu.nctu.minukucore.dao.DAO;
import edu.nctu.minukucore.dao.DAOException;
import edu.nctu.minukucore.user.User;

/**
 * Created by shriti on 7/15/16.
 * Author: Neeraj Kumar
 */
public class LocationDataRecordDAO implements DAO<LocationDataRecord> {

    private String TAG = "LocationDataRecordDAO";
    private String myUserEmail;
    private DBHelper dBHelper;
    private UUID uuID;

    private long recordCount;
    private long tripCount;
    private long tripLocCount;
    private ArrayList<LocationDataRecord> LocationToTrip;
    private int KEEPALIVE_MINUTE = 5;
    private long sKeepalive;
//    private TripManager tripManager;

    public LocationDataRecordDAO() {
        recordCount = 0;
        tripCount = 0;
        tripLocCount = 0;
        sKeepalive = KEEPALIVE_MINUTE * Constants.MILLISECONDS_PER_MINUTE;
//        tripManager = new TripManager();
        LocationToTrip = new ArrayList<LocationDataRecord>();
        myUserEmail = UserPreferences.getInstance().getPreference(Constants.KEY_ENCODED_EMAIL);
    }

    public LocationDataRecordDAO(Context applicationContext){
        recordCount = 0;
        tripCount = 0;
        tripLocCount = 0;
        sKeepalive = KEEPALIVE_MINUTE * Constants.MILLISECONDS_PER_MINUTE;
//        tripManager = new TripManager();
        LocationToTrip = new ArrayList<LocationDataRecord>();
        dBHelper = DBHelper.getInstance(applicationContext);
    }

    @Override
    public void setDevice(User user, UUID uuid) {

    }

    @Override
    public void add(LocationDataRecord entity) throws DAOException {
        Log.d(TAG, "Adding location data record.");
        /* * This is old function created by umich.
        Firebase locationListRef = new Firebase(Constants.FIREBASE_URL_LOCATION)
                .child(myUserEmail)
                .child(new SimpleDateFormat("MMddyyyy").format(new Date()).toString());
        locationListRef.push().setValue((LocationDataRecord) entity);*/

        ContentValues values = new ContentValues();

        try {
            SQLiteDatabase db = DBManager.getInstance().openDatabase();

            values.put(DBHelper.TIME, entity.getCreationTime());
//            values.put(DBHelper.TaskDayCount, entity.getTaskDayCount());
//            values.put(DBHelper.HOUR, entity.getHour());
            values.put(DBHelper.latitude_col, entity.getLatitude());
            values.put(DBHelper.longitude_col, entity.getLongitude());
            values.put(DBHelper.Accuracy_col, entity.getAccuracy());
            values.put(DBHelper.Altitude_col, entity.getAltitude());
            values.put(DBHelper.Speed_col, entity.getSpeed());
            values.put(DBHelper.Bearing_col, entity.getBearing());
            values.put(DBHelper.Provider_col, entity.getProvider());

            db.insert(DBHelper.location_table, null, values);
        }
        catch(NullPointerException e){
            e.printStackTrace();
        }
        finally {
            values.clear();
            DBManager.getInstance().closeDatabase(); // Closing database connection
        }
    }

    public void query_counting(){
        SQLiteDatabase db = DBManager.getInstance().openDatabase();
        Cursor latitudeCursor = db.rawQuery("SELECT "+ DBHelper.latitude_col +" FROM "+ DBHelper.location_table, null);
        Cursor longitudeCursor = db.rawQuery("SELECT "+ DBHelper.longitude_col +" FROM "+ DBHelper.location_table, null);
        Cursor AccuracyCursor = db.rawQuery("SELECT "+ DBHelper.Accuracy_col +" FROM "+ DBHelper.location_table, null);

        int latituderow    = latitudeCursor.getCount();
        int latitudecol    = latitudeCursor.getColumnCount();
        int longituderow= longitudeCursor.getCount();
        int longitudecol= longitudeCursor.getColumnCount();
        int Accuracyrow = AccuracyCursor.getCount();
        int Accuracycol = AccuracyCursor.getColumnCount();

        Log.d(TAG,"latituderow : " + latituderow +" latitudecol : " + latitudecol+" longituderow : " + longituderow+
                " longitudecol : " + longitudecol+" Accuracyrow : " + Accuracyrow+" Accuracycol : " + Accuracycol);

    }

    public void query_getAll(){
        SQLiteDatabase db = DBManager.getInstance().openDatabase();
        Cursor latitudeCursor = db.rawQuery("SELECT * FROM "+ DBHelper.location_table, null);

        Log.d(TAG, latitudeCursor.toString());
    }

    public void addToBeATrip(LocationDataRecord entity){

        boolean updateOrNot = false;

        LocationDataRecord record = new LocationDataRecord(
                entity.getLatitude(),
                entity.getLongitude(),
                entity.getAccuracy());

        long id = recordCount++;
        record.setID(id);

        Log.d(TAG,String.valueOf(record.getID())+ "," +
                record.getCreationTime()+ "," +
                record.getLatitude()+ "," +
                record.getLongitude()+ "," +
                record.getAccuracy());

        LocationToTrip.add(record);

        //remove out of time data
        for (int i=0; i<LocationToTrip.size(); i++) {

            LocationDataRecord locationDataRecord = LocationToTrip.get(i);

            //calculate time difference
            long diff = new Date().getTime() - LocationToTrip.get(i).getCreationTime();

            //remove outdated records.
            if (diff >= sKeepalive){
                LocationToTrip.remove(locationDataRecord);
                Log.d(TAG,"remove : "+locationDataRecord.getCreationTime()+",location : ("+locationDataRecord.getLatitude() + ","+ locationDataRecord.getLongitude()+ ")");
                updateOrNot = true;
                i--;
            }
        }

        if(updateOrNot)
            storeUpdateLocationIntoSQLite();
    }

    private void storeUpdateLocationIntoSQLite(){

        JSONObject tempdataset = new JSONObject();

        JSONObject tempdata = new JSONObject();

//        String tempdataString = "{";

        String firstTime = getmillisecondToDateWithTime(LocationToTrip.get(0).getCreationTime());
        String lastTime = getmillisecondToDateWithTime(LocationToTrip.get(LocationToTrip.size()-1).getCreationTime());

        for(int i=0; i<LocationToTrip.size(); i++){
            LocationDataRecord locationDataRecord = LocationToTrip.get(i);

            JSONObject data = new JSONObject();
            try {
                data.put("Time",locationDataRecord.getCreationTime());
                data.put("Latitude",locationDataRecord.getLatitude());
                data.put("Longtitude",locationDataRecord.getLongitude());
                data.put("Accuracy",locationDataRecord.getAccuracy());

            }catch (JSONException e){
                e.printStackTrace();
            }

            Log.d(TAG,"data : "+ data.toString());

            long tripLocid = tripLocCount++;

            try {

                tempdata.put(String.valueOf(tripLocid),data.toString());
//                tempdata += data.toString();

            }catch(JSONException e) {
                Log.d(TAG, "tempdata : " + tempdata);

            }
        }

//        tempdataString += "}";

        try{
            tempdataset.put(firstTime+"-"+lastTime,tempdata);
        }catch (JSONException e){
            e.printStackTrace();
        }

        Log.d(TAG, "tempdataset : "+ tempdataset);

        long id = tripCount++;

        JSONObject dataset = new JSONObject();
        try{
            dataset.put(String.valueOf(id),String.valueOf(tempdataset));
        }catch (JSONException e){
            e.printStackTrace();
        }

        Log.d(TAG, "dataset : "+ dataset);

        ContentValues values = new ContentValues();

        try {
            SQLiteDatabase db = DBManager.getInstance().openDatabase();

            values.put("Trip", String.valueOf(dataset));

            db.insert(DBHelper.trip_table, null, values);
        }
        catch(NullPointerException e){
            e.printStackTrace();
        }
        finally {
            values.clear();
            DBManager.getInstance().closeDatabase(); // Closing database connection
        }

    }

    private String getmillisecondToDateWithTime(long timeStamp){

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timeStamp);

        int mYear = calendar.get(Calendar.YEAR);
        int mMonth = calendar.get(Calendar.MONTH);
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

    public static ArrayList<String> getTripDatafromSQLite(){

        Log.d("getTripDatafromSQLite","test1");

        ArrayList<String> rows = new ArrayList<String>();

        try {
            SQLiteDatabase db = DBManager.getInstance().openDatabase();
            Cursor tripCursor = db.rawQuery("SELECT * FROM " + DBHelper.trip_table, null);

//        int columnCount = tripCursor.getColumnCount();

            Log.d("getTripDatafromSQLite while","test2");

            while (tripCursor.moveToNext()) {
                String curRow = "";
            /*for (int i=0; i<columnCount; i++){

                Log.d("getTripDatafromSQLite", "tripCursor.getString(i) : "+ tripCursor.getString(i));

                curRow = tripCursor.getString(i);
            }*/
                Log.d("getTripDatafromSQLite", "tripCursor.getString(i) : " + tripCursor.getString(1));

                JSONObject data = new JSONObject(tripCursor.getString(1));

                Iterator<String> keys = data.keys();
               /* // get some_name_i_wont_know in str_Name
                String str_Name=keys.next();
                // get the value i care about
                String value = data.optString(str_Name);*/

                Log.d("getTripDatafromSQLite while2","test3");

                while (keys.hasNext()){

                    String key =keys.next();
                    String value = data.getString(key);

                    Log.d("getTripDatafromSQLite while2", "tripCursor.getString(i) : " + key +":"+value);

                    JSONObject toList = new JSONObject(value);

                    Log.d("getTripDatafromSQLite while3","toList : " + toList);

                    Log.d("getTripDatafromSQLite while3","test4");

                    Iterator<String> timekeys = toList.keys();
                    while (timekeys.hasNext()){

                        String timekey = timekeys.next();
                        String timevalue = toList.getString(timekey);
                        //timekey = 2017/6/26 15:42:34-2017/6/26 15:47:31
                        Log.d("getTripDatafromSQLite while3", "toList : " + timekey +":"+new JSONObject(timevalue));

                        /*//TODO maybe change to annotateohio
                        JSONObject EachLoctoList = new JSONObject(timevalue);
                        Iterator<String> EachLockeys = EachLoctoList.keys();
                        while (EachLockeys.hasNext()){
                            String EachLockey = EachLockeys.next();
                            String EachLocValue = EachLoctoList.getString(EachLockey);

                            JSONObject EachLoc = new JSONObject(EachLocValue);


                        }*/

                        rows.add(timekey);

                    }


                }

            }
            tripCursor.close();

            DBManager.getInstance().closeDatabase();
        }catch (Exception e){
            e.printStackTrace();
        }

        return rows;
    }

    public static ArrayList<LatLng> getTripLocToDrawOnMap(String timekey){

        Log.d("getTripandLocDatafromSQLite","test1");

        ArrayList<LatLng> rows = new ArrayList<LatLng>();

        try {
            SQLiteDatabase db = DBManager.getInstance().openDatabase();
            Cursor tripCursor = db.rawQuery("SELECT * FROM " + DBHelper.trip_table, null);

            Log.d("getTripandLocDatafromSQLite while","test2");

            while (tripCursor.moveToNext()) {
                String curRow = "";

                Log.d("getTripandLocDatafromSQLite", "tripCursor.getString(i) : " + tripCursor.getString(1).replace("\\",""));

                JSONObject data = new JSONObject(tripCursor.getString(1).replace("\\",""));

                Log.d("getTripandLocDatafromSQLite","data : " + data.toString());

                String timevalue = data.getString(timekey);

                Log.d("getTripandLocDatafromSQLite","data.getString(timekey) : " + data.getString(timekey));

                JSONObject EachLoctoList = new JSONObject(timevalue);
                Iterator<String> EachLockeys = EachLoctoList.keys();
                while (EachLockeys.hasNext()){
                    String EachLockey = EachLockeys.next();
                    String EachLocValue = EachLoctoList.getString(EachLockey);

                    JSONObject EachLoc = new JSONObject(EachLocValue);
                    double lat = Double.valueOf(EachLoc.getString("Latitude"));
                    double lng = Double.valueOf(EachLoc.getString("Longtitude"));
                    LatLng latLng = new LatLng(lat,lng);

                    rows.add(latLng);

                }

               /* Iterator<String> keys = data.keys();
                // get some_name_i_wont_know in str_Name
                String str_Name=keys.next();
                // get the value i care about
                String value = data.optString(str_Name);

                Log.d("getTripDatafromSQLite while2","test3");

                *//*while (keys.hasNext()){

                    String key =keys.next();
                    String value = data.getString(key);

                    Log.d("getTripDatafromSQLite while2", "tripCursor.getString(i) : " + key +":"+value);

                    JSONObject toList = new JSONObject(value);

                    Log.d("getTripDatafromSQLite while3","toList : " + toList);

                    Log.d("getTripDatafromSQLite while3","test4");

                    Iterator<String> timekeys = toList.keys();
                    while (timekeys.hasNext()){

                        String timekey = timekeys.next();
                        String timevalue = toList.getString(timekey);
                        //timekey = 2017/6/26 15:42:34-2017/6/26 15:47:31
                        Log.d("getTripDatafromSQLite while3", "toList : " + timekey +":"+new JSONObject(timevalue));

                        //TODO maybe change to annotateohio
                        JSONObject EachLoctoList = new JSONObject(timevalue);
                        Iterator<String> EachLockeys = EachLoctoList.keys();
                        while (EachLockeys.hasNext()){
                            String EachLockey = EachLockeys.next();
                            String EachLocValue = EachLoctoList.getString(EachLockey);

                            JSONObject EachLoc = new JSONObject(EachLocValue);


                        }

                        rows.add(timekey);

                    }

                }*/

            }
            tripCursor.close();

            DBManager.getInstance().closeDatabase();
        }catch (Exception e){

        }

        return rows;
    }

    @Override
    public void delete(LocationDataRecord entity) throws DAOException {
        // no-op for now.
    }

    @Override
    public Future<List<LocationDataRecord>> getAll() throws DAOException {
        final SettableFuture<List<LocationDataRecord>> settableFuture =
                SettableFuture.create();
        /*
        Firebase locationListRef = new Firebase(Constants.FIREBASE_URL_LOCATION)
                .child(myUserEmail)
                .child(new SimpleDateFormat("MMddyyyy").format(new Date()).toString());

        locationListRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Map<String, LocationDataRecord> locationListMap =
                        (HashMap<String,LocationDataRecord>) dataSnapshot.getValue();
                List<LocationDataRecord> values = (List) locationListMap.values();
                settableFuture.set(values);
            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {
                settableFuture.set(null);
            }
        });
        */
        return settableFuture;
    }

    @Override
    public Future<List<LocationDataRecord>> getLast(int N) throws DAOException {
        final SettableFuture<List<LocationDataRecord>> settableFuture = SettableFuture.create();
        /*
        final Date today = new Date();

        final List<LocationDataRecord> lastNRecords = Collections.synchronizedList(
                new ArrayList<LocationDataRecord>());

        getLastNValues(N,
                myUserEmail,
                today,
                lastNRecords,
                settableFuture);
*/
        return settableFuture;
    }

    @Override
    public void update(LocationDataRecord oldEntity, LocationDataRecord newEntity)
            throws DAOException {
        Log.e(TAG, "Method not implemented. Returning null");
    }

    private final void getLastNValues(final int N,
                                      final String userEmail,
                                      final Date someDate,
                                      final List<LocationDataRecord> synchronizedListOfRecords,
                                      final SettableFuture settableFuture) {
        /* This is old function created by umich.
        Firebase firebaseRef = new Firebase(Constants.FIREBASE_URL_LOCATION)
                .child(userEmail)
                .child(new SimpleDateFormat("MMddyyyy").format(someDate).toString());
        */
        if(N <= 0) {
            /* TODO(neerajkumar): Get this f***up fixed! */

            // The first element in the list is actually the last in the database.
            // Reverse the list before setting the future with a result.
            Collections.reverse(synchronizedListOfRecords);

            settableFuture.set(synchronizedListOfRecords);
            return;
        }


/*      firebaseRef.limitToLast(N).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {

                int newN = N;

                // dataSnapshot.exists returns false when the
                // <root>/<datarecord>/<userEmail>/<date> location does not exist.
                // What it means is that no entries were added for this date, i.e.
                // all the historic information has been exhausted.
                if(!dataSnapshot.exists()) {
                    /* TODO(neerajkumar): Get this f***up fixed! */

                    // The first element in the list is actually the last in the database.
                    // Reverse the list before setting the future with a result.
/*                    Collections.reverse(synchronizedListOfRecords);

                    settableFuture.set(synchronizedListOfRecords);
                    return;
                }

                for(DataSnapshot snapshot: dataSnapshot.getChildren()) {
                    synchronizedListOfRecords.add(snapshot.getValue(LocationDataRecord.class));
                    newN--;
                }
                Date newDate = new Date(someDate.getTime() - 26 * 60 * 60 * 1000); /* -1 Day */
/*              getLastNValues(newN,
                        userEmail,
                        newDate,
                        synchronizedListOfRecords,
                        settableFuture);
            }



            @Override
            public void onCancelled(FirebaseError firebaseError) {

                /* TODO(neerajkumar): Get this f***up fixed! */

                // The first element in the list is actually the last in the database.
                // Reverse the list before setting the future with a result.
/*                Collections.reverse(synchronizedListOfRecords);

                // This would mean that the firebase ref does not exist thereby meaning that
                // the number of entries for all dates are over before we could get the last N
                // results
                settableFuture.set(synchronizedListOfRecords);
            }
        });*/
    }

}
