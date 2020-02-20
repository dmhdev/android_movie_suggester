package com.parse;

import android.content.Context;
import com.parse.ParseQuery.CachePolicy;
import com.parse.Task.TaskCompletionSource;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Member;
import java.lang.reflect.Modifier;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SimpleTimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

public class ParseObject {
    static final String API_VERSION = "2";
    private static final String AUTO_CLASS_NAME = "_Automatic";
    private static final String TAG = "com.parse.ParseObject";
    static final String VERSION_NAME = "1.4.3";
    private static final Map<Class<? extends ParseObject>, String> classNames = new ConcurrentHashMap();
    private static final DateFormat impreciseDateFormat;
    private static final ThreadLocal<Boolean> isCreatingPointer = new ThreadLocal<Boolean>() {
        /* access modifiers changed from: protected */
        public Boolean initialValue() {
            return Boolean.valueOf(false);
        }
    };
    private static final Map<String, Class<? extends ParseObject>> objectTypes = new ConcurrentHashMap();
    static String server = "https://api.parse.com";
    /* access modifiers changed from: private */
    public String className;
    private Date createdAt;
    private final Map<String, Boolean> dataAvailability;
    boolean dirty;
    /* access modifiers changed from: private */
    public final Map<String, Object> estimatedData;
    boolean hasBeenFetched;
    private final Map<Object, ParseJSONCacheItem> hashedObjects;
    private String localId;
    final Object mutex;
    /* access modifiers changed from: private */
    public String objectId;
    final LinkedList<Map<String, ParseFieldOperation>> operationSetQueue;
    private final ParseMulticastDelegate<ParseObject> saveEvent;
    private final Map<String, Object> serverData;
    final TaskQueue taskQueue;
    private Date updatedAt;

    static {
        DateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        format.setTimeZone(new SimpleTimeZone(0, "GMT"));
        impreciseDateFormat = format;
    }

    protected ParseObject() {
        this(AUTO_CLASS_NAME);
    }

    public ParseObject(String theClassName) {
        this.saveEvent = new ParseMulticastDelegate<>();
        this.mutex = new Object();
        this.taskQueue = new TaskQueue();
        boolean isPointer = ((Boolean) isCreatingPointer.get()).booleanValue();
        isCreatingPointer.set(Boolean.valueOf(false));
        if (theClassName == null) {
            throw new IllegalArgumentException("You must specify a Parse class name when creating a new ParseObject.");
        }
        if (AUTO_CLASS_NAME.equals(theClassName)) {
            theClassName = getClassName(getClass());
        }
        if (getClass().equals(ParseObject.class) && objectTypes.containsKey(theClassName) && !((Class) objectTypes.get(theClassName)).isInstance(this)) {
            throw new IllegalArgumentException("You must create this type of ParseObject using ParseObject.create() or the proper subclass.");
        } else if (getClass().equals(ParseObject.class) || getClass().equals(objectTypes.get(theClassName))) {
            this.localId = null;
            this.serverData = new HashMap();
            this.operationSetQueue = new LinkedList<>();
            this.operationSetQueue.add(new HashMap());
            this.estimatedData = new HashMap();
            this.hashedObjects = new IdentityHashMap();
            this.dataAvailability = new HashMap();
            this.className = theClassName;
            if (!isPointer) {
                setDefaultValues();
                this.hasBeenFetched = true;
                this.dirty = true;
                return;
            }
            this.dirty = false;
            this.hasBeenFetched = false;
        } else {
            throw new IllegalArgumentException("You must register this ParseObject subclass before instantiating it.");
        }
    }

