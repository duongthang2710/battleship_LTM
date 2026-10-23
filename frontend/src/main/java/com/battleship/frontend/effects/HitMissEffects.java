package com.battleship.frontend.effects;

import com.battleship.frontend.graphics.Materials;
import com.jme3.material.Material;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue.ShadowMode;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Torus;
import java.util.ArrayList;
import java.util.List;

import static com.battleship.frontend.graphics.ProceduralMeshes.*;

public final class HitMissEffects {
    private final Node root = new Node("Hit and miss visual preview");
    private final List<Geometry> ripples = new ArrayList<>();
    private final Material hit;
    private final Material miss;

    public HitMissEffects(Materials materials) {
        hit = materials.glow(Materials.rgb(0xFF593E), 2);
        miss = materials.glow(Materials.rgb(0x6FCBFF), 1.8f);
        root.setShadowMode(ShadowMode.Off);
        marker(-0.5f, -0.5f, true);
        marker(1.5f, 2.5f, true);
        marker(-0.5f, 3.5f, false);
        marker(3.5f, 0.5f, false);
    }

    public Node root() { return root; }

    private void marker(float x, float z, boolean isHit) {
        Node marker = new Node(isHit ? "Hit preview" : "Miss preview");
        marker.setLocalTranslation(x, 0.24f, z);
        Material material = isHit ? hit : miss;
        for (float radius : new float[] {0.24f, 0.36f}) {
            Geometry ring = new Geometry("Water ripple", new Torus(6, 48, 0.009f, radius));
            ring.rotate(FastMath.HALF_PI, 0, 0);
            ring.setMaterial(material);
            marker.attachChild(ring);
            ripples.add(ring);
        }
        float r = isHit ? 0.42f : 0.21f;
        rod(marker, "Marker cross", new Vector3f(-r, 0, 0), new Vector3f(r, 0, 0), 0.009f, material);
        rod(marker, "Marker cross", new Vector3f(0, 0, -r), new Vector3f(0, 0, r), 0.009f, material);
        if (isHit) {
            gem(marker, new Vector3f(0, 0.15f, 0), new Vector3f(0.07f, 0.20f, 0.07f), material);
            for (int i = 0; i < 6; i++) {
                float angle = i * FastMath.TWO_PI / 6;
                rod(marker, "Light burst", new Vector3f(0, 0.10f, 0),
                        new Vector3f(FastMath.cos(angle) * 0.27f, 0.31f, FastMath.sin(angle) * 0.27f),
                        0.007f, material);
            }
        }
        root.attachChild(marker);
    }

    public void update(float time) {
        for (int i = 0; i < ripples.size(); i++) {
            float scale = 1 + 0.10f * FastMath.sin(time * 1.2f + i * 0.6f);
            ripples.get(i).setLocalScale(scale);
        }
    }
}
