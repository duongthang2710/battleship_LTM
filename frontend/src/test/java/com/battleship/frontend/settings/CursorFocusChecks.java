package com.battleship.frontend.settings;

import com.battleship.frontend.app.BattleshipApplication;
import com.jme3.system.AppSettings;
import com.jme3.system.lwjgl.LwjglWindow;
import com.jme3.app.state.ScreenshotAppState;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Quad;
import javax.imageio.ImageIO;
import java.net.ServerSocket;
import java.nio.file.*;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import org.lwjgl.glfw.GLFW;

/** Real production application and native focus transfers to a second test window. */
public final class CursorFocusChecks {
    public static void main(String[] args) throws Exception {
        Path folder = Path.of("build/window-review").toAbsolutePath(); Files.createDirectories(folder);
        System.setProperty("battleship.settingsFile", folder.resolve("cursor-focus.properties").toString());
        new SettingsStore().save(new ClientPreferences(960, 600, false, 1, 1));
        // Isolate the application from the user's authenticated backend; no account or database is touched.
        try (var listener = new ServerSocket(0)) {
            System.setProperty("battleship.host", "127.0.0.1");
            System.setProperty("battleship.port", Integer.toString(listener.getLocalPort()));
            var app = new BattleshipApplication();
            var settings = new AppSettings(true); settings.setResolution(960, 600); settings.setResizable(true);
            settings.setTitle("Battleship cursor focus verification"); settings.setRenderer(AppSettings.LWJGL_OPENGL32);
            settings.setAudioRenderer(null); settings.setFrameRate(30); settings.setSamples(4); settings.setGammaCorrection(true);
            app.setSettings(settings); app.setShowSettings(false); app.setPauseOnLostFocus(false); app.start();
            long helper = 0;
            try {
                long handle = onRender(app, () -> ((LwjglWindow) app.getContext()).getWindowHandle());
                var screenshots = new ScreenshotAppState(folder.toString() + java.io.File.separator);
                onRender(app, () -> { screenshots.setIsNumbered(false); app.getStateManager().attach(screenshots); return null; });
                helper = onRender(app, () -> {
                    GLFW.glfwDefaultWindowHints(); GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_NO_API);
                    GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE); GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE);
                    long result = GLFW.glfwCreateWindow(320, 160, "Cursor focus test companion", 0, 0);
                    GLFW.glfwDefaultWindowHints(); require(result != 0, "Native focus companion created"); return result;
                });
                long other = helper; var display = new DisplaySettings(app);
                for (boolean fullscreen : new boolean[] {false, true}) {
                    onRender(app, () -> { display.apply(new ClientPreferences(960, 600, fullscreen, 1, 1)); return null; });
                    for (int cycle = 0; cycle < 3; cycle++) {
                        onRender(app, () -> { GLFW.glfwShowWindow(other); GLFW.glfwFocusWindow(other); return null; });
                        await(app, () -> GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_FOCUSED) == GLFW.GLFW_FALSE
                                && GLFW.glfwGetWindowAttrib(other, GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE, "Native focus left the app");
                        int nativeMode = cycle == 1 ? GLFW.GLFW_CURSOR_DISABLED : GLFW.GLFW_CURSOR_HIDDEN;
                        onRender(app, () -> {
                            require(app.getInputManager().isCursorVisible(), "Engine visibility cache still says visible");
                            GLFW.glfwSetInputMode(handle, GLFW.GLFW_CURSOR, nativeMode);
                            // This is the stale-cache case a repeated InputManager.setCursorVisible(true) cannot fix.
                            app.getInputManager().setCursorVisible(true);
                            require(GLFW.glfwGetInputMode(handle, GLFW.GLFW_CURSOR) == nativeMode, "Reproduced cached visibility mismatch");
                            GLFW.glfwFocusWindow(handle); return null;
                        });
                        await(app, () -> GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE
                                && GLFW.glfwGetInputMode(handle, GLFW.GLFW_CURSOR) == (fullscreen ? GLFW.GLFW_CURSOR_HIDDEN : GLFW.GLFW_CURSOR_NORMAL),
                                "Production gainFocus restores the appropriate non-grabbing cursor mode");
                        onRender(app, () -> {
                            // Inject the real GLFW callback, as UiInteractionChecks does: programmatic warps
                            // do not reliably emit cursor callbacks on the Windows test desktop.
                            move(app, handle, 150, 180); return null;
                        });
                        await(app, () -> app.getInputManager().isCursorVisible()
                                && app.getInputManager().getCursorPosition().x > 0 && Float.isFinite(app.getInputManager().getCursorPosition().y), "Mouse input still updates after focus recovery");
                        if (fullscreen) {
                            await(app, () -> app.getGuiNode().getChild("Fullscreen cursor").getLocalCullHint() == Spatial.CullHint.Never, "Rendered fullscreen cursor is present");
                            var plate = onRender(app, () -> {
                                var point = app.getInputManager().getCursorPosition();
                                var material = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
                                material.setColor("Color", new ColorRGBA(.02f, .03f, .05f, 1));
                                var backing = new Geometry("Cursor pixel verification backing", new Quad(50, 60)); backing.setMaterial(material);
                                backing.setLocalTranslation(point.x - 4, point.y - 45, 499); app.getGuiNode().attachChild(backing); return backing;
                            });
                            var visible = capture(app, screenshots, folder, "cursor-fullscreen-" + cycle);
                            int white = arrowPixels(visible, 150, 180);
                            require(white >= 30, "Cursor must be painted into the framebuffer, not just flagged visible; white pixels=" + white);
                            onRender(app, () -> { move(app, handle, 300, 180); return null; });
                            var moved = capture(app, screenshots, folder, "cursor-moved-" + cycle);
                            require(arrowPixels(moved, 150, 180) < 5, "Rendered cursor follows the pointer and leaves its old position");
                            onRender(app, () -> { GLFW.glfwSetCursorPos(handle, 180, 180); return null; });
                            await(app, () -> Math.abs(app.getGuiNode().getChild("Fullscreen cursor").getLocalTranslation().x - 180) < 1,
                                    "Rendered cursor follows native position even without a motion callback");
                            onRender(app, () -> { plate.removeFromParent(); return null; });
                            System.out.println("PASS rendered cursor pixels after fullscreen focus return " + (cycle + 1) + ": " + white + " bright pixels, previous position clears after movement.");
                        } else require(onRender(app, () -> app.getGuiNode().getChild("Fullscreen cursor").getLocalCullHint()) == Spatial.CullHint.Always,
                                "Windowed uses the normal native cursor without a duplicate overlay");
                        System.out.println("PASS production cursor: " + (fullscreen ? "fullscreen" : "windowed") + ", native focus return " + (cycle + 1) + ", stale cache recovered and mouse input active.");
                    }
                }
                System.out.println("PASS: six native focus loss/return cycles on the production application, including hidden/disabled native cursor with a visible engine cache.");
            } finally {
                long closeHelper = helper;
                onRender(app, () -> { if (closeHelper != 0) GLFW.glfwDestroyWindow(closeHelper); app.stop(); return null; });
            }
        }
    }

    private static void move(BattleshipApplication app, long handle, int x, int y) {
        GLFW.glfwSetCursorPos(handle, x, y);
        var callback = GLFW.glfwSetCursorPosCallback(handle, null); GLFW.glfwSetCursorPosCallback(handle, callback);
        require(callback != null, "Production mouse callback remains registered"); callback.invoke(handle, x, y);
    }
    private static java.awt.image.BufferedImage capture(BattleshipApplication app, ScreenshotAppState shots, Path folder, String name) throws Exception {
        var path = folder.resolve(name + ".png"); Files.deleteIfExists(path);
        onRender(app, () -> { shots.setFileName(name); shots.takeScreenshot(); return null; });
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!Files.exists(path)) { if (System.nanoTime() > end) throw new AssertionError("Screenshot not saved"); Thread.sleep(20); }
        onRender(app, () -> true); // Wait until the screenshot's postFrame writer has finished.
        return ImageIO.read(path.toFile());
    }
    private static int arrowPixels(java.awt.image.BufferedImage image, int x, int y) {
        int count = 0;
        for (int py = y; py < y + 32; py++) for (int px = x; px < x + 23; px++) {
            int rgb = image.getRGB(px, py);
            if (((rgb >> 16) & 255) > 210 && ((rgb >> 8) & 255) > 210 && (rgb & 255) > 200) count++;
        }
        return count;
    }

    private static <T> T onRender(BattleshipApplication app, Callable<T> action) throws Exception {
        // jME's timed AppTask.get incorrectly treats a completed null result as a timeout.
        return app.enqueue(() -> java.util.Optional.ofNullable(action.call())).get(60, TimeUnit.SECONDS).orElse(null);
    }
    private static void await(BattleshipApplication app, Callable<Boolean> condition, String message) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        do { if (onRender(app, condition)) return; Thread.sleep(40); } while (System.nanoTime() < end);
        throw new AssertionError(message);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
