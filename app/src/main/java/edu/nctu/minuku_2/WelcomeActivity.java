package edu.nctu.minuku_2;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import edu.nctu.minuku_2.controller.timer_move;

public class WelcomeActivity extends AppCompatActivity {

    Button go;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        go = (Button)findViewById(R.id.btn_go);
        go.setOnClickListener(doClick);
    }

    private Button.OnClickListener doClick = new Button.OnClickListener() {
        @Override
        public void onClick(View v) {
            Intent intent = new Intent();
            intent.setClass(WelcomeActivity.this, timer_move.class);
            startActivity(intent);
        }
    };
}
