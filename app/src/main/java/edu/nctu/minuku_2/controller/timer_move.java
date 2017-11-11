package edu.nctu.minuku_2.controller;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import edu.nctu.minuku_2.MainActivity;
import edu.nctu.minuku_2.R;

import static edu.nctu.minuku_2.controller.home.CountFlag;


//import edu.ohio.minuku_2.R;

/**
 * Created by Lawrence on 2017/4/22.
 */

public class timer_move extends AppCompatActivity {

    final private String LOG_TAG = "timer_move";

    Button walk,bike,car;
    private Button site2;

    public static String TrafficFlag;

    private LayoutInflater mInflater;
    private Context context;

    public timer_move(LayoutInflater mInflater){
        this.mInflater = mInflater;
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.timer_move);

//        inittimer_move();
    }

    public void inittimer_move(View view){



        walk = (Button) view.findViewById(R.id.walk);
        bike = (Button) view.findViewById(R.id.bike);
        car = (Button) view.findViewById(R.id.car);

        site2 = (Button) view.findViewById(R.id.site);
        walk.setOnClickListener(walkingTime);
        bike.setOnClickListener(bikingTime);
        car.setOnClickListener(carTime);

        site2.setOnClickListener(siting);


    }
    //CountFlag: countung situation --- true:stop, false:ongoing
    private ImageButton.OnClickListener bikingTime = new ImageButton.OnClickListener() {
        @Override
        public void onClick(View view) {
            if(!CountFlag && (TrafficFlag.equals("walk") || TrafficFlag.equals("car"))) {
                Toast toast = Toast.makeText(timer_move.this, "You must finish the current situation first : " + TrafficFlag, Toast.LENGTH_LONG);
                toast.show();
            }else{
                TrafficFlag="bike";

//                startActivity(new Intent(timer_move.this, MainActivity.class));
//                LayoutInflater mInflater = getLayoutInflater().from(timer_move.this);
////        timerview = mInflater.inflate(R.layout.home, null);
                MainActivity.timerview = mInflater.inflate(R.layout.home, null);
                MainActivity.recordview = mInflater.inflate(R.layout.activity_timeline, null);


                MainActivity.mViewPager.addView(MainActivity.timerview);

                home newhome = new home();
                newhome.inithome(MainActivity.timerview);



//                timer_move.this.finish();
            }

        }
    };

    private ImageButton.OnClickListener carTime = new ImageButton.OnClickListener() {
        @Override
        public void onClick(View view) {

            if(!CountFlag && (TrafficFlag.equals("walk") || TrafficFlag.equals("bike"))){
                Toast toast = Toast.makeText(timer_move.this, "You must finish the current situation first : " + TrafficFlag, Toast.LENGTH_LONG);
                toast.show();
            }else{
                TrafficFlag="car";

                startActivity(new Intent(timer_move.this, MainActivity.class));
                timer_move.this.finish();
            }

        }
    };

    private ImageButton.OnClickListener walkingTime = new ImageButton.OnClickListener() {
        @Override
        public void onClick(View view) {

            if(!CountFlag && (TrafficFlag.equals("car") || TrafficFlag.equals("bike"))){
                Toast toast = Toast.makeText(timer_move.this, "You must finish the current situation first : " + TrafficFlag, Toast.LENGTH_LONG);
                toast.show();
            }else{
                TrafficFlag="walk";

                startActivity(new Intent(timer_move.this, MainActivity.class));
                timer_move.this.finish();
            }

        }
    };

    //to view timer_site
    private Button.OnClickListener siting = new Button.OnClickListener() {
        public void onClick(View v) {
            Log.e(LOG_TAG,"site clicked");

            //TODO this function will increase the screen in stack, need to be optimized.
            startActivity(new Intent(timer_move.this, timer_site.class));
            timer_move.this.finish();

        }
    };
}
