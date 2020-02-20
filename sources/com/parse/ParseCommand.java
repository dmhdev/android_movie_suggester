package com.parse;

import android.net.http.AndroidHttpClient;
import com.parse.codec.digest.DigestUtils;
import com.parse.signpost.OAuthConsumer;
import com.parse.signpost.commonshttp.CommonsHttpOAuthConsumer;
import com.parse.signpost.exception.OAuthCommunicationException;
import com.parse.signpost.exception.OAuthExpectationFailedException;
import com.parse.signpost.exception.OAuthMessageSignerException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.UUID;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;
import org.json.JSONTokener;

class ParseCommand extends ParseRequest<JSONObject, Object> {
    private String localId;

    /* renamed from: op */
    private String f1op;
    JSONObject params;
    private final String sessionToken;

    private static String generateUrl(String op) {
        return String.format("%s/%s/%s", new Object[]{ParseObject.server, "2", op});
    }

    ParseCommand(String op, String sessionToken2) {
        this(op, new JSONObject(), null, sessionToken2);
    }

    ParseCommand(JSONObject json) throws JSONException {
        this(json.getString("op"), json.getJSONObject("params"), json.optString("localId", null), json.has("session_token") ? json.getString("session_token") : ParseUser.getCurrentSessionToken());
    }

    private ParseCommand(String op, JSONObject params2, String localId2, String sessionToken2) {
        super(1, generateUrl(op));
        this.f1op = op;
        this.params = params2;
        this.localId = localId2;
        this.sessionToken = sessionToken2;
        this.maxRetries = 0;
    }

