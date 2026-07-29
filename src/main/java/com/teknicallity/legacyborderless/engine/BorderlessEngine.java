package com.teknicallity.legacyborderless.engine;

import org.lwjgl.opengl.Display;

import java.awt.Rectangle;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

/**
 * Central borderless state machine. Has no Minecraft/Forge dependencies on purpose: it is loaded very early
 * (the coremod-injected call into {@code Minecraft.toggleFullscreen} references it) and must stay safe to
 * class-load before Forge is ready. It only touches LWJGL, AWT and reflection.
 */
public final class BorderlessEngine {

    /** Debug: set {@code -Dlegacyborderless.debugAutoCycle=true} to auto-toggle borderless on/off during a run. */
    private static final boolean DEBUG_AUTO_CYCLE = Boolean.getBoolean("legacyborderless.debugAutoCycle");

    /** Debug: set {@code -Dlegacyborderless.debug=true} (or the auto-cycle flag) to dump startup diagnostics. */
    private static final boolean DEBUG = DEBUG_AUTO_CYCLE || Boolean.getBoolean("legacyborderless.debug");

    /**
     * Extra pixels added to the covered area so the window does NOT exactly match the monitor. Windows 10/11
     * otherwise promotes an exactly-monitor-sized borderless window to fullscreen-optimization / DWM independent
     * flip, which black-flashes on alt-tab and behaves like exclusive fullscreen. Overriding via
     * {@code -Dlegacyborderless.overscan=N} (0 = exact cover) is possible for experimentation.
     */
    private static final int OVERSCAN = Integer.getInteger("legacyborderless.overscan", 1);

    private static boolean borderless;
    private static boolean pendingApply;

    /** Where the borderless on/off state is persisted between launches (set by the client proxy at pre-init). */
    private static File configFile;

    private static int savedX = 100;
    private static int savedY = 100;
    private static int savedW = 854;
    private static int savedH = 480;

    private static boolean diagnosticsDumped;
    private static boolean lastFullscreenState;
    private static long tickCount;

    private BorderlessEngine() {
    }

    public static synchronized boolean isBorderless() {
        return borderless;
    }

    /**
     * Wires up state persistence and schedules restoration of the last-used borderless state. Called once by the
     * client proxy at pre-init with the mod's config file. If the saved state was borderless, it is applied on the
     * first client tick (once the window exists).
     */
    public static synchronized void initPersistence(File file) {
        configFile = file;
        boolean saved = readSaved();
        pendingApply = saved;
        Log.info("Persistence: configFile=" + file + " savedBorderless=" + saved
                + " (will " + (saved ? "restore" : "stay windowed") + " on start).");
    }

    private static boolean readSaved() {
        if (configFile == null || !configFile.isFile()) {
            return false;
        }
        InputStream in = null;
        try {
            in = new FileInputStream(configFile);
            Properties props = new Properties();
            props.load(in);
            return Boolean.parseBoolean(props.getProperty("borderless", "false"));
        } catch (Throwable t) {
            Log.warn("Could not read borderless state from " + configFile, t);
            return false;
        } finally {
            closeQuietly(in);
        }
    }

    private static void save() {
        if (configFile == null) {
            return;
        }
        OutputStream out = null;
        try {
            File dir = configFile.getParentFile();
            if (dir != null && !dir.exists()) {
                dir.mkdirs();
            }
            Properties props = new Properties();
            props.setProperty("borderless", Boolean.toString(borderless));
            out = new FileOutputStream(configFile);
            props.store(out, "Legacy Borderless Window - remembers whether the window was borderless");
        } catch (Throwable t) {
            Log.warn("Could not write borderless state to " + configFile, t);
        } finally {
            closeQuietly(out);
        }
    }

