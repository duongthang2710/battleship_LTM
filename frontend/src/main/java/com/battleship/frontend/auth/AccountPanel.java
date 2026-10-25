package com.battleship.frontend.auth;

import com.battleship.frontend.graphics.Materials;
import com.battleship.shared.auth.AccountProfile;
import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial.CullHint;
import com.jme3.scene.shape.Quad;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Account form only. All requests go through the existing TCP controller. */
public final class AccountPanel {
    public enum Action { SUBMIT, SWITCH, BACK, LOGOUT, CONNECT, EXIT }
    private static final int GOLD = 0xDEB663, IVORY = 0xF4EAD1, CYAN = 0x91CEFF;
    private final Node root = new Node("Account form");
    private final Node card = new Node("Account card");
    private final Materials materials;
    private final BitmapFont display, body;
    private final List<Button> buttons = new ArrayList<>();
    private final String[] values = {"", "", ""};
    private final BitmapText[] fields = new BitmapText[3];
    private BitmapText statusLabel;
    private boolean register, visible, busy, connected;
    private AccountProfile account;
    private String status = "";
    private int focused;
    private float scale = 1, left, bottom;

    public AccountPanel(AssetManager assets) {
        materials = new Materials(assets);
        display = assets.loadFont("fonts/CormorantGaramond.fnt");
        body = assets.loadFont("fonts/Manrope.fnt");
        root.attachChild(card);
        resize(1440, 900);
        rebuild();
        hide();
    }
    public Node root() { return root; }
    public boolean visible() { return visible; }
    public boolean registering() { return register; }
    public String username() { return values[0]; }
    public String password() { return values[1]; }
    public String confirmation() { return values[2]; }
    public void show() { focused = 0; visible = true; root.setCullHint(CullHint.Never); rebuild(); }
    public void hide() { visible = false; root.setCullHint(CullHint.Always); clearSecrets(); }
    public void switchMode() { register = !register; focused = 0; clearSecrets(); rebuild(); }
    public void clearSecrets() { values[1] = values[2] = ""; refreshFields(); }

