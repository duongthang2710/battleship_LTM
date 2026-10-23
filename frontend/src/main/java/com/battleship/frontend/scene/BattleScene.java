package com.battleship.frontend.scene;

import com.battleship.frontend.effects.HitMissEffects;
import com.battleship.frontend.graphics.Materials;
import com.battleship.frontend.graphics.ship.ShipFactory;
import com.battleship.frontend.scene.board.BoardBuilder;
import com.battleship.frontend.scene.environment.LayeredEnvironment;
import com.battleship.frontend.scene.environment.OceanSurface;
import com.jme3.asset.AssetManager;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.light.PointLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Node;
import java.util.ArrayList;
import java.util.List;

public final class BattleScene {
    private final Node root = new Node("Battle scene");
    private final Node boardPresentation = new Node("Board presentation");
    private final OceanSurface boardWater;
    private final LayeredEnvironment environment;
    private final HitMissEffects markers;
    private final List<Node> ships = new ArrayList<>();
    private final DirectionalLight keyLight = new DirectionalLight();
    private final Quaternion rocking = new Quaternion();
    private float time;

    public BattleScene(AssetManager assets) {
        Materials materials = new Materials(assets);
        environment = new LayeredEnvironment(assets, materials);
        boardWater = new OceanSurface(assets, 10, 100, 0.15f, true);
        root.attachChild(environment.root());
        root.attachChild(boardPresentation);
        boardPresentation.attachChild(boardWater.geometry());
        Node board = new BoardBuilder(materials).create(assets.loadFont("fonts/CormorantGaramond.fnt"));
        boardPresentation.attachChild(board);
        // Refresh parent flags before measuring the board so later lights reach its batches.
        root.updateGeometricState();
        environment.placeCastles(assets, board);
        boardPresentation.setLocalScale(1.3f);
        boardPresentation.setLocalTranslation(0, 0.52f, 0);
        ShipFactory factory = new ShipFactory(materials);
        addShip(factory, ShipFactory.Fleet.RADIANT, -2.5f, -1.8f, 0.70f, 0.04f);
        addShip(factory, ShipFactory.Fleet.RADIANT, -3.5f, 2.7f, 0.57f, -0.08f);
        addShip(factory, ShipFactory.Fleet.RADIANT, -0.5f, 2.0f, 0.63f, -0.68f);
        addShip(factory, ShipFactory.Fleet.ABYSSAL, 2.5f, -2.2f, 0.73f, FastMath.PI);
        addShip(factory, ShipFactory.Fleet.ABYSSAL, 2.5f, 1.7f, 0.66f, FastMath.PI + 0.13f);
        markers = new HitMissEffects(materials);
        boardPresentation.attachChild(markers.root());

        keyLight.setDirection(new Vector3f(0.38f, -0.86f, 0.33f).normalizeLocal());
        keyLight.setColor(new ColorRGBA(1.5f, 1.18f, 0.78f, 1));
        root.addLight(keyLight);
        AmbientLight ambient = new AmbientLight();
        ambient.setColor(new ColorRGBA(0.12f, 0.15f, 0.26f, 1));
        root.addLight(ambient);
        DirectionalLight rim = new DirectionalLight();
        rim.setDirection(new Vector3f(-0.65f, -0.40f, -0.7f).normalizeLocal());
        rim.setColor(new ColorRGBA(0.20f, 0.34f, 0.85f, 1));
        root.addLight(rim);
        PointLight radiant = new PointLight();
        radiant.setPosition(new Vector3f(-5.2f, 5.2f, 0));
        radiant.setColor(new ColorRGBA(0.08f, 0.30f, 0.72f, 1));
        radiant.setRadius(18.2f);
        root.addLight(radiant);
        PointLight abyssal = new PointLight();
        abyssal.setPosition(new Vector3f(5.2f, 3.9f, 0));
        abyssal.setColor(new ColorRGBA(0.52f, 0.06f, 0.85f, 1));
        abyssal.setRadius(16.9f);
        root.addLight(abyssal);
    }

    private void addShip(ShipFactory factory, ShipFactory.Fleet fleet, float x, float z, float scale, float yaw) {
        Node placement = new Node("Fleet display placement");
        placement.setLocalTranslation(x, 0.20f, z);
        placement.rotate(0, yaw, 0);
        Node ship = factory.create(fleet);
        ship.setLocalScale(scale);
        placement.attachChild(ship);
        boardPresentation.attachChild(placement);
        ships.add(ship);
    }

    public Node root() { return root; }
    public Node beginLocalGameplay() {
        for (Node ship : ships) ship.getParent().removeFromParent();
        ships.clear();
        markers.root().removeFromParent();
        return (Node) boardPresentation.getChild("Collector edition battle board");
    }
    public DirectionalLight keyLight() { return keyLight; }
    public void installOceanReflections(ViewPort viewport) { environment.installReflections(viewport); }

    public void update(float delta, Camera camera) {
        time += delta;
        environment.update(time, camera);
        boardWater.update(time);
        if (markers.root().getParent() != null) markers.update(time);
        for (int i = 0; i < ships.size(); i++) {
            float phase = time * 0.55f + i * 0.7f;
            ships.get(i).setLocalTranslation(0, FastMath.sin(phase) * 0.018f, 0);
            rocking.fromAngles(FastMath.sin(phase) * 0.01f, 0, FastMath.cos(phase) * 0.015f);
            ships.get(i).setLocalRotation(rocking);
        }
    }
}
