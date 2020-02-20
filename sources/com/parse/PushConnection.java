package com.parse;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Looper;
import android.os.SystemClock;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

class PushConnection {
    private static final int CONNECT_TIMEOUT_MS = 40000;
    static boolean ENABLE_QUICK_ACK_CHECK = true;
    static boolean ENABLE_RETRY_DELAY = true;
    static long KEEP_ALIVE_ACK_INTERVAL = 60000;
    static long KEEP_ALIVE_INTERVAL = 900000;
    private static final long MAX_RETRY_DELAY_MS = 300000;
    private static final long MIN_RETRY_DELAY_MS = 15000;
    private static final double RETRY_MULT_FACTOR_MAX = 2.0d;
    private static final double RETRY_MULT_FACTOR_MIN = 1.5d;
    private static final String TAG = "com.parse.PushConnection";
    /* access modifiers changed from: private */
    public static StateTransitionListener stateTransitionListener;
    /* access modifiers changed from: private */
    public final EventSet eventSet = new EventSet();
    /* access modifiers changed from: private */
    public final ExecutorService executor = Executors.newSingleThreadExecutor();
    /* access modifiers changed from: private */
    public final String host;
    /* access modifiers changed from: private */
    public final AtomicLong lastReadTime = new AtomicLong();
    /* access modifiers changed from: private */
    public final int port;
    /* access modifiers changed from: private */
    public final Service service;

    public class ConnectState extends State {
        private long lastDelay;

        public ConnectState(long lastDelay2) {
            super();
            this.lastDelay = lastDelay2;
        }

        public State runState() {
            boolean connectedAndSentHandshake = false;
            Socket socket = new Socket();
            Throwable t = null;
            try {
                InetSocketAddress address = new InetSocketAddress(PushConnection.this.host, PushConnection.this.port);
                if (address != null) {
                    socket.connect(address, PushConnection.CONNECT_TIMEOUT_MS);
                    socket.setKeepAlive(true);
                    socket.setTcpNoDelay(true);
                    connectedAndSentHandshake = sendHandshake(socket);
                }
            } catch (IOException e) {
                t = e;
            } catch (SecurityException e2) {
                t = e2;
            }
            if (t != null) {
                Parse.logI(PushConnection.TAG, "Failed to connect to push server due to " + t);
            }
            if (connectedAndSentHandshake) {
                return new ConnectedState(socket);
            }
            PushConnection.closeSocket(socket);
            return new WaitRetryState(nextDelay());
        }

        private boolean sendHandshake(Socket socket) {
            Task<JSONObject> handshakeTask = PushRouter.getPushRequestJSONAsync();
            try {
                handshakeTask.waitForCompletion();
            } catch (InterruptedException e) {
                Parse.logE(PushConnection.TAG, "Unexpected interruption when waiting for handshake to be sent", e);
            }
            JSONObject request = (JSONObject) handshakeTask.getResult();
            if (request != null) {
                return PushConnection.writeLine(socket, request.toString());
            }
            return false;
        }

        private long nextDelay() {
            return Math.min(Math.max(PushConnection.MIN_RETRY_DELAY_MS, (long) (((double) this.lastDelay) * (PushConnection.RETRY_MULT_FACTOR_MIN + (Math.random() * 0.5d)))), PushConnection.MAX_RETRY_DELAY_MS);
        }
    }

    public class ConnectedState extends State {
        private Socket socket;

        public ConnectedState(Socket socket2) {
            super();
            this.socket = socket2;
        }

        public State runState() {
            State nextState = null;
            ReachabilityMonitor reachabilityMonitor = new ReachabilityMonitor();
            KeepAliveMonitor keepAliveMonitor = new KeepAliveMonitor(this.socket, PushConnection.KEEP_ALIVE_INTERVAL);
            ReaderThread readerThread = new ReaderThread(this.socket);
            reachabilityMonitor.register();
            keepAliveMonitor.register();
            readerThread.start();
            while (nextState == null) {
                Set<Event> e = PushConnection.this.eventSet.await(Event.STOP, Event.CONNECTIVITY_CHANGED, Event.KEEP_ALIVE_ERROR, Event.READ_ERROR);
                if (e.contains(Event.STOP)) {
                    nextState = new StoppedState();
                } else if (e.contains(Event.READ_ERROR) || e.contains(Event.KEEP_ALIVE_ERROR) || e.contains(Event.CONNECTIVITY_CHANGED)) {
                    nextState = new WaitRetryState(0);
                }
            }
            reachabilityMonitor.unregister();
            keepAliveMonitor.unregister();
            readerThread.stopReading();
            PushConnection.closeSocket(this.socket);
            PushConnection.this.eventSet.removeEvents(Event.CONNECTIVITY_CHANGED, Event.KEEP_ALIVE_ERROR, Event.READ_ERROR);
            return nextState;
        }
    }

