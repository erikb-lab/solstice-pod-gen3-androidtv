package com.podtv.hdmid;

import android.system.Os;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Root helper for the Pod's native HDMI output (HDMI OUT1, the HDMI 2.0 port, fb1 / HWC display 1).
 * Started by init (service podtv_hdmid).
 *
 *  - Drives TLMM GPIO 127 high. That is the port's enable line ("MER6641 HDMI OUT1 enable gpio"
 *    in stock Solstice init.qcom.post_boot.sh). Without it the port gets no HPD and no DDC/EDID.
 *  - Watches /sys/class/switch/hdmi and re-applies the saved mode after every hotplug.
 *  - Talks to the display HAL through `vndservice call display.qservice`
 *    (25 SET_ACTIVE_CONFIG, 26 GET_ACTIVE_CONFIG, 27 GET_CONFIG_COUNT, 28 GET_DISPLAY_ATTRIBUTES_FOR_CONFIG).
 *
 * Socket 127.0.0.1:6469, one request line per connection:
 *   "L"                 -> "CONN <0|1>", "NAME <monitor>", "PREF <auto|WxH@mHz>", "ACTIVE <idx>",
 *                          then "MODE <idx> <w> <h> <milliHz>" per config, then "END"
 *   "S <w> <h> <mHz>"   -> switch now, not saved                     -> "OK <idx>" | "ERR ..."
 *   "P auto" | "P <w> <h> <mHz>" -> save preference and apply       -> "OK" | "ERR ..."
 *   "F 0" | "F 1"       -> all-modes (synthetic EDID) off / forced on -> "OK"
 *   "L" also returns "FORCE <0|1>" and "EDID <real|none|synthetic>".
 * Only root and the uid that owns /data/data/com.podtv.hdmiout may connect (exact peer socket
 * looked up in /proc/net/tcp{,6}). Callers can only change display modes; nothing else.
 */
public class HdmiModeDaemon {
    static final int PORT = 6469;
    static final int DPY = 1; // HWC_DISPLAY_EXTERNAL
    static final String APP_PKG = "com.podtv.hdmiout";
    static final String PREF_DIR = "/data/misc/podtv-hdmi";
    static final String PREF_FILE = PREF_DIR + "/mode";
    static final String SWITCH = "/sys/class/switch/hdmi/state";
    static final String EDID = "/sys/class/graphics/fb1/edid_raw_data";
    static final String EDID_OVERRIDE = "/sys/class/graphics/fb1/edid"; // hex string >=128 bytes = custom EDID
    static final String HOT_PLUG = "/sys/class/graphics/fb1/hot_plug";
    static final String FORCE_FILE = PREF_DIR + "/force";

    static final Object LOCK = new Object();

    /** "real" = monitor's own EDID, "none" = monitor gave no/invalid EDID (synthetic in use), "synthetic" = forced. */
    static volatile String edidState = "real";
    static volatile boolean synthActive = false;
    /** Hotplug events before this time are our own hpd cycles and are ignored by the watcher. */
    static volatile long suppressUntil = 0;

    static void log(String s) { System.out.println("podtv_hdmid: " + s); }

    // ---------- GPIO ----------
    static void write(String path, String v) {
        try (FileOutputStream o = new FileOutputStream(path)) { o.write(v.getBytes()); }
        catch (Exception e) { /* already exported etc. */ }
    }
    static String read(String path) {
        try (BufferedReader r = new BufferedReader(new FileReader(path))) {
            String l = r.readLine(); return l == null ? "" : l.trim();
        } catch (Exception e) { return ""; }
    }
    static void enablePort() {
        if (!new File("/sys/class/gpio/gpio127").exists()) write("/sys/class/gpio/export", "127");
        write("/sys/class/gpio/gpio127/direction", "out");
        write("/sys/class/gpio/gpio127/value", "1");
        log("gpio127 (HDMI OUT1 enable) = " + read("/sys/class/gpio/gpio127/value"));
    }

