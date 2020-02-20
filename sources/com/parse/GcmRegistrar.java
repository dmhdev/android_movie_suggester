package com.parse;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemClock;
import com.parse.Task.TaskCompletionSource;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

class GcmRegistrar {
    private static final String ERROR_EXTRA = "error";
    public static final String REGISTER_ACTION = "com.google.android.c2dm.intent.REGISTER";
    public static final String REGISTER_RESPONSE_ACTION = "com.google.android.c2dm.intent.REGISTRATION";
    private static final String REGISTRATION_ID_EXTRA = "registration_id";
    private static final String SENDER_ID = "1076345567071";
    private static final String TAG = "com.parse.GcmRegistrar";
    private Context context = null;
    /* access modifiers changed from: private */
    public Object lock = new Object();
    /* access modifiers changed from: private */
    public Request request = null;

    private static class Request {
        private static final int BACKOFF_INTERVAL_MS = 3000;
        private static final int MAX_RETRIES = 5;
        private static final String RETRY_ACTION = "com.parse.RetryGcmRegistration";
        private final PendingIntent appIntent = PendingIntent.getBroadcast(this.context, this.identifier, new Intent(), 0);
        private final Context context;
        /* access modifiers changed from: private */
        public final int identifier = this.random.nextInt();
        private final Random random = new Random();
        private final PendingIntent retryIntent;
        private final BroadcastReceiver retryReceiver;
        private final String senderId;
        private final TaskCompletionSource tcs = Task.create();
        private final AtomicInteger tries = new AtomicInteger(0);

        public static Request createAndSend(Context context2, String senderId2) {
            Request request = new Request(context2, senderId2);
            request.send();
            return request;
        }

        private Request(Context context2, String senderId2) {
            this.context = context2;
            this.senderId = senderId2;
            String packageName = this.context.getPackageName();
            Intent intent = new Intent(RETRY_ACTION).setPackage(packageName);
            intent.addCategory(packageName);
            intent.putExtra("random", this.identifier);
            this.retryIntent = PendingIntent.getBroadcast(this.context, this.identifier, intent, 0);
            this.retryReceiver = new BroadcastReceiver() {
                public void onReceive(Context context, Intent intent) {
                    if (intent != null && intent.getIntExtra("random", 0) == Request.this.identifier) {
                        Request.this.send();
                    }
                }
            };
            IntentFilter filter = new IntentFilter();
            filter.addAction(RETRY_ACTION);
            filter.addCategory(packageName);
            context2.registerReceiver(this.retryReceiver, filter);
        }

        public Task<String> getTask() {
            return this.tcs.getTask();
        }

        /* access modifiers changed from: private */
        public void send() {
            Intent intent = new Intent(GcmRegistrar.REGISTER_ACTION);
            intent.setPackage("com.google.android.gsf");
            intent.putExtra("sender", this.senderId);
            intent.putExtra("app", this.appIntent);
            ComponentName name = null;
            try {
                name = this.context.startService(intent);
            } catch (SecurityException e) {
            }
            if (name == null) {
                finish(null, "GSF_PACKAGE_NOT_AVAILABLE");
            }
            this.tries.incrementAndGet();
            Parse.logV(GcmRegistrar.TAG, "Sending GCM registration intent");
        }

        public void onReceiveResponseIntent(Intent intent) {
            String registrationId = intent.getStringExtra(GcmRegistrar.REGISTRATION_ID_EXTRA);
            String error = intent.getStringExtra(GcmRegistrar.ERROR_EXTRA);
            if (registrationId == null && error == null) {
                Parse.logE(GcmRegistrar.TAG, "Got no registration info in GCM onReceiveResponseIntent");
            } else if (!"SERVICE_NOT_AVAILABLE".equals(error) || this.tries.get() >= 5) {
                finish(registrationId, error);
            } else {
                ((AlarmManager) this.context.getSystemService("alarm")).set(2, SystemClock.elapsedRealtime() + ((long) (((1 << this.tries.get()) * BACKOFF_INTERVAL_MS) + this.random.nextInt(BACKOFF_INTERVAL_MS))), this.retryIntent);
            }
        }

        private void finish(String registrationId, String error) {
            boolean didSetResult;
            if (registrationId != null) {
                didSetResult = this.tcs.trySetResult(registrationId);
            } else {
                didSetResult = this.tcs.trySetError(new Exception("GCM registration error: " + error));
            }
            if (didSetResult) {
                this.appIntent.cancel();
                this.retryIntent.cancel();
                this.context.unregisterReceiver(this.retryReceiver);
            }
        }
    }

    private static class Singleton {
        public static final GcmRegistrar INSTANCE = new GcmRegistrar(Parse.getApplicationContext());

        private Singleton() {
        }
    }

    public static GcmRegistrar getInstance() {
        return Singleton.INSTANCE;
    }

    public static void updateAsync() {
        Task.callInBackground(new Callable<Void>() {
            public Void call() {
                GcmRegistrar.getInstance().update();
                return null;
            }
        });
    }

    GcmRegistrar(Context context2) {
        this.context = context2;
    }

    public void register() {
        if (ManifestInfo.getPushType() == PushType.GCM) {
            synchronized (this.lock) {
                ParseInstallation installation = ParseInstallation.getCurrentInstallation();
                if (installation.getDeviceToken() == null || installation.isDeviceTokenStale()) {
                    if (installation.getPushType() != PushType.GCM) {
                        installation.setPushType(PushType.GCM);
                        installation.saveEventually();
                    }
                    sendRegistrationRequest();
                }
            }
        }
    }

    public void update() {
        if (ParseInstallation.hasCurrentInstallation() && ManifestInfo.getPushType() == PushType.GCM) {
            synchronized (this.lock) {
                ParseInstallation installation = ParseInstallation.getCurrentInstallation();
                if (installation.getPushType() == PushType.GCM && (installation.getDeviceToken() == null || installation.isDeviceTokenStale())) {
                    sendRegistrationRequest();
                }
            }
        }
    }

    private void sendRegistrationRequest() {
        synchronized (this.lock) {
            if (this.request == null) {
                this.request = Request.createAndSend(this.context, SENDER_ID);
                this.request.getTask().continueWith(new Continuation<String, Void>() {
                    public Void then(Task<String> task) {
                        Exception e = task.getError();
                        if (e != null) {
                            Parse.logE(GcmRegistrar.TAG, "Got error when trying to register for GCM push", e);
                        }
                        synchronized (GcmRegistrar.this.lock) {
                            GcmRegistrar.this.request = null;
                        }
                        return null;
                    }
                });
            }
        }
    }

    public boolean isRegistrationIntent(Intent intent) {
        return intent != null && REGISTER_RESPONSE_ACTION.equals(intent.getAction());
    }

    public void handleRegistrationIntent(Intent intent) {
        if (isRegistrationIntent(intent)) {
            String registrationId = intent.getStringExtra(REGISTRATION_ID_EXTRA);
            if (registrationId != null && registrationId.length() > 0) {
                ParseInstallation installation = ParseInstallation.getCurrentInstallation();
                installation.setPushType(PushType.GCM);
                installation.setDeviceToken(registrationId);
                installation.saveEventually();
            }
            synchronized (this.lock) {
                if (this.request != null) {
                    this.request.onReceiveResponseIntent(intent);
                }
            }
        }
    }

    /* access modifiers changed from: 0000 */
    public int getRequestIdentifier() {
        int i;
        synchronized (this.lock) {
            i = this.request != null ? this.request.identifier : 0;
        }
        return i;
    }
}