    private enum Event {
        START,
        STOP,
        CONNECTIVITY_CHANGED,
        KEEP_ALIVE_ERROR,
        READ_ERROR
    }

    private static class EventSet {
        private final Condition condition;
        private final Lock lock;
        private final HashSet<Event> signaledEvents;

        private EventSet() {
            this.lock = new ReentrantLock();
            this.condition = this.lock.newCondition();
            this.signaledEvents = new HashSet<>();
        }

        public void signalEvent(Event event) {
            this.lock.lock();
            try {
                this.signaledEvents.add(event);
                this.condition.signalAll();
            } finally {
                this.lock.unlock();
            }
        }

        public void removeEvents(Event... eventsToRemove) {
            this.lock.lock();
            try {
                for (Event e : eventsToRemove) {
                    this.signaledEvents.remove(e);
                }
            } finally {
                this.lock.unlock();
            }
        }

        public Set<Event> await(Event... eventsToAwait) {
            return timedAwait(Long.MAX_VALUE, eventsToAwait);
        }

        public Set<Event> timedAwait(long timeoutMs, Event... eventsToAwait) {
            Set<Event> e;
            Set<Event> e2 = Collections.EMPTY_SET;
            Set<Event> toAwait = new HashSet<>(Arrays.asList(eventsToAwait));
            long startMs = SystemClock.elapsedRealtime();
            boolean awaitForever = timeoutMs == Long.MAX_VALUE;
            this.lock.lock();
            while (true) {
                try {
                    e = e2;
                    long delta = SystemClock.elapsedRealtime() - startMs;
                    e2 = new HashSet<>(toAwait);
                    try {
                        e2.retainAll(this.signaledEvents);
                        this.signaledEvents.removeAll(toAwait);
                        if (e2.size() != 0 || (!awaitForever && delta >= timeoutMs)) {
                            break;
                        } else if (awaitForever) {
                            this.condition.awaitUninterruptibly();
                        } else {
                            try {
                                this.condition.await(timeoutMs - delta, TimeUnit.MILLISECONDS);
                            } catch (InterruptedException e3) {
                            }
                        }
                    } catch (Throwable th) {
                        th = th;
                    }
                } catch (Throwable th2) {
                    th = th2;
                    Set set = e;
                    this.lock.unlock();
                    throw th;
                }
            }
            this.lock.unlock();
            return e2;
        }
    }

    private class KeepAliveMonitor {
        private PendingIntent broadcast;
        private final long interval;
        /* access modifiers changed from: private */
        public Task<Void> keepAliveTask;
        /* access modifiers changed from: private */
        public AlarmManager manager;
        private BroadcastReceiver readReceiver;
        /* access modifiers changed from: private */
        public final Socket socket;
        private boolean unregistered;
        private BroadcastReceiver writeReceiver;

        public KeepAliveMonitor(Socket socket2, long interval2) {
            this.socket = socket2;
            this.interval = interval2;
        }

