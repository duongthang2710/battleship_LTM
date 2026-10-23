package com.battleship.frontend.scene.environment;

import com.jme3.asset.AssetManager;
import com.jme3.bounding.BoundingBox;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import java.util.List;

final class CastleEnvironmentBuilder {
    private final Node root = new Node("Board-side faction bases");
    private final List<CastleImpostor> bases;
    private final Vector3f boardCenter;

    CastleEnvironmentBuilder(AssetManager assets, BoundingBox boardBounds, float waterline) {
        float offsetX = boardBounds.getXExtent() * 23.0f;
        float height = boardBounds.getZExtent() * 10.5f;
        Vector3f center = boardBounds.getCenter();
        boardCenter = new Vector3f(center.x, 0.2f, center.z);
        CastleLayerBuilder builder = new CastleLayerBuilder(assets);
        CastleImpostor radiant = builder.create("RadiantCastleRoot", false,
                new Vector3f(center.x - offsetX, waterline - 0.35f, center.z), height);
        CastleImpostor abyssal = builder.create("AbyssalCastleRoot", true,
                new Vector3f(center.x + offsetX, waterline - 0.35f, center.z), height);
        bases = List.of(radiant, abyssal);
        for (CastleImpostor base : bases) root.attachChild(base.root());
    }

    Node root() { return root; }

    void update(Camera camera) {
        float boardDepth = boardCenter.subtract(camera.getLocation()).dot(camera.getDirection());
        for (CastleImpostor base : bases) {
            float depth = base.root().getWorldTranslation().subtract(camera.getLocation()).dot(camera.getDirection());
            float visibility = FastMath.clamp((depth / boardDepth - 0.55f) / 0.35f, 0, 1);
            base.setOpacity(visibility * visibility * (3 - 2 * visibility));
        }
    }
}
