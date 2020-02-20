package com.parse;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.Set;
import org.json.JSONException;
import org.json.JSONObject;

class PushHistory {
    private static final String TAG = "com.parse.PushHistory";
    private String cutoff = null;
    private final PriorityQueue<Entry> entries;
    private String lastTime = null;
    private final int maxHistoryLength;
    private final HashSet<String> pushIds;

    private static class Entry implements Comparable<Entry> {
        public String pushId;
        public String timestamp;

        public Entry(String pushId2, String timestamp2) {
            this.pushId = pushId2;
            this.timestamp = timestamp2;
        }

        public int compareTo(Entry other) {
            return this.timestamp.compareTo(other.timestamp);
        }
    }

    public PushHistory(int maxHistoryLength2, JSONObject json) {
        this.maxHistoryLength = maxHistoryLength2;
        this.entries = new PriorityQueue<>(maxHistoryLength2 + 1);
        this.pushIds = new HashSet<>(maxHistoryLength2 + 1);
        if (json != null) {
            setCutoffTimestamp(json.optString("ignoreAfter", null));
            setLastReceivedTimestamp(json.optString("lastTime", null));
            JSONObject jsonHistory = json.optJSONObject("history");
            if (jsonHistory != null) {
                Iterator<String> it = jsonHistory.keys();
                while (it.hasNext()) {
                    String pushId = (String) it.next();
                    String timestamp = jsonHistory.optString(pushId, null);
                    if (!(pushId == null || timestamp == null)) {
                        tryInsertPush(pushId, timestamp);
                    }
                }
            }
        }
    }

    public JSONObject toJSON() throws JSONException {
        JSONObject json = new JSONObject();
        if (this.entries.size() > 0) {
            JSONObject history = new JSONObject();
            Iterator i$ = this.entries.iterator();
            while (i$.hasNext()) {
                Entry e = (Entry) i$.next();
                history.put(e.pushId, e.timestamp);
            }
            json.put("history", history);
        }
        json.putOpt("ignoreAfter", this.cutoff);
        json.putOpt("lastTime", this.lastTime);
        return json;
    }

    public String getCutoffTimestamp() {
        return this.cutoff;
    }

    private void setCutoffTimestamp(String cutoff2) {
        this.cutoff = cutoff2;
    }

    public String getLastReceivedTimestamp() {
        return this.lastTime;
    }

    private void setLastReceivedTimestamp(String lastTime2) {
        this.lastTime = lastTime2;
    }

    public Set<String> getPushIds() {
        return Collections.unmodifiableSet(this.pushIds);
    }

    public boolean tryInsertPush(String pushId, String timestamp) {
        if (timestamp == null) {
            throw new IllegalArgumentException("Can't insert null pushId or timestamp into history");
        }
        if (this.lastTime == null || timestamp.compareTo(this.lastTime) > 0) {
            this.lastTime = timestamp;
        }
        if (this.cutoff == null || timestamp.compareTo(this.cutoff) > 0) {
            if (pushId != null) {
                if (this.pushIds.contains(pushId)) {
                    Parse.logE(TAG, "Ignored duplicate push " + pushId);
                    return false;
                }
                this.entries.add(new Entry(pushId, timestamp));
                this.pushIds.add(pushId);
                while (this.entries.size() > this.maxHistoryLength) {
                    Entry head = (Entry) this.entries.remove();
                    this.pushIds.remove(head.pushId);
                    this.cutoff = head.timestamp;
                }
            }
            return true;
        }
        Parse.logE(TAG, "Ignored old push " + pushId + " at " + timestamp + " before cutoff " + this.cutoff);
        return false;
    }
}