    private static void closeQuietly(java.io.Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Throwable ignored) {
                // no-op
            }
        }
    }

    /** Invoked by the bytecode the coremod injects in place of {@code Display.setFullscreen(boolean)}. */
    public static synchronized void setFullscreenFromGame(boolean fullscreen) {
        Log.info("setFullscreenFromGame(" + fullscreen + ") created=" + safeIsCreated());
        try {
            if (!Display.isCreated()) {
                pendingApply = fullscreen;
                return;
            }
            if (fullscreen) {
                enable();
            } else {
                disable();
            }
        } catch (Throwable t) {
            Log.warn("setFullscreenFromGame(" + fullscreen + ") failed.", t);
        }
    }

    /** Dedicated key binding: flip the borderless state. */
    public static synchronized void toggle() {
        Log.info("toggle() borderless=" + borderless + " created=" + safeIsCreated());
        try {
            if (!Display.isCreated()) {
                return;
            }
            if (borderless) {
                disable();
            } else {
                enable();
            }
        } catch (Throwable t) {
            Log.warn("toggle() failed.", t);
        }
    }

    /** Runs every client tick. */
    public static synchronized void onClientTick() {
        try {
            if (!Display.isCreated()) {
                return;
            }
            tickCount++;

            if (DEBUG && !diagnosticsDumped) {
                diagnosticsDumped = true;
                dumpDiagnostics();
            }

            boolean fs = Display.isFullscreen();
            if (fs != lastFullscreenState) {
                lastFullscreenState = fs;
                Log.info("Display.isFullscreen() changed to " + fs
                        + " (if true, exclusive fullscreen is being engaged by something we did not redirect).");
            }

            if (pendingApply) {
                pendingApply = false;
                Log.info("Applying deferred fullscreen request now that the window exists.");
                enable();
                return;
            }

            if (DEBUG_AUTO_CYCLE) {
                if (tickCount == 60) {
                    Log.info("[debug] auto-cycle: enabling borderless");
                    enable();
                } else if (tickCount == 140) {
                    Log.info("[debug] auto-cycle: disabling borderless");
                    disable();
                }
            }
        } catch (Throwable t) {
            Log.warn("onClientTick() failed.", t);
        }
    }

    private static void dumpDiagnostics() {
        Log.info("=== diagnostics ===");
        Log.info("supported=" + Lwjgl2Window.isSupported());
        Log.info("hwnd=0x" + Long.toHexString(Lwjgl2Window.currentHwnd()));
        Log.info("Display pos/size = " + Display.getX() + "," + Display.getY() + " "
                + Display.getWidth() + "x" + Display.getHeight());
        Log.info("Display.isFullscreen()=" + Display.isFullscreen());
        try {
            Log.info("desktopDisplayMode=" + Display.getDesktopDisplayMode());
        } catch (Throwable t) {
            Log.warn("desktopDisplayMode read failed", t);
        }
        Rectangle m = MonitorLocator.monitorBoundsFor(
                Display.getX(), Display.getY(), Display.getWidth(), Display.getHeight());
        Log.info("computed monitor bounds = " + m.x + "," + m.y + " " + m.width + "x" + m.height);
        Log.info("=== end diagnostics ===");
    }

    private static boolean safeIsCreated() {
        try {
            return Display.isCreated();
        } catch (Throwable t) {
            return false;
        }
    }

    private static void enable() {
        if (borderless) {
            return;
        }
        if (!Lwjgl2Window.isSupported()) {
            Log.warn("Borderless requested but unsupported on this platform; leaving the window as-is.");
            return;
        }
        // Remember the exact OUTER window rectangle so we can restore it precisely. Using getWindowRect (not
        // Display.getWidth/Height, which are the client size) avoids the window shrinking a little each cycle.
        int[] rect = Lwjgl2Window.windowRect();
        if (rect != null) {
            savedX = rect[0];
            savedY = rect[1];
            savedW = rect[2];
            savedH = rect[3];
        } else {
            savedX = Display.getX();
            savedY = Display.getY();
            savedW = Display.getWidth();
            savedH = Display.getHeight();
        }
        Log.info("saved windowed rect = " + savedX + "," + savedY + " " + savedW + "x" + savedH);

        Rectangle monitor = MonitorLocator.monitorBoundsFor(
                Display.getX(), Display.getY(), Display.getWidth(), Display.getHeight());
        // Extend past the monitor (default 1px) so Windows doesn't treat it as exclusive fullscreen.
        int width = monitor.width + OVERSCAN;
        int height = monitor.height + OVERSCAN;
        Log.info("enable(): monitor " + monitor.x + "," + monitor.y + " " + monitor.width + "x" + monitor.height
                + " -> covering " + width + "x" + height + " (overscan=" + OVERSCAN + ")");
        Lwjgl2Window.makeBorderless(monitor.x, monitor.y, width, height);
        borderless = true;
        save();
    }

    private static void disable() {
        if (!borderless) {
            return;
        }
        Log.info("disable(): restoring " + savedX + "," + savedY + " " + savedW + "x" + savedH);
        Lwjgl2Window.makeWindowed(savedX, savedY, savedW, savedH);
        borderless = false;
        save();
    }
}