    /* access modifiers changed from: 0000 */
    public void put(String key, String value) {
        try {
            this.params.put(key, value);
        } catch (JSONException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    /* access modifiers changed from: 0000 */
    public void put(String key, int value) {
        try {
            this.params.put(key, value);
        } catch (JSONException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    /* access modifiers changed from: 0000 */
    public void put(String key, long value) {
        try {
            this.params.put(key, value);
        } catch (JSONException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    /* access modifiers changed from: 0000 */
    public void put(String key, JSONArray value) {
        try {
            this.params.put(key, value);
        } catch (JSONException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    /* access modifiers changed from: 0000 */
    public void put(String key, JSONObject value) {
        try {
            this.params.put(key, value);
        } catch (JSONException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    /* access modifiers changed from: 0000 */
    public String getOp() {
        return this.f1op;
    }

    /* access modifiers changed from: 0000 */
    public void setOp(String op) {
        this.f1op = op;
        setUrl(generateUrl(op));
    }

    /* access modifiers changed from: 0000 */
    public String getLocalId() {
        return this.localId;
    }

    /* access modifiers changed from: 0000 */
    public void setLocalId(String theLocalId) {
        this.localId = theLocalId;
    }

    /* access modifiers changed from: 0000 */
    public void enableRetrying() {
        this.maxRetries = 4;
    }

    /* access modifiers changed from: 0000 */
    public JSONObject toJSONObject() {
        try {
            JSONObject answer = new JSONObject();
            answer.put("op", this.f1op);
            answer.put("params", this.params);
            if (this.localId != null) {
                answer.put("localId", this.localId);
            }
            answer.put("session_token", this.sessionToken != null ? this.sessionToken : JSONObject.NULL);
            return answer;
        } catch (JSONException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    /* access modifiers changed from: 0000 */
    public String getCacheKey() {
        try {
            String json = toDeterministicString(this.params);
            if (this.sessionToken != null) {
                json = json + this.sessionToken;
            }
            return "ParseCommand." + this.f1op + "." + "2" + "." + DigestUtils.md5Hex(json);
        } catch (JSONException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    static String toDeterministicString(Object o) throws JSONException {
        JSONStringer stringer = new JSONStringer();
        addToStringer(stringer, o);
        return stringer.toString();
    }

    static void addToStringer(JSONStringer stringer, Object o) throws JSONException {
        if (o instanceof JSONObject) {
            stringer.object();
            JSONObject object = (JSONObject) o;
            Iterator<String> keyIterator = object.keys();
            ArrayList<String> keys = new ArrayList<>();
            while (keyIterator.hasNext()) {
                keys.add(keyIterator.next());
            }
            Collections.sort(keys);
            Iterator i$ = keys.iterator();
            while (i$.hasNext()) {
                String key = (String) i$.next();
                stringer.key(key);
                addToStringer(stringer, object.opt(key));
            }
            stringer.endObject();
        } else if (o instanceof JSONArray) {
            JSONArray array = (JSONArray) o;
            stringer.array();
            for (int i = 0; i < array.length(); i++) {
                addToStringer(stringer, array.get(i));
            }
            stringer.endArray();
        } else {
            stringer.value(o);
        }
    }

    /* access modifiers changed from: protected */
    public HttpEntity newEntity() {
        Iterator<String> keys = this.params.keys();
        JSONObject fullParams = new JSONObject();
        while (keys.hasNext()) {
            try {
                String key = (String) keys.next();
                fullParams.put(key, this.params.get(key));
            } catch (JSONException e) {
                throw new RuntimeException(e.getMessage());
            }
        }
        fullParams.put("v", "a1.4.3");
        fullParams.put("iid", ParseInstallation.getOrCreateCurrentInstallationId());
        fullParams.put("uuid", UUID.randomUUID().toString());
        if (this.sessionToken != null) {
            fullParams.put("session_token", this.sessionToken);
        }
        try {
            StringEntity entity = new StringEntity(fullParams.toString(), "UTF8");
            entity.setContentType("application/json");
            return entity;
        } catch (UnsupportedEncodingException e2) {
            throw new RuntimeException(e2.getMessage());
        }
    }

    /* access modifiers changed from: protected */
    public HttpUriRequest newRequest() throws ParseException {
        HttpUriRequest request = super.newRequest();
        try {
            OAuthConsumer consumer = new CommonsHttpOAuthConsumer(Parse.applicationId, Parse.clientKey);
            consumer.setTokenWithSecret(null, "");
            consumer.sign((Object) request);
            return request;
        } catch (OAuthMessageSignerException e) {
            throw new ParseException((int) ParseException.NOT_INITIALIZED, e.getMessage());
        } catch (OAuthExpectationFailedException e2) {
            throw new ParseException((int) ParseException.NOT_INITIALIZED, e2.getMessage());
        } catch (OAuthCommunicationException e3) {
            throw new ParseException((int) ParseException.NOT_INITIALIZED, e3.getMessage());
        }
    }

    /* access modifiers changed from: protected */
    public Task<Void> onPreExecute(Task<Void> task) {
        Parse.checkInit();
        resolveLocalIds();
        return task;
    }

    /* access modifiers changed from: protected */
    public JSONObject onResponse(HttpResponse response, ProgressCallback progressCallback) throws IOException, ParseException {
        try {
            return new JSONObject(new JSONTokener(new String(ParseIOUtils.toByteArray(AndroidHttpClient.getUngzippedContent(response.getEntity())))));
        } catch (JSONException e) {
            throw connectionFailed("bad json response", e);
        }
    }

    /* access modifiers changed from: protected */
    public Task<Object> onPostExecute(Task<JSONObject> task) throws ParseException {
        JSONObject json = (JSONObject) task.getResult();
        try {
            if (!json.has("error")) {
                return Task.forResult(json.get("result"));
            }
            throw new ParseException(json.getInt("code"), json.getString("error"));
        } catch (JSONException e) {
            throw connectionFailed("corrupted json", e);
        }
    }

    private static void getLocalPointersIn(Object container, ArrayList<JSONObject> localPointers) throws JSONException {
        if (container instanceof JSONObject) {
            JSONObject object = (JSONObject) container;
            if (!"Pointer".equals(object.opt("__type")) || !object.has("localId")) {
                Iterator<String> keyIterator = object.keys();
                while (keyIterator.hasNext()) {
                    getLocalPointersIn(object.get((String) keyIterator.next()), localPointers);
                }
            } else {
                localPointers.add((JSONObject) container);
                return;
            }
        }
        if (container instanceof JSONArray) {
            JSONArray array = (JSONArray) container;
            for (int i = 0; i < array.length(); i++) {
                getLocalPointersIn(array.get(i), localPointers);
            }
        }
    }

    public void maybeChangeServerOperation() throws JSONException {
        if (this.localId != null) {
            String objectId = LocalIdManager.getDefaultInstance().getObjectId(this.localId);
            if (objectId != null) {
                this.localId = null;
                JSONObject data = this.params.optJSONObject("data");
                if (data != null) {
                    data.put("objectId", objectId);
                }
                if (this.f1op.equals("create")) {
                    setOp("update");
                }
            }
        }
    }

    public void resolveLocalIds() {
        try {
            Object data = this.params.get("data");
            ArrayList<JSONObject> localPointers = new ArrayList<>();
            getLocalPointersIn(data, localPointers);
            Iterator i$ = localPointers.iterator();
            while (i$.hasNext()) {
                JSONObject pointer = (JSONObject) i$.next();
                String objectId = LocalIdManager.getDefaultInstance().getObjectId((String) pointer.get("localId"));
                if (objectId == null) {
                    throw new IllegalStateException("Tried to serialize a command referencing a new, unsaved object.");
                }
                pointer.put("objectId", objectId);
                pointer.remove("localId");
            }
            maybeChangeServerOperation();
        } catch (JSONException e) {
        }
    }

    public void retainLocalIds() {
        if (this.localId != null) {
            LocalIdManager.getDefaultInstance().retainLocalIdOnDisk(this.localId);
        }
        try {
            Object data = this.params.get("data");
            ArrayList<JSONObject> localPointers = new ArrayList<>();
            getLocalPointersIn(data, localPointers);
            Iterator i$ = localPointers.iterator();
            while (i$.hasNext()) {
                LocalIdManager.getDefaultInstance().retainLocalIdOnDisk((String) ((JSONObject) i$.next()).get("localId"));
            }
        } catch (JSONException e) {
        }
    }

    public void releaseLocalIds() {
        if (this.localId != null) {
            LocalIdManager.getDefaultInstance().releaseLocalIdOnDisk(this.localId);
        }
        try {
            Object data = this.params.get("data");
            ArrayList<JSONObject> localPointers = new ArrayList<>();
            getLocalPointersIn(data, localPointers);
            Iterator i$ = localPointers.iterator();
            while (i$.hasNext()) {
                LocalIdManager.getDefaultInstance().releaseLocalIdOnDisk((String) ((JSONObject) i$.next()).get("localId"));
            }
        } catch (JSONException e) {
        }
    }
}
