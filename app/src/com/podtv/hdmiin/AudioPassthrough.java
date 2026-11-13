package com.podtv.hdmiin;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.util.Log;

/**
 * Pipes HDMI-in audio to the normal output. On this pod the TC358840's I2S audio lands on
 * TERT_MI2S_TX, which Mersive wired up as Qualcomm's FM path: the audio HAL's "capture-fm"
 * mixer path, selected by recording from the AUDIO_DEVICE_IN_FM_TUNER input device.
 */
class AudioPassthrough {
    private static final String TAG = "HdmiIn";
    private static final int RATE = 44100;      // playback at the capture rate: no resampling in the app
    private static final int REC_RATE = 44100;
    private final Context ctx;
    private volatile boolean running;
    private Thread thread;

    AudioPassthrough(Context ctx) { this.ctx = ctx; }

    /** Returns a short status string for the on-screen label. */
    String start() {
        if (running) return "audio on";
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        AudioDeviceInfo fm = null;
        for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_INPUTS)) {
            Log.i(TAG, "input device type " + d.getType() + " " + d.getProductName());
            if (d.getType() == AudioDeviceInfo.TYPE_FM_TUNER) fm = d;
        }

        final int inCh = AudioFormat.CHANNEL_IN_STEREO, outCh = AudioFormat.CHANNEL_OUT_STEREO;
        final int enc = AudioFormat.ENCODING_PCM_16BIT;
        int inMin = AudioRecord.getMinBufferSize(RATE, inCh, enc);
        int outMin = AudioTrack.getMinBufferSize(RATE, outCh, enc);
        final AudioRecord rec;
        final AudioTrack trk;
        try {
            // Same as Mersive's HdmiInputAudio: CAMCORDER source, 44.1 kHz stereo 16-bit, no preferred
            // device. Their audio HAL maps the camcorder source to the HDMI-in I2S port.
            int recMin = AudioRecord.getMinBufferSize(REC_RATE, inCh, enc);
            rec = new AudioRecord(MediaRecorder.AudioSource.CAMCORDER, REC_RATE, inCh, enc, Math.max(recMin, 3528) * 2);
            trk = new AudioTrack(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build(),
                    new AudioFormat.Builder().setSampleRate(RATE).setEncoding(enc).setChannelMask(outCh).build(),
                    Math.max(outMin, 3840) * 2, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE);
        } catch (Exception e) {
            Log.e(TAG, "audio setup failed", e);
            return "audio setup failed: " + e.getMessage();
        }
        if (rec.getState() != AudioRecord.STATE_INITIALIZED || trk.getState() != AudioTrack.STATE_INITIALIZED) {
            rec.release(); trk.release();
            return "audio device busy / not initialised";
        }
        running = true;
        thread = new Thread(new Runnable() { public void run() {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
            byte[] buf = new byte[1764];            // 10 ms of 44.1 kHz stereo 16-bit
            try {
                rec.startRecording();
                AudioDeviceInfo routed = rec.getRoutedDevice();
                Log.i(TAG, "recording from " + (routed == null ? "?" : routed.getType() + " " + routed.getProductName()));
                trk.play();
                long peakLog = 0;
                while (running) {
                    int n = rec.read(buf, 0, buf.length);
                    if (n <= 0) { Log.w(TAG, "read " + n); continue; }
                    trk.write(buf, 0, n);
                    long now = System.currentTimeMillis();
                    if (now - peakLog > 5000) {
                        peakLog = now;
                        int peak = 0;
                        for (int i = 0; i + 1 < n; i += 2) peak = Math.max(peak, Math.abs((short) ((buf[i] & 0xff) | (buf[i + 1] << 8))));
                        Log.i(TAG, "audio peak " + peak);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "audio loop died", e);
            } finally {
                try { rec.stop(); } catch (Exception ignored) {}
                try { trk.stop(); } catch (Exception ignored) {}
                rec.release(); trk.release();
            }
        }}, "hdmi-audio");
        thread.start();
        return "audio on";
    }

    void stop() {
        running = false;
        if (thread != null) {
            try { thread.join(500); } catch (InterruptedException ignored) {}
            thread = null;
        }
    }
}
