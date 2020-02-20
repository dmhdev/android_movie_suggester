package com.parse;

import android.webkit.MimeTypeMap;
import com.parse.Task.TaskCompletionSource;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import org.json.JSONException;
import org.json.JSONObject;

public class ParseFile {
    private String contentType;
    /* access modifiers changed from: private */
    public Set<TaskCompletionSource> currentTasks;
    byte[] data;
    /* access modifiers changed from: private */
    public boolean dirty;
    private String name;
    private ParseAWSRequest request;
    final TaskQueue taskQueue;
    /* access modifiers changed from: private */
    public String url;

    static File getCacheDir() {
        return Parse.getParseCacheDir("files");
    }

    static File getFilesDir() {
        return Parse.getParseFilesDir("files");
    }

    static void clearCache() {
        for (File file : getCacheDir().listFiles()) {
            file.delete();
        }
    }

    public ParseFile(String name2, byte[] data2, String contentType2) {
        this.dirty = false;
        this.name = null;
        this.url = null;
        this.contentType = null;
        this.taskQueue = new TaskQueue();
        this.currentTasks = Collections.synchronizedSet(new HashSet());
        if (data2.length > Parse.maxParseFileSize) {
            throw new IllegalArgumentException(String.format("ParseFile must be less than %d bytes", new Object[]{Integer.valueOf(Parse.maxParseFileSize)}));
        }
        this.name = name2;
        this.data = data2;
        this.contentType = contentType2;
        this.dirty = true;
    }

    public ParseFile(byte[] data2) {
        this(null, data2, null);
    }

    public ParseFile(String name2, byte[] data2) {
        this(name2, data2, null);
    }

    public ParseFile(byte[] data2, String contentType2) {
        this(null, data2, contentType2);
    }

    ParseFile(String name2, String url2) {
        this.dirty = false;
        this.name = null;
        this.url = null;
        this.contentType = null;
        this.taskQueue = new TaskQueue();
        this.currentTasks = Collections.synchronizedSet(new HashSet());
        this.name = name2;
        this.url = url2;
    }

    private String getFilename() {
        return this.name;
    }

    /* access modifiers changed from: 0000 */
    public File getCacheFile() {
        String filename = getFilename();
        if (filename != null) {
            return new File(getCacheDir(), filename);
        }
        return null;
    }

    /* access modifiers changed from: 0000 */
    public File getFilesFile() {
        String filename = getFilename();
        if (filename != null) {
            return new File(getFilesDir(), filename);
        }
        return null;
    }

    public String getName() {
        return this.name;
    }

    public boolean isDirty() {
        return this.dirty;
    }

    public boolean isDataAvailable() {
        return this.data != null || (isPinned() ? getFilesFile().exists() : getCacheFile().exists());
    }

    public String getUrl() {
        return this.url;
    }

    /* access modifiers changed from: private */
    public byte[] getCachedData() {
        if (this.data != null) {
            return this.data;
        }
        try {
            File file = getCacheFile();
            if (file != null) {
                return ParseFileUtils.readFileToByteArray(file);
            }
        } catch (IOException e) {
        }
        try {
            File file2 = getFilesFile();
            if (file2 != null) {
                return ParseFileUtils.readFileToByteArray(file2);
            }
        } catch (IOException e2) {
        }
        return null;
    }

    /* access modifiers changed from: 0000 */
    public boolean isPinned() {
        File file = getFilesFile();
        return file != null && file.exists();
    }

    /* access modifiers changed from: 0000 */
    public void pin() throws ParseException {
        setPinned(true);
    }

    /* access modifiers changed from: 0000 */
    public void unpin() throws ParseException {
        setPinned(false);
    }

    /* access modifiers changed from: 0000 */
    public void pinInBackground() {
        pinInBackground(null);
    }

    /* access modifiers changed from: 0000 */
    public void unpinInBackground() {
        unpinInBackground(null);
    }

    /* access modifiers changed from: 0000 */
    public void pinInBackground(ParseCallback<Void> callback) {
        setPinnedInBackground(true, callback);
    }

    /* access modifiers changed from: 0000 */
    public void unpinInBackground(ParseCallback<Void> callback) {
        setPinnedInBackground(false, callback);
    }

    private void setPinned(boolean pinned) throws ParseException {
        Parse.waitForTask(setPinnedAsync(pinned));
    }

    private void setPinnedInBackground(boolean pinned, ParseCallback<Void> callback) {
        Parse.callbackOnMainThreadAsync(setPinnedAsync(pinned), callback);
    }

