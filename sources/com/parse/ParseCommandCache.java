package com.parse;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import com.parse.Task.TaskCompletionSource;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

class ParseCommandCache {
    private static final String TAG = "com.parse.ParseCommandCache";
    private static int filenameCounter = 0;
    /* access modifiers changed from: private */
    public static Object lock = new Object();
    private File cachePath;
    private boolean connected = false;
    private ConnectivityListener connectivityListener = new ConnectivityListener() {
        public void networkConnectivityStatusChanged(Intent intent) {
            if (intent.getBooleanExtra("noConnectivity", false)) {
                ParseCommandCache.this.setConnected(false);
            } else {
                ParseCommandCache.this.setConnected(ConnectivityNotifier.getNotifier().isConnected());
            }
        }
    };
    private Logger log;
    private int maxCacheSizeBytes = 10485760;
    private HashMap<File, TaskCompletionSource> pendingTasks = new HashMap<>();
    private boolean running = false;
    private Object runningLock;
    private boolean shouldStop = false;
    private TestHelper testHelper = null;
    private int timeoutMaxRetries = 5;
    private double timeoutRetryWaitSeconds = 600.0d;
    private boolean unprocessedCommandsExist;

    public class TestHelper {
        public static final int COMMAND_ENQUEUED = 3;
        public static final int COMMAND_FAILED = 2;
        public static final int COMMAND_NOT_ENQUEUED = 4;
        public static final int COMMAND_SUCCESSFUL = 1;
        private static final int MAX_EVENTS = 1000;
        public static final int OBJECT_REMOVED = 6;
        public static final int OBJECT_UPDATED = 5;
        @SuppressLint({"UseSparseArrays"})
        private HashMap<Integer, Semaphore> events;

        private TestHelper() {
            this.events = new HashMap<>();
            clear();
        }

        public void clear() {
            this.events.clear();
            this.events.put(Integer.valueOf(1), new Semaphore(MAX_EVENTS));
            this.events.put(Integer.valueOf(2), new Semaphore(MAX_EVENTS));
            this.events.put(Integer.valueOf(3), new Semaphore(MAX_EVENTS));
            this.events.put(Integer.valueOf(4), new Semaphore(MAX_EVENTS));
            this.events.put(Integer.valueOf(5), new Semaphore(MAX_EVENTS));
            this.events.put(Integer.valueOf(6), new Semaphore(MAX_EVENTS));
            for (Integer intValue : this.events.keySet()) {
                ((Semaphore) this.events.get(Integer.valueOf(intValue.intValue()))).acquireUninterruptibly(MAX_EVENTS);
            }
        }

        public int unexpectedEvents() {
            int sum = 0;
            for (Integer intValue : this.events.keySet()) {
                sum += ((Semaphore) this.events.get(Integer.valueOf(intValue.intValue()))).availablePermits();
            }
            return sum;
        }

        public void notify(int event) {
            ((Semaphore) this.events.get(Integer.valueOf(event))).release();
        }

        public boolean waitFor(int event) {
            try {
                return ((Semaphore) this.events.get(Integer.valueOf(event))).tryAcquire(5000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
                return false;
            }
        }
    }

    public ParseCommandCache(Context context) {
        lock = new Object();
        this.runningLock = new Object();
        this.log = Logger.getLogger(TAG);
        this.cachePath = new File(Parse.getParseDir(), "CommandCache");
        this.cachePath.mkdirs();
        if (Parse.hasPermission("android.permission.ACCESS_NETWORK_STATE")) {
            setConnected(ConnectivityNotifier.getNotifier().isConnected());
            ConnectivityNotifier.getNotifier().addListener(this.connectivityListener, context);
            resume();
        }
    }

    public void setTimeoutMaxRetries(int tries) {
        synchronized (lock) {
            this.timeoutMaxRetries = tries;
        }
    }

    public void setTimeoutRetryWaitSeconds(double seconds) {
        synchronized (lock) {
            this.timeoutRetryWaitSeconds = seconds;
        }
    }

    public void setMaxCacheSizeBytes(int bytes) {
        synchronized (lock) {
            this.maxCacheSizeBytes = bytes;
        }
    }

    public void resume() {
        synchronized (this.runningLock) {
            if (!this.running) {
                new Thread("ParseCommandCache.runLoop()") {
                    public void run() {
                        ParseCommandCache.this.runLoop();
                    }
                }.start();
                try {
                    this.runningLock.wait();
                } catch (InterruptedException e) {
                    synchronized (lock) {
                        this.shouldStop = true;
                        lock.notifyAll();
                    }
                }
            }
        }
    }

