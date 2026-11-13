package com.battleship.frontend.effects;

import com.battleship.frontend.game.BoardCoordinates;
import com.battleship.frontend.graphics.Materials;
import com.battleship.shared.game.Coordinate;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.shape.Quad;
import java.util.List;

/** World-space radar clipped to a full 3x3 scan footprint. */
final class SonarEffect {
    static final float DURATION = 2.8f;
    private record Bounds(float minX, float maxX, float minZ, float maxZ) { }
    private final Geometry surface;
    private final Material material;

    SonarEffect(Materials materials, BoardCoordinates coordinates, List<Coordinate> cells) {
        Bounds bounds = bounds(coordinates, cells);
        surface = new Geometry("Sonar radar sweep", new Quad(bounds.maxX - bounds.minX, bounds.maxZ - bounds.minZ));
        material = materials.sonarSweep();
        material.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        material.getAdditionalRenderState().setDepthWrite(false);
        surface.setMaterial(material);
        surface.rotate(-FastMath.HALF_PI, 0, 0);
        surface.setLocalTranslation(bounds.minX, .257f, bounds.maxZ);
        surface.setQueueBucket(RenderQueue.Bucket.Transparent);
        surface.setShadowMode(RenderQueue.ShadowMode.Off);
    }

    Geometry node() { return surface; }
    void update(float elapsed) { material.setFloat("Elapsed", elapsed); }

    static Geometry outline(Materials materials, BoardCoordinates coordinates, List<Coordinate> cells) {
        Bounds bounds = bounds(coordinates, cells);
        float x = bounds.minX, z = bounds.minZ, right = bounds.maxX, back = bounds.maxZ;
        float inset = .025f, y = .254f;
        float[] vertices = {
                x,y,z, right,y,z, right,y,back, x,y,back,
                x+inset,y,z+inset, right-inset,y,z+inset,
                right-inset,y,back-inset, x+inset,y,back-inset
        };
        int[] indices = new int[24];
        for (int edge = 0; edge < 4; edge++) {
            int next = (edge + 1) % 4, index = edge * 6;
            indices[index] = edge; indices[index+1] = next; indices[index+2] = edge+4;
            indices[index+3] = next; indices[index+4] = next+4; indices[index+5] = edge+4;
        }
        Mesh mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, vertices);
        mesh.setBuffer(VertexBuffer.Type.Index, 3, indices);
        mesh.updateBound();
        Geometry outline = new Geometry("Sonar scanned area outline", mesh);
        Material rim = materials.glow(Materials.linear(0x71D9B8), .8f);
        rim.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        outline.setMaterial(rim);
        outline.setShadowMode(RenderQueue.ShadowMode.Off);
        return outline;
    }

    private static Bounds bounds(BoardCoordinates coordinates, List<Coordinate> cells) {
        float minX = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        for (Coordinate cell : cells) {
            Vector3f center = coordinates.localCenter(cell, 0);
            minX = Math.min(minX, center.x - .5f); maxX = Math.max(maxX, center.x + .5f);
            minZ = Math.min(minZ, center.z - .5f); maxZ = Math.max(maxZ, center.z + .5f);
        }
        return new Bounds(minX, maxX, minZ, maxZ);
    }
}
