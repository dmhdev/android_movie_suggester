package com.parse;

public abstract class CountCallback extends ParseCallback<Integer> {
    public abstract void done(int i, ParseException parseException);

    /* access modifiers changed from: 0000 */
    public void internalDone(Integer returnValue, ParseException e) {
        if (e == null) {
            done(returnValue.intValue(), null);
        } else {
            done(-1, e);
        }
    }
}
