package com.podtv.hdmiout;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Resolution / refresh-rate picker for the Pod's native HDMI port (HDMI 2.0, HWC display 1). */
public class MainActivity extends Activity {
    static final int BG = 0xFF0E0E10, CARD = 0xFF1A1A1D, FOCUS = 0xFFFFFFFF, DIM = 0x99FFFFFF, ACCENT = 0xFF4C8DFF;
    static final int CONFIRM_SECONDS = 15;

    static class Mode {
        int w, h, mhz;
        boolean interlaced() { return w > h * 5 / 2; }
        String res() { return interlaced() ? w + " × " + (h * 2) + "i" : w + " × " + h; }
        String hz() {
            return (mhz % 1000 == 0) ? (mhz / 1000) + " Hz" : String.format(Locale.US, "%.2f Hz", mhz / 1000.0);
        }
        String label() { return res() + "  ·  " + hz(); }
        String key() { return w + "x" + h + "@" + mhz; }
    }

    static class State {
        boolean ok, connected, force;
        String name = "", pref = "auto", edid = "real"; // edid: real | none | synthetic
        int active = -1;
        Mode activeMode;
        List<Mode> modes = new ArrayList<>();
        String error;
    }

    final Handler ui = new Handler(Looper.getMainLooper());
    LinearLayout list;
    TextView title, subtitle;
    FrameLayout root;
    View confirm;
    State state = new State();
    Mode pendingRevert; // mode to return to if the user does not confirm
    Mode pendingNew;
    int countdown;
    boolean busy;

