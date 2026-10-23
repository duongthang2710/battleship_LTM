package com.battleship.frontend.scene.environment;

import com.battleship.frontend.graphics.Materials;
import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.FastMath;
import com.jme3.math.Vector4f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.texture.Texture;
import com.jme3.texture.image.ColorSpace;
import java.util.Arrays;

final class CoastlineBuilder {
    private final Node root = new Node("Open coastal headlands");

    CoastlineBuilder(AssetManager assets, Materials materials, float waterline) {
        Geometry land = new Geometry("Solid land beyond the bay", landMesh(waterline));
        land.setMaterial(materials.metal(Materials.linear(0x0E1B2E), 6));
        land.setShadowMode(RenderQueue.ShadowMode.Off);
        root.attachChild(land);
        band(assets, "Distant alpine coastline", "alpine-coast.png", 212, 76, waterline - 3, 6, 0.30f);
        band(assets, "Rolling foothill coastline", "low-foothills.png", 182, 42, waterline - 2, 7, 0.20f);
        band(assets, "Near coastal escarpment", "coastal-cliffs.png", 158, 25, waterline - 3, 8, 0.12f);
    }

    Node root() { return root; }

    static boolean isOpenSea(float angle) {
        return Math.abs(FastMath.sin(angle)) <= 0.84f;
    }

    static float coastTaper(float angle) {
        float value = FastMath.clamp((Math.abs(FastMath.sin(angle)) - 0.84f) / 0.16f, 0, 1);
        return value * value * (3 - 2 * value);
    }

    private void band(AssetManager assets, String name, String file, float radius,
                      float height, float bottom, int repeats, float fog) {
        Texture texture = assets.loadTexture("textures/coastline/" + file);
        texture.getImage().setColorSpace(ColorSpace.sRGB);
        texture.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
        texture.setMagFilter(Texture.MagFilter.Bilinear);
        texture.setWrap(Texture.WrapAxis.S, Texture.WrapMode.Repeat);
        texture.setWrap(Texture.WrapAxis.T, Texture.WrapMode.EdgeClamp);
        Material material = new Material(assets, "materials/LayeredArtwork.j3md");
        material.setTexture("ColorMap", texture);
        material.setVector4("Crop", new Vector4f(0, 0, 1, 1));
        material.setFloat("Brightness", 1);
        material.setFloat("Sharpness", 0.35f);
        material.setFloat("FogStrength", fog);
        material.setFloat("Opacity", 1);
        material.setFloat("EdgeFade", 0);
        RenderState state = material.getAdditionalRenderState();
        state.setBlendMode(RenderState.BlendMode.Alpha);
        state.setDepthWrite(true);
        state.setFaceCullMode(RenderState.FaceCullMode.Off);

        int segments = 192;
        float[] positions = new float[(segments + 1) * 6];
        float[] uv = new float[(segments + 1) * 4];
        int[] indices = new int[segments * 6];
        int indexCount = 0;
        for (int i = 0; i <= segments; i++) {
            float fraction = i / (float) segments, angle = fraction * FastMath.TWO_PI;
            float x = FastMath.sin(angle) * radius, z = FastMath.cos(angle) * radius;
            int p = i * 6, t = i * 4;
            positions[p] = x; positions[p + 1] = bottom; positions[p + 2] = z;
            positions[p + 3] = x; positions[p + 4] = bottom + height * coastTaper(angle); positions[p + 5] = z;
            uv[t] = fraction * repeats; uv[t + 1] = 0;
            uv[t + 2] = fraction * repeats; uv[t + 3] = 1;
            if (i == segments || isOpenSea((i + 0.5f) * FastMath.TWO_PI / segments)) continue;
            int a = i * 2, offset = indexCount;
            indices[offset] = a; indices[offset + 1] = a + 1; indices[offset + 2] = a + 2;
            indices[offset + 3] = a + 1; indices[offset + 4] = a + 3; indices[offset + 5] = a + 2;
            indexCount += 6;
        }
        Geometry ridge = new Geometry(name, mesh(positions, uv, null, Arrays.copyOf(indices, indexCount)));
        ridge.setMaterial(material);
        // Alpha-cut skyline writes depth before the transparent castle plates are drawn.
        ridge.setQueueBucket(RenderQueue.Bucket.Opaque);
        ridge.setShadowMode(RenderQueue.ShadowMode.Off);
        root.attachChild(ridge);
    }

    private static Mesh landMesh(float waterline) {
        float[] radii = {160, 168, 182, 240, 600, 1700};
        float[] heights = {-0.4f, 1.5f, 4, 7, 10, 12};
        int segments = 128, stride = segments + 1;
        float[] positions = new float[radii.length * stride * 3];
        float[] normals = new float[positions.length];
        int[] indices = new int[(radii.length - 1) * segments * 6];
        int indexCount = 0;
        for (int ring = 0; ring < radii.length; ring++) {
            for (int i = 0; i <= segments; i++) {
                float angle = i * FastMath.TWO_PI / segments;
                int vertex = ring * stride + i, p = vertex * 3;
                positions[p] = FastMath.sin(angle) * radii[ring];
                positions[p + 1] = waterline - 1 + (heights[ring] + 1) * coastTaper(angle);
                positions[p + 2] = FastMath.cos(angle) * radii[ring];
                normals[p + 1] = 1;
                if (ring == radii.length - 1 || i == segments
                        || isOpenSea((i + 0.5f) * FastMath.TWO_PI / segments)) continue;
                int a = vertex, b = a + 1, c = a + stride, d = c + 1;
                int offset = indexCount;
                indices[offset] = a; indices[offset + 1] = c; indices[offset + 2] = b;
                indices[offset + 3] = b; indices[offset + 4] = c; indices[offset + 5] = d;
                indexCount += 6;
            }
        }
        return mesh(positions, null, normals, Arrays.copyOf(indices, indexCount));
    }

    private static Mesh mesh(float[] positions, float[] uv, float[] normals, int[] indices) {
        Mesh mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, positions);
        if (uv != null) mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, uv);
        if (normals != null) mesh.setBuffer(VertexBuffer.Type.Normal, 3, normals);
        mesh.setBuffer(VertexBuffer.Type.Index, 3, indices);
        mesh.updateBound();
        return mesh;
    }
}
