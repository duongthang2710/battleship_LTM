package com.battleship.frontend.game;

import com.battleship.frontend.graphics.Materials;
import com.battleship.frontend.graphics.ship.BoardShipBuilder;
import com.battleship.frontend.graphics.ship.ShipFactory;
import com.battleship.shared.game.Board;
import com.battleship.shared.game.Coordinate;
import com.battleship.shared.game.Ship;
import com.battleship.shared.game.ShipOrientation;
import com.battleship.shared.game.ShipType;
import com.jme3.bounding.BoundingBox;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import java.util.EnumMap;
import java.util.Map;

public final class FleetRenderer {
    private final Node root = new Node("Gameplay fleet");
    private final BoardCoordinates coordinates;
    private final BoardShipBuilder factory;
    private final Map<ShipType, Node> models = new EnumMap<>(ShipType.class);
    private ShipFactory.Fleet displayedFleet;

    public FleetRenderer(Node board, Materials materials) {
        board.attachChild(root);
        coordinates = new BoardCoordinates(board);
        factory = new BoardShipBuilder(materials);
    }

    public Node root() { return root; }

    public void display(Board board, boolean showShips, ShipFactory.Fleet fleet) {
        if (displayedFleet != fleet) {
            clear();
            models.clear();
            displayedFleet = fleet;
        }
        for (ShipType type : ShipType.values()) {
            Ship ship = board.ship(type);
            Node model = models.get(type);
            if (ship == null || (!showShips && !ship.isSunk())) {
                if (model != null) model.removeFromParent();
                continue;
            }
            if (model == null) {
                model = createModel(type, fleet);
                models.put(type, model);
            }
            positionModel(model, ship);
            if (model.getParent() != root) root.attachChild(model);
        }
    }

    public void previewPlacement(ShipType type, Coordinate position, ShipOrientation orientation) {
        Node model = models.get(type);
        if (model != null) positionModel(model, new Ship(type, position, orientation));
    }

    private void positionModel(Node model, Ship ship) {
        Vector3f center = new Vector3f();
        for (Coordinate cell : ship.occupiedCells()) center.addLocal(coordinates.localCenter(cell, 0.235f));
        center.divideLocal(ship.occupiedCells().size());
        model.setLocalTranslation(center);
        model.setLocalRotation(new Quaternion().fromAngleAxis(
                ship.orientation() == ShipOrientation.HORIZONTAL ? FastMath.PI : FastMath.HALF_PI,
                Vector3f.UNIT_Y));
    }

    private Node createModel(ShipType type, ShipFactory.Fleet fleet) {
        Node placement = new Node(type + " grid placement");
        placement.setUserData("shipType", type.name());
        Node ship = factory.create(type, fleet);
        ship.updateGeometricState();
        BoundingBox bounds = (BoundingBox) ship.getWorldBound();
        float scale = Math.min(1f, Math.min(0.86f / (bounds.getXExtent() * 2),
                (type.length() - 0.12f) / (bounds.getZExtent() * 2)));
        float heightScale = scale * 1.5f;
        Vector3f center = bounds.getCenter();
        ship.setLocalScale(scale, heightScale, scale);
        ship.setLocalTranslation(-center.x * scale,
                -(center.y - bounds.getYExtent()) * heightScale, -center.z * scale);
        placement.attachChild(ship);
        return placement;
    }

    public void clear() { root.detachAllChildren(); }
}
