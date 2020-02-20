package com.parse;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import com.parse.PushRoutes.Route;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

class PushRouter {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    public static final String GCM_RECEIVE_ACTION = "com.google.android.c2dm.intent.RECEIVE";
    private static final String LEGACY_ROUTE_LOCATION = "persistentCallbacks";
    static int MAX_HISTORY_LENGTH = 10;
    private static final String STATE_LOCATION = "pushState";
    private static final String TAG = "com.parse.ParsePushRouter";
    private static PushRouter instance;
    private static Task<Void> lastTask;
    private static PushListener pushListener;
    private final PushHistory history;
    /* access modifiers changed from: private */
    public final AtomicBoolean isRefreshingInstallation = new AtomicBoolean(false);
    private final PushRoutes routes;
    private final String stateLocation;

    enum HandlePushResult {
        INVALID_DATA,
        FAILED_HISTORY_TEST,
        NO_ROUTE_FOUND,
        INVALID_ROUTE,
        BROADCAST_INTENT,
        SHOW_NOTIFICATION,
        SHOW_NOTIFICATION_AND_BROADCAST_INTENT
    }

    interface PushListener {
        void onPushHandled(JSONObject jSONObject, HandlePushResult handlePushResult);
    }

    public static Task<Void> subscribeAsync(final String channel, final Class<? extends Activity> cls, final int iconId) {
        Task<Void> subscribeTask;
        if (channel != null && !PushRoutes.isValidChannelName(channel)) {
            throw new IllegalArgumentException("Invalid channel name: + " + channel + " (must be empty " + "string or a letter followed by alphanumerics or hyphen)");
        } else if (cls == null) {
            throw new IllegalArgumentException("Can't subscribe to channel with null activity class.");
        } else if (iconId == 0) {
            throw new IllegalArgumentException("Must subscribe to channel with a valid icon identifier.");
        } else {
            synchronized (PushRouter.class) {
                subscribeTask = getLastTask().onSuccess(new Continuation<Void, Void>() {
                    public Void then(Task<Void> task) {
                        PushRouter.getInstance().subscribe(channel, cls, iconId);
                        return null;
                    }
                }, EXECUTOR);
                lastTask = makeUnhandledExceptionsFatal(subscribeTask);
            }
            return subscribeTask;
        }
    }

    public static Task<Void> unsubscribeAsync(final String channel) {
        Task<Void> unsubscribeTask;
        synchronized (PushRouter.class) {
            unsubscribeTask = getLastTask().onSuccess(new Continuation<Void, Void>() {
                public Void then(Task<Void> task) {
                    PushRouter.getInstance().unsubscribe(channel);
                    return null;
                }
            }, EXECUTOR);
            lastTask = makeUnhandledExceptionsFatal(unsubscribeTask);
        }
        return unsubscribeTask;
    }

    public static Task<Set<String>> getSubscriptionsAsync(final boolean includeDefaultRoute) {
        Task<Set<String>> getSubscriptionsTask;
        synchronized (PushRouter.class) {
            getSubscriptionsTask = getLastTask().onSuccess(new Continuation<Void, Set<String>>() {
                public Set<String> then(Task<Void> task) {
                    return PushRouter.getInstance().getSubscriptions(includeDefaultRoute);
                }
            }, EXECUTOR);
            lastTask = makeUnhandledExceptionsFatal(getSubscriptionsTask.makeVoid());
        }
        return getSubscriptionsTask;
    }

    public static Task<JSONObject> getPushRequestJSONAsync() {
        Task<JSONObject> getPushRequestTask;
        synchronized (PushRouter.class) {
            getPushRequestTask = getLastTask().onSuccess(new Continuation<Void, JSONObject>() {
                public JSONObject then(Task<Void> task) {
                    return PushRouter.getInstance().getPushRequestJSON();
                }
            }, EXECUTOR);
            lastTask = makeUnhandledExceptionsFatal(getPushRequestTask.makeVoid());
        }
        return getPushRequestTask;
    }

    public static boolean isGcmPushIntent(Intent intent) {
        return intent != null && GCM_RECEIVE_ACTION.equals(intent.getAction());
    }

