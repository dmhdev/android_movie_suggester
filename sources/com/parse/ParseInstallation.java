package com.parse;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import org.json.JSONObject;

@ParseClassName("_Installation")
public class ParseInstallation extends ParseObject {
    static final String INSTALLATION_ID_LOCATION = "installationId";
    private static final String STORAGE_LOCATION = "currentInstallation";
    private static final String TAG = "com.parse.ParseInstallation";
    static ParseInstallation currentInstallation = null;
    static String installationId = null;
    private static final Object installationLock = new Object();
    private static final List<String> readonlyFields = Arrays.asList(new String[]{"deviceType", INSTALLATION_ID_LOCATION, "deviceToken", "pushType", "timeZone", "appVersion", "appName", "parseVersion", "deviceTokenLastModified", "appIdentifier"});

    /* access modifiers changed from: 0000 */
    public void setDefaultValues() {
        super.setDefaultValues();
        super.put("deviceType", "android");
        super.put(INSTALLATION_ID_LOCATION, getOrCreateCurrentInstallationId());
    }

    static boolean hasCurrentInstallation() {
        boolean hasCurrentInstallation;
        synchronized (installationLock) {
            hasCurrentInstallation = currentInstallation != null || new File(Parse.getParseDir(), STORAGE_LOCATION).exists();
        }
        return hasCurrentInstallation;
    }

    public static ParseInstallation getCurrentInstallation() {
        boolean deserializedInstallationFromDisk = false;
        synchronized (installationLock) {
            if (currentInstallation == null) {
                ParseObject installation = getFromDisk(Parse.applicationContext, STORAGE_LOCATION);
                if (installation == null) {
                    currentInstallation = (ParseInstallation) ParseObject.create(ParseInstallation.class);
                } else {
                    deserializedInstallationFromDisk = true;
                    currentInstallation = (ParseInstallation) installation;
                    Parse.logV(TAG, "Successfully deserialized Installation object");
                }
            }
        }
        if (deserializedInstallationFromDisk) {
            currentInstallation.maybeUpdateInstallationIdOnDisk();
        }
        return currentInstallation;
    }

    public static ParseQuery<ParseInstallation> getQuery() {
        return ParseQuery.getQuery(ParseInstallation.class);
    }