        public void register() {
            final Context appContext = Parse.applicationContext;
            String packageName = appContext.getPackageName();
            String str = "com.parse.PushConnection.readKeepAlive";
            final Intent readIntent = new Intent("com.parse.PushConnection.readKeepAlive");
            readIntent.setPackage(packageName);
            readIntent.addCategory(packageName);
            String str2 = "com.parse.PushConnection.writeKeepAlive";
            Intent intent = new Intent("com.parse.PushConnection.writeKeepAlive");
            intent.setPackage(packageName);
            intent.addCategory(packageName);
            this.manager = (AlarmManager) appContext.getSystemService("alarm");
            PendingIntent oldReadBroadcast = PendingIntent.getBroadcast(appContext, 0, readIntent, 0);
            this.manager.cancel(oldReadBroadcast);
            oldReadBroadcast.cancel();
            this.broadcast = PendingIntent.getBroadcast(appContext, 0, intent, 0);
            this.manager.cancel(this.broadcast);
            this.manager.setInexactRepeating(2, SystemClock.elapsedRealtime(), this.interval, this.broadcast);
            this.readReceiver = new BroadcastReceiver() {
                public void onReceive(Context context, Intent intent) {
                    long delta = SystemClock.elapsedRealtime() - PushConnection.this.lastReadTime.get();
                    if (delta > PushConnection.KEEP_ALIVE_ACK_INTERVAL * 2) {
                        Parse.logV(PushConnection.TAG, "Keep alive failure: last read was " + delta + " ms ago.");
                        KeepAliveMonitor.this.signalKeepAliveFailure();
                    }
                }
            };
            this.writeReceiver = new BroadcastReceiver() {
                public void onReceive(Context context, Intent intent) {
                    final ParseWakeLock wl = ParseWakeLock.acquireNewWakeLock(PushConnection.this.service, 1, "push-keep-alive", 20000);
                    if (KeepAliveMonitor.this.keepAliveTask == null) {
                        KeepAliveMonitor.this.keepAliveTask = Task.forResult(null).makeVoid();
                    }
                    KeepAliveMonitor.this.keepAliveTask = KeepAliveMonitor.this.keepAliveTask.continueWith(new Continuation<Void, Void>() {
                        public Void then(Task<Void> task) {
                            if (!PushConnection.writeLine(KeepAliveMonitor.this.socket, "{}")) {
                                KeepAliveMonitor.this.signalKeepAliveFailure();
                            }
                            boolean quickAckCheckSucceeded = false;
                            if (PushConnection.ENABLE_QUICK_ACK_CHECK) {
                                try {
                                    Thread.sleep(2500);
                                } catch (InterruptedException e) {
                                }
                                quickAckCheckSucceeded = SystemClock.elapsedRealtime() - PushConnection.this.lastReadTime.get() <= 2 * 2500;
                            }
                            if (!quickAckCheckSucceeded) {
                                KeepAliveMonitor.this.manager.set(2, SystemClock.elapsedRealtime() + PushConnection.KEEP_ALIVE_ACK_INTERVAL, PendingIntent.getBroadcast(appContext, System.identityHashCode(this), readIntent, 1342177280));
                            } else {
                                Parse.logV(PushConnection.TAG, "Keep alive ack was received quickly.");
                            }
                            wl.release();
                            return null;
                        }
                    }, ParseCommand.NETWORK_EXECUTOR);
                }
            };
            IntentFilter readFilter = new IntentFilter("com.parse.PushConnection.readKeepAlive");
            readFilter.addCategory(packageName);
            appContext.registerReceiver(this.readReceiver, readFilter);
            IntentFilter writeFilter = new IntentFilter("com.parse.PushConnection.writeKeepAlive");
            writeFilter.addCategory(packageName);
            appContext.registerReceiver(this.writeReceiver, writeFilter);
        }

        /* access modifiers changed from: private */
        public synchronized void signalKeepAliveFailure() {
            if (!this.unregistered) {
                PushConnection.this.eventSet.signalEvent(Event.KEEP_ALIVE_ERROR);
            }
        }

        public void unregister() {
            Parse.applicationContext.unregisterReceiver(this.readReceiver);
            Parse.applicationContext.unregisterReceiver(this.writeReceiver);
            this.manager.cancel(this.broadcast);
            this.broadcast.cancel();
            synchronized (this) {
                this.unregistered = true;
            }
        }
    }

    private class ReachabilityMonitor {
        private ConnectivityListener listener;
        /* access modifiers changed from: private */
        public boolean unregistered;

        private ReachabilityMonitor() {
        }

        public void register() {
            this.listener = new ConnectivityListener() {
                public void networkConnectivityStatusChanged(Intent intent) {
                    synchronized (ReachabilityMonitor.this) {
                        if (!ReachabilityMonitor.this.unregistered) {
                            PushConnection.this.eventSet.signalEvent(Event.CONNECTIVITY_CHANGED);
                        }
                    }
                }
            };
            ConnectivityNotifier.getNotifier().addListener(this.listener, PushConnection.this.service);
        }

        public void unregister() {
            ConnectivityNotifier.getNotifier().removeListener(this.listener);
            synchronized (this) {
                this.unregistered = true;
            }
        }
    }

    private class ReaderThread extends Thread {
        private Socket socket;
        private boolean stopped = false;

        public ReaderThread(Socket socket2) {
            this.socket = socket2;
        }