    private Task<Void> setPinnedAsync(final boolean pinned) {
        return this.taskQueue.enqueue(new Continuation<Void, Task<Void>>() {
            public Task<Void> then(Task<Void> task) throws Exception {
                return task;
            }
        }).continueWith(new Continuation<Void, Void>() {
            public Void then(Task<Void> task) throws Exception {
                File src;
                File dest;
                if ((!pinned || !ParseFile.this.isPinned()) && (pinned || ParseFile.this.isPinned())) {
                    if (pinned) {
                        src = ParseFile.this.getCacheFile();
                        dest = ParseFile.this.getFilesFile();
                    } else {
                        src = ParseFile.this.getFilesFile();
                        dest = ParseFile.this.getCacheFile();
                    }
                    if (dest == null) {
                        throw new IllegalStateException("Unable to pin file before saving");
                    }
                    if (dest.exists()) {
                        ParseFileUtils.deleteQuietly(dest);
                    }
                    if (pinned && ParseFile.this.data != null) {
                        ParseFileUtils.writeByteArrayToFile(dest, ParseFile.this.data);
                        if (src.exists()) {
                            ParseFileUtils.deleteQuietly(src);
                        }
                    } else if (src == null || !src.exists()) {
                        throw new IllegalStateException("Unable to pin file before retrieving");
                    } else {
                        ParseFileUtils.moveFile(src, dest);
                    }
                }
                return null;
            }
        }, Task.BACKGROUND_EXECUTOR);
    }

    /* access modifiers changed from: private */
    public ParseCommand constructFileUploadCommand(String sessionToken) {
        ParseCommand currentCommand = new ParseCommand("upload_file", sessionToken);
        currentCommand.enableRetrying();
        if (this.name != null) {
            currentCommand.put("name", this.name);
        }
        return currentCommand;
    }

    private void prepareFileUploadPost(JSONObject result, ProgressCallback progressCallback) {
        String mimeType = null;
        try {
            this.name = result.getString("name");
            this.url = result.getString("url");
            JSONObject postParams = result.getJSONObject("post_params");
            if (this.contentType != null) {
                mimeType = this.contentType;
            } else if (this.name.lastIndexOf(".") != -1) {
                mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(this.name.substring(this.name.lastIndexOf(".") + 1));
            }
            if (mimeType == null) {
                mimeType = "application/octet-stream";
            }
            try {
                this.request = new ParseAWSRequest(1, result.getString("post_url"));
                this.request.setProgressCallback(progressCallback);
                this.request.setMimeType(mimeType);
                this.request.setPostParams(postParams);
                this.request.setData(getCachedData());
            } catch (JSONException e) {
                throw new RuntimeException(e.getMessage());
            }
        } catch (JSONException e2) {
            throw new RuntimeException(e2.getMessage());
        }
    }

    /* access modifiers changed from: private */
    public Task<Void> handleFileUploadResultAsync(JSONObject result, ProgressCallback progressCallback) {
        if (this.request == null) {
            prepareFileUploadPost(result, progressCallback);
        }
        return this.request.executeAsync().makeVoid();
    }

    public void save() throws ParseException {
        save(null);
    }

    private void save(ProgressCallback progressCallback) throws ParseException {
        Parse.waitForTask(saveAsync(progressCallback));
    }

    /* access modifiers changed from: 0000 */
    public Task<Void> saveAsync(final ProgressCallback progressCallback, Task<Void> toAwait) {
        if (!isDirty()) {
            return Task.forResult(null);
        }
        final TaskCompletionSource tcs = Task.create();
        this.currentTasks.add(tcs);
        toAwait.continueWith(new Continuation<Void, Void>() {
            public Void then(Task<Void> task) throws Exception {
                if (!ParseFile.this.isDirty()) {
                    tcs.trySetResult(null);
                } else {
                    final String sessionToken = ParseUser.getCurrentSessionToken();
                    Task.call(new Callable<ParseCommand>() {
                        public ParseCommand call() throws Exception {
                            final ParseCommand command = ParseFile.this.constructFileUploadCommand(sessionToken);
                            tcs.getTask().continueWith(new Continuation<Void, Void>() {
                                public Void then(Task<Void> task) throws Exception {
                                    if (task.isCancelled()) {
                                        command.cancel();
                                    }
                                    return null;
                                }
                            });
                            return command;
                        }
                    }).onSuccessTask(new Continuation<ParseCommand, Task<Object>>() {
                        public Task<Object> then(Task<ParseCommand> task) throws Exception {
                            return ((ParseCommand) task.getResult()).executeAsync();
                        }
                    }).onSuccessTask(new Continuation<Object, Task<Void>>() {
                        public Task<Void> then(Task<Object> task) throws Exception {
                            return ParseFile.this.handleFileUploadResultAsync((JSONObject) task.getResult(), progressCallback);
                        }
                    }).continueWithTask(new Continuation<Void, Task<Void>>() {
                        public Task<Void> then(Task<Void> task) throws Exception {
                            if (!task.isFaulted()) {
                                try {
                                    ParseFileUtils.writeByteArrayToFile(ParseFile.this.getCacheFile(), ParseFile.this.data);
                                } catch (IOException e) {
                                }
                                ParseFile.this.dirty = false;
                            }
                            return task;
                        }
                    }).continueWith(new Continuation<Void, Void>() {
                        public Void then(Task<Void> task) throws Exception {
                            ParseFile.this.currentTasks.remove(tcs);
                            if (task.isCancelled()) {
                                tcs.trySetCancelled();
                            } else if (task.isFaulted()) {
                                tcs.trySetError(task.getError());
                            } else {
                                tcs.trySetResult(task.getResult());
                            }
                            return null;
                        }
                    });
                }
                return null;
            }
        });
        return tcs.getTask();
    }