    // ---------------- daemon I/O ----------------
    static List<String> call(String cmd) throws Exception {
        List<String> out = new ArrayList<>();
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", 6469), 2000);
            s.setSoTimeout(15000);
            OutputStream o = s.getOutputStream();
            o.write((cmd + "\n").getBytes("UTF-8"));
            o.flush();
            BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));
            String l;
            while ((l = r.readLine()) != null) out.add(l);
        }
        return out;
    }

    static State load() {
        State st = new State();
        try {
            List<String> lines = call("L");
            List<int[]> raw = new ArrayList<>();
            for (String l : lines) {
                String[] p = l.split(" ", 2);
                String v = p.length > 1 ? p[1] : "";
                if (p[0].equals("CONN")) st.connected = v.equals("1");
                else if (p[0].equals("NAME")) st.name = v;
                else if (p[0].equals("PREF")) st.pref = v;
                else if (p[0].equals("FORCE")) st.force = v.equals("1");
                else if (p[0].equals("EDID")) st.edid = v.trim();
                else if (p[0].equals("ACTIVE")) st.active = Integer.parseInt(v.trim());
                else if (p[0].equals("MODE")) {
                    String[] a = v.trim().split("\\s+");
                    raw.add(new int[] {Integer.parseInt(a[0]), Integer.parseInt(a[1]),
                            Integer.parseInt(a[2]), Integer.parseInt(a[3])});
                }
            }
            st.ok = !lines.isEmpty();
            for (int[] a : raw) {
                Mode m = new Mode();
                m.w = a[1]; m.h = a[2]; m.mhz = a[3];
                boolean dup = false;
                for (Mode e : st.modes) if (e.key().equals(m.key())) dup = true;
                if (!dup) st.modes.add(m);
                if (a[0] == st.active && st.activeMode == null) st.activeMode = m;
            }
            Collections.sort(st.modes, new Comparator<Mode>() {
                @Override public int compare(Mode a, Mode b) {
                    int pa = a.w * (a.interlaced() ? a.h * 2 : a.h), pb = b.w * (b.interlaced() ? b.h * 2 : b.h);
                    if (pa != pb) return pb - pa;
                    if (a.interlaced() != b.interlaced()) return a.interlaced() ? 1 : -1;
                    return b.mhz - a.mhz;
                }
            });
        } catch (Exception e) {
            st.ok = false;
            st.error = "HDMI helper service is not running (podtv_hdmid).";
        }
        return st;
    }

    /**
     * What "Auto" means: HWC's own pick (largest progressive mode, highest refresh), except under the
     * all-modes list, where the helper uses 1080p60 because the list is not what the screen supports.
     */
    static Mode best(List<Mode> modes, String edid) {
        if (!edid.equals("real")) {
            for (Mode m : modes) if (m.w == 1920 && m.h == 1080 && Math.abs(m.mhz - 60000) < 1000) return m;
        }
        for (Mode m : modes) if (!m.interlaced()) return m;
        return modes.isEmpty() ? null : modes.get(0);
    }

    // ---------------- UI ----------------
    int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    static GradientDrawable pill(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(64), dp(40), dp(64), dp(24));

        title = text("HDMI Out", 30, FOCUS, true);
        subtitle = text("Reading display…", 15, DIM, false);
        subtitle.setPadding(0, dp(4), 0, dp(20));
        col.addView(title);
        col.addView(subtitle);

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        col.addView(sv, new LinearLayout.LayoutParams(dp(560), 0, 1f));

        root.addView(col);
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh(true);
        ui.postDelayed(poll, 3000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(poll);
        if (confirm != null) revert(); // never leave an unconfirmed mode behind
    }

    final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!busy && confirm == null) refresh(false);
            ui.postDelayed(this, 3000);
        }
    };

    void refresh(final boolean focusActive) {
        busy = true;
        new Thread(new Runnable() {
            @Override public void run() {
                final State st = load();
                ui.post(new Runnable() {
                    @Override public void run() {
                        busy = false;
                        boolean changed = !sameState(state, st);
                        state = st;
                        if (changed || focusActive) render(focusActive);
                    }
                });
            }
        }).start();
    }

    static boolean sameState(State a, State b) {
        if (a.ok != b.ok || a.connected != b.connected || a.active != b.active) return false;
        if (a.force != b.force || !a.edid.equals(b.edid)) return false;
        if (!a.pref.equals(b.pref) || !a.name.equals(b.name) || a.modes.size() != b.modes.size()) return false;
        return true;
    }

    void render(boolean focusActive) {
        View focused = getCurrentFocus();
        String focusKey = focused != null && focused.getTag() != null ? (String) focused.getTag() : null;
        list.removeAllViews();
        if (!state.ok) {
            subtitle.setText(state.error != null ? state.error : "No answer from the HDMI helper.");
            return;
        }
        if (!state.connected) {
            subtitle.setText("No display on the HDMI 2.0 port. Plug one in and it will show up here.");
            return;
        }
        String name;
        if (state.edid.equals("none")) name = "Screen sent no EDID";
        else if (state.edid.equals("synthetic")) name = "All modes (EDID ignored)";
        else name = state.name.isEmpty() ? "Display connected" : state.name;
        subtitle.setText(name + (state.activeMode != null ? "  ·  now " + state.activeMode.label() : ""));

        View toFocus = null;
        boolean real = state.edid.equals("real");
        Mode b = best(state.modes, state.edid);
        View auto = row("Auto", (real ? "Best mode the display reports" : "Safe default")
                + (b != null ? " (" + b.label() + ")" : ""), state.pref.equals("auto"), false, "auto");
        auto.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { chooseAuto(); }
        });
        list.addView(auto);
        if ("auto".equals(focusKey)) toFocus = auto;

        String allSub;
        if (state.force) allSub = "On: every mode is listed, even ones this screen may not show";
        else if (state.edid.equals("none")) allSub = "On automatically: this screen or adapter sent no EDID";
        else allSub = "Off: only modes the screen reports. Turn on for adapters that lie";
        final LinearLayout all = (LinearLayout) row("All modes", allSub, false, false, "force");
        boolean allOn = state.force || state.edid.equals("none");
        TextView sw = text(allOn ? "ON" : "OFF", 11, allOn ? ACCENT : DIM, true);
        sw.setPadding(dp(12), 0, 0, 0);
        all.addView(sw);
        all.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleAllModes(); }
        });
        list.addView(all);
        if ("force".equals(focusKey)) toFocus = all;

        String lastRes = null;
        for (final Mode m : state.modes) {
            if (!m.res().equals(lastRes)) {
                TextView h = text(m.res(), 13, DIM, true);
                h.setPadding(dp(20), dp(18), 0, dp(6));
                list.addView(h);
                lastRes = m.res();
            }
            boolean isActive = state.activeMode != null && state.activeMode.key().equals(m.key());
            View r = row(m.hz(), m.interlaced() ? "interlaced" : null,
                    state.pref.equals(m.key()), isActive, m.key());
            r.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { chooseMode(m); }
            });
            list.addView(r);
            if (m.key().equals(focusKey) || (focusKey == null && focusActive && isActive)) toFocus = r;
        }
        if (toFocus == null) toFocus = auto;
        toFocus.requestFocus();
    }

    View row(String main, String sub, boolean saved, boolean active, String tag) {
        final LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(20), dp(12), dp(20), dp(12));
        r.setFocusable(true);
        r.setDefaultFocusHighlightEnabled(false);
        r.setClickable(true);
        r.setTag(tag);
        r.setBackground(pill(CARD, dp(14)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        final TextView t1 = text(main, 18, FOCUS, true);
        texts.addView(t1);
        final TextView t2 = sub != null ? text(sub, 13, DIM, false) : null;
        if (t2 != null) texts.addView(t2);
        r.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (active) {
            TextView badge = text("ACTIVE", 11, ACCENT, true);
            badge.setPadding(dp(10), 0, 0, 0);
            r.addView(badge);
        }
        if (saved) {
            TextView badge = text("✓ SAVED", 11, 0xFF6FD08C, true);
            badge.setPadding(dp(12), 0, 0, 0);
            r.addView(badge);
        }
        r.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) {
                r.setBackground(pill(has ? 0xFF2C2C31 : CARD, dp(14)));
                r.animate().scaleX(has ? 1.02f : 1f).scaleY(has ? 1.02f : 1f).setDuration(120).start();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(3), 0, dp(3));
        r.setLayoutParams(lp);
        return r;
    }

    // ---------------- actions ----------------
    void send(final String cmd, final Runnable after) {
        busy = true;
        new Thread(new Runnable() {
            @Override public void run() {
                try { call(cmd); } catch (Exception ignored) {}
                ui.post(new Runnable() {
                    @Override public void run() {
                        busy = false;
                        if (after != null) after.run();
                    }
                });
            }
        }).start();
    }

    void toggleAllModes() {
        subtitle.setText("Re-reading the display…");
        send(state.force ? "F 0" : "F 1", new Runnable() {
            @Override public void run() { refresh(false); }
        });
    }

    void chooseAuto() {
        final Mode b = best(state.modes, state.edid);
        send("P auto", new Runnable() {
            @Override public void run() {
                if (b != null && (state.activeMode == null || !state.activeMode.key().equals(b.key()))) {
                    send("S " + b.w + " " + b.h + " " + b.mhz, new Runnable() {
                        @Override public void run() { refresh(false); }
                    });
                } else {
                    refresh(false);
                }
            }
        });
    }

    void chooseMode(final Mode m) {
        if (state.activeMode != null && state.activeMode.key().equals(m.key())) {
            send("P " + m.w + " " + m.h + " " + m.mhz, new Runnable() {
                @Override public void run() { refresh(false); }
            });
            return;
        }
        pendingRevert = state.activeMode;
        pendingNew = m;
        send("S " + m.w + " " + m.h + " " + m.mhz, new Runnable() {
            @Override public void run() { showConfirm(); }
        });
    }

    void showConfirm() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(32), dp(28), dp(32), dp(24));
        box.setBackground(pill(0xF21A1A1D, dp(22)));
        final TextView q = text("Keep " + pendingNew.label() + "?", 22, FOCUS, true);
        final TextView c = text("", 14, DIM, false);
        c.setPadding(0, dp(6), 0, dp(20));
        box.addView(q);
        box.addView(c);
        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        View keep = button("Keep");
        View back = button("Revert");
        btns.addView(keep);
        btns.addView(back);
        box.addView(btns);
        keep.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { keep(); }
        });
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { revert(); }
        });

        FrameLayout scrim = new FrameLayout(this);
        scrim.setBackgroundColor(0xB3000000);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(480), ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        scrim.addView(box, lp);
        keep.setId(View.generateViewId());
        back.setId(View.generateViewId());
        keep.setNextFocusRightId(back.getId());
        keep.setNextFocusLeftId(keep.getId());
        keep.setNextFocusUpId(keep.getId());
        keep.setNextFocusDownId(keep.getId());
        back.setNextFocusLeftId(keep.getId());
        back.setNextFocusRightId(back.getId());
        back.setNextFocusUpId(back.getId());
        back.setNextFocusDownId(back.getId());
        // the list behind the scrim must not take focus, or D-pad left/right lands on hidden rows
        list.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        confirm = scrim;
        root.addView(scrim);
        keep.requestFocus(); // the 15 s timer still reverts if the screen went blank

        countdown = CONFIRM_SECONDS;
        final Runnable tick = new Runnable() {
            @Override public void run() {
                if (confirm == null) return;
                if (countdown <= 0) { revert(); return; }
                c.setText("Going back in " + countdown + " s if you do nothing.");
                countdown--;
                ui.postDelayed(this, 1000);
            }
        };
        tick.run();
    }

    View button(String label) {
        final TextView t = text(label, 16, FOCUS, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(28), dp(10), dp(28), dp(10));
        t.setFocusable(true);
        t.setDefaultFocusHighlightEnabled(false);
        t.setClickable(true);
        t.setBackground(pill(0xFF2C2C31, dp(22)));
        t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) {
                t.setBackground(pill(has ? FOCUS : 0xFF2C2C31, dp(22)));
                t.setTextColor(has ? 0xFF000000 : FOCUS);
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(12), 0);
        t.setLayoutParams(lp);
        return t;
    }

    void closeConfirm() {
        if (confirm != null) root.removeView(confirm);
        confirm = null;
        list.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
    }

    void keep() {
        final Mode m = pendingNew;
        closeConfirm();
        send("P " + m.w + " " + m.h + " " + m.mhz, new Runnable() {
            @Override public void run() { refresh(false); }
        });
    }

    void revert() {
        final Mode m = pendingRevert;
        closeConfirm();
        if (m == null) { refresh(false); return; }
        send("S " + m.w + " " + m.h + " " + m.mhz, new Runnable() {
            @Override public void run() { refresh(false); }
        });
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && confirm != null) { revert(); return true; }
        return super.onKeyDown(keyCode, event);
    }
}
