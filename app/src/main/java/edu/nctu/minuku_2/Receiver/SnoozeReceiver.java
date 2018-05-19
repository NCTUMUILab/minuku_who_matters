package edu.nctu.minuku_2.Receiver;

import android.annotation.TargetApi;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import edu.nctu.minuku_2.R;

/**
 * Created by Lawrence on 2017/8/22.
 */

public class SnoozeReceiver extends BroadcastReceiver{

    private final String TAG = "SnoozeReceiver";
    private SharedPreferences sharedPrefs;
    private NotificationManager mManager;
    private NotificationCompat.Builder mBuilder;
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Get SnoozeReceiver");
        mManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        mBuilder = new NotificationCompat.Builder(context);
        mManager.cancel(0);


        SharedPreferences pref = context.getSharedPreferences("edu.nctu.minuku", context.MODE_PRIVATE);

        pref.edit()
                .putLong("last_form_notification_sent_time", 0)
                .apply();


        Long last_form_notification_sent_time = context.getSharedPreferences("edu.nctu.minuku", context.MODE_PRIVATE)
                .getLong("last_form_notification_sent_time", 1);

        CharSequence text = "已略過問卷";
        int duration = Toast.LENGTH_SHORT;

        Toast toast = Toast.makeText(context, text, duration);
        toast.show();

        Log.d(TAG, "noti:" + last_form_notification_sent_time.toString());

    }

}
