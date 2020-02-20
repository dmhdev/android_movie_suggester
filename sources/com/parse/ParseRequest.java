package com.parse;

import android.content.Context;
import android.net.SSLCertificateSocketFactory;
import android.net.SSLSessionCache;
import android.net.http.AndroidHttpClient;
import com.parse.Task.TaskCompletionSource;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;

abstract class ParseRequest<Response, Result> {
    private static final int CORE_POOL_SIZE = ((CPU_COUNT * 2) + 1);
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    protected static final int DEFAULT_MAX_RETRIES = 4;
    private static final long KEEP_ALIVE_TIME = 1;
    private static final int MAX_POOL_SIZE = (((CPU_COUNT * 2) * 2) + 1);
    private static final int MAX_QUEUE_SIZE = 128;
    static final ExecutorService NETWORK_EXECUTOR = newThreadPoolExecutor(CORE_POOL_SIZE, MAX_POOL_SIZE, KEEP_ALIVE_TIME, TimeUnit.SECONDS, new LinkedBlockingQueue(128));
    private static final int SOCKET_OPERATION_TIMEOUT = 10000;
    private static final String USER_AGENT = "Parse Android SDK";
    private static HttpClient defaultClient = newHttpClient(null);
    /* access modifiers changed from: private */
    public static long defaultInitialRetryDelay = 1000;
    /* access modifiers changed from: private */
    public HttpClient client;
    /* access modifiers changed from: private */
    public AtomicReference<TaskCompletionSource> currentTask;
    protected int maxRetries;
    protected int method;
    /* access modifiers changed from: private */
    public HttpUriRequest request;
    protected String url;

    public interface Method {
        public static final int GET = 0;
        public static final int POST = 1;
    }

    /* access modifiers changed from: protected */
    public abstract Response onResponse(HttpResponse httpResponse, ProgressCallback progressCallback) throws IOException, ParseException;

