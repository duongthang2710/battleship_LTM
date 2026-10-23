package com.battleship.frontend.game;

import com.battleship.frontend.graphics.Materials;
import com.battleship.shared.game.Board;
import com.battleship.shared.game.CellState;
import com.battleship.shared.game.Coordinate;
import com.battleship.shared.game.Ship;
import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector2f;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial.CullHint;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.scene.shape.Quad;
import java.util.EnumMap;
import java.util.Map;

public final class OwnBoardMinimap {
    private static final float CELL = 20;
    private static final float SIZE = CELL * Board.SIZE;
    private static final int GOLD = 0xDEB663;
    private final Node root = new Node("Own board minimap");
    private final Node layout = new Node("Own waters layout");
    private final Node fleet = new Node("Minimap fleet");
    private final Materials materials;
    private final Material surface;
    private final Material hullGold;
    private final Material hullBlue;
    private final Material deck;
    private final Material sunkHull;
    private final Map<CellState, Material> fills = new EnumMap<>(CellState.class);
    private final Geometry[][] cells = new Geometry[Board.SIZE][Board.SIZE];
    private final Node[][] markers = new Node[Board.SIZE][Board.SIZE];
    private Board displayedBoard;
    private float scale = 1;
    private float left;
    private final float bottom = 170;
    private com.battleship.shared.game.Side side;

    public OwnBoardMinimap(AssetManager assets) {
        materials = new Materials(assets);
        surface = new Material(assets, "materials/MinimapSurface.j3md");
        hullGold = materials.flat(Materials.linear(0xD1B172));
        hullBlue = materials.flat(Materials.linear(0x80B8C8));
        deck = materials.flat(Materials.linear(0x274C63));
        sunkHull = materials.flat(Materials.linear(0x535066));
        fills.put(CellState.EMPTY, materials.flat(Materials.rgb(0x071F38)));
        fills.put(CellState.SHIP, materials.flat(Materials.rgb(0x397B97)));
        fills.put(CellState.HIT, materials.flat(Materials.rgb(0x743440)));
        fills.put(CellState.MISS, fills.get(CellState.EMPTY));
        fills.put(CellState.SUNK, materials.flat(Materials.rgb(0x4D253A)));
        root.attachChild(layout);
    }

    public Node root() { return root; }

    public void setSide(com.battleship.shared.game.Side side) {
        this.side = side;
        hullBlue.setColor("Color", Materials.linear(side == com.battleship.shared.game.Side.BLACK ? 0x252936
                : side == com.battleship.shared.game.Side.WHITE ? 0xEDE8DE : 0x80B8C8));
        position();
    }

    private void position() {
        boolean reversed = side == com.battleship.shared.game.Side.WHITE;
        layout.setLocalRotation(new com.jme3.math.Quaternion().fromAngles(0, 0, reversed ? FastMath.PI : 0));
        layout.setLocalTranslation((left + (reversed ? SIZE : 0)) * scale, (bottom + (reversed ? SIZE : 0)) * scale, 0);
    }

    public void resize(int width, int height) {
        scale = Math.min(width / 1440f, height / 900f);
        left = width / scale - 60 - SIZE;
        layout.setLocalScale(scale);
        position();
        layout.detachAllChildren();
        quad(layout, "Crystalline minimap grid", 0, 0, SIZE, SIZE, 19, surface);
        for (int row = 0; row < Board.SIZE; row++) {
            for (int column = 0; column < Board.SIZE; column++) {
                // A1 is nearest-left at the default camera: a 90-degree counterclockwise turn.
                float x = row * CELL, y = column * CELL;
                cells[row][column] = quad(layout, "Own cell " + row + ":" + column,
                        x + 0.35f, y + 0.35f, CELL - 0.7f, CELL - 0.7f, 20, fills.get(CellState.EMPTY));
                Node marker = new Node("Own cell marker " + row + ":" + column);
                marker.setLocalTranslation(x + CELL / 2, y + CELL / 2, 24);
                layout.attachChild(marker);
                markers[row][column] = marker;
            }
        }
        layout.attachChild(fleet);
        if (displayedBoard != null) renderBoard();
    }

    public void update(Board ownBoard) {
        if (ownBoard == displayedBoard) return;
        displayedBoard = ownBoard;
        if (cells[0][0] != null) renderBoard();
    }

    public boolean contains(Vector2f screen) {
        float x = screen.x / scale, y = screen.y / scale;
        return cells[0][0] != null && x >= left && x <= left + SIZE
                && y >= bottom && y <= bottom + SIZE;
    }

