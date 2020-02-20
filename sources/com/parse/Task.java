package com.parse;

import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

class Task<TResult> {
    public static final ExecutorService BACKGROUND_EXECUTOR = Executors.newCachedThreadPool();
    private static final Executor IMMEDIATE_EXECUTOR = new ImmediateExecutor();
    public static final Executor UI_THREAD_EXECUTOR = new UIThreadExecutor();
    /* access modifiers changed from: private */
    public boolean cancelled;
    /* access modifiers changed from: private */
    public boolean complete;
    private List<Continuation<TResult, Void>> continuations = new ArrayList();
    /* access modifiers changed from: private */
    public Exception error;
    /* access modifiers changed from: private */
    public final Object lock = new Object();
    /* access modifiers changed from: private */
    public TResult result;

    private static class ImmediateExecutor implements Executor {
        private static final int MAX_DEPTH = 15;
        private ThreadLocal<Integer> executionDepth;

        private ImmediateExecutor() {
            this.executionDepth = new ThreadLocal<>();
        }

        private int incrementDepth() {
            Integer oldDepth = (Integer) this.executionDepth.get();
            if (oldDepth == null) {
                oldDepth = Integer.valueOf(0);
            }
            int newDepth = oldDepth.intValue() + 1;
            this.executionDepth.set(Integer.valueOf(newDepth));
            return newDepth;
        }

        private int decrementDepth() {
            Integer oldDepth = (Integer) this.executionDepth.get();
            if (oldDepth == null) {
                oldDepth = Integer.valueOf(0);
            }
            int newDepth = oldDepth.intValue() - 1;
            if (newDepth == 0) {
                this.executionDepth.remove();
            } else {
                this.executionDepth.set(Integer.valueOf(newDepth));
            }
            return newDepth;
        }

        public void execute(Runnable command) {
            if (incrementDepth() <= 15) {
                try {
                    command.run();
                } catch (Throwable th) {
                    decrementDepth();
                    throw th;
                }
            } else {
                Task.BACKGROUND_EXECUTOR.execute(command);
            }
            decrementDepth();
        }
    }

    public class TaskCompletionSource {
        private TaskCompletionSource() {
        }

        public Task<TResult> getTask() {
            return Task.this;
        }

        public boolean trySetCancelled() {
            boolean z = true;
            synchronized (Task.this.lock) {
                if (Task.this.complete) {
                    z = false;
                } else {
                    Task.this.complete = true;
                    Task.this.cancelled = true;
                    Task.this.lock.notifyAll();
                    Task.this.runContinuations();
                }
            }
            return z;
        }

        public boolean trySetResult(TResult result) {
            boolean z = true;
            synchronized (Task.this.lock) {
                if (Task.this.complete) {
                    z = false;
                } else {
                    Task.this.complete = true;
                    Task.this.result = result;
                    Task.this.lock.notifyAll();
                    Task.this.runContinuations();
                }
            }
            return z;
        }

        public boolean trySetError(Exception error) {
            boolean z = true;
            synchronized (Task.this.lock) {
                if (Task.this.complete) {
                    z = false;
                } else {
                    Task.this.complete = true;
                    Task.this.error = error;
                    Task.this.lock.notifyAll();
                    Task.this.runContinuations();
                }
            }
            return z;
        }

        public void setCancelled() {
            if (!trySetCancelled()) {
                throw new IllegalStateException("Cannot cancel a completed task.");
            }
        }

        public void setResult(TResult result) {
            if (!trySetResult(result)) {
                throw new IllegalStateException("Cannot set the result of a completed task.");
            }
        }

        public void setError(Exception error) {
            if (!trySetError(error)) {
                throw new IllegalStateException("Cannot set the error on a completed task.");
            }
        }
    }

    private static class UIThreadExecutor implements Executor {
        private UIThreadExecutor() {
        }

        public void execute(Runnable command) {
            new Handler(Looper.getMainLooper()).post(command);
        }
    }

    private Task() {
    }

    public static <TResult> TaskCompletionSource create() {
        Task<TResult> task = new Task<>();
        task.getClass();
        return new TaskCompletionSource<>();
    }

    public boolean isCompleted() {
        boolean z;
        synchronized (this.lock) {
            z = this.complete;
        }
        return z;
    }

    public boolean isCancelled() {
        boolean z;
        synchronized (this.lock) {
            z = this.cancelled;
        }
        return z;
    }