    public static void handleGcmPushIntent(final Intent intent) {
        final Semaphore done = new Semaphore(0);
        EXECUTOR.submit(new Runnable() {
            public void run() {
                PushRouter.getInstance().handleGcmPush(intent);
                done.release();
            }
        });
        done.acquireUninterruptibly();
    }

    public static Task<Void> handlePpnsPushAsync(final JSONObject pushPayload) {
        Task<Void> receivedPushTask;
        synchronized (PushRouter.class) {
            receivedPushTask = getLastTask().onSuccess(new Continuation<Void, Void>() {
                public Void then(Task<Void> task) {
                    if (pushPayload != null) {
                        PushRouter.getInstance().handlePpnsPush(pushPayload);
                    }
                    return null;
                }
            }, EXECUTOR);
            lastTask = makeUnhandledExceptionsFatal(receivedPushTask);
        }
        return receivedPushTask;
    }

    public static Task<Void> reloadFromDiskAsync(final boolean removeExistingState) {
        Task<Void> reloadTask;
        synchronized (PushRouter.class) {
            reloadTask = getLastTask().onSuccess(new Continuation<Void, Void>() {
                public Void then(Task<Void> task) {
                    PushRouter.reloadInstance(removeExistingState);
                    return null;
                }
            }, EXECUTOR);
            lastTask = makeUnhandledExceptionsFatal(reloadTask);
        }
        return reloadTask;
    }

    private static synchronized Task<Void> getLastTask() {
        Task<Void> task;
        synchronized (PushRouter.class) {
            if (lastTask == null) {
                lastTask = Task.forResult(null).makeVoid();
            }
            task = lastTask;
        }
        return task;
    }

    private static Task<Void> makeUnhandledExceptionsFatal(Task<Void> lastTask2) {
        return lastTask2.continueWith(new Continuation<Void, Void>() {
            public Void then(final Task<Void> task) {
                if (task.isFaulted()) {
                    Task.UI_THREAD_EXECUTOR.execute(new Runnable() {
                        public void run() {
                            throw new RuntimeException(task.getError());
                        }
                    });
                }
                return null;
            }
        }, EXECUTOR);
    }

    private static JSONArray getChannelsArrayFromInstallation(ParseInstallation installation) {
        JSONArray array = null;
        List<String> list = installation.getList("channels");
        if (list != null) {
            array = (JSONArray) Parse.encode(list, PointerOrLocalIdEncodingStrategy.get());
        }
        return array != null ? array : new JSONArray();
    }

    static synchronized void setGlobalPushListener(PushListener listener) {
        synchronized (PushRouter.class) {
            pushListener = listener;
        }
    }

    static void noteHandlePushResult(final JSONObject pushData, final HandlePushResult result) {
        PushListener listener;
        synchronized (PushRouter.class) {
            listener = pushListener;
        }
        if (listener != null) {
            final PushListener finalListener = listener;
            getLastTask().continueWith(new Continuation<Void, Void>() {
                public Void then(Task<Void> task) {
                    finalListener.onPushHandled(pushData, result);
                    return null;
                }
            }, EXECUTOR);
        }
    }

    /* access modifiers changed from: private */
    public static PushRouter getInstance() {
        if (instance == null) {
            JSONObject json = migrateV1toV3(LEGACY_ROUTE_LOCATION, STATE_LOCATION);
            if (json == null) {
                json = migrateV2toV3(STATE_LOCATION, STATE_LOCATION);
            }
            if (json == null) {
                json = ParseObject.getDiskObject(Parse.applicationContext, STATE_LOCATION);
            }
            instance = new PushRouter(STATE_LOCATION, new PushRoutes(json), new PushHistory(MAX_HISTORY_LENGTH, json));
        }
        return instance;
    }

    /* access modifiers changed from: private */
    public static PushRouter reloadInstance(boolean removeExistingState) {
        if (removeExistingState) {
            ParseObject.deleteDiskObject(Parse.applicationContext, LEGACY_ROUTE_LOCATION);
            ParseObject.deleteDiskObject(Parse.applicationContext, STATE_LOCATION);
        }
        instance = null;
        return getInstance();
    }

