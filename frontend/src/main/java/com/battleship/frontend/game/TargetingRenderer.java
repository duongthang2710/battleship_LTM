package com.battleship.frontend.game;

import com.battleship.frontend.graphics.Materials;
import com.battleship.shared.game.Coordinate;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.FastMath;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.renderer.queue.RenderQueue.ShadowMode;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial.CullHint;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Torus;

public final class TargetingRenderer {
    private final Node root = new Node("Attack targeting reticle");
    private final BoardCoordinates coordinates;

    public TargetingRenderer(Node board, Materials materials) {
        coordinates = new BoardCoordinates(board);
        Material cyan = materials.glow(Materials.linear(0x8CCEDD), 0.60f);
        Material gold = materials.glow(Materials.linear(0xDBBD7B), 0.65f);
        for (int x : new int[] {-1, 1}) for (int z : new int[] {-1, 1}) {
            Geometry horizontal = geometry(new Geometry("Target corner", new Box(0.085f, 0.004f, 0.009f)), cyan);
            horizontal.setLocalTranslation(x * 0.35f, 0, z * 0.43f);
            Geometry vertical = geometry(new Geometry("Target corner", new Box(0.009f, 0.004f, 0.085f)), gold);
            vertical.setLocalTranslation(x * 0.43f, 0, z * 0.35f);
        }
        Geometry ring = geometry(new Geometry("Target sight", new Torus(5, 32, 0.008f, 0.20f)), gold);
        ring.rotate(-FastMath.HALF_PI, 0, 0);
        board.attachChild(root);
        clear();
    }

    public void show(Coordinate cell) {
        root.setLocalTranslation(coordinates.localCenter(cell, 0.265f));
        root.setCullHint(CullHint.Inherit);
    }

    public void clear() { root.setCullHint(CullHint.Always); }

    private Geometry geometry(Geometry geometry, Material material) {
        material.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        material.getAdditionalRenderState().setDepthWrite(false);
        geometry.setMaterial(material);
        geometry.setQueueBucket(Bucket.Transparent);
        geometry.setShadowMode(ShadowMode.Off);
        root.attachChild(geometry);
        return geometry;
    }
}
