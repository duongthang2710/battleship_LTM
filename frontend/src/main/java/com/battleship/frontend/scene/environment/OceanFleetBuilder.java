package com.battleship.frontend.scene.environment;

import com.battleship.frontend.graphics.Materials;
import com.battleship.frontend.graphics.ship.ShipFactory;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector4f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Node;
import java.util.ArrayList;
import java.util.List;

final class OceanFleetBuilder {
    private final Node root = new Node("Ocean display fleet");
    private final List<Node> vessels = new ArrayList<>();
    private final Vector4f[] wakeSources;
    private final float waterline;
    private final Quaternion rocking = new Quaternion();

    OceanFleetBuilder(Materials materials, float waterline) {
        this.waterline = waterline;
        ShipFactory factory = new ShipFactory(materials);
        // Paired escorts stay on their faction's side of the board.
        float[][] berths = {{-22,-10,0.85f,1.4f}, {23,-11,-0.85f,1.4f},
                {-30,-32,0.50f,1.25f}, {29,-34,-0.55f,1.25f},
                {-7,-40,0.35f,1.1f}, {8,-47,-0.35f,1.1f},
                {-26,15,2.10f,1.42f}, {26,15,-2.10f,1.42f},
                {-48,32,2.18f,1.26f}, {48,32,-2.18f,1.26f},
                {-67,-24,1.22f,1.36f}, {67,-24,-1.22f,1.36f}};
        wakeSources = new Vector4f[berths.length];
        for (int i = 0; i < berths.length; i++) {
            float[] berth = berths[i];
            Node anchor = new Node("Ocean vessel " + i);
            anchor.setLocalTranslation(berth[0], 0, berth[1]);
            anchor.rotate(0, berth[2], 0);
            Node vessel = factory.createOceanVessel(i % 2 == 0
                    ? ShipFactory.Fleet.RADIANT : ShipFactory.Fleet.ABYSSAL);
            float scale = berth[3] * 1.18f;
            vessel.setLocalScale(scale);
            vessel.setLocalTranslation(0, waterline + 0.12f, 0);
            for (int side : new int[] {-1, 1}) {
                Node muzzle = new Node("Ocean broadside muzzle");
                muzzle.setUserData("firingSide", i % 2 == 0 ? "WHITE" : "BLACK");
                muzzle.setLocalTranslation(side * .68f, .43f, -.20f);
                vessel.attachChild(muzzle);
            }
            vessel.setShadowMode(RenderQueue.ShadowMode.Off);
            anchor.attachChild(vessel);
            vessels.add(vessel);
            wakeSources[i] = new Vector4f(berth[0], berth[1], berth[2], scale);
            root.attachChild(anchor);
        }
    }

    Node root() { return root; }
    Vector4f[] wakeSources() { return wakeSources; }

    void update(float time) {
        for (int i = 0; i < vessels.size(); i++) {
            Node vessel = vessels.get(i);
            vessel.setLocalTranslation(0, waterline + 0.12f
                    + FastMath.sin(time * 0.53f + i * 1.7f) * 0.06f, 0);
            vessel.setLocalRotation(rocking.fromAngles(
                    FastMath.sin(time * 0.42f + i) * 0.008f, 0,
                    FastMath.sin(time * 0.58f + i * 0.8f) * 0.012f));
        }
    }
}