    /* JADX WARNING: Removed duplicated region for block: B:17:0x0034  */
    /* JADX WARNING: Removed duplicated region for block: B:23:0x004a A[SYNTHETIC, Splitter:B:23:0x004a] */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    static java.lang.String getOrCreateCurrentInstallationId() {
        /*
            java.lang.Object r6 = installationLock
            monitor-enter(r6)
            java.lang.String r5 = installationId     // Catch:{ all -> 0x0057 }
            if (r5 != 0) goto L_0x0030
            java.io.File r4 = new java.io.File     // Catch:{ all -> 0x0057 }
            java.io.File r5 = com.parse.Parse.getParseDir()     // Catch:{ all -> 0x0057 }
            java.lang.String r7 = "installationId"
            r4.<init>(r5, r7)     // Catch:{ all -> 0x0057 }
            r2 = 0
            java.io.RandomAccessFile r3 = new java.io.RandomAccessFile     // Catch:{ all -> 0x0047 }
            java.lang.String r5 = "r"
            r3.<init>(r4, r5)     // Catch:{ all -> 0x0047 }
            long r7 = r3.length()     // Catch:{ all -> 0x0069 }
            int r5 = (int) r7     // Catch:{ all -> 0x0069 }
            byte[] r0 = new byte[r5]     // Catch:{ all -> 0x0069 }
            r3.readFully(r0)     // Catch:{ all -> 0x0069 }
            java.lang.String r5 = new java.lang.String     // Catch:{ all -> 0x0069 }
            r5.<init>(r0)     // Catch:{ all -> 0x0069 }
            installationId = r5     // Catch:{ all -> 0x0069 }
            if (r3 == 0) goto L_0x0030
            r3.close()     // Catch:{ FileNotFoundException -> 0x0066, IOException -> 0x005a }
        L_0x0030:
            java.lang.String r5 = installationId     // Catch:{ all -> 0x0057 }
            if (r5 != 0) goto L_0x0043
            java.util.UUID r5 = java.util.UUID.randomUUID()     // Catch:{ all -> 0x0057 }
            java.lang.String r5 = r5.toString()     // Catch:{ all -> 0x0057 }
            installationId = r5     // Catch:{ all -> 0x0057 }
            java.lang.String r5 = installationId     // Catch:{ all -> 0x0057 }
            setCurrentInstallationId(r5)     // Catch:{ all -> 0x0057 }
        L_0x0043:
            monitor-exit(r6)     // Catch:{ all -> 0x0057 }
            java.lang.String r5 = installationId
            return r5
        L_0x0047:
            r5 = move-exception
        L_0x0048:
            if (r2 == 0) goto L_0x004d
            r2.close()     // Catch:{ FileNotFoundException -> 0x004e, IOException -> 0x0064 }
        L_0x004d:
            throw r5     // Catch:{ FileNotFoundException -> 0x004e, IOException -> 0x0064 }
        L_0x004e:
            r1 = move-exception
        L_0x004f:
            java.lang.String r5 = "com.parse.ParseInstallation"
            java.lang.String r7 = "Couldn't find existing installationId file. Creating one instead."
            com.parse.Parse.logI(r5, r7)     // Catch:{ all -> 0x0057 }
            goto L_0x0030
        L_0x0057:
            r5 = move-exception
            monitor-exit(r6)     // Catch:{ all -> 0x0057 }
            throw r5
        L_0x005a:
            r1 = move-exception
            r2 = r3
        L_0x005c:
            java.lang.String r5 = "com.parse.ParseInstallation"
            java.lang.String r7 = "Unexpected exception reading installation id from disk"
            com.parse.Parse.logE(r5, r7, r1)     // Catch:{ all -> 0x0057 }
            goto L_0x0030
        L_0x0064:
            r1 = move-exception
            goto L_0x005c
        L_0x0066:
            r1 = move-exception
            r2 = r3
            goto L_0x004f
        L_0x0069:
            r5 = move-exception
            r2 = r3
            goto L_0x0048
        */
        throw new UnsupportedOperationException("Method not decompiled: com.parse.ParseInstallation.getOrCreateCurrentInstallationId():java.lang.String");
    }

    /* JADX WARNING: Removed duplicated region for block: B:20:0x002b A[SYNTHETIC, Splitter:B:20:0x002b] */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    static void setCurrentInstallationId(java.lang.String r8) {
        /*
            java.lang.Object r5 = installationLock
            monitor-enter(r5)
            java.io.File r3 = new java.io.File     // Catch:{ all -> 0x0038 }
            java.io.File r4 = com.parse.Parse.getParseDir()     // Catch:{ all -> 0x0038 }
            java.lang.String r6 = "installationId"
            r3.<init>(r4, r6)     // Catch:{ all -> 0x0038 }
            r1 = 0
            java.io.RandomAccessFile r2 = new java.io.RandomAccessFile     // Catch:{ all -> 0x0028 }
            java.lang.String r4 = "rw"
            r2.<init>(r3, r4)     // Catch:{ all -> 0x0028 }
            r6 = 0
            r2.setLength(r6)     // Catch:{ all -> 0x003e }
            r2.writeBytes(r8)     // Catch:{ all -> 0x003e }
            if (r2 == 0) goto L_0x0023
            r2.close()     // Catch:{ IOException -> 0x003b }
        L_0x0023:
            r1 = r2
        L_0x0024:
            installationId = r8     // Catch:{ all -> 0x0038 }
            monitor-exit(r5)     // Catch:{ all -> 0x0038 }
            return
        L_0x0028:
            r4 = move-exception
        L_0x0029:
            if (r1 == 0) goto L_0x002e
            r1.close()     // Catch:{ IOException -> 0x002f }
        L_0x002e:
            throw r4     // Catch:{ IOException -> 0x002f }
        L_0x002f:
            r0 = move-exception
        L_0x0030:
            java.lang.String r4 = "com.parse.ParseInstallation"
            java.lang.String r6 = "Unexpected exception writing installation id to disk"
            com.parse.Parse.logE(r4, r6, r0)     // Catch:{ all -> 0x0038 }
            goto L_0x0024
        L_0x0038:
            r4 = move-exception
            monitor-exit(r5)     // Catch:{ all -> 0x0038 }
            throw r4
        L_0x003b:
            r0 = move-exception
            r1 = r2
            goto L_0x0030
        L_0x003e:
            r4 = move-exception
            r1 = r2
            goto L_0x0029
        */
        throw new UnsupportedOperationException("Method not decompiled: com.parse.ParseInstallation.setCurrentInstallationId(java.lang.String):void");
    }

