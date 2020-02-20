package com.parse;

import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import com.parse.Task.TaskCompletionSource;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

class LocationNotifier {
    private static Location fakeLocation = null;
    public static final String testProviderName = "Test";

    LocationNotifier() {
    }

    static Task<ParseGeoPoint> getCurrentLocationAsync(long timeout, Criteria criteria) {
        Parse.checkContext();
        final TaskCompletionSource tcs = Task.create();
        final Capture<ScheduledFuture<?>> timeoutFuture = new Capture<>();
        final LocationManager manager = (LocationManager) Parse.applicationContext.getSystemService("location");
        final LocationListener listener = new LocationListener() {
            public void onLocationChanged(Location location) {
                ((ScheduledFuture) timeoutFuture.get()).cancel(true);
                ParseGeoPoint geoPoint = null;
                if (location != null) {
                    geoPoint = new ParseGeoPoint(location.getLatitude(), location.getLongitude());
                }
                tcs.trySetResult(geoPoint);
                manager.removeUpdates(this);
            }

            public void onProviderDisabled(String provider) {
            }

            public void onProviderEnabled(String provider) {
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {
            }
        };
        timeoutFuture.set(Parse.getScheduledExecutor().schedule(new Runnable() {
            public void run() {
                tcs.trySetError(new ParseException((int) ParseException.TIMEOUT, "location fetch timed out"));
                manager.removeUpdates(listener);
            }
        }, timeout, TimeUnit.MILLISECONDS));
        String providerName = manager.getBestProvider(criteria, true);
        if (providerName != null) {
            manager.requestLocationUpdates(providerName, 0, 0.0f, listener);
        }
        if (fakeLocation != null) {
            listener.onLocationChanged(fakeLocation);
        }
        return tcs.getTask();
    }

    static void setFakeLocation(Location location) {
        fakeLocation = location;
    }
}
