package com.battleship.frontend.game;

import com.battleship.frontend.graphics.Materials;
import com.battleship.shared.game.Board;
import com.battleship.shared.game.CellState;
import com.battleship.shared.game.Coordinate;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.renderer.queue.RenderQueue.ShadowMode;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Torus;
import java.util.List;

public final class PlacementFeedback {
    private final Node preview = new Node("Ship placement preview");
    private final Node selection = new Node("Selected ship footprint");
    private final Node attacks = new Node("Gameplay attack markers");
    private final BoardCoordinates coordinates;
    private final Material valid;
    private final Material invalid;
    private final Material selected;
    private final Material miss;
    private final Material hit;
    private final Material sunk;
    private List<Coordinate> previewCells = List.of();
    private boolean previewValid;
    private List<Coordinate> selectionCells = List.of();

    public PlacementFeedback(Node board, Materials materials) {
        coordinates = new BoardCoordinates(board);
        valid = translucent(materials, 0x49C5ED, 0.24f);
        invalid = translucent(materials, 0xEA5252, 0.35f);
        selected = materials.glow(Materials.linear(0xF6D596), 0.70f);
        miss = materials.glow(Materials.linear(0x86D6F0), 0.55f);
        hit = materials.glow(Materials.linear(0xF19A65), 0.75f);
        sunk = materials.glow(Materials.linear(0xEAC16F), 0.80f);
        board.attachChild(preview);
        board.attachChild(selection);
        board.attachChild(attacks);
    }

    public void showPreview(List<Coordinate> cells, boolean validPlacement) {
        if (previewCells.equals(cells) && previewValid == validPlacement) return;
        previewCells = List.copyOf(cells);
        previewValid = validPlacement;
        preview.detachAllChildren();
        for (Coordinate cell : cells) {
            if (!Board.inBounds(cell)) continue;
            Geometry tile = new Geometry("Placement " + cell, new Box(0.47f, 0.003f, 0.47f));
            tile.setMaterial(validPlacement ? valid : invalid);
            tile.setLocalTranslation(coordinates.localCenter(cell, 0.228f));
            tile.setQueueBucket(Bucket.Transparent);
            tile.setShadowMode(ShadowMode.Off);
            preview.attachChild(tile);
        }
    }

    public void clearPreview() {
        preview.detachAllChildren();
        previewCells = List.of();
    }

    public void showSelection(List<Coordinate> cells) {
        if (selectionCells.equals(cells)) return;
        selectionCells = List.copyOf(cells);
        selection.detachAllChildren();
        for (Coordinate cell : cells) {
            Vector3f center = coordinates.localCenter(cell, 0.241f);
            for (int side : new int[] {-1, 1}) {
                line(selection, center.add(side * 0.46f, 0, 0), new Vector3f(0.012f, 0.003f, 0.46f), selected);
                line(selection, center.add(0, 0, side * 0.46f), new Vector3f(0.46f, 0.003f, 0.012f), selected);
            }
        }
    }

    public void clearSelection() {
        selection.detachAllChildren();
        selectionCells = List.of();
    }

    public void displayAttacks(Board board) {
        attacks.detachAllChildren();
        for (int row = 0; row < Board.SIZE; row++) {
            for (int column = 0; column < Board.SIZE; column++) {
                Coordinate cell = new Coordinate(row, column);
                CellState state = board.cell(cell).state();
                if (state != CellState.MISS && state != CellState.HIT && state != CellState.SUNK) continue;
                Vector3f center = coordinates.localCenter(cell, 0.247f);
                if (state == CellState.MISS) {
                    Geometry ripple = new Geometry("Miss marker", new Torus(6, 24, 0.015f, 0.17f));
                    ripple.rotate(-FastMath.HALF_PI, 0, 0);
                    ripple.setLocalTranslation(center);
                    ripple.setMaterial(miss);
                    ripple.setShadowMode(ShadowMode.Off);
                    attacks.attachChild(ripple);
                } else {
                    for (int direction : new int[] {-1, 1}) {
                        Geometry cross = line(attacks, center, new Vector3f(0.19f, 0.008f, 0.025f),
                                state == CellState.SUNK ? sunk : hit);
                        cross.rotate(0, direction * FastMath.QUARTER_PI, 0);
                    }
                }
            }
        }
    }

    public void clearAttacks() { attacks.detachAllChildren(); }

    private static Material translucent(Materials materials, int color, float alpha) {
        ColorRGBA tint = Materials.linear(color);
        tint.a = alpha;
        Material material = materials.flat(tint);
        material.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        material.getAdditionalRenderState().setDepthWrite(false);
        return material;
    }

    private static Geometry line(Node parent, Vector3f center, Vector3f size, Material material) {
        Geometry geometry = new Geometry("Gameplay footprint line", new Box(size.x, size.y, size.z));
        geometry.setLocalTranslation(center);
        geometry.setMaterial(material);
        geometry.setShadowMode(ShadowMode.Off);
        parent.attachChild(geometry);
        return geometry;
    }

}
