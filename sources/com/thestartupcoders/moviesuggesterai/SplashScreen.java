package com.thestartupcoders.moviesuggesterai;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class SplashScreen extends Activity {
    /* access modifiers changed from: protected */
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(1);
        setContentView(C0292R.layout.splash);
        new Thread() {
            public void run() {
                try {
                    sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    SplashScreen.this.startActivity(new Intent("com.thestartupcoders.moviesuggesterai.MAINACTIVITY"));
                }
            }
        }.start();
    }
}
