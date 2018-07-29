package edu.nctu.minuku_2;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
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

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import android.os.Build;
import java.util.concurrent.ExecutionException;

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
        SharedPreferences pref = getSharedPreferences("edu.nctu.minuku", MODE_PRIVATE);
        pref.edit()
                .putLong("state_esm_create", System.currentTimeMillis() / 1000L)
                .apply();
        device_id = getDeviceid();
//        Amplitude.getInstance().initialize(this, "357d2125a984bc280669e6229646816c").enableForegroundTracking(getApplication());

//        Identify identify = new Identify().set("DEVICE_ID", device_id);
//        Amplitude.getInstance().identify(identify);
//        Amplitude.getInstance().logEvent("CREATE_FORM");
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
                Log.d(TAG, "http://who.nctu.me:8000/esm/count?device_id="+ device_id+ "&name=" + title);
                if(url.contains("https://kevchentw.github.io/minuku_web/")){
//                    Amplitude.getInstance().logEvent("FINISH_FORM");
                    Answers.getInstance().logContentView(new ContentViewEvent().putContentName("form done").putCustomAttribute("device_id", Constants.DEVICE_ID));
                    sharedPrefs.edit().putString("last_title", title).apply();
                    view.loadUrl("http://who.nctu.me:8000/esm/count?device_id="+ device_id + "&name=" + title + "&app=" + app);
                    SharedPreferences pref = getSharedPreferences("edu.nctu.minuku", MODE_PRIVATE);
                    pref.edit()
                            .putLong("last_form_done_time", System.currentTimeMillis() / 1000L)
                            .apply();

                    pref.edit()
                            .putLong("state_esm_done", System.currentTimeMillis() / 1000L)
                            .apply();
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

    private class SendHttpRequestTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
            HttpURLConnection connection = null;
            android.util.Log.d(TAG, "SendHttpRequestTask");
            SharedPreferences pref = getSharedPreferences("edu.nctu.minuku", MODE_PRIVATE);
            Long created_at;
            try {
                URL url = new URL("http://who.nctu.me:8000/blacklist/?user=" + device_id);
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
                    android.util.Log.d(TAG, "GET RESULT BLACKLIST");
                    android.util.Log.d(TAG, response.toString());

                    pref.edit()
                            .putString("blacklist", response.toString())
                            .apply();

                    String blacklist = getSharedPreferences("edu.nctu.minuku", MODE_PRIVATE)
                            .getString("blacklist", "");

                    android.util.Log.d(TAG,"saved current blacklist"+ blacklist);

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
