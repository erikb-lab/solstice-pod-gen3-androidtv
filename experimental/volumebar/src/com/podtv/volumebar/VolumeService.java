package com.podtv.volumebar;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.media.AudioManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/** Draws a small volume bar on screen whenever the music volume changes. Replaces SystemUI's dialog. */
public class VolumeService extends Service {
    private static final String ACTION_VOLUME = "android.media.VOLUME_CHANGED_ACTION";
    private static final String ACTION_MUTE = "android.media.STREAM_MUTE_CHANGED_ACTION";
    private static final long HIDE_MS = 2500;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private AudioManager mAudio;
    private WindowManager mWm;
    private VolumeView mPanel;
    private boolean mShown;

    private final Runnable mHide = new Runnable() {
        @Override public void run() {
            if (!mShown) return;
            mPanel.animate().alpha(0f).translationX(24f).setDuration(220).withEndAction(new Runnable() {
                @Override public void run() {
                    if (mShown && mPanel.getAlpha() == 0f) { mWm.removeView(mPanel); mShown = false; }
                }
            }).start();
        }
    };

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            int stream = i.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", AudioManager.STREAM_MUSIC);
            if (stream != AudioManager.STREAM_MUSIC) return;
            show();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("vol", "Volume bar", NotificationManager.IMPORTANCE_MIN));
        startForeground(1, new Notification.Builder(this, "vol")
                .setContentTitle("Volume bar").setSmallIcon(android.R.drawable.ic_lock_silent_mode_off).build());
        mAudio = getSystemService(AudioManager.class);
        mWm = getSystemService(WindowManager.class);
        buildPanel();
        IntentFilter f = new IntentFilter(ACTION_VOLUME);
        f.addAction(ACTION_MUTE);
        registerReceiver(mReceiver, f);
    }

    private void buildPanel() {
        mPanel = new VolumeView(this);
        mPanel.setAlpha(0f);
    }

    private void show() {
        int max = mAudio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int cur = mAudio.getStreamVolume(AudioManager.STREAM_MUSIC);
        boolean muted = mAudio.isStreamMute(AudioManager.STREAM_MUSIC);
        mPanel.set(cur, max, muted, mShown);
        if (!mShown) {
            if (!Settings.canDrawOverlays(this)) return;
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
            lp.x = 56;
            lp.setTitle("VolumeBar");
            mWm.addView(mPanel, lp);
            mShown = true;
        }
        mPanel.animate().cancel();
        mPanel.setTranslationX(mPanel.getAlpha() < 1f ? 24f : 0f);
        mPanel.animate().alpha(1f).translationX(0f).setDuration(150).start();
        mHandler.removeCallbacks(mHide);
        mHandler.postDelayed(mHide, HIDE_MS);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }

    @Override
    public void onDestroy() {
        unregisterReceiver(mReceiver);
        mHandler.removeCallbacks(mHide);
        if (mShown) { mWm.removeView(mPanel); mShown = false; }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
