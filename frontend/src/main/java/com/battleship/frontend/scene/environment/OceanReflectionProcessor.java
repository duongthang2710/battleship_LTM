package com.battleship.frontend.scene.environment;

import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Matrix4f;
import com.jme3.math.Plane;
import com.jme3.math.Vector3f;
import com.jme3.post.SceneProcessor;
import com.jme3.profile.AppProfiler;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.texture.FrameBuffer;
import com.jme3.texture.Image;
import com.jme3.texture.Texture2D;
import com.jme3.texture.Texture;
import com.jme3.water.WaterUtils;
import java.util.function.Predicate;

final class OceanReflectionProcessor implements SceneProcessor {
    private final Node environment;
    private final Geometry ocean;
    private final Plane waterPlane;
    private final Material material;
    private final Matrix4f lastViewProjection = new Matrix4f();
    private RenderManager manager;
    private ViewPort mainView;
    private ViewPort reflectionView;
    private Camera reflectionCamera;
    private FrameBuffer buffer;
    private float delta;
    private float reflectionAge;
    private boolean reflectionReady;

    OceanReflectionProcessor(Node environment, Geometry ocean) {
        this.environment = environment;
        this.ocean = ocean;
        material = ocean.getMaterial();
        waterPlane = new Plane(Vector3f.UNIT_Y, ocean.getLocalTranslation().y);
    }

    @Override
    public void initialize(RenderManager manager, ViewPort viewport) {
        this.manager = manager;
        mainView = viewport;
        int width = 1024;
        int height = Math.max(1, Math.round(width * (float) viewport.getCamera().getHeight()
                / viewport.getCamera().getWidth()));
        Texture2D texture = new Texture2D(width, height, Image.Format.RGBA8);
        texture.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
        texture.setMagFilter(Texture.MagFilter.Bilinear);
        buffer = new FrameBuffer(width, height, 1);
        buffer.setDepthTarget(FrameBuffer.FrameBufferTarget.newTarget(Image.Format.Depth));
        buffer.addColorTarget(FrameBuffer.FrameBufferTarget.newTarget(texture));
        reflectionCamera = new Camera(width, height);
        // Render only this environment: no board, HUD or recursive water reflection.
        reflectionView = new ViewPort("Ocean planar reflections", reflectionCamera);
        reflectionView.setClearFlags(true, true, true);
        reflectionView.setBackgroundColor(new ColorRGBA(0.035f, 0.08f, 0.15f, 1));
        reflectionView.setOutputFrameBuffer(buffer);
        reflectionView.attachScene(environment);
        material.setTexture("ReflectionMap", texture);
    }

    @Override
    public boolean isInitialized() { return manager != null; }

    @Override
    public void preFrame(float delta) {
        this.delta = delta;
        reflectionAge += delta;
    }

    @Override
    public void postQueue(RenderQueue queue) {
        Camera camera = mainView.getCamera();
        // Camera movement refreshes immediately; tiny ship bobbing needs only 30 Hz reflections.
        if (reflectionReady && reflectionAge < 1f / 30f
                && lastViewProjection.equals(camera.getViewProjectionMatrix())) return;
        reflectionCamera.setProjectionMatrix(null);
        WaterUtils.updateReflectionCam(reflectionCamera, waterPlane, camera);
        reflectionCamera.setClipPlane(waterPlane, Plane.Side.Positive);
        material.setMatrix4("ReflectionViewProjection", reflectionCamera.getViewProjectionMatrix());
        Predicate<Geometry> previous = manager.getRenderFilter();
        manager.setRenderFilter(geometry -> geometry != ocean
                && (previous == null || previous.test(geometry)));
        try {
            manager.renderViewPort(reflectionView, delta);
            lastViewProjection.set(camera.getViewProjectionMatrix());
            reflectionAge = 0;
            reflectionReady = true;
        } finally {
            manager.setRenderFilter(previous);
            manager.getRenderer().setFrameBuffer(mainView.getOutputFrameBuffer());
            manager.setCamera(camera, false);
        }
    }

    @Override
    public void cleanup() {
        if (manager != null) manager.getRenderer().deleteFrameBuffer(buffer);
        material.clearParam("ReflectionMap");
        material.clearParam("ReflectionViewProjection");
        manager = null;
        reflectionReady = false;
    }

    @Override
    public void reshape(ViewPort viewport, int width, int height) { }

    @Override
    public void postFrame(FrameBuffer output) { }

    @Override
    public void setProfiler(AppProfiler profiler) { }
}
