package com.battleship.frontend.settings;

import com.battleship.frontend.camera.OrbitCamera;
import com.jme3.app.SimpleApplication;
import com.jme3.system.AppSettings;
import com.jme3.system.lwjgl.LwjglWindow;
import java.nio.file.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;

/** Native multi-monitor check; settings are written only under build/window-review. */
public final class DisplayWindowChecks extends SimpleApplication {
    private DisplaySettings display;
    private SettingsController controller;
    private long handle;
    private long[] monitors;
    private int index, phase, frame;
    private Path profile;

    public static void main(String[] args) throws Exception {
        var folder = Path.of("build/window-review").toAbsolutePath(); Files.createDirectories(folder);
        var app = new DisplayWindowChecks(); app.profile = folder.resolve("settings.properties");
        System.setProperty("battleship.settingsFile", app.profile.toString());
        new SettingsStore().save(new ClientPreferences(960, 600, true, 1.5f, .75f));
        var settings = new AppSettings(true); settings.setResolution(960, 600); settings.setResizable(true);
        settings.setTitle("Battleship window verification"); settings.setRenderer(AppSettings.LWJGL_OPENGL32);
        settings.setAudioRenderer(null); settings.setFrameRate(30);
        app.setSettings(settings); app.setShowSettings(false); app.start();
    }

    @Override public void simpleInitApp() {
        flyCam.setEnabled(false); setDisplayFps(false); setDisplayStatView(false); setPauseOnLostFocus(false);
        handle = ((LwjglWindow) getContext()).getWindowHandle(); display = new DisplaySettings(this);
        var connected = GLFW.glfwGetMonitors(); require(connected != null, "At least one connected monitor");
        monitors = new long[connected.limit()]; for (int i = 0; i < monitors.length; i++) monitors[i] = connected.get(i);
        controller = new SettingsController(new SettingsPanel(assetManager), new OrbitCamera(cam), display, () -> { });
        System.out.println("Native display verification: " + monitors.length + " monitors connected.");
    }

    @Override public void simpleUpdate(float delta) {
        controller.update();
        if (++frame < 8 || !controller.ready() || controller.saving()) return;
        frame = 0;
        switch (phase) {
            case 0 -> {
                if (index == 0) {
                    require(!display.fullscreen() && !controller.current().fullscreen(), "Saved fullscreen=true cannot enable borderless mode on startup");
                    require(controller.current().rotation() == 1.5f && controller.current().zoom() == .75f, "Safe startup preserves camera preferences");
                    require(new SettingsStore(profile).load().preferences().fullscreen(), "Regression test really loaded an unchanged fullscreen=true file");
                    System.out.println("PASS startup regression: saved fullscreen=true -> decorated Windowed, other preferences retained.");
                }
                try (var stack = MemoryStack.stackPush()) {
                    var x = stack.mallocInt(1); var y = stack.mallocInt(1); var w = stack.mallocInt(1); var h = stack.mallocInt(1);
                    GLFW.glfwGetMonitorWorkarea(monitors[index], x, y, w, h);
                    // Intentionally straddle the right edge. apply() must recover the entire frame.
                    GLFW.glfwSetWindowPos(handle, x.get(0) + w.get(0) - 700, y.get(0) + 80);
                }
                display.apply(new ClientPreferences(960, 600, false, 1, 1)); phase = 1;
            }
            case 1 -> {
                assertWindowed(); GLFW.glfwMaximizeWindow(handle); phase = 2;
            }
            case 2 -> {
                require(GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE, "Native maximize works");
                display.apply(new ClientPreferences(960, 600, true, 1, 1)); phase = 3;
            }
            case 3 -> {
                require(display.fullscreen(), "Borderless fullscreen enabled");
                try (var stack = MemoryStack.stackPush()) {
                    var x = stack.mallocInt(1); var y = stack.mallocInt(1); var w = stack.mallocInt(1); var h = stack.mallocInt(1);
                    GLFW.glfwGetWindowPos(handle, x, y); GLFW.glfwGetWindowSize(handle, w, h);
                    int wx = x.get(0), wy = y.get(0); var video = GLFW.glfwGetVideoMode(monitors[index]);
                    GLFW.glfwGetMonitorPos(monitors[index], x, y);
                    require(video != null && wx == x.get(0) && wy == y.get(0) && w.get(0) == video.width() && h.get(0) == video.height(),
                            "Fullscreen covers exactly the current monitor, not the virtual desktop");
                }
                controller.toggleFullscreen(); phase = 4;
            }
            case 4 -> {
                assertWindowed();
                require(!new SettingsStore(profile).load().preferences().fullscreen(), "Shortcut persists windowed mode");
                require(((LwjglWindow) getContext()).getWindowHandle() == handle, "Display changes preserve the same native window/OpenGL context");
                GLFW.glfwIconifyWindow(handle); phase = 5;
            }
            case 5 -> {
                require(GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_ICONIFIED) == GLFW.GLFW_TRUE, "Native minimize works");
                GLFW.glfwRestoreWindow(handle); phase = 6;
            }
            case 6 -> {
                assertWindowed(); System.out.println("PASS monitor " + (index + 1) + ": framed window bounds, maximize/minimize/restore, current-monitor fullscreen, persisted return to windowed, same context.");
                if (++index < monitors.length) phase = 0;
                else { System.out.println("PASS native display controls across all " + monitors.length + " connected monitors; closing via GLFW window close request."); GLFW.glfwSetWindowShouldClose(handle, true); }
            }
        }
    }

    private void assertWindowed() {
        require(!display.fullscreen() && GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_RESIZABLE) == GLFW.GLFW_TRUE, "Window has native frame and resize controls");
        try (var stack = MemoryStack.stackPush()) {
            var x = stack.mallocInt(1); var y = stack.mallocInt(1); var w = stack.mallocInt(1); var h = stack.mallocInt(1);
            var left = stack.mallocInt(1); var top = stack.mallocInt(1); var right = stack.mallocInt(1); var bottom = stack.mallocInt(1);
            GLFW.glfwGetWindowPos(handle, x, y); GLFW.glfwGetWindowSize(handle, w, h);
            GLFW.glfwGetWindowFrameSize(handle, left, top, right, bottom);
            int x1 = x.get(0) - left.get(0), y1 = y.get(0) - top.get(0), x2 = x.get(0) + w.get(0) + right.get(0), y2 = y.get(0) + h.get(0) + bottom.get(0);
            GLFW.glfwGetMonitorWorkarea(monitors[index], x, y, w, h);
            require(x1 >= x.get(0) && y1 >= y.get(0) && x2 <= x.get(0) + w.get(0) && y2 <= y.get(0) + h.get(0), "Entire title bar/frame is within monitor " + (index + 1));
            if (System.getProperty("os.name").startsWith("Windows")) require(top.get(0) > 0, "Windows title bar exists");
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    @Override public void handleError(String message, Throwable error) { if (error != null) error.printStackTrace(); System.exit(1); }
    @Override public void destroy() { if (controller != null) controller.close(); super.destroy(); }
}