    static JSONObject migrateV1toV3(String location, String migratedLocation) {
        ParseInstallation installation = ParseInstallation.getCurrentInstallation();
        JSONObject legacyJSON = ParseObject.getDiskObject(Parse.applicationContext, location);
        JSONObject migratedJSON = null;
        if (legacyJSON != null) {
            Parse.logD(TAG, "Migrating push state from V1 to V3: " + legacyJSON);
            ArrayList<String> channels = new ArrayList<>();
            Iterator<String> keys = legacyJSON.keys();
            while (keys.hasNext()) {
                channels.add(keys.next());
            }
            installation.addAllUnique("channels", channels);
            installation.saveEventually();
            try {
                JSONObject json = new JSONObject();
                json.put("version", 3);
                json.put("routes", legacyJSON);
                json.put("channels", getChannelsArrayFromInstallation(installation));
                ParseObject.saveDiskObject(Parse.applicationContext, migratedLocation, json);
                migratedJSON = json;
            } catch (JSONException e) {
                Parse.logE(TAG, "Unexpected JSONException when serializing upgraded v1 push state: ", e);
            }
            if (!location.equals(migratedLocation)) {
                ParseObject.deleteDiskObject(Parse.applicationContext, location);
            }
        }
        return migratedJSON;
    }

    static JSONObject migrateV2toV3(String location, String migratedLocation) {
        ParseInstallation installation = ParseInstallation.getCurrentInstallation();
        JSONObject json = ParseObject.getDiskObject(Parse.applicationContext, location);
        JSONObject migratedJSON = null;
        if (json == null) {
            return null;
        }
        if (json.optInt("version") == 2) {
            Parse.logD(TAG, "Migrating push state from V2 to V3: " + json);
            JSONArray addChannels = json.optJSONArray("addChannels");
            if (addChannels != null) {
                ArrayList<String> toAdd = new ArrayList<>();
                for (int i = 0; i < addChannels.length(); i++) {
                    toAdd.add(addChannels.optString(i));
                }
                installation.addAllUnique("channels", toAdd);
                installation.saveEventually();
            }
            JSONArray removeChannels = json.optJSONArray("removeChannels");
            if (removeChannels != null) {
                ArrayList<String> toRemove = new ArrayList<>();
                for (int i2 = 0; i2 < removeChannels.length(); i2++) {
                    toRemove.add(removeChannels.optString(i2));
                }
                installation.removeAll("channels", toRemove);
                installation.saveEventually();
            }
            if (json.has("installation")) {
                installation.mergeAfterFetch(json.optJSONObject("installation"), new ParseDecoder(), true);
                installation.saveEventually();
            }
            try {
                json.put("version", 3);
                json.remove("addChannels");
                json.remove("removeChannels");
                json.remove("installation");
                json.put("channels", getChannelsArrayFromInstallation(installation));
                ParseObject.saveDiskObject(Parse.applicationContext, migratedLocation, json);
                migratedJSON = json;
            } catch (JSONException e) {
                Parse.logE(TAG, "Unexpected JSONException when serializing upgraded v2 push state: ", e);
            }
            if (location.equals(migratedLocation)) {
                return migratedJSON;
            }
            ParseObject.deleteDiskObject(Parse.applicationContext, location);
            return migratedJSON;
        } else if (json.optInt("version") == 3) {
            return json;
        } else {
            return null;
        }
    }

    public PushRouter(String stateLocation2, PushRoutes routes2, PushHistory history2) {
        this.stateLocation = stateLocation2;
        this.routes = routes2;
        this.history = history2;
    }

    public JSONObject toJSON() throws JSONException {
        JSONObject json = merge(this.routes.toJSON(), this.history.toJSON());
        json.put("version", 3);
        json.put("channels", getChannelsArrayFromInstallation(ParseInstallation.getCurrentInstallation()));
        return json;
    }

    private static JSONObject merge(JSONObject... objects) throws JSONException {
        JSONObject[] arr$;
        JSONObject merged = new JSONObject();
        for (JSONObject object : objects) {
            Iterator<String> it = object.keys();
            while (it.hasNext()) {
                String key = (String) it.next();
                merged.put(key, object.get(key));
            }
        }
        return merged;
    }

