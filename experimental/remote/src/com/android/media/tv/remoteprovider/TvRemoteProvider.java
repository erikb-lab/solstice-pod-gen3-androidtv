package com.android.media.tv.remoteprovider;

import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.os.StrictMode;
import android.util.Log;
import java.io.OutputStream;
import java.net.Socket;

/**
 * Stand-in for the Android TV framework's TvRemoteProvider on a non-TV build.
 * Key events go to the root KeyInjector on 127.0.0.1:6468. Opening a bridge
 * also asks KeyInjector to (re)create a uhid device with the bridge's name,
 * because AtvRemoteService 6.x waits for that input device to appear before
 * finishing the remote_configure handshake.
 */
public abstract class TvRemoteProvider {
    public static final String SERVICE_INTERFACE =
            "com.android.media.tv.remoteprovider.TvRemoteProvider";
    private static final String TAG = "TvRemoteProvider";
    private final Context mContext;
    private final Binder mStub = new Binder();

    public TvRemoteProvider(Context context) {
        mContext = context.getApplicationContext();
    }

    public final Context getContext() { return mContext; }
    public IBinder getBinder() { return mStub; }

    public void onInputBridgeConnected(IBinder token) {}

    private static void sendCmd(String cmd) {
        StrictMode.ThreadPolicy old = StrictMode.allowThreadDiskWrites();
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.LAX);
        try (Socket s = new Socket("127.0.0.1", 6468)) {
            OutputStream o = s.getOutputStream();
            o.write(cmd.getBytes("UTF-8"));
            o.flush();
        } catch (Exception e) {
            Log.e(TAG, "sendCmd failed", e);
        } finally {
            StrictMode.setThreadPolicy(old);
        }
    }

    public void openRemoteInputBridge(IBinder token, String name, int width, int height, int maxPointers) {
        Log.i(TAG, "openRemoteInputBridge: " + name + " " + width + "x" + height);
        String safe = name == null ? "virtual-remote" : name.replaceAll("[^A-Za-z0-9._-]", "");
        sendCmd("O " + safe + " " + android.os.Process.myPid() + "\n");
        onInputBridgeConnected(token);
    }

    public void openGamepadBridge(IBinder token, String name) {
        openRemoteInputBridge(token, name, 0, 0, 0);
    }

    public void closeInputBridge(IBinder token) {}
    public void clearInputBridge(IBinder token) {}
    public void sendTimestamp(IBinder token, long timestamp) {}
    public void sendKeyDown(IBinder token, int keyCode) { sendCmd("K 0 " + keyCode + "\n"); }
    public void sendKeyUp(IBinder token, int keyCode) { sendCmd("K 1 " + keyCode + "\n"); }
    public void sendPointerDown(IBinder token, int pointerId, int x, int y) {}
    public void sendPointerUp(IBinder token, int pointerId) {}
    public void sendPointerSync(IBinder token) {}
    public void sendGamepadKeyDown(IBinder token, int keyCode) { sendKeyDown(token, keyCode); }
    public void sendGamepadKeyUp(IBinder token, int keyCode) { sendKeyUp(token, keyCode); }
    public void sendGamepadAxisValue(IBinder token, int axis, float value) {}
}
