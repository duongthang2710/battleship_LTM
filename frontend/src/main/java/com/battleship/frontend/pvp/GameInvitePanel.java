package com.battleship.frontend.pvp;

import com.battleship.frontend.graphics.Materials;
import com.battleship.frontend.ui.UiTypography;
import com.battleship.shared.room.*;
import com.jme3.asset.AssetManager;
import com.jme3.font.*;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.*;
import com.jme3.scene.*;
import com.jme3.scene.shape.Quad;
import java.util.*;

/** Small global notification above existing menus; it never owns a room or a socket. */
public final class GameInvitePanel {
    public enum Action { ACCEPT, DECLINE, DISMISS }
    private static final float WIDTH = 432, HEIGHT = 176;
    private static final float BUTTON_WIDTH = 138, BUTTON_Y = 16, BUTTON_HEIGHT = 32;
    private final Node root = new Node("PvP invitation notification");
    private final Node card = new Node("PvP invitation card");
    private final Materials materials;
    private final BitmapFont display, body;
    private final Map<Action, Float> buttons = new EnumMap<>(Action.class);
    private BitmapText countdown;
    private boolean visible, incoming, pending, busy;
    private long seconds = -1;
    private float scale = 1, left, bottom;

    public GameInvitePanel(AssetManager assets) {
        materials = new Materials(assets); display = UiTypography.display(assets); body = UiTypography.body(assets);
        root.attachChild(card); resize(1440, 900); hide();
    }
    public Node root() { return root; }
    public boolean visible() { return visible; }
    public long seconds() { return seconds; }
    public void hide() { visible = false; root.setCullHint(Spatial.CullHint.Always); }
    public void resize(int width, int height) {
        scale = UiTypography.scale(width, height);
        left = width - (WIDTH + 36) * scale; bottom = height - (HEIGHT + 36) * scale;
        card.setLocalScale(scale); card.setLocalTranslation(left, bottom, 0);
    }
    public void show(GameInvite invite, boolean incoming, boolean busy, long millis) {
        this.incoming = incoming; this.busy = busy; pending = invite.status() == GameInviteStatus.PENDING;
        build(pending ? incoming ? "INVITATION TO BATTLE" : "INVITATION SENT" : invite.status().name(),
                (incoming ? invite.inviter() : invite.recipient()).displayName() + "  @"
                        + (incoming ? invite.inviter() : invite.recipient()).username(), invite.message());
        updateCountdown(millis);
    }
    public void waiting() { incoming = pending = false; busy = true; build("PVP INVITATION", "Sending invitation...", "Waiting for server confirmation."); }
    public void error(String message) { incoming = pending = busy = false; build("PVP INVITATION", "Unable to complete the invitation", message); }
    private void build(String title, String name, String message) {
        visible = true; root.setCullHint(Spatial.CullHint.Never); card.detachAllChildren(); buttons.clear(); seconds = -1;
        rect(0, 0, WIDTH, HEIGHT, new ColorRGBA(.003f, .009f, .025f, .99f));
        frame(0, 0, WIDTH, HEIGHT, 0xDEB663); frame(6, 6, WIDTH - 12, HEIGHT - 12, 0x655638);
        UiTypography.fit(text(display, title, 24, 18, HEIGHT - 18, 0xF4EAD1), WIDTH - 100);
        UiTypography.fit(text(body, name, 12, 18, HEIGHT - 51, 0xDEB663), WIDTH - 36);
        text(body, wrapMessage(message), 11, 18, HEIGHT - 78, 0x91CEFF);
        countdown = text(body, "", 13, WIDTH - 68, HEIGHT - 20, 0x91CEFF);
        if (pending && incoming) { button(Action.ACCEPT, 18, "ACCEPT"); button(Action.DECLINE, 172, "DECLINE"); }
        else button(Action.DISMISS, 18, "CLOSE");
    }
    public void updateCountdown(long millis) {
        long next = Math.max(0, (millis + 999) / 1000);
        if (next == seconds || countdown == null) return;
        seconds = next; countdown.setText(pending ? String.format(Locale.ROOT, "%02d:%02d", next / 60, next % 60) : "");
    }
    public boolean contains(Vector2f point) {
        return visible && point.x >= left && point.x <= left + WIDTH * scale && point.y >= bottom && point.y <= bottom + HEIGHT * scale;
    }
    public Optional<Action> actionAt(Vector2f point) {
        if (!contains(point) || busy) return Optional.empty();
        float x = (point.x - left) / scale, y = (point.y - bottom) / scale;
        if (y < BUTTON_Y || y > BUTTON_Y + BUTTON_HEIGHT) return Optional.empty();
        return buttons.entrySet().stream().filter(b -> x >= b.getValue() && x <= b.getValue() + BUTTON_WIDTH
                && (b.getKey() == Action.DISMISS || pending && incoming && seconds > 0)).map(Map.Entry::getKey).findFirst();
    }
    public Vector2f buttonCenter(Action action) {
        Float x = buttons.get(action);
        if (x == null) throw new IllegalArgumentException("Invitation button not shown: " + action);
        return new Vector2f(left + (x + BUTTON_WIDTH / 2) * scale, bottom + (BUTTON_Y + BUTTON_HEIGHT / 2) * scale);
    }
    private void button(Action action, float x, String caption) {
        buttons.put(action, x); rect(x, BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT, new ColorRGBA(.025f, .055f, .095f, 1));
        frame(x, BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT, busy ? 0x516275 : 0xDEB663);
        UiTypography.centerIn(text(body, caption, 12, x, BUTTON_Y, busy ? 0x69788D : 0xF4EAD1), x, BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT);
    }
    private String wrapMessage(String value) {
        String message = UiTypography.normalize(value).replaceAll("\\s+", " ").strip();
        BitmapText probe = new BitmapText(body); probe.setSize(UiTypography.bodySize(11));
        probe.setText(message);
        if (probe.getLineWidth() <= WIDTH - 36) return message;
        int end = message.length();
        while (end > 0 && probe.getLineWidth() > WIDTH - 36) {
            end = message.offsetByCodePoints(end, -1); probe.setText(message.substring(0, end));
        }
        int word = message.lastIndexOf(' ', end);
        if (word > 0) end = word;
        String first = message.substring(0, end).strip();
        probe.setText(message.substring(end).strip()); UiTypography.fit(probe, WIDTH - 36);
        return first + "\n" + probe.getText();
    }

    private void frame(float x, float y, float w, float h, int color) {
        ColorRGBA c = Materials.rgb(color);
        rect(x, y, w, .8f, c); rect(x, y + h, w, .8f, c); rect(x, y, .8f, h, c); rect(x + w, y, .8f, h, c);
    }
    private void rect(float x, float y, float w, float h, ColorRGBA color) {
        var material = materials.flat(color); material.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        Geometry geometry = new Geometry("Invitation detail", new Quad(w, h)); geometry.setMaterial(material);
        geometry.setLocalTranslation(x, y, 200 + card.getQuantity() * .01f); card.attachChild(geometry);
    }
    private BitmapText text(BitmapFont font, String value, float size, float x, float y, int color) {
        BitmapText label = new BitmapText(font); label.setText(UiTypography.normalize(value)); label.setSize(font == body ? UiTypography.bodySize(size) : size); label.setColor(Materials.rgb(color));
        label.setLocalTranslation(x, y, 205); card.attachChild(label); return label;
    }
}
