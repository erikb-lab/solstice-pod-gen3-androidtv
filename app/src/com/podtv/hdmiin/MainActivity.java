package com.podtv.hdmiin;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.Arrays;

/**
 * Fullscreen passthrough of the pod's HDMI inputs. The two TC358840 HDMI-to-CSI bridges are
 * exposed by the camera HAL as camera ids, so this is a Camera2 preview stretched to the screen.
 * OK / D-pad left / right switch inputs; the last used input is remembered.
 */
public class MainActivity extends Activity implements TextureView.SurfaceTextureListener {
    private static final String TAG = "HdmiIn";
    private TextureView view;
    private TextView label;
    private CameraManager cm;
    private String[] ids = new String[0];
    private int idx;
    private CameraDevice cam;
    private CameraCaptureSession session;
    private HandlerThread thread;
    private Handler bg;
    private final Handler ui = new Handler();
    private SharedPreferences prefs;
    private AudioPassthrough audio;
    private volatile boolean frameSeen;
    private static final long NO_SIGNAL_MS = 6000;   // close to launcher if no frame arrives in this long
    private final Runnable noSignal = new Runnable() { public void run() {
        if (!frameSeen) {
            Log.i(TAG, "no HDMI frames within " + NO_SIGNAL_MS + "ms, closing");
            finish();
        }
    }};

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences("hdmiin", MODE_PRIVATE);
        idx = prefs.getInt("idx", 0);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        view = new TextureView(this);
        view.setSurfaceTextureListener(this);
        root.addView(view, new FrameLayout.LayoutParams(-1, -1));
        label = new TextView(this);
        label.setTextColor(Color.WHITE);
        label.setTextSize(28);
        label.setShadowLayer(6, 0, 0, Color.BLACK);
        label.setPadding(48, 32, 48, 32);
        root.addView(label, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START));
        setContentView(root);
        cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        audio = new AudioPassthrough(this);
    }

    private boolean granted(String p) { return checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED; }

    /** HDMI audio capture (camcorder source, as Mersive did) only ever returned silence on this unit; off. */
    private static final boolean AUDIO_ENABLED = false;

    private void startAudio() {
        if (!AUDIO_ENABLED || !granted(Manifest.permission.RECORD_AUDIO)) return;
        final String s = audio.start();
        Log.i(TAG, "audio: " + s);
        if (!s.equals("audio on")) show(s, true);
    }

    @Override protected void onResume() {
        super.onResume();
        hideUi();
        thread = new HandlerThread("cam");
        thread.start();
        bg = new Handler(thread.getLooper());
        if (!granted(Manifest.permission.CAMERA)) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 1);
            return;
        }
        if (view.isAvailable()) open();
    }

    @Override protected void onPause() {
        ui.removeCallbacks(noSignal);
        audio.stop();
        close();
        if (thread != null) { thread.quitSafely(); thread = null; }
        super.onPause();
    }

    @Override public void onRequestPermissionsResult(int rc, String[] p, int[] r) {
        if (granted(Manifest.permission.CAMERA)) { if (view.isAvailable()) open(); }
        else show("Camera permission is needed to show HDMI input", false);
    }

    private void hideUi() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    private void show(final String s, final boolean fade) {
        ui.post(new Runnable() { public void run() {
            label.setText(s);
            label.setAlpha(1f);
            label.animate().cancel();
            if (fade) label.animate().alpha(0f).setStartDelay(2500).setDuration(600).start();
        }});
    }

    private void open() {
        try {
            ids = cm.getCameraIdList();
        } catch (CameraAccessException e) {
            show("Camera service error: " + e.getMessage(), false);
            return;
        }
        if (ids.length == 0) { show("No HDMI inputs found", false); return; }
        if (idx >= ids.length) idx = 0;
        final String id = ids[idx];
        Size size = pickSize(id);
        Log.i(TAG, "inputs " + Arrays.toString(ids) + " opening " + id + " at " + size);
        SurfaceTexture st = view.getSurfaceTexture();
        st.setDefaultBufferSize(size.getWidth(), size.getHeight());
        final Surface surface = new Surface(st);
        show("HDMI " + (idx + 1) + " of " + ids.length + "  (" + size.getWidth() + "x" + size.getHeight() + ")", true);
        try {
            cm.openCamera(id, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice d) {
                    cam = d;
                    try {
                        d.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                            @Override public void onConfigured(CameraCaptureSession s) {
                                if (cam == null) return;
                                session = s;
                                try {
                                    CaptureRequest.Builder rb = cam.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                                    rb.addTarget(surface);
                                    s.setRepeatingRequest(rb.build(), null, bg);
                                    startAudio();   // I2S audio flows once the bridge is streaming
                                    frameSeen = false;
                                    ui.removeCallbacks(noSignal);
                                    ui.postDelayed(noSignal, NO_SIGNAL_MS);
                                } catch (Exception e) {
                                    show("Could not start HDMI " + (idx + 1) + ": " + e.getMessage(), false);
                                }
                            }
                            @Override public void onConfigureFailed(CameraCaptureSession s) {
                                show("HDMI " + (idx + 1) + ": no signal / unsupported mode", false);
                                ui.postDelayed(noSignal, 1500);
                            }
                        }, bg);
                    } catch (CameraAccessException e) {
                        show("HDMI " + (idx + 1) + " error: " + e.getMessage(), false);
                    }
                }
                @Override public void onDisconnected(CameraDevice d) { d.close(); cam = null; }
                @Override public void onError(CameraDevice d, int err) {
                    d.close(); cam = null;
                    show("HDMI " + (idx + 1) + " error " + err + " (nothing plugged in?)", false);
                    ui.postDelayed(noSignal, 1500);
                }
            }, bg);
        } catch (Exception e) {
            show("Could not open HDMI " + (idx + 1) + ": " + e.getMessage(), false);
        }
    }

    private Size pickSize(String id) {
        try {
            CameraCharacteristics c = cm.getCameraCharacteristics(id);
            StreamConfigurationMap m = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] sizes = m.getOutputSizes(SurfaceTexture.class);
            Log.i(TAG, "camera " + id + " sizes " + Arrays.toString(sizes));
            Size best = null;
            for (Size s : sizes) {
                if (s.getWidth() == 1920 && s.getHeight() == 1080) return s;
                if (s.getWidth() <= 1920 && s.getHeight() <= 1080
                        && (best == null || s.getWidth() * s.getHeight() > best.getWidth() * best.getHeight())) best = s;
            }
            if (best != null) return best;
            if (sizes.length > 0) return sizes[0];
        } catch (Exception e) {
            Log.w(TAG, "size lookup failed", e);
        }
        return new Size(1920, 1080);
    }

    private void close() {
        if (session != null) { try { session.close(); } catch (Exception ignored) {} session = null; }
        if (cam != null) { cam.close(); cam = null; }
    }

    private void switchInput(int delta) {
        if (ids.length < 2) { show("Only one HDMI input", true); return; }
        idx = (idx + delta + ids.length) % ids.length;
        prefs.edit().putInt("idx", idx).apply();
        close();
        open();
    }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_RIGHT: case KeyEvent.KEYCODE_CHANNEL_UP:
                switchInput(1); return true;
            case KeyEvent.KEYCODE_DPAD_LEFT: case KeyEvent.KEYCODE_CHANNEL_DOWN:
                switchInput(-1); return true;
        }
        return super.onKeyDown(code, e);
    }

    @Override public void onWindowFocusChanged(boolean f) { if (f) hideUi(); }
    @Override public void onSurfaceTextureAvailable(SurfaceTexture s, int w, int h) {
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) open();
    }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture s, int w, int h) {}
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture s) { close(); return true; }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture s) {
        if (!frameSeen) { frameSeen = true; ui.removeCallbacks(noSignal); }   // live signal: cancel auto-close
    }
}
