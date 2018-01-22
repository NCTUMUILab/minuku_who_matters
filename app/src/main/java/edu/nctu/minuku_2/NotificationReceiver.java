package edu.nctu.minuku_2;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.app.Notification;
import android.app.NotificationManager;

import edu.nctu.minuku.DBHelper.DBHelper;
import edu.nctu.minuku.service.TransportationModeService;
import edu.nctu.minuku_2.service.BackgroundService;
import edu.nctu.minuku_2.service.NotificationListener;

/**
 * Created by Lawrence on 2017/7/19.
 */

public class NotificationReceiver extends BroadcastReceiver {

    private static final String TAG = "BootCompleteReceiver";
    private  static DBHelper dbhelper = null;


    @Override
    public void onReceive(Context context, Intent intent) {

        int notificationId = intent.getIntExtra("notificationId", 0);

        // if you want cancel notification
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.cancel(notificationId);

    }
}
