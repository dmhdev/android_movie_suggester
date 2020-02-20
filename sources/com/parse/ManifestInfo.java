package com.parse;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Build.VERSION;
import java.io.File;
import java.util.List;

class ManifestInfo {
    private static final String TAG = "com.parse.ManifestInfo";
    private static String displayName = null;
    private static int iconId = 0;
    private static long lastModified = -1;
    private static Object lock = new Object();
    private static PushType pushType;
    private static int versionCode = -1;

    enum ManifestCheckResult {
        HAS_ALL_DECLARATIONS,
        MISSING_OPTIONAL_DECLARATIONS,
        MISSING_REQUIRED_DECLARATIONS
    }

    ManifestInfo() {
    }

    public static long getLastModified() {
        synchronized (lock) {
            if (lastModified == -1) {
                lastModified = new File(getApplicationInfo().sourceDir).lastModified();
            }
        }
        return lastModified;
    }

    public static int getVersionCode() {
        synchronized (lock) {
            if (versionCode == -1) {
                try {
                    versionCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
                } catch (NameNotFoundException e) {
                    Parse.logE(TAG, "Couldn't find info about own package", e);
                }
            }
        }
        return versionCode;
    }

    public static String getDisplayName() {
        synchronized (lock) {
            if (displayName == null) {
                displayName = getPackageManager().getApplicationLabel(getApplicationInfo()).toString();
            }
        }
        return displayName;
    }

    public static int getIconId() {
        synchronized (lock) {
            if (iconId == 0) {
                iconId = getApplicationInfo().icon;
            }
        }
        return iconId;
    }

    static void setPushType(PushType newPushType) {
        synchronized (lock) {
            pushType = newPushType;
        }
    }

    public static PushType getPushType() {
        synchronized (lock) {
            if (pushType == null) {
                boolean deviceSupportsGcm = deviceSupportsGcm();
                boolean hasAnyGcmSpecificDeclaration = hasAnyGcmSpecificDeclaration();
                ManifestCheckResult gcmSupportLevel = gcmSupportLevel();
                ManifestCheckResult ppnsSupportLevel = ppnsSupportLevel();
                if (!deviceSupportsGcm || gcmSupportLevel == ManifestCheckResult.MISSING_REQUIRED_DECLARATIONS) {
                    if (!hasAnyGcmSpecificDeclaration || !deviceSupportsGcm) {
                        if (ppnsSupportLevel != ManifestCheckResult.MISSING_REQUIRED_DECLARATIONS) {
                            pushType = PushType.PPNS;
                        }
                    }
                    pushType = PushType.NONE;
                } else {
                    pushType = PushType.GCM;
                }
                if (Parse.getLogLevel() <= 5) {
                    if (pushType == PushType.GCM && gcmSupportLevel == ManifestCheckResult.MISSING_OPTIONAL_DECLARATIONS) {
                        Parse.logW(TAG, "Using GCM for push, but the app manifest is missing some optional declarations that should be added for maximum reliability. Please " + getGcmManifestMessage());
                    } else if (pushType == PushType.PPNS && ppnsSupportLevel == ManifestCheckResult.MISSING_OPTIONAL_DECLARATIONS) {
                        Parse.logW(TAG, "Using PPNS for push, but the app manifest is missing some optional declarations that should be added for maximum reliability. Please " + getPpnsManifestMessage());
                    }
                }
                if (Parse.getLogLevel() <= 6 && pushType == PushType.NONE && hasAnyGcmSpecificDeclaration) {
                    if (!deviceSupportsGcm) {
                        Parse.logE(TAG, "Cannot use GCM for push on this device because Google Play Services is not installed. Install Google Play Service from the Play Store, or enable PPNS as a fallback push service. To enable PPNS as a fallback push service on devices without Google Play support, please " + getPpnsManifestMessage());
                    }
                    if (gcmSupportLevel != ManifestCheckResult.HAS_ALL_DECLARATIONS) {
                        Parse.logE(TAG, "Cannot use GCM for push because the app manifest is missing some required declarations. Please " + getGcmManifestMessage());
                    }
                }
                if (Parse.getLogLevel() <= 2) {
                    Parse.logV(TAG, "Using " + pushType + " for push.");
                }
            }
        }
        return pushType;
    }

