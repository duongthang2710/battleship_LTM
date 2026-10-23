package com.battleship.frontend.scene.environment;

import com.jme3.asset.AssetManager;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;

final class CastleLayerBuilder {
    private final AssetManager assets;

    CastleLayerBuilder(AssetManager assets) { this.assets = assets; }

    CastleImpostor create(String name, boolean dark, Vector3f position, float height) {
        return new CastleImpostor(assets, name,
                dark ? "abyssal" : "radiant",
                position, dark ? -FastMath.HALF_PI : FastMath.HALF_PI, height);
    }
}