    public String getInstallationId() {
        return getString(INSTALLATION_ID_LOCATION);
    }

    /* access modifiers changed from: 0000 */
    public void checkKeyIsMutable(String key) throws IllegalArgumentException {
        synchronized (this.mutex) {
            if (readonlyFields.contains(key)) {
                throw new IllegalArgumentException("Cannot change " + key + " property of an installation object.");
            }
        }
    }

    public void put(String key, Object value) throws IllegalArgumentException {
        synchronized (this.mutex) {
            checkKeyIsMutable(key);
            super.put(key, value);
        }
    }

    public void remove(String key) {
        synchronized (this.mutex) {
            checkKeyIsMutable(key);
            super.remove(key);
        }
    }

    /* access modifiers changed from: 0000 */
    public Task<Void> saveAsync(Task<Void> toAwait) {
        Task<Void> saveAsync;
        synchronized (this.mutex) {
            updateTimezone();
            updateVersionInfo();
            updateDeviceInfo();
            saveAsync = super.saveAsync(toAwait);
        }
        return saveAsync;
    }

    public void saveEventually(SaveCallback callback) {
        synchronized (this.mutex) {
            updateTimezone();
            updateVersionInfo();
            updateDeviceInfo();
            super.saveEventually(callback);
        }
    }

    /* access modifiers changed from: 0000 */
    public <T extends ParseObject> Task<T> fetchAsync(final Task<Void> toAwait) {
        Task<Void> result;
        Task<T> onSuccessTask;
        synchronized (this.mutex) {
            if (getObjectId() == null) {
                result = saveAsync(toAwait);
            } else {
                result = Task.forResult(null);
            }
            onSuccessTask = result.onSuccessTask(new Continuation<Void, Task<T>>() {
                public Task<T> then(Task<Void> task) throws Exception {
                    return ParseInstallation.super.fetchAsync(toAwait);
                }
            });
        }
        return onSuccessTask;
    }

    /* access modifiers changed from: 0000 */
    public void handleSaveResult(String op, JSONObject result, Map<String, ParseFieldOperation> operationsBeforeSave) {
        super.handleSaveResult(op, result, operationsBeforeSave);
        maybeFlushToDisk(this);
    }

    /* access modifiers changed from: 0000 */
    public void handleFetchResult(JSONObject result) {
        super.handleFetchResult(result);
        maybeFlushToDisk(this);
    }

    private void maybeUpdateInstallationIdOnDisk() {
        String installationIdInObject = getInstallationId();
        String installationIdOnDisk = getOrCreateCurrentInstallationId();
        if (!(installationIdInObject == null || installationIdInObject.length() == 0) && !installationIdInObject.equals(installationIdOnDisk)) {
            Parse.logW(TAG, "Will update installation id on disk: " + installationIdOnDisk + " because it does not match installation id in ParseInstallation: " + installationIdInObject);
            setCurrentInstallationId(installationIdInObject);
        }
    }

