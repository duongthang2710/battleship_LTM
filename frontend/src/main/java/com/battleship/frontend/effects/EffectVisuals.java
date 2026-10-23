package com.battleship.frontend.effects;

import com.battleship.frontend.graphics.Materials;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.renderer.queue.RenderQueue.ShadowMode;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Sphere;
import com.jme3.scene.shape.Torus;

final class EffectVisuals {
    private EffectVisuals() {}

    static Geometry ring(Node parent, Materials materials, int color, float radius, float thickness) {
        Geometry ring = geometry(parent, new Geometry("Attack ripple", new Torus(5, 40, thickness, radius)),
                materials, color, true);
        ring.rotate(-FastMath.HALF_PI, 0, 0);
        return ring;
    }

    static Geometry sphere(Node parent, Materials materials, int color, float radius, boolean glow) {
        return geometry(parent, new Geometry("Attack particle", new Sphere(6, 8, radius)),
                materials, color, glow);
    }

    private static Geometry geometry(Node parent, Geometry geometry, Materials materials, int color, boolean glow) {
        Material material = glow ? materials.glow(Materials.linear(color), 0.85f)
                : materials.flat(Materials.linear(color));
        material.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        material.getAdditionalRenderState().setDepthWrite(false);
        geometry.setMaterial(material);
        geometry.setQueueBucket(Bucket.Transparent);
        geometry.setShadowMode(ShadowMode.Off);
        parent.attachChild(geometry);
        return geometry;
    }

    static void fade(Geometry geometry, float opacity) {
        Material material = geometry.getMaterial();
        ColorRGBA tint = ((ColorRGBA) material.getParam("Color").getValue()).clone();
        tint.a = FastMath.clamp(opacity, 0, 1);
        material.setColor("Color", tint);
        if (material.getParam("GlowColor") != null) {
            ColorRGBA glow = tint.mult(tint.a);
            glow.a = tint.a;
            material.setColor("GlowColor", glow);
        }
    }

    static Vector3f direction(int index, int count) {
        float angle = FastMath.TWO_PI * index / count;
        return new Vector3f(FastMath.cos(angle), 0, FastMath.sin(angle));
    }
}
