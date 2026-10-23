package com.battleship.frontend.scene.environment;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.Vector3f;
import com.jme3.math.Vector4f;
import com.jme3.renderer.queue.RenderQueue.ShadowMode;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;

public final class OceanSurface {
    private final Geometry geometry;
    private final Material material;

    public OceanSurface(AssetManager assets, float size, int subdivisions, float height, boolean basin) {
        geometry = new Geometry(basin ? "Board water" : "Surrounding ocean", mesh(size, subdivisions, basin));
        material = new Material(assets, "materials/Ocean.j3md");
        material.setFloat("Time", 0);
        material.setFloat("WaveAmplitude", basin ? 0.025f : 0.12f);
        material.setFloat("Basin", basin ? 1 : 0);
        if (!basin) {
            var sky = assets.loadTexture("textures/environment/celestial-sky.png");
            sky.getImage().setColorSpace(com.jme3.texture.image.ColorSpace.sRGB);
            material.setTexture("SkyMap", sky);
        }
        geometry.setMaterial(material);
        geometry.setLocalTranslation(0, height, 0);
        geometry.setShadowMode(basin ? ShadowMode.Receive : ShadowMode.Off);
    }

    public Geometry geometry() { return geometry; }
    public void update(float time) { material.setFloat("Time", time); }

    void setReflectionSources(Vector3f radiant, Vector3f abyssal) {
        material.setVector3("RadiantLight", radiant.add(0, 5, 0));
        material.setVector3("AbyssalLight", abyssal.add(0, 5, 0));
    }

    void setWakeSources(Vector4f[] sources) {
        for (int i = 0; i < sources.length; i++) material.setVector4("Wake" + i, sources[i]);
    }

    private static Mesh mesh(float size, int subdivisions, boolean basin) {
        int stride = subdivisions + 1;
        float[] positions = new float[stride * stride * 3];
        float[] normals = new float[positions.length];
        int[] indices = new int[subdivisions * subdivisions * 6];
        for (int z = 0; z < stride; z++) {
            for (int x = 0; x < stride; x++) {
                int offset = (z * stride + x) * 3;
                positions[offset] = coordinate(x, subdivisions, size, basin);
                positions[offset + 2] = coordinate(z, subdivisions, size, basin);
                normals[offset + 1] = 1;
            }
        }
        int i = 0;
        for (int z = 0; z < subdivisions; z++) {
            for (int x = 0; x < subdivisions; x++) {
                int a = z * stride + x, b = a + 1, c = a + stride, d = c + 1;
                indices[i++] = a; indices[i++] = c; indices[i++] = b;
                indices[i++] = b; indices[i++] = c; indices[i++] = d;
            }
        }
        Mesh mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, positions);
        mesh.setBuffer(VertexBuffer.Type.Normal, 3, normals);
        mesh.setBuffer(VertexBuffer.Type.Index, 3, indices);
        mesh.updateBound();
        // GPU wave displacement extends the flat mesh's CPU-side bounds.
        ((com.jme3.bounding.BoundingBox) mesh.getBound()).setYExtent(basin ? 0.2f : 0.35f);
        return mesh;
    }

    private static float coordinate(int index, int subdivisions, float size, boolean basin) {
        float position = 2f * index / subdivisions - 1;
        // Concentrate vertices near the board while the same mesh reaches past the far clip.
        return size * 0.5f * (basin ? position : position * position * position);
    }
}