    public void sync(AccountProfile profile, String nextStatus, boolean pending, boolean online) {
        boolean changed = !Objects.equals(account, profile) || busy != pending || connected != online;
        account = profile;
        busy = pending;
        connected = online;
        if (changed && visible) rebuild();
        if (!status.equals(nextStatus)) {
            status = nextStatus;
            if (statusLabel != null) statusLabel.setText(wrap(status));
        }
    }
    public void resize(int width, int height) {
        scale = Math.min(width / 1440f, height / 900f);
        left = 56 * scale;
        bottom = (height - 620 * scale) / 2;
        card.setLocalScale(scale);
        card.setLocalTranslation(left, bottom, 0);
    }
    public boolean enabled(Action action) {
        return switch (action) {
            case BACK -> !busy && account != null;
            case EXIT -> !busy && account == null;
            case SWITCH -> !busy && account == null;
            case CONNECT -> !busy && !connected;
            case LOGOUT -> !busy && account != null && connected;
            case SUBMIT -> !busy && connected && account == null && !values[0].isBlank()
                    && !values[1].isEmpty() && (!register || !values[2].isEmpty());
        };
    }
    public Optional<Action> click(Vector2f cursor) {
        if (!visible) return Optional.empty();
        float x = (cursor.x - left) / scale, y = (cursor.y - bottom) / scale;
        if (account == null && !busy && x >= 30 && x <= 410) {
            for (int i = 0; i < (register ? 3 : 2); i++) if (y >= fieldY(i) && y <= fieldY(i) + 48) {
                focused = i; refreshFields(); return Optional.empty();
            }
        }
        for (Button button : buttons) if (enabled(button.action()) && x >= 30 && x <= 410
                && y >= button.y() && y <= button.y() + 42) return Optional.of(button.action());
        return Optional.empty();
    }
    public Vector2f buttonCenter(Action action) {
        Button button = buttons.stream().filter(b -> b.action() == action).findFirst().orElseThrow();
        return new Vector2f(left + 220 * scale, bottom + (button.y() + 21) * scale);
    }
    public void nextField() { if (!busy) { focused = (focused + 1) % (register ? 3 : 2); refreshFields(); } }
    public void backspace() {
        if (!busy && !values[focused].isEmpty()) {
            values[focused] = values[focused].substring(0, values[focused].length() - 1); rebuild();
        }
    }
    public void inputCharacter(char value) {
        if (busy || account != null || Character.isISOControl(value) || Character.isSurrogate(value)) return;
        int limit = focused == 0 ? 24 : 128;
        if (values[focused].length() >= limit) return;
        if (focused == 0 && !(value >= 'a' && value <= 'z' || value >= 'A' && value <= 'Z'
                || value >= '0' && value <= '9' || value == '_')) return;
        values[focused] += value;
        rebuild();
    }
    private float fieldY(int i) { return 408 - i * 84; }
    private void refreshFields() {
        for (int i = 0; i < 3; i++) if (fields[i] != null) {
            String value = i == 0 ? values[i] : "*".repeat(Math.min(28, values[i].length()));
            fields[i].setText(value.isEmpty() ? (i == focused ? "|" : "") : value);
            fields[i].setColor(Materials.rgb(i == focused ? CYAN : IVORY));
        }
    }
    private void rebuild() {
        card.detachAllChildren(); buttons.clear(); java.util.Arrays.fill(fields, null);
        rect(0, 0, 440, 620, new ColorRGBA(.003f, .007f, .021f, .96f), 80);
        outline(0, 0, 440, 620, GOLD);
        outline(8, 8, 424, 604, 0x655638);
        text(display, "BATTLESHIP", 20, 30, 582, GOLD);
        text(display, account != null ? "YOUR ACCOUNT" : register ? "CREATE ACCOUNT" : "SIGN IN", 36, 30, 540, IVORY);
        text(body, "ADMIRAL'S REGISTRY", 10, 32, 501, CYAN);
        if (account == null) {
            String[] captions = {"USERNAME", "PASSWORD", "CONFIRM PASSWORD"};
            for (int i = 0; i < (register ? 3 : 2); i++) {
                float y = fieldY(i);
                text(body, captions[i], 10, 32, y + 65, GOLD);
                rect(30, y, 380, 48, new ColorRGBA(.015f, .038f, .07f, 1), 81);
                outline(30, y, 380, 48, 0x516275);
                fields[i] = text(body, "", 16, 44, y + 31, IVORY);
            }
            text(body, register ? "12-128 characters / Tab to move" : "Tab to move / Enter to sign in", 10, 32,
                    register ? 220 : 290, 0xAFBBD0);
            button(166, !connected ? "CONNECT TO SERVER" : register ? "REGISTER" : "SIGN IN", connected ? Action.SUBMIT : Action.CONNECT);
            button(112, register ? "ALREADY REGISTERED? SIGN IN" : "CREATE AN ACCOUNT", Action.SWITCH);
        } else {
            text(display, account.displayName(), 31, 30, 426, IVORY);
            text(body, "@" + account.username(), 14, 32, 388, CYAN);
            text(body, "Signed in. Your PvP results are saved.", 12, 32, 332, IVORY);
            button(166, "SIGN OUT", Action.LOGOUT);
        }
        statusLabel = text(body, wrap(status), 11, 32, 89, CYAN);
        button(22, account == null ? "EXIT" : "MAIN MENU", account == null ? Action.EXIT : Action.BACK);
        refreshFields();
    }
    private String wrap(String value) {
        if (value.length() <= 48) return value;
        int split = value.lastIndexOf(' ', 48);
        if (split < 1) split = 48;
        return value.substring(0, split) + "\n" + value.substring(split).strip();
    }
    private void button(float y, String caption, Action action) {
        boolean enabled = enabled(action);
        rect(30, y, 380, 42, new ColorRGBA(.02f, .05f, .085f, 1), 81);
        outline(30, y, 380, 42, enabled ? GOLD : 0x334050);
        BitmapText label = text(body, caption, 11, 0, y + 26, enabled ? IVORY : 0x69788D);
        label.setLocalTranslation((440 - label.getLineWidth()) / 2, y + 26, 84);
        buttons.add(new Button(y, action));
    }
    private void outline(float x, float y, float w, float h, int color) {
        ColorRGBA c = Materials.rgb(color);
        rect(x, y, w, .8f, c, 82); rect(x, y + h, w, .8f, c, 82);
        rect(x, y, .8f, h, c, 82); rect(x + w, y, .8f, h, c, 82);
    }
    private void rect(float x, float y, float w, float h, ColorRGBA color, float z) {
        Geometry geometry = new Geometry("Account panel detail", new Quad(w, h));
        Material material = materials.flat(color);
        material.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        geometry.setMaterial(material); geometry.setLocalTranslation(x, y, z); card.attachChild(geometry);
    }
    private BitmapText text(BitmapFont font, String value, float size, float x, float y, int color) {
        BitmapText label = new BitmapText(font);
        label.setText(value); label.setSize(size); label.setColor(Materials.rgb(color));
        label.setLocalTranslation(x, y, 84); card.attachChild(label); return label;
    }
    private record Button(float y, Action action) {}
}
