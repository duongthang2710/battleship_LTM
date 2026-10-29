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
    private final List<Geometry> jets = new ArrayList<>();

    public MissEffect(Materials materials) {
        for (int i = 0; i < 3; i++) jets.add(EffectVisuals.sphere(node, materials, i == 0 ? 0xF0FBFF : 0x92CEE9, .075f, false));
        for (int i = 0; i < 14; i++) {
            Geometry drop = EffectVisuals.sphere(node, materials, 0xD6F4FF, 0.043f, false);
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
        float lifetime = AttackEffectManager.IMPACT_LIFETIME;
        float progress = Math.min(1, elapsed / lifetime);
        for (int i = 0; i < jets.size(); i++) {
            float rise = Math.max(0, 1 - elapsed / .50f);
            Geometry jet = jets.get(i);
            jet.setLocalTranslation((i - 1) * elapsed * .13f, .12f + elapsed * .28f, (i % 2) * .05f);
            jet.setLocalScale(.65f + elapsed * 1.7f, rise * (i == 1 ? 6 : 4), .65f + elapsed * 1.7f);
            EffectVisuals.fade(jet, rise * .80f);
        }
        for (int i = 0; i < droplets.size(); i++) {
            Geometry drop = droplets.get(i);
            Vector3f position = EffectVisuals.direction(i, droplets.size()).mult(elapsed * 0.62f);
            position.y = 0.08f + elapsed * (1.4f + (i % 3) * 0.16f) - elapsed * elapsed * 1.9f;
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