    public static ParseObject create(String className2) {
        if (!objectTypes.containsKey(className2)) {
            return new ParseObject(className2);
        }
        try {
            return (ParseObject) ((Class) objectTypes.get(className2)).newInstance();
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw ((RuntimeException) e);
            }
            throw new RuntimeException("Failed to create instance of subclass.", e);
        }
    }

    public static <T extends ParseObject> T create(Class<T> subclass) {
        return create(getClassName(subclass));
    }

    public static ParseObject createWithoutData(String className2, String objectId2) {
        try {
            isCreatingPointer.set(Boolean.valueOf(true));
            ParseObject result = create(className2);
            result.setObjectId(objectId2);
            result.dirty = false;
            if (result.isDirty()) {
                throw new IllegalStateException("A ParseObject subclass default constructor must not make changes to the object that cause it to be dirty.");
            }
            isCreatingPointer.set(Boolean.valueOf(false));
            return result;
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw ((RuntimeException) e);
            }
            throw new RuntimeException("Failed to create instance of subclass.", e);
        } catch (Throwable th) {
            isCreatingPointer.set(Boolean.valueOf(false));
            throw th;
        }
    }

    public static <T extends ParseObject> T createWithoutData(Class<T> subclass, String objectId2) {
        return createWithoutData(getClassName(subclass), objectId2);
    }

    private static boolean isAccessible(Member m) {
        return Modifier.isPublic(m.getModifiers()) || (m.getDeclaringClass().getPackage().getName().equals("com.parse") && !Modifier.isPrivate(m.getModifiers()) && !Modifier.isProtected(m.getModifiers()));
    }

    public static void registerSubclass(Class<? extends ParseObject> subclass) {
        String className2 = getClassName(subclass);
        if (className2 == null) {
            throw new IllegalArgumentException("No ParseClassName annoation provided on " + subclass);
        }
        if (subclass.getDeclaredConstructors().length > 0) {
            try {
                if (!isAccessible(subclass.getDeclaredConstructor(new Class[0]))) {
                    throw new IllegalArgumentException("Default constructor for " + subclass + " is not accessible.");
                }
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException("No default constructor provided for " + subclass);
            }
        }
        Class<? extends ParseObject> oldValue = (Class) objectTypes.get(className2);
        if (oldValue == null || !subclass.isAssignableFrom(oldValue)) {
            objectTypes.put(className2, subclass);
            if (oldValue != null && !subclass.equals(oldValue)) {
                if (className2.equals(getClassName(ParseUser.class))) {
                    ParseUser.clearCurrentUserFromMemory();
                } else if (className2.equals(getClassName(ParseInstallation.class))) {
                    ParseInstallation.clearCurrentInstallationFromMemory();
                }
            }
        }
    }

    static void unregisterSubclass(String className2) {
        objectTypes.remove(className2);
    }

    static String getApplicationId() {
        Parse.checkInit();
        return Parse.applicationId;
    }

    static <T> Task<T> enqueueForAll(List<? extends ParseObject> objects, Continuation<Void, Task<T>> taskStart) {
        final TaskCompletionSource readyToStart = Task.create();
        List<Lock> locks = new ArrayList<>(objects.size());
        for (ParseObject obj : objects) {
            locks.add(obj.taskQueue.getLock());
        }
        LockSet lock = new LockSet(locks);
        lock.lock();
        try {
            final Task<T> fullTask = (Task) taskStart.then(readyToStart.getTask());
            final List<Task<Void>> childTasks = new ArrayList<>();
            for (ParseObject obj2 : objects) {
                obj2.taskQueue.enqueue(new Continuation<Void, Task<T>>() {
                    public Task<T> then(Task<Void> task) throws Exception {
                        childTasks.add(task);
                        return fullTask;
                    }
                });
            }
            Task.whenAll(childTasks).continueWith(new Continuation<Void, Void>() {
                public Void then(Task<Void> task) throws Exception {
                    readyToStart.setResult(null);
                    return null;
                }
            });
            lock.unlock();
            return fullTask;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e2) {
            throw new RuntimeException(e2);
        } catch (Throwable th) {
            lock.unlock();
            throw th;
        }
    }

    private static synchronized Date impreciseParseDate(String encoded) {
        Date date;
        synchronized (ParseObject.class) {
            try {
                date = impreciseDateFormat.parse(encoded);
            } catch (ParseException e) {
                Parse.logE(TAG, "could not parse date: " + encoded, e);
                date = null;
            }
        }
        return date;
    }

    static synchronized JSONObject getDiskObject(Context context, String filename) {
        JSONObject diskObject;
        synchronized (ParseObject.class) {
            Parse.setContextIfNeeded(context);
            diskObject = getDiskObject(new File(Parse.getParseDir(), filename));
        }
        return diskObject;
    }

    static synchronized JSONObject getDiskObject(File file) {
        JSONObject jSONObject = null;
        synchronized (ParseObject.class) {
            if (file.exists()) {
                try {
                    RandomAccessFile f = new RandomAccessFile(file, "r");
                    byte[] bytes = new byte[((int) f.length())];
                    f.readFully(bytes);
                    f.close();
                    try {
                        jSONObject = new JSONObject(new JSONTokener(new String(bytes, "UTF-8")));
                    } catch (JSONException e) {
                    }
                } catch (IOException e2) {
                }
            }
        }
        return jSONObject;
    }

    static synchronized void saveDiskObject(Context context, String filename, JSONObject object) {
        synchronized (ParseObject.class) {
            Parse.setContextIfNeeded(context);
            saveDiskObject(new File(Parse.getParseDir(), filename), object);
        }
    }

    static synchronized void saveDiskObject(File file, JSONObject object) {
        synchronized (ParseObject.class) {
            try {
                FileOutputStream out = new FileOutputStream(file);
                out.write(object.toString().getBytes("UTF-8"));
                out.flush();
                out.close();
            } catch (IOException | UnsupportedEncodingException e) {
            }
        }
    }

    static synchronized void deleteDiskObject(Context context, String filename) {
        synchronized (ParseObject.class) {
            Parse.setContextIfNeeded(context);
            File file = new File(Parse.getParseDir(), filename);
            if (file != null) {
                file.delete();
            }
        }
    }

    /* access modifiers changed from: 0000 */
    public void saveToDisk(Context context, String filename) {
        synchronized (this.mutex) {
            saveDiskObject(context, filename, toJSONObjectForDataFile(false, PointerEncodingStrategy.get()));
        }
    }

    /* access modifiers changed from: 0000 */
    public void addToHashedObjects(Object object) {
        synchronized (this.mutex) {
            try {
                this.hashedObjects.put(object, new ParseJSONCacheItem(object));
            } catch (JSONException e) {
                throw new IllegalArgumentException("Couldn't serialize container value to JSON.");
            }
        }
    }

    static ParseObject getFromDisk(Context context, String filename) {
        JSONObject object = getDiskObject(context, filename);
        if (object == null) {
            return null;
        }
        try {
            ParseObject parseObject = createWithoutData(object.getString("classname"), (String) null);
            parseObject.mergeFromServer(object, new ParseDecoder(), true);
            return parseObject;
        } catch (JSONException e) {
            return null;
        }
    }

    static ParseObject fromJSONObjectForDataFile(JSONObject json, ParseDecoder decoder, boolean completeData) throws JSONException {
        ParseObject parseObject = createWithoutData(json.getString("classname"), (String) null);
        parseObject.mergeFromServer(json, decoder, completeData);
        return parseObject;
    }

    static <T extends ParseObject> T fromJSON(JSONObject json, String defaultClassName, boolean isComplete) {
        return fromJSON(json, defaultClassName, isComplete, new ParseDecoder());
    }

    static <T extends ParseObject> T fromJSON(JSONObject json, String defaultClassName, boolean isComplete, ParseDecoder decoder) {
        JSONObject data = json.optJSONObject("data");
        String objectId2 = null;
        String className2 = null;
        if (data != null) {
            objectId2 = data.optString("objectId", null);
            className2 = data.optString("classname", defaultClassName);
        }
        T object = createWithoutData(className2, objectId2);
        object.mergeAfterFetch(json, decoder, isComplete);
        return object;
    }

    public String getClassName() {
        String str;
        synchronized (this.mutex) {
            str = this.className;
        }
        return str;
    }

    public Set<String> keySet() {
        Set<String> unmodifiableSet;
        synchronized (this.mutex) {
            unmodifiableSet = Collections.unmodifiableSet(this.estimatedData.keySet());
        }
        return unmodifiableSet;
    }

    public Date getUpdatedAt() {
        Date date;
        synchronized (this.mutex) {
            date = this.updatedAt;
        }
        return date;
    }

    public Date getCreatedAt() {
        Date date;
        synchronized (this.mutex) {
            date = this.createdAt;
        }
        return date;
    }

    /* access modifiers changed from: 0000 */
    public void copyChangesFrom(ParseObject other) {
        synchronized (this.mutex) {
            Map<String, ParseFieldOperation> operations = (Map) other.operationSetQueue.getFirst();
            for (String key : operations.keySet()) {
                performOperation(key, (ParseFieldOperation) operations.get(key));
            }
        }
    }

    /* access modifiers changed from: 0000 */
    public void mergeFromObject(ParseObject other) {
        synchronized (this.mutex) {
            this.objectId = other.objectId;
            this.createdAt = other.createdAt;
            this.updatedAt = other.updatedAt;
            this.serverData.clear();
            this.serverData.putAll(other.serverData);
            if (this.operationSetQueue.size() != 1) {
                throw new IllegalStateException("Attempt to mergeFromObject during a save.");
            }
            this.operationSetQueue.clear();
            this.operationSetQueue.add(new HashMap());
            this.dirty = false;
            rebuildEstimatedData();
        }
    }

    /* access modifiers changed from: 0000 */
    public void revert() {
        synchronized (this.mutex) {
            currentOperations().clear();
            rebuildEstimatedData();
        }
    }

    /* access modifiers changed from: 0000 */
    public void mergeAfterFetch(JSONObject result, ParseDecoder decoder, boolean completeData) {
        synchronized (this.mutex) {
            mergeFromServer(result, decoder, completeData);
            rebuildEstimatedData();
            checkpointAllMutableContainers();
        }
    }

    private void mergeAfterSave(JSONObject result, ParseDecoder decoder, boolean justCreated, Map<String, ParseFieldOperation> operationsBeforeSave) {
        ParseFieldOperation operation2;
        synchronized (this.mutex) {
            ListIterator<Map<String, ParseFieldOperation>> opIterator = this.operationSetQueue.listIterator(this.operationSetQueue.indexOf(operationsBeforeSave));
            opIterator.next();
            opIterator.remove();
            Map<String, ParseFieldOperation> nextOperation = (Map) opIterator.next();
            if (result == null) {
                for (String key : operationsBeforeSave.keySet()) {
                    ParseFieldOperation operation1 = (ParseFieldOperation) operationsBeforeSave.get(key);
                    ParseFieldOperation operation22 = (ParseFieldOperation) nextOperation.get(key);
                    if (operation22 != null) {
                        operation2 = operation22.mergeWithPrevious(operation1);
                    } else {
                        operation2 = operation1;
                    }
                    ((Map) this.operationSetQueue.getFirst()).put(key, operation2);
                }
            } else {
                applyOperations(operationsBeforeSave, this.serverData);
                mergeFromServer(result, decoder, false);
                rebuildEstimatedData();
                checkpointAllMutableContainers();
            }
        }
    }

    private Map<String, ParseObject> collectFetchedObjects() {
        final Map<String, ParseObject> fetchedObjects = new HashMap<>();
        new ParseTraverser() {
            /* access modifiers changed from: protected */
            public boolean visit(Object object) {
                if (object instanceof ParseObject) {
                    ParseObject parseObj = (ParseObject) object;
                    if (parseObj.objectId != null && parseObj.isDataAvailable()) {
                        fetchedObjects.put(parseObj.objectId, parseObj);
                    }
                }
                return true;
            }
        }.traverse(this.estimatedData);
        return fetchedObjects;
    }

    /* access modifiers changed from: 0000 */
    public void mergeFromServer(JSONObject object, ParseDecoder decoder, boolean completeData) {
        synchronized (this.mutex) {
            this.dirty = false;
            this.hasBeenFetched = this.hasBeenFetched || completeData;
            try {
                if (object.has("id") && this.objectId == null) {
                    setObjectIdInternal(object.getString("id"));
                }
                if (object.has("created_at")) {
                    String createdAtString = object.getString("created_at");
                    if (createdAtString != null) {
                        this.createdAt = impreciseParseDate(createdAtString);
                    }
                }
                if (object.has("updated_at")) {
                    String updatedAtString = object.getString("updated_at");
                    if (updatedAtString != null) {
                        this.updatedAt = impreciseParseDate(updatedAtString);
                    }
                }
                if (object.has("pointers")) {
                    JSONObject newPointers = object.getJSONObject("pointers");
                    Iterator<String> keys = newPointers.keys();
                    while (keys.hasNext()) {
                        String key = (String) keys.next();
                        JSONArray pointerArray = newPointers.getJSONArray(key);
                        this.serverData.put(key, createWithoutData(pointerArray.optString(0), pointerArray.optString(1)));
                    }
                }
                if (object.has("data")) {
                    JSONObject newData = object.getJSONObject("data");
                    Iterator<String> keys2 = newData.keys();
                    while (keys2.hasNext()) {
                        String key2 = (String) keys2.next();
                        this.dataAvailability.put(key2, Boolean.valueOf(true));
                        if (key2.equals("objectId")) {
                            setObjectIdInternal(newData.getString(key2));
                        } else if (key2.equals("createdAt")) {
                            this.createdAt = Parse.stringToDate(newData.getString(key2));
                        } else if (key2.equals("updatedAt")) {
                            this.updatedAt = Parse.stringToDate(newData.getString(key2));
                        } else if (key2.equals("ACL")) {
                            ParseACL acl = ParseACL.createACLFromJSONObject(newData.getJSONObject(key2));
                            this.serverData.put("ACL", acl);
                            addToHashedObjects(acl);
                        } else if (!key2.equals("__type") && !key2.equals("className")) {
                            Object decodedObject = decoder.decode(newData.get(key2));
                            if (Parse.isContainerObject(decodedObject)) {
                                addToHashedObjects(decodedObject);
                            }
                            this.serverData.put(key2, decodedObject);
                        }
                    }
                }
                if (this.updatedAt == null && this.createdAt != null) {
                    this.updatedAt = this.createdAt;
                }
                this.dirty = false;
                rebuildEstimatedData();
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /* access modifiers changed from: 0000 */
    public JSONObject toRest(ParseObjectEncodingStrategy objectEncoder) {
        JSONObject json;
        synchronized (this.mutex) {
            checkForChangesToMutableContainers();
            json = new JSONObject();
            try {
                json.put("className", this.className);
                for (String key : this.serverData.keySet()) {
                    json.put(key, Parse.encode(this.serverData.get(key), objectEncoder));
                }
                if (this.createdAt != null) {
                    json.put("createdAt", Parse.dateToString(this.createdAt));
                }
                if (this.updatedAt != null) {
                    json.put("updatedAt", Parse.dateToString(this.updatedAt));
                }
                if (this.objectId != null) {
                    json.put("objectId", this.objectId);
                }
                JSONArray operations = new JSONArray();
                Iterator i$ = this.operationSetQueue.iterator();
                while (i$.hasNext()) {
                    Map<String, ParseFieldOperation> operationSet = (Map) i$.next();
                    JSONObject operationSetJSON = new JSONObject();
                    for (String key2 : operationSet.keySet()) {
                        operationSetJSON.put(key2, ((ParseFieldOperation) operationSet.get(key2)).encode(objectEncoder));
                    }
                    operations.put(operationSetJSON);
                }
                json.put("__operations", operations);
                json.put("__complete", this.hasBeenFetched);
            } catch (JSONException e) {
                throw new RuntimeException("could not serialize object to JSON");
            }
        }
        return json;
    }

    static ParseObject fromREST(JSONObject json, ParseDecoder decoder) throws JSONException {
        ParseObject parseObject;
        String className2 = json.getString("className");
        String objectId2 = json.optString("objectId", null);
        if (objectId2 == null) {
            parseObject = create(className2);
        } else {
            parseObject = createWithoutData(className2, objectId2);
        }
        parseObject.mergeREST(json, decoder);
        return parseObject;
    }

    /* access modifiers changed from: 0000 */
    public void mergeREST(JSONObject object, ParseDecoder decoder) {
        ParseFieldOperation operation2;
        ParseFieldOperation op;
        synchronized (this.mutex) {
            try {
                Iterator<String> keys = object.keys();
                while (keys.hasNext()) {
                    String key = (String) keys.next();
                    this.dataAvailability.put(key, Boolean.valueOf(true));
                    if (!key.equals("__type") && !key.equals("className")) {
                        if (key.equals("objectId")) {
                            setObjectIdInternal(object.getString(key));
                        } else if (key.equals("createdAt")) {
                            this.createdAt = Parse.stringToDate(object.getString(key));
                        } else if (key.equals("updatedAt")) {
                            this.updatedAt = Parse.stringToDate(object.getString(key));
                        } else if (key.equals("ACL")) {
                            ParseACL acl = ParseACL.createACLFromJSONObject(object.getJSONObject(key));
                            this.serverData.put("ACL", acl);
                            addToHashedObjects(acl);
                        } else if (key.equals("__complete")) {
                            this.hasBeenFetched = this.hasBeenFetched || object.getBoolean(key);
                        } else if (key.equals("__operations")) {
                            Map<String, ParseFieldOperation> newerOperations = currentOperations();
                            JSONArray operations = object.getJSONArray("__operations");
                            if (operations != null) {
                                this.operationSetQueue.clear();
                                for (int i = 0; i < operations.length(); i++) {
                                    JSONObject operationSetJSON = operations.getJSONObject(i);
                                    HashMap<String, ParseFieldOperation> operationSet = new HashMap<>();
                                    Iterator<String> opKeys = operationSetJSON.keys();
                                    while (opKeys.hasNext()) {
                                        String opKey = (String) opKeys.next();
                                        Object value = decoder.decode(operationSetJSON.get(opKey));
                                        if (value instanceof ParseFieldOperation) {
                                            op = (ParseFieldOperation) value;
                                        } else {
                                            op = new ParseSetOperation(value);
                                        }
                                        operationSet.put(opKey, op);
                                    }
                                    this.operationSetQueue.add(operationSet);
                                }
                            }
                            Map<String, ParseFieldOperation> olderOperations = currentOperations();
                            for (String opKey2 : newerOperations.keySet()) {
                                ParseFieldOperation operation1 = (ParseFieldOperation) olderOperations.get(opKey2);
                                ParseFieldOperation operation22 = (ParseFieldOperation) newerOperations.get(opKey2);
                                if (operation22 != null) {
                                    operation2 = operation22.mergeWithPrevious(operation1);
                                } else {
                                    operation2 = operation1;
                                }
                                olderOperations.put(opKey2, operation2);
                            }
                        } else {
                            Object decodedObject = decoder.decode(object.get(key));
                            if (Parse.isContainerObject(decodedObject)) {
                                addToHashedObjects(decodedObject);
                            }
                            this.serverData.put(key, decodedObject);
                        }
                    }
                }
                if (this.updatedAt == null && this.createdAt != null) {
                    this.updatedAt = this.createdAt;
                }
                rebuildEstimatedData();
            } catch (JSONException e) {
                RuntimeException runtimeException = new RuntimeException(e);
                throw runtimeException;
            }
        }
    }

    private boolean hasDirtyChildren() {
        boolean z;
        synchronized (this.mutex) {
            List<ParseObject> unsavedChildren = new ArrayList<>();
            findUnsavedChildren(this.estimatedData, unsavedChildren);
            z = unsavedChildren.size() > 0;
        }
        return z;
    }

    public boolean isDirty() {
        return isDirty(true);
    }

    private boolean isDirty(boolean considerChildren) {
        boolean z;
        synchronized (this.mutex) {
            checkForChangesToMutableContainers();
            z = this.dirty || currentOperations().size() > 0 || (considerChildren && hasDirtyChildren());
        }
        return z;
    }

    public boolean isDirty(String key) {
        boolean containsKey;
        synchronized (this.mutex) {
            containsKey = currentOperations().containsKey(key);
        }
        return containsKey;
    }

    private void checkpointAllMutableContainers() {
        synchronized (this.mutex) {
            for (Object o : this.estimatedData.values()) {
                checkpointMutableContainer(o);
            }
        }
    }

    private void checkpointMutableContainer(Object object) {
        synchronized (this.mutex) {
            if (Parse.isContainerObject(object)) {
                try {
                    this.hashedObjects.put(object, new ParseJSONCacheItem(object));
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void checkForChangesToMutableContainer(String key, Object object) {
        synchronized (this.mutex) {
            if (Parse.isContainerObject(object)) {
                ParseJSONCacheItem oldCacheItem = (ParseJSONCacheItem) this.hashedObjects.get(object);
                if (oldCacheItem == null) {
                    throw new IllegalArgumentException("ParseObject contains container item that isn't cached.");
                }
                try {
                    if (!oldCacheItem.equals(new ParseJSONCacheItem(object))) {
                        performOperation(key, new ParseSetOperation(object));
                    }
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            } else {
                this.hashedObjects.remove(object);
            }
        }
    }

    /* access modifiers changed from: 0000 */
    public void checkForChangesToMutableContainers() {
        synchronized (this.mutex) {
            for (String key : this.estimatedData.keySet()) {
                checkForChangesToMutableContainer(key, this.estimatedData.get(key));
            }
            this.hashedObjects.keySet().retainAll(this.estimatedData.values());
        }
    }

    public String getObjectId() {
        String str;
        synchronized (this.mutex) {
            str = this.objectId;
        }
        return str;
    }

    /* access modifiers changed from: 0000 */
    public synchronized String getOrCreateLocalId() {
        String str;
        synchronized (this.mutex) {
            if (this.localId == null) {
                if (this.objectId != null) {
                    throw new IllegalStateException("Attempted to get a localId for an object with an objectId.");
                }
                this.localId = LocalIdManager.getDefaultInstance().createLocalId();
            }
            str = this.localId;
        }
        return str;
    }

    public void setObjectId(String newObjectId) {
        synchronized (this.mutex) {
            this.dirty = true;
            setObjectIdInternal(newObjectId);
        }
    }

    private void setObjectIdInternal(String newObjectId) {
        synchronized (this.mutex) {
            this.objectId = newObjectId;
            if (this.localId != null) {
                LocalIdManager.getDefaultInstance().setObjectId(this.localId, this.objectId);
                this.localId = null;
            }
        }
    }

    private static void findUnsavedChildren(Object data, List<ParseObject> unsaved) {
        if (data instanceof List) {
            for (Object elem : (List) data) {
                findUnsavedChildren(elem, unsaved);
            }
        } else if (data instanceof Map) {
            for (Object elem2 : ((Map) data).values()) {
                findUnsavedChildren(elem2, unsaved);
            }
        } else if (data instanceof ParseObject) {
            ParseObject object = (ParseObject) data;
            if (object.isDirty()) {
                unsaved.add(object);
            }
        }
    }

    /* access modifiers changed from: 0000 */
    public ParseCommand constructSaveCommand(Map<String, ParseFieldOperation> operations, ParseObjectEncodingStrategy objectEncoder, String sessionToken) throws ParseException {
        ParseCommand command;
        synchronized (this.mutex) {
            JSONObject objectJSON = toJSONObjectForSaving(operations, objectEncoder);
            command = new ParseCommand(this.objectId == null ? "create" : "update", sessionToken);
            command.enableRetrying();
            command.put("classname", this.className);
            try {
                command.put("data", objectJSON.getJSONObject("data"));
            } catch (JSONException e) {
                throw new RuntimeException("could not decode data");
            }
        }
        return command;
    }

    /* access modifiers changed from: 0000 */
    public JSONObject toJSONObjectForDataFile(boolean includeOperations, ParseObjectEncodingStrategy objectEncoder) {
        JSONObject objectJSON;
        synchronized (this.mutex) {
            checkForChangesToMutableContainers();
            objectJSON = new JSONObject();
            JSONObject dataJSON = new JSONObject();
            try {
                for (String key : this.serverData.keySet()) {
                    Object object = this.serverData.get(key);
                    if (!Parse.isContainerObject(object) || !this.hashedObjects.containsKey(object)) {
                        dataJSON.put(key, Parse.encode(object, objectEncoder));
                    } else {
                        dataJSON.put(key, ((ParseJSONCacheItem) this.hashedObjects.get(object)).getJSONObject());
                    }
                }
                if (this.createdAt != null) {
                    dataJSON.put("createdAt", Parse.dateToString(this.createdAt));
                }
                if (this.updatedAt != null) {
                    dataJSON.put("updatedAt", Parse.dateToString(this.updatedAt));
                }
                if (this.objectId != null) {
                    dataJSON.put("objectId", this.objectId);
                }
                objectJSON.put("data", dataJSON);
                objectJSON.put("classname", this.className);
                if (includeOperations) {
                    JSONArray operations = new JSONArray();
                    Iterator i$ = this.operationSetQueue.iterator();
                    while (i$.hasNext()) {
                        Map<String, ParseFieldOperation> operationSet = (Map) i$.next();
                        JSONObject operationSetJSON = new JSONObject();
                        for (String key2 : operationSet.keySet()) {
                            operationSetJSON.put(key2, ((ParseFieldOperation) operationSet.get(key2)).encode(objectEncoder));
                        }
                        operations.put(operationSetJSON);
                    }
                    objectJSON.put("operations", operations);
                }
            } catch (JSONException e) {
                throw new RuntimeException("could not serialize object to JSON");
            }
        }
        return objectJSON;
    }

    /* access modifiers changed from: 0000 */
    public JSONObject toJSONObjectForSaving(Map<String, ParseFieldOperation> operations, ParseObjectEncodingStrategy objectEncoder) {
        JSONObject objectJSON;
        synchronized (this.mutex) {
            objectJSON = new JSONObject();
            JSONObject dataJSON = new JSONObject();
            try {
                for (String key : operations.keySet()) {
                    ParseFieldOperation operation = (ParseFieldOperation) operations.get(key);
                    dataJSON.put(key, Parse.encode(operation, objectEncoder));
                    if (operation instanceof ParseSetOperation) {
                        Object object = ((ParseSetOperation) operation).getValue();
                        if (Parse.isContainerObject(object) && this.hashedObjects.containsKey(object)) {
                            this.hashedObjects.put(object, new ParseJSONCacheItem(object));
                        }
                    }
                }
                if (this.objectId != null) {
                    dataJSON.put("objectId", this.objectId);
                }
                objectJSON.put("data", dataJSON);
                objectJSON.put("classname", this.className);
            } catch (JSONException e) {
                throw new RuntimeException("could not serialize object to JSON");
            }
        }
        return objectJSON;
    }

    /* access modifiers changed from: 0000 */
    public void handleSaveResult(String op, JSONObject result, Map<String, ParseFieldOperation> operationsBeforeSave) {
        synchronized (this.mutex) {
            mergeAfterSave(result, new KnownParseObjectDecoder(collectFetchedObjects()), op.equals("create") || op.equals("user_signup"), operationsBeforeSave);
            this.saveEvent.invoke(this, null);
        }
    }

    /* access modifiers changed from: 0000 */
    public Map<String, ParseFieldOperation> startSave() {
        Map<String, ParseFieldOperation> currentOperations;
        synchronized (this.mutex) {
            currentOperations = currentOperations();
            this.operationSetQueue.addLast(new HashMap());
        }
        return currentOperations;
    }

    /* access modifiers changed from: 0000 */
    public void validateSave() {
    }

    public final void save() throws ParseException {
        Parse.waitForTask(saveAsync());
    }

    /* access modifiers changed from: 0000 */
    public Task<Void> saveAsync(Task<Void> toAwait) {
        final Capture<Map<String, ParseFieldOperation>> operations = new Capture<>();
        if (!isDirty()) {
            return Task.forResult(null);
        }
        final String sessionToken = ParseUser.getCurrentSessionToken();
        return Task.forResult(null).onSuccessTask(new Continuation<Void, Task<Void>>() {
            public Task<Void> then(Task<Void> task) throws Exception {
                synchronized (ParseObject.this.mutex) {
                    ParseObject.this.validateSave();
                    operations.set(ParseObject.this.startSave());
                    if (ParseObject.this.isDataAvailable("ACL") && ParseObject.this.getACL(false) != null && ParseObject.this.getACL(false).hasUnresolvedUser()) {
                        task = ParseUser.getCurrentUser().saveAsync().onSuccess(new Continuation<Void, Void>() {
                            public Void then(Task<Void> task) throws Exception {
                                if (!ParseObject.this.getACL(false).hasUnresolvedUser()) {
                                    return null;
                                }
                                throw new IllegalStateException("ACL has an unresolved ParseUser. Save or sign up before attempting to serialize the ACL.");
                            }
                        });
                    }
                }
                return task;
            }
        }).onSuccessTask(new Continuation<Void, Task<Void>>() {
            public Task<Void> then(Task<Void> task) throws Exception {
                Task<Void> access$200;
                synchronized (ParseObject.this.mutex) {
                    access$200 = ParseObject.deepSaveAsync(ParseObject.this.estimatedData, sessionToken);
                }
                return access$200;
            }
        }).onSuccessTask(TaskQueue.waitFor(toAwait)).onSuccessTask(new Continuation<Void, Task<Void>>() {
            public Task<Void> then(Task<Void> task) throws Exception {
                final ParseCommand command = ParseObject.this.constructSaveCommand((Map) operations.get(), PointerEncodingStrategy.get(), sessionToken);
                return command.executeAsync().continueWithTask(new Continuation<Object, Task<Void>>() {
                    public Task<Void> then(Task<Object> task) throws Exception {
                        ParseObject.this.handleSaveResult(command.getOp(), (JSONObject) task.getResult(), (Map) operations.get());
                        return task.makeVoid();
                    }
                });
            }
        });
    }

    /* access modifiers changed from: 0000 */
    public final Task<Void> saveAsync() {
        return this.taskQueue.enqueue(new Continuation<Void, Task<Void>>() {
            public Task<Void> then(Task<Void> task) throws Exception {
                return ParseObject.this.saveAsync(task);
            }
        });
    }

    public final void saveInBackground(SaveCallback callback) {
        Parse.callbackOnMainThreadAsync(saveAsync(), callback);
    }

    public final void saveInBackground() {
        saveInBackground(null);
    }

    public final void saveEventually() {
        saveEventually(null);
    }

    public void saveEventually(SaveCallback callback) {
        if (!isDirty()) {
            Parse.getCommandCache().fakeObjectUpdate();
            Parse.callbackOnMainThreadAsync(Task.forResult(null).makeVoid(), callback);
            return;
        }
        synchronized (this.mutex) {
            List<ParseObject> unsavedChildren = new ArrayList<>();
            findUnsavedChildren(this.estimatedData, unsavedChildren);
            String localId2 = null;
            if (getObjectId() == null) {
                localId2 = getOrCreateLocalId();
            }
            final Map<String, ParseFieldOperation> operations = startSave();
            final ParseCommandCache cache = Parse.getCommandCache();
            try {
                final ParseCommand command = constructSaveCommand(operations, PointerOrLocalIdEncodingStrategy.get(), ParseUser.getCurrentSessionToken());
                command.setLocalId(localId2);
                command.retainLocalIds();
                for (ParseObject object : unsavedChildren) {
                    object.saveEventually();
                }
                final Capture<Boolean> succeeded = new Capture<>(Boolean.valueOf(false));
                Parse.callbackOnMainThreadAsync(cache.runEventuallyAsync(command, this).continueWith(new Continuation<Object, Void>() {
                    public Void then(Task<Object> task) throws Exception {
                        if (task.getResult() != null) {
                            ParseObject.this.handleSaveResult(command.getOp(), (JSONObject) task.getResult(), operations);
                            succeeded.set(Boolean.valueOf(true));
                        }
                        return null;
                    }
                }), callback).continueWithTask(new Continuation<Void, Task<Void>>() {
                    public Task<Void> then(Task<Void> task) throws Exception {
                        if (((Boolean) succeeded.get()).booleanValue()) {
                            cache.getTestHelper().notify(5);
                        }
                        return task;
                    }
                });
                command.releaseLocalIds();
            } catch (ParseException exception) {
                throw new IllegalStateException("Unable to saveEventually.", exception);
            }
        }
    }

    public final void deleteEventually() {
        deleteEventually(null);
    }

    public final void deleteEventually(DeleteCallback callback) {
        synchronized (this.mutex) {
            final ParseCommandCache cache = Parse.getCommandCache();
            try {
                Parse.callbackOnMainThreadAsync(cache.runEventuallyAsync(constructDeleteCommand(false, ParseUser.getCurrentSessionToken()), this).makeVoid(), callback).continueWithTask(new Continuation<Void, Task<Void>>() {
                    public Task<Void> then(Task<Void> task) throws Exception {
                        cache.getTestHelper().notify(6);
                        return task;
                    }
                });
                this.dirty = true;
            } catch (ParseException e) {
                throw new IllegalStateException("Cannot deleteEventually this object.", e);
            }
        }
    }

    /* access modifiers changed from: 0000 */
    public void handleFetchResult(JSONObject result) {
        synchronized (this.mutex) {
            this.serverData.clear();
            this.dataAvailability.clear();
            mergeAfterFetch(result, new KnownParseObjectDecoder(collectFetchedObjects()), true);
        }
    }

    public final void refresh() throws ParseException {
        fetch();
    }

    public final void refreshInBackground(RefreshCallback callback) {
        Parse.callbackOnMainThreadAsync(fetchAsync(), callback);
    }

    public <T extends ParseObject> T fetch() throws ParseException {
        return (ParseObject) Parse.waitForTask(fetchAsync());
    }

    /* access modifiers changed from: 0000 */
    public <T extends ParseObject> Task<T> fetchAsync(Task<Void> toAwait) {
        final String sessionToken = ParseUser.getCurrentSessionToken();
        return Task.call(new Callable<ParseCommand>() {
            public ParseCommand call() throws Exception {
                ParseCommand command;
                synchronized (ParseObject.this.mutex) {
                    command = new ParseCommand("get", sessionToken);
                    command.enableRetrying();
                    command.put("classname", ParseObject.this.className);
                    JSONObject data = new JSONObject();
                    try {
                        data.put("objectId", ParseObject.this.objectId);
                        command.put("data", data);
                    } catch (JSONException e) {
                        throw new RuntimeException(e.getMessage());
                    }
                }
                return command;
            }
        }).onSuccessTask(TaskQueue.waitFor(toAwait)).onSuccessTask(new Continuation<ParseCommand, Task<JSONObject>>() {
            public Task<JSONObject> then(Task<ParseCommand> task) throws Exception {
                return ((ParseCommand) task.getResult()).executeAsync().cast();
            }
        }).onSuccess(new Continuation<JSONObject, T>() {
            public T then(Task<JSONObject> task) throws Exception {
                ParseObject.this.handleFetchResult((JSONObject) task.getResult());
                return ParseObject.this;
            }
        });
    }

    /* access modifiers changed from: 0000 */
    public final <T extends ParseObject> Task<T> fetchAsync() {
        return this.taskQueue.enqueue(new Continuation<Void, Task<T>>() {
            public Task<T> then(Task<Void> task) throws Exception {
                return ParseObject.this.fetchAsync(task);
            }
        });
    }

    public final <T extends ParseObject> void fetchInBackground(GetCallback<T> callback) {
        Parse.callbackOnMainThreadAsync(fetchAsync(), callback);
    }

    /* access modifiers changed from: 0000 */
    public final <T extends ParseObject> Task<T> fetchIfNeededAsync() {
        Task<T> fetchAsync;
        synchronized (this.mutex) {
            if (isDataAvailable()) {
                fetchAsync = Task.forResult(this);
            } else {
                fetchAsync = fetchAsync();
            }
        }
        return fetchAsync;
    }

    public <T extends ParseObject> T fetchIfNeeded() throws ParseException {
        return (ParseObject) Parse.waitForTask(fetchIfNeededAsync());
    }

    public final <T extends ParseObject> void fetchIfNeededInBackground(GetCallback<T> callback) {
        Parse.callbackOnMainThreadAsync(fetchIfNeededAsync(), callback);
    }

    /* access modifiers changed from: private */
    public ParseCommand constructDeleteCommand(boolean requireObjectId, String sessionToken) throws ParseException {
        ParseCommand command;
        synchronized (this.mutex) {
            command = new ParseCommand("delete", sessionToken);
            command.enableRetrying();
            command.put("classname", this.className);
            JSONObject data = new JSONObject();
            try {
                data.put("objectId", this.objectId);
                command.put("data", data);
            } catch (JSONException e) {
                throw new RuntimeException(e.getMessage());
            }
        }
        return command;
    }

    /* access modifiers changed from: 0000 */
    public void validateDelete() {
    }

    /* access modifiers changed from: private */
    public Task<Void> deleteAsync(Task<Void> toAwait) {
        final String sessionToken = ParseUser.getCurrentSessionToken();
        return Task.call(new Callable<ParseCommand>() {
            public ParseCommand call() throws Exception {
                ParseCommand access$600;
                synchronized (ParseObject.this.mutex) {
                    ParseObject.this.validateDelete();
                    if (ParseObject.this.objectId == null) {
                        access$600 = null;
                    } else {
                        access$600 = ParseObject.this.constructDeleteCommand(true, sessionToken);
                    }
                }
                return access$600;
            }
        }).onSuccessTask(TaskQueue.waitFor(toAwait)).onSuccessTask(new Continuation<ParseCommand, Task<Object>>() {
            public Task<Object> then(Task<ParseCommand> task) throws Exception {
                return ((ParseCommand) task.getResult()).executeAsync();
            }
        }).onSuccess(new Continuation<Object, Void>() {
            public Void then(Task<Object> task) throws Exception {
                synchronized (ParseObject.this.mutex) {
                    ParseObject.this.dirty = true;
                }
                return null;
            }
        });
    }

    private Task<Void> deleteAsync() {
        return this.taskQueue.enqueue(new Continuation<Void, Task<Void>>() {
            public Task<Void> then(Task<Void> task) throws Exception {
                return ParseObject.this.deleteAsync(task);
            }
        });
    }

    public final void delete() throws ParseException {
        Parse.waitForTask(deleteAsync());
    }

    public final void deleteInBackground(DeleteCallback callback) {
        Parse.callbackOnMainThreadAsync(deleteAsync(), callback);
    }

    public final void deleteInBackground() {
        deleteInBackground(null);
    }

    private static Task<Void> deleteAllAsync(List<ParseObject> objects, final String sessionToken) {
        final List<ParseObject> uniqueObjects = new ArrayList<>();
        HashSet<String> idSet = new HashSet<>();
        for (ParseObject obj : objects) {
            if (!idSet.contains(obj.getObjectId())) {
                idSet.add(obj.getObjectId());
                uniqueObjects.add(obj);
            }
        }
        return Task.forResult(null).continueWithTask(new Continuation<Void, Task<Void>>() {
            public Task<Void> then(Task<Void> task) throws Exception {
                if (uniqueObjects.size() == 0) {
                    return Task.forResult(null);
                }
                return ParseObject.enqueueForAll(uniqueObjects, new Continuation<Void, Task<Void>>() {
                    public Task<Void> then(Task<Void> toAwait) throws Exception {
                        return toAwait.continueWithTask(new Continuation<Void, Task<Void>>() {
                            public Task<Void> then(Task<Void> task) throws Exception {
                                JSONArray commands = new JSONArray();
                                for (int i = 0; i < uniqueObjects.size(); i++) {
                                    commands.put(((ParseObject) uniqueObjects.get(i)).constructDeleteCommand(true, sessionToken).toJSONObject());
                                }
                                ParseCommand multiCommand = new ParseCommand("multi", sessionToken);
                                multiCommand.put("commands", commands);
                                return multiCommand.executeAsync().makeVoid();
                            }
                        });
                    }
                });
            }
        });
    }

    public static void deleteAll(List<ParseObject> objects) throws ParseException {
        Parse.waitForTask(deleteAllAsync(objects, ParseUser.getCurrentSessionToken()));
    }

    public static void deleteAllInBackground(List<ParseObject> objects, DeleteCallback callback) {
        Parse.callbackOnMainThreadAsync(deleteAllAsync(objects, ParseUser.getCurrentSessionToken()), callback);
    }

    private static void collectDirtyChildren(Object node, List<ParseObject> dirtyChildren, List<ParseFile> dirtyFiles, IdentityHashMap<ParseObject, ParseObject> seen, IdentityHashMap<ParseObject, ParseObject> seenNew) {
        IdentityHashMap identityHashMap;
        if (node instanceof List) {
            for (Object item : (List) node) {
                collectDirtyChildren(item, dirtyChildren, dirtyFiles, seen, seenNew);
            }
        } else if (node instanceof Map) {
            for (Object value : ((Map) node).values()) {
                collectDirtyChildren(value, dirtyChildren, dirtyFiles, seen, seenNew);
            }
        } else if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            int i = 0;
            while (i < array.length()) {
                try {
                    collectDirtyChildren(array.get(i), dirtyChildren, dirtyFiles, seen, seenNew);
                    i++;
                } catch (JSONException e) {
                    RuntimeException runtimeException = new RuntimeException("Invalid JSONArray on object.", e);
                    throw runtimeException;
                }
            }
        } else if (node instanceof JSONObject) {
            JSONObject dictionary = (JSONObject) node;
            Iterator<String> keys = dictionary.keys();
            while (keys.hasNext()) {
                try {
                    collectDirtyChildren(dictionary.get((String) keys.next()), dirtyChildren, dirtyFiles, seen, seenNew);
                } catch (JSONException e2) {
                    RuntimeException runtimeException2 = new RuntimeException("Invalid JSONDictionary on object.", e2);
                    throw runtimeException2;
                }
            }
        } else if (node instanceof ParseACL) {
            if (((ParseACL) node).hasUnresolvedUser()) {
                collectDirtyChildren(ParseUser.getCurrentUser(), dirtyChildren, dirtyFiles, seen, seenNew);
            }
        } else if (node instanceof ParseObject) {
            ParseObject object = (ParseObject) node;
            if (object.getObjectId() != null) {
                identityHashMap = new IdentityHashMap();
            } else if (seenNew.containsKey(object)) {
                throw new RuntimeException("Found a circular dependency while saving.");
            } else {
                IdentityHashMap identityHashMap2 = new IdentityHashMap(seenNew);
                identityHashMap2.put(object, object);
                identityHashMap = identityHashMap2;
            }
            if (!seen.containsKey(object)) {
                IdentityHashMap identityHashMap3 = new IdentityHashMap(seen);
                identityHashMap3.put(object, object);
                collectDirtyChildren(object.estimatedData, dirtyChildren, dirtyFiles, identityHashMap3, identityHashMap);
                if (object.isDirty(false)) {
                    dirtyChildren.add(object);
                }
                IdentityHashMap identityHashMap4 = identityHashMap3;
            }
        } else if (node instanceof ParseFile) {
            ParseFile file = (ParseFile) node;
            if (file.getUrl() == null) {
                dirtyFiles.add(file);
            }
        }
    }

    private static void collectDirtyChildren(Object node, List<ParseObject> dirtyChildren, List<ParseFile> dirtyFiles) {
        collectDirtyChildren(node, dirtyChildren, dirtyFiles, new IdentityHashMap(), new IdentityHashMap());
    }

    private static boolean canBeSerializedAsValue(Object value) {
        if (value instanceof ParseObject) {
            return ((ParseObject) value).getObjectId() != null;
        }
        if (value instanceof Map) {
            for (Object item : ((Map) value).values()) {
                if (!canBeSerializedAsValue(item)) {
                    return false;
                }
            }
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            int i = 0;
            while (i < array.length()) {
                try {
                    if (!canBeSerializedAsValue(array.get(i))) {
                        return false;
                    }
                    i++;
                } catch (JSONException e) {
                    throw new RuntimeException("Unable to find related objects for saving.", e);
                }
            }
        } else if (value instanceof JSONObject) {
            JSONObject dictionary = (JSONObject) value;
            Iterator<String> keys = dictionary.keys();
            while (keys.hasNext()) {
                try {
                    if (!canBeSerializedAsValue(dictionary.get((String) keys.next()))) {
                        return false;
                    }
                } catch (JSONException e2) {
                    throw new RuntimeException("Unable to find related objects for saving.", e2);
                }
            }
        } else if ((value instanceof ParseACL) && ((ParseACL) value).hasUnresolvedUser() && !canBeSerializedAsValue(ParseUser.getCurrentUser())) {
            return false;
        }
        return true;
    }

    /* access modifiers changed from: private */
    public boolean canBeSerialized() {
        boolean z = false;
        synchronized (this.mutex) {
            if (canBeSerializedAsValue(this.estimatedData)) {
                if (!isDataAvailable("ACL") || getACL(false) == null || !getACL(false).hasUnresolvedUser()) {
                    z = true;
                }
            }
        }
        return z;
    }

    /* access modifiers changed from: private */
    public static Task<Void> deepSaveAsync(Object object, final String sessionToken) {
        final List<ParseObject> objects = new ArrayList<>();
        List<ParseFile> files = new ArrayList<>();
        collectDirtyChildren(object, objects, files);
        List<Task<Void>> fileSaveTasks = new ArrayList<>();
        for (ParseFile file : files) {
            fileSaveTasks.add(file.saveAsync(null));
        }
        return Task.whenAll(fileSaveTasks).onSuccessTask(new Continuation<Void, Task<Void>>() {
            public Task<Void> then(Task<Void> task) throws Exception {
                IdentityHashMap<ParseObject, Boolean> uniqueObjects = new IdentityHashMap<>();
                for (ParseObject obj : objects) {
                    uniqueObjects.put(obj, Boolean.valueOf(true));
                }
                final Capture<List<ParseObject>> remaining = new Capture<>(new ArrayList(uniqueObjects.keySet()));
                return Task.forResult(null).continueWhile(new Callable<Boolean>() {
                    public Boolean call() throws Exception {
                        return Boolean.valueOf(((List) remaining.get()).size() > 0);
                    }
                }, new Continuation<Void, Task<Void>>() {
                    public Task<Void> then(Task<Void> task) throws Exception {
                        final List<ParseObject> current = new ArrayList<>();
                        List<ParseObject> nextBatch = new ArrayList<>();
                        for (ParseObject obj : (List) remaining.get()) {
                            if (obj.canBeSerialized()) {
                                current.add(obj);
                            } else {
                                nextBatch.add(obj);
                            }
                        }
                        remaining.set(nextBatch);
                        if (current.size() == 0) {
                            throw new RuntimeException("Unable to save a PFObject with a relation to a cycle.");
                        }
                        Task<Void> result = Task.forResult(null);
                        if (ParseUser.getCurrentUser() != null && ParseUser.getCurrentUser().isLazy() && current.contains(ParseUser.getCurrentUser())) {
                            result = result.onSuccessTask(new Continuation<Void, Task<Void>>() {
                                public Task<Void> then(Task<Void> task) throws Exception {
                                    return ParseUser.getCurrentUser().saveAsync();
                                }
                            }).onSuccess(new Continuation<Void, Void>() {
                                public Void then(Task<Void> task) throws Exception {
                                    current.remove(ParseUser.getCurrentUser());
                                    return null;
                                }
                            });
                        }
                        final List<String> ops = new ArrayList<>();
                        final List<Map<String, ParseFieldOperation>> operations = new ArrayList<>();
                        return result.onSuccessTask(new Continuation<Void, Task<Void>>() {
                            public Task<Void> then(Task<Void> task) throws Exception {
                                if (current.size() == 0) {
                                    return Task.forResult(null);
                                }
                                return ParseObject.enqueueForAll(current, new Continuation<Void, Task<Void>>() {
                                    public Task<Void> then(Task<Void> toAwait) throws Exception {
                                        for (ParseObject obj : current) {
                                            synchronized (obj.mutex) {
                                                obj.validateSave();
                                                operations.add(obj.startSave());
                                            }
                                        }
                                        return toAwait.continueWithTask(new Continuation<Void, Task<Void>>() {
                                            public Task<Void> then(Task<Void> task) throws Exception {
                                                JSONArray commands = new JSONArray();
                                                for (int i = 0; i < current.size(); i++) {
                                                    ParseCommand command = ((ParseObject) current.get(i)).constructSaveCommand((Map) operations.get(i), PointerEncodingStrategy.get(), sessionToken);
                                                    commands.put(command.toJSONObject());
                                                    ops.add(command.getOp());
                                                }
                                                ParseCommand multiCommand = new ParseCommand("multi", sessionToken);
                                                multiCommand.put("commands", commands);
                                                return multiCommand.executeAsync().cast().onSuccess(new Continuation<JSONArray, Void>() {
                                                    public Void then(Task<JSONArray> task) throws Exception {
                                                        for (int i = 0; i < current.size(); i++) {
                                                            ((ParseObject) current.get(i)).handleSaveResult((String) ops.get(i), ((JSONArray) task.getResult()).getJSONObject(i), (Map) operations.get(i));
                                                        }
                                                        return null;
                                                    }
                                                });
                                            }
                                        });
                                    }
                                });
                            }
                        });
                    }
                });
            }
        });
    }

    private static Task<Void> saveAllAsync(List<ParseObject> objects) {
        return deepSaveAsync(objects, ParseUser.getCurrentSessionToken());
    }

    public static void saveAll(List<ParseObject> objects) throws ParseException {
        Parse.waitForTask(saveAllAsync(objects));
    }

    /* access modifiers changed from: private */
    public static <T extends ParseObject> Task<List<T>> fetchAllIfNeededAsync(final List<T> objects, Task<Void> toAwait) {
        List<String> ids = new ArrayList<>();
        String className2 = null;
        for (T object : objects) {
            if (!object.isDataAvailable()) {
                if (className2 == null || className2.equals(object.getClassName())) {
                    className2 = object.getClassName();
                    String id = object.getObjectId();
                    if (id != null) {
                        ids.add(id);
                    }
                } else {
                    throw new IllegalArgumentException("All objects should have the same class");
                }
            }
        }
        if (ids.size() == 0) {
            return Task.forResult(objects);
        }
        final ParseQuery<T> query = ParseQuery.getQuery(className2);
        query.whereContainedIn("objectId", ids);
        return toAwait.continueWithTask(new Continuation<Void, Task<List<T>>>() {
            public Task<List<T>> then(Task<Void> task) throws Exception {
                return query.findWithCachePolicyAsync(CachePolicy.IGNORE_CACHE);
            }
        }).onSuccess(new Continuation<List<T>, List<T>>() {
            public List<T> then(Task<List<T>> task) throws Exception {
                Map<String, T> resultMap = new HashMap<>();
                for (T o : (List) task.getResult()) {
                    resultMap.put(o.getObjectId(), o);
                }
                for (int i = 0; i < objects.size(); i++) {
                    if (!((ParseObject) objects.get(i)).isDataAvailable()) {
                        T newObject = (ParseObject) resultMap.get(((ParseObject) objects.get(i)).getObjectId());
                        if (newObject == null) {
                            throw new RuntimeException("Object id " + ((ParseObject) objects.get(i)).getObjectId() + " does not exist");
                        }
                        ((ParseObject) objects.get(i)).mergeFromObject(newObject);
                        ((ParseObject) objects.get(i)).hasBeenFetched = true;
                    }
                }
                return objects;
            }
        });
    }

    private static <T extends ParseObject> Task<List<T>> fetchAllIfNeededAsync(final List<T> objects) {
        return enqueueForAll(objects, new Continuation<Void, Task<List<T>>>() {
            public Task<List<T>> then(Task<Void> task) throws Exception {
                return ParseObject.fetchAllIfNeededAsync(objects, task);
            }
        });
    }

    public static <T extends ParseObject> List<T> fetchAllIfNeeded(List<T> objects) throws ParseException {
        return (List) Parse.waitForTask(fetchAllIfNeededAsync(objects));
    }

    public static <T extends ParseObject> void fetchAllIfNeededInBackground(List<T> objects, FindCallback<T> callback) {
        Parse.callbackOnMainThreadAsync(fetchAllIfNeededAsync(objects), callback);
    }

    /* access modifiers changed from: private */
    public static <T extends ParseObject> Task<List<T>> fetchAllAsync(final List<T> objects, Task<Void> toAwait) {
        if (objects.size() == 0) {
            return Task.forResult(objects);
        }
        List<String> ids = new ArrayList<>();
        String className2 = ((ParseObject) objects.get(0)).getClassName();
        int i = 0;
        while (i < objects.size()) {
            if (!((ParseObject) objects.get(i)).getClassName().equals(className2)) {
                throw new IllegalArgumentException("All objects should have the same class");
            } else if (((ParseObject) objects.get(i)).getObjectId() == null) {
                throw new IllegalArgumentException("All objects must exist on the server");
            } else {
                ids.add(((ParseObject) objects.get(i)).getObjectId());
                i++;
            }
        }
        final ParseQuery<T> query = ParseQuery.getQuery(className2);
        query.whereContainedIn("objectId", ids);
        return toAwait.continueWithTask(new Continuation<Void, Task<List<T>>>() {
            public Task<List<T>> then(Task<Void> task) throws Exception {
                return query.findWithCachePolicyAsync(CachePolicy.IGNORE_CACHE);
            }
        }).onSuccess(new Continuation<List<T>, List<T>>() {
            public List<T> then(Task<List<T>> task) throws Exception {
                Map<String, T> resultMap = new HashMap<>();
                for (T o : (List) task.getResult()) {
                    resultMap.put(o.getObjectId(), o);
                }
                for (int i = 0; i < objects.size(); i++) {
                    ParseObject newObject = (ParseObject) resultMap.get(((ParseObject) objects.get(i)).getObjectId());
                    if (newObject == null) {
                        throw new RuntimeException("Object id " + ((ParseObject) objects.get(i)).getObjectId() + " does not exist");
                    }
                    ((ParseObject) objects.get(i)).mergeFromObject(newObject);
                    ((ParseObject) objects.get(i)).hasBeenFetched = true;
                }
                return objects;
            }
        });
    }

    private static <T extends ParseObject> Task<List<T>> fetchAllAsync(final List<T> objects) {
        return enqueueForAll(objects, new Continuation<Void, Task<List<T>>>() {
            public Task<List<T>> then(Task<Void> task) throws Exception {
                return ParseObject.fetchAllAsync(objects, task);
            }
        });
    }

    public static List<ParseObject> fetchAll(List<ParseObject> objects) throws ParseException {
        return (List) Parse.waitForTask(fetchAllAsync(objects));
    }

    public static <T extends ParseObject> void fetchAllInBackground(List<T> objects, FindCallback<T> callback) {
        Parse.callbackOnMainThreadAsync(fetchAllAsync(objects), callback);
    }

    public static void saveAllInBackground(List<ParseObject> objects, SaveCallback callback) {
        Parse.callbackOnMainThreadAsync(saveAllAsync(objects), callback);
    }

    public static void saveAllInBackground(List<ParseObject> objects) {
        saveAllInBackground(objects, null);
    }

    public void remove(String key) {
        synchronized (this.mutex) {
            if (get(key) != null) {
                performOperation(key, ParseDeleteOperation.getInstance());
            }
        }
    }

    public boolean has(String key) {
        return containsKey(key);
    }

    private Map<String, ParseFieldOperation> currentOperations() {
        Map<String, ParseFieldOperation> map;
        synchronized (this.mutex) {
            map = (Map) this.operationSetQueue.getLast();
        }
        return map;
    }

    private void applyOperations(Map<String, ParseFieldOperation> operations, Map<String, Object> map) {
        synchronized (this.mutex) {
            for (String key : operations.keySet()) {
                Object newValue = ((ParseFieldOperation) operations.get(key)).apply(map.get(key), this, key);
                if (newValue != null) {
                    map.put(key, newValue);
                } else {
                    map.remove(key);
                }
            }
        }
    }

    private void rebuildEstimatedData() {
        synchronized (this.mutex) {
            this.estimatedData.clear();
            this.estimatedData.putAll(this.serverData);
            Iterator i$ = this.operationSetQueue.iterator();
            while (i$.hasNext()) {
                applyOperations((Map) i$.next(), this.estimatedData);
            }
        }
    }

    /* access modifiers changed from: 0000 */
    public void performOperation(String key, ParseFieldOperation operation) {
        synchronized (this.mutex) {
            Object newValue = operation.apply(this.estimatedData.get(key), this, key);
            if (newValue != null) {
                this.estimatedData.put(key, newValue);
            } else {
                this.estimatedData.remove(key);
            }
            currentOperations().put(key, operation.mergeWithPrevious((ParseFieldOperation) currentOperations().get(key)));
            checkpointMutableContainer(newValue);
            this.dataAvailability.put(key, Boolean.TRUE);
        }
    }

    public void put(String key, Object value) {
        if (key == null) {
            throw new IllegalArgumentException("key may not be null.");
        } else if (value == null) {
            throw new IllegalArgumentException("value may not be null.");
        } else if (!Parse.isValidType(value)) {
            throw new IllegalArgumentException("invalid type for value: " + value.getClass().toString());
        } else {
            performOperation(key, new ParseSetOperation(value));
        }
    }

    public void increment(String key) {
        increment(key, Integer.valueOf(1));
    }

    public void increment(String key, Number amount) {
        performOperation(key, new ParseIncrementOperation(amount));
    }

    public void add(String key, Object value) {
        addAll(key, Arrays.asList(new Object[]{value}));
    }

    public void addAll(String key, Collection<?> values) {
        performOperation(key, new ParseAddOperation(values));
    }

    public void addUnique(String key, Object value) {
        addAllUnique(key, Arrays.asList(new Object[]{value}));
    }

    public void addAllUnique(String key, Collection<?> values) {
        performOperation(key, new ParseAddUniqueOperation(values));
    }

    public void removeAll(String key, Collection<?> values) {
        performOperation(key, new ParseRemoveOperation(values));
    }

    public boolean containsKey(String key) {
        boolean containsKey;
        synchronized (this.mutex) {
            containsKey = this.estimatedData.containsKey(key);
        }
        return containsKey;
    }

    public String getString(String key) {
        synchronized (this.mutex) {
            checkGetAccess(key);
            if (!this.estimatedData.containsKey(key)) {
                return null;
            }
            Object value = this.estimatedData.get(key);
            if (!(value instanceof String)) {
                return null;
            }
            String str = (String) value;
            return str;
        }
    }

    public byte[] getBytes(String key) {
        synchronized (this.mutex) {
            checkGetAccess(key);
            if (!this.estimatedData.containsKey(key)) {
                return null;
            }
            Object value = this.estimatedData.get(key);
            if (!(value instanceof byte[])) {
                return null;
            }
            byte[] bArr = (byte[]) value;
            return bArr;
        }
    }

    public Number getNumber(String key) {
        synchronized (this.mutex) {
            checkGetAccess(key);
            if (!this.estimatedData.containsKey(key)) {
                return null;
            }
            Object value = this.estimatedData.get(key);
            if (!(value instanceof Number)) {
                return null;
            }
            Number number = (Number) value;
            return number;
        }
    }

    public JSONArray getJSONArray(String key) {
        synchronized (this.mutex) {
            checkGetAccess(key);
            if (!this.estimatedData.containsKey(key)) {
                return null;
            }
            Object value = this.estimatedData.get(key);
            if (value instanceof List) {
                value = Parse.encode(value, PointerOrLocalIdEncodingStrategy.get());
                put(key, value);
            }
            if (!(value instanceof JSONArray)) {
                return null;
            }
            JSONArray jSONArray = (JSONArray) value;
            return jSONArray;
        }
    }

    public <T> List<T> getList(String key) {
        List<T> list = null;
        synchronized (this.mutex) {
            if (this.estimatedData.containsKey(key)) {
                Object value = this.estimatedData.get(key);
                if (value instanceof JSONArray) {
                    value = new ParseDecoder().convertJSONArrayToList((JSONArray) value);
                    put(key, value);
                }
                if (value instanceof List) {
                    list = (List) value;
                }
            }
        }
        return list;
    }

    public <V> Map<String, V> getMap(String key) {
        Map<String, V> map = null;
        synchronized (this.mutex) {
            if (this.estimatedData.containsKey(key)) {
                Object value = this.estimatedData.get(key);
                if (value instanceof JSONObject) {
                    value = new ParseDecoder().convertJSONObjectToMap((JSONObject) value);
                    put(key, value);
                }
                if (value instanceof Map) {
                    map = (Map) value;
                }
            }
        }
        return map;
    }

    public JSONObject getJSONObject(String key) {
        synchronized (this.mutex) {
            checkGetAccess(key);
            if (!this.estimatedData.containsKey(key)) {
                return null;
            }
            Object value = this.estimatedData.get(key);
            if (value instanceof Map) {
                value = Parse.encode(value, PointerOrLocalIdEncodingStrategy.get());
                put(key, value);
            }
            if (!(value instanceof JSONObject)) {
                return null;
            }
            JSONObject jSONObject = (JSONObject) value;
            return jSONObject;
        }
    }

    public int getInt(String key) {
        Number number = getNumber(key);
        if (number == null) {
            return 0;
        }
        return number.intValue();
    }

    public double getDouble(String key) {
        Number number = getNumber(key);
        if (number == null) {
            return 0.0d;
        }
        return number.doubleValue();
    }

    public long getLong(String key) {
        Number number = getNumber(key);
        if (number == null) {
            return 0;
        }
        return number.longValue();
    }

    public boolean getBoolean(String key) {
        boolean z = false;
        synchronized (this.mutex) {
            checkGetAccess(key);
            if (this.estimatedData.containsKey(key)) {
                Object value = this.estimatedData.get(key);
                if (value instanceof Boolean) {
                    z = ((Boolean) value).booleanValue();
                }
            }
        }
        return z;
    }

    public Date getDate(String key) {
        synchronized (this.mutex) {
            checkGetAccess(key);
            if (!this.estimatedData.containsKey(key)) {
                return null;
            }
            Object value = this.estimatedData.get(key);
            if (!(value instanceof Date)) {
                return null;
            }
            Date date = (Date) value;
            return date;
        }
    }

    public ParseObject getParseObject(String key) {
        Object value = get(key);
        if (!(value instanceof ParseObject)) {
            return null;
        }
        return (ParseObject) value;
    }

    public ParseUser getParseUser(String key) {
        Object value = get(key);
        if (!(value instanceof ParseUser)) {
            return null;
        }
        return (ParseUser) value;
    }

    public ParseFile getParseFile(String key) {
        Object value = get(key);
        if (!(value instanceof ParseFile)) {
            return null;
        }
        return (ParseFile) value;
    }

    public ParseGeoPoint getParseGeoPoint(String key) {
        synchronized (this.mutex) {
            checkGetAccess(key);
            if (!this.estimatedData.containsKey(key)) {
                return null;
            }
            Object value = this.estimatedData.get(key);
            if (!(value instanceof ParseGeoPoint)) {
                return null;
            }
            ParseGeoPoint parseGeoPoint = (ParseGeoPoint) value;
            return parseGeoPoint;
        }
    }

    public ParseACL getACL() {
        return getACL(true);
    }

    /* access modifiers changed from: private */
    public ParseACL getACL(boolean mayCopy) {
        synchronized (this.mutex) {
            checkGetAccess("ACL");
            Object acl = this.estimatedData.get("ACL");
            if (acl == null) {
                return null;
            }
            if (!(acl instanceof ParseACL)) {
                throw new RuntimeException("only ACLs can be stored in the ACL key");
            }
            if (mayCopy) {
                if (((ParseACL) acl).isShared()) {
                    ParseACL copy = ((ParseACL) acl).copy();
                    this.estimatedData.put("ACL", copy);
                    addToHashedObjects(copy);
                    return copy;
                }
            }
            ParseACL parseACL = (ParseACL) acl;
            return parseACL;
        }
    }

    public void setACL(ParseACL acl) {
        put("ACL", acl);
    }

    public boolean isDataAvailable() {
        boolean z;
        synchronized (this.mutex) {
            z = this.hasBeenFetched;
        }
        return z;
    }

    /* access modifiers changed from: private */
    public boolean isDataAvailable(String key) {
        boolean z;
        synchronized (this.mutex) {
            z = isDataAvailable() || (this.dataAvailability.containsKey(key) && ((Boolean) this.dataAvailability.get(key)).booleanValue());
        }
        return z;
    }

    public <T extends ParseObject> ParseRelation<T> getRelation(String key) {
        ParseRelation<T> relation;
        synchronized (this.mutex) {
            relation = new ParseRelation<>(this, key);
            Object value = this.estimatedData.get(key);
            if (value instanceof ParseRelation) {
                relation.setTargetClass(((ParseRelation) value).getTargetClass());
            }
        }
        return relation;
    }

    public Object get(String key) {
        Object value;
        synchronized (this.mutex) {
            checkGetAccess(key);
            if (!this.estimatedData.containsKey(key)) {
                value = null;
            } else {
                value = this.estimatedData.get(key);
                if ((value instanceof ParseACL) && key.equals("ACL")) {
                    ParseACL acl = (ParseACL) value;
                    if (acl.isShared()) {
                        ParseACL copy = acl.copy();
                        this.estimatedData.put("ACL", copy);
                        addToHashedObjects(copy);
                        value = getACL();
                    }
                }
                if (value instanceof ParseRelation) {
                    ((ParseRelation) value).ensureParentAndKey(this, key);
                }
            }
        }
        return value;
    }

    private void checkGetAccess(String key) {
        if (!isDataAvailable(key)) {
            throw new IllegalStateException("ParseObject has no data for this key.  Call fetchIfNeeded() to get the data.");
        }
    }

    public boolean hasSameId(ParseObject other) {
        boolean z;
        synchronized (this.mutex) {
            z = getClassName() != null && getObjectId() != null && getClassName().equals(other.getClassName()) && getObjectId().equals(other.getObjectId());
        }
        return z;
    }

    /* access modifiers changed from: 0000 */
    public void registerSaveListener(GetCallback<ParseObject> callback) {
        synchronized (this.mutex) {
            this.saveEvent.subscribe(callback);
        }
    }

    /* access modifiers changed from: 0000 */
    public void unregisterSaveListener(GetCallback<ParseObject> callback) {
        synchronized (this.mutex) {
            this.saveEvent.unsubscribe(callback);
        }
    }

    static String getClassName(Class<? extends ParseObject> clazz) {
        String name = (String) classNames.get(clazz);
        if (name == null) {
            ParseClassName info = (ParseClassName) clazz.getAnnotation(ParseClassName.class);
            if (info == null) {
                return null;
            }
            name = info.value();
            classNames.put(clazz, name);
        }
        return name;
    }

    /* access modifiers changed from: 0000 */
    public void setDefaultValues() {
        if (needsDefaultACL() && ParseACL.getDefaultACL() != null) {
            setACL(ParseACL.getDefaultACL());
        }
    }

    /* access modifiers changed from: 0000 */
    public boolean needsDefaultACL() {
        return true;
    }

    static void registerParseSubclasses() {
        registerSubclass(ParseUser.class);
        registerSubclass(ParseRole.class);
        registerSubclass(ParseInstallation.class);
    }
}
