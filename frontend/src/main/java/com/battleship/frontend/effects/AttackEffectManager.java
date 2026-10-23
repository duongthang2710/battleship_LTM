package com.battleship.frontend.effects;

import com.battleship.frontend.graphics.Materials;
import com.battleship.frontend.game.BoardCoordinates;
import com.battleship.shared.game.AttackResult;
import com.battleship.shared.game.AttackResultType;
import com.battleship.shared.game.Coordinate;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import java.util.ArrayList;
import java.util.List;

/** Visual timing only: the shared engine has already determined the attack result. */
public final class AttackEffectManager {
    public static final float AIM_DURATION = 0.16f;
    public static final float IMPACT_TIME = 0.72f;
    public static final float TOTAL_DURATION = 1.70f;

    private final Node node = new Node("Local attack effects");
    private final Materials materials;
    private final BoardCoordinates coordinates;
    private final List<Geometry> sunkRings = new ArrayList<>();
    private ProjectileEffect projectile;
    private HitEffect hit;
    private MissEffect miss;
    private Geometry targetRing;
    private AttackResult result;
    private List<Coordinate> sunkCells = List.of();
    private Runnable onImpact;
    private Runnable onComplete;
    private float elapsed;
    private boolean impacted;

    public AttackEffectManager(Node board, Materials materials) {
        this.materials = materials;
        coordinates = new BoardCoordinates(board);
        board.attachChild(node);
    }

    public boolean isBusy() { return result != null; }

    public void play(AttackResult result, List<Coordinate> sunkCells, Runnable onImpact, Runnable onComplete) {
        if (isBusy() || !result.accepted()) return;
        this.result = result;
        this.sunkCells = List.copyOf(sunkCells);
        this.onImpact = onImpact;
        this.onComplete = onComplete;
        elapsed = 0;
        impacted = false;
        Vector3f target = coordinates.localCenter(result.coordinate(), 0.247f);
        targetRing = EffectVisuals.ring(node, materials, 0xD5E9BA, 0.33f, 0.013f);
        targetRing.setLocalTranslation(target);
        // Launch from the near edge belonging to the firing side, never screen coordinates.
        boolean radiant = "player".equals(result.attackerId());
        Vector3f source = new Vector3f(target.x * 0.35f, 0.38f, radiant ? 5.25f : -5.25f);
        projectile = new ProjectileEffect(materials, source, target, radiant);
        node.attachChild(projectile.node());
        update(0);
    }

    public void update(float dt) {
        if (!isBusy()) return;
        elapsed += Math.max(0, dt);
        projectile.update(elapsed);
        targetRing.setLocalScale(1.08f - Math.min(1, elapsed / IMPACT_TIME) * 0.14f);
        EffectVisuals.fade(targetRing, elapsed < IMPACT_TIME ? 0.65f : Math.max(0, 1 - (elapsed - IMPACT_TIME) * 5));
        if (!impacted && elapsed >= IMPACT_TIME) {
            impacted = true;
            impact();
            if (onImpact != null) onImpact.run();
            // A callback may cancel the animation while replacing the match.
            if (!isBusy()) return;
        }
        float impactAge = Math.max(0, elapsed - IMPACT_TIME);
        if (hit != null) hit.update(impactAge);
        if (miss != null) miss.update(impactAge);
        for (Geometry ring : sunkRings) {
            ring.setLocalScale(1 + impactAge * 0.12f);
            EffectVisuals.fade(ring, Math.max(0, 1 - impactAge / (TOTAL_DURATION - IMPACT_TIME)) * 0.72f);
        }
        if (elapsed >= TOTAL_DURATION) {
            Runnable complete = onComplete;
            clear();
            if (complete != null) complete.run();
        }
    }

    private void impact() {
        Vector3f target = coordinates.localCenter(result.coordinate(), 0.255f);
        if (result.type() == AttackResultType.MISS) {
            miss = new MissEffect(materials);
            miss.node().setLocalTranslation(target);
            node.attachChild(miss.node());
        } else {
            hit = new HitEffect(materials, result.sunk());
            hit.node().setLocalTranslation(target);
            node.attachChild(hit.node());
        }
        if (result.sunk()) {
            for (Coordinate cell : sunkCells) {
                Geometry ring = EffectVisuals.ring(node, materials, 0xF0D392, 0.37f, 0.017f);
                ring.setLocalTranslation(coordinates.localCenter(cell, 0.263f));
                sunkRings.add(ring);
            }
        }
    }

    public void clear() {
        node.detachAllChildren();
        sunkRings.clear();
        result = null;
        sunkCells = List.of();
        projectile = null;
        hit = null;
        miss = null;
        targetRing = null;
        onImpact = null;
        onComplete = null;
        elapsed = 0;
        impacted = false;
    }
}
