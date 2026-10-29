package com.battleship.frontend.ui;

import com.jme3.asset.AssetManager;
import com.jme3.input.InputManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.Camera;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.*;
import com.jme3.system.JmeContext;
import com.jme3.system.lwjgl.LwjglWindow;
import com.jme3.util.BufferUtils;
import org.lwjgl.glfw.GLFW;

/** A rendered fullscreen pointer; native input stays ungrabbed and keeps the same click coordinates. */
public final class FullscreenCursor implements AutoCloseable {
    private final Node root = new Node("Fullscreen cursor");
    private final InputManager input;
    private final JmeContext context;
    private final Camera guiCamera;
    private final double[] nativeX = new double[1], nativeY = new double[1];
    private final int[] windowWidth = new int[1], windowHeight = new int[1];
    private boolean software;

    public FullscreenCursor(AssetManager assets, Node gui, InputManager input, JmeContext context, Camera guiCamera) {
        this.input = input; this.context = context; this.guiCamera = guiCamera;
        root.setQueueBucket(RenderQueue.Bucket.Gui);
        root.setCullHint(Spatial.CullHint.Always);
        root.attachChild(arrow(assets, "Pointer outline", new ColorRGBA(.018f, .025f, .04f, 1),
                new float[] {0, 0, 0, -25, 6.8f, -18.4f, 12.1f, -29, 16.4f, -26.8f, 11, -16.4f, 20, -16.1f}));
        root.attachChild(arrow(assets, "Pointer fill", new ColorRGBA(.98f, .97f, .93f, 1),
                new float[] {1.6f, -3.9f, 1.6f, -21.2f, 7.2f, -15.7f, 12.8f, -26.7f, 14.2f, -25.9f, 8.5f, -14.6f, 16.1f, -14.7f}));
        root.getChild(1).setLocalTranslation(0, 0, .1f);
        gui.attachChild(root);
    }

    public void update() {
        if (!(context instanceof LwjglWindow window) || window.getWindowHandle() == 0) return;
        long handle = window.getWindowHandle();
        boolean active = GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_DECORATED) == GLFW.GLFW_FALSE
                && GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE
                && GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_ICONIFIED) == GLFW.GLFW_FALSE;
        if (software != active) {
            software = active;
            if (!active) {
                context.getMouseInput().setCursorVisible(true);
                input.setMouseCursor(null);
            }
        }
        root.setCullHint(Spatial.CullHint.Always);
        if (!active) return;
        // HIDDEN only hides the native image. DISABLED would grab the pointer and change controls.
        if (GLFW.glfwGetInputMode(handle, GLFW.GLFW_CURSOR) != GLFW.GLFW_CURSOR_HIDDEN)
            GLFW.glfwSetInputMode(handle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
        GLFW.glfwGetWindowSize(handle, windowWidth, windowHeight);
        GLFW.glfwGetCursorPos(handle, nativeX, nativeY);
        // A fullscreen window may keep keyboard focus while the pointer is on another monitor.
        if (nativeX[0] < 0 || nativeY[0] < 0 || nativeX[0] >= windowWidth[0] || nativeY[0] >= windowHeight[0]) return;
        float scaleX = (float) guiCamera.getWidth() / Math.max(1, windowWidth[0]);
        float scaleY = (float) guiCamera.getHeight() / Math.max(1, windowHeight[0]);
        // Read the current native position: a focus/resize/warp need not emit a mouse-motion event.
        root.setLocalScale(scaleX);
        root.setLocalTranslation((float) nativeX[0] * scaleX, guiCamera.getHeight() - (float) nativeY[0] * scaleY, 500);
        root.setCullHint(Spatial.CullHint.Never);
    }

    public void refresh() {
        software = false;
        input.setMouseCursor(null);
        update();
    }

    private static Geometry arrow(AssetManager assets, String name, ColorRGBA color, float[] points) {
        float[] vertices = new float[points.length / 2 * 3];
        for (int i = 0; i < points.length / 2; i++) { vertices[i * 3] = points[i * 2]; vertices[i * 3 + 1] = points[i * 2 + 1]; }
        int[] triangles = new int[(points.length / 2 - 2) * 3];
        for (int i = 0; i < triangles.length / 3; i++) { triangles[i * 3] = 0; triangles[i * 3 + 1] = i + 1; triangles[i * 3 + 2] = i + 2; }
        var mesh = new Mesh(); mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(vertices));
        mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(triangles)); mesh.updateBound(); mesh.setStatic();
        var material = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md"); material.setColor("Color", color);
        material.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        material.getAdditionalRenderState().setDepthTest(false); material.getAdditionalRenderState().setDepthWrite(false);
        var geometry = new Geometry(name, mesh); geometry.setMaterial(material); return geometry;
    }

    @Override public void close() {
        root.removeFromParent();
        if (context.isCreated() && context instanceof LwjglWindow window && window.getWindowHandle() != 0) {
            context.getMouseInput().setCursorVisible(true);
            input.setMouseCursor(null);
        }
    }
}