    public boolean isFaulted() {
        boolean z;
        synchronized (this.lock) {
            z = this.error != null;
        }
        return z;
    }

    public TResult getResult() {
        TResult tresult;
        synchronized (this.lock) {
            tresult = this.result;
        }
        return tresult;
    }

    public Exception getError() {
        Exception exc;
        synchronized (this.lock) {
            exc = this.error;
        }
        return exc;
    }

    public void waitForCompletion() throws InterruptedException {
        synchronized (this.lock) {
            if (!isCompleted()) {
                this.lock.wait();
            }
        }
    }

    public static <TResult> Task<TResult> forResult(TResult value) {
        TaskCompletionSource tcs = create();
        tcs.setResult(value);
        return tcs.getTask();
    }

    public static <TResult> Task<TResult> forError(Exception error2) {
        TaskCompletionSource tcs = create();
        tcs.setError(error2);
        return tcs.getTask();
    }

    public static <TResult> Task<TResult> cancelled() {
        TaskCompletionSource tcs = create();
        tcs.setCancelled();
        return tcs.getTask();
    }

    public <TOut> Task<TOut> cast() {
        return this;
    }

    public Task<Void> makeVoid() {
        return continueWithTask(new Continuation<TResult, Task<Void>>() {
            public Task<Void> then(Task<TResult> task) throws Exception {
                if (task.isCancelled()) {
                    return Task.cancelled();
                }
                if (task.isFaulted()) {
                    return Task.forError(task.getError());
                }
                return Task.forResult(null);
            }
        });
    }

    public static <TResult> Task<TResult> callInBackground(Callable<TResult> callable) {
        return call(callable, BACKGROUND_EXECUTOR);
    }

    public static <TResult> Task<TResult> call(final Callable<TResult> callable, Executor executor) {
        final TaskCompletionSource tcs = create();
        executor.execute(new Runnable() {
            public void run() {
                try {
                    tcs.setResult(callable.call());
                } catch (Exception e) {
                    tcs.setError(e);
                }
            }
        });
        return tcs.getTask();
    }

    public static <TResult> Task<TResult> call(Callable<TResult> callable) {
        return call(callable, IMMEDIATE_EXECUTOR);
    }

    public static Task<Void> whenAll(Collection<? extends Task<?>> tasks) {
        final TaskCompletionSource tcs = create();
        if (tasks.size() == 0) {
            tcs.setResult(null);
        } else {
            final AtomicInteger count = new AtomicInteger(tasks.size());
            for (Task<?> task : tasks) {
                task.continueWith(new Continuation<Object, Void>() {
                    public Void then(Task<Object> task) {
                        if (count.decrementAndGet() == 0) {
                            tcs.setResult(null);
                        }
                        return null;
                    }
                });
            }
        }
        return tcs.getTask();
    }

    public Task<Void> continueWhile(Callable<Boolean> predicate, Continuation<Void, Task<Void>> continuation) {
        return continueWhile(predicate, continuation, IMMEDIATE_EXECUTOR);
    }

    public Task<Void> continueWhile(Callable<Boolean> predicate, Continuation<Void, Task<Void>> continuation, Executor executor) {
        final Capture<Continuation<Void, Task<Void>>> predicateContinuation = new Capture<>();
        final Callable<Boolean> callable = predicate;
        final Continuation<Void, Task<Void>> continuation2 = continuation;
        final Executor executor2 = executor;
        predicateContinuation.set(new Continuation<Void, Task<Void>>() {
            public Task<Void> then(Task<Void> task) throws Exception {
                if (((Boolean) callable.call()).booleanValue()) {
                    return Task.forResult(null).onSuccessTask(continuation2, executor2).onSuccessTask((Continuation) predicateContinuation.get(), executor2);
                }
                return Task.forResult(null);
            }
        });
        return makeVoid().continueWithTask((Continuation) predicateContinuation.get(), executor);
    }

    public <TContinuationResult> Task<TContinuationResult> continueWith(final Continuation<TResult, TContinuationResult> continuation, final Executor executor) {
        boolean completed;
        final TaskCompletionSource tcs = create();
        synchronized (this.lock) {
            completed = isCompleted();
            if (!completed) {
                this.continuations.add(new Continuation<TResult, Void>() {
                    public Void then(Task<TResult> task) {
                        Task.completeImmediately(tcs, continuation, task, executor);
                        return null;
                    }
                });
            }
        }
        if (completed) {
            completeImmediately(tcs, continuation, this, executor);
        }
        return tcs.getTask();
    }

