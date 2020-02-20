package com.parse;

import android.content.Context;
import android.support.p000v4.view.accessibility.AccessibilityEventCompat;
import android.util.Log;
import com.parse.Task.TaskCompletionSource;
import com.parse.codec.binary.Base64;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SimpleTimeZone;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

public class Parse {
    public static final int LOG_LEVEL_DEBUG = 3;
    public static final int LOG_LEVEL_ERROR = 6;
    public static final int LOG_LEVEL_INFO = 4;
    public static final int LOG_LEVEL_NONE = Integer.MAX_VALUE;
    public static final int LOG_LEVEL_VERBOSE = 2;
    public static final int LOG_LEVEL_WARNING = 5;
    private static final Object SCHEDULED_EXECUTOR_LOCK = new Object();
    private static final String TAG = "com.parse.Parse";
    static Context applicationContext;
    static String applicationId;
    static String clientKey;
    static ParseCommandCache commandCache = null;
    private static final DateFormat dateFormat;
    static final Object lock = new Object();
    private static int logLevel = 6;
    static int maxKeyValueCacheBytes = AccessibilityEventCompat.TYPE_TOUCH_INTERACTION_END;
    static int maxKeyValueCacheFiles = 1000;
    static int maxParseFileSize = 10485760;
    private static ScheduledExecutorService scheduledExecutor;

    static {
        DateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        format.setTimeZone(new SimpleTimeZone(0, "GMT"));
        dateFormat = format;
    }

    private Parse() {
        throw new AssertionError();
    }

    public static void initialize(Context context, String applicationId2, String clientKey2) {
        ParseRequest.initialize(context);
        ParseObject.registerParseSubclasses();
        applicationId = applicationId2;
        clientKey = clientKey2;
        if (context != null) {
            applicationContext = context.getApplicationContext();
            checkCacheApplicationId();
            new Thread("Parse.initialize Disk Check & Starting Command Cache") {
                public void run() {
                    Parse.getCommandCache();
                }
            }.start();
        }
        ParseFieldOperations.registerDefaultDecoders();
        GcmRegistrar.updateAsync();
    }

    static Context getApplicationContext() {
        checkContext();
        return applicationContext;
    }

    public static void setLogLevel(int logLevel2) {
        logLevel = logLevel2;
    }

    public static int getLogLevel() {
        return logLevel;
    }

    private static void log(int messageLogLevel, String tag, String message, Throwable tr) {
        if (messageLogLevel < logLevel) {
            return;
        }
        if (tr == null) {
            Log.println(logLevel, tag, message);
        } else {
            Log.println(logLevel, tag, message + 10 + Log.getStackTraceString(tr));
        }
    }

    static void logV(String tag, String message, Throwable tr) {
        log(2, tag, message, tr);
    }

    static void logV(String tag, String message) {
        logV(tag, message, null);
    }

    static void logD(String tag, String message, Throwable tr) {
        log(3, tag, message, tr);
    }

    static void logD(String tag, String message) {
        logD(tag, message, null);
    }

    static void logI(String tag, String message, Throwable tr) {
        log(4, tag, message, tr);
    }

    static void logI(String tag, String message) {
        logI(tag, message, null);
    }

    static void logW(String tag, String message, Throwable tr) {
        log(5, tag, message, tr);
    }

    static void logW(String tag, String message) {
        logW(tag, message, null);
    }

    static void logE(String tag, String message, Throwable tr) {
        log(6, tag, message, tr);
    }

    static void logE(String tag, String message) {
        logE(tag, message, null);
    }

    static void setContextIfNeeded(Context context) {
        if (applicationContext == null) {
            applicationContext = context;
        }
    }

    static File getParseDir() {
        File dir;
        synchronized (lock) {
            checkContext();
            dir = applicationContext.getDir("Parse", 0);
        }
        return dir;
    }

