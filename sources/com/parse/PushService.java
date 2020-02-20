package com.parse;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PushService extends Service {
    private static final String START_IF_REQUIRED_ACTION = "com.parse.PushService.startIfRequired";
    private static final String TAG = "com.parse.PushService";
    private static final int WAKE_LOCK_TIMEOUT_MS = 20000;
    private static String host = "push.parse.com";
    private static LifecycleListener lifecycleListener = null;
    private static boolean loggedStartError = false;
    private static int port = 8253;
    private PushConnection connection;
    private ExecutorService executor;

    interface LifecycleListener {
        void onServiceCreated(Service service);

        void onServiceDestroyed(Service service);
    }

    static void setLifecycleListener(LifecycleListener listener) {
        lifecycleListener = listener;
    }

    static void runGcmIntentInService(Context context, Intent intent) {
        ServiceUtils.runWakefulIntentInService(context, intent, PushService.class, 20000);
    }

    static void stopPpnsService(Context context) {
        if (ManifestInfo.getPushType() == PushType.PPNS) {
            context.stopService(new Intent(context, PushService.class));
        }
    }

    private static void startPpnsServiceIfRequired(Context context) {
        if (ManifestInfo.getPushType() == PushType.PPNS) {
            ParseInstallation installation = ParseInstallation.getCurrentInstallation();
            if (installation.getPushType() == PushType.GCM) {
                Parse.logW(TAG, "Detected a client that used to use GCM and is now using PPNS.");
                installation.removePushType();
                installation.removeDeviceToken();
                installation.saveEventually();
            }
            ServiceUtils.runIntentInService(context, new Intent(START_IF_REQUIRED_ACTION), PushService.class);
        }
    }

    public static void startServiceIfRequired(Context context) {
        switch (ManifestInfo.getPushType()) {
            case PPNS:
                startPpnsServiceIfRequired(context);
                return;
            case GCM:
                GcmRegistrar.getInstance().register();
                return;
            default:
                if (!loggedStartError) {
                    Parse.logE(TAG, "Tried to use push, but this app is not configured for push due to: " + ManifestInfo.getNonePushTypeLogMessage());
                    loggedStartError = true;
                    return;
                }
                return;
        }
    }

    public static void subscribe(Context context, String channel, Class<? extends Activity> cls) {
        subscribe(context, channel, cls, context.getApplicationInfo().icon);
    }

    public static synchronized void subscribe(Context context, String channel, Class<? extends Activity> cls, int icon) {
        synchronized (PushService.class) {
            if (channel == null) {
                throw new IllegalArgumentException("Can't subscribe to null channel.");
            }
            PushRouter.subscribeAsync(channel, cls, icon).onSuccess(new Continuation<Void, Void>() {
                public Void then(Task<Void> task) {
                    PushService.startServiceIfRequired(Parse.applicationContext);
                    return null;
                }
            });
        }
    }

    public static synchronized void unsubscribe(Context context, String channel) {
        synchronized (PushService.class) {
            if (channel == null) {
                throw new IllegalArgumentException("Can't unsubscribe from null channel.");
            }
            unsubscribeInternal(channel);
        }
    }

    private static void unsubscribeInternal(String channel) {
        PushRouter.unsubscribeAsync(channel).onSuccessTask(new Continuation<Void, Task<Set<String>>>() {
            public Task<Set<String>> then(Task<Void> task) {
                return PushRouter.getSubscriptionsAsync(true);
            }
        }).onSuccess(new Continuation<Set<String>, Void>() {
            public Void then(Task<Set<String>> task) {
                if (((Set) task.getResult()).size() == 0) {
                    PushService.stopPpnsService(Parse.applicationContext);
                }
                return null;
            }
        });
    }

    public static void setDefaultPushCallback(Context context, Class<? extends Activity> cls) {
        setDefaultPushCallback(context, cls, context.getApplicationInfo().icon);
    }

    public static void setDefaultPushCallback(Context context, Class<? extends Activity> cls, int icon) {
        if (icon == 0) {
            throw new IllegalArgumentException("Must subscribe to channel with a valid icon identifier.");
        } else if (cls == null) {
            unsubscribeInternal(null);
        } else {
            PushRouter.subscribeAsync(null, cls, icon).onSuccess(new Continuation<Void, Void>() {
                public Void then(Task<Void> task) {
                    PushService.startServiceIfRequired(Parse.applicationContext);
                    return null;
                }
            });
        }
    }

    public static Set<String> getSubscriptions(Context context) {
        try {
            return (Set) Parse.waitForTask(PushRouter.getSubscriptionsAsync(false));
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    static void useServer(String theHost, int thePort) {
        host = theHost;
        port = thePort;
    }

    public void onCreate() {
        super.onCreate();
        if (Parse.applicationContext == null) {
            Parse.logE(TAG, "The Parse push service cannot start because Parse.initialize has not yet been called. If you call Parse.initialize from an Activity's onCreate, that call should instead be in the Application.onCreate. Be sure your Application class is registered in your AndroidManifest.xml with the android:name property of your <application> tag.");
            stopSelf();
            return;
        }
        switch (ManifestInfo.getPushType()) {
            case PPNS:
                this.connection = new PushConnection(this, host, port);
                break;
            case GCM:
                this.executor = Executors.newSingleThreadExecutor();
                break;
            default:
                Parse.logE(TAG, "PushService somehow started even though this device doesn't support push.");
                break;
        }
        if (lifecycleListener != null) {
            lifecycleListener.onServiceCreated(this);
        }
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        switch (ManifestInfo.getPushType()) {
            case PPNS:
                return onPpnsStartCommand(intent, flags, startId);
            case GCM:
                return onGcmStartCommand(intent, flags, startId);
            default:
                Parse.logE(TAG, "Started push service even though no push service is enabled: " + intent);
                ServiceUtils.completeWakefulIntent(intent);
                return 2;
        }
    }

    private int onPpnsStartCommand(Intent intent, int flags, int startId) {
        final PushConnection conn = this.connection;
        if (intent == null || intent.getAction() == null || intent.getAction().equals(START_IF_REQUIRED_ACTION)) {
            Parse.logI(TAG, "Received request to start service if required");
            PushRouter.getSubscriptionsAsync(true).continueWith(new Continuation<Set<String>, Void>() {
                public Void then(Task<Set<String>> task) {
                    Set<String> subscriptions = (Set) task.getResult();
                    if (subscriptions == null || subscriptions.size() == 0) {
                        Parse.logI(PushService.TAG, "Stopping PushService because there are no more subscriptions.");
                        PushService.this.stopSelf();
                    } else {
                        conn.start();
                    }
                    return null;
                }
            });
        }
        return 1;
    }

    private int onGcmStartCommand(final Intent intent, int flags, final int startId) {
        this.executor.execute(new Runnable() {
            public void run() {
                try {
                    PushService.this.onHandleGcmIntent(intent);
                } finally {
                    ServiceUtils.completeWakefulIntent(intent);
                    PushService.this.stopSelf(startId);
                }
            }
        });
        return 2;
    }

    /* access modifiers changed from: private */
    public void onHandleGcmIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        if (GcmRegistrar.getInstance().isRegistrationIntent(intent)) {
            GcmRegistrar.getInstance().handleRegistrationIntent(intent);
        } else if (PushRouter.isGcmPushIntent(intent)) {
            PushRouter.handleGcmPushIntent(intent);
        } else {
            Parse.logE(TAG, "PushService got unknown intent in GCM mode: " + intent);
        }
    }

    public IBinder onBind(Intent intent) {
        throw new IllegalArgumentException("You cannot bind directly to the PushService. Use PushService.subscribe instead.");
    }

    public void onDestroy() {
        if (this.connection != null) {
            this.connection.stop();
        }
        if (this.executor != null) {
            this.executor.shutdown();
        }
        if (lifecycleListener != null) {
            lifecycleListener.onServiceDestroyed(this);
        }
        super.onDestroy();
    }
}
