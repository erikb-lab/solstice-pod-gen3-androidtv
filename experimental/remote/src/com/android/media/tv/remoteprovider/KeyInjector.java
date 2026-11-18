package com.android.media.tv.remoteprovider;

import android.hardware.input.InputManager;
import android.os.SystemClock;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.system.Os;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

/**
 * Root helper started by init (tv_key_injector).
 *   "K <0|1> <keycode>"  inject a key down/up through InputManager
 *   "O <name> <pid>"     create a uhid keyboard named <name> via /system/bin/hid
 *
 * Only connections owned by the remote service's uid (or root) are accepted: the
 * owner of the peer socket is looked up in /proc/net/tcp{,6}.
 */
public class KeyInjector {
    // Keyboard report descriptor covering usages 0x00-0xFF (includes 0x66 Power).
    private static final String DESCRIPTOR =
            "[5,1,9,6,161,1,5,7,25,224,41,231,21,0,37,1,117,1,149,8,129,2,149,1,117,8,129,1,"
            + "149,6,117,8,21,0,38,255,0,5,7,25,0,41,255,129,0,192]";
    private static final Map<String, Process> sDevices = new HashMap<>();

    private static final Map<String, String> sOwner = new HashMap<>();

    /** Create the device once per caller process; recreate only for a new caller pid or a dead hid. */
    private static void openDevice(String name, String owner) {
        Process cur = sDevices.get(name);
        if (cur != null && cur.isAlive() && owner.equals(sOwner.get(name))) {
            return;
        }
        sOwner.put(name, owner);
        Process old = sDevices.remove(name);
        if (old != null) {
            old.destroy();
            try { old.waitFor(); } catch (InterruptedException ignored) {}
        }
        try {
            ProcessBuilder pb = new ProcessBuilder("/system/bin/hid", "-");
            pb.redirectErrorStream(true);
            pb.redirectOutput(new File("/dev/null"));
            Process p = pb.start();
            OutputStream in = p.getOutputStream();
            String json = "{\"id\":1,\"command\":\"register\",\"name\":\"" + name
                    + "\",\"vid\":6353,\"pid\":" + productId(name) + ",\"bus\":\"usb\",\"descriptor\":" + DESCRIPTOR + "}\n";
            in.write(json.getBytes("UTF-8"));
            in.flush();
            // Keep stdin open: hid keeps the device alive until EOF or kill.
            sDevices.put(name, p);
            System.out.println("KeyInjector: created uhid device " + name);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Distinct product id per name, otherwise InputReader merges same-descriptor devices into one. */
    private static int productId(String name) {
        if (name.equals("virtual-remote")) return 0x1234;
        return 0x2000 + (name.hashCode() & 0x0fff);
    }

    private static final String REMOTE_PKG = "com.google.android.tv.remote.service";

    /** uid of the installed remote service, or -1 if it is not installed. */
    private static int remoteUid() {
        try {
            return Os.stat("/data/data/" + REMOTE_PKG).st_uid;
        } catch (Exception e) {
            return -1;
        }
    }

    /** uid owning the local TCP socket bound to {@code port}, or -1 if not found. */
    private static int socketUid(int port) {
        String hex = String.format(":%04X ", port);
        for (String f : new String[] {"/proc/net/tcp6", "/proc/net/tcp"}) {
            try (BufferedReader r = new BufferedReader(new FileReader(f))) {
                String l;
                while ((l = r.readLine()) != null) {
                    String[] c = l.trim().split("\\s+");
                    if (c.length > 7 && (c[1] + " ").endsWith(hex) && c[2].endsWith(":1944")) {
                        return Integer.parseInt(c[7]);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return -1;
    }

    private static boolean allowed(Socket s) {
        int uid = socketUid(s.getPort());
        return uid == 0 || (uid >= 0 && uid == remoteUid());
    }

    public static void main(String[] args) throws Exception {
        Object im = InputManager.class.getDeclaredMethod("getInstance").invoke(null);
        Method inject = InputManager.class.getDeclaredMethod(
                "injectInputEvent", InputEvent.class, Integer.TYPE);
        ServerSocket server = new ServerSocket(6468, 50, InetAddress.getByName("127.0.0.1"));
        System.out.println("KeyInjector listening on 127.0.0.1:6468");
        while (true) {
            try {
                String line;
                try (Socket s = server.accept()) {
                    if (!allowed(s)) {
                        System.out.println("KeyInjector: rejected connection from port " + s.getPort());
                        continue;
                    }
                    line = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8")).readLine();
                }
                if (line == null) continue;
                String[] p = line.trim().split("\\s+");
                if (p[0].equals("K") && p.length >= 3) {
                    int action = Integer.parseInt(p[1]);
                    int code = Integer.parseInt(p[2]);
                    long now = SystemClock.uptimeMillis();
                    KeyEvent ev = new KeyEvent(now, now, action, code, 0, 0, -1, 0, 8, 0x301);
                    inject.invoke(im, ev, 0);
                } else if (p[0].equals("O") && p.length >= 2 && p[1].matches("[A-Za-z0-9._-]{1,64}")) {
                    openDevice(p[1], p.length >= 3 ? p[2] : "");
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }
}
