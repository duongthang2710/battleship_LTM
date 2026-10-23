package com.battleship.frontend.graphics;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;

public final class Materials {
    private final AssetManager assets;

    public Materials(AssetManager assets) { this.assets = assets; }

    public Material metal(ColorRGBA color, float shininess) {
        Material material = new Material(assets, "Common/MatDefs/Light/Lighting.j3md");
        material.setBoolean("UseMaterialColors", true);
        material.setColor("Diffuse", color);
        material.setColor("Ambient", color.mult(0.65f));
        material.setColor("Specular", color.mult(0.55f).add(new ColorRGBA(0.15f, 0.15f, 0.15f, 0)));
        material.setFloat("Shininess", shininess);
        return material;
    }

    public Material flat(ColorRGBA color) {
        Material material = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setColor("Color", color);
        return material;
    }

    public Material glow(ColorRGBA color, float strength) {
        Material material = flat(color.mult(strength));
        material.setColor("GlowColor", color.mult(strength));
        return material;
    }

    public static ColorRGBA rgb(int hex) {
        return new ColorRGBA(((hex >> 16) & 255) / 255f,
                ((hex >> 8) & 255) / 255f, (hex & 255) / 255f, 1);
    }

    public static ColorRGBA linear(int hex) {
        ColorRGBA color = rgb(hex);
        // Lighting and the gamma-correct framebuffer expect linear scene colors.
        color.r = (float) Math.pow(color.r, 2.2);
        color.g = (float) Math.pow(color.g, 2.2);
        color.b = (float) Math.pow(color.b, 2.2);
        return color;
    }
}
