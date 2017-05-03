 package com.nalan.animation;

import android.os.Handler;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.OverScroller;
import android.widget.ProgressBar;
import android.widget.SeekBar;

import com.facebook.rebound.SimpleSpringListener;
import com.facebook.rebound.Spring;
import com.facebook.rebound.SpringConfig;
import com.facebook.rebound.SpringSystem;
import com.nalan.animation.core.SpringCalculator;
import com.nalan.animation.core.SpringCurveView;

 public class MainActivity extends AppCompatActivity {
    private Handler mainHandler;
     private Runnable springRunnable;
     private SpringCalculator springCalculator;

     private ImageView ivSpirit;
     private SeekBar barTension,barFriction;
     private long lastTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spring);

        final SpringCurveView scvCurve = (SpringCurveView) findViewById(R.id.scv_curve);
        barTension = (SeekBar) findViewById(R.id.bar_tension);
        barFriction = (SeekBar) findViewById(R.id.bar_friction);
        barTension.setProgress(40);
        barFriction.setProgress(3);

        mainHandler = new Handler();
        springCalculator = new SpringCalculator();
        ivSpirit = (ImageView) findViewById(R.id.iv_spirit);
        ivSpirit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(springCalculator.isFinished()){
                    springCalculator.setConfig(barTension.getProgress(),barFriction.getProgress());
                    springCalculator.start(1, 0);
                    lastTime = SystemClock.uptimeMillis();
                    mainHandler.post(springRunnable);

                    scvCurve.start(lastTime,0);
                }
            }
        });

        springRunnable = new Runnable() {
            @Override
            public void run() {
                final long currentTime = SystemClock.uptimeMillis();
                if(springCalculator.advance(currentTime-lastTime)){
                    float value = (float) springCalculator.getCurrentValue();
                    float scale = 1f - (value * 0.5f);
                    ivSpirit.setScaleX(scale);
                    ivSpirit.setScaleY(scale);
                    lastTime = currentTime;
                    mainHandler.post(this);

                    scvCurve.add(currentTime,value);
                }
            }
        };
    }
}
