package com.parse;

import org.json.JSONObject;

class PointerEncodingStrategy extends PointerOrLocalIdEncodingStrategy {
    private static final PointerEncodingStrategy instance = new PointerEncodingStrategy();

    PointerEncodingStrategy() {
    }

    public static PointerEncodingStrategy get() {
        return instance;
    }

    public JSONObject encodeRelatedObject(ParseObject object) {
        if (object.getObjectId() != null) {
            return super.encodeRelatedObject(object);
        }
        throw new IllegalStateException("unable to encode an association with an unsaved ParseObject");
    }
}
