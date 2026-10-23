package com.battleship.frontend.scene.environment;

import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.math.Vector4f;
import com.jme3.scene.Node;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

final class BackdropLayerBuilder {
    private record Tile(Vector4f crop, float aspect) {}
    private final Node root = new Node("Layered painted archipelago");
    private final AssetManager assets;
    private final Map<String, Tile[]> atlases = new HashMap<>();

    BackdropLayerBuilder(AssetManager assets, float waterline) {
        this.assets = assets;
        LayeredArtwork.Atlas mountains = LayeredArtwork.loadAtlas(assets, "textures/layered/mountain-depth.png", 3);
        for (int i = 0; i < 16; i++) {
            boolean low = i >= 8;
            float angle = (i % 8) * FastMath.QUARTER_PI + (low ? 0.14f : 0.32f);
            if (CoastlineBuilder.isOpenSea(angle)) continue;
            int variant = i % 3;
            float radius = low ? 182 : 226;
            float height = low ? 18 + variant * 3 : 40 + variant * 5;
            height *= 0.45f + 0.55f * CoastlineBuilder.coastTaper(angle);
            Vector3f inward = new Vector3f(-FastMath.sin(angle), 0, -FastMath.cos(angle));
            LayeredArtwork ridge = new LayeredArtwork(assets, mountains, variant,
                    (low ? "LowMountainRoot" : "HighMountainRoot") + (i % 8),
                    new Vector3f(FastMath.sin(angle) * radius, waterline, FastMath.cos(angle) * radius),
                    angle + FastMath.PI, height * mountains.aspect(variant) * (low ? 3.6f : 1.75f), height,
                    inward, low ? 1.8f : 3.2f, 1.0f, 0.24f);
            ridge.setEdgeFade(0.055f);
            root.attachChild(ridge.root());
        }
        sprite("island-cliffs", 0, "Distant weathered island", 0.30f,
                330, waterline, 13, 0, 1, 0.90f);
        sprite("shore-rocks", 1, "Distant stone arch", 2.88f,
                285, waterline, 9, 0, 1, 0.95f);
        sprite("far-mountains", 2, "Far ocean ridge", -0.36f,
                410, waterline, 16, 0, 1, 0.80f);
        for (int side : new int[] {-1, 1}) {
            for (int flank : new int[] {-1, 1}) {
                float angle = side * FastMath.HALF_PI + flank * 0.50f;
                Node shoulder = CoastalSlopeBuilder.create(assets,
                        (side < 0 ? "Radiant" : "Abyssal") + " gentle mountain flank " + flank,
                        new Vector4f(flank < 0 ? 0 : 0.5f, 0, 0.5f, 1),
                        new Vector3f(FastMath.sin(angle) * 205, waterline - 0.8f,
                                FastMath.cos(angle) * 205), 115, 42, -flank);
                root.attachChild(shoulder);
            }
        }
        for (int i = 0; i < 9; i++) {
            float angle = i * FastMath.TWO_PI / 9 + 0.17f;
            sprite("high-clouds", i % 3, "High cloud layer " + i, angle,
                    170 + i % 3 * 33, 19 + i % 4 * 6, 23 + i % 3 * 6, 4, 0.58f, 0.55f);
        }
        for (int i = 0; i < 8; i++) {
            float angle = i * FastMath.QUARTER_PI + 0.36f;
            sprite("low-mist", i % 3, "Waterfront mist layer " + i, angle,
                    54 + i % 4 * 22, waterline + 0.02f, 3.0f + i % 3, 4, 0.26f, 0.65f);
        }
        WorldArtwork moon = new WorldArtwork(assets, "Celestial moon",
                "textures/environment/celestial-moon.png", new Vector4f(0, 0, 1, 1),
                new Vector3f(62, 33, -350), 48, 48, 3, 0.95f);
        moon.setBrightness(1.05f);
        root.attachChild(moon.root());
    }

    private void sprite(String atlas, int index, String name, float angle, float distance,
                        float y, float height, float mode, float opacity, float brightness) {
        Tile tile = atlases.computeIfAbsent(atlas, this::readTiles)[index];
        WorldArtwork layer = new WorldArtwork(assets, name, "textures/environment/" + atlas + ".png",
                tile.crop(), new Vector3f(FastMath.sin(angle) * distance, y, FastMath.cos(angle) * distance),
                height * tile.aspect(), height, mode, opacity);
        layer.setBrightness(brightness);
        root.attachChild(layer.root());
    }

    private Tile[] readTiles(String name) {
        String path = "textures/environment/" + name + ".png";
        try (var stream = assets.locateAsset(new AssetKey<>(path)).openStream()) {
            BufferedImage image = ImageIO.read(stream);
            Tile[] tiles = new Tile[3];
            for (int tile = 0; tile < 3; tile++) {
                int start = tile * image.getWidth() / 3;
                int end = (tile + 1) * image.getWidth() / 3;
                int minX = end, maxX = start, minY = image.getHeight(), maxY = 0;
                int[] pixels = image.getRGB(start, 0, end - start, image.getHeight(), null, 0, end - start);
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = start; x < end; x++) {
                        if ((pixels[y * (end - start) + x - start] >>> 24) <= 16) continue;
                        minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                        minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                    }
                }
                // Crop UVs, not the bitmap: transparent padding cannot lift the visual waterline.
                minX = Math.max(start, minX - 2); maxX = Math.min(end - 1, maxX + 2);
                minY = Math.max(0, minY - 2); maxY = Math.min(image.getHeight() - 1, maxY + 2);
                float width = maxX - minX + 1, height = maxY - minY + 1;
                tiles[tile] = new Tile(new Vector4f((float) minX / image.getWidth(),
                        (image.getHeight() - maxY - 1f) / image.getHeight(),
                        width / image.getWidth(), height / image.getHeight()), width / height);
            }
            return tiles;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read environment atlas: " + path, exception);
        }
    }

    Node root() { return root; }

}