    // ---------- qservice ----------
    static final Pattern WORD = Pattern.compile("(?<![x0-9a-fA-F])([0-9a-f]{8})(?![0-9a-fA-F:])");

    /** Runs vndservice call and returns the 32-bit words of the reply parcel (null on failure). */
    static int[] qs(int code, int... args) {
        List<String> cmd = new ArrayList<>();
        cmd.add("/vendor/bin/vndservice"); cmd.add("call"); cmd.add("display.qservice");
        cmd.add(Integer.toString(code));
        for (int a : args) { cmd.add("i32"); cmd.add(Integer.toString(a)); }
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String l;
                while ((l = r.readLine()) != null) sb.append(l.replaceAll("'[^']*'", " ")).append('\n');
            }
            p.waitFor();
            String out = sb.toString();
            if (!out.contains("Result: Parcel(")) {
                log("qservice " + code + " failed: " + out.trim());
                return null;
            }
            List<Integer> w = new ArrayList<>();
            Matcher m = WORD.matcher(out);
            while (m.find()) w.add((int) Long.parseLong(m.group(1), 16));
            int[] r = new int[w.size()];
            for (int i = 0; i < r.length; i++) r[i] = w.get(i);
            return r;
        } catch (Exception e) {
            log("qservice " + code + " error: " + e);
            return null;
        }
    }

    static class Mode {
        int idx, w, h, mhz;
        String key() { return w + "x" + h + "@" + mhz; }
    }

    static List<Mode> modes() {
        List<Mode> list = new ArrayList<>();
        int[] c = qs(27, DPY);
        if (c == null || c.length < 1) return list;
        for (int i = 0; i < c[0] && i < 64; i++) {
            int[] a = qs(28, i, DPY); // vsync_period_ns, xres, yres, ...
            if (a == null || a.length < 3 || a[0] <= 0 || a[1] <= 0 || a[2] <= 0) continue;
            Mode m = new Mode();
            m.idx = i; m.w = a[1]; m.h = a[2];
            m.mhz = (int) Math.round(1e12 / (double) (a[0] & 0xffffffffL));
            list.add(m);
        }
        return list;
    }

    static int active() {
        int[] a = qs(26, DPY);
        return (a == null || a.length < 1) ? -1 : a[0];
    }

    /** Closest config to w x h @ mhz (exact size, nearest refresh within 1 Hz), or null. */
    static Mode find(List<Mode> list, int w, int h, int mhz) {
        Mode best = null;
        for (Mode m : list) {
            if (m.w != w || m.h != h) continue;
            if (Math.abs(m.mhz - mhz) > 1000) continue;
            if (best == null || Math.abs(m.mhz - mhz) < Math.abs(best.mhz - mhz)) best = m;
        }
        return best;
    }

    static boolean setIdx(int idx) {
        int[] r = qs(25, idx, DPY);
        if (r == null) return false;
        return active() == idx;
    }

    // ---------- preference ----------
    static String pref() {
        String p = read(PREF_FILE);
        return p.matches("\\d+x\\d+@\\d+") ? p : "auto";
    }
    /** Settings dir and files are root-only (0700 / 0600). */
    static void saveSetting(String file, String v) {
        new File(PREF_DIR).mkdirs();
        try { Os.chmod(PREF_DIR, 0700); } catch (Exception ignored) {}
        write(file, v);
        try { Os.chmod(file, 0600); } catch (Exception ignored) {}
    }
    static void savePref(String p) { saveSetting(PREF_FILE, p + "\n"); }

    static boolean force() { return read(FORCE_FILE).equals("1"); }
    static void saveForce(boolean f) { saveSetting(FORCE_FILE, f ? "1\n" : "0\n"); }

    /**
     * Applies the saved preference. "auto" leaves HWC's own EDID-based best mode in place, except under
     * the synthetic all-modes EDID, where it picks 1080p60 (HWC's "best" there could be 4K the screen can't show).
     */
    static void applyPref() {
        String p = pref();
        if (p.equals("auto") && synthActive) p = "1920x1080@60000";
        if (p.equals("auto")) { log("preference auto, keeping HWC default"); return; }
        String[] a = p.split("[x@]");
        int w = Integer.parseInt(a[0]), h = Integer.parseInt(a[1]), mhz = Integer.parseInt(a[2]);
        for (int tries = 0; tries < 20; tries++) {
            List<Mode> list = modes();
            if (!list.isEmpty()) {
                Mode m = find(list, w, h, mhz);
                if (m == null) { log("saved mode " + p + " not offered by this display, keeping default"); return; }
                if (active() == m.idx || setIdx(m.idx)) { log("applied " + p + " (config " + m.idx + ")"); return; }
            }
            sleep(500);
        }
        log("could not apply " + p);
    }

    static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) {} }

    // ---------- synthetic "all modes" EDID ----------
    static int[] dtd(int pclk10k, int ha, int hb, int hfp, int hs, int va, int vb, int vfp, int vs, int flags) {
        return new int[] {
            pclk10k & 0xff, pclk10k >> 8,
            ha & 0xff, hb & 0xff, ((ha >> 8) << 4) | (hb >> 8),
            va & 0xff, vb & 0xff, ((va >> 8) << 4) | (vb >> 8),
            hfp & 0xff, hs & 0xff, ((vfp & 0xf) << 4) | (vs & 0xf),
            ((hfp >> 8) << 6) | ((hs >> 8) << 4) | ((vfp >> 4) << 2) | (vs >> 4),
            0x13, 0x2a, 0x21, 0, 0, flags }; // 531 x 298 mm
    }
    static void put(byte[] b, int off, int... v) { for (int i = 0; i < v.length; i++) b[off + i] = (byte) v[i]; }
    static void checksum(byte[] b, int blk) {
        int s = 0;
        for (int i = 0; i < 127; i++) s += b[blk * 128 + i] & 0xff;
        b[blk * 128 + 127] = (byte) ((256 - (s & 0xff)) & 0xff);
    }

    /**
     * EDID 1.4 + CEA-861 block advertising every mode the msm hdmi_tx parser understands, for
     * adapters/screens that give no EDID. Kept to <= 300 MHz TMDS (HDMI 1.4 sinks / cheap adapters).
     */
    static byte[] synthEdid() {
        byte[] b = new byte[256];
        put(b, 0, 0x00, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0x00,
                0x42, 0x96,             // "PTV"
                0x01, 0x00, 0, 0, 0, 0, // product, serial
                1, 0x22,                // week 1, 2024
                0x01, 0x04,             // EDID 1.4 (needed for the ET3 descriptor to be parsed)
                0x80, 0x35, 0x1e, 0x78, 0x06,
                0xee, 0x91, 0xa3, 0x54, 0x4c, 0x99, 0x26, 0x0f, 0x50, 0x54,
                0x21, 0x08, 0x00,       // established: 640x480@60, 800x600@60, 1024x768@60
                0x81, 0x80, 0x61, 0x40, // standard: 1280x1024@60, 1024x768@60
                1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1);
        put(b, 54, dtd(14850, 1920, 280, 88, 44, 1080, 45, 4, 5, 0x1e));   // 1080p60 (preferred)
        put(b, 72, dtd(8550, 1366, 426, 70, 143, 768, 30, 3, 3, 0x1e));    // 1366x768@60
        put(b, 90, 0, 0, 0, 0xf7, 0, 0x0a, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0, 0, 0, 0, 0, 0); // ET3: all
        put(b, 108, 0, 0, 0, 0xfc, 0);
        byte[] name = "All modes\n   ".getBytes();
        System.arraycopy(name, 0, b, 113, 13);
        b[126] = 1;
        checksum(b, 0);

        int[] svd = {0x90, 31, 34, 33, 32, 4, 19, 5, 20, 3, 2, 18, 17, 1, 95, 94, 93};
        int o = 128;
        put(b, o, 0x02, 0x03, 0, 0xc1); // CEA rev 3, DTD offset filled below, basic audio, 1 native DTD
        int p = o + 4;
        b[p++] = (byte) (0x40 | svd.length);
        for (int v : svd) b[p++] = (byte) v;
        put(b, p, 0x23, 0x09, 0x07, 0x07); p += 4;             // audio: LPCM 2ch 32/44.1/48 kHz
        put(b, p, 0x83, 0x01, 0x00, 0x00); p += 4;             // speakers: FL/FR
        put(b, p, 0x67, 0x03, 0x0c, 0x00, 0x10, 0x00, 0x00, 0x3c); p += 8; // HDMI VSDB, max TMDS 300 MHz
        b[o + 2] = (byte) (p - o);
        put(b, p, dtd(7100, 1280, 160, 48, 32, 800, 23, 3, 6, 0x1a)); p += 18;   // 1280x800 RB
        put(b, p, dtd(9775, 1600, 160, 48, 32, 900, 26, 3, 5, 0x1a)); p += 18;   // 1600x900 RB
        put(b, p, dtd(26850, 2560, 160, 48, 32, 1600, 46, 3, 6, 0x1a)); p += 18; // 2560x1600 RB
        checksum(b, 1);
        return b;
    }

    static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format("%02x", x & 0xff));
        return s.toString();
    }

    /** True when the connected display returned a valid EDID base block (header + checksum). */
    static boolean realEdidValid() {
        try (java.io.FileInputStream in = new java.io.FileInputStream(EDID)) {
            byte[] b = new byte[256];
            int n = in.read(b);
            if (n < 128) return false;
            int[] hdr = {0, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0};
            for (int i = 0; i < 8; i++) if ((b[i] & 0xff) != hdr[i]) return false;
            int s = 0;
            for (int i = 0; i < 128; i++) s += b[i] & 0xff;
            return (s & 0xff) == 0;
        } catch (Exception e) { return false; }
    }

    /**
     * Software replug through fb1/hot_plug: queues hdmi_tx's hpd work with DISCONNECT then CONNECT, and the
     * CONNECT pass re-reads sink info (DDC, or the custom EDID). Unlike fb1/hpd it leaves the 5V line alone.
     */
    static void cycleHpd() {
        suppressUntil = System.currentTimeMillis() + 15000;
        write(HOT_PLUG, "0");
        for (int i = 0; i < 12 && !read(SWITCH).equals("0"); i++) sleep(250);
        sleep(1000);
        write(HOT_PLUG, "1");
        for (int i = 0; i < 20 && !read(SWITCH).equals("1"); i++) sleep(250);
        sleep(1500); // let HWC finish ConnectDisplay
        suppressUntil = System.currentTimeMillis() + 1500;
    }

    static void useSynth(String state) {
        write(EDID_OVERRIDE, hex(synthEdid()));
        synthActive = true;
        edidState = state;
        log("synthetic all-modes EDID on (" + state + ")");
        if (read(SWITCH).equals("1")) cycleHpd();
    }

    static void clearSynth(boolean reread) {
        write(EDID_OVERRIDE, "0"); // < 128 bytes disables custom_edid
        synthActive = false;
        edidState = "real";
        log("synthetic EDID off");
        if (reread && read(SWITCH).equals("1")) cycleHpd();
    }

    /** Called (under LOCK) once a display is connected: picks real vs synthetic EDID, then applies the mode. */
    static void onConnected() {
        // after a daemon restart the kernel may still hold our EDID from before
        if (!synthActive && monitorName().equals("All modes")) synthActive = true;
        if (force()) {
            if (!synthActive) useSynth("synthetic");
            else edidState = "synthetic";
        } else if (synthActive) {
            // left over from a forced or EDID-less session; give the real EDID another chance
            clearSynth(true);
            if (!realEdidValid()) useSynth("none");
        } else if (!realEdidValid()) {
            log("display gave no valid EDID");
            useSynth("none");
        } else {
            edidState = "real";
        }
        applyPref();
    }

    // ---------- EDID monitor name ----------
    static String monitorName() {
        try (java.io.FileInputStream in = new java.io.FileInputStream(EDID)) {
            byte[] b = new byte[256];
            int n = in.read(b);
            if (n < 128 || (b[0] & 0xff) != 0 || (b[1] & 0xff) != 0xff) return "";
            for (int d = 54; d <= 108; d += 18) {
                if (b[d] == 0 && b[d + 1] == 0 && (b[d + 3] & 0xff) == 0xfc) {
                    StringBuilder s = new StringBuilder();
                    for (int i = d + 5; i < d + 18; i++) {
                        int c = b[i] & 0xff;
                        if (c == 0x0a) break;
                        if (c >= 0x20 && c < 0x7f) s.append((char) c);
                    }
                    return s.toString().trim();
                }
            }
        } catch (Exception ignored) {}
        return "";
    }

    // ---------- access control ----------
    static int appUid() {
        try { return Os.stat("/data/data/" + APP_PKG).st_uid; } catch (Exception e) { return -1; }
    }
    /**
     * uid owning the client end of a connection to 127.0.0.1:{@code serverPort}, or -1.
     * Matches the exact socket: local 127.0.0.1:{@code clientPort} (or ::ffff:127.0.0.1 in tcp6),
     * remote 127.0.0.1:{@code serverPort}, state ESTABLISHED, live inode, and exactly one match.
     * (Matching on ports alone could be fooled by TIME_WAIT / orphan entries, which report uid 0.)
     */
    static int peerUid(int clientPort, int serverPort) {
        String lp = String.format("%04X", clientPort), sp = String.format("%04X", serverPort);
        String[][] files = {{"/proc/net/tcp", "0100007F"}, {"/proc/net/tcp6", "0000000000000000FFFF00000100007F"}};
        int uid = -1, matches = 0;
        for (String[] f : files) {
            try (BufferedReader r = new BufferedReader(new FileReader(f[0]))) {
                String l;
                while ((l = r.readLine()) != null) {
                    String[] c = l.trim().split("\\s+");
                    if (c.length < 10) continue;
                    if (!c[1].equalsIgnoreCase(f[1] + ":" + lp)) continue;
                    if (!c[2].equalsIgnoreCase(f[1] + ":" + sp)) continue;
                    if (!c[3].equals("01") || c[9].equals("0")) continue;
                    matches++;
                    uid = Integer.parseInt(c[7]);
                }
            } catch (Exception ignored) {}
        }
        return matches == 1 ? uid : -1;
    }
    static boolean allowed(Socket s) {
        if (!s.getInetAddress().isLoopbackAddress()) return false;
        int uid = peerUid(s.getPort(), PORT);
        return uid == 0 || (uid >= 0 && uid == appUid());
    }

    /** One request line: at most 64 chars, 3 s total, so a slow or huge request cannot stall the loop. */
    static String readRequest(Socket s) throws java.io.IOException {
        java.io.InputStream in = s.getInputStream();
        long end = System.currentTimeMillis() + 3000;
        StringBuilder sb = new StringBuilder();
        while (true) {
            long left = end - System.currentTimeMillis();
            if (left <= 0) return null;
            s.setSoTimeout((int) left);
            int b = in.read();
            if (b < 0 || b == '\n') break;
            if (sb.length() >= 64) return null;
            sb.append((char) b);
        }
        return sb.toString();
    }

    // ---------- request handling ----------
    static String handle(String line) {
        String[] p = line.trim().split("\\s+");
        synchronized (LOCK) {
            if (p[0].equals("L")) {
                StringBuilder sb = new StringBuilder();
                boolean conn = read(SWITCH).equals("1");
                sb.append("CONN ").append(conn ? 1 : 0).append('\n');
                sb.append("NAME ").append(conn ? monitorName() : "").append('\n');
                sb.append("PREF ").append(pref()).append('\n');
                sb.append("FORCE ").append(force() ? 1 : 0).append('\n');
                sb.append("EDID ").append(conn ? edidState : "real").append('\n');
                if (conn) {
                    sb.append("ACTIVE ").append(active()).append('\n');
                    for (Mode m : modes()) {
                        sb.append("MODE ").append(m.idx).append(' ').append(m.w).append(' ')
                          .append(m.h).append(' ').append(m.mhz).append('\n');
                    }
                }
                return sb.append("END\n").toString();
            }
            if (p[0].equals("S") && p.length == 4) {
                Mode m = find(modes(), num(p[1]), num(p[2]), num(p[3]));
                if (m == null) return "ERR mode not offered\n";
                return setIdx(m.idx) ? "OK " + m.idx + "\n" : "ERR display HAL refused\n";
            }
            if (p[0].equals("P") && p.length == 2 && p[1].equals("auto")) {
                savePref("auto");
                return "OK\n";
            }
            if (p[0].equals("P") && p.length == 4) {
                int w = num(p[1]), h = num(p[2]), mhz = num(p[3]);
                if (w <= 0 || h <= 0 || mhz <= 0) return "ERR bad mode\n";
                savePref(w + "x" + h + "@" + mhz);
                Mode m = find(modes(), w, h, mhz);
                if (m != null && active() != m.idx) setIdx(m.idx);
                return "OK\n";
            }
            if (p[0].equals("F") && p.length == 2 && (p[1].equals("0") || p[1].equals("1"))) {
                boolean f = p[1].equals("1");
                saveForce(f);
                if (read(SWITCH).equals("1")) {
                    if (f && !synthActive) { useSynth("synthetic"); applyPref(); }
                    else if (f) edidState = "synthetic";
                    else if (synthActive) onConnected(); // drops synthetic, keeps it only if the screen has no EDID
                }
                return "OK\n";
            }
            return "ERR unknown command\n";
        }
    }
    static int num(String s) { try { return Integer.parseInt(s); } catch (Exception e) { return -1; } }

    public static void main(String[] args) throws Exception {
        enablePort();
        // Hotplug watcher: re-apply the saved mode whenever a display (re)connects.
        Thread watcher = new Thread(() -> {
            String last = "";
            while (true) {
                String st = read(SWITCH);
                if (System.currentTimeMillis() < suppressUntil) { // our own hpd cycle
                    last = st;
                    sleep(500);
                    continue;
                }
                if (st.equals("1") && !last.equals("1")) {
                    sleep(1500); // let HWC finish ConnectDisplay
                    synchronized (LOCK) { onConnected(); }
                    st = read(SWITCH);
                } else if (st.equals("0") && last.equals("1")) {
                    // unplugged: drop the synthetic EDID unless forced, so the next screen's own EDID is read
                    synchronized (LOCK) { if (synthActive && !force()) clearSynth(false); }
                }
                if (!st.isEmpty()) last = st;
                sleep(1000);
            }
        }, "hotplug");
        watcher.setDaemon(true);
        watcher.start();

        ServerSocket server = new ServerSocket(PORT, 16, InetAddress.getByName("127.0.0.1"));
        log("listening on 127.0.0.1:" + PORT);
        while (true) {
            try (Socket s = server.accept()) {
                if (!allowed(s)) { log("rejected connection from port " + s.getPort()); continue; }
                String line = readRequest(s);
                if (line == null) continue;
                s.setSoTimeout(30000);
                OutputStream o = s.getOutputStream();
                o.write(handle(line).getBytes("UTF-8"));
                o.flush();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }
}