    static File getParseCacheDir(String subDir) {
        File dir;
        synchronized (lock) {
            checkContext();
            dir = new File(new File(applicationContext.getCacheDir(), "com.parse"), subDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
        }
        return dir;
    }

    static File getParseFilesDir(String subDir) {
        File dir;
        synchronized (lock) {
            checkContext();
            dir = new File(new File(applicationContext.getFilesDir(), "com.parse"), subDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
        }
        return dir;
    }

    static void recursiveDelete(File file) {
        synchronized (lock) {
            if (file.isDirectory()) {
                for (File f : file.listFiles()) {
                    recursiveDelete(f);
                }
            }
            file.delete();
        }
    }

    static void checkCacheApplicationId() {
        synchronized (lock) {
            if (applicationId != null) {
                File applicationIdFile = new File(getParseDir(), "applicationId");
                if (applicationIdFile.exists()) {
                    boolean matches = false;
                    try {
                        RandomAccessFile f = new RandomAccessFile(applicationIdFile, "r");
                        byte[] bytes = new byte[((int) f.length())];
                        f.readFully(bytes);
                        f.close();
                        matches = new String(bytes, "UTF-8").equals(applicationId);
                    } catch (FileNotFoundException | IOException e) {
                    }
                    if (!matches) {
                        recursiveDelete(getParseDir());
                    }
                }
                try {
                    FileOutputStream out = new FileOutputStream(new File(getParseDir(), "applicationId"));
                    out.write(applicationId.getBytes("UTF-8"));
                    out.close();
                } catch (FileNotFoundException | IOException | UnsupportedEncodingException e2) {
                }
            }
        }
    }

    static File getKeyValueCacheDir() {
        File parseCacheDir;
        synchronized (lock) {
            checkContext();
            parseCacheDir = new File(applicationContext.getCacheDir(), "ParseKeyValueCache");
            if (!parseCacheDir.isDirectory() && !parseCacheDir.mkdir()) {
                throw new RuntimeException("could not create Parse cache directory");
            }
        }
        return parseCacheDir;
    }

    static File getKeyValueCacheFile(String key) {
        final String suffix = '.' + key;
        File[] matches = getKeyValueCacheDir().listFiles(new FilenameFilter() {
            public boolean accept(File dir, String filename) {
                return filename.endsWith(suffix);
            }
        });
        if (matches == null || matches.length == 0) {
            return null;
        }
        return matches[0];
    }

    static long getKeyValueCacheAge(File cacheFile) {
        String name = cacheFile.getName();
        try {
            return Long.parseLong(name.substring(0, name.indexOf(46)));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static File createKeyValueCacheFile(String key) {
        return new File(getKeyValueCacheDir(), String.valueOf(new Date().getTime()) + '.' + key);
    }

    static void clearCacheDir() {
        File[] entries = getKeyValueCacheDir().listFiles();
        if (entries != null) {
            for (File delete : entries) {
                delete.delete();
            }
        }
    }

    static void saveToKeyValueCache(String key, String value) {
        File prior = getKeyValueCacheFile(key);
        if (prior != null) {
            prior.delete();
        }
        try {
            FileOutputStream out = new FileOutputStream(createKeyValueCacheFile(key));
            out.write(value.getBytes("UTF-8"));
            out.close();
        } catch (IOException | UnsupportedEncodingException e) {
        }
        File[] files = getKeyValueCacheDir().listFiles();
        int numFiles = files.length;
        int numBytes = 0;
        for (File file : files) {
            numBytes = (int) (((long) numBytes) + file.length());
        }
        if (numFiles > maxKeyValueCacheFiles || numBytes > maxKeyValueCacheBytes) {
            Arrays.sort(files, new Comparator<File>() {
                public int compare(File f1, File f2) {
                    int dateCompare = Long.valueOf(f1.lastModified()).compareTo(Long.valueOf(f2.lastModified()));
                    return dateCompare != 0 ? dateCompare : f1.getName().compareTo(f2.getName());
                }
            });
            File[] arr$ = files;
            int len$ = arr$.length;
            int i$ = 0;
            while (i$ < len$) {
                File file2 = arr$[i$];
                numFiles--;
                numBytes = (int) (((long) numBytes) - file2.length());
                file2.delete();
                if (numFiles > maxKeyValueCacheFiles || numBytes > maxKeyValueCacheBytes) {
                    i$++;
                } else {
                    return;
                }
            }
        }
    }

    static void clearFromKeyValueCache(String key) {
        File file = getKeyValueCacheFile(key);
        if (file != null) {
            file.delete();
        }
    }

    static String loadFromKeyValueCache(String key, long maxAgeMilliseconds) {
        File file = getKeyValueCacheFile(key);
        if (file == null) {
            return null;
        }
        Date now = new Date();
        if (getKeyValueCacheAge(file) < Math.max(0, now.getTime() - maxAgeMilliseconds)) {
            return null;
        }
        file.setLastModified(now.getTime());
        try {
            RandomAccessFile f = new RandomAccessFile(file, "r");
            byte[] bytes = new byte[((int) f.length())];
            f.readFully(bytes);
            f.close();
            return new String(bytes, "UTF-8");
        } catch (IOException e) {
            logE(TAG, "error reading from cache", e);
            return null;
        }
    }

    static Object jsonFromKeyValueCache(String key, long maxAgeMilliseconds) {
        Object obj = null;
        String raw = loadFromKeyValueCache(key, maxAgeMilliseconds);
        if (raw == null) {
            return obj;
        }
        try {
            return new JSONTokener(raw).nextValue();
        } catch (JSONException e) {
            logE(TAG, "corrupted cache for " + key, e);
            clearFromKeyValueCache(key);
            return obj;
        }
    }

    static ParseCommandCache getCommandCache() {
        ParseCommandCache parseCommandCache;
        synchronized (lock) {
            if (commandCache == null) {
                checkContext();
                commandCache = new ParseCommandCache(applicationContext);
            }
            parseCommandCache = commandCache;
        }
        return parseCommandCache;
    }

    static void checkInit() {
        if (applicationId == null) {
            throw new RuntimeException("applicationId is null. You must call Parse.initialize(context, applicationId, clientKey) before using the Parse library.");
        } else if (clientKey == null) {
            throw new RuntimeException("clientKey is null. You must call Parse.initialize(context, applicationId, clientKey) before using the Parse library.");
        }
    }

    static void checkContext() {
        if (applicationContext == null) {
            throw new RuntimeException("applicationContext is null. You must call Parse.initialize(context, applicationId, clientKey) before using the Parse library.");
        }
    }

    static boolean hasPermission(String permission) {
        checkContext();
        return applicationContext.checkCallingOrSelfPermission(permission) == 0;
    }

    static void requirePermission(String permission) {
        if (!hasPermission(permission)) {
            throw new IllegalStateException("To use this functionality, add this to your AndroidManifest.xml:\n<uses-permission android:name=\"" + permission + "\" />");
        }
    }

    static boolean isValidType(Object value) {
        return (value instanceof JSONObject) || (value instanceof JSONArray) || (value instanceof String) || (value instanceof Number) || (value instanceof Boolean) || value == JSONObject.NULL || (value instanceof ParseObject) || (value instanceof ParseACL) || (value instanceof ParseFile) || (value instanceof ParseGeoPoint) || (value instanceof Date) || (value instanceof byte[]) || (value instanceof List) || (value instanceof Map) || (value instanceof ParseRelation);
    }

    static Object encode(Object object, ParseObjectEncodingStrategy objectEncoder) {
        try {
            if (object instanceof ParseObject) {
                return objectEncoder.encodeRelatedObject((ParseObject) object);
            }
            if (object instanceof ParseQuery) {
                return ((ParseQuery) object).toREST();
            }
            if (object instanceof Date) {
                return encodeDate((Date) object);
            }
            if (object instanceof byte[]) {
                JSONObject json = new JSONObject();
                json.put("__type", "Bytes");
                json.put("base64", Base64.encodeBase64String((byte[]) object));
                return json;
            } else if (object instanceof ParseFile) {
                ParseFile file = (ParseFile) object;
                JSONObject json2 = new JSONObject();
                json2.put("__type", "File");
                json2.put("url", file.getUrl());
                json2.put("name", file.getName());
                return json2;
            } else if (object instanceof ParseGeoPoint) {
                ParseGeoPoint point = (ParseGeoPoint) object;
                JSONObject json3 = new JSONObject();
                json3.put("__type", "GeoPoint");
                json3.put("latitude", point.getLatitude());
                json3.put("longitude", point.getLongitude());
                return json3;
            } else if (object instanceof ParseACL) {
                return ((ParseACL) object).toJSONObject();
            } else {
                if (object instanceof Map) {
                    Map map = (Map) object;
                    JSONObject json4 = new JSONObject();
                    for (Entry<String, Object> pair : map.entrySet()) {
                        json4.put((String) pair.getKey(), encode(pair.getValue(), objectEncoder));
                    }
                    return json4;
                } else if (object instanceof JSONObject) {
                    JSONObject map2 = (JSONObject) object;
                    JSONObject json5 = new JSONObject();
                    Iterator<String> keys = map2.keys();
                    while (keys.hasNext()) {
                        String key = (String) keys.next();
                        json5.put(key, encode(map2.opt(key), objectEncoder));
                    }
                    return json5;
                } else if (object instanceof List) {
                    JSONArray array = new JSONArray();
                    for (Object item : (List) object) {
                        array.put(encode(item, objectEncoder));
                    }
                    return array;
                } else if (object instanceof JSONArray) {
                    JSONArray array2 = (JSONArray) object;
                    JSONArray json6 = new JSONArray();
                    for (int i = 0; i < array2.length(); i++) {
                        json6.put(encode(array2.opt(i), objectEncoder));
                    }
                    return json6;
                } else if (object instanceof ParseRelation) {
                    return ((ParseRelation) object).encodeToJSON(objectEncoder);
                } else {
                    if (object instanceof ParseFieldOperation) {
                        return ((ParseFieldOperation) object).encode(objectEncoder);
                    }
                    if (object instanceof RelationConstraint) {
                        return ((RelationConstraint) object).encode(objectEncoder);
                    }
                    if (isValidType(object)) {
                        return object;
                    }
                    throw new IllegalArgumentException("invalid type for ParseObject: " + object.getClass().toString());
                }
            }
        } catch (JSONException e) {
            RuntimeException runtimeException = new RuntimeException(e);
            throw runtimeException;
        }
    }

    static Date stringToDate(String dateString) {
        Date date;
        synchronized (lock) {
            try {
                date = dateFormat.parse(dateString);
            } catch (ParseException e) {
                logE(TAG, "could not parse date: " + dateString, e);
                date = null;
            }
        }
        return date;
    }

    static String dateToString(Date date) {
        String format;
        synchronized (lock) {
            format = dateFormat.format(date);
        }
        return format;
    }

    static JSONObject encodeDate(Date date) {
        JSONObject object = new JSONObject();
        String iso = dateToString(date);
        try {
            object.put("__type", "Date");
            object.put("iso", iso);
            return object;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    static Iterable<String> keys(JSONObject object) {
        final JSONObject finalObject = object;
        return new Iterable<String>() {
            public Iterator<String> iterator() {
                return finalObject.keys();
            }
        };
    }

    static boolean isContainerObject(Object object) {
        return (object instanceof JSONObject) || (object instanceof JSONArray) || (object instanceof ParseACL) || (object instanceof ParseGeoPoint) || (object instanceof List) || (object instanceof Map);
    }

    static Number addNumbers(Number first, Number second) {
        if ((first instanceof Double) || (second instanceof Double)) {
            return Double.valueOf(first.doubleValue() + second.doubleValue());
        }
        if ((first instanceof Float) || (second instanceof Float)) {
            return Float.valueOf(first.floatValue() + second.floatValue());
        }
        if ((first instanceof Long) || (second instanceof Long)) {
            return Long.valueOf(first.longValue() + second.longValue());
        }
        if ((first instanceof Integer) || (second instanceof Integer)) {
            return Integer.valueOf(first.intValue() + second.intValue());
        }
        if ((first instanceof Short) || (second instanceof Short)) {
            return Integer.valueOf(first.shortValue() + second.shortValue());
        }
        if ((first instanceof Byte) || (second instanceof Byte)) {
            return Integer.valueOf(first.byteValue() + second.byteValue());
        }
        throw new RuntimeException("Unknown number type.");
    }

    static Number subtractNumbers(Number first, Number second) {
        if ((first instanceof Double) || (second instanceof Double)) {
            return Double.valueOf(first.doubleValue() - second.doubleValue());
        }
        if ((first instanceof Float) || (second instanceof Float)) {
            return Float.valueOf(first.floatValue() - second.floatValue());
        }
        if ((first instanceof Long) || (second instanceof Long)) {
            return Long.valueOf(first.longValue() - second.longValue());
        }
        if ((first instanceof Integer) || (second instanceof Integer)) {
            return Integer.valueOf(first.intValue() - second.intValue());
        }
        if ((first instanceof Short) || (second instanceof Short)) {
            return Integer.valueOf(first.shortValue() - second.shortValue());
        }
        if ((first instanceof Byte) || (second instanceof Byte)) {
            return Integer.valueOf(first.byteValue() - second.byteValue());
        }
        throw new RuntimeException("Unknown number type.");
    }

    static int compareNumbers(Number first, Number second) {
        if ((first instanceof Double) || (second instanceof Double)) {
            return (int) Math.signum(first.doubleValue() - second.doubleValue());
        }
        if ((first instanceof Float) || (second instanceof Float)) {
            return (int) Math.signum(first.floatValue() - second.floatValue());
        }
        if ((first instanceof Long) || (second instanceof Long)) {
            long diff = first.longValue() - second.longValue();
            if (diff < 0) {
                return -1;
            }
            return diff > 0 ? 1 : 0;
        } else if ((first instanceof Integer) || (second instanceof Integer)) {
            return first.intValue() - second.intValue();
        } else {
            if ((first instanceof Short) || (second instanceof Short)) {
                return first.shortValue() - second.shortValue();
            }
            if ((first instanceof Byte) || (second instanceof Byte)) {
                return first.byteValue() - second.byteValue();
            }
            throw new RuntimeException("Unknown number type.");
        }
    }

    static String join(Collection<String> items, String delimiter) {
        StringBuffer buffer = new StringBuffer();
        Iterator<String> iter = items.iterator();
        if (iter.hasNext()) {
            buffer.append((String) iter.next());
            while (iter.hasNext()) {
                buffer.append(delimiter);
                buffer.append((String) iter.next());
            }
        }
        return buffer.toString();
    }

    static <T> T waitForTask(Task<T> task) throws ParseException {
        try {
            task.waitForCompletion();
            if (task.isFaulted()) {
                Exception error = task.getError();
                if (error instanceof ParseException) {
                    throw ((ParseException) error);
                } else if (error instanceof RuntimeException) {
                    throw ((RuntimeException) error);
                } else {
                    throw new RuntimeException(error);
                }
            } else if (!task.isCancelled()) {
                return task.getResult();
            } else {
                throw new RuntimeException(new CancellationException());
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    static ScheduledExecutorService getScheduledExecutor() {
        synchronized (SCHEDULED_EXECUTOR_LOCK) {
            if (scheduledExecutor == null) {
                scheduledExecutor = Executors.newScheduledThreadPool(1);
            }
        }
        return scheduledExecutor;
    }

    static <T> Task<T> callbackOnMainThreadAsync(Task<T> task, ParseCallback<T> callback) {
        return callbackOnMainThreadAsync(task, callback, false);
    }

    static <T> Task<T> callbackOnMainThreadAsync(Task<T> task, final ParseCallback<T> callback, final boolean reportCancellation) {
        if (callback == null) {
            return task;
        }
        final TaskCompletionSource tcs = Task.create();
        task.continueWith(new Continuation<T, Void>() {
            public Void then(final Task<T> task) throws Exception {
                if (!task.isCancelled() || reportCancellation) {
                    Task.UI_THREAD_EXECUTOR.execute(new Runnable() {
                        public void run() {
                            try {
                                Exception error = task.getError();
                                if (error != null && !(error instanceof ParseException)) {
                                    error = new ParseException(error);
                                }
                                callback.internalDone(task.getResult(), (ParseException) error);
                            } finally {
                                if (task.isCancelled()) {
                                    tcs.setCancelled();
                                } else if (task.isFaulted()) {
                                    tcs.setError(task.getError());
                                } else {
                                    tcs.setResult(task.getResult());
                                }
                            }
                        }
                    });
                } else {
                    tcs.setCancelled();
                }
                return null;
            }
        });
        return tcs.getTask();
    }
}
