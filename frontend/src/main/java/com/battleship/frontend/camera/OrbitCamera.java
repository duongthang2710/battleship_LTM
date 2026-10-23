package com.battleship.frontend.camera;

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;

public final class OrbitCamera {
    private static final float MIN_DISTANCE = 20;
    private static final float MAX_DISTANCE = 27;
    private static final float INITIAL_YAW = FastMath.HALF_PI;
    private static final float MIN_ELEVATION = 0.28f;
    private static final float MAX_ELEVATION = FastMath.PI - MIN_ELEVATION;
    private static final Vector3f BOARD_CENTER = new Vector3f(0, 0.2f, 0);
    private final Camera camera;
    private float yaw = INITIAL_YAW;
    private float elevation = 0.50f;
    private float distance = 21;
    private float homeYaw = INITIAL_YAW;

    public OrbitCamera(Camera camera) {
        this.camera = camera;
        update();
    }

    public void orbit(float dx, float dy) {
        yaw -= dx * (1.92f / 1024);
        elevation -= dy * (1.44f / 1024);
        yaw %= FastMath.TWO_PI;
        elevation = FastMath.clamp(elevation, MIN_ELEVATION, MAX_ELEVATION);
        update();
    }

    public void zoom(int wheelDelta) {
        distance = FastMath.clamp(distance - wheelDelta * 0.24f, MIN_DISTANCE, MAX_DISTANCE);
        update();
    }

    public void reset() {
        yaw = homeYaw;
        elevation = 0.50f;
        distance = 21;
        update();
    }

    public void setPlayerSide(com.battleship.shared.game.Side side) {
        // White base is at -X; look through the board toward the black base at +X.
        homeYaw = side == com.battleship.shared.game.Side.WHITE ? -FastMath.HALF_PI : FastMath.HALF_PI;
        reset();
    }

    public void useDefaultView() { homeYaw = INITIAL_YAW; reset(); }

    public void update() {
        camera.setFrustumPerspective(58, (float) camera.getWidth() / camera.getHeight(), 0.1f, 1400);
        // Frame the large coastal bases while orbiting around the board's original anchor.
        camera.setFrustumTop(camera.getFrustumTop() * 1.38f);
        camera.setFrustumBottom(camera.getFrustumBottom() * 1.20f);
        float sine = FastMath.sin(elevation), cosine = FastMath.cos(elevation);
        float sinYaw = FastMath.sin(yaw), cosYaw = FastMath.cos(yaw);
        float horizontal = distance * cosine;
        camera.setLocation(new Vector3f(horizontal * sinYaw,
                BOARD_CENTER.y + distance * sine, horizontal * cosYaw));
        float overhead = FastMath.clamp((Math.min(elevation, FastMath.PI - elevation) - 0.50f)
                / (FastMath.HALF_PI - 0.50f), 0, 1);
        float focusLift = 4.3f * (1 - overhead * overhead * (3 - 2 * overhead));
        // The orbit tangent remains valid at the zenith, unlike a fixed vertical up vector.
        Vector3f up = new Vector3f(-sine * sinYaw, cosine, -sine * cosYaw);
        camera.lookAt(BOARD_CENTER.add(0, focusLift, 0), up);
    }
}
