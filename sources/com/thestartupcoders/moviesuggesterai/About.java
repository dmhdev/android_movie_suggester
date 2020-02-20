package com.thestartupcoders.moviesuggesterai;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

public class About extends Activity {
    /* access modifiers changed from: protected */
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(C0292R.layout.about);
        getActionBar().setDisplayHomeAsUpEnabled(true);
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        startActivityForResult(new Intent(getApplicationContext(), MainActivity.class), 0);
        return true;
    }
}
