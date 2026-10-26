package com.battleship.frontend.settings;

import com.jme3.app.Application;
import com.jme3.system.lwjgl.LwjglWindow;
import java.util.List;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;

/** Borderless fullscreen preserves the OpenGL context, assets, inputs and TCP session. */
public final class DisplaySettings {
    private final Application application;
    private int windowX, windowY;
    private boolean restorePosition;

    public DisplaySettings(Application application) { this.application = application; }
    private long window() {
        if (!(application.getContext() instanceof LwjglWindow context) || context.getWindowHandle() == 0)
            throw new IllegalStateException("Display controls require a desktop window.");
        return context.getWindowHandle();
    }
    public List<ClientPreferences.Resolution> resolutions() {
        try (var stack = MemoryStack.stackPush()) {
            var x = stack.mallocInt(1); var y = stack.mallocInt(1); var w = stack.mallocInt(1); var h = stack.mallocInt(1);
            long monitor = GLFW.glfwGetPrimaryMonitor();
            if (monitor == 0) throw new IllegalStateException("No display monitor is available.");
            GLFW.glfwGetMonitorWorkarea(monitor, x, y, w, h);
            var sizes = ClientPreferences.RESOLUTIONS.stream().filter(s -> s.width() <= w.get(0) - 64 && s.height() <= h.get(0) - 64).toList();
            if (sizes.isEmpty()) throw new IllegalStateException("Display needs at least a 960 x 600 usable window.");
            return sizes;
        }
    }
    public ClientPreferences fit(ClientPreferences value) {
        var sizes = resolutions();
        if (sizes.contains(new ClientPreferences.Resolution(value.width(), value.height()))) return value;
        var size = sizes.getLast();
        return new ClientPreferences(size.width(), size.height(), value.fullscreen(), value.rotation(), value.zoom());
    }
    public boolean fullscreen() { return GLFW.glfwGetWindowAttrib(window(), GLFW.GLFW_DECORATED) == GLFW.GLFW_FALSE; }
    public ClientPreferences.Resolution windowSize() {
        try (var stack = MemoryStack.stackPush()) {
            var w = stack.mallocInt(1); var h = stack.mallocInt(1); GLFW.glfwGetWindowSize(window(), w, h);
            return new ClientPreferences.Resolution(w.get(0), h.get(0));
        }
    }
    public void apply(ClientPreferences value) {
        long handle = window(), monitor = GLFW.glfwGetPrimaryMonitor();
        if (monitor == 0) throw new IllegalStateException("No display monitor is available.");
        var video = GLFW.glfwGetVideoMode(monitor);
        if (video == null) throw new IllegalStateException("No display monitor is available.");
        if (GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE) GLFW.glfwRestoreWindow(handle);
        boolean wasFullscreen = fullscreen();
        try (var stack = MemoryStack.stackPush()) {
            var x = stack.mallocInt(1); var y = stack.mallocInt(1); var w = stack.mallocInt(1); var h = stack.mallocInt(1);
            if (value.fullscreen()) {
                if (!wasFullscreen) { GLFW.glfwGetWindowPos(handle, x, y); windowX = x.get(0); windowY = y.get(0); restorePosition = true; }
                GLFW.glfwGetMonitorPos(monitor, x, y);
                GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE);
                GLFW.glfwSetWindowPos(handle, x.get(0), y.get(0));
                GLFW.glfwSetWindowSize(handle, video.width(), video.height());
            } else {
                GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_DECORATED, GLFW.GLFW_TRUE);
                GLFW.glfwSetWindowSize(handle, value.width(), value.height());
                if (wasFullscreen) {
                    GLFW.glfwGetMonitorWorkarea(monitor, x, y, w, h);
                    int px = restorePosition ? Math.max(x.get(0), Math.min(windowX, x.get(0) + w.get(0) - value.width())) : x.get(0) + (w.get(0) - value.width()) / 2;
                    int py = restorePosition ? Math.max(y.get(0) + 32, Math.min(windowY, y.get(0) + h.get(0) - value.height())) : y.get(0) + 32;
                    GLFW.glfwSetWindowPos(handle, px, py);
                }
            }
        }
        if (fullscreen() != value.fullscreen()) throw new IllegalStateException("The requested display mode could not be applied.");
    }
}
