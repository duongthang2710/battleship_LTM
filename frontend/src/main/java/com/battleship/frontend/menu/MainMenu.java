package com.battleship.frontend.menu;

import com.battleship.frontend.graphics.Materials;
import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector2f;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial.CullHint;
import com.jme3.scene.shape.Quad;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class MainMenu {
    public enum Action {
        PVP, PVE, LEADERBOARD, CREATE_ROOM, JOIN_ROOM, FRIENDS, PROFILE, SETTINGS, EXIT
    }

    private static final float WIDTH = 400;
    private static final float HEIGHT = 650;
    private static final int GOLD = 0xDEB663;
    private static final int IVORY = 0xF4EAD1;
    private static final int CYAN = 0x91CEFF;
    private final Node root = new Node("Main menu");
    private final Node layout = new Node("Main menu panel");
    private final Materials materials;
    private final BitmapFont display;
    private final BitmapFont body;
    private final List<Button> buttons = new ArrayList<>();
    private float scale = 1;
    private float left;
    private float bottom;
    private boolean visible;
    private Action hovered;

    public MainMenu(AssetManager assets) {
        materials = new Materials(assets);
        display = assets.loadFont("fonts/CormorantGaramond.fnt");
        body = assets.loadFont("fonts/Manrope.fnt");
        root.attachChild(layout);
        build();
        resize(1440, 900);
        show();
    }

    public Node root() { return root; }
    public void friendsUpdated(boolean updated) {
        buttons.stream().filter(button -> button.action == Action.FRIENDS).forEach(button -> {
            button.label.setText(updated ? "FRIENDS *" : "FRIENDS");
            button.label.setLocalTranslation(button.x + (button.width - button.label.getLineWidth()) / 2,
                    button.label.getLocalTranslation().y, button.label.getLocalTranslation().z);
        });
    }

    public void resize(int width, int height) {
        scale = Math.min(width / 1440f, height / 900f);
        left = 56 * scale;
        bottom = (height - HEIGHT * scale) / 2;
        layout.setLocalScale(scale);
        layout.setLocalTranslation(left, bottom, 0);
    }

    public void show() {
        visible = true;
        root.setCullHint(CullHint.Never);
        update(new Vector2f(-1, -1));
    }

    public void hide() {
        visible = false;
        root.setCullHint(CullHint.Always);
    }

    public boolean visible() { return visible; }

    public Optional<Action> actionAt(Vector2f screen) {
        if (!visible) return Optional.empty();
        float x = (screen.x - left) / scale;
        float y = (screen.y - bottom) / scale;
        for (Button button : buttons) {
            if (x >= button.x && x <= button.x + button.width
                    && y >= button.y && y <= button.y + button.height) {
                return Optional.of(button.action);
            }
        }
        return Optional.empty();
    }

    public Vector2f buttonCenter(Action action) {
        for (Button button : buttons) {
            if (button.action == action) {
                return new Vector2f(left + (button.x + button.width / 2) * scale,
                        bottom + (button.y + button.height / 2) * scale);
            }
        }
        throw new IllegalArgumentException("Unknown menu action: " + action);
    }

    public void update(Vector2f cursor) {
        if (!visible) return;
        Action next = actionAt(cursor).orElse(null);
        if (next == hovered) return;
        hovered = next;
        for (Button button : buttons) {
            boolean active = button.action == hovered;
            boolean primary = button.action == Action.PVP;
            button.fill.setColor("Color", active
                    ? new ColorRGBA(0.055f, 0.098f, 0.137f, 0.98f)
                    : primary ? new ColorRGBA(0.029f, 0.068f, 0.109f, 0.97f)
                    : new ColorRGBA(0.009f, 0.022f, 0.044f, 0.9f));
            button.border.setColor("Color", Materials.rgb(active || primary ? GOLD : 0x516275));
            button.label.setColor(Materials.rgb(active || primary ? IVORY : 0xCFD5E1));
        }
    }

    private void build() {
        panel(0, 0, WIDTH, HEIGHT, new ColorRGBA(0.003f, 0.007f, 0.021f, 0.9f), 60);
        frame(0, 0, WIDTH, HEIGHT, materials.flat(Materials.rgb(GOLD)), 61);
        frame(8, 8, WIDTH - 16, HEIGHT - 16,
                materials.flat(new ColorRGBA(0.38f, 0.32f, 0.22f, 0.55f)), 61);
        compass(WIDTH / 2, 589);
        centered(display, "BATTLESHIP", 48, WIDTH / 2, 545, IVORY);
        centered(body, "FANTASY NAVAL STRATEGY", 10, WIDTH / 2, 492, GOLD);
        divider(469);
        button(28, 380, 344, 66, "PVP", Action.PVP, true);
        centered(body, "ONLINE MATCH", 10, WIDTH / 2, 398, CYAN);
        button(28, 311, 165, 52, "PVE / VS AI", Action.PVE, false);
        button(207, 311, 165, 52, "LEADERBOARD", Action.LEADERBOARD, false);
        button(28, 247, 165, 52, "CREATE ROOM", Action.CREATE_ROOM, false);
        button(207, 247, 165, 52, "JOIN ROOM", Action.JOIN_ROOM, false);
        button(28, 183, 165, 52, "FRIENDS", Action.FRIENDS, false);
        button(207, 183, 165, 52, "PROFILE", Action.PROFILE, false);
        button(28, 119, 165, 52, "SETTINGS", Action.SETTINGS, false);
        button(207, 119, 165, 52, "EXIT", Action.EXIT, false);
        divider(85);
        centered(display, "Two fleets. One ocean.", 20, WIDTH / 2, 60, 0xB8C1D5);
    }

    private void button(float x, float y, float width, float height, String caption,
                        Action action, boolean primary) {
        Material fill = materials.flat(primary ? new ColorRGBA(0.029f, 0.068f, 0.109f, 0.97f)
                : new ColorRGBA(0.009f, 0.022f, 0.044f, 0.9f));
        panel(x, y, width, height, fill, 62);
        Material border = materials.flat(Materials.rgb(primary ? GOLD : 0x516275));
        frame(x, y, width, height, border, 63);
        BitmapText label = centered(primary ? display : body, caption, primary ? 28 : 12,
                x + width / 2, y + (primary ? 55 : 32), primary ? IVORY : 0xCFD5E1);
        buttons.add(new Button(x, y, width, height, action, fill, border, label));
    }

    private void compass(float x, float y) {
        Material gold = materials.flat(Materials.rgb(GOLD));
        Material muted = materials.flat(Materials.rgb(0x546F87));
        for (int i = 0; i < 32; i++) {
            float a = i * FastMath.TWO_PI / 32;
            float b = (i + 1) * FastMath.TWO_PI / 32;
            line(x + FastMath.cos(a) * 24, y + FastMath.sin(a) * 24,
                    x + FastMath.cos(b) * 24, y + FastMath.sin(b) * 24, muted, 62);
        }
        for (int i = 0; i < 8; i++) {
            float angle = i * FastMath.QUARTER_PI;
            float length = i % 2 == 0 ? 34 : 19;
            float px = x + FastMath.cos(angle) * length;
            float py = y + FastMath.sin(angle) * length;
            float ax = x + FastMath.cos(angle + FastMath.HALF_PI) * 4;
            float ay = y + FastMath.sin(angle + FastMath.HALF_PI) * 4;
            float bx = x + FastMath.cos(angle - FastMath.HALF_PI) * 4;
            float by = y + FastMath.sin(angle - FastMath.HALF_PI) * 4;
            line(ax, ay, px, py, gold, 63);
            line(px, py, bx, by, gold, 63);
        }
        Geometry center = panel(x, y - 3.5f, 5, 5, Materials.rgb(CYAN), 64);
        center.rotate(0, 0, FastMath.QUARTER_PI);
        line(28, y, x - 46, y, muted, 62);
        line(x + 46, y, WIDTH - 28, y, muted, 62);
    }

    private void divider(float y) {
        Material muted = materials.flat(Materials.rgb(0x516275));
        line(28, y, WIDTH / 2 - 12, y, muted, 61);
        line(WIDTH / 2 + 12, y, WIDTH - 28, y, muted, 61);
        Geometry diamond = panel(WIDTH / 2, y - 3.5f, 5, 5, Materials.rgb(GOLD), 62);
        diamond.rotate(0, 0, FastMath.QUARTER_PI);
    }

    private void frame(float x, float y, float width, float height, Material material, float depth) {
        line(x + 10, y, x + width - 10, y, material, depth);
        line(x + 10, y + height, x + width - 10, y + height, material, depth);
        line(x, y + 10, x, y + height - 10, material, depth);
        line(x + width, y + 10, x + width, y + height - 10, material, depth);
        for (int sx : new int[] {-1, 1}) {
            for (int sy : new int[] {-1, 1}) {
                float px = sx < 0 ? x : x + width;
                float py = sy < 0 ? y : y + height;
                line(px, py - sy * 10, px - sx * 10, py, material, depth);
            }
        }
    }

    private void line(float x, float y, float endX, float endY, Material material, float depth) {
        float dx = endX - x, dy = endY - y;
        Geometry geometry = panel(x, y, FastMath.sqrt(dx * dx + dy * dy), 0.8f, material, depth);
        geometry.rotate(0, 0, FastMath.atan2(dy, dx));
    }

    private BitmapText centered(BitmapFont font, String value, float size, float x, float y, int color) {
        BitmapText label = new BitmapText(font);
        label.setText(value);
        label.setSize(size);
        label.setColor(Materials.rgb(color));
        label.setLocalTranslation(x - label.getLineWidth() / 2, y, 65);
        layout.attachChild(label);
        return label;
    }

    private Geometry panel(float x, float y, float width, float height, ColorRGBA color, float depth) {
        return panel(x, y, width, height, materials.flat(color), depth);
    }

    private Geometry panel(float x, float y, float width, float height, Material material, float depth) {
        Geometry geometry = new Geometry("Menu ornament", new Quad(width, height));
        material.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        geometry.setMaterial(material);
        geometry.setQueueBucket(Bucket.Gui);
        geometry.setLocalTranslation(x, y, depth);
        layout.attachChild(geometry);
        return geometry;
    }

    private record Button(float x, float y, float width, float height, Action action,
                          Material fill, Material border, BitmapText label) {}
}
