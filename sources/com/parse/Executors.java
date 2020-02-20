package com.parse;

import android.os.Build.VERSION;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

final class Executors {
    static final int CORE_POOL_SIZE = (CPU_COUNT + 1);
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    static final long KEEP_ALIVE_TIME = 1;
    static final int MAX_POOL_SIZE = ((CPU_COUNT * 2) + 1);
    static final int MAX_QUEUE_SIZE = 128;

    private Executors() {
    }

    public static ExecutorService newCachedThreadPool() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(CORE_POOL_SIZE, MAX_POOL_SIZE, KEEP_ALIVE_TIME, TimeUnit.SECONDS, new LinkedBlockingQueue(128));
        allowCoreThreadTimeout(executor, true);
        return executor;
    }

    public static ExecutorService newCachedThreadPool(ThreadFactory threadFactory) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(CORE_POOL_SIZE, MAX_POOL_SIZE, KEEP_ALIVE_TIME, TimeUnit.SECONDS, new LinkedBlockingQueue(128), threadFactory);
        allowCoreThreadTimeout(executor, true);
        return executor;
    }

    public static void allowCoreThreadTimeout(ThreadPoolExecutor executor, boolean value) {
        if (VERSION.SDK_INT >= 9) {
            executor.allowCoreThreadTimeOut(value);
        }
    }
}