    private void updateTimezone() {
        String zone = TimeZone.getDefault().getID();
        if ((zone.indexOf(47) > 0 || zone.equals("GMT")) && !zone.equals(get("timeZone"))) {
            super.put("timeZone", zone);
        }
    }

    private void updateVersionInfo() {
        boolean z;
        boolean z2 = true;
        synchronized (this.mutex) {
            try {
                String packageName = Parse.applicationContext.getPackageName();
                PackageManager pm = Parse.applicationContext.getPackageManager();
                String appVersion = pm.getPackageInfo(packageName, 0).versionName;
                String appName = pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString();
                if (packageName != null && !packageName.equals(get("appIdentifier"))) {
                    super.put("appIdentifier", packageName);
                }
                if (appName != null && !appName.equals(get("appName"))) {
                    super.put("appName", appName);
                }
                if (appVersion != null) {
                    z = true;
                } else {
                    z = false;
                }
                if (appVersion.equals(get("appVersion"))) {
                    z2 = false;
                }
                if (z2 && z) {
                    super.put("appVersion", appVersion);
                }
            } catch (NameNotFoundException e) {
                Parse.logW(TAG, "Cannot load package info; will not be saved to installation");
            }
            if (!"1.4.3".equals(get("parseVersion"))) {
                super.put("parseVersion", "1.4.3");
            }
        }
    }

    private void updateDeviceInfo() {
        if (!has(INSTALLATION_ID_LOCATION)) {
            super.put(INSTALLATION_ID_LOCATION, getOrCreateCurrentInstallationId());
        }
        String deviceType = "android";
        if (!deviceType.equals(get("deviceType"))) {
            super.put("deviceType", deviceType);
        }
    }

    /* access modifiers changed from: 0000 */
    public PushType getPushType() {
        return PushType.fromString(super.getString("pushType"));
    }

    /* access modifiers changed from: 0000 */
    public void setPushType(PushType pushType) {
        if (pushType != null) {
            super.put("pushType", pushType.toString());
        }
    }

    /* access modifiers changed from: 0000 */
    public void removePushType() {
        super.remove("pushType");
    }

    /* access modifiers changed from: 0000 */
    public String getDeviceToken() {
        return super.getString("deviceToken");
    }

    /* access modifiers changed from: 0000 */
    public boolean isDeviceTokenStale() {
        return super.getLong("deviceTokenLastModified") != ManifestInfo.getLastModified();
    }

    /* access modifiers changed from: 0000 */
    public void setDeviceTokenLastModified(long lastModified) {
        super.put("deviceTokenLastModified", Long.valueOf(lastModified));
    }

    /* access modifiers changed from: 0000 */
    public void setDeviceToken(String deviceToken) {
        if (deviceToken != null && deviceToken.length() > 0) {
            super.put("deviceToken", deviceToken);
            super.put("deviceTokenLastModified", Long.valueOf(ManifestInfo.getLastModified()));
        }
    }

    /* access modifiers changed from: 0000 */
    public void removeDeviceToken() {
        super.remove("deviceToken");
        super.remove("deviceTokenLastModified");
    }

    private static void maybeFlushToDisk(ParseInstallation installation) {
        boolean isCurrentInstallation;
        synchronized (installationLock) {
            isCurrentInstallation = installation == currentInstallation;
        }
        if (isCurrentInstallation) {
            installation.saveToDisk(Parse.applicationContext, STORAGE_LOCATION);
            installation.maybeUpdateInstallationIdOnDisk();
        }
    }

    static void clearCurrentInstallationFromMemory() {
        synchronized (installationLock) {
            currentInstallation = null;
        }
    }

    static void clearCurrentInstallationFromDisk(Context context) {
        synchronized (installationLock) {
            currentInstallation = null;
            installationId = null;
            ParseObject.deleteDiskObject(context, STORAGE_LOCATION);
            ParseObject.deleteDiskObject(context, INSTALLATION_ID_LOCATION);
        }
    }

    /* access modifiers changed from: 0000 */
    public boolean needsDefaultACL() {
        return false;
    }
}
