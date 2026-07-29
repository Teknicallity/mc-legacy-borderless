package io.github.teknicallity.legacyborderless.engine;

/**
 * Tiny dependency-free logger.
 * <p>
 * The engine is reachable both from the FML coremod transformer (which runs before Forge's logging is set up)
 * and from ordinary mod code, so it deliberately avoids Forge/Log4j and just writes to the console with a
 * recognizable prefix.
 */
public final class Log {

    private static final String PREFIX = "[LegacyBorderless] ";

    private Log() {
    }

    public static void info(String message) {
        System.out.println(PREFIX + message);
    }

    public static void warn(String message) {
        System.out.println(PREFIX + "WARN: " + message);
    }

    public static void warn(String message, Throwable t) {
        System.out.println(PREFIX + "WARN: " + message);
        if (t != null) {
            t.printStackTrace(System.out);
        }
    }
}