    public static String getNonePushTypeLogMessage() {
        String str;
        synchronized (lock) {
            if (pushType == PushType.NONE) {
                str = "Push is not configured for this app because the app manifest is missing required declarations. Please add the following declarations to your app manifest to support either GCM or PPNS for push (or both). To enable GCM support, please " + getGcmManifestMessage() + "To enable PPNS support, please " + getPpnsManifestMessage();
            } else {
                str = "";
            }
        }
        return str;
    }

    private static Context getContext() {
        return Parse.getApplicationContext();
    }

    private static String getPackageName() {
        return getContext().getPackageName();
    }

    private static PackageManager getPackageManager() {
        return getContext().getPackageManager();
    }

    private static ApplicationInfo getApplicationInfo() {
        return getContext().getApplicationInfo();
    }

    private static PackageInfo getPackageInfo(String name) {
        PackageInfo info = null;
        try {
            return getPackageManager().getPackageInfo(name, 0);
        } catch (NameNotFoundException e) {
            return info;
        }
    }

    private static ServiceInfo getServiceInfo(Class<? extends Service> clazz) {
        ServiceInfo info = null;
        try {
            return getPackageManager().getServiceInfo(new ComponentName(getContext(), clazz), 0);
        } catch (NameNotFoundException e) {
            return info;
        }
    }

    private static ActivityInfo getReceiverInfo(Class<? extends BroadcastReceiver> clazz) {
        ActivityInfo info = null;
        try {
            return getPackageManager().getReceiverInfo(new ComponentName(getContext(), clazz), 0);
        } catch (NameNotFoundException e) {
            return info;
        }
    }