    public <TContinuationResult> Task<TContinuationResult> continueWith(Continuation<TResult, TContinuationResult> continuation) {
        return continueWith(continuation, IMMEDIATE_EXECUTOR);
    }

    public <TContinuationResult> Task<TContinuationResult> continueWithTask(final Continuation<TResult, Task<TContinuationResult>> continuation, final Executor executor) {
        boolean completed;
        final TaskCompletionSource tcs = create();
        synchronized (this.lock) {
            completed = isCompleted();
            if (!completed) {
                this.continuations.add(new Continuation<TResult, Void>() {
                    public Void then(Task<TResult> task) {
                        Task.completeAfterTask(tcs, continuation, task, executor);
                        return null;
                    }
                });
            }
        }
        if (completed) {
            completeAfterTask(tcs, continuation, this, executor);
        }
        return tcs.getTask();
    }

    public <TContinuationResult> Task<TContinuationResult> continueWithTask(Continuation<TResult, Task<TContinuationResult>> continuation) {
        return continueWithTask(continuation, IMMEDIATE_EXECUTOR);
    }

    public <TContinuationResult> Task<TContinuationResult> onSuccess(final Continuation<TResult, TContinuationResult> continuation, Executor executor) {
        return continueWithTask(new Continuation<TResult, Task<TContinuationResult>>() {
            public Task<TContinuationResult> then(Task<TResult> task) {
                if (task.isFaulted()) {
                    return Task.forError(task.getError());
                }
                if (task.isCancelled()) {
                    return Task.cancelled();
                }
                return task.continueWith(continuation);
            }
        }, executor);
    }

    public <TContinuationResult> Task<TContinuationResult> onSuccess(Continuation<TResult, TContinuationResult> continuation) {
        return onSuccess(continuation, IMMEDIATE_EXECUTOR);
    }

    public <TContinuationResult> Task<TContinuationResult> onSuccessTask(final Continuation<TResult, Task<TContinuationResult>> continuation, Executor executor) {
        return continueWithTask(new Continuation<TResult, Task<TContinuationResult>>() {
            public Task<TContinuationResult> then(Task<TResult> task) {
                if (task.isFaulted()) {
                    return Task.forError(task.getError());
                }
                if (task.isCancelled()) {
                    return Task.cancelled();
                }
                return task.continueWithTask(continuation);
            }
        }, executor);
    }

    public <TContinuationResult> Task<TContinuationResult> onSuccessTask(Continuation<TResult, Task<TContinuationResult>> continuation) {
        return onSuccessTask(continuation, IMMEDIATE_EXECUTOR);
    }

    /* access modifiers changed from: private */
    public static <TContinuationResult, TResult> void completeImmediately(final TaskCompletionSource tcs, final Continuation<TResult, TContinuationResult> continuation, final Task<TResult> task, Executor executor) {
        executor.execute(new Runnable() {
            public void run() {
                try {
                    tcs.setResult(continuation.then(task));
                } catch (Exception e) {
                    tcs.setError(e);
                }
            }
        });
    }

    /* access modifiers changed from: private */
    public static <TContinuationResult, TResult> void completeAfterTask(final TaskCompletionSource tcs, final Continuation<TResult, Task<TContinuationResult>> continuation, final Task<TResult> task, Executor executor) {
        executor.execute(new Runnable() {
            public void run() {
                try {
                    Task<TContinuationResult> result = (Task) continuation.then(task);
                    if (result == null) {
                        tcs.setResult(null);
                    } else {
                        result.continueWith(new Continuation<TContinuationResult, Void>() {
                            public Void then(Task<TContinuationResult> task) {
                                if (task.isCancelled()) {
                                    tcs.setCancelled();
                                } else if (task.isFaulted()) {
                                    tcs.setError(task.getError());
                                } else {
                                    tcs.setResult(task.getResult());
                                }
                                return null;
                            }
                        });
                    }
                } catch (Exception e) {
                    tcs.setError(e);
                }
            }
        });
    }

    /* access modifiers changed from: private */
    public void runContinuations() {
        synchronized (this.lock) {
            for (Continuation<TResult, ?> continuation : this.continuations) {
                try {
                    continuation.then(this);
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e2) {
                    throw new RuntimeException(e2);
                }
            }
            this.continuations = null;
        }
    }
}
