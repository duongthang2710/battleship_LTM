package com.battleship.frontend.scene.environment;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Sphere;
import com.jme3.texture.Texture;
import com.jme3.texture.image.ColorSpace;

final class SkyBuilder {
    private final AssetManager assets;

    SkyBuilder(AssetManager assets) { this.assets = assets; }

    Geometry create() {
        Texture panorama = assets.loadTexture("textures/environment/celestial-sky.png");
        panorama.getImage().setColorSpace(ColorSpace.sRGB);
        panorama.setWrap(Texture.WrapAxis.S, Texture.WrapMode.Repeat);
        panorama.setWrap(Texture.WrapAxis.T, Texture.WrapMode.EdgeClamp);
        panorama.setMagFilter(Texture.MagFilter.Bilinear);
        Material material = new Material(assets, "materials/Sky.j3md");
        material.setTexture("SkyMap", panorama);
        Geometry sky = new Geometry("Enclosing celestial sky", new Sphere(24, 48, 900, false, true));
        sky.setMaterial(material);
        sky.setQueueBucket(RenderQueue.Bucket.Sky);
        sky.setShadowMode(RenderQueue.ShadowMode.Off);
        return sky;
    }
}
