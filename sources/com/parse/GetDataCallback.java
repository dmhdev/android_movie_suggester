package com.parse;

public abstract class GetDataCallback extends ParseCallback<byte[]> {
    public abstract void done(byte[] bArr, ParseException parseException);

    /* access modifiers changed from: 0000 */
    public final void internalDone(byte[] returnValue, ParseException e) {
        done(returnValue, e);
    }
}