    private static boolean hasPermissions(String... permissions) {
        for (String permission : permissions) {
            if (getPackageManager().checkPermission(permission, getPackageName()) != 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean checkResolveInfo(Class<? extends BroadcastReceiver> clazz, List<ResolveInfo> infoList) {
        for (ResolveInfo info : infoList) {
            if (info.activityInfo != null && clazz.getCanonicalName().equals(info.activityInfo.name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean checkReceiver(Class<? extends BroadcastReceiver> clazz, String permission, Intent[] intents) {
        ActivityInfo receiver = getReceiverInfo(clazz);
        if (receiver == null) {
            return false;
        }
        if (permission != null && !permission.equals(receiver.permission)) {
            return false;
        }
        for (Intent intent : intents) {
            List<ResolveInfo> receivers = getPackageManager().queryBroadcastReceivers(intent, 0);
            if (receivers.isEmpty() || !checkResolveInfo(clazz, receivers)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasAnyGcmSpecificDeclaration() {
        if (!hasPermissions("com.google.android.c2dm.permission.RECEIVE")) {
            if (!hasPermissions(getPackageName() + ".permission.C2D_MESSAGE") && getReceiverInfo(GcmBroadcastReceiver.class) == null) {
                return false;
            }
        }
        return true;
    }

    private static boolean deviceSupportsGcm() {
        return VERSION.SDK_INT >= 8 && getPackageInfo("com.google.android.gsf") != null;
    }

    private static ManifestCheckResult gcmSupportLevel() {
        if (getServiceInfo(PushService.class) == null) {
            return ManifestCheckResult.MISSING_REQUIRED_DECLARATIONS;
        }
        if (!hasPermissions("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE", "android.permission.WAKE_LOCK", "android.permission.GET_ACCOUNTS", "com.google.android.c2dm.permission.RECEIVE", getPackageName() + ".permission.C2D_MESSAGE")) {
            return ManifestCheckResult.MISSING_REQUIRED_DECLARATIONS;
        }
        String packageName = getPackageName();
        Intent[] intents = {new Intent(PushRouter.GCM_RECEIVE_ACTION).setPackage(packageName).addCategory(packageName), new Intent(GcmRegistrar.REGISTER_RESPONSE_ACTION).setPackage(packageName).addCategory(packageName)};
        if (!checkReceiver(GcmBroadcastReceiver.class, "com.google.android.c2dm.permission.SEND", intents)) {
            return ManifestCheckResult.MISSING_REQUIRED_DECLARATIONS;
        }
        if (!hasPermissions("android.permission.VIBRATE")) {
            return ManifestCheckResult.MISSING_OPTIONAL_DECLARATIONS;
        }
        return ManifestCheckResult.HAS_ALL_DECLARATIONS;
    }

    private static ManifestCheckResult ppnsSupportLevel() {
        if (getServiceInfo(PushService.class) == null) {
            return ManifestCheckResult.MISSING_REQUIRED_DECLARATIONS;
        }
        if (!hasPermissions("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE", "android.permission.VIBRATE", "android.permission.WAKE_LOCK", "android.permission.RECEIVE_BOOT_COMPLETED")) {
            return ManifestCheckResult.MISSING_OPTIONAL_DECLARATIONS;
        }
        if (!checkReceiver(ParseBroadcastReceiver.class, null, new Intent[]{new Intent("android.intent.action.BOOT_COMPLETED").setPackage(getPackageName()), new Intent("android.intent.action.USER_PRESENT").setPackage(getPackageName())})) {
            return ManifestCheckResult.MISSING_OPTIONAL_DECLARATIONS;
        }
        return ManifestCheckResult.HAS_ALL_DECLARATIONS;
    }

    private static String getGcmManifestMessage() {
        String gcmPackagePermission = getPackageName() + ".permission.C2D_MESSAGE";
        return "make sure that these permissions are declared as children of the root <manifest> element:\n\n<uses-permission android:name=\"android.permission.INTERNET\" />\n<uses-permission android:name=\"android.permission.ACCESS_NETWORK_STATE\" />\n<uses-permission android:name=\"android.permission.VIBRATE\" />\n<uses-permission android:name=\"android.permission.WAKE_LOCK\" />\n<uses-permission android:name=\"android.permission.GET_ACCOUNTS\" />\n<uses-permission android:name=\"com.google.android.c2dm.permission.RECEIVE\" />\n<permission android:name=\"" + gcmPackagePermission + "\" " + "android:protectionLevel=\"signature\" />\n" + "<uses-permission android:name=\"" + gcmPackagePermission + "\" />\n" + "\n" + "Also, please make sure that these services and broadcast receivers are declared as " + "children of the <application> element:\n" + "\n" + "<service android:name=\"com.parse.PushService\" />\n" + "<receiver android:name=\"com.parse.GcmBroadcastReceiver\" " + "android:permission=\"com.google.android.c2dm.permission.SEND\">\n" + "  <intent-filter>\n" + "    <action android:name=\"com.google.android.c2dm.intent.RECEIVE\" />\n" + "    <action android:name=\"com.google.android.c2dm.intent.REGISTRATION\" />\n" + "    <category android:name=\"" + getPackageName() + "\" />\n" + "  </intent-filter>\n" + "</receiver>\n";
    }

    private static String getPpnsManifestMessage() {
        return "make sure that these permissions are declared as children of the root <manifest> element:\n\n<uses-permission android:name=\"android.permission.INTERNET\" />\n<uses-permission android:name=\"android.permission.ACCESS_NETWORK_STATE\" />\n<uses-permission android:name=\"android.permission.RECEIVE_BOOT_COMPLETED\" />\n<uses-permission android:name=\"android.permission.VIBRATE\" />\n<uses-permission android:name=\"android.permission.WAKE_LOCK\" />\n\nAlso, please make sure that these services and broadcast receivers are declared as children of the <application> element:\n\n<service android:name=\"com.parse.PushService\" />\n<receiver android:name=\"com.parse.ParseBroadcastReceiver\">\n  <intent-filter>\n    <action android:name=\"android.intent.action.BOOT_COMPLETED\" />\n    <action android:name=\"android.intent.action.USER_PRESENT\" />\n  </intent-filter>\n</receiver>\n";
    }
}
