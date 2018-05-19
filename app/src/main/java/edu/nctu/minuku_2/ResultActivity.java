package edu.nctu.minuku_2;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.telephony.TelephonyManager;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.content.Context;

import edu.nctu.minuku.config.Constants;
import edu.nctu.minuku.logger.Log;
import edu.nctu.minuku_2.service.BackgroundService;
import edu.nctu.minuku_2.service.NotificationListener;

import android.graphics.Bitmap;
import android.net.Uri;
import android.widget.Button;

import com.amplitude.api.Identify;
import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.ContentViewEvent;
import com.amplitude.api.Amplitude;
import android.webkit.WebChromeClient;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

/**
 * Created by kevchentw on 2018/2/24.
 */

public class ResultActivity extends AppCompatActivity {
    private String TAG = "RESULT_ACTIVITY";
    private WebView mWebView;
    private String title = "";
    private SharedPreferences sharedPrefs;
    private String device_id;
    private String app = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        device_id = getDeviceid();
        Amplitude.getInstance().initialize(this, "357d2125a984bc280669e6229646816c").enableForegroundTracking(getApplication());

        Identify identify = new Identify().set("DEVICE_ID", device_id);
        Amplitude.getInstance().identify(identify);
        Amplitude.getInstance().logEvent("CREATE_FORM");
        Log.d(TAG, "Creating Result activity");
        Answers.getInstance().logContentView(new ContentViewEvent().putContentName("open form").putCustomAttribute("device_id", device_id));
        startService(new Intent(getBaseContext(), BackgroundService.class));
        startService(new Intent(getBaseContext(), NotificationListener.class));
        sharedPrefs = getSharedPreferences(getString(R.string.sharedPreference), MODE_PRIVATE);

        setContentView(R.layout.activity_form);

        mWebView = (WebView) findViewById(R.id.activity_form_webview);




        WebViewClient mWebViewClient = new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                Uri uri=Uri.parse(url);
                title = uri.getQueryParameter("title");
                app = uri.getQueryParameter("app");

                Log.i("Listener~~", url);
                if(url.contains("minuku_web")){
                    SharedPreferences pref = getSharedPreferences("edu.nctu.minuku", MODE_PRIVATE);
                    pref.edit()
                            .putLong("last_form_done_time", System.currentTimeMillis() / 1000L)
                            .apply();
                }

            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.i("Listener", "Finish");
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Log.d(TAG, "http://who.nctu.me:8000/esm/count?device_id="+ Constants.DEVICE_ID + "&name=" + title);
                if(url.contains("https://kevchentw.github.io/minuku_web/")){
                    Amplitude.getInstance().logEvent("FINISH_FORM");
                    Answers.getInstance().logContentView(new ContentViewEvent().putContentName("form done").putCustomAttribute("device_id", Constants.DEVICE_ID));
                    sharedPrefs.edit().putString("last_title", title).apply();
                    view.loadUrl("http://who.nctu.me:8000/esm/count?device_id="+ Constants.DEVICE_ID + "&name=" + title + "&app=" + app);
                    if(url.contains("minuku_web")){
                        SharedPreferences pref = getSharedPreferences("edu.nctu.minuku", MODE_PRIVATE);
                        pref.edit()
                                .putLong("last_form_done_time", System.currentTimeMillis() / 1000L)
                                .apply();
                    }
                    return false;
                }
                else if(url.contains("who.nctu.me")){
                    return false;
                }

                return true;
            }

        };

        mWebView.setWebViewClient(mWebViewClient);

        // Enable Javascript
        WebSettings webSettings = mWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);

        mWebView.setWebChromeClient(new WebChromeClient());

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
//        mWebView.loadUrl("http://example.com/");
        Log.d(TAG, "GET EXTRA");
        if(extras != null){
            for (String key : extras.keySet()) {
                Object value = extras.get(key);
                Log.d(TAG, String.format("%s %s (%s)", key,
                        value.toString(), value.getClass().getName()));
            }
            if(extras.containsKey("URL"))
            {
                Log.d(TAG, "GET URL~~");
//                setContentView(R.layout.form_activity);
                // extract the extra-data in the Notification
                String url= extras.getString("URL");
                mWebView.loadUrl(url);
                Log.d(TAG, "GET URL~~");
                Log.d(TAG, mWebView.getUrl());
            }
        }

    }
    public String getDeviceid(){

        TelephonyManager mngr = (TelephonyManager)getSystemService(TELEPHONY_SERVICE);
        int permissionStatus= ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_PHONE_STATE);
        if(permissionStatus== PackageManager.PERMISSION_GRANTED){
            Constants.DEVICE_ID = mngr.getDeviceId();
            Log.e(TAG,"DEVICE_ID"+Constants.DEVICE_ID+" : "+mngr.getDeviceId());
            return mngr.getDeviceId();

        }
        return "NA";
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_result, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_refresh) {
            mWebView.reload();
            return true;
        }
        else if (id == R.id.action_reset_form_time) {
            SharedPreferences pref = getSharedPreferences("edu.nctu.minuku", MODE_PRIVATE);
            pref.edit()
                    .putLong("last_form_notification_sent_time", 0)
                    .apply();
            Context context = getApplicationContext();
            CharSequence text = "已略過問卷，請按返回鍵離開問卷！";
            int duration = Toast.LENGTH_SHORT;

            Toast toast = Toast.makeText(context, text, duration);
            toast.show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


}
