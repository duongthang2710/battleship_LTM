package com.battleship.frontend.settings;

import com.battleship.frontend.graphics.Materials;
import com.jme3.asset.AssetManager;
import com.jme3.font.*;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.*;
import com.jme3.scene.*;
import com.jme3.scene.shape.Quad;
import java.util.*;

public final class SettingsPanel {
    public enum Action { MODE, SIZE_PREVIOUS, SIZE_NEXT, ROTATION_DOWN, ROTATION_UP, ZOOM_DOWN, ZOOM_UP, SAVE, RESET, BACK }
    private record Button(Action action, float x, float y, float width) {}
    private static final float WIDTH = 1080, HEIGHT = 690;
    private static final int GOLD = 0xDEB663, IVORY = 0xF4EAD1, CYAN = 0x91CEFF, MUTED = 0xA3AEC2;
    private final Node root = new Node("Settings");
    private final Node card = new Node("Captain's preferences");
    private final Materials materials;
    private final BitmapFont display, body;
    private final List<Button> buttons = new ArrayList<>();
    private ClientPreferences draft = ClientPreferences.defaults();
    private List<ClientPreferences.Resolution> sizes = ClientPreferences.RESOLUTIONS;
    private boolean visible, busy, displayAvailable;
    private String status = "Loading settings...";
    private float scale = 1, left, bottom;