    public boolean saveStateToDisk() {
        try {
            ParseObject.saveDiskObject(Parse.applicationContext, this.stateLocation, toJSON());
            return true;
        } catch (JSONException e) {
            Parse.logE(TAG, "Error serializing push state to json", e);
            return false;
        }
    }

    public JSONObject getPushRequestJSON() {
        JSONObject request = new JSONObject();
        try {
            request.put("installation_id", ParseInstallation.getCurrentInstallation().getInstallationId());
            request.put("oauth_key", ParseObject.getApplicationId());
            request.put("v", "a1.4.3");
            Object lastReceivedTimestamp = this.history.getLastReceivedTimestamp();
            String str = "last";
            if (lastReceivedTimestamp == null) {
                lastReceivedTimestamp = JSONObject.NULL;
            }
            request.put(str, lastReceivedTimestamp);
            Set<String> pushIds = this.history.getPushIds();
            if (pushIds.size() > 0) {
                request.put("last_seen", new JSONArray(pushIds));
            }
            request.put("ack_keep_alive", true);
            request.putOpt("ignore_after", this.history.getCutoffTimestamp());
            return request;
        } catch (JSONException e) {
            Parse.logE(TAG, "Unexpected JSONException serializing push handshake", e);
            return null;
        }
    }

    public void subscribe(String channel, Class<? extends Activity> cls, int icon) {
        ParseInstallation installation = ParseInstallation.getCurrentInstallation();
        Route route = new Route(cls.getName(), icon);
        Route oldRoute = this.routes.put(channel, route);
        if (!route.equals(oldRoute)) {
            saveStateToDisk();
        }
        if (oldRoute == null && channel != null) {
            installation.addUnique("channels", channel);
        }
        installation.saveEventually();
    }

    public void unsubscribe(String channel) {
        if (this.routes.remove(channel) != null) {
            saveStateToDisk();
            if (channel != null) {
                ParseInstallation installation = ParseInstallation.getCurrentInstallation();
                installation.removeAll("channels", Arrays.asList(new String[]{channel}));
                installation.saveEventually();
            }
        }
    }

    public Set<String> getSubscriptions(boolean includeDefaultRoute) {
        Set<String> subscriptions = new HashSet<>();
        List<String> channels = ParseInstallation.getCurrentInstallation().getList("channels");
        if (channels != null) {
            subscriptions.addAll(channels);
        }
        subscriptions.addAll(this.routes.getChannels());
        if (!includeDefaultRoute) {
            subscriptions.remove(null);
        }
        return Collections.unmodifiableSet(subscriptions);
    }

    private Date serverInstallationUpdatedAt(JSONObject pushData) {
        String updatedAtString = pushData.optString("installation_updated_at", null);
        if (updatedAtString != null) {
            return Parse.stringToDate(updatedAtString);
        }
        return null;
    }

    private void maybeRefreshInstallation(Date serverUpdatedAt) {
        Date updatedAt = ParseInstallation.getCurrentInstallation().getUpdatedAt();
        if (updatedAt != null && serverUpdatedAt != null && updatedAt.compareTo(serverUpdatedAt) < 0 && this.isRefreshingInstallation.compareAndSet(false, true)) {
            ParseInstallation.getCurrentInstallation().fetchAsync().continueWith(new Continuation<ParseObject, Void>() {
                public Void then(Task<ParseObject> task) {
                    PushRouter.this.isRefreshingInstallation.set(false);
                    return null;
                }
            });
        }
    }

