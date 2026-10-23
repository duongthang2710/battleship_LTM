package com.battleship.frontend.effects;

import com.jme3.asset.AssetManager;
import com.jme3.light.DirectionalLight;
import com.jme3.math.Vector3f;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.filters.BloomFilter;
import com.jme3.post.filters.FXAAFilter;
import com.jme3.post.filters.ToneMapFilter;
import com.jme3.post.ssao.SSAOFilter;
import com.jme3.renderer.ViewPort;
import com.jme3.shadow.DirectionalLightShadowFilter;
import com.jme3.shadow.EdgeFilteringMode;

public final class SceneEffects {
    private SceneEffects() {}

    public static void install(AssetManager assets, ViewPort viewport, DirectionalLight light) {
        FilterPostProcessor processor = new FilterPostProcessor(assets);
        processor.setNumSamples(4);
        DirectionalLightShadowFilter shadows = new DirectionalLightShadowFilter(assets, 2048, 3);
        shadows.setLight(light);
        shadows.setEdgeFilteringMode(EdgeFilteringMode.PCFPOISSON);
        shadows.setShadowIntensity(0.42f);
        shadows.setShadowZExtend(45);
        shadows.setShadowZFadeLength(8);
        processor.addFilter(shadows);
        processor.addFilter(new SSAOFilter(0.35f, 0.85f, 0.45f, 0.06f));
        BloomFilter bloom = new BloomFilter(BloomFilter.GlowMode.Objects);
        bloom.setBloomIntensity(0.35f);
        bloom.setBlurScale(1.3f);
        bloom.setExposurePower(4);
        bloom.setExposureCutOff(0.95f);
        processor.addFilter(bloom);
        processor.addFilter(new ToneMapFilter(new Vector3f(1.8f, 1.8f, 1.8f)));
        processor.addFilter(new FXAAFilter());
        viewport.addProcessor(processor);
    }
}
