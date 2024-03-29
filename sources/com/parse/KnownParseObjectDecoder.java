package com.parse;

import java.util.Map;

class KnownParseObjectDecoder extends ParseDecoder {
    private Map<String, ParseObject> fetchedObjects;

    public KnownParseObjectDecoder(Map<String, ParseObject> fetchedObjects2) {
        this.fetchedObjects = fetchedObjects2;
    }

    /* access modifiers changed from: protected */
    public ParseObject decodePointer(String className, String objectId) {
        if (this.fetchedObjects == null || !this.fetchedObjects.containsKey(objectId)) {
            return super.decodePointer(className, objectId);
        }
        return (ParseObject) this.fetchedObjects.get(objectId);
    }
}
