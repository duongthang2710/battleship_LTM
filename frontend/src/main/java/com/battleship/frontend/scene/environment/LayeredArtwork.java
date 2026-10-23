package com.battleship.frontend.scene.environment;

import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
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
import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.imageio.ImageIO;

final class LayeredArtwork {
    static final class Atlas {
        private final Texture texture;
        private final Vector4f[][] crops;
        private final float[] aspects;

        private Atlas(Texture texture, Vector4f[][] crops, float[] aspects) {
            this.texture = texture;
            this.crops = crops;
            this.aspects = aspects;
        }

        float aspect(int variant) { return aspects[variant]; }
    }

    private final Node root;
    private final Material[] materials = new Material[3];

    LayeredArtwork(AssetManager assets, Atlas atlas, int variant, String name, Vector3f position,
                   float yaw, float width, float height, Vector3f depthAxis, float spacing,
                   float brightness, float fogStrength) {
        root = new Node(name);
        root.setLocalTranslation(position);
        root.setLocalRotation(new Quaternion().fromAngles(0, yaw, 0));
        Quaternion inverseHeading = new Quaternion().fromAngles(0, -yaw, 0);
        String[] names = {"RearLayer", "MiddleLayer", "FrontLayer"};
        for (int layer = 0; layer < materials.length; layer++) {
            Node anchor = new Node(names[layer]);
            anchor.setLocalTranslation(inverseHeading.mult(depthAxis.mult((layer - 1) * spacing)));
            Material material = new Material(assets, "materials/LayeredArtwork.j3md");
            material.setTexture("ColorMap", atlas.texture);
            material.setVector4("Crop", atlas.crops[variant][layer]);
            material.setFloat("Brightness", brightness);
            material.setFloat("Sharpness", 0.65f);
            material.setFloat("FogStrength", fogStrength);
            material.setFloat("Opacity", 1);
            material.setFloat("EdgeFade", 0);
            materials[layer] = material;
            RenderState state = material.getAdditionalRenderState();
            state.setBlendMode(RenderState.BlendMode.Alpha);
            state.setDepthWrite(false);
            state.setFaceCullMode(RenderState.FaceCullMode.Off);
            Mesh mesh = new Mesh();
            mesh.setBuffer(VertexBuffer.Type.Position, 3, new float[] {
                    -width/2,0,0, width/2,0,0, width/2,height,0, -width/2,height,0});
            mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, new float[] {0,0, 1,0, 1,1, 0,1});
            mesh.setBuffer(VertexBuffer.Type.Index, 3, new int[] {0,1,2, 0,2,3});
            mesh.updateBound();
            Geometry plate = new Geometry(name + " " + names[layer] + " image", mesh);
            plate.setMaterial(material);
            plate.setQueueBucket(RenderQueue.Bucket.Transparent);
            plate.setShadowMode(RenderQueue.ShadowMode.Off);
            anchor.attachChild(plate);
            root.attachChild(anchor);
        }
    }

    Node root() { return root; }

    void setEdgeFade(float value) {
        for (Material material : materials) material.setFloat("EdgeFade", value);
    }

    static Atlas loadAtlas(AssetManager assets, String path, int variants) {
        Texture texture = assets.loadTexture(path);
        texture.getImage().setColorSpace(ColorSpace.sRGB);
        texture.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
        texture.setMagFilter(Texture.MagFilter.Bilinear);
        texture.setWrap(Texture.WrapMode.EdgeClamp);
        try (var stream = assets.locateAsset(new AssetKey<>(path)).openStream()) {
            BufferedImage image = ImageIO.read(stream);
            Vector4f[][] crops = new Vector4f[variants][3];
            float[] aspects = new float[variants];
            for (int row = 0; row < variants; row++) {
                int top = row * image.getHeight() / variants;
                int bottom = (row + 1) * image.getHeight() / variants;
                int tileWidth = image.getWidth() / 3;
                int minX = tileWidth, maxX = 0, minY = bottom - top, maxY = 0;
                for (int layer = 0; layer < 3; layer++) {
                    int start = layer * tileWidth;
                    int[] pixels = image.getRGB(start, top, tileWidth, bottom - top, null, 0, tileWidth);
                    for (int y = 0; y < bottom - top; y++) {
                        for (int x = 0; x < tileWidth; x++) {
                            if ((pixels[y * tileWidth + x] >>> 24) <= 8) continue;
                            minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                            minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                        }
                    }
                }
                // One union crop for all depth plates preserves their original pixel registration.
                minX = Math.max(0, minX - 2); maxX = Math.min(tileWidth - 1, maxX + 2);
                minY = Math.max(0, minY - 2); maxY = Math.min(bottom - top - 1, maxY + 2);
                float width = maxX - minX + 1, height = maxY - minY + 1;
                aspects[row] = width / height;
                for (int layer = 0; layer < 3; layer++) {
                    crops[row][layer] = new Vector4f((layer * tileWidth + minX) / (float) image.getWidth(),
                            (image.getHeight() - top - maxY - 1f) / image.getHeight(),
                            width / image.getWidth(), height / image.getHeight());
                }
            }
            return new Atlas(texture, crops, aspects);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load layered artwork: " + path, exception);
        }
    }
}
