package com.parse;

import org.json.JSONException;
import org.json.JSONObject;

class PointerOrLocalIdEncodingStrategy implements ParseObjectEncodingStrategy {
    private static final PointerOrLocalIdEncodingStrategy instance = new PointerOrLocalIdEncodingStrategy();

    PointerOrLocalIdEncodingStrategy() {
    }

    public static PointerOrLocalIdEncodingStrategy get() {
        return instance;
    }

    public JSONObject encodeRelatedObject(ParseObject object) {
        JSONObject json = new JSONObject();
        try {
            if (object.getObjectId() != null) {
                json.put("__type", "Pointer");
                json.put("className", object.getClassName());
                json.put("objectId", object.getObjectId());
            } else {
                json.put("__type", "Pointer");
                json.put("className", object.getClassName());
                json.put("localId", object.getOrCreateLocalId());
            }
            return json;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }
}
