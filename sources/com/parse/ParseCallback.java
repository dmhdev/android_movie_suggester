package com.parse;

abstract class ParseCallback<T> {
    /* access modifiers changed from: 0000 */
    public abstract void internalDone(T t, ParseException parseException);
}
