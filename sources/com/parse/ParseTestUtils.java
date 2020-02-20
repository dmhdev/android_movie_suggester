package com.parse;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import com.parse.ParseCommandCache.TestHelper;
import com.parse.PushConnection.ConnectState;
import com.parse.PushConnection.State;
import com.parse.PushConnection.StoppedState;
import com.parse.PushConnection.WaitRetryState;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

class ParseTestUtils {
    private static final String TAG = "com.parse.ParseTestUtils";
    private static final Object TEST_SERVER_LOCK = new Object();
    /* access modifiers changed from: private */
    public static volatile Semaphore awaitStartSemaphore;
    /* access modifiers changed from: private */
    public static volatile Semaphore awaitStopSemaphore;
    /* access modifiers changed from: private */
    public static PushRoutedListener globalListener;
    private static StateTransitionListener listener;
    static final AtomicBoolean strictModeEnabled = new AtomicBoolean(false);
    private static Synchronizer synchronizer;
    private static String testServer;
    static int totalNotifications = 0;

    public interface PushRoutedListener {
        void onPushRouted(JSONObject jSONObject);
    }

    static class StateTransition {
        public final PushConnection connection;
        public final State fromState;
        public final long timestamp;
        public final State toState;

        StateTransition(long timestamp2, PushConnection connection2, State fromState2, State toState2) {
            this.timestamp = timestamp2;
            this.connection = connection2;
            this.fromState = fromState2;
            this.toState = toState2;
        }

        public String toString() {
            return this.timestamp + " ms: " + this.fromState + " to " + this.toState;
        }
    }

    static class StateTransitionListener implements com.parse.PushConnection.StateTransitionListener {
        private ArrayList<StateTransition> transitions = new ArrayList<>();

        StateTransitionListener() {
        }

        public synchronized void onStateChange(PushConnection connection, State fromState, State toState) {
            this.transitions.add(new StateTransition(SystemClock.elapsedRealtime(), connection, fromState, toState));
            if (toState != null && ParseTestUtils.awaitStartSemaphore != null && (toState instanceof ConnectState)) {
                ParseTestUtils.awaitStartSemaphore.release();
            } else if (toState != null) {
                if (ParseTestUtils.awaitStopSemaphore != null && (toState instanceof StoppedState)) {
                    ParseTestUtils.awaitStopSemaphore.release();
                }
            }
        }

        public synchronized List<StateTransition> getTransitions() {
            return Collections.unmodifiableList(this.transitions);
        }
    }

    ParseTestUtils() {
    }

    public static String useServer(String theServer) {
        String oldServer = ParseObject.server;
        ParseObject.server = theServer;
        return oldServer;
    }

    public static void setTestServer(String server) {
        synchronized (TEST_SERVER_LOCK) {
            testServer = server;
        }
    }

    public static String getTestServer(Context context) {
        if (testServer == null) {
            synchronized (TEST_SERVER_LOCK) {
                if (testServer == null) {
                    try {
                        testServer = new BufferedReader(new InputStreamReader(context.getAssets().open("server.config"))).readLine();
                    } catch (Exception e) {
                        if (Build.PRODUCT.contains("vbox")) {
                            testServer = "http://192.168.56.1:3000";
                        } else if (Build.PRODUCT.contains("sdk")) {
                            testServer = "http://10.0.2.2:3000";
                        } else {
                            testServer = "http://localhost:3000";
                        }
                    }
                }
            }
        }
        return testServer;
    }

    public static String useTestServer(Context context) {
        return useServer(getTestServer(context));
    }

    public static String useBadServerPort() {
        return useBadServerPort(ParseObject.server);
    }

    public static String useInvalidServer() {
        return useServer("http://invalid.server:3000");
    }

    public static String useBadServerPort(String baseUrl) {
        String newUrl = "http://10.0.2.2:6000";
        try {
            URL base = new URL(baseUrl);
            newUrl = base.getProtocol() + "://" + base.getHost() + ":" + (base.getPort() + 999);
        } catch (MalformedURLException e) {
        }
        return useServer(newUrl);
    }

