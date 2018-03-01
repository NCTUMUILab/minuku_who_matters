package edu.nctu.minuku_2;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.content.Context;

import edu.nctu.minuku.logger.Log;
import android.graphics.Bitmap;
/**
 * Created by kevchentw on 2018/2/24.
 */

public class ResultActivity extends AppCompatActivity {
    private String TAG = "RESULT_ACTIVITY";
    private WebView mWebView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "Creating Result activity");

        setContentView(R.layout.activity_form);

        mWebView = (WebView) findViewById(R.id.activity_form_webview);

        WebViewClient mWebViewClient = new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                Log.i("Listener", "Start");
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

                if(url.contains("https://kevchentw.github.io/minuku_web/")){
                    return false;
                }

                return true;
            }

        };

        // Force links and redirects to open in the WebView instead of in a browser
        mWebView.setWebViewClient(mWebViewClient);

        // Enable Javascript
        WebSettings webSettings = mWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);

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

}