    public void pause() {
        synchronized (this.runningLock) {
            if (this.running) {
                synchronized (lock) {
                    this.shouldStop = true;
                    lock.notifyAll();
                }
            }
            while (this.running) {
                try {
                    this.runningLock.wait();
                } catch (InterruptedException e) {
                }
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:15:0x002c A[SYNTHETIC, Splitter:B:15:0x002c] */
    /* JADX WARNING: Removed duplicated region for block: B:33:0x0056 A[SYNTHETIC, Splitter:B:33:0x0056] */
    /* JADX WARNING: Unknown top exception splitter block from list: {B:35:0x0059=Splitter:B:35:0x0059, B:17:0x002f=Splitter:B:17:0x002f} */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private void removeFile(java.io.File r12) {
        /*
            r11 = this;
            java.lang.Object r9 = lock
            monitor-enter(r9)
            java.util.HashMap<java.io.File, com.parse.Task$TaskCompletionSource<>> r8 = r11.pendingTasks     // Catch:{ all -> 0x005a }
            r8.remove(r12)     // Catch:{ all -> 0x005a }
            r4 = 0
            r2 = 0
            java.io.BufferedInputStream r3 = new java.io.BufferedInputStream     // Catch:{ Exception -> 0x0068, all -> 0x0053 }
            java.io.FileInputStream r8 = new java.io.FileInputStream     // Catch:{ Exception -> 0x0068, all -> 0x0053 }
            r8.<init>(r12)     // Catch:{ Exception -> 0x0068, all -> 0x0053 }
            r3.<init>(r8)     // Catch:{ Exception -> 0x0068, all -> 0x0053 }
            java.io.ByteArrayOutputStream r0 = new java.io.ByteArrayOutputStream     // Catch:{ Exception -> 0x0028, all -> 0x0061 }
            r0.<init>()     // Catch:{ Exception -> 0x0028, all -> 0x0061 }
            r8 = 1024(0x400, float:1.435E-42)
            byte[] r7 = new byte[r8]     // Catch:{ Exception -> 0x0028, all -> 0x0061 }
        L_0x001d:
            int r6 = r3.read(r7)     // Catch:{ Exception -> 0x0028, all -> 0x0061 }
            if (r6 <= 0) goto L_0x0034
            r8 = 0
            r0.write(r7, r8, r6)     // Catch:{ Exception -> 0x0028, all -> 0x0061 }
            goto L_0x001d
        L_0x0028:
            r8 = move-exception
            r2 = r3
        L_0x002a:
            if (r2 == 0) goto L_0x002f
            r2.close()     // Catch:{ IOException -> 0x005d }
        L_0x002f:
            r12.delete()     // Catch:{ all -> 0x005a }
            monitor-exit(r9)     // Catch:{ all -> 0x005a }
            return
        L_0x0034:
            org.json.JSONObject r5 = new org.json.JSONObject     // Catch:{ Exception -> 0x0028, all -> 0x0061 }
            java.lang.String r8 = "UTF-8"
            java.lang.String r8 = r0.toString(r8)     // Catch:{ Exception -> 0x0028, all -> 0x0061 }
            r5.<init>(r8)     // Catch:{ Exception -> 0x0028, all -> 0x0061 }
            com.parse.ParseCommand r1 = new com.parse.ParseCommand     // Catch:{ Exception -> 0x006a, all -> 0x0064 }
            r1.<init>(r5)     // Catch:{ Exception -> 0x006a, all -> 0x0064 }
            r1.releaseLocalIds()     // Catch:{ Exception -> 0x006a, all -> 0x0064 }
            if (r3 == 0) goto L_0x006e
            r3.close()     // Catch:{ IOException -> 0x004f }
            r2 = r3
            r4 = r5
            goto L_0x002f
        L_0x004f:
            r8 = move-exception
            r2 = r3
            r4 = r5
            goto L_0x002f
        L_0x0053:
            r8 = move-exception
        L_0x0054:
            if (r2 == 0) goto L_0x0059
            r2.close()     // Catch:{ IOException -> 0x005f }
        L_0x0059:
            throw r8     // Catch:{ all -> 0x005a }
        L_0x005a:
            r8 = move-exception
            monitor-exit(r9)     // Catch:{ all -> 0x005a }
            throw r8
        L_0x005d:
            r8 = move-exception
            goto L_0x002f
        L_0x005f:
            r10 = move-exception
            goto L_0x0059
        L_0x0061:
            r8 = move-exception
            r2 = r3
            goto L_0x0054
        L_0x0064:
            r8 = move-exception
            r2 = r3
            r4 = r5
            goto L_0x0054
        L_0x0068:
            r8 = move-exception
            goto L_0x002a
        L_0x006a:
            r8 = move-exception
            r2 = r3
            r4 = r5
            goto L_0x002a
        L_0x006e:
            r2 = r3
            r4 = r5
            goto L_0x002f
        */
        throw new UnsupportedOperationException("Method not decompiled: com.parse.ParseCommandCache.removeFile(java.io.File):void");
    }

    /* access modifiers changed from: 0000 */
    public void simulateReboot() {
        synchronized (lock) {
            this.pendingTasks.clear();
        }
    }

    /* access modifiers changed from: 0000 */
    public void fakeObjectUpdate() {
        if (this.testHelper != null) {
            this.testHelper.notify(3);
            this.testHelper.notify(1);
            this.testHelper.notify(5);
        }
    }

    public Task<Object> runEventuallyAsync(ParseCommand command, ParseObject object) {
        Parse.requirePermission("android.permission.ACCESS_NETWORK_STATE");
        return runEventuallyInternalAsync(command, false, object);
    }

    /* JADX INFO: finally extract failed */
    private Task<Object> runEventuallyInternalAsync(ParseCommand command, boolean preferOldest, ParseObject object) {
        TaskCompletionSource tcs = Task.create();
        if (object != null) {
            try {
                if (object.getObjectId() == null) {
                    command.setLocalId(object.getOrCreateLocalId());
                }
            } catch (UnsupportedEncodingException e) {
                if (5 >= Parse.getLogLevel()) {
                    this.log.log(Level.WARNING, "UTF-8 isn't supported.  This shouldn't happen.", e);
                }
                if (this.testHelper != null) {
                    this.testHelper.notify(4);
                }
                return Task.forResult(null);
            }
        }
        byte[] json = command.toJSONObject().toString().getBytes("UTF-8");
        if (json.length > this.maxCacheSizeBytes) {
            if (5 >= Parse.getLogLevel()) {
                this.log.warning("Unable to save command for later because it's too big.");
            }
            if (this.testHelper != null) {
                this.testHelper.notify(4);
            }
            return Task.forResult(null);
        }
        synchronized (lock) {
            try {
                String[] fileNames = this.cachePath.list();
                if (fileNames != null) {
                    Arrays.sort(fileNames);
                    int size = 0;
                    for (String fileName : fileNames) {
                        size += (int) new File(this.cachePath, fileName).length();
                    }
                    int size2 = size + json.length;
                    if (size2 > this.maxCacheSizeBytes) {
                        if (preferOldest) {
                            if (5 >= Parse.getLogLevel()) {
                                this.log.warning("Unable to save command for later because storage is full.");
                            }
                            Task<Object> forResult = Task.forResult(null);
                            lock.notifyAll();
                            return forResult;
                        }
                        if (5 >= Parse.getLogLevel()) {
                            this.log.warning("Deleting old commands to make room in command cache.");
                        }
                        int indexToDelete = 0;
                        while (size2 > this.maxCacheSizeBytes && indexToDelete < fileNames.length) {
                            int indexToDelete2 = indexToDelete + 1;
                            File file = new File(this.cachePath, fileNames[indexToDelete]);
                            size2 -= (int) file.length();
                            removeFile(file);
                            indexToDelete = indexToDelete2;
                        }
                    }
                }
                String prefix1 = Long.toHexString(System.currentTimeMillis());
                if (prefix1.length() < 16) {
                    char[] zeroes = new char[(16 - prefix1.length())];
                    Arrays.fill(zeroes, '0');
                    StringBuilder sb = new StringBuilder();
                    String str = new String(zeroes);
                    prefix1 = sb.append(str).append(prefix1).toString();
                }
                int i = filenameCounter;
                filenameCounter = i + 1;
                String prefix2 = Integer.toHexString(i);
                if (prefix2.length() < 8) {
                    char[] zeroes2 = new char[(8 - prefix2.length())];
                    Arrays.fill(zeroes2, '0');
                    StringBuilder sb2 = new StringBuilder();
                    String str2 = new String(zeroes2);
                    prefix2 = sb2.append(str2).append(prefix2).toString();
                }
                File path = File.createTempFile("CachedCommand_" + prefix1 + "_" + prefix2 + "_", "", this.cachePath);
                this.pendingTasks.put(path, tcs);
                command.retainLocalIds();
                FileOutputStream fileOutputStream = new FileOutputStream(path);
                OutputStream output = new BufferedOutputStream(fileOutputStream);
                output.write(json);
                output.close();
                if (this.testHelper != null) {
                    this.testHelper.notify(3);
                }
                this.unprocessedCommandsExist = true;
                lock.notifyAll();
            } catch (IOException e2) {
                if (5 >= Parse.getLogLevel()) {
                    this.log.log(Level.WARNING, "Unable to save command for later.", e2);
                }
                lock.notifyAll();
                return tcs.getTask();
            } catch (Throwable th) {
                lock.notifyAll();
                throw th;
            }
        }
    }

    public int pendingCount() {
        int length;
        synchronized (lock) {
            String[] files = this.cachePath.list();
            length = files == null ? 0 : files.length;
        }
        return length;
    }

    public void clear() {
        synchronized (lock) {
            File[] files = this.cachePath.listFiles();
            if (files != null) {
                for (File file : files) {
                    removeFile(file);
                }
                this.pendingTasks.clear();
            }
        }
    }

    public void setConnected(boolean connected2) {
        synchronized (lock) {
            if (this.connected != connected2) {
                this.connected = connected2;
                if (connected2) {
                    lock.notifyAll();
                }
            }
        }
    }

    private <T> T waitForTaskWithoutLock(Task<T> task) throws ParseException {
        T waitForTask;
        synchronized (lock) {
            final Capture<Boolean> finished = new Capture<>(Boolean.valueOf(false));
            task.continueWith(new Continuation<T, Void>() {
                public Void then(Task<T> task) throws Exception {
                    finished.set(Boolean.valueOf(true));
                    synchronized (ParseCommandCache.lock) {
                        ParseCommandCache.lock.notifyAll();
                    }
                    return null;
                }
            }, Task.BACKGROUND_EXECUTOR);
            while (!((Boolean) finished.get()).booleanValue()) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    this.shouldStop = true;
                }
            }
            waitForTask = Parse.waitForTask(task);
        }
        return waitForTask;
    }

    /* JADX WARNING: Code restructure failed: missing block: B:161:?, code lost:
        return;
     */
    /* JADX WARNING: No exception handlers in catch block: Catch:{  } */
    /* JADX WARNING: Removed duplicated region for block: B:144:0x00a4 A[SYNTHETIC] */
    /* JADX WARNING: Removed duplicated region for block: B:146:0x00a4 A[SYNTHETIC] */
    /* JADX WARNING: Removed duplicated region for block: B:147:0x00a4 A[SYNTHETIC] */
    /* JADX WARNING: Removed duplicated region for block: B:35:0x008c A[Catch:{ all -> 0x0192 }] */
    /* JADX WARNING: Removed duplicated region for block: B:37:0x00a1 A[SYNTHETIC, Splitter:B:37:0x00a1] */
    /* JADX WARNING: Removed duplicated region for block: B:66:0x0141 A[Catch:{ all -> 0x0192 }] */
    /* JADX WARNING: Removed duplicated region for block: B:69:0x015b A[SYNTHETIC, Splitter:B:69:0x015b] */
    /* JADX WARNING: Removed duplicated region for block: B:77:0x0170 A[Catch:{ all -> 0x0192 }] */
    /* JADX WARNING: Removed duplicated region for block: B:80:0x018a A[SYNTHETIC, Splitter:B:80:0x018a] */
    /* JADX WARNING: Removed duplicated region for block: B:85:0x0195 A[SYNTHETIC, Splitter:B:85:0x0195] */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private void maybeRunAllCommandsNow(int r34) {
        /*
            r33 = this;
            java.lang.Object r28 = lock
            monitor-enter(r28)
            r27 = 0
            r0 = r27
            r1 = r33
            r1.unprocessedCommandsExist = r0     // Catch:{ all -> 0x0028 }
            r0 = r33
            boolean r0 = r0.connected     // Catch:{ all -> 0x0028 }
            r27 = r0
            if (r27 != 0) goto L_0x0015
            monitor-exit(r28)     // Catch:{ all -> 0x0028 }
        L_0x0014:
            return
        L_0x0015:
            r0 = r33
            java.io.File r0 = r0.cachePath     // Catch:{ all -> 0x0028 }
            r27 = r0
            java.lang.String[] r13 = r27.list()     // Catch:{ all -> 0x0028 }
            if (r13 == 0) goto L_0x0026
            int r0 = r13.length     // Catch:{ all -> 0x0028 }
            r27 = r0
            if (r27 != 0) goto L_0x002b
        L_0x0026:
            monitor-exit(r28)     // Catch:{ all -> 0x0028 }
            goto L_0x0014
        L_0x0028:
            r27 = move-exception
            monitor-exit(r28)     // Catch:{ all -> 0x0028 }
            throw r27
        L_0x002b:
            java.util.Arrays.sort(r13)     // Catch:{ all -> 0x0028 }
            r4 = r13
            int r0 = r4.length     // Catch:{ all -> 0x0028 }
            r20 = r0
            r14 = 0
        L_0x0033:
            r0 = r20
            if (r14 >= r0) goto L_0x02fd
            r12 = r4[r14]     // Catch:{ all -> 0x0028 }
            java.io.File r11 = new java.io.File     // Catch:{ all -> 0x0028 }
            r0 = r33
            java.io.File r0 = r0.cachePath     // Catch:{ all -> 0x0028 }
            r27 = r0
            r0 = r27
            r11.<init>(r0, r12)     // Catch:{ all -> 0x0028 }
            r18 = 0
            r16 = 0
            java.io.BufferedInputStream r17 = new java.io.BufferedInputStream     // Catch:{ FileNotFoundException -> 0x0318, IOException -> 0x0134, JSONException -> 0x0163 }
            java.io.FileInputStream r27 = new java.io.FileInputStream     // Catch:{ FileNotFoundException -> 0x0318, IOException -> 0x0134, JSONException -> 0x0163 }
            r0 = r27
            r0.<init>(r11)     // Catch:{ FileNotFoundException -> 0x0318, IOException -> 0x0134, JSONException -> 0x0163 }
            r0 = r17
            r1 = r27
            r0.<init>(r1)     // Catch:{ FileNotFoundException -> 0x0318, IOException -> 0x0134, JSONException -> 0x0163 }
            java.io.ByteArrayOutputStream r5 = new java.io.ByteArrayOutputStream     // Catch:{ FileNotFoundException -> 0x007d, IOException -> 0x0313, JSONException -> 0x030e, all -> 0x0309 }
            r5.<init>()     // Catch:{ FileNotFoundException -> 0x007d, IOException -> 0x0313, JSONException -> 0x030e, all -> 0x0309 }
            r27 = 1024(0x400, float:1.435E-42)
            r0 = r27
            byte[] r0 = new byte[r0]     // Catch:{ FileNotFoundException -> 0x007d, IOException -> 0x0313, JSONException -> 0x030e, all -> 0x0309 }
            r24 = r0
        L_0x0067:
            r0 = r17
            r1 = r24
            int r22 = r0.read(r1)     // Catch:{ FileNotFoundException -> 0x007d, IOException -> 0x0313, JSONException -> 0x030e, all -> 0x0309 }
            if (r22 <= 0) goto L_0x00a7
            r27 = 0
            r0 = r24
            r1 = r27
            r2 = r22
            r5.write(r0, r1, r2)     // Catch:{ FileNotFoundException -> 0x007d, IOException -> 0x0313, JSONException -> 0x030e, all -> 0x0309 }
            goto L_0x0067
        L_0x007d:
            r10 = move-exception
            r16 = r17
        L_0x0080:
            r27 = 6
            int r29 = com.parse.Parse.getLogLevel()     // Catch:{ all -> 0x0192 }
            r0 = r27
            r1 = r29
            if (r0 < r1) goto L_0x009f
            r0 = r33
            java.util.logging.Logger r0 = r0.log     // Catch:{ all -> 0x0192 }
            r27 = r0
            java.util.logging.Level r29 = java.util.logging.Level.SEVERE     // Catch:{ all -> 0x0192 }
            java.lang.String r30 = "File disappeared from cache while being read."
            r0 = r27
            r1 = r29
            r2 = r30
            r0.log(r1, r2, r10)     // Catch:{ all -> 0x0192 }
        L_0x009f:
            if (r16 == 0) goto L_0x00a4
            r16.close()     // Catch:{ IOException -> 0x0303 }
        L_0x00a4:
            int r14 = r14 + 1
            goto L_0x0033
        L_0x00a7:
            org.json.JSONObject r19 = new org.json.JSONObject     // Catch:{ FileNotFoundException -> 0x007d, IOException -> 0x0313, JSONException -> 0x030e, all -> 0x0309 }
            java.lang.String r27 = "UTF-8"
            r0 = r27
            java.lang.String r27 = r5.toString(r0)     // Catch:{ FileNotFoundException -> 0x007d, IOException -> 0x0313, JSONException -> 0x030e, all -> 0x0309 }
            r0 = r19
            r1 = r27
            r0.<init>(r1)     // Catch:{ FileNotFoundException -> 0x007d, IOException -> 0x0313, JSONException -> 0x030e, all -> 0x0309 }
            if (r17 == 0) goto L_0x00bd
            r17.close()     // Catch:{ IOException -> 0x0300 }
        L_0x00bd:
            r6 = 0
            r0 = r33
            java.util.HashMap<java.io.File, com.parse.Task$TaskCompletionSource<>> r0 = r0.pendingTasks     // Catch:{ all -> 0x0028 }
            r27 = r0
            r0 = r27
            boolean r27 = r0.containsKey(r11)     // Catch:{ all -> 0x0028 }
            if (r27 == 0) goto L_0x0199
            r0 = r33
            java.util.HashMap<java.io.File, com.parse.Task$TaskCompletionSource<>> r0 = r0.pendingTasks     // Catch:{ all -> 0x0028 }
            r27 = r0
            r0 = r27
            java.lang.Object r27 = r0.get(r11)     // Catch:{ all -> 0x0028 }
            com.parse.Task$TaskCompletionSource r27 = (com.parse.Task.TaskCompletionSource) r27     // Catch:{ all -> 0x0028 }
            r23 = r27
        L_0x00dc:
            com.parse.ParseCommand r6 = new com.parse.ParseCommand     // Catch:{ JSONException -> 0x019d }
            r0 = r19
            r6.<init>(r0)     // Catch:{ JSONException -> 0x019d }
            java.lang.String r21 = r6.getLocalId()     // Catch:{ ParseException -> 0x01c8 }
            com.parse.Task r27 = r6.executeAsync()     // Catch:{ ParseException -> 0x01c8 }
            com.parse.ParseCommandCache$4 r29 = new com.parse.ParseCommandCache$4     // Catch:{ ParseException -> 0x01c8 }
            r0 = r29
            r1 = r33
            r2 = r23
            r3 = r21
            r0.<init>(r2, r3)     // Catch:{ ParseException -> 0x01c8 }
            r0 = r27
            r1 = r29
            com.parse.Task r7 = r0.onSuccess(r1)     // Catch:{ ParseException -> 0x01c8 }
            r0 = r33
            r0.waitForTaskWithoutLock(r7)     // Catch:{ ParseException -> 0x01c8 }
            if (r23 == 0) goto L_0x0112
            com.parse.Task r27 = r23.getTask()     // Catch:{ ParseException -> 0x01c8 }
            r0 = r33
            r1 = r27
            r0.waitForTaskWithoutLock(r1)     // Catch:{ ParseException -> 0x01c8 }
        L_0x0112:
            r0 = r33
            r0.removeFile(r11)     // Catch:{ ParseException -> 0x01c8 }
            r0 = r33
            com.parse.ParseCommandCache$TestHelper r0 = r0.testHelper     // Catch:{ ParseException -> 0x01c8 }
            r27 = r0
            if (r27 == 0) goto L_0x012e
            r0 = r33
            com.parse.ParseCommandCache$TestHelper r0 = r0.testHelper     // Catch:{ ParseException -> 0x01c8 }
            r27 = r0
            r29 = 1
            r0 = r27
            r1 = r29
            r0.notify(r1)     // Catch:{ ParseException -> 0x01c8 }
        L_0x012e:
            r16 = r17
            r18 = r19
            goto L_0x00a4
        L_0x0134:
            r10 = move-exception
        L_0x0135:
            r27 = 6
            int r29 = com.parse.Parse.getLogLevel()     // Catch:{ all -> 0x0192 }
            r0 = r27
            r1 = r29
            if (r0 < r1) goto L_0x0154
            r0 = r33
            java.util.logging.Logger r0 = r0.log     // Catch:{ all -> 0x0192 }
            r27 = r0
            java.util.logging.Level r29 = java.util.logging.Level.SEVERE     // Catch:{ all -> 0x0192 }
            java.lang.String r30 = "Unable to read contents of file in cache."
            r0 = r27
            r1 = r29
            r2 = r30
            r0.log(r1, r2, r10)     // Catch:{ all -> 0x0192 }
        L_0x0154:
            r0 = r33
            r0.removeFile(r11)     // Catch:{ all -> 0x0192 }
            if (r16 == 0) goto L_0x00a4
            r16.close()     // Catch:{ IOException -> 0x0160 }
            goto L_0x00a4
        L_0x0160:
            r27 = move-exception
            goto L_0x00a4
        L_0x0163:
            r10 = move-exception
        L_0x0164:
            r27 = 6
            int r29 = com.parse.Parse.getLogLevel()     // Catch:{ all -> 0x0192 }
            r0 = r27
            r1 = r29
            if (r0 < r1) goto L_0x0183
            r0 = r33
            java.util.logging.Logger r0 = r0.log     // Catch:{ all -> 0x0192 }
            r27 = r0
            java.util.logging.Level r29 = java.util.logging.Level.SEVERE     // Catch:{ all -> 0x0192 }
            java.lang.String r30 = "Error parsing JSON found in cache."
            r0 = r27
            r1 = r29
            r2 = r30
            r0.log(r1, r2, r10)     // Catch:{ all -> 0x0192 }
        L_0x0183:
            r0 = r33
            r0.removeFile(r11)     // Catch:{ all -> 0x0192 }
            if (r16 == 0) goto L_0x00a4
            r16.close()     // Catch:{ IOException -> 0x018f }
            goto L_0x00a4
        L_0x018f:
            r27 = move-exception
            goto L_0x00a4
        L_0x0192:
            r27 = move-exception
        L_0x0193:
            if (r16 == 0) goto L_0x0198
            r16.close()     // Catch:{ IOException -> 0x0306 }
        L_0x0198:
            throw r27     // Catch:{ all -> 0x0028 }
        L_0x0199:
            r23 = 0
            goto L_0x00dc
        L_0x019d:
            r10 = move-exception
            r27 = 6
            int r29 = com.parse.Parse.getLogLevel()     // Catch:{ all -> 0x0028 }
            r0 = r27
            r1 = r29
            if (r0 < r1) goto L_0x01bd
            r0 = r33
            java.util.logging.Logger r0 = r0.log     // Catch:{ all -> 0x0028 }
            r27 = r0
            java.util.logging.Level r29 = java.util.logging.Level.SEVERE     // Catch:{ all -> 0x0028 }
            java.lang.String r30 = "Unable to create ParseCommand from JSON."
            r0 = r27
            r1 = r29
            r2 = r30
            r0.log(r1, r2, r10)     // Catch:{ all -> 0x0028 }
        L_0x01bd:
            r0 = r33
            r0.removeFile(r11)     // Catch:{ all -> 0x0028 }
            r16 = r17
            r18 = r19
            goto L_0x00a4
        L_0x01c8:
            r10 = move-exception
            int r27 = r10.getCode()     // Catch:{ all -> 0x0028 }
            r29 = 100
            r0 = r27
            r1 = r29
            if (r0 != r1) goto L_0x02bc
            if (r34 <= 0) goto L_0x02f7
            r27 = 4
            int r29 = com.parse.Parse.getLogLevel()     // Catch:{ all -> 0x0028 }
            r0 = r27
            r1 = r29
            if (r0 < r1) goto L_0x021d
            r0 = r33
            java.util.logging.Logger r0 = r0.log     // Catch:{ all -> 0x0028 }
            r27 = r0
            java.lang.StringBuilder r29 = new java.lang.StringBuilder     // Catch:{ all -> 0x0028 }
            r29.<init>()     // Catch:{ all -> 0x0028 }
            java.lang.String r30 = "Network timeout in command cache. Waiting for "
            java.lang.StringBuilder r29 = r29.append(r30)     // Catch:{ all -> 0x0028 }
            r0 = r33
            double r0 = r0.timeoutRetryWaitSeconds     // Catch:{ all -> 0x0028 }
            r30 = r0
            java.lang.StringBuilder r29 = r29.append(r30)     // Catch:{ all -> 0x0028 }
            java.lang.String r30 = " seconds and then retrying "
            java.lang.StringBuilder r29 = r29.append(r30)     // Catch:{ all -> 0x0028 }
            r0 = r29
            r1 = r34
            java.lang.StringBuilder r29 = r0.append(r1)     // Catch:{ all -> 0x0028 }
            java.lang.String r30 = " times."
            java.lang.StringBuilder r29 = r29.append(r30)     // Catch:{ all -> 0x0028 }
            java.lang.String r29 = r29.toString()     // Catch:{ all -> 0x0028 }
            r0 = r27
            r1 = r29
            r0.info(r1)     // Catch:{ all -> 0x0028 }
        L_0x021d:
            long r8 = java.lang.System.currentTimeMillis()     // Catch:{ all -> 0x0028 }
            r0 = r33
            double r0 = r0.timeoutRetryWaitSeconds     // Catch:{ all -> 0x0028 }
            r29 = r0
            r31 = 4652007308841189376(0x408f400000000000, double:1000.0)
            double r29 = r29 * r31
            r0 = r29
            long r0 = (long) r0     // Catch:{ all -> 0x0028 }
            r29 = r0
            long r25 = r8 + r29
        L_0x0235:
            int r27 = (r8 > r25 ? 1 : (r8 == r25 ? 0 : -1))
            if (r27 >= 0) goto L_0x02ad
            r0 = r33
            boolean r0 = r0.connected     // Catch:{ all -> 0x0028 }
            r27 = r0
            if (r27 == 0) goto L_0x0249
            r0 = r33
            boolean r0 = r0.shouldStop     // Catch:{ all -> 0x0028 }
            r27 = r0
            if (r27 == 0) goto L_0x0267
        L_0x0249:
            r27 = 4
            int r29 = com.parse.Parse.getLogLevel()     // Catch:{ all -> 0x0028 }
            r0 = r27
            r1 = r29
            if (r0 < r1) goto L_0x0264
            r0 = r33
            java.util.logging.Logger r0 = r0.log     // Catch:{ all -> 0x0028 }
            r27 = r0
            java.lang.String r29 = "Aborting wait because runEventually thread should stop."
            r0 = r27
            r1 = r29
            r0.info(r1)     // Catch:{ all -> 0x0028 }
        L_0x0264:
            monitor-exit(r28)     // Catch:{ all -> 0x0028 }
            goto L_0x0014
        L_0x0267:
            java.lang.Object r27 = lock     // Catch:{ InterruptedException -> 0x02a3 }
            long r29 = r25 - r8
            r0 = r27
            r1 = r29
            r0.wait(r1)     // Catch:{ InterruptedException -> 0x02a3 }
        L_0x0272:
            long r8 = java.lang.System.currentTimeMillis()     // Catch:{ all -> 0x0028 }
            r0 = r33
            double r0 = r0.timeoutRetryWaitSeconds     // Catch:{ all -> 0x0028 }
            r29 = r0
            r31 = 4652007308841189376(0x408f400000000000, double:1000.0)
            double r29 = r29 * r31
            r0 = r29
            long r0 = (long) r0     // Catch:{ all -> 0x0028 }
            r29 = r0
            long r29 = r25 - r29
            int r27 = (r8 > r29 ? 1 : (r8 == r29 ? 0 : -1))
            if (r27 >= 0) goto L_0x0235
            r0 = r33
            double r0 = r0.timeoutRetryWaitSeconds     // Catch:{ all -> 0x0028 }
            r29 = r0
            r31 = 4652007308841189376(0x408f400000000000, double:1000.0)
            double r29 = r29 * r31
            r0 = r29
            long r0 = (long) r0     // Catch:{ all -> 0x0028 }
            r29 = r0
            long r8 = r25 - r29
            goto L_0x0235
        L_0x02a3:
            r15 = move-exception
            r27 = 1
            r0 = r27
            r1 = r33
            r1.shouldStop = r0     // Catch:{ all -> 0x0028 }
            goto L_0x0272
        L_0x02ad:
            int r27 = r34 + -1
            r0 = r33
            r1 = r27
            r0.maybeRunAllCommandsNow(r1)     // Catch:{ all -> 0x0028 }
            r16 = r17
            r18 = r19
            goto L_0x00a4
        L_0x02bc:
            r27 = 6
            int r29 = com.parse.Parse.getLogLevel()     // Catch:{ all -> 0x0028 }
            r0 = r27
            r1 = r29
            if (r0 < r1) goto L_0x02db
            r0 = r33
            java.util.logging.Logger r0 = r0.log     // Catch:{ all -> 0x0028 }
            r27 = r0
            java.util.logging.Level r29 = java.util.logging.Level.SEVERE     // Catch:{ all -> 0x0028 }
            java.lang.String r30 = "Failed to run command."
            r0 = r27
            r1 = r29
            r2 = r30
            r0.log(r1, r2, r10)     // Catch:{ all -> 0x0028 }
        L_0x02db:
            r0 = r33
            r0.removeFile(r11)     // Catch:{ all -> 0x0028 }
            r0 = r33
            com.parse.ParseCommandCache$TestHelper r0 = r0.testHelper     // Catch:{ all -> 0x0028 }
            r27 = r0
            if (r27 == 0) goto L_0x02f7
            r0 = r33
            com.parse.ParseCommandCache$TestHelper r0 = r0.testHelper     // Catch:{ all -> 0x0028 }
            r27 = r0
            r29 = 2
            r0 = r27
            r1 = r29
            r0.notify(r1)     // Catch:{ all -> 0x0028 }
        L_0x02f7:
            r16 = r17
            r18 = r19
            goto L_0x00a4
        L_0x02fd:
            monitor-exit(r28)     // Catch:{ all -> 0x0028 }
            goto L_0x0014
        L_0x0300:
            r27 = move-exception
            goto L_0x00bd
        L_0x0303:
            r27 = move-exception
            goto L_0x00a4
        L_0x0306:
            r29 = move-exception
            goto L_0x0198
        L_0x0309:
            r27 = move-exception
            r16 = r17
            goto L_0x0193
        L_0x030e:
            r10 = move-exception
            r16 = r17
            goto L_0x0164
        L_0x0313:
            r10 = move-exception
            r16 = r17
            goto L_0x0135
        L_0x0318:
            r10 = move-exception
            goto L_0x0080
        */
        throw new UnsupportedOperationException("Method not decompiled: com.parse.ParseCommandCache.maybeRunAllCommandsNow(int):void");
    }

    /* access modifiers changed from: private */
    /* JADX WARNING: Code restructure failed: missing block: B:11:0x0022, code lost:
        r4 = lock;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:12:0x0025, code lost:
        monitor-enter(r4);
     */
    /* JADX WARNING: Code restructure failed: missing block: B:15:0x0028, code lost:
        if (r9.shouldStop != false) goto L_0x0056;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:17:0x002e, code lost:
        if (java.lang.Thread.interrupted() != false) goto L_0x0056;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:18:0x0030, code lost:
        r1 = true;
     */
    /* JADX WARNING: Code restructure failed: missing block: B:19:0x0031, code lost:
        monitor-exit(r4);
     */
    /* JADX WARNING: Code restructure failed: missing block: B:43:0x0056, code lost:
        r1 = false;
     */
    /* JADX WARNING: Removed duplicated region for block: B:33:0x004d A[Catch:{ Exception -> 0x0060, all -> 0x007b }] */
    /* JADX WARNING: Removed duplicated region for block: B:63:0x0077 A[Catch:{ Exception -> 0x0060, all -> 0x007b }] */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void runLoop() {
        /*
            r9 = this;
            r8 = 4
            r3 = 0
            r2 = 1
            int r4 = com.parse.Parse.getLogLevel()
            if (r8 < r4) goto L_0x0010
            java.util.logging.Logger r4 = r9.log
            java.lang.String r5 = "Parse command cache has started processing queued commands."
            r4.info(r5)
        L_0x0010:
            java.lang.Object r4 = r9.runningLock
            monitor-enter(r4)
            boolean r5 = r9.running     // Catch:{ all -> 0x0053 }
            if (r5 == 0) goto L_0x0019
            monitor-exit(r4)     // Catch:{ all -> 0x0053 }
        L_0x0018:
            return
        L_0x0019:
            r5 = 1
            r9.running = r5     // Catch:{ all -> 0x0053 }
            java.lang.Object r5 = r9.runningLock     // Catch:{ all -> 0x0053 }
            r5.notifyAll()     // Catch:{ all -> 0x0053 }
            monitor-exit(r4)     // Catch:{ all -> 0x0053 }
            r1 = 0
            java.lang.Object r4 = lock
            monitor-enter(r4)
            boolean r5 = r9.shouldStop     // Catch:{ all -> 0x0058 }
            if (r5 != 0) goto L_0x0056
            boolean r5 = java.lang.Thread.interrupted()     // Catch:{ all -> 0x0058 }
            if (r5 != 0) goto L_0x0056
            r1 = r2
        L_0x0031:
            monitor-exit(r4)     // Catch:{ all -> 0x0058 }
        L_0x0032:
            if (r1 == 0) goto L_0x0084
            java.lang.Object r5 = lock
            monitor-enter(r5)
            int r4 = r9.timeoutMaxRetries     // Catch:{ Exception -> 0x0060 }
            r9.maybeRunAllCommandsNow(r4)     // Catch:{ Exception -> 0x0060 }
            boolean r4 = r9.shouldStop     // Catch:{ Exception -> 0x0060 }
            if (r4 != 0) goto L_0x0049
            boolean r4 = r9.unprocessedCommandsExist     // Catch:{ InterruptedException -> 0x005b }
            if (r4 != 0) goto L_0x0049
            java.lang.Object r4 = lock     // Catch:{ InterruptedException -> 0x005b }
            r4.wait()     // Catch:{ InterruptedException -> 0x005b }
        L_0x0049:
            boolean r4 = r9.shouldStop     // Catch:{ all -> 0x0050 }
            if (r4 != 0) goto L_0x0077
            r1 = r2
        L_0x004e:
            monitor-exit(r5)     // Catch:{ all -> 0x0050 }
            goto L_0x0032
        L_0x0050:
            r2 = move-exception
            monitor-exit(r5)     // Catch:{ all -> 0x0050 }
            throw r2
        L_0x0053:
            r2 = move-exception
            monitor-exit(r4)     // Catch:{ all -> 0x0053 }
            throw r2
        L_0x0056:
            r1 = r3
            goto L_0x0031
        L_0x0058:
            r2 = move-exception
            monitor-exit(r4)     // Catch:{ all -> 0x0058 }
            throw r2
        L_0x005b:
            r0 = move-exception
            r4 = 1
            r9.shouldStop = r4     // Catch:{ Exception -> 0x0060 }
            goto L_0x0049
        L_0x0060:
            r0 = move-exception
            r4 = 6
            int r6 = com.parse.Parse.getLogLevel()     // Catch:{ all -> 0x007b }
            if (r4 < r6) goto L_0x0071
            java.util.logging.Logger r4 = r9.log     // Catch:{ all -> 0x007b }
            java.util.logging.Level r6 = java.util.logging.Level.SEVERE     // Catch:{ all -> 0x007b }
            java.lang.String r7 = "saveEventually thread had an error."
            r4.log(r6, r7, r0)     // Catch:{ all -> 0x007b }
        L_0x0071:
            boolean r4 = r9.shouldStop     // Catch:{ all -> 0x0050 }
            if (r4 != 0) goto L_0x0079
            r1 = r2
        L_0x0076:
            goto L_0x004e
        L_0x0077:
            r1 = r3
            goto L_0x004e
        L_0x0079:
            r1 = r3
            goto L_0x0076
        L_0x007b:
            r4 = move-exception
            boolean r6 = r9.shouldStop     // Catch:{ all -> 0x0050 }
            if (r6 != 0) goto L_0x0082
            r1 = r2
        L_0x0081:
            throw r4     // Catch:{ all -> 0x0050 }
        L_0x0082:
            r1 = r3
            goto L_0x0081
        L_0x0084:
            java.lang.Object r3 = r9.runningLock
            monitor-enter(r3)
            r2 = 0
            r9.running = r2     // Catch:{ all -> 0x009f }
            java.lang.Object r2 = r9.runningLock     // Catch:{ all -> 0x009f }
            r2.notifyAll()     // Catch:{ all -> 0x009f }
            monitor-exit(r3)     // Catch:{ all -> 0x009f }
            int r2 = com.parse.Parse.getLogLevel()
            if (r8 < r2) goto L_0x0018
            java.util.logging.Logger r2 = r9.log
            java.lang.String r3 = "saveEventually thread has stopped processing commands."
            r2.info(r3)
            goto L_0x0018
        L_0x009f:
            r2 = move-exception
            monitor-exit(r3)     // Catch:{ all -> 0x009f }
            throw r2
        */
        throw new UnsupportedOperationException("Method not decompiled: com.parse.ParseCommandCache.runLoop():void");
    }

    public TestHelper getTestHelper() {
        if (this.testHelper == null) {
            this.testHelper = new TestHelper();
        }
        return this.testHelper;
    }
}