    private static ThreadPoolExecutor newThreadPoolExecutor(int corePoolSize, int maxPoolSize, long keepAliveTime, TimeUnit timeUnit, BlockingQueue<Runnable> workQueue) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(corePoolSize, maxPoolSize, keepAliveTime, timeUnit, workQueue);
        Executors.allowCoreThreadTimeout(executor, true);
        return executor;
    }

    public static void setDefaultClient(HttpClient client2) {
        defaultClient = client2;
    }

    public static HttpClient getDefaultClient() {
        return defaultClient;
    }

    public static void setDefaultInitialRetryDelay(long delay) {
        defaultInitialRetryDelay = delay;
    }

    public static long getDefaultInitialRetryDelay() {
        return defaultInitialRetryDelay;
    }

    public static void initialize(Context context) {
        if (defaultClient != null) {
            if (defaultClient instanceof AndroidHttpClient) {
                defaultClient.close();
            } else {
                defaultClient.getConnectionManager().shutdown();
            }
            defaultClient = null;
        }
        defaultClient = newHttpClient(context);
    }

    private static HttpClient newHttpClient(Context context) {
        HttpParams params = new BasicHttpParams();
        HttpConnectionParams.setStaleCheckingEnabled(params, false);
        HttpConnectionParams.setConnectionTimeout(params, SOCKET_OPERATION_TIMEOUT);
        HttpConnectionParams.setSoTimeout(params, SOCKET_OPERATION_TIMEOUT);
        HttpConnectionParams.setSocketBufferSize(params, 8192);
        HttpClientParams.setRedirecting(params, false);
        SSLSessionCache sessionCache = context == null ? null : new SSLSessionCache(context);
        HttpProtocolParams.setUserAgent(params, USER_AGENT);
        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
        schemeRegistry.register(new Scheme("https", SSLCertificateSocketFactory.getHttpSocketFactory(SOCKET_OPERATION_TIMEOUT, sessionCache), 443));
        ConnManagerParams.setMaxConnectionsPerRoute(params, new ConnPerRouteBean(20));
        ConnManagerParams.setMaxTotalConnections(params, 20);
        String host = System.getProperty("http.proxyHost");
        String portString = System.getProperty("http.proxyPort");
        if (!(host == null || host.length() == 0 || portString == null || portString.length() == 0)) {
            params.setParameter("http.route.default-proxy", new HttpHost(host, Integer.parseInt(portString), "http"));
        }
        return new DefaultHttpClient(new ThreadSafeClientConnManager(params, schemeRegistry), params);
    }

    public ParseRequest(String url2) {
        this(0, url2);
    }

    public ParseRequest(int method2, String url2) {
        this.maxRetries = 4;
        this.currentTask = new AtomicReference<>();
        this.client = defaultClient;
        this.method = method2;
        this.url = url2;
    }

    public void setClient(HttpClient client2) {
        this.client = client2;
    }

    public void setMethod(int method2) {
        this.method = method2;
    }

    public void setUrl(String url2) {
        this.url = url2;
    }

    public void setMaxRetries(int max) {
        this.maxRetries = max;
    }

    /* access modifiers changed from: protected */
    public Task<Void> onPreExecute(Task<Void> task) {
        return null;
    }

    /* access modifiers changed from: protected */
    public HttpEntity newEntity() {
        return null;
    }

    /* access modifiers changed from: protected */
    public HttpUriRequest newRequest() throws ParseException {
        HttpPost httpPost;
        if (this.method == 0) {
            httpPost = new HttpGet(this.url);
        } else if (this.method == 1) {
            String hostHeader = null;
            if (this.url.contains(".s3.amazonaws.com")) {
                Matcher s3UrlMatcher = Pattern.compile("^https://([a-zA-Z0-9.]*\\.s3\\.amazonaws\\.com)/?.*").matcher(this.url);
                if (s3UrlMatcher.matches()) {
                    String hostname = s3UrlMatcher.group(1);
                    this.url = this.url.replace(hostname, "s3.amazonaws.com");
                    hostHeader = hostname;
                }
            }
            HttpPost post = new HttpPost(this.url);
            post.setEntity(newEntity());
            if (hostHeader != null) {
                post.addHeader("Host", hostHeader);
            }
            httpPost = post;
        } else {
            throw new IllegalStateException("Invalid method " + this.method);
        }
        AndroidHttpClient.modifyRequestToAcceptGzipResponse(httpPost);
        return httpPost;
    }

    /* access modifiers changed from: protected */
    public Task<Result> onPostExecute(Task<Response> task) throws ParseException {
        return task.cast();
    }

    private Task<Response> sendOneRequestAsync(final ProgressCallback progressCallback) {
        if (((TaskCompletionSource) this.currentTask.get()).getTask().isCancelled()) {
            return Task.cancelled();
        }
        return Task.call(new Callable<Response>() {
            public Response call() throws Exception {
                try {
                    return ParseRequest.this.onResponse(ParseRequest.this.client.execute(ParseRequest.this.request), progressCallback);
                } catch (ClientProtocolException e) {
                    throw ParseRequest.this.connectionFailed("bad protocol", e);
                } catch (IOException e2) {
                    throw ParseRequest.this.connectionFailed("i/o failure", e2);
                }
            }
        }, ParseCommand.NETWORK_EXECUTOR);
    }

    public Task<Result> executeAsync() {
        return executeAsync(null);
    }

    public Task<Result> executeAsync(final ProgressCallback progressCallback) {
        final TaskCompletionSource tcs = Task.create();
        this.currentTask.set(tcs);
        Task.forResult(null).continueWithTask(new Continuation<Void, Task<Void>>() {
            public Task<Void> then(Task<Void> task) throws Exception {
                return ParseRequest.this.onPreExecute(task);
            }
        }).onSuccessTask(new Continuation<Void, Task<Response>>() {
            public Task<Response> then(Task<Void> task) throws Exception {
                long delay = ParseRequest.defaultInitialRetryDelay + ((long) (((double) ParseRequest.defaultInitialRetryDelay) * Math.random()));
                if (ParseRequest.this.request == null) {
                    ParseRequest.this.request = ParseRequest.this.newRequest();
                }
                return ParseRequest.this.executeAsync(0, delay, progressCallback);
            }
        }).onSuccessTask(new Continuation<Response, Task<Result>>() {
            public Task<Result> then(Task<Response> task) throws Exception {
                return ParseRequest.this.onPostExecute(task);
            }
        }).continueWithTask(new Continuation<Result, Task<Void>>() {
            public Task<Void> then(Task<Result> task) throws Exception {
                if (task.isCancelled()) {
                    tcs.trySetCancelled();
                } else if (task.isFaulted()) {
                    tcs.trySetError(task.getError());
                } else {
                    tcs.trySetResult(task.getResult());
                }
                return null;
            }
        });
        return tcs.getTask();
    }

    /* access modifiers changed from: private */
    public Task<Response> executeAsync(int attemptsMade, long delay, ProgressCallback progressCallback) {
        final int i = attemptsMade;
        final long j = delay;
        final ProgressCallback progressCallback2 = progressCallback;
        return sendOneRequestAsync(progressCallback).continueWithTask(new Continuation<Response, Task<Response>>() {
            public Task<Response> then(Task<Response> task) throws Exception {
                if (!task.isFaulted() || !(task.getError() instanceof ParseException)) {
                    return task;
                }
                if (((TaskCompletionSource) ParseRequest.this.currentTask.get()).getTask().isCancelled()) {
                    return Task.cancelled();
                }
                if (i < ParseRequest.this.maxRetries) {
                    Parse.logI("com.parse.ParseRequest", "Request failed. Waiting " + j + " milliseconds before attempt #" + (i + 1));
                    final TaskCompletionSource retryTask = Task.create();
                    Parse.getScheduledExecutor().schedule(new Runnable() {
                        public void run() {
                            ParseRequest.this.executeAsync(i + 1, j * 2, progressCallback2).continueWithTask(new Continuation<Response, Task<Void>>() {
                                public Task<Void> then(Task<Response> task) throws Exception {
                                    if (task.isCancelled()) {
                                        retryTask.setCancelled();
                                    } else if (task.isFaulted()) {
                                        retryTask.setError(task.getError());
                                    } else {
                                        retryTask.setResult(task.getResult());
                                    }
                                    return null;
                                }
                            });
                        }
                    }, j, TimeUnit.MILLISECONDS);
                    return retryTask.getTask();
                } else if (ParseRequest.this.request.isAborted()) {
                    return task;
                } else {
                    Parse.logI("com.parse.ParseRequest", "Request failed. Giving up.");
                    return task;
                }
            }
        });
    }

    public void cancel() {
        TaskCompletionSource curr = (TaskCompletionSource) this.currentTask.get();
        if (curr != null) {
            curr.trySetCancelled();
        }
        if (this.request != null) {
            this.request.abort();
        }
    }

    /* access modifiers changed from: protected */
    public ParseException connectionFailed(String message, Exception e) {
        return new ParseException(100, message + ": " + e.getClass().getName() + ": " + e.getMessage());
    }
}
