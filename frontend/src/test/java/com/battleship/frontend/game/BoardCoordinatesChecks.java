package com.battleship.frontend.game;

import com.battleship.shared.game.Coordinate;
import com.jme3.math.FastMath;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;

/** Runs without a display: verifies the transformed grid and actual ray/surface intersection. */
public final class BoardCoordinatesChecks {
    public static void main(String[] args) {
        Node presentation = new Node("Board presentation");
        presentation.setLocalScale(1.3f);
        presentation.setLocalTranslation(0, 0.52f, 0);
        Node board = new Node("Collector edition battle board");
        board.rotate(0, FastMath.HALF_PI, 0);
        presentation.attachChild(board);
        Geometry surface = new Geometry("Crystalline playing surface", new Quad(10, 10));
        surface.rotate(-FastMath.HALF_PI, 0, 0);
        surface.setLocalTranslation(-5, 0.185f, 5);
        board.attachChild(surface);
        presentation.updateGeometricState();
        BoardCoordinates coordinates = new BoardCoordinates(board);
        Vector3f a1 = coordinates.worldCenter(new Coordinate(0, 0), 0.185f);
        require(a1.distance(new Vector3f(5.85f, 0.7605f, 5.85f)) < 0.001f, "A1 must match printed labels");
        for (int row = 0; row < 10; row++) {
            for (int column = 0; column < 10; column++) {
                Coordinate cell = new Coordinate(row, column);
                require(coordinates.cellAtWorld(coordinates.worldCenter(cell, 0.185f)).orElseThrow().equals(cell),
                        "Coordinate round trip " + cell);
            }
        }
        for (Vector3f outside : new Vector3f[] {
                new Vector3f(-5.01f, 0.185f, 0), new Vector3f(5.01f, 0.185f, 0),
                new Vector3f(0, 0.185f, -5.01f), new Vector3f(0, 0.185f, 5.01f)}) {
            require(coordinates.cellAtWorld(board.localToWorld(outside, new Vector3f())).isEmpty(), "Outside grid");
        }
        for (Vector3f eye : new Vector3f[] {
                new Vector3f(0, 12, 20), new Vector3f(20, 12, 0), new Vector3f(0, 25, 0)}) {
            Camera camera = new Camera(1440, 900);
            camera.setFrustumPerspective(45, 1440f / 900, 0.1f, 200);
            camera.setLocation(eye);
            camera.lookAt(new Vector3f(0, 0.7605f, 0), eye.z == 0 && eye.x == 0 ? Vector3f.UNIT_Z : Vector3f.UNIT_Y);
            camera.update();
            BoardPicker picker = new BoardPicker(camera, null, board);
            for (int row = 0; row < 10; row++) {
                for (int column = 0; column < 10; column++) {
                    Coordinate cell = new Coordinate(row, column);
                    Vector3f screen = camera.getScreenCoordinates(coordinates.worldCenter(cell, 0.185f));
                    var picked = picker.pick(new Vector2f(screen.x, screen.y));
                    require(picked.isPresent() && picked.get().equals(cell),
                            "Orbit raycast " + eye + " cell " + cell + " picked " + picked
                            + " target " + coordinates.worldCenter(cell, 0.185f) + " screen " + screen
                            + " near " + camera.getWorldCoordinates(new Vector2f(screen.x, screen.y), 0)
                            + " far " + camera.getWorldCoordinates(new Vector2f(screen.x, screen.y), 1)
                            + " surface bounds " + surface.getWorldBound());
                }
            }
        }
        System.out.println("Board coordinates: all 100 cells, bounds and 300 camera raycasts passed.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
