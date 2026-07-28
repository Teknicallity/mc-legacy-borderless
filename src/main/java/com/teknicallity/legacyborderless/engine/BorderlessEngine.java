package com.teknicallity.legacyborderless.engine;

import org.lwjgl.opengl.Display;

import java.awt.Rectangle;

/**
 * Central borderless state machine. Has no Minecraft/Forge dependencies on purpose: it is loaded very early
 * (the coremod-injected call into {@code Minecraft.toggleFullscreen} references it) and must stay safe to
 * class-load before Forge is ready. It only touches LWJGL, AWT and reflection.
 */
public final class BorderlessEngine {

    /** Debug: set {@code -Dlegacyborderless.debugAutoCycle=true} to auto-toggle borderless on/off during a run. */
    private static final boolean DEBUG_AUTO_CYCLE = Boolean.getBoolean("legacyborderless.debugAutoCycle");

    /**
     * Extra pixels added to the covered area so the window does NOT exactly match the monitor. Windows 10/11
     * otherwise promotes an exactly-monitor-sized borderless window to fullscreen-optimization / DWM independent
     * flip, which black-flashes on alt-tab and behaves like exclusive fullscreen. Overriding via
     * {@code -Dlegacyborderless.overscan=N} (0 = exact cover) is possible for experimentation.
     */
    private static final int OVERSCAN = Integer.getInteger("legacyborderless.overscan", 1);

    private static boolean borderless;
    private static boolean pendingApply;

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

            if (!diagnosticsDumped) {
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
    }

    private static void disable() {
        if (!borderless) {
            return;
        }
        Log.info("disable(): restoring " + savedX + "," + savedY + " " + savedW + "x" + savedH);
        Lwjgl2Window.makeWindowed(savedX, savedY, savedW, savedH);
        borderless = false;
    }
}
