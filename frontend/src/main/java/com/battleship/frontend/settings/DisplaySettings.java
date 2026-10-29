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
    private long monitor(long handle) {
        long attached = GLFW.glfwGetWindowMonitor(handle);
        if (attached != 0) return attached;
        long selected = GLFW.glfwGetPrimaryMonitor();
        var monitors = GLFW.glfwGetMonitors();
        if (monitors == null || selected == 0) throw new IllegalStateException("No display monitor is available.");
        try (var stack = MemoryStack.stackPush()) {
            var x = stack.mallocInt(1); var y = stack.mallocInt(1); var w = stack.mallocInt(1); var h = stack.mallocInt(1);
            GLFW.glfwGetWindowPos(handle, x, y); GLFW.glfwGetWindowSize(handle, w, h);
            int wx = x.get(0), wy = y.get(0), ww = w.get(0), wh = h.get(0);
            long largestArea = 0;
            for (int i = 0; i < monitors.limit(); i++) {
                long candidate = monitors.get(i);
                GLFW.glfwGetMonitorWorkarea(candidate, x, y, w, h);
                long area = (long) Math.max(0, Math.min(wx + ww, x.get(0) + w.get(0)) - Math.max(wx, x.get(0)))
                        * Math.max(0, Math.min(wy + wh, y.get(0) + h.get(0)) - Math.max(wy, y.get(0)));
                if (area > largestArea) { largestArea = area; selected = candidate; }
            }
        }
        return selected;
    }
    public List<ClientPreferences.Resolution> resolutions() {
        try (var stack = MemoryStack.stackPush()) {
            var x = stack.mallocInt(1); var y = stack.mallocInt(1); var w = stack.mallocInt(1); var h = stack.mallocInt(1);
            long monitor = monitor(window());
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
        return new ClientPreferences(size.width(), size.height(), value.fullscreen(), value.rotation(), value.zoom(), value.volume(), value.muted());
    }
    public boolean fullscreen() { return GLFW.glfwGetWindowAttrib(window(), GLFW.GLFW_DECORATED) == GLFW.GLFW_FALSE; }
    public ClientPreferences.Resolution windowSize() {
        try (var stack = MemoryStack.stackPush()) {
            var w = stack.mallocInt(1); var h = stack.mallocInt(1); GLFW.glfwGetWindowSize(window(), w, h);
            return new ClientPreferences.Resolution(w.get(0), h.get(0));
        }
    }
    public void apply(ClientPreferences value) {
        long handle = window(), monitor = monitor(handle);
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
                GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE);
                var size = fit(value);
                GLFW.glfwSetWindowSize(handle, size.width(), size.height());
                GLFW.glfwGetWindowPos(handle, x, y);
                int px = wasFullscreen && restorePosition ? windowX : x.get(0);
                int py = wasFullscreen && restorePosition ? windowY : y.get(0);
                GLFW.glfwGetMonitorWorkarea(monitor, x, y, w, h);
                var left = stack.mallocInt(1); var top = stack.mallocInt(1);
                var right = stack.mallocInt(1); var bottom = stack.mallocInt(1);
                GLFW.glfwGetWindowFrameSize(handle, left, top, right, bottom);
                // Clamp the complete native frame, including the title bar, to one monitor.
                px = Math.max(x.get(0) + left.get(0), Math.min(px, x.get(0) + w.get(0) - size.width() - right.get(0)));
                py = Math.max(y.get(0) + top.get(0), Math.min(py, y.get(0) + h.get(0) - size.height() - bottom.get(0)));
                GLFW.glfwSetWindowPos(handle, px, py);
            }
        }
        if (fullscreen() != value.fullscreen()) throw new IllegalStateException("The requested display mode could not be applied.");
    }
}
