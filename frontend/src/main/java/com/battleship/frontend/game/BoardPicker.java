package com.battleship.frontend.game;

import com.battleship.shared.game.Coordinate;
import com.battleship.shared.game.ShipType;
import com.jme3.collision.CollisionResult;
import com.jme3.collision.CollisionResults;
import com.jme3.input.InputManager;
import com.jme3.math.Plane;
import com.jme3.math.Ray;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.Optional;

public final class BoardPicker {
    public record ShipHit(ShipType type, Coordinate cell, float height) { }

    private final Camera camera;
    private final InputManager input;
    private final Node board;
    private final Geometry surface;
    private final BoardCoordinates coordinates;

    public BoardPicker(Camera camera, InputManager input, Node board) {
        this.camera = camera;
        this.input = input;
        this.board = board;
        surface = (Geometry) board.getChild("Crystalline playing surface");
        if (surface == null) throw new IllegalArgumentException("Board has no playing surface");
        coordinates = new BoardCoordinates(board);
    }

    public Optional<Coordinate> pick() { return pick(input.getCursorPosition()); }

    public Optional<Coordinate> pick(Vector2f cursor) {
        return pickPlane(cursor, surface.getWorldTranslation());
    }

    public Optional<Coordinate> pickAtHeight(Vector2f cursor, float localHeight) {
        return pickPlane(cursor, coordinates.worldCenter(new Coordinate(0, 0), localHeight));
    }

    private Optional<Coordinate> pickPlane(Vector2f cursor, Vector3f origin) {
        Ray ray = ray(cursor);
        Vector3f normal = surface.getWorldRotation().mult(Vector3f.UNIT_Z);
        Plane plane = new Plane(normal, normal.dot(origin));
        Vector3f contact = new Vector3f();
        // The real surface plane avoids zero-thickness triangle precision gaps at the overhead view.
        if (!ray.intersectsWherePlane(plane, contact)) return Optional.empty();
        return coordinates.cellAtWorld(contact);
    }

    public Optional<ShipHit> pickShip(Vector2f cursor, Node fleetRoot) {
        CollisionResults hits = new CollisionResults();
        fleetRoot.collideWith(ray(cursor), hits);
        for (CollisionResult hit : hits) {
            for (Spatial part = hit.getGeometry(); part != null; part = part.getParent()) {
                String type = part.getUserData("shipType");
                if (type != null) {
                    // Use the actual geometry contact footprint even when a tall sail was clicked.
                    float height = board.worldToLocal(hit.getContactPoint(), new Vector3f()).y;
                    return coordinates.cellAtWorld(hit.getContactPoint())
                            .map(cell -> new ShipHit(ShipType.valueOf(type), cell, height));
                }
                if (part == fleetRoot) break;
            }
        }
        return Optional.empty();
    }

    private Ray ray(Vector2f cursor) {
        Vector3f near = camera.getWorldCoordinates(cursor, 0);
        Vector3f far = camera.getWorldCoordinates(cursor, 1);
        return new Ray(near, far.subtract(near).normalizeLocal());
    }

    public BoardCoordinates coordinates() { return coordinates; }
}