    public SettingsPanel(AssetManager assets) {
        materials = new Materials(assets); display = assets.loadFont("fonts/CormorantGaramond.fnt"); body = assets.loadFont("fonts/Manrope.fnt");
        root.attachChild(card); resize(1440, 900); rebuild(); hide();
    }
    public Node root() { return root; }
    public boolean visible() { return visible; }
    public ClientPreferences draft() { return draft; }
    public String status() { return status; }
    public void show() { visible = true; root.setCullHint(Spatial.CullHint.Never); }
    public void hide() { visible = false; root.setCullHint(Spatial.CullHint.Always); }
    public void setData(ClientPreferences value, List<ClientPreferences.Resolution> sizes, boolean displayAvailable, boolean busy, String status) {
        draft = value; this.sizes = sizes; this.displayAvailable = displayAvailable; this.busy = busy; this.status = status; rebuild();
    }
    public void message(String value, boolean busy) { status = value; this.busy = busy; rebuild(); }
    public void resize(int width, int height) {
        scale = Math.min(width / 1440f, height / 900f); left = (width - WIDTH * scale) / 2; bottom = (height - HEIGHT * scale) / 2;
        card.setLocalScale(scale); card.setLocalTranslation(left, bottom, 0);
    }
    public boolean enabled(Action action) {
        if (busy) return false;
        return switch (action) {
            case MODE -> displayAvailable;
            case SIZE_PREVIOUS, SIZE_NEXT -> displayAvailable && !draft.fullscreen() && sizes.size() > 1;
            case ROTATION_DOWN -> draft.rotation() > .5f;
            case ROTATION_UP -> draft.rotation() < 2;
            case ZOOM_DOWN -> draft.zoom() > .5f;
            case ZOOM_UP -> draft.zoom() < 2;
            default -> true;
        };
    }
    public Optional<Action> actionAt(Vector2f point) {
        if (!visible) return Optional.empty();
        float x = (point.x - left) / scale, y = (point.y - bottom) / scale;
        return buttons.stream().filter(b -> enabled(b.action) && x >= b.x && x <= b.x + b.width && y >= b.y && y <= b.y + 36).map(Button::action).findFirst();
    }
    public Vector2f buttonCenter(Action action) {
        var b = buttons.stream().filter(v -> v.action == action).findFirst().orElseThrow();
        return new Vector2f(left + (b.x + b.width / 2) * scale, bottom + (b.y + 18) * scale);
    }
    public void edit(Action action) {
        if (!enabled(action)) return;
        var size = new ClientPreferences.Resolution(draft.width(), draft.height());
        boolean fullscreen = draft.fullscreen(); float rotation = draft.rotation(), zoom = draft.zoom();
        switch (action) {
            case MODE -> fullscreen = !fullscreen;
            case SIZE_PREVIOUS, SIZE_NEXT -> size = sizes.get(Math.floorMod(sizes.indexOf(size) + (action == Action.SIZE_NEXT ? 1 : -1), sizes.size()));
            case ROTATION_DOWN -> rotation -= .25f;
            case ROTATION_UP -> rotation += .25f;
            case ZOOM_DOWN -> zoom -= .25f;
            case ZOOM_UP -> zoom += .25f;
            default -> { return; }
        }
        draft = new ClientPreferences(size.width(), size.height(), fullscreen, rotation, zoom); status = "Apply and save to use these changes."; rebuild();
    }
    private void rebuild() {
        card.detachAllChildren(); buttons.clear();
        rect(0, 0, WIDTH, HEIGHT, new ColorRGBA(.003f, .009f, .025f, .97f)); frame(0, 0, WIDTH, HEIGHT, GOLD); frame(8, 8, WIDTH - 16, HEIGHT - 16, 0x655638);
        text(body, "THE CAPTAIN'S PREFERENCES", 11, 30, 655, GOLD); text(display, "SETTINGS", 43, 28, 630, IVORY);
        text(body, "Apply and save changes. Back discards unsaved edits.", 13, 30, 580, MUTED);
        text(display, "Display", 27, 30, 535, IVORY);
        text(body, "WINDOW MODE", 12, 32, 502, GOLD);
        button(Action.MODE, 235, 477, 275, draft.fullscreen() ? "FULLSCREEN / BORDERLESS" : "WINDOWED");
        text(body, "WINDOWED SIZE", 12, 32, 443, GOLD);
        button(Action.SIZE_PREVIOUS, 235, 418, 40, "<");
        text(body, draft.width() + " x " + draft.height(), 14, 302, 442, CYAN);
        button(Action.SIZE_NEXT, 470, 418, 40, ">");
        text(body, draft.fullscreen() ? "Fullscreen uses the monitor's native size." : "Minimum supported window: 960 x 600.", 12, 32, 397, MUTED);
        text(display, "Camera controls", 27, 30, 353, IVORY);
        row("ORBIT SENSITIVITY", draft.rotation(), 298, Action.ROTATION_DOWN, Action.ROTATION_UP);
        row("ZOOM SENSITIVITY", draft.zoom(), 243, Action.ZOOM_DOWN, Action.ZOOM_UP);
        text(display, "Audio", 27, 30, 203, IVORY);
        text(body, "No music or sound sources are present in this build.", 13, 32, 174, MUTED);
        text(body, "Volume controls are unavailable until audio is added.", 13, 32, 155, MUTED);
        text(display, "Controls", 27, 565, 535, IVORY);
        String[] controls = {"Left drag: orbit  /  Wheel: zoom", "R: restore your default view", "", "Placement", "1-5: select ship  /  Click: place", "Drag a placed ship to move it", "T: rotate  /  Delete: remove", "C: reset  /  F: random fleet", "Enter: confirm a complete fleet", "", "Battle", "Click enemy cell: fire  /  Tab: switch board", "Both players Ready to begin", "60 seconds per turn; expiry loses the match", "", "Chat", "Enter: focus / send  /  Esc: leave input", "Wheel, PgUp/PgDn: read older messages", "+ / -: expand or collapse Battle chat"};
        for (int i = 0; i < controls.length; i++) text(body, controls[i], 13, 567, 499 - i * 20, i == 3 || i == 10 || i == 15 ? GOLD : IVORY);
        text(body, status.length() > 132 ? status.substring(0, 129) + "..." : status, 12, 32, 100, CYAN);
        button(Action.SAVE, 32, 28, 240, "APPLY & SAVE"); button(Action.RESET, 290, 28, 200, "RESET TO SAVED"); button(Action.BACK, 840, 28, 208, "BACK");
    }
    private void row(String label, float value, float y, Action down, Action up) {
        text(body, label, 12, 32, y + 24, GOLD); button(down, 235, y, 40, "-");
        text(body, Math.round(value * 100) + "%", 14, 331, y + 24, CYAN); button(up, 470, y, 40, "+");
    }
    private void button(Action action, float x, float y, float w, String value) {
        buttons.add(new Button(action, x, y, w)); rect(x, y, w, 36, new ColorRGBA(.025f, .055f, .095f, 1)); frame(x, y, w, 36, enabled(action) ? 0x516275 : 0x334050);
        var label = text(body, value, 13, 0, y + 24, enabled(action) ? IVORY : 0x69788D); label.setLocalTranslation(x + (w - label.getLineWidth()) / 2, y + 24, 94);
    }
    private void frame(float x, float y, float w, float h, int color) {
        ColorRGBA c = Materials.rgb(color); rect(x, y, w, .8f, c); rect(x, y + h, w, .8f, c); rect(x, y, .8f, h, c); rect(x + w, y, .8f, h, c);
    }
    private void rect(float x, float y, float w, float h, ColorRGBA color) {
        var material = materials.flat(color); material.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        Geometry geometry = new Geometry("Settings detail", new Quad(w, h)); geometry.setMaterial(material); geometry.setLocalTranslation(x, y, 90 + card.getQuantity() * .001f); card.attachChild(geometry);
    }
    private BitmapText text(BitmapFont font, String value, float size, float x, float y, int color) {
        var label = new BitmapText(font); label.setText(value); label.setSize(size); label.setColor(Materials.rgb(color)); label.setLocalTranslation(x, y, 94); card.attachChild(label); return label;
    }
}
