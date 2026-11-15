package com.battleship.frontend.ui;

import com.battleship.frontend.graphics.Materials;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Highlights the button identified by a panel's existing hit test. */
public final class ButtonHover<T> {
    private static final ColorRGBA GOLD = Materials.rgb(0xDEB663);
    private final Map<T, Appearance> buttons = new HashMap<>();
    private T hovered;

    public void add(T button, Material fill, Material border) {
        buttons.put(button, new Appearance(fill, color(fill), border, border == null ? null : color(border)));
    }

    public void clear() {
        buttons.clear();
        hovered = null;
    }

    public void update(T button) {
        if (Objects.equals(button, hovered)) return;
        Appearance previous = buttons.get(hovered);
        if (previous != null) previous.apply(false);
        hovered = button;
        Appearance current = buttons.get(hovered);
        if (current != null) current.apply(true);
    }

    private static ColorRGBA color(Material material) {
        return ((ColorRGBA) material.getParam("Color").getValue()).clone();
    }

    private record Appearance(Material fill, ColorRGBA base, Material border, ColorRGBA rim) {
        void apply(boolean hovered) {
            fill.setColor("Color", hovered ? new ColorRGBA(
                    Math.min(1, base.r + .046f), Math.min(1, base.g + .076f),
                    Math.min(1, base.b + .093f), 1) : base);
            if (border != null) border.setColor("Color", hovered ? GOLD : rim);
        }
    }
}
