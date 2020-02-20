package com.parse;

public abstract class RequestPasswordResetCallback extends ParseCallback<Void> {
    public abstract void done(ParseException parseException);

    /* access modifiers changed from: 0000 */
    public final void internalDone(Void returnValue, ParseException e) {
        done(e);
    }
}
