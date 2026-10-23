package com.battleship.frontend.scene.environment;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.math.Vector4f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.texture.Texture;
import com.jme3.texture.image.ColorSpace;

final class CoastalSlopeBuilder {
    private CoastalSlopeBuilder() {}

    static Node create(AssetManager assets, String name, Vector4f crop, Vector3f position,
                       float width, float height, int seawardSign) {
        Node root = new Node(name);
        root.setLocalTranslation(position);
        root.setLocalRotation(new Quaternion().fromAngles(0,
                FastMath.atan2(-position.x, -position.z), 0));
        Texture texture = coastlineTexture(assets, "low-foothills.png");
        root.attachChild(layer(assets, texture, name + " sloping headland", crop,
                width * 1.18f, height, width, seawardSign, 0));
        root.attachChild(layer(assets, texture, name + " low shoreline rocks",
                new Vector4f(crop.x, crop.y, crop.z, crop.w * 0.22f),
                width * 1.32f, height * 0.20f, width, seawardSign, 3.5f));
        root.attachChild(layer(assets, coastlineTexture(assets, "coastal-cliffs.png"),
                name + " tidal rock tongue",
                new Vector4f(crop.x, crop.y, crop.z, crop.w * 0.26f),
                width * 1.42f, height * 0.10f, width, seawardSign, 7));
        return root;
    }

    private static Texture coastlineTexture(AssetManager assets, String file) {
        Texture texture = assets.loadTexture("textures/coastline/" + file);
        texture.getImage().setColorSpace(ColorSpace.sRGB);
        texture.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
        texture.setMagFilter(Texture.MagFilter.Bilinear);
        texture.setWrap(Texture.WrapMode.EdgeClamp);
        return texture;
    }

    private static Geometry layer(AssetManager assets, Texture texture, String name,
                                  Vector4f crop, float width, float height, float originalWidth,
                                  int seawardSign, float forward) {
        Material material = new Material(assets, "materials/WorldArtwork.j3md");
        material.setTexture("ColorMap", texture);
        material.setVector4("Crop", crop);
        material.setFloat("Mode", 0);
        material.setFloat("Opacity", 1);
        material.setFloat("Brightness", 0.95f);
        material.setFloat("EdgeFade", 0.035f);
        RenderState state = material.getAdditionalRenderState();
        state.setBlendMode(RenderState.BlendMode.Alpha);
        state.setDepthWrite(true);
        state.setFaceCullMode(RenderState.FaceCullMode.Off);

        int segments = 40;
        float[] positions = new float[(segments + 1) * 6];
        float[] uv = new float[(segments + 1) * 4];
        int[] indices = new int[segments * 6];
        for (int i = 0; i <= segments; i++) {
            float u = i / (float) segments;
            float towardSea = seawardSign > 0 ? u : 1 - u;
            float t = FastMath.clamp((towardSea - 0.06f) / 0.94f, 0, 1);
            float smooth = t * t * (3 - 2 * t);
            // The seaward endpoint closes below the ocean, never as a vertical card edge.
            float profile = FastMath.pow(1 - smooth, 1.25f);
            if (forward > 0) {
                profile *= 1 + towardSea * (0.14f * FastMath.sin(towardSea * 13 + forward)
                        + 0.07f * FastMath.sin(towardSea * 29));
            }
            // Extend only the sea-facing end; the ridge-side attachment stays in place.
            float x = (u - 0.5f) * width + seawardSign * (width - originalWidth) * 0.5f;
            float z = forward + 2.5f * FastMath.sin(towardSea * FastMath.PI);
            int p = i * 6, v = i * 4;
            positions[p] = x; positions[p + 1] = 0; positions[p + 2] = z;
            positions[p + 3] = x; positions[p + 4] = height * profile;
            positions[p + 5] = z;
            uv[v] = u; uv[v + 1] = 0;
            uv[v + 2] = u; uv[v + 3] = 1;
            if (i == segments) continue;
            int a = i * 2, q = i * 6;
            indices[q] = a; indices[q + 1] = a + 2; indices[q + 2] = a + 1;
            indices[q + 3] = a + 1; indices[q + 4] = a + 2; indices[q + 5] = a + 3;
        }
        Mesh mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, positions);
        mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, uv);
        mesh.setBuffer(VertexBuffer.Type.Index, 3, indices);
        mesh.updateBound();
        Geometry layer = new Geometry(name, mesh);
        layer.setMaterial(material);
        layer.setQueueBucket(RenderQueue.Bucket.Opaque);
        layer.setShadowMode(RenderQueue.ShadowMode.Off);
        return layer;
    }
}
