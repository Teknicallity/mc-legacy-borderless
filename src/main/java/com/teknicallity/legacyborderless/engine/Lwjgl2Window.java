package com.teknicallity.legacyborderless.engine;

import org.lwjgl.opengl.Display;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

/**
 * Thin reflection bridge to LWJGL 2's Windows implementation of {@link Display}.
 * <p>
 * LWJGL 2 does not expose its native Win32 helpers publicly, but {@code org.lwjgl.opengl.WindowsDisplay}
 * carries the whole toolkit we need to turn the existing game window into a borderless one <em>without</em>
 * destroying/recreating it (which would drop the GL context and every texture):
 * <ul>
 *     <li>the window handle ({@code hwnd}),</li>
 *     <li>{@code setWindowLongPtr} — Win32 {@code SetWindowLongPtr}, to swap the window style,</li>
 *     <li>{@code setWindowPos} — Win32 {@code SetWindowPos}, to move/resize/restack it.</li>
 * </ul>
 * Because we only restyle and reposition the live window, the OpenGL context and LWJGL input keep working,
 * and Minecraft picks up the new size through its normal {@code Display.wasResized()} -> {@code resize()} path.
 */
public final class Lwjgl2Window {

    // --- Win32 constants ---
    private static final int GWL_STYLE = -16;
    private static final int WS_POPUP = 0x80000000;
    private static final int WS_VISIBLE = 0x10000000;
    private static final int WS_OVERLAPPEDWINDOW = 0x00CF0000;

    private static final long HWND_TOP = 0L;
    private static final long HWND_NOTOPMOST = -2L;

    private static final int SWP_NOZORDER = 0x0004;
    private static final int SWP_FRAMECHANGED = 0x0020;
    private static final int SWP_SHOWWINDOW = 0x0040;

    // --- reflected handles (resolved once) ---
    private static boolean resolved;
    private static boolean supported;
    private static Object displayImpl;          // org.lwjgl.opengl.WindowsDisplay instance
    private static Method getHwndMethod;         // WindowsDisplay#getHwnd() : long
    private static Field hwndField;              // WindowsDisplay#hwnd : long (fallback)
    private static Method setWindowLongPtrMethod; // static (long, int, long) : long
    private static Method setWindowPosMethod;     // static (long, long, int, int, int, int, long) : boolean
    private static Method getWindowRectMethod;    // instance (long, IntBuffer) : boolean  (Win32 GetWindowRect)
    private static IntBuffer rectBuffer;          // direct buffer for the RECT {left, top, right, bottom}

    private Lwjgl2Window() {
    }

    /** @return {@code true} if the running LWJGL is the Windows implementation and reflection succeeded. */
    public static synchronized boolean isSupported() {
        resolve();
        return supported;
    }

    private static void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        try {
            Method getImplementation = Display.class.getDeclaredMethod("getImplementation");
            getImplementation.setAccessible(true);
            Object impl = getImplementation.invoke(null);
            Log.info("Display implementation is: " + (impl == null ? "null" : impl.getClass().getName()));
            if (impl == null || !impl.getClass().getName().equals("org.lwjgl.opengl.WindowsDisplay")) {
                Log.info("Not the Windows LWJGL display; borderless is unavailable on this platform.");
                return;
            }
            displayImpl = impl;
            Class<?> windowsDisplay = impl.getClass();

            try {
                getHwndMethod = windowsDisplay.getDeclaredMethod("getHwnd");
                getHwndMethod.setAccessible(true);
            } catch (NoSuchMethodException ignored) {
                Log.info("getHwnd() not found; falling back to the hwnd field.");
                hwndField = windowsDisplay.getDeclaredField("hwnd");
                hwndField.setAccessible(true);
            }

            setWindowLongPtrMethod = windowsDisplay.getDeclaredMethod(
                    "setWindowLongPtr", long.class, int.class, long.class);
            setWindowLongPtrMethod.setAccessible(true);

            setWindowPosMethod = windowsDisplay.getDeclaredMethod(
                    "setWindowPos", long.class, long.class, int.class, int.class, int.class, int.class, long.class);
            setWindowPosMethod.setAccessible(true);

            try {
                getWindowRectMethod = windowsDisplay.getDeclaredMethod("getWindowRect", long.class, IntBuffer.class);
                getWindowRectMethod.setAccessible(true);
                rectBuffer = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder()).asIntBuffer();
            } catch (NoSuchMethodException e) {
                Log.info("getWindowRect() not available; will restore windows from client size (may drift).");
            }

