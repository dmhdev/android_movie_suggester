package com.thestartupcoders.moviesuggesterai;

import com.parse.Parse;

public class Application extends android.app.Application {
    public void onCreate() {
        super.onCreate();
        Parse.initialize(this, "cfm7ucTlY0aLn3EQUFg4w15L8eWivQImNCNupMOs", "2usvwNEh7xXbXn5ROthdbZi0mfDZpvNgdyMDFzWh");
    }
}
