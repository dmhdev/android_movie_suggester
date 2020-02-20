package com.parse;

public abstract class LogInCallback extends ParseCallback<ParseUser> {
    public abstract void done(ParseUser parseUser, ParseException parseException);

    /* access modifiers changed from: 0000 */
    public void internalDone(ParseUser returnValue, ParseException e) {
        done(returnValue, e);
    }
}