            supported = true;
            Log.info("LWJGL2 Windows display bridge ready (hwnd via " + (getHwndMethod != null ? "getHwnd()" : "field")
                    + ").");
        } catch (Throwable t) {
            supported = false;
            Log.warn("Could not wire up the LWJGL2 Windows display bridge; borderless is unavailable.", t);
        }
    }

    public static synchronized long currentHwnd() {
        try {
            return hwnd();
        } catch (Throwable t) {
            return 0L;
        }
    }

    private static long hwnd() throws Exception {
        if (getHwndMethod != null) {
            return (Long) getHwndMethod.invoke(displayImpl);
        }
        return hwndField.getLong(displayImpl);
    }

    /**
     * The current OUTER window rectangle (including title bar and borders) via Win32 {@code GetWindowRect}.
     *
     * @return {@code {x, y, width, height}} in virtual-desktop pixels, or {@code null} if unavailable
     */
    public static synchronized int[] windowRect() {
        if (!isSupported() || getWindowRectMethod == null) {
            return null;
        }
        try {
            long hwnd = hwnd();
            Object ok = getWindowRectMethod.invoke(displayImpl, hwnd, rectBuffer);
            if (Boolean.FALSE.equals(ok)) {
                return null;
            }
            int left = rectBuffer.get(0);
            int top = rectBuffer.get(1);
            int right = rectBuffer.get(2);
            int bottom = rectBuffer.get(3);
            return new int[]{left, top, right - left, bottom - top};
        } catch (Throwable t) {
            Log.warn("getWindowRect failed.", t);
            return null;
        }
    }

    /**
     * Strips the window's border/title bar and grows it to cover the given monitor rectangle.
     * Coordinates are virtual-desktop pixels (the same space {@link Display#getX()} reports).
     */
    public static synchronized void makeBorderless(int x, int y, int width, int height) {
        if (!isSupported()) {
            return;
        }
        try {
            long hwnd = hwnd();
            // WS_POPUP | WS_VISIBLE, masked to an unsigned 32-bit value so the sign bit of WS_POPUP
            // (0x80000000) is not sign-extended into the high half of the LONG_PTR.
            long style = ((long) (WS_POPUP | WS_VISIBLE)) & 0xFFFFFFFFL;
            Object prev = setWindowLongPtrMethod.invoke(null, hwnd, GWL_STYLE, style);
            Object ok = setWindowPosMethod.invoke(null, hwnd, HWND_TOP, x, y, width, height,
                    (long) (SWP_FRAMECHANGED | SWP_SHOWWINDOW));
            Log.info("makeBorderless hwnd=0x" + Long.toHexString(hwnd) + " prevStyle=0x"
                    + Long.toHexString(((Number) prev).longValue()) + " -> " + x + "," + y + " " + width + "x"
                    + height + " setWindowPos=" + ok);
        } catch (Throwable t) {
            Log.warn("Failed to apply borderless window style.", t);
        }
    }

    /**
     * Restores the normal decorated window style and places it at the given rectangle.
     */
    public static synchronized void makeWindowed(int x, int y, int width, int height) {
        if (!isSupported()) {
            return;
        }
        try {
            long hwnd = hwnd();
            long style = ((long) (WS_OVERLAPPEDWINDOW | WS_VISIBLE)) & 0xFFFFFFFFL;
            Object prev = setWindowLongPtrMethod.invoke(null, hwnd, GWL_STYLE, style);
            Object ok = setWindowPosMethod.invoke(null, hwnd, HWND_NOTOPMOST, x, y, width, height,
                    (long) (SWP_FRAMECHANGED | SWP_SHOWWINDOW | SWP_NOZORDER));
            Log.info("makeWindowed hwnd=0x" + Long.toHexString(hwnd) + " prevStyle=0x"
                    + Long.toHexString(((Number) prev).longValue()) + " -> " + x + "," + y + " " + width + "x"
                    + height + " setWindowPos=" + ok);
        } catch (Throwable t) {
            Log.warn("Failed to restore decorated window style.", t);
        }
    }
}
