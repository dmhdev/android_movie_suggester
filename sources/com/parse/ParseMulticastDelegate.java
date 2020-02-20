package com.parse;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

class ParseMulticastDelegate<T> {
    private final List<ParseCallback<T>> callbacks = new LinkedList();

    public void subscribe(ParseCallback<T> callback) {
        this.callbacks.add(callback);
    }

    public void unsubscribe(ParseCallback<T> callback) {
        this.callbacks.remove(callback);
    }

    public void invoke(T result, ParseException exception) {
        Iterator i$ = new ArrayList(this.callbacks).iterator();
        while (i$.hasNext()) {
            ((ParseCallback) i$.next()).internalDone(result, exception);
        }
    }

    public void clear() {
        this.callbacks.clear();
    }
}
