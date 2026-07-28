# Legacy Borderless Window

A borderless-windowed-fullscreen mod for **Minecraft 1.6.4 (Forge)** — a backport in spirit of
[Nekeras' Borderless Window](https://www.curseforge.com/minecraft/mc-mods/borderless).

## Why

1.6.4's only "fullscreen" is LWJGL 2's **exclusive** fullscreen (`Display.setFullscreen(true)`). It blacks out /
minimises the game the moment it loses focus, which makes alt-tabbing (and multi-monitor use) miserable. The
maximised *windowed* mode keeps its title bar, which is rough on OLED. The `-Dorg.lwjgl.opengl.Window.undecorated=true`
JVM flag only affects windowed mode, so it can't fix the fullscreen path.

This mod replaces that with **true borderless windowed fullscreen**: a title-bar-less window sized to exactly cover
one monitor, and *not* exclusive fullscreen — so alt-tab never blacks out or minimises, other windows can sit on top,
and there's no title bar to burn in.

## How it works

The window is never destroyed/recreated (that would drop the GL context and every texture). Instead the mod restyles
the **existing** window in place using LWJGL 2's own Win32 bindings, reached by reflection:

- `SetWindowLongPtr(hwnd, GWL_STYLE, WS_POPUP)` — strip the border/title bar
- `SetWindowPos(hwnd, …)` — grow it to cover the current monitor

Minecraft picks up the new size through its normal `Display.wasResized()` → `resize()` path, and LWJGL input keeps
working. The monitor is chosen from the window's centre point via AWT, so it targets the display the game is on.

An FML core-mod ([`FullscreenRedirectTransformer`](src/main/java/com/teknicallity/legacyborderless/coremod/FullscreenRedirectTransformer.java)) rewrites `Minecraft` on load so that:

| Trigger | Mechanism |
| --- | --- |
| **F11** | Vanilla's non-rebindable F11 (`Keyboard.getEventKey() == 87 → toggleFullscreen()`) is disabled, and a proper **rebindable** key binding — default **F11**, changeable in Controls — calls the borderless engine instead. |
| **Video Settings → Fullscreen** toggle | `toggleFullscreen()` is neutered (its body replaced with `return`), so this option does nothing — its display-mode dance fought the borderless engine and is unnecessary. Use the key binding instead. |
| **Launch-in-fullscreen** | The `Display.setFullscreen(boolean)` call in `startGame()` is rewritten to route through the borderless engine, so starting with `fullscreen:true` comes up borderless rather than exclusive. |

Only Windows is supported (that's where the LWJGL 2 native helpers live); on other platforms the mod no-ops and the
window is left alone.

## Building

Requires the Java 11 toolchain already configured in [`gradle.properties`](gradle.properties).

```sh
./gradlew build
```

The distributable jar is `build/libs/legacy-windowed-borderless.jar` (drop it in a real 1.6.4 Forge instance's
`mods/` folder). The `-dev.jar` is the un-reobfuscated workspace jar — don't ship that one.

## Testing in the dev workspace

```sh
./gradlew runClient
```

The `runClient` task passes `-Dfml.coreMods.load=…` automatically so the core-mod loads in-workspace. Watch the log
for:

```text
[LegacyBorderless] Patched Minecraft: redirected 2 Display.setFullscreen call(s), disabled 1 hard-coded F11 handler(s).
```

Then in-game:

- Press **F11** → the window should go borderless and cover the monitor (and toggle back).
- Alt-tab away and back → it should **not** black out or minimise.
- Rebind the "Toggle Borderless Fullscreen" key in **Controls** → F11 is now a normal rebindable key.
- Toggle **Fullscreen** in Video Settings → also borderless, never exclusive.

## Notes & limitations

- **Windows only.** The borderless path relies on `org.lwjgl.opengl.WindowsDisplay`.
- **The key binding and the menu label can desync.** The engine owns the actual window state; toggling with the
  key binding (F11) doesn't touch Minecraft's own `fullscreen` flag, so the Video Settings *Fullscreen* label can read
  out of step with reality. It's cosmetic — the window behaves correctly.
- **Display scaling.** Monitor bounds come from AWT. At 100% scaling (or a single monitor) this is exact; with
  per-monitor DPI scaling on a multi-monitor setup the covered rectangle can be slightly off. The chosen rectangle is
  logged so it's easy to diagnose.
- **Overscan / fullscreen-optimization.** A borderless window that *exactly* matches a monitor gets promoted by
  Windows 10/11 to fullscreen-optimization / DWM independent-flip, which black-flashes on alt-tab and behaves like
  exclusive fullscreen. To avoid that, the window is made 1px larger than the monitor (the extra pixel is off-screen).
  Tune with `-Dlegacyborderless.overscan=N` (0 = exact cover). In the dev run: `./gradlew runClient -PlbOverscan=2`.
