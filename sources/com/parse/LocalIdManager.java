package com.parse;

import java.io.File;
import java.io.IOException;
import java.util.Random;
import org.json.JSONException;
import org.json.JSONObject;

class LocalIdManager {
    private static LocalIdManager defaultInstance;
    private File diskPath = new File(Parse.getParseDir(), "LocalId");
    private Random random;

    private class MapEntry {
        String objectId;
        int retainCount;

        private MapEntry() {
        }
    }

    public static synchronized LocalIdManager getDefaultInstance() {
        LocalIdManager localIdManager;
        synchronized (LocalIdManager.class) {
            if (defaultInstance == null) {
                defaultInstance = new LocalIdManager();
            }
            localIdManager = defaultInstance;
        }
        return localIdManager;
    }

    private LocalIdManager() {
        this.diskPath.mkdirs();
        this.random = new Random();
    }

    private boolean isLocalId(String localId) {
        if (!localId.startsWith("local_")) {
            return false;
        }
        for (int i = 6; i < localId.length(); i++) {
            char c = localId.charAt(i);
            if ((c < '0' || c > '9') && (c < 'a' || c > 'f')) {
                return false;
            }
        }
        return true;
    }

    private synchronized MapEntry getMapEntry(String localId) {
        MapEntry entry;
        if (!isLocalId(localId)) {
            throw new IllegalStateException("Tried to get invalid local id: \"" + localId + "\".");
        }
        File file = new File(this.diskPath, localId);
        if (!file.exists()) {
            entry = new MapEntry();
        } else {
            JSONObject json = ParseObject.getDiskObject(file);
            entry = new MapEntry();
            entry.retainCount = json.optInt("retainCount", 0);
            entry.objectId = json.optString("objectId", null);
        }
        return entry;
    }

    private synchronized void putMapEntry(String localId, MapEntry entry) {
        if (!isLocalId(localId)) {
            throw new IllegalStateException("Tried to get invalid local id: \"" + localId + "\".");
        }
        JSONObject json = new JSONObject();
        try {
            json.put("retainCount", entry.retainCount);
            if (entry.objectId != null) {
                json.put("objectId", entry.objectId);
            }
            File file = new File(this.diskPath, localId);
            if (!this.diskPath.exists()) {
                this.diskPath.mkdirs();
            }
            ParseObject.saveDiskObject(file, json);
        } catch (JSONException je) {
            throw new IllegalStateException("Error creating local id map entry.", je);
        }
    }

    private synchronized void removeMapEntry(String localId) {
        if (!isLocalId(localId)) {
            throw new IllegalStateException("Tried to get invalid local id: \"" + localId + "\".");
        }
        new File(this.diskPath, localId).delete();
    }

    /* access modifiers changed from: 0000 */
    public synchronized String createLocalId() {
        String localId;
        localId = "local_" + Long.toHexString(this.random.nextLong());
        if (!isLocalId(localId)) {
            throw new IllegalStateException("Generated an invalid local id: \"" + localId + "\". " + "This should never happen. Contact us at https://parse.com/help");
        }
        return localId;
    }

    /* access modifiers changed from: 0000 */
    public synchronized void retainLocalIdOnDisk(String localId) {
        MapEntry entry = getMapEntry(localId);
        entry.retainCount++;
        putMapEntry(localId, entry);
    }

    /* access modifiers changed from: 0000 */
    public synchronized void releaseLocalIdOnDisk(String localId) {
        MapEntry entry = getMapEntry(localId);
        entry.retainCount--;
        if (entry.retainCount > 0) {
            putMapEntry(localId, entry);
        } else {
            removeMapEntry(localId);
        }
    }

    /* access modifiers changed from: 0000 */
    public synchronized String getObjectId(String localId) {
        return getMapEntry(localId).objectId;
    }

    /* access modifiers changed from: 0000 */
    public synchronized void setObjectId(String localId, String objectId) {
        MapEntry entry = getMapEntry(localId);
        if (entry.retainCount > 0) {
            if (entry.objectId != null) {
                throw new IllegalStateException("Tried to set an objectId for a localId that already has one.");
            }
            entry.objectId = objectId;
            putMapEntry(localId, entry);
        }
    }

    /* access modifiers changed from: 0000 */
    public synchronized boolean clear() throws IOException {
        String[] arr$;
        boolean z = false;
        synchronized (this) {
            String[] files = this.diskPath.list();
            if (files != null) {
                if (files.length != 0) {
                    for (String fileName : files) {
                        if (!new File(this.diskPath, fileName).delete()) {
                            throw new IOException("Unable to delete file " + fileName + " in localId cache.");
                        }
                    }
                    z = true;
                }
            }
        }
        return z;
    }
}
