package com.parse;

public abstract class ProgressCallback extends ParseCallback<Integer> {
    Integer maxProgressSoFar = Integer.valueOf(0);

    public abstract void done(Integer num);

    /* access modifiers changed from: 0000 */
    public final void internalDone(Integer percentDone, ParseException e) {
        if (percentDone.intValue() > this.maxProgressSoFar.intValue()) {
            this.maxProgressSoFar = percentDone;
            done(percentDone);
        }
    }
}
