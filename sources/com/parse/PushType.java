package com.parse;

enum PushType {
    NONE("none"),
    PPNS("ppns"),
    GCM("gcm");
    
    private final String pushType;

    private PushType(String pushType2) {
        this.pushType = pushType2;
    }

    static PushType fromString(String pushType2) {
        if ("none".equals(pushType2)) {
            return NONE;
        }
        if ("ppns".equals(pushType2)) {
            return PPNS;
        }
        if ("gcm".equals(pushType2)) {
            return GCM;
        }
        return null;
    }

    public String toString() {
        return this.pushType;
    }
}
