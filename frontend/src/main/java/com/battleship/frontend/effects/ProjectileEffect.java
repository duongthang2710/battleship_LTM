package com.battleship.frontend.effects;

import com.battleship.frontend.graphics.Materials;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial.CullHint;
import java.util.ArrayList;
import java.util.List;

public final class ProjectileEffect {
    private final Node node = new Node("Board-local projectile");
    private final Geometry core;
    private final List<Geometry> trail = new ArrayList<>();
    private final Vector3f start;
    private final Vector3f target;

    public ProjectileEffect(Materials materials, Vector3f start, Vector3f target, boolean radiant) {
        this.start = start.clone();
        this.target = target.clone();
        core = EffectVisuals.sphere(node, materials, radiant ? 0xFFF4C5 : 0xECD5FF, 0.055f, true);
        for (int i = 0; i < 7; i++) {
            Geometry particle = EffectVisuals.sphere(node, materials, radiant ? 0x8DDFFF : 0xBC86EC, 0.040f, true);
            particle.setLocalScale(1 - i * 0.10f);
            trail.add(particle);
        }
        node.setCullHint(CullHint.Always);
    }

    public Node node() { return node; }

    public void update(float elapsed) {
        if (elapsed < AttackEffectManager.AIM_DURATION || elapsed >= AttackEffectManager.IMPACT_TIME) {
            node.setCullHint(CullHint.Always);
            return;
        }
        node.setCullHint(CullHint.Inherit);
        float progress = (elapsed - AttackEffectManager.AIM_DURATION)
                / (AttackEffectManager.IMPACT_TIME - AttackEffectManager.AIM_DURATION);
        core.setLocalTranslation(path(progress));
        for (int i = 0; i < trail.size(); i++) {
            Geometry particle = trail.get(i);
            particle.setLocalTranslation(path(Math.max(0, progress - (i + 1) * 0.032f)));
            EffectVisuals.fade(particle, (1 - i / 8f) * Math.min(1, progress * 12));
        }
    }

    private Vector3f path(float progress) {
        Vector3f position = new Vector3f().interpolateLocal(start, target, progress);
        position.y += FastMath.sin(progress * FastMath.PI) * 2.4f;
        return position;
    }
}
