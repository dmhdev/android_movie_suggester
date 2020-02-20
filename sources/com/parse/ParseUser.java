package com.parse;

import com.parse.ParseAuthenticationProvider.ParseAuthenticationCallback;
import com.parse.ParseFacebookUtils.Permissions.User;
import com.parse.Task.TaskCompletionSource;
import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

@ParseClassName("_User")
public class ParseUser extends ParseObject {
    private static final String CURRENT_USER_FILENAME = "currentUser";
    private static Map<String, ParseAuthenticationProvider> authenticationProviders = new HashMap();
    private static boolean autoUserEnabled;
    private static ParseUser currentUser;
    private static boolean currentUserMatchesDisk = false;
    private static final Object currentUserMutex = new Object();
    /* access modifiers changed from: private */
    public final JSONObject authData = new JSONObject();
    private boolean isCurrentUser = false;
    /* access modifiers changed from: private */
    public boolean isLazy = false;
    /* access modifiers changed from: private */
    public boolean isNew;
    /* access modifiers changed from: private */
    public final Set<String> linkedServiceNames = new HashSet();
    private String password;
    private final Set<String> readOnlyLinkedServiceNames = Collections.unmodifiableSet(this.linkedServiceNames);
    private String sessionToken;

    static ParseUser logInLazyUser(String authType, JSONObject authData2) {
        ParseUser user;
        synchronized (currentUserMutex) {
            user = (ParseUser) ParseObject.create(ParseUser.class);
            user.isCurrentUser = true;
            user.isLazy = true;
            try {
                user.authData.put(authType, authData2);
                user.linkedServiceNames.add(authType);
                currentUser = user;
                currentUserMatchesDisk = false;
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
        return user;
    }

    /* access modifiers changed from: 0000 */
    public boolean isLazy() {
        boolean z;
        synchronized (this.mutex) {
            z = this.isLazy;
        }
        return z;
    }

    public boolean isAuthenticated() {
        boolean z;
        synchronized (this.mutex) {
            z = isLazy() || !(this.sessionToken == null || getCurrentUser() == null || !getObjectId().equals(getCurrentUser().getObjectId()));
        }
        return z;
    }

    public void remove(String key) {
        if ("username".equals(key)) {
            throw new IllegalArgumentException("Can't remove the username key.");
        }
        super.remove(key);
    }

    /* access modifiers changed from: 0000 */
    public JSONObject toJSONObjectForSaving(Map<String, ParseFieldOperation> operations, ParseObjectEncodingStrategy objectEncoder) {
        JSONObject objectJSON;
        synchronized (this.mutex) {
            objectJSON = super.toJSONObjectForSaving(operations, objectEncoder);
            if (this.sessionToken != null) {
                try {
                    objectJSON.put("session_token", this.sessionToken);
                } catch (JSONException e) {
                    throw new RuntimeException("could not attach key: auth_data");
                } catch (JSONException e2) {
                    throw new RuntimeException("could not encode value for key: sessionToken");
                }
            }
            if (this.authData.length() > 0) {
                objectJSON.put("auth_data", this.authData);
            }
        }
        return objectJSON;
    }

    /* access modifiers changed from: 0000 */
    public JSONObject toJSONObjectForDataFile(boolean includeOperations, ParseObjectEncodingStrategy objectEncoder) {
        JSONObject objectJSON;
        synchronized (this.mutex) {
            objectJSON = super.toJSONObjectForDataFile(includeOperations, objectEncoder);
            if (this.sessionToken != null) {
                try {
                    objectJSON.put("session_token", this.sessionToken);
                } catch (JSONException e) {
                    throw new RuntimeException("could not attach key: auth_data");
                } catch (JSONException e2) {
                    throw new RuntimeException("could not encode value for key: sessionToken");
                }
            }
            if (this.authData.length() > 0) {
                objectJSON.put("auth_data", this.authData);
            }
        }
        return objectJSON;
    }

    /* access modifiers changed from: 0000 */
    public void mergeFromObject(ParseObject other) {
        synchronized (this.mutex) {
            super.mergeFromObject(other);
            if (other instanceof ParseUser) {
                this.sessionToken = ((ParseUser) other).sessionToken;
                this.isNew = ((ParseUser) other).isNew();
                Iterator<String> key = this.authData.keys();
                while (key.hasNext()) {
                    key.next();
                    key.remove();
                }
                Iterator<String> key2 = ((ParseUser) other).authData.keys();
                while (key2.hasNext()) {
                    String k = (String) key2.next();
                    try {
                        this.authData.put(k, ((ParseUser) other).authData.get(k));
                    } catch (JSONException e) {
                        throw new RuntimeException("A JSONException occurred where one was not possible.");
                    }
                }
                this.linkedServiceNames.clear();
                this.linkedServiceNames.addAll(((ParseUser) other).linkedServiceNames);
            }
        }
    }

    /* access modifiers changed from: 0000 */
    public void mergeFromServer(JSONObject object, ParseDecoder decoder, boolean completeData) {
        synchronized (this.mutex) {
            super.mergeFromServer(object, decoder, completeData);
            if (object.has("session_token")) {
                try {
                    this.sessionToken = object.getString("session_token");
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                } catch (JSONException e2) {
                    throw new RuntimeException(e2.getMessage());
                } catch (JSONException e3) {
                    throw new RuntimeException(e3);
                }
            }
            if (object.has("auth_data")) {
                JSONObject newData = object.getJSONObject("auth_data");
                Iterator i = newData.keys();
                while (i.hasNext()) {
                    String key = (String) i.next();
                    this.authData.put(key, newData.get(key));
                    if (!newData.isNull(key)) {
                        this.linkedServiceNames.add(key);
                    }
                    synchronizeAuthData(key);
                }
            }
            if (object.has("is_new")) {
                this.isNew = object.getBoolean("is_new");
            }
        }
    }

    /* access modifiers changed from: 0000 */
    public boolean isCurrentUser() {
        boolean z;
        synchronized (this.mutex) {
            z = this.isCurrentUser;
        }
        return z;
    }

    /* access modifiers changed from: 0000 */
    public void cleanUpAuthData() {
        synchronized (this.mutex) {
            if (isCurrentUser()) {
                Iterator<String> i = this.authData.keys();
                while (i.hasNext()) {
                    String key = (String) i.next();
                    if (this.authData.isNull(key)) {
                        i.remove();
                        this.linkedServiceNames.remove(key);
                        if (authenticationProviders.containsKey(key)) {
                            ((ParseAuthenticationProvider) authenticationProviders.get(key)).restoreAuthentication(null);
                        }
                    }
                }
            }
        }
    }

    public void setUsername(String username) {
        put("username", username);
    }

    public String getUsername() {
        return getString("username");
    }

    public void setPassword(String password2) {
        synchronized (this.mutex) {
            this.password = password2;
            this.dirty = true;
        }
    }

    public void setEmail(String email) {
        put(User.EMAIL, email);
    }

    public String getEmail() {
        return getString(User.EMAIL);
    }

    public void put(String key, Object value) {
        synchronized (this.mutex) {
            if ("username".equals(key)) {
                stripAnonymity();
            }
            super.put(key, value);
        }
    }

    /* access modifiers changed from: private */
    public void stripAnonymity() {
        synchronized (this.mutex) {
            if (ParseAnonymousUtils.isLinked(this)) {
                this.linkedServiceNames.remove("anonymous");
                try {
                    this.authData.put("anonymous", JSONObject.NULL);
                    this.dirty = true;
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    /* access modifiers changed from: private */
    public void restoreAnonymity(JSONObject anonymousData) {
        synchronized (this.mutex) {
            if (anonymousData != null) {
                this.linkedServiceNames.add("anonymous");
                try {
                    this.authData.put("anonymous", anonymousData);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public String getSessionToken() {
        String str;
        synchronized (this.mutex) {
            str = this.sessionToken;
        }
        return str;
    }

    /* access modifiers changed from: 0000 */
    public void validateSave() {
        synchronized (this.mutex) {
            if (getObjectId() == null) {
                throw new IllegalArgumentException("Cannot save a ParseUser until it has been signed up. Call signUp first.");
            } else if (!isAuthenticated() && isDirty() && !getObjectId().equals(getCurrentUser().getObjectId())) {
                throw new IllegalArgumentException("Cannot save a ParseUser that is not authenticated.");
            }
        }
    }

    /* access modifiers changed from: 0000 */
    public Task<Void> saveAsync(Task<Void> toAwait) {
        Task<Void> onSuccessTask;
        synchronized (this.mutex) {
            if (isLazy()) {
                onSuccessTask = resolveLazinessAsync(toAwait);
            } else {
                onSuccessTask = super.saveAsync(toAwait).onSuccessTask(new Continuation<Void, Task<Void>>() {
                    public Task<Void> then(Task<Void> task) throws Exception {
                        Task<Void> forResult;
                        synchronized (ParseUser.this.mutex) {
                            ParseUser.this.cleanUpAuthData();
                            if (ParseUser.this.isCurrentUser()) {
                                forResult = ParseUser.saveCurrentUserAsync(ParseUser.this);
                            } else {
                                forResult = Task.forResult(null);
                            }
                        }
                        return forResult;
                    }
                });
            }
        }
        return onSuccessTask;
    }

    /* access modifiers changed from: 0000 */
    public void validateDelete() {
        synchronized (this.mutex) {
            super.validateDelete();
            if (!isAuthenticated() && isDirty()) {
                throw new IllegalArgumentException("Cannot delete a ParseUser that is not authenticated.");
            }
        }
    }

    public ParseUser fetch() throws ParseException {
        return (ParseUser) super.fetch();
    }

    /* access modifiers changed from: 0000 */
    public <T extends ParseObject> Task<T> fetchAsync(Task<Void> toAwait) {
        Task<T> onSuccessTask;
        synchronized (this.mutex) {
            if (isLazy()) {
                onSuccessTask = Task.forResult(this);
            } else {
                onSuccessTask = super.fetchAsync(toAwait).onSuccessTask(new Continuation<T, Task<T>>() {
                    public Task<T> then(final Task<T> fetchAsyncTask) throws Exception {
                        synchronized (ParseUser.this.mutex) {
                            ParseUser.this.cleanUpAuthData();
                            if (ParseUser.this.isCurrentUser()) {
                                fetchAsyncTask = ParseUser.saveCurrentUserAsync(ParseUser.this).continueWithTask(new Continuation<Void, Task<T>>() {
                                    public Task<T> then(Task<Void> task) throws Exception {
                                        return fetchAsyncTask;
                                    }
                                });
                            }
                        }
                        return fetchAsyncTask;
                    }
                });
            }
        }
        return onSuccessTask;
    }

    /* access modifiers changed from: 0000 */
    public ParseCommand constructSaveCommand(Map<String, ParseFieldOperation> operations, ParseObjectEncodingStrategy objectEncoder, String sessionToken2) throws ParseException {
        ParseCommand command;
        synchronized (this.mutex) {
            command = super.constructSaveCommand(operations, objectEncoder, sessionToken2);
            if (command == null) {
                command = null;
            } else {
                if (this.password != null) {
                    command.put("user_password", this.password);
                }
                if (this.authData.length() > 0) {
                    command.put("auth_data", this.authData);
                }
            }
        }
        return command;
    }

    /* access modifiers changed from: private */
    public ParseCommand constructSignUpCommand(Map<String, ParseFieldOperation> operations, String sessionToken2) throws ParseException {
        ParseCommand command = constructSaveCommand(operations, PointerEncodingStrategy.get(), sessionToken2);
        command.setOp("user_signup");
        return command;
    }

    /* access modifiers changed from: private */
    public ParseCommand constructSignUpOrLoginCommand(Map<String, ParseFieldOperation> operations) throws ParseException {
        ParseCommand command;
        synchronized (this.mutex) {
            command = new ParseCommand("user_signup_or_login", null);
            JSONObject params = toJSONObjectForSaving(operations, PointerEncodingStrategy.get());
            Iterator keys = params.keys();
            while (keys.hasNext()) {
                String key = (String) keys.next();
                try {
                    Object value = params.get(key);
                    if (value instanceof JSONObject) {
                        command.put(key, (JSONObject) value);
                    } else if (value instanceof JSONArray) {
                        command.put(key, (JSONArray) value);
                    } else if (value instanceof String) {
                        command.put(key, (String) value);
                    } else {
                        command.put(key, params.getInt(key));
                    }
                } catch (JSONException e) {
                }
            }
            if (this.password != null) {
                command.put("user_password", this.password);
            }
        }
        return command;
    }

    private static ParseCommand constructPasswordResetCommand(String email, String sessionToken2) {
        ParseCommand command = new ParseCommand("user_request_password_reset", sessionToken2);
        command.put(User.EMAIL, email);
        return command;
    }

    private Task<Void> signUpAsync() {
        return this.taskQueue.enqueue(new Continuation<Void, Task<Void>>() {
            public Task<Void> then(Task<Void> task) throws Exception {
                return ParseUser.this.signUpAsync(task);
            }
        });
    }

    /* access modifiers changed from: private */
    public Task<Void> signUpAsync(Task<Void> toAwait) {
        Task<Void> onSuccessTask;
        synchronized (this.mutex) {
            final String sessionToken2 = getCurrentSessionToken();
            if (getUsername() == null || getUsername().length() == 0) {
                throw new IllegalArgumentException("Username cannot be missing or blank");
            } else if (this.password == null) {
                throw new IllegalArgumentException("Password cannot be missing or blank");
            } else if (getObjectId() != null) {
                try {
                    if (!this.authData.has("anonymous") || this.authData.get("anonymous") != JSONObject.NULL) {
                        throw new IllegalArgumentException("Cannot sign up a user that has already signed up.");
                    }
                    onSuccessTask = saveAsync(toAwait);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            } else if (this.operationSetQueue.size() > 1) {
                throw new IllegalArgumentException("Cannot sign up a user that is already signing up.");
            } else if (getCurrentUser() == null || !ParseAnonymousUtils.isLinked(getCurrentUser())) {
                onSuccessTask = Task.call(new Callable<Map<String, ParseFieldOperation>>() {
                    public Map<String, ParseFieldOperation> call() throws Exception {
                        Map<String, ParseFieldOperation> startSave;
                        synchronized (ParseUser.this.mutex) {
                            startSave = ParseUser.this.startSave();
                        }
                        return startSave;
                    }
                }).continueWithTask(TaskQueue.waitFor(toAwait)).onSuccessTask(new Continuation<Map<String, ParseFieldOperation>, Task<Void>>() {
                    public Task<Void> then(Task<Map<String, ParseFieldOperation>> task) throws Exception {
                        final Map<String, ParseFieldOperation> operations = (Map) task.getResult();
                        final ParseCommand command = ParseUser.this.constructSignUpCommand(operations, sessionToken2);
                        if (command == null) {
                            return Task.forResult(null);
                        }
                        return command.executeAsync().continueWithTask(new Continuation<Object, Task<Void>>() {
                            public Task<Void> then(Task<Object> task) throws Exception {
                                Task<Void> makeVoid;
                                synchronized (ParseUser.this.mutex) {
                                    ParseUser.this.handleSaveResult(command.getOp(), (JSONObject) task.getResult(), operations);
                                    if (task.isCancelled() || task.isFaulted()) {
                                        makeVoid = task.makeVoid();
                                    } else {
                                        ParseUser.this.isNew = true;
                                        makeVoid = ParseUser.saveCurrentUserAsync(ParseUser.this);
                                    }
                                }
                                return makeVoid;
                            }
                        });
                    }
                });
            } else if (isCurrentUser()) {
                throw new IllegalArgumentException("Attempt to merge currentUser with itself.");
            } else {
                checkForChangesToMutableContainers();
                getCurrentUser().checkForChangesToMutableContainers();
                getCurrentUser().copyChangesFrom(this);
                getCurrentUser().dirty = true;
                getCurrentUser().setPassword(this.password);
                getCurrentUser().setUsername(getUsername());
                revert();
                onSuccessTask = getCurrentUser().saveAsync(toAwait).onSuccessTask(new Continuation<Void, Task<Void>>() {
                    public Task<Void> then(Task<Void> task) throws Exception {
                        Task<Void> access$000;
                        synchronized (ParseUser.this.mutex) {
                            ParseUser.this.mergeFromObject(ParseUser.getCurrentUser());
                            access$000 = ParseUser.saveCurrentUserAsync(ParseUser.this);
                        }
                        return access$000;
                    }
                });
            }
        }
        return onSuccessTask;
    }

    public void signUp() throws ParseException {
        Parse.waitForTask(signUpAsync());
    }

    public void signUpInBackground(SignUpCallback callback) {
        Parse.callbackOnMainThreadAsync(signUpAsync(), callback);
    }

    private static ParseCommand constructLogInCommand(String username, String password2) {
        ParseCommand command = new ParseCommand("user_login", null);
        command.put("username", username);
        command.put("user_password", password2);
        return command;
    }

    private static Task<ParseUser> logInAsync(String username, String password2) {
        if (username == null) {
            throw new IllegalArgumentException("Must specify a username for the user to log in with");
        } else if (password2 == null) {
            throw new IllegalArgumentException("Must specify a password for the user to log in with");
        } else {
            final Capture<ParseUser> userResult = new Capture<>();
            return constructLogInCommand(username, password2).executeAsync().onSuccessTask(new Continuation<Object, Task<Void>>() {
                public Task<Void> then(Task<Object> task) throws Exception {
                    if (task.getResult() == JSONObject.NULL) {
                        throw new ParseException((int) ParseException.OBJECT_NOT_FOUND, "invalid login credentials");
                    }
                    ParseUser user = (ParseUser) ParseObject.create(ParseUser.class);
                    user.handleFetchResult((JSONObject) task.getResult());
                    userResult.set(user);
                    return ParseUser.saveCurrentUserAsync(user);
                }
            }).onSuccess(new Continuation<Void, ParseUser>() {
                public ParseUser then(Task<Void> task) throws Exception {
                    return (ParseUser) userResult.get();
                }
            });
        }
    }

    private static ParseCommand constructBecomeCommand(String sessionToken2) {
        return new ParseCommand("client_me", sessionToken2);
    }

    private static Task<ParseUser> becomeAsync(String sessionToken2) {
        if (sessionToken2 == null) {
            throw new IllegalArgumentException("Must specify a sessionToken for the user to log in with");
        }
        final Capture<ParseUser> userResult = new Capture<>();
        return constructBecomeCommand(sessionToken2).executeAsync().onSuccessTask(new Continuation<Object, Task<Void>>() {
            public Task<Void> then(Task<Object> task) throws Exception {
                if (task.getResult() == JSONObject.NULL) {
                    throw new ParseException((int) ParseException.OBJECT_NOT_FOUND, "invalid login credentials");
                }
                ParseUser user = (ParseUser) ParseObject.create(ParseUser.class);
                user.handleFetchResult((JSONObject) task.getResult());
                userResult.set(user);
                return ParseUser.saveCurrentUserAsync(user);
            }
        }).onSuccess(new Continuation<Void, ParseUser>() {
            public ParseUser then(Task<Void> task) throws Exception {
                return (ParseUser) userResult.get();
            }
        });
    }

    public static ParseUser logIn(String username, String password2) throws ParseException {
        return (ParseUser) Parse.waitForTask(logInAsync(username, password2));
    }

    public static void logInInBackground(String username, String password2, LogInCallback callback) {
        Parse.callbackOnMainThreadAsync(logInAsync(username, password2), callback);
    }

    public static ParseUser become(String sessionToken2) throws ParseException {
        return (ParseUser) Parse.waitForTask(becomeAsync(sessionToken2));
    }

    public static void becomeInBackground(String sessionToken2, LogInCallback callback) {
        Parse.callbackOnMainThreadAsync(becomeAsync(sessionToken2), callback);
    }

    public static ParseUser getCurrentUser() {
        ParseUser parseUser;
        synchronized (currentUserMutex) {
            checkApplicationContext();
            if (currentUser != null) {
                parseUser = currentUser;
            } else if (currentUserMatchesDisk) {
                if (isAutomaticUserEnabled()) {
                    ParseAnonymousUtils.lazyLogIn();
                }
                parseUser = currentUser;
            } else {
                currentUserMatchesDisk = true;
                ParseObject user = getFromDisk(Parse.applicationContext, CURRENT_USER_FILENAME);
                if (user == null) {
                    if (isAutomaticUserEnabled()) {
                        ParseAnonymousUtils.lazyLogIn();
                    }
                    parseUser = currentUser;
                } else {
                    currentUser = (ParseUser) user;
                    currentUser.isCurrentUser = true;
                    parseUser = currentUser;
                }
            }
        }
        return parseUser;
    }

    static String getCurrentSessionToken() {
        String str;
        synchronized (currentUserMutex) {
            if (getCurrentUser() != null) {
                str = getCurrentUser().getSessionToken();
            } else {
                str = null;
            }
        }
        return str;
    }

    /* access modifiers changed from: private */
    public static Task<Void> saveCurrentUserAsync(ParseUser user) {
        Task<Void> forResult;
        synchronized (currentUserMutex) {
            checkApplicationContext();
            if (currentUser != user) {
                logOut();
            }
            synchronized (user.mutex) {
                user.isCurrentUser = true;
                user.synchronizeAllAuthData();
                user.saveToDisk(Parse.applicationContext, CURRENT_USER_FILENAME);
            }
            currentUserMatchesDisk = true;
            currentUser = user;
            forResult = Task.forResult(null);
        }
        return forResult;
    }

    public static void logOut() {
        synchronized (currentUserMutex) {
            checkApplicationContext();
            if (currentUser != null) {
                synchronized (currentUser.mutex) {
                    for (String authType : currentUser.getLinkedServiceNames()) {
                        currentUser.logOutWith(authType);
                    }
                    currentUser.isCurrentUser = false;
                    currentUser.sessionToken = null;
                }
            }
            currentUserMatchesDisk = true;
            currentUser = null;
            new File(Parse.getParseDir(), CURRENT_USER_FILENAME).delete();
        }
    }

    private static Task<Void> requestPasswordResetAsync(String email) {
        return constructPasswordResetCommand(email, getCurrentSessionToken()).executeAsync().makeVoid();
    }

    public static void requestPasswordReset(String email) throws ParseException {
        Parse.waitForTask(requestPasswordResetAsync(email));
    }

    public static void requestPasswordResetInBackground(String email, RequestPasswordResetCallback callback) {
        Parse.callbackOnMainThreadAsync(requestPasswordResetAsync(email), callback);
    }

    private static void checkApplicationContext() {
        if (Parse.applicationContext == null) {
            throw new RuntimeException("You must call Parse.initialize(context, oauthKey, oauthSecret) before using the Parse library.");
        }
    }

    public ParseUser fetchIfNeeded() throws ParseException {
        return (ParseUser) super.fetchIfNeeded();
    }

    /* access modifiers changed from: 0000 */
    public Set<String> getLinkedServiceNames() {
        Set<String> set;
        synchronized (this.mutex) {
            set = this.readOnlyLinkedServiceNames;
        }
        return set;
    }

    /* access modifiers changed from: private */
    /* JADX WARNING: Code restructure failed: missing block: B:20:?, code lost:
        return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void synchronizeAuthData(java.lang.String r6) {
        /*
            r5 = this;
            java.lang.Object r3 = r5.mutex
            monitor-enter(r3)
            boolean r2 = r5.isCurrentUser()     // Catch:{ all -> 0x0015 }
            if (r2 != 0) goto L_0x000b
            monitor-exit(r3)     // Catch:{ all -> 0x0015 }
        L_0x000a:
            return
        L_0x000b:
            java.util.Map<java.lang.String, com.parse.ParseAuthenticationProvider> r2 = authenticationProviders     // Catch:{ all -> 0x0015 }
            boolean r2 = r2.containsKey(r6)     // Catch:{ all -> 0x0015 }
            if (r2 != 0) goto L_0x0018
            monitor-exit(r3)     // Catch:{ all -> 0x0015 }
            goto L_0x000a
        L_0x0015:
            r2 = move-exception
            monitor-exit(r3)     // Catch:{ all -> 0x0015 }
            throw r2
        L_0x0018:
            java.util.Map<java.lang.String, com.parse.ParseAuthenticationProvider> r2 = authenticationProviders     // Catch:{ all -> 0x0015 }
            java.lang.Object r0 = r2.get(r6)     // Catch:{ all -> 0x0015 }
            com.parse.ParseAuthenticationProvider r0 = (com.parse.ParseAuthenticationProvider) r0     // Catch:{ all -> 0x0015 }
            org.json.JSONObject r2 = r5.authData     // Catch:{ all -> 0x0015 }
            java.lang.String r4 = r0.getAuthType()     // Catch:{ all -> 0x0015 }
            org.json.JSONObject r2 = r2.optJSONObject(r4)     // Catch:{ all -> 0x0015 }
            boolean r1 = r0.restoreAuthentication(r2)     // Catch:{ all -> 0x0015 }
            if (r1 != 0) goto L_0x0033
            r5.unlinkFromAsync(r6)     // Catch:{ all -> 0x0015 }
        L_0x0033:
            monitor-exit(r3)     // Catch:{ all -> 0x0015 }
            goto L_0x000a
        */
        throw new UnsupportedOperationException("Method not decompiled: com.parse.ParseUser.synchronizeAuthData(java.lang.String):void");
    }

    private void synchronizeAllAuthData() {
        synchronized (this.mutex) {
            if (this.authData != null) {
                Iterator<String> authTypes = this.authData.keys();
                while (authTypes.hasNext()) {
                    synchronizeAuthData((String) authTypes.next());
                }
            }
        }
    }

    /* access modifiers changed from: 0000 */
    public Task<Void> unlinkFromAsync(final String authType) {
        Task<Void> continueWithTask;
        synchronized (this.mutex) {
            if (authType == null) {
                continueWithTask = Task.forResult(null);
            } else {
                continueWithTask = Task.forResult(null).continueWithTask(new Continuation<Void, Task<Void>>() {
                    public Task<Void> then(Task<Void> task) throws Exception {
                        Task<Void> forResult;
                        synchronized (ParseUser.this.mutex) {
                            if (ParseUser.this.authData.has(authType)) {
                                ParseUser.this.authData.put(authType, JSONObject.NULL);
                                ParseUser.this.dirty = true;
                                forResult = ParseUser.this.saveAsync();
                            } else {
                                forResult = Task.forResult(null);
                            }
                        }
                        return forResult;
                    }
                });
            }
        }
        return continueWithTask;
    }

    static void registerAuthenticationProvider(ParseAuthenticationProvider provider) {
        authenticationProviders.put(provider.getAuthType(), provider);
        if (getCurrentUser() != null) {
            getCurrentUser().synchronizeAuthData(provider.getAuthType());
        }
    }

    static Task<ParseUser> logInWithAsync(String authType) {
        if (authenticationProviders.containsKey(authType)) {
            return logInWithAsync((ParseAuthenticationProvider) authenticationProviders.get(authType));
        }
        throw new IllegalArgumentException("No authentication provider could be found for the provided authType");
    }

    static Task<ParseUser> logInWithAsync(final String authType, final JSONObject authData2) {
        final Continuation<Void, Task<ParseUser>> logInWithTask = new Continuation<Void, Task<ParseUser>>() {
            public Task<ParseUser> then(Task<Void> task) throws Exception {
                final ParseUser user = (ParseUser) ParseObject.create(ParseUser.class);
                try {
                    user.authData.put(authType, authData2);
                    user.linkedServiceNames.add(authType);
                    final Map<String, ParseFieldOperation> operations = user.startSave();
                    final ParseCommand command = user.constructSignUpOrLoginCommand(operations);
                    return command.executeAsync().continueWithTask(new Continuation<Object, Task<Object>>() {
                        public Task<Object> then(Task<Object> task) throws Exception {
                            user.handleSaveResult(command.getOp(), (JSONObject) task.getResult(), operations);
                            return null;
                        }
                    }).onSuccessTask(new Continuation<Object, Task<Void>>() {
                        public Task<Void> then(Task<Object> task) throws Exception {
                            Task<Void> access$000;
                            synchronized (user.mutex) {
                                user.synchronizeAuthData(authType);
                                access$000 = ParseUser.saveCurrentUserAsync(user);
                            }
                            return access$000;
                        }
                    }).continueWith(new Continuation<Void, ParseUser>() {
                        public ParseUser then(Task<Void> task) throws Exception {
                            return user;
                        }
                    });
                } catch (JSONException e) {
                    throw new ParseException(e);
                }
            }
        };
        final ParseUser user = getCurrentUser();
        if (user != null) {
            synchronized (user.mutex) {
                if (ParseAnonymousUtils.isLinked(user)) {
                    if (user.isLazy()) {
                        final JSONObject oldAnonymousData = user.authData.optJSONObject("anonymous");
                        Task<ParseUser> enqueue = user.taskQueue.enqueue(new Continuation<Void, Task<ParseUser>>(user) {
                            final /* synthetic */ ParseUser val$user;

                            {
                                this.val$user = r1;
                            }

                            public Task<ParseUser> then(Task<Void> task) throws Exception {
                                return Task.forResult(null).continueWithTask(new Continuation<Void, Task<Void>>() {
                                    public Task<Void> then(Task<Void> task) throws Exception {
                                        Task<Void> access$1000;
                                        synchronized (C021113.this.val$user.mutex) {
                                            C021113.this.val$user.stripAnonymity();
                                            C021113.this.val$user.authData.put(authType, authData2);
                                            C021113.this.val$user.linkedServiceNames.add(authType);
                                            access$1000 = C021113.this.val$user.resolveLazinessAsync(task);
                                        }
                                        return access$1000;
                                    }
                                }).continueWithTask(new Continuation<Void, Task<ParseUser>>() {
                                    public Task<ParseUser> then(Task<Void> task) throws Exception {
                                        Task<ParseUser> forResult;
                                        synchronized (C021113.this.val$user.mutex) {
                                            if (task.isFaulted()) {
                                                C021113.this.val$user.authData.remove(authType);
                                                C021113.this.val$user.linkedServiceNames.remove(authType);
                                                C021113.this.val$user.restoreAnonymity(oldAnonymousData);
                                                forResult = Task.forError(task.getError());
                                            } else if (task.isCancelled()) {
                                                forResult = Task.cancelled();
                                            } else {
                                                forResult = Task.forResult(C021113.this.val$user);
                                            }
                                        }
                                        return forResult;
                                    }
                                });
                            }
                        });
                        return enqueue;
                    }
                    Task<ParseUser> continueWithTask = user.linkWithAsync(authType, authData2).continueWithTask(new Continuation<Void, Task<ParseUser>>() {
                        public Task<ParseUser> then(Task<Void> task) throws Exception {
                            if (task.isFaulted() && (task.getError() instanceof ParseException) && ((ParseException) task.getError()).getCode() == 208) {
                                return Task.forResult(null).continueWithTask(logInWithTask);
                            }
                            if (task.isCancelled()) {
                                return Task.cancelled();
                            }
                            return Task.forResult(user);
                        }
                    });
                    return continueWithTask;
                }
            }
        }
        return Task.forResult(null).continueWithTask(logInWithTask);
    }

    /* access modifiers changed from: private */
    public Task<Void> resolveLazinessAsync(Task<Void> toAwait) {
        Task<Void> onSuccessTask;
        synchronized (this.mutex) {
            if (!isLazy()) {
                onSuccessTask = Task.forResult(null);
            } else if (this.linkedServiceNames.size() == 0) {
                onSuccessTask = signUpAsync(toAwait).onSuccess(new Continuation<Void, Void>() {
                    public Void then(Task<Void> task) throws Exception {
                        synchronized (ParseUser.this.mutex) {
                            ParseUser.this.isLazy = false;
                        }
                        return null;
                    }
                });
            } else {
                final Capture<Map<String, ParseFieldOperation>> operations = new Capture<>();
                onSuccessTask = Task.call(new Callable<Map<String, ParseFieldOperation>>() {
                    public Map<String, ParseFieldOperation> call() throws Exception {
                        return ParseUser.this.startSave();
                    }
                }).onSuccessTask(TaskQueue.waitFor(toAwait)).onSuccessTask(new Continuation<Map<String, ParseFieldOperation>, Task<Object>>() {
                    public Task<Object> then(Task<Map<String, ParseFieldOperation>> task) throws Exception {
                        operations.set(task.getResult());
                        return ParseUser.this.constructSignUpOrLoginCommand((Map) operations.get()).executeAsync();
                    }
                }).onSuccessTask(new Continuation<Object, Task<Void>>() {
                    public Task<Void> then(Task<Object> task) throws Exception {
                        Task<Void> access$000;
                        synchronized (ParseUser.this.mutex) {
                            JSONObject commandResult = (JSONObject) task.getResult();
                            ParseUser.this.handleSaveResult("create", commandResult, (Map) operations.get());
                            if (commandResult.optBoolean("is_new")) {
                                ParseUser.this.isLazy = false;
                                access$000 = Task.forResult(null);
                            } else {
                                ParseUser newUser = (ParseUser) ParseObject.create(ParseUser.class);
                                newUser.handleFetchResult(commandResult);
                                access$000 = ParseUser.saveCurrentUserAsync(newUser);
                            }
                        }
                        return access$000;
                    }
                });
            }
        }
        return onSuccessTask;
    }

    private static Task<JSONObject> authenticateAsync(ParseAuthenticationProvider authenticator) {
        final TaskCompletionSource tcs = Task.create();
        authenticator.authenticate(new ParseAuthenticationCallback() {
            public void onSuccess(JSONObject authData) {
                tcs.setResult(authData);
            }

            public void onCancel() {
                tcs.setCancelled();
            }

            public void onError(Throwable error) {
                tcs.setError(new ParseException(error));
            }
        });
        return tcs.getTask();
    }

    private static Task<ParseUser> logInWithAsync(final ParseAuthenticationProvider authenticator) {
        return authenticateAsync(authenticator).onSuccessTask(new Continuation<JSONObject, Task<ParseUser>>() {
            public Task<ParseUser> then(Task<JSONObject> task) throws Exception {
                return ParseUser.logInWithAsync(authenticator.getAuthType(), (JSONObject) task.getResult());
            }
        });
    }

    /* access modifiers changed from: private */
    public Task<Void> linkWithAsync(final String authType, final JSONObject authData2, final Task<Void> toAwait) {
        Task<Void> continueWithTask;
        final JSONObject oldAnonymousData = authData2.optJSONObject("anonymous");
        synchronized (this.mutex) {
            continueWithTask = Task.call(new Callable<Void>() {
                public Void call() throws Exception {
                    synchronized (ParseUser.this.mutex) {
                        ParseUser.this.authData.put(authType, authData2);
                        ParseUser.this.linkedServiceNames.add(authType);
                        ParseUser.this.stripAnonymity();
                        ParseUser.this.dirty = true;
                    }
                    return null;
                }
            }).onSuccessTask(new Continuation<Void, Task<Void>>() {
                public Task<Void> then(Task<Void> task) throws Exception {
                    return ParseUser.this.saveAsync(toAwait);
                }
            }).continueWithTask(new Continuation<Void, Task<Void>>() {
                public Task<Void> then(Task<Void> task) throws Exception {
                    synchronized (ParseUser.this.mutex) {
                        if (task.isFaulted() || task.isCancelled()) {
                            ParseUser.this.restoreAnonymity(oldAnonymousData);
                        } else {
                            ParseUser.this.synchronizeAuthData(authType);
                        }
                    }
                    return task;
                }
            });
        }
        return continueWithTask;
    }

    /* access modifiers changed from: 0000 */
    public Task<Void> linkWithAsync(final String authType, final JSONObject authData2) {
        return this.taskQueue.enqueue(new Continuation<Void, Task<Void>>() {
            public Task<Void> then(Task<Void> task) throws Exception {
                return ParseUser.this.linkWithAsync(authType, authData2, task);
            }
        });
    }

    /* access modifiers changed from: 0000 */
    public Task<Void> linkWithAsync(String authType) {
        if (authenticationProviders.containsKey(authType)) {
            return linkWithAsync((ParseAuthenticationProvider) authenticationProviders.get(authType));
        }
        throw new IllegalArgumentException("No authentication provider could be found for the provided authType");
    }

    private Task<Void> linkWithAsync(final ParseAuthenticationProvider authenticator) {
        return authenticateAsync(authenticator).onSuccessTask(new Continuation<JSONObject, Task<Void>>() {
            public Task<Void> then(Task<JSONObject> task) throws Exception {
                return ParseUser.this.linkWithAsync(authenticator.getAuthType(), (JSONObject) task.getResult());
            }
        });
    }

    /* access modifiers changed from: 0000 */
    public void logOutWith(String authType) {
        synchronized (this.mutex) {
            if (authenticationProviders.containsKey(authType) && this.linkedServiceNames.contains(authType)) {
                logOutWith((ParseAuthenticationProvider) authenticationProviders.get(authType));
            }
        }
    }

    private void logOutWith(ParseAuthenticationProvider provider) {
        provider.deauthenticate();
    }

    public boolean isNew() {
        boolean z;
        synchronized (this.mutex) {
            z = this.isNew;
        }
        return z;
    }

    static void disableAutomaticUser() {
        autoUserEnabled = false;
    }

    public static void enableAutomaticUser() {
        autoUserEnabled = true;
    }

    static boolean isAutomaticUserEnabled() {
        return autoUserEnabled;
    }

    public static ParseQuery<ParseUser> getQuery() {
        return ParseQuery.getQuery(ParseUser.class);
    }

    static void clearCurrentUserFromMemory() {
        synchronized (currentUserMutex) {
            currentUser = null;
            currentUserMatchesDisk = false;
        }
    }

    /* access modifiers changed from: 0000 */
    public boolean needsDefaultACL() {
        return false;
    }
}
