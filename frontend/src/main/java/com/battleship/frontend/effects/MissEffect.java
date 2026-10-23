package com.battleship.frontend.effects;

import com.battleship.frontend.graphics.Materials;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import java.util.ArrayList;
import java.util.List;

public final class MissEffect {
    private final Node node = new Node("Miss water splash");
    private final List<Geometry> droplets = new ArrayList<>();
    private final List<Geometry> ripples = new ArrayList<>();

    public MissEffect(Materials materials) {
        for (int i = 0; i < 8; i++) {
            Geometry drop = EffectVisuals.sphere(node, materials, 0xD6F4FF, 0.031f, true);
            drop.setLocalScale(0.55f, 1.8f, 0.55f);
            droplets.add(drop);
        }
        for (int i = 0; i < 3; i++) {
            ripples.add(EffectVisuals.ring(node, materials, 0xA1DFF2, 0.17f, 0.012f));
        }
        update(0);
    }

    public Node node() { return node; }

    public void update(float elapsed) {
        float lifetime = AttackEffectManager.TOTAL_DURATION - AttackEffectManager.IMPACT_TIME;
        float progress = Math.min(1, elapsed / lifetime);
        for (int i = 0; i < droplets.size(); i++) {
            Geometry drop = droplets.get(i);
            Vector3f position = EffectVisuals.direction(i, droplets.size()).mult(elapsed * 0.48f);
            position.y = 0.04f + elapsed * (0.85f + (i % 3) * 0.12f) - elapsed * elapsed * 1.3f;
            drop.setLocalTranslation(position);
            EffectVisuals.fade(drop, Math.max(0, 1 - progress * 1.7f) * 0.85f);
        }
        for (int i = 0; i < ripples.size(); i++) {
            Geometry ripple = ripples.get(i);
            float age = Math.max(0, elapsed - i * 0.11f);
            ripple.setLocalTranslation(0, i * 0.007f, 0);
            ripple.setLocalScale(1 + age * 3.8f);
            EffectVisuals.fade(ripple, (1 - progress) * Math.min(1, age * 10) * 0.65f);
        }
    }
}
