package com.parse;

import org.json.JSONObject;

class NoObjectsEncodingStrategy implements ParseObjectEncodingStrategy {
    private static final NoObjectsEncodingStrategy instance = new NoObjectsEncodingStrategy();

    NoObjectsEncodingStrategy() {
    }

    public static NoObjectsEncodingStrategy get() {
        return instance;
    }

    public JSONObject encodeRelatedObject(ParseObject object) {
        throw new IllegalArgumentException("ParseObjects not allowed here");
    }
}
