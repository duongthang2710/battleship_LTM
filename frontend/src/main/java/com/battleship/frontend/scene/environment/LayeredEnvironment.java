package com.battleship.frontend.scene.environment;

import com.battleship.frontend.graphics.Materials;
import com.jme3.asset.AssetManager;
import com.jme3.bounding.BoundingBox;
import com.jme3.renderer.Camera;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Node;

public final class LayeredEnvironment {
    private final Node root = new Node("World-space fantasy ocean");
    private final OceanSurface ocean;
    private final OceanFleetBuilder fleet;
    private CastleEnvironmentBuilder castles;

    public LayeredEnvironment(AssetManager assets, Materials materials) {
        ocean = new OceanSurface(assets, 3000, 240, -0.94f, false);
        root.attachChild(ocean.geometry());
        root.attachChild(new SkyBuilder(assets).create());
        root.attachChild(new CoastlineBuilder(assets, materials, ocean.geometry().getLocalTranslation().y).root());
        root.attachChild(new BackdropLayerBuilder(assets, ocean.geometry().getLocalTranslation().y).root());
        fleet = new OceanFleetBuilder(materials, ocean.geometry().getLocalTranslation().y);
        root.attachChild(fleet.root());
        ocean.setWakeSources(fleet.wakeSources());
    }

    public void placeCastles(AssetManager assets, Node board) {
        board.updateGeometricState();
        castles = new CastleEnvironmentBuilder(assets, (BoundingBox) board.getWorldBound(),
                ocean.geometry().getLocalTranslation().y);
        root.attachChild(castles.root());
        ocean.setReflectionSources(castles.root().getChild(0).getLocalTranslation(),
                castles.root().getChild(1).getLocalTranslation());
    }

    public Node root() { return root; }

    public void installReflections(ViewPort viewport) {
        viewport.addProcessor(new OceanReflectionProcessor(root, ocean.geometry()));
    }

    public void update(float time, Camera camera) {
        ocean.update(time);
        castles.update(camera);
        fleet.update(time);
    }
}