    /* access modifiers changed from: 0000 */
    public JSONObject convertGcmIntentToJSONObject(Intent intent) {
        if (intent == null) {
            return null;
        }
        String messageType = intent.getStringExtra("message_type");
        if (messageType != null) {
            Parse.logI(TAG, "Ignored special message type " + messageType + " from GCM via intent" + intent);
            return null;
        }
        String pushDataString = intent.getStringExtra("data");
        String channel = intent.getStringExtra("channel");
        String installationUpdatedAt = intent.getStringExtra("installation_updated_at");
        JSONObject pushData = null;
        boolean ignore = false;
        if (pushDataString != null) {
            try {
                pushData = new JSONObject(pushDataString);
            } catch (JSONException e) {
                Parse.logE(TAG, "Ignoring push because of JSON exception while processing: " + pushDataString, e);
                ignore = true;
            }
        }
        if (ignore) {
            return null;
        }
        try {
            JSONObject pushPayload = new JSONObject();
            try {
                pushPayload.putOpt("data", pushData);
                pushPayload.putOpt("channel", channel);
                pushPayload.putOpt("installation_updated_at", installationUpdatedAt);
                return pushPayload;
            } catch (JSONException e2) {
                e = e2;
                JSONObject jSONObject = pushPayload;
                Parse.logE(TAG, "Ignoring push because of JSON exception while building payload", e);
                return null;
            }
        } catch (JSONException e3) {
            e = e3;
            Parse.logE(TAG, "Ignoring push because of JSON exception while building payload", e);
            return null;
        }
    }

    public HandlePushResult handleGcmPush(Intent intent) {
        JSONObject pushPayload = convertGcmIntentToJSONObject(intent);
        return pushPayload != null ? handlePush(pushPayload) : HandlePushResult.INVALID_DATA;
    }

    public HandlePushResult handlePpnsPush(JSONObject pushPayload) {
        HandlePushResult result = HandlePushResult.FAILED_HISTORY_TEST;
        String pushId = pushPayload.optString("push_id", null);
        String timestamp = pushPayload.optString("time", null);
        if (timestamp == null) {
            Parse.logE(TAG, "Ignoring PPNS push missing timestamp");
            return result;
        } else if (!this.history.tryInsertPush(pushId, timestamp)) {
            return result;
        } else {
            HandlePushResult result2 = handlePush(pushPayload);
            saveStateToDisk();
            return result2;
        }
    }

    public HandlePushResult handlePush(JSONObject pushPayload) {
        HandlePushResult result = handlePushInternal(pushPayload);
        maybeRefreshInstallation(serverInstallationUpdatedAt(pushPayload));
        noteHandlePushResult(pushPayload, result);
        return result;
    }

    private HandlePushResult handlePushInternal(JSONObject pushPayload) {
        JSONObject pushData = pushPayload.optJSONObject("data");
        if (pushData == null) {
            pushData = new JSONObject();
        }
        String channel = pushPayload.optString("channel", null);
        String action = pushData.optString("action", null);
        Bundle extras = new Bundle();
        extras.putString("com.parse.Data", pushData.toString());
        extras.putString("com.parse.Channel", channel);
        if (action != null) {
            Intent broadcastIntent = new Intent();
            broadcastIntent.putExtras(extras);
            broadcastIntent.setAction(action);
            broadcastIntent.setPackage(Parse.applicationContext.getPackageName());
            Parse.applicationContext.sendBroadcast(broadcastIntent);
            if (!pushData.has("alert") && !pushData.has("title")) {
                return HandlePushResult.BROADCAST_INTENT;
            }
        }
        Route route = this.routes.get(channel);
        if (route == null && channel != null) {
            route = this.routes.get(null);
        }
        if (route == null) {
            Parse.logW(TAG, "Received push that has no handler. Did you call PushService.setDefaultPushCallback or PushService.subscribe? Push payload: " + pushPayload);
            return action != null ? HandlePushResult.BROADCAST_INTENT : HandlePushResult.NO_ROUTE_FOUND;
        }
        Class<? extends Activity> cls = route.getActivityClass();
        int iconId = route.getIconId();
        String title = pushData.optString("title", ManifestInfo.getDisplayName());
        String body = pushData.optString("alert", "Notification received.");
        if (iconId == 0) {
            Parse.logE(TAG, "Ignoring push associated with route " + route + " because iconId is invalid.");
            return HandlePushResult.INVALID_ROUTE;
        } else if (title == null) {
            Parse.logE(TAG, "Ignoring push " + pushPayload + " because no title could be found.");
            return HandlePushResult.INVALID_ROUTE;
        } else {
            ParseNotificationManager.getInstance().showNotification(Parse.applicationContext, title, body, cls, iconId, extras);
            return action != null ? HandlePushResult.SHOW_NOTIFICATION_AND_BROADCAST_INTENT : HandlePushResult.SHOW_NOTIFICATION;
        }
    }
}