    /* access modifiers changed from: 0000 */
    public Task<Void> saveAsync(final ProgressCallback progressCallback) {
        return this.taskQueue.enqueue(new Continuation<Void, Task<Void>>() {
            public Task<Void> then(Task<Void> task) throws Exception {
                return ParseFile.this.saveAsync(progressCallback, task);
            }
        });
    }

    public synchronized void saveInBackground(SaveCallback saveCallback, ProgressCallback progressCallback) {
        Parse.callbackOnMainThreadAsync(saveAsync(progressCallback), saveCallback);
    }

    public void saveInBackground(SaveCallback callback) {
        saveInBackground(callback, null);
    }

    public void saveInBackground() {
        saveInBackground(null);
    }

    public byte[] getData() throws ParseException {
        return (byte[]) Parse.waitForTask(getDataAsync(null));
    }

    /* access modifiers changed from: private */
    public Task<byte[]> getDataAsync(final ProgressCallback progressCallback, Task<Void> toAwait) {
        if (this.data != null) {
            return Task.forResult(this.data);
        }
        final TaskCompletionSource tcs = Task.create();
        this.currentTasks.add(tcs);
        toAwait.continueWith(new Continuation<Void, byte[]>() {
            public byte[] then(Task<Void> task) throws Exception {
                return ParseFile.this.getCachedData();
            }
        }, Task.BACKGROUND_EXECUTOR).continueWith(new Continuation<byte[], Void>() {
            public Void then(Task<byte[]> task) throws Exception {
                byte[] result = (byte[]) task.getResult();
                if (result != null) {
                    tcs.trySetResult(result);
                } else {
                    new ParseAWSRequest(ParseFile.this.url).executeAsync(progressCallback).continueWithTask(new Continuation<byte[], Task<byte[]>>() {
                        public Task<byte[]> then(Task<byte[]> task) throws Exception {
                            if (task.isFaulted() && (task.getError() instanceof IllegalStateException)) {
                                return Task.forError(new ParseException(100, task.getError().getMessage()));
                            }
                            if (tcs.getTask().isCancelled()) {
                                return tcs.getTask();
                            }
                            ParseFile.this.data = (byte[]) task.getResult();
                            if (ParseFile.this.data == null) {
                                return task;
                            }
                            ParseFileUtils.writeByteArrayToFile(ParseFile.this.getCacheFile(), ParseFile.this.data);
                            return task;
                        }
                    }).continueWith(new Continuation<byte[], Void>() {
                        public Void then(Task<byte[]> task) throws Exception {
                            ParseFile.this.currentTasks.remove(tcs);
                            if (task.isCancelled()) {
                                tcs.trySetCancelled();
                            } else if (task.isFaulted()) {
                                tcs.trySetError(task.getError());
                            } else {
                                tcs.trySetResult(task.getResult());
                            }
                            return null;
                        }
                    });
                }
                return null;
            }
        });
        return tcs.getTask();
    }

    /* access modifiers changed from: 0000 */
    public Task<byte[]> getDataAsync(final ProgressCallback progressCallback) {
        return this.taskQueue.enqueue(new Continuation<Void, Task<byte[]>>() {
            public Task<byte[]> then(Task<Void> task) throws Exception {
                return ParseFile.this.getDataAsync(progressCallback, task);
            }
        });
    }

    public void getDataInBackground(GetDataCallback dataCallback, ProgressCallback progressCallback) {
        Parse.callbackOnMainThreadAsync(getDataAsync(progressCallback), dataCallback);
    }

    public void getDataInBackground(GetDataCallback dataCallback) {
        getDataInBackground(dataCallback, null);
    }

    public void cancel() {
        Set<TaskCompletionSource> tasks = new HashSet<>(this.currentTasks);
        for (TaskCompletionSource tcs : tasks) {
            tcs.trySetCancelled();
        }
        this.currentTasks.removeAll(tasks);
    }
}
