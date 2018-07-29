package edu.nctu.minuku_2;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import edu.nctu.minuku.DBHelper.DBHelper;
import edu.nctu.minuku.service.TransportationModeService;
import edu.nctu.minuku_2.service.BackgroundService;
import edu.nctu.minuku_2.service.NotificationListener;
import edu.nctu.minuku.service.MobileAccessibilityService;
import android.content.SharedPreferences;

/**
 * Created by Lawrence on 2017/7/19.
 */

public class BootCompleteReceiver extends BroadcastReceiver {

    private static final String TAG = "BootCompleteReceiver";
    private  static DBHelper dbhelper = null;


    @Override
    public void onReceive(Context context, Intent intent) {

        if(intent.getAction().equalsIgnoreCase(Intent.ACTION_BOOT_COMPLETED))
        {
            Log.d(TAG,"boot_complete in first");
            SharedPreferences pref = context.getSharedPreferences("edu.nctu.minuku", context.MODE_PRIVATE);
            pref.edit()
                    .putLong("state_bootcomplete", System.currentTimeMillis() / 1000L)
                    .apply();
            try{
                dbhelper = new DBHelper(context);
                dbhelper.getWritableDatabase();
                Log.d(TAG,"db is ok");

                /*if(!InstanceManager.isInitialized()) {
                    InstanceManager.getInstance(context);
                }*/

            }finally {

                Log.d(TAG, "Successfully receive reboot request");

//                here we start the service

                Intent tintent = new Intent(context, TransportationModeService.class);
                context.startService(tintent);
                Log.d(TAG,"TransportationModeService is ok");

                Intent bintent = new Intent(context, BackgroundService.class);
                context.startService(bintent);
                Log.d(TAG,"BackgroundService is ok");

                Intent nintent = new Intent(context, NotificationListener.class);
                context.startService(bintent);
                Log.d(TAG,"NotificationListener is ok");

                Intent mintent = new Intent(context, MobileAccessibilityService.class);
                context.startService(mintent);
                Log.d(TAG,"MobileAccessibilityService is ok");

            }




        }

    }
}
