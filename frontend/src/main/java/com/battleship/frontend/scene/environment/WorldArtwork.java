package com.battleship.frontend.scene.environment;

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

final class WorldArtwork {
    private final Node root;
    private final Material material;

    WorldArtwork(AssetManager assets, String name, String texturePath, Vector4f crop,
                 Vector3f position, float width, float height, float mode, float opacity) {
        root = new Node(name);
        boolean celestial = mode == 3;
        root.setLocalTranslation(position);
        float yaw = (float) Math.atan2(-position.x, -position.z);
        root.setLocalRotation(new Quaternion().fromAngles(0, yaw, 0));
        Texture texture = assets.loadTexture(texturePath);
        texture.getImage().setColorSpace(ColorSpace.sRGB);
        texture.setMagFilter(Texture.MagFilter.Bilinear);
        material = new Material(assets, "materials/WorldArtwork.j3md");
        material.setTexture("ColorMap", texture);
        material.setVector4("Crop", crop);
        material.setFloat("Mode", mode);
        material.setFloat("Opacity", opacity);
        material.setFloat("Brightness", 1);
        material.setFloat("EdgeFade", 0);
        RenderState state = material.getAdditionalRenderState();
        state.setBlendMode(RenderState.BlendMode.Alpha);
        state.setDepthWrite(false);
        state.setFaceCullMode(RenderState.FaceCullMode.Off);
        float bottom = celestial ? -height / 2 : 0;
        Mesh plane = new Mesh();
        plane.setBuffer(VertexBuffer.Type.Position, 3, new float[] {
                -width/2,bottom,0, width/2,bottom,0,
                width/2,bottom+height,0, -width/2,bottom+height,0});
        plane.setBuffer(VertexBuffer.Type.TexCoord, 2, new float[] {0,0, 1,0, 1,1, 0,1});
        plane.setBuffer(VertexBuffer.Type.Index, 3, new int[] {0,1,2, 0,2,3});
        plane.updateBound();
        Geometry image = new Geometry(name + " image", plane);
        image.setMaterial(material);
        image.setQueueBucket(RenderQueue.Bucket.Transparent);
        image.setShadowMode(RenderQueue.ShadowMode.Off);
        image.setCullHint(Node.CullHint.Never);
        root.attachChild(image);
    }

    Node root() { return root; }

    void setBrightness(float value) { material.setFloat("Brightness", value); }

    void setEdgeFade(float value) { material.setFloat("EdgeFade", value); }

}