    public static void clearApp() {
        try {
            Parse.waitForTask(new ParseCommand("clear_app", null).executeAsync());
        } catch (ParseException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public static void mockV8Client() {
        try {
            Parse.waitForTask(new ParseCommand("mock_v8_client", null).executeAsync());
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    public static void unmockV8Client() {
        try {
            Parse.waitForTask(new ParseCommand("unmock_v8_client", null).executeAsync());
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    public static void useDevPushServer() {
        PushService.useServer("10.0.2.2", 8253);
    }

    public static void saveObjectToDisk(ParseObject object, Context context, String filename) {
        object.saveToDisk(context, filename);
    }

    public static ParseObject getObjectFromDisk(Context context, String filename) {
        return ParseObject.getFromDisk(context, filename);
    }

    public static ParseUser getUserObjectFromDisk(Context context, String filename) {
        return (ParseUser) ParseObject.getFromDisk(context, filename);
    }

    public static void saveStringToDisk(String string, Context context, String filename) {
        try {
            FileOutputStream out = new FileOutputStream(new File(getParseDir(context), filename));
            out.write(string.getBytes("UTF-8"));
            out.close();
        } catch (IOException | UnsupportedEncodingException e) {
        }
    }

    static File getParseDir(Context context) {
        return context.getDir("Parse", 0);
    }

    public static void initSynchronizer() {
        synchronizer = new Synchronizer();
    }

    public static Set<String> keySet(ParseObject object) {
        return object.keySet();
    }

    public static void start(int count) {
        synchronizer.start(count);
    }

    public static void assertFinishes() {
        synchronizer.assertFinishes();
    }

    public static void finish() {
        synchronizer.finish();
    }

    public static void setCommandInitialDelay(long milliSeconds) {
        ParseCommand.setDefaultInitialRetryDelay(milliSeconds);
    }

    public static void recursiveDelete(File file) {
        if (file.exists()) {
            if (file.isDirectory()) {
                for (File child : file.listFiles()) {
                    recursiveDelete(child);
                }
            }
            file.delete();
        }
    }

    public static void clearFiles() {
        recursiveDelete(Parse.getParseDir());
        recursiveDelete(Parse.getKeyValueCacheDir());
        if (Parse.commandCache != null) {
            Parse.commandCache.pause();
            Parse.commandCache = null;
        }
    }

    public static void reloadPushRouter() {
        PushRouter.reloadFromDiskAsync(false);
    }

    public static void clearCurrentInstallationFromMemory() {
        ParseInstallation.currentInstallation = null;
    }

    public static Set<String> pushRoutes(Context context) {
        Task<Set<String>> subscriptionsTask = PushRouter.getSubscriptionsAsync(false);
        try {
            subscriptionsTask.waitForCompletion();
        } catch (InterruptedException e) {
        }
        return (Set) subscriptionsTask.getResult();
    }

    public static int totalNotifications() {
        return totalNotifications;
    }

    public static String getInstallationId(Context context) {
        return ParseInstallation.getCurrentInstallation().getInstallationId();
    }

    public static JSONObject getPushRequestJSON() {
        Task<JSONObject> task = PushRouter.getPushRequestJSONAsync();
        try {
            task.waitForCompletion();
            return (JSONObject) task.getResult();
        } catch (InterruptedException e) {
            return null;
        }
    }

    public static JSONObject getSerializedPushStateJSON() {
        return ParseObject.getDiskObject(Parse.applicationContext, "pushState");
    }

    public static void resetAwaitConnectionStarted() {
        awaitStartSemaphore = new Semaphore(0);
    }

    public static void resetAwaitConnectionStopped() {
        awaitStopSemaphore = new Semaphore(0);
    }

    public static boolean awaitConnectionStarted() throws Exception {
        return awaitStartSemaphore.tryAcquire(5, TimeUnit.SECONDS);
    }

    public static boolean awaitConnectionStopped() throws Exception {
        if (awaitStopSemaphore == null) {
            awaitStopSemaphore = new Semaphore(0);
        }
        return awaitStopSemaphore.tryAcquire(5, TimeUnit.SECONDS);
    }

    public static List<StateTransition> getPushConnectionStateTransitions() {
        return listener.getTransitions();
    }

    public static List<Long> getPushConnectionRetryDelays() {
        List<Long> delays = new ArrayList<>();
        for (StateTransition transition : getPushConnectionStateTransitions()) {
            if (transition.fromState instanceof WaitRetryState) {
                delays.add(Long.valueOf(((WaitRetryState) transition.fromState).getDelay()));
            }
        }
        return delays;
    }

    public static void tearDownPushTest(Context context) {
        PushConnection.setStateTransitionListener(null);
        PushConnection.KEEP_ALIVE_INTERVAL = 900000;
        PushConnection.ENABLE_RETRY_DELAY = true;
        ParseNotificationManager.getInstance().setShouldShowNotifications(true);
        clearFiles();
        ParseInstallation.clearCurrentInstallationFromDisk(context);
        PushRouter.reloadFromDiskAsync(true);
        setPushRoutedListener(null);
        awaitStartSemaphore = null;
        awaitStopSemaphore = null;
    }

    public static synchronized void setPushRoutedListener(PushRoutedListener listener2) {
        synchronized (ParseTestUtils.class) {
            globalListener = listener2;
        }
    }

    public static void setUpPushTest(Context context) {
        ManifestInfo.setPushType(PushType.PPNS);
        awaitStartSemaphore = null;
        awaitStopSemaphore = null;
        listener = new StateTransitionListener();
        PushConnection.setStateTransitionListener(listener);
        ParseNotificationManager.getInstance().setShouldShowNotifications(false);
        useTestServer(context);
        ParseInstallation.clearCurrentInstallationFromDisk(context);
        PushRouter.reloadFromDiskAsync(true);
        initSynchronizer();
        totalNotifications = 0;
        PushRouter.setGlobalPushListener(new PushListener() {
            public void onPushHandled(JSONObject pushData, HandlePushResult result) {
                PushRoutedListener listener;
                if (result == HandlePushResult.SHOW_NOTIFICATION) {
                    ParseTestUtils.totalNotifications++;
                    synchronized (ParseTestUtils.class) {
                        listener = ParseTestUtils.globalListener;
                    }
                    if (listener != null) {
                        listener.onPushRouted(pushData);
                    }
                }
            }
        });
    }

    public static void startServiceIfRequired(Context context) {
        PushService.startServiceIfRequired(context);
    }

    public static void setRetryDelayEnabled(boolean enable) {
        PushConnection.ENABLE_RETRY_DELAY = enable;
    }

    public static ServerSocket mockPushServer() {
        try {
            ServerSocket socket = new ServerSocket(0);
            InetSocketAddress address = (InetSocketAddress) socket.getLocalSocketAddress();
            PushService.useServer(address.getHostName(), address.getPort());
            Parse.logI(TAG, "running mockPushServer on port " + address);
            return socket;
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public static int numKeyValueCacheFiles() {
        return Parse.getKeyValueCacheDir().listFiles().length;
    }

    public static void setMaxKeyValueCacheFiles(int max) {
        Parse.maxKeyValueCacheFiles = max;
    }

    public static void setMaxKeyValueCacheBytes(int max) {
        Parse.maxKeyValueCacheBytes = max;
    }

    public static void resetCommandCache() {
        ParseCommandCache cache = Parse.getCommandCache();
        TestHelper helper = cache.getTestHelper();
        cache.clear();
        helper.clear();
    }

    public static void disconnectCommandCache() {
        Parse.getCommandCache().setConnected(false);
    }

    public static void reconnectCommandCache() {
        Parse.getCommandCache().setConnected(true);
    }

    public static boolean waitForCommandCacheEnqueue() {
        return Parse.getCommandCache().getTestHelper().waitFor(3);
    }

    public static boolean waitForCommandCacheSuccess() {
        return Parse.getCommandCache().getTestHelper().waitFor(1) && Parse.getCommandCache().getTestHelper().waitFor(5);
    }

    public static boolean waitForCommandCacheFailure() {
        return Parse.getCommandCache().getTestHelper().waitFor(2);
    }

    public static int commandCacheUnexpectedEvents() {
        return Parse.getCommandCache().getTestHelper().unexpectedEvents();
    }

    public static int setPushHistoryLength(int length) {
        int oldLength = PushRouter.MAX_HISTORY_LENGTH;
        PushRouter.MAX_HISTORY_LENGTH = length;
        return oldLength;
    }

    public static void setStrictModeEnabledForMainThread(final boolean enabled) {
        if (strictModeEnabled.compareAndSet(!enabled, enabled)) {
            final Semaphore done = new Semaphore(0);
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                public void run() {
                    ParseTestUtils.setStrictModeEnabledForThisThread(enabled);
                    done.release();
                }
            });
            done.acquireUninterruptibly();
        }
    }

    public static void setStrictModeEnabledForThisThread(boolean enabled) {
        try {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            Class<?> strictModeClass = Class.forName("android.os.StrictMode", true, loader);
            if (enabled) {
                Class<?> threadPolicyClass = Class.forName("android.os.StrictMode$ThreadPolicy", true, loader);
                Class<?> threadPolicyBuilderClass = Class.forName("android.os.StrictMode$ThreadPolicy$Builder", true, loader);
                Object threadPolicy = threadPolicyBuilderClass.getMethod("build", new Class[0]).invoke(threadPolicyBuilderClass.getMethod("penaltyDeath", new Class[0]).invoke(threadPolicyBuilderClass.getMethod("detectNetwork", new Class[0]).invoke(threadPolicyBuilderClass.getConstructor(new Class[0]).newInstance(new Object[0]), new Object[0]), new Object[0]), new Object[0]);
                strictModeClass.getMethod("setThreadPolicy", new Class[]{threadPolicyClass}).invoke(strictModeClass, new Object[]{threadPolicy});
                return;
            }
            strictModeClass.getMethod("enableDefaults", new Class[0]).invoke(strictModeClass, new Object[0]);
        } catch (Exception e) {
        }
    }
}