    private void renderBoard() {
        for (int row = 0; row < Board.SIZE; row++) {
            for (int column = 0; column < Board.SIZE; column++) {
                CellState state = displayedBoard.cell(new Coordinate(row, column)).state();
                cells[row][column].setMaterial(fills.get(state));
                cells[row][column].setCullHint(state == CellState.HIT || state == CellState.SUNK
                        ? CullHint.Inherit : CullHint.Always);
                Node marker = markers[row][column];
                marker.detachAllChildren();
                switch (state) {
                    case HIT, SUNK -> {
                        int color = state == CellState.SUNK ? GOLD : 0xF5AE60;
                        line(marker, -4, -4, 4, 4, color, 0);
                        line(marker, -4, 4, 4, -4, color, 0);
                    }
                    case MISS -> {
                        Geometry dot = quad(marker, "Miss diamond", 0, -2.4f,
                                2.4f * 1.414f, 2.4f * 1.414f, 0,
                                materials.flat(Materials.rgb(0xD9E9F1)));
                        dot.rotate(0, 0, FastMath.QUARTER_PI);
                    }
                    default -> { }
                }
            }
        }
        fleet.detachAllChildren();
        for (Ship ship : displayedBoard.ships()) drawShip(ship);
    }

    private void drawShip(Ship ship) {
        Vector2f start = center(ship.occupiedCells().getFirst());
        Vector2f end = center(ship.occupiedCells().getLast());
        float length = start.distance(end) / 2 + CELL * 0.42f;
        Node silhouette = new Node("Minimap ship " + ship.type());
        silhouette.setLocalTranslation((start.x + end.x) / 2, (start.y + end.y) / 2, 21);
        silhouette.rotate(0, 0, FastMath.atan2(end.y - start.y, end.x - start.x) - FastMath.HALF_PI);
        Material halo = materials.flat(new ColorRGBA(0.025f, 0.12f, 0.19f, 0.26f));
        hull(silhouette, CELL * 0.35f, length + 0.8f, halo, 0);
        hull(silhouette, CELL * 0.30f, length, ship.isSunk() ? sunkHull : hullGold, 0.1f);
        hull(silhouette, CELL * 0.26f, length - 0.8f, ship.isSunk() ? sunkHull : hullBlue, 0.2f);
        hull(silhouette, CELL * 0.16f, length * 0.67f, deck, 0.3f);
        quad(silhouette, "Ivory bridge", -2.1f, -4.5f, 4.2f, 8, 0.4f,
                materials.flat(Materials.linear(ship.isSunk() ? 0x8B8195 : 0xDDE9DF)));
        line(silhouette, 0, length * 0.12f, 0, length * 0.55f, GOLD, 0.5f);
        fleet.attachChild(silhouette);
    }

    private Vector2f center(Coordinate cell) {
        return new Vector2f((cell.row() + 0.5f) * CELL, (cell.column() + 0.5f) * CELL);
    }

    private void hull(Node parent, float width, float length, Material material, float z) {
        float[] contour = {0, length, -width * 0.7f, length - 6, -width, length * 0.5f,
                -width, -length + 4, -width * 0.65f, -length, width * 0.65f, -length,
                width, -length + 4, width, length * 0.5f, width * 0.7f, length - 6};
        float[] vertices = new float[contour.length / 2 * 3];
        for (int i = 0; i < contour.length / 2; i++) {
            vertices[i * 3] = contour[i * 2];
            vertices[i * 3 + 1] = contour[i * 2 + 1];
        }
        int[] indices = new int[(contour.length / 2 - 2) * 3];
        for (int i = 0; i < indices.length / 3; i++) {
            indices[i * 3] = 0;
            indices[i * 3 + 1] = i + 1;
            indices[i * 3 + 2] = i + 2;
        }
        Mesh mesh = new Mesh();
        mesh.setBuffer(Type.Position, 3, vertices);
        mesh.setBuffer(Type.Index, 3, indices);
        mesh.updateBound();
        Geometry geometry = new Geometry("Miniature engraved hull", mesh);
        material.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        geometry.setMaterial(material);
        geometry.setQueueBucket(Bucket.Gui);
        geometry.setLocalTranslation(0, 0, z);
        parent.attachChild(geometry);
    }

    private void line(Node parent, float x, float y, float endX, float endY, int color, float z) {
        float dx = endX - x, dy = endY - y;
        Geometry line = quad(parent, "Minimap line", x, y, FastMath.sqrt(dx * dx + dy * dy),
                0.85f, z, materials.flat(Materials.rgb(color)));
        line.rotate(0, 0, FastMath.atan2(dy, dx));
    }

    private Geometry quad(Node parent, String name, float x, float y, float width, float height,
                          float z, Material material) {
        Geometry geometry = new Geometry(name, new Quad(width, height));
        material.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        geometry.setMaterial(material);
        geometry.setQueueBucket(Bucket.Gui);
        geometry.setLocalTranslation(x, y, z);
        parent.attachChild(geometry);
        return geometry;
    }
}
