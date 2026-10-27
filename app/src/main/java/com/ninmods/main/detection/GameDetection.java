package com.ninmods.main.detection;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * GAME DETECTION - JAVA ROUTING LAYER
 * ------------------------------------
 * The Java side of the dedicated detection engine.
 *
 * The engine itself lives in C++ (jni/src/Detection/GameDetection.cpp) and
 * owns the whole question "which process is the game". This class is only
 * the routing layer in front of it:
 *
 *  - it caches the state, so the UI never waits for a scan,
 *  - it watches the engine in the background and tells its listeners when
 *    the state changed,
 *  - it is the single entry point for every UI that shows the game state.
 *
 * Why the engine exists at all: the old detector only walked /proc and read
 * /proc/<pid>/cmdline. Since Android 9 an app may not read the cmdline of
 * a process of another user - and the game runs in its own user. The panel
 * therefore showed "Waiting for Game" although the game was running. The
 * engine now scans /proc with root rights (the app owns a root grant for
 * the kernel driver loader) and falls back to the plain scan.
 */
public final class GameDetection {

    private static final String TAG = "GameDetection";
    private static final long WATCH_INTERVAL_MS = 1000L;

    private static volatile GameDetection sInstance;

    /** A listener that is called when the detected game changed. */
    public interface Listener {
        void onGameDetectionChanged(GameState state);
    }

    /** An immutable snapshot of the detection state. */
    public static final class GameState {
        public final int pid;
        public final String packageName;
        public final String title;
        public final boolean isMax;
        public final boolean isDetected;

        GameState(int pid, String packageName, String title, boolean isMax, boolean isDetected) {
            this.pid = pid;
            this.packageName = packageName == null ? "" : packageName;
            this.title = title == null ? "" : title;
            this.isMax = isMax;
            this.isDetected = isDetected;
        }

        /** The text the menus show when the game is not running. */
        public static final String WAITING_TEXT = "Waiting for Game";
    }

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> mListeners = new CopyOnWriteArrayList<>();

    private volatile GameState mState = new GameState(0, "", "", false, false);
    private volatile boolean mWatching;

    /* --- the native detection engine --- */
    private static native int getGamePidNative();

    private static native String getGamePackageNative();

    private static native String getGameTitleNative();

    private static native boolean isGameMaxNative();

    private static native void refreshDetectionNative();

    static {
        try {
            System.loadLibrary("ninmods");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to load libninmods.so - the detection engine is not available", t);
        }
    }

    private GameDetection() {
    }

    public static GameDetection get() {
        if (sInstance == null) {
            synchronized (GameDetection.class) {
                if (sInstance == null) {
                    sInstance = new GameDetection();
                }
            }
        }
        return sInstance;
    }

    /**
     * Reads the current state from the engine and updates the cache.
     *
     * @return the state of the detection
     */
    public GameState refresh() {
        GameState state;
        try {
            int pid = getGamePidNative();
            String pkg = getGamePackageNative();
            String title = getGameTitleNative();
            boolean isMax = isGameMaxNative();
            state = new GameState(pid, pkg, title, isMax, pid > 0);
        } catch (Throwable t) {
            Log.e(TAG, "Detection failed: " + t.getMessage());
            state = new GameState(0, "", "", false, false);
        }

        GameState previous = mState;
        mState = state;

        if (previous.pid != state.pid || previous.isDetected != state.isDetected) {
            AppLog.d(TAG, "Detection changed: pid=" + state.pid + " title=" + state.title);
            notifyListeners(state);
        }
        return state;
    }

    /** The last known state without a new scan. */
    public GameState getState() {
        return mState;
    }

    public int getPid() {
        return mState.pid;
    }

    public String getTitle() {
        return mState.isDetected ? mState.title : GameState.WAITING_TEXT;
    }

    public String getPackageName() {
        return mState.packageName;
    }

    public boolean isMax() {
        return mState.isMax;
    }

    public boolean isDetected() {
        return mState.isDetected;
    }

    public void addListener(Listener listener) {
        if (listener != null && !mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    public void removeListener(Listener listener) {
        mListeners.remove(listener);
    }

    private void notifyListeners(GameState state) {
        for (Listener listener : mListeners) {
            try {
                listener.onGameDetectionChanged(state);
            } catch (Throwable t) {
                Log.e(TAG, "Detection listener failed: " + t.getMessage());
            }
        }
    }

    /**
     * Starts the background watcher. The panel only refreshes its own text
     * when it is visible, so the watcher keeps the state fresh even while
     * the panel is closed.
     */
    public void startWatching() {
        if (mWatching) return;
        mWatching = true;
        mHandler.post(mWatchTask);
    }

    public void stopWatching() {
        mWatching = false;
        mHandler.removeCallbacks(mWatchTask);
    }

    private final Runnable mWatchTask = new Runnable() {
        @Override
        public void run() {
            if (!mWatching) return;
            try {
                refresh();
            } catch (Throwable ignored) {
            }
            mHandler.postDelayed(this, WATCH_INTERVAL_MS);
        }
    };

    /** Tiny logging helper that never crashes the app. */
    private static final class AppLog {
        static void d(String tag, String message) {
            Log.d(tag, message);
        }
    }
}
