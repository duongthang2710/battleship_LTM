package com.battleship.frontend.ui;

import com.battleship.frontend.graphics.Materials;
import com.jme3.font.*;
import com.jme3.scene.*;
import com.jme3.scene.shape.Quad;

/** Text and blinking caret only. Panels own input values, backgrounds and actions. */
public final class UiTextField extends Node {
    private final BitmapText label, measure;
    private final Geometry caret;
    private final String placeholder;
    private final boolean centered;
    private String value = "";
    private boolean focused;
    private float width, height, blink;

    public UiTextField(BitmapFont font, Materials materials, float width, float height, float size, String placeholder, boolean centered) {
        super("Text entry");
        this.placeholder = placeholder; this.centered = centered;
        label = new BitmapText(font); label.setSize(size); measure = new BitmapText(font); measure.setSize(size);
        caret = new Geometry("Blinking caret", new Quad(1.3f, Math.min(20, height - 12)));
        caret.setMaterial(materials.flat(Materials.rgb(0x91CEFF))); attachChild(label); attachChild(caret);
        layout(width, height);
    }
    public void layout(float width, float height) {
        if (this.width == width && this.height == height) return;
        this.width = width; this.height = height; refresh();
    }
    public void setContent(String value, boolean focused) {
        String next = UiTypography.normalize(value);
        if (next.equals(this.value) && focused == this.focused) return;
        this.value = next; this.focused = focused; blink = 0; refresh();
    }
    public void update(float delta) {
        blink = (blink + Math.max(0, delta)) % 1f;
        caret.setCullHint(focused && blink < .5f ? CullHint.Never : CullHint.Always);
    }
    public boolean caretVisible() { return caret.getLocalCullHint() != CullHint.Always; }
    public String visibleText() { return label.getText(); }
    private void refresh() {
        String tail = value;
        measure.setText(tail);
        while (!tail.isEmpty() && measure.getLineWidth() > width - 4) {
            tail = tail.substring(tail.offsetByCodePoints(0, 1)); measure.setText(tail);
        }
        float textWidth = measure.getLineWidth();
        label.setText(value.isEmpty() && !focused ? placeholder : tail);
        label.setBox(new Rectangle(0, 0, width, height)); label.setVerticalAlignment(BitmapFont.VAlign.Center);
        label.setAlignment(centered ? BitmapFont.Align.Center : BitmapFont.Align.Left); label.setLineWrapMode(LineWrapMode.Clip);
        label.setColor(Materials.rgb(value.isEmpty() && !focused ? 0xA3AEC2 : 0xF4EAD1)); label.setLocalTranslation(0, height, 0);
        caret.setLocalTranslation(centered ? (width + textWidth) / 2 + 1 : textWidth + 1, (height - Math.min(20, height - 12)) / 2, 1);
        update(0);
    }
}
