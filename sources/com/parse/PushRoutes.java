package com.parse;

import android.app.Activity;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Pattern;
import org.json.JSONException;
import org.json.JSONObject;

class PushRoutes {
    private static final Pattern CHANNEL_PATTERN = Pattern.compile("^$|^[a-zA-Z][A-Za-z0-9_-]*$");
    private static final String TAG = "com.parse.PushRoutes";
    private final HashMap<String, Route> channels = new HashMap<>();

    public static final class Route {
        private final String activityClassName;
        private final int iconId;

        public static Route newFromJSON(JSONObject json) {
            String activityClassName2 = null;
            int iconId2 = 0;
            if (json != null) {
                JSONObject data = json.optJSONObject("data");
                if (data != null) {
                    activityClassName2 = data.optString("activityClass", null);
                    iconId2 = data.optInt("icon", 0);
                }
            }
            if (activityClassName2 == null || iconId2 == 0) {
                return null;
            }
            return new Route(activityClassName2, iconId2);
        }

        public Route(String activityClassName2, int iconId2) {
            if (activityClassName2 == null) {
                throw new IllegalArgumentException("activityClassName can't be null");
            } else if (iconId2 == 0) {
                throw new IllegalArgumentException("iconId can't be 0");
            } else {
                this.activityClassName = activityClassName2;
                this.iconId = iconId2;
            }
        }

        public boolean equals(Object other) {
            if (other == null || !(other instanceof Route)) {
                return false;
            }
            Route that = (Route) other;
            return this.activityClassName.equals(that.activityClassName) && this.iconId == that.iconId;
        }

        public int hashCode() {
            return ((this.activityClassName.hashCode() + 31) * 31) + this.iconId;
        }

        public String toString() {
            return super.toString() + " (activityClassName: " + this.activityClassName + " iconId: " + this.iconId + ")";
        }

        public JSONObject toJSON() throws JSONException {
            JSONObject json = new JSONObject();
            JSONObject data = new JSONObject();
            data.put("appName", ManifestInfo.getDisplayName());
            data.put("activityPackage", Parse.applicationContext.getPackageName());
            data.put("activityClass", this.activityClassName);
            data.put("icon", this.iconId);
            json.put("data", data);
            json.put("name", "com.parse.StandardPushCallback");
            return json;
        }

        public Class<? extends Activity> getActivityClass() {
            Class<? extends Activity> clazz = null;
            try {
                clazz = Class.forName(this.activityClassName);
            } catch (ClassNotFoundException e) {
            }
            if (clazz != null && !Activity.class.isAssignableFrom(clazz)) {
                clazz = null;
            }
            if (clazz == null) {
                Parse.logE(PushRoutes.TAG, "Activity class " + this.activityClassName + " registered to handle push no " + "longer exists. Call PushService.subscribe with an activity class that does exist.");
            }
            return clazz;
        }

        public int getIconId() {
            return this.iconId;
        }
    }

    public static synchronized boolean isValidChannelName(String channel) {
        boolean matches;
        synchronized (PushRoutes.class) {
            matches = CHANNEL_PATTERN.matcher(channel).matches();
        }
        return matches;
    }

    public PushRoutes(JSONObject json) {
        if (json != null) {
            JSONObject jsonRoutes = json.optJSONObject("routes");
            if (jsonRoutes != null) {
                Iterator<String> it = jsonRoutes.keys();
                while (it.hasNext()) {
                    String channel = (String) it.next();
                    Route route = Route.newFromJSON(jsonRoutes.optJSONObject(channel));
                    if (!(channel == null || route == null)) {
                        put(channel, route);
                    }
                }
            }
            JSONObject defaultRoute = json.optJSONObject("defaultRoute");
            if (defaultRoute != null) {
                Route route2 = Route.newFromJSON(defaultRoute);
                if (route2 != null) {
                    put(null, route2);
                }
            }
        }
    }

    public JSONObject toJSON() throws JSONException {
        JSONObject json = new JSONObject();
        JSONObject namedRoutes = new JSONObject();
        for (Entry<String, Route> channelRoute : this.channels.entrySet()) {
            String channel = (String) channelRoute.getKey();
            Route route = (Route) channelRoute.getValue();
            if (channel == null) {
                json.put("defaultRoute", route.toJSON());
            } else {
                namedRoutes.put(channel, route.toJSON());
            }
        }
        json.put("routes", namedRoutes);
        return json;
    }

    public Set<String> getChannels() {
        return Collections.unmodifiableSet(this.channels.keySet());
    }

    public Route get(String channel) {
        return (Route) this.channels.get(channel);
    }

    public Route put(String channel, Route route) {
        if (route == null) {
            throw new IllegalArgumentException("Can't insert null route");
        } else if (channel == null || isValidChannelName(channel)) {
            return (Route) this.channels.put(channel, route);
        } else {
            throw new IllegalArgumentException("invalid channel name: " + channel);
        }
    }

    public Route remove(String channel) {
        return (Route) this.channels.remove(channel);
    }
}
