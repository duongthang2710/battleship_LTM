package com.battleship.frontend.effects;

import com.battleship.shared.game.Side;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Samples the real, rocking ocean vessel's cannon transform at the moment of firing. */
final class OceanGunfire {
    private final Node board;
    private final Node scene;
    private final List<Node> muzzles = new ArrayList<>();

    OceanGunfire(Node board) {
        this.board = board;
        Node ancestor = board;
        while (ancestor.getParent() != null) ancestor = ancestor.getParent();
        scene = ancestor;
        scene.depthFirstTraversal(spatial -> {
            if (spatial instanceof Node node && node.getUserData("firingSide") != null) muzzles.add(node);
        });
    }

    Vector3f source(Side side, Vector3f boardTarget) {
        scene.updateGeometricState();
        Vector3f target = board.localToWorld(boardTarget, new Vector3f());
        return muzzles.stream().filter(node -> side.name().equals(node.getUserData("firingSide")))
                .min(Comparator.comparingDouble(node -> node.getWorldTranslation().distanceSquared(target)))
                .map(node -> board.worldToLocal(node.getWorldTranslation(), new Vector3f()))
                .orElseGet(() -> new Vector3f(0, .38f, side == Side.WHITE ? 7.5f : -7.5f));
    }
}
