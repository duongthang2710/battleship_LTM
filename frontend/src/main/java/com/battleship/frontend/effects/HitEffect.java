package com.battleship.frontend.effects;

import com.battleship.frontend.graphics.Materials;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import java.util.ArrayList;
import java.util.List;

public final class HitEffect {
    private final Node node = new Node("Hit explosion");
    private final Geometry flash;
    private final List<Geometry> sparks = new ArrayList<>();
    private final List<Geometry> smoke = new ArrayList<>();
    private final List<Geometry> ripples = new ArrayList<>();
    private final List<Geometry> fireballs = new ArrayList<>();
    private final float strength;

    public HitEffect(Materials materials, boolean sunk) {
        strength = sunk ? 1.3f : 1f;
        flash = EffectVisuals.sphere(node, materials, 0xFFF1C7, 0.23f, true);
        for (int i = 0; i < 6; i++) fireballs.add(EffectVisuals.sphere(node, materials,
                i % 2 == 0 ? 0xFFB049 : 0xF16A27, .13f, true));
        for (int i = 0; i < 16; i++) {
            Geometry spark = EffectVisuals.sphere(node, materials,
                    i % 2 == 0 ? 0xFFD287 : 0xE89955, 0.032f, true);
            sparks.add(spark);
        }
        for (int i = 0; i < 7; i++) smoke.add(EffectVisuals.sphere(node, materials, 0x74808B, 0.23f, false));
        for (int i = 0; i < 2; i++) {
            ripples.add(EffectVisuals.ring(node, materials, 0xABDCEC, 0.20f, 0.013f));
        }
        update(0);
    }

    public Node node() { return node; }

    public void update(float elapsed) {
        float lifetime = AttackEffectManager.IMPACT_LIFETIME;
        float progress = Math.min(1, elapsed / lifetime);
        flash.setLocalTranslation(0, 0.25f, 0);
        flash.setLocalScale(strength * (0.5f + Math.min(elapsed * 8, 1)));
        EffectVisuals.fade(flash, Math.max(0, 1 - elapsed / 0.24f) * 0.8f);
        for (int i = 0; i < fireballs.size(); i++) {
            Geometry flame = fireballs.get(i);
            Vector3f direction = EffectVisuals.direction(i, fireballs.size());
            flame.setLocalTranslation(direction.mult(.08f + elapsed * .36f).addLocal(0, .16f + elapsed * .30f, 0));
            flame.setLocalScale(strength * (.55f + Math.min(elapsed * 7, 1.2f)));
            EffectVisuals.fade(flame, Math.max(0, 1 - elapsed / .55f) * .9f);
        }
        for (int i = 0; i < sparks.size(); i++) {
            float speed = strength * (0.85f + (i % 3) * 0.16f);
            Vector3f position = EffectVisuals.direction(i, sparks.size()).mult(elapsed * speed);
            position.y = 0.12f + elapsed * (1.1f + (i % 4) * 0.20f) - elapsed * elapsed * 1.1f;
            sparks.get(i).setLocalTranslation(position);
            EffectVisuals.fade(sparks.get(i), Math.max(0, 1 - progress * 1.4f));
        }
        for (int i = 0; i < smoke.size(); i++) {
            Geometry puff = smoke.get(i);
            float age = Math.max(0, elapsed - i * .045f);
            float smokeProgress = Math.min(1, age / (AttackEffectManager.TOTAL_DURATION - AttackEffectManager.IMPACT_TIME));
            Vector3f drift = EffectVisuals.direction(i, smoke.size()).mult(age * .14f);
            puff.setLocalTranslation(drift.x, .32f + age * .72f + (i % 3) * .08f, drift.z);
            puff.setLocalScale(strength * (.65f + smokeProgress * 2.1f));
            EffectVisuals.fade(puff, (1 - smokeProgress) * Math.min(1, age * 5) * .62f);
        }
        for (int i = 0; i < ripples.size(); i++) {
            Geometry ripple = ripples.get(i);
            ripple.setLocalTranslation(0, i * 0.008f, 0);
            ripple.setLocalScale(1 + Math.max(0, elapsed - i * 0.09f) * 3.2f);
            EffectVisuals.fade(ripple, (1 - progress) * 0.42f);
        }
    }
}
