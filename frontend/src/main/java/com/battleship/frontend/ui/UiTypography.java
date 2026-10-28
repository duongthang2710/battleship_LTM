package com.battleship.frontend.ui;

import com.jme3.asset.AssetManager;
import com.jme3.asset.TextureKey;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.font.LineWrapMode;
import com.jme3.font.Rectangle;
import com.jme3.material.Material;
import com.jme3.texture.Texture;
import java.text.Normalizer;

public final class UiTypography {
    private UiTypography() { }

    public static BitmapFont body(AssetManager assets) { return load(assets, "fonts/Manrope.fnt"); }
    public static BitmapFont display(AssetManager assets) { return load(assets, "fonts/CormorantGaramond.fnt"); }

    private static BitmapFont load(AssetManager assets, String path) {
        BitmapFont source = assets.loadFont(path);
        BitmapFont result = new BitmapFont();
        result.setCharSet(source.getCharSet());
        result.setRightToLeft(source.isRightToLeft());
        result.setGlyphParser(source.getGlyphParser());
        Material[] pages = new Material[source.getPageSize()];
        for (int i = 0; i < pages.length; i++) {
            pages[i] = source.getPage(i).clone();
            Texture original = pages[i].getTextureParam("ColorMap").getTextureValue();
            TextureKey key = new TextureKey(original.getKey().getName(), ((TextureKey) original.getKey()).isFlipY());
            key.setGenerateMips(true);
            Texture texture = assets.loadTexture(key);
            texture.setMinFilter(Texture.MinFilter.Trilinear);
            texture.setMagFilter(Texture.MagFilter.Bilinear);
            pages[i].setTexture("ColorMap", texture);
        }
        // The board shares the original font; only the UI receives filtered page materials.
        result.setPages(pages);
        return result;
    }

    public static float scale(int width, int height) { return Math.min(width / 1280f, height / 800f); }

    public static float bodySize(float size) {
        if (size <= 10) return 14;
        if (size <= 11) return 15;
        if (size <= 13) return 16;
        if (size <= 14) return 17;
        return Math.max(18, size);
    }

    public static String normalize(String value) { return Normalizer.normalize(value, Normalizer.Form.NFC); }

    public static void centerIn(BitmapText text, float x, float y, float width, float height) {
        text.setAlignment(BitmapFont.Align.Left);
        text.setVerticalAlignment(BitmapFont.VAlign.Top);
        text.setBox(null);
        fit(text, Math.max(1, width - 12));
        text.setBox(new Rectangle(0, 0, width, height));
        text.setAlignment(BitmapFont.Align.Center);
        text.setVerticalAlignment(BitmapFont.VAlign.Center);
        text.setLineWrapMode(LineWrapMode.Clip);
        text.setLocalTranslation(x, y + height, text.getLocalTranslation().z);
    }

    public static void fit(BitmapText text, float width) {
        String value = normalize(text.getText());
        text.setText(value);
        if (text.getLineWidth() <= width) return;
        int end = value.length();
        do {
            end = value.offsetByCodePoints(end, -1);
            text.setText(value.substring(0, end).stripTrailing() + "...");
        } while (end > 0 && text.getLineWidth() > width);
    }
}