        public void run() {
            BufferedReader reader = null;
            try {
                reader = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));
            } catch (IOException e) {
            }
            if (reader != null) {
                runReaderLoop(reader);
                try {
                    reader.close();
                } catch (IOException e2) {
                }
            }
            synchronized (this) {
                if (!this.stopped) {
                    PushConnection.this.eventSet.signalEvent(Event.READ_ERROR);
                }
            }
        }

        private void runReaderLoop(BufferedReader reader) {
            while (true) {
                String line = null;
                try {
                    line = reader.readLine();
                    PushConnection.this.lastReadTime.set(SystemClock.elapsedRealtime());
                } catch (IOException e) {
                }
                if (line != null) {
                    JSONObject message = null;
                    try {
                        message = new JSONObject(new JSONTokener(line));
                    } catch (JSONException e2) {
                        Parse.logE(PushConnection.TAG, "bad json: " + line, e2);
                    }
                    if (message != null) {
                        PushRouter.handlePpnsPushAsync(message);
                    }
                    synchronized (this) {
                        if (this.stopped) {
                            return;
                        }
                    }
                } else {
                    return;
                }
            }
        }

        public void stopReading() {
            synchronized (this) {
                this.stopped = true;
            }
        }
    }

    public abstract class State implements Runnable {
        public abstract State runState();

        public State() {
        }

        public void run() {
            State nextState = runState();
            synchronized (PushConnection.class) {
                if (PushConnection.stateTransitionListener != null) {
                    PushConnection.stateTransitionListener.onStateChange(PushConnection.this, this, nextState);
                }
            }
            if (isTerminal()) {
                Parse.logI(PushConnection.TAG, this + " finished and is the terminal state. Thread exiting.");
                PushConnection.this.executor.shutdown();
            } else if (nextState != null) {
                Parse.logI(PushConnection.TAG, "PushConnection transitioning from " + this + " to " + nextState);
                PushConnection.this.executor.execute(nextState);
            } else {
                throw new NullPointerException(this + " tried to transition to null state.");
            }
        }

        public boolean isTerminal() {
            return false;
        }
    }

    public interface StateTransitionListener {
        void onStateChange(PushConnection pushConnection, State state, State state2);
    }

    public class StoppedState extends State {
        public StoppedState() {
            super();
        }

        public State runState() {
            return null;
        }

        public boolean isTerminal() {
            return true;
        }
    }

    public class WaitRetryState extends State {
        private long delay;

        public WaitRetryState(long delay2) {
            super();
            this.delay = delay2;
        }

        public long getDelay() {
            return this.delay;
        }

        public State runState() {
            PushConnection.this.eventSet.removeEvents(Event.START);
            long actualDelay = this.delay;
            if (!PushConnection.ENABLE_RETRY_DELAY) {
                actualDelay = 0;
            }
            Set<Event> e = PushConnection.this.eventSet.timedAwait(actualDelay, Event.STOP, Event.START);
            if (e.contains(Event.STOP)) {
                return new StoppedState();
            }
            if (e.contains(Event.START)) {
                return new ConnectState(0);
            }
            return new ConnectState(this.delay);
        }
    }

    public class WaitStartState extends State {
        public WaitStartState() {
            super();
        }

        public State runState() {
            Set<Event> e = PushConnection.this.eventSet.await(Event.START, Event.STOP);
            if (e.contains(Event.STOP)) {
                return new StoppedState();
            }
            if (e.contains(Event.START)) {
                return new ConnectState(0);
            }
            return null;
        }
    }

    public PushConnection(Service service2, String host2, int port2) {
        this.service = service2;
        this.host = host2;
        this.port = port2;
        this.executor.execute(new WaitStartState());
    }

    public synchronized void start() {
        this.eventSet.signalEvent(Event.START);
    }

    public synchronized void stop() {
        this.eventSet.signalEvent(Event.STOP);
    }

    /* access modifiers changed from: private */
    public static boolean writeLine(Socket socket, String string) {
        if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
            throw new Error("Wrote to push socket on main thread.");
        }
        try {
            OutputStream stream = socket.getOutputStream();
            stream.write((string + "\n").getBytes("UTF-8"));
            stream.flush();
            return true;
        } catch (IOException e) {
            Parse.logV(TAG, "PushConnection write failed: " + string + " due to exception: " + e);
            return false;
        }
    }

    /* access modifiers changed from: private */
    public static void closeSocket(Socket socket) {
        try {
            socket.shutdownInput();
            socket.close();
        } catch (IOException e) {
        }
    }

    public static void setStateTransitionListener(StateTransitionListener listener) {
        synchronized (PushConnection.class) {
            stateTransitionListener = listener;
        }
    }
}
