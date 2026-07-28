package com.teknicallity.legacyborderless.engine;

import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;

/**
 * Works out which monitor the game window currently sits on and returns that monitor's full bounds, so the
 * borderless window can be grown to cover exactly one display in a multi-monitor setup.
 * <p>
 * AWT is used because LWJGL 2 exposes no multi-monitor query. If AWT is unavailable or fails, we fall back to
 * the primary desktop mode at the origin.
 */
public final class MonitorLocator {

    private MonitorLocator() {
    }

    /**
     * @param winX   window left edge (virtual-desktop coordinates, as {@link Display#getX()} reports)
     * @param winY   window top edge
     * @param winW   window width
     * @param winH   window height
     * @return the bounds of the monitor containing the window's centre, or a best-effort fallback
     */
    public static Rectangle monitorBoundsFor(int winX, int winY, int winW, int winH) {
        int centreX = winX + winW / 2;
        int centreY = winY + winH / 2;

        try {
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice[] devices = ge.getScreenDevices();
            Rectangle first = null;
            for (GraphicsDevice device : devices) {
                Rectangle bounds = device.getDefaultConfiguration().getBounds();
                if (first == null) {
                    first = bounds;
                }
                if (bounds.contains(centreX, centreY)) {
                    return bounds;
                }
            }
            if (first != null) {
                Log.info("Window centre " + centreX + "," + centreY
                        + " not inside any monitor; using first monitor " + first);
                return first;
            }
        } catch (Throwable t) {
            Log.warn("AWT monitor lookup failed; falling back to primary desktop mode.", t);
        }

        return primaryDesktopBounds();
    }

    private static Rectangle primaryDesktopBounds() {
        try {
            DisplayMode desktop = Display.getDesktopDisplayMode();
            return new Rectangle(0, 0, desktop.getWidth(), desktop.getHeight());
        } catch (Throwable t) {
            Log.warn("Could not read desktop display mode; defaulting to 1920x1080.", t);
            return new Rectangle(0, 0, 1920, 1080);
        }
    }
}
