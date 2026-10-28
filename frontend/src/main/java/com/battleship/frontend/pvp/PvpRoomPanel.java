package com.battleship.frontend.pvp;

import com.battleship.frontend.graphics.Materials;
import com.battleship.frontend.ui.UiTypography;
import com.battleship.frontend.ui.UiTextField;
import com.battleship.frontend.ui.UiShapes;
import com.battleship.shared.room.RoomPlayer;
import com.battleship.shared.room.RoomSnapshot;
import com.battleship.shared.room.RoomPhase;
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

public final class PvpRoomPanel {
    public enum Action {
        CREATE_ROOM, SHOW_JOIN, JOIN_ROOM, BACK, INVITE_FRIENDS, TOGGLE_READY, LEAVE_ROOM, COPY_CODE
    }

    private enum View { CHOICE, JOIN, WAITING }

    private static final float WIDTH = 440;
    private static final float HEIGHT = 580;
    private static final int GOLD = 0xDEB663;
    private static final int IVORY = 0xF4EAD1;
    private static final int CYAN = 0x91CEFF;
    private final Node root = new Node("PVP room menu");
    private final Node layout = new Node("PVP room card");
    private final Materials materials;
    private final BitmapFont display;
    private final BitmapFont body;
    private final List<Button> buttons = new ArrayList<>();
    private View view = View.CHOICE;
    private RoomSnapshot room;
    private String localClientId;
    private String code = "";
    private String status = "";
    private boolean connected;
    private boolean connecting;
    private boolean busy;
    private boolean visible;
    private float scale = 1;
    private float left;
    private float bottom;
    private final UiTextField codeField;
    private BitmapText statusLabel;
    private Action hovered;

    public PvpRoomPanel(AssetManager assets) {
        materials = new Materials(assets);
        display = UiTypography.display(assets);
        body = UiTypography.body(assets);
        codeField = new UiTextField(body, materials, 344, 72, 29, "Room code", true);
        root.attachChild(layout);
        rebuild();
        resize(1440, 900);
        hide();
    }

    public Node root() { return root; }
    public boolean visible() { return visible; }
    public String roomCode() { return code; }
    public boolean acceptsRoomCode() { return visible && view == View.JOIN && !busy; }

    public void resize(int width, int height) {
        scale = UiTypography.scale(width, height);
        left = 56 * scale;
        bottom = (height - HEIGHT * scale) / 2;
        layout.setLocalScale(scale);
        layout.setLocalTranslation(left, bottom, 0);
    }

    public void showChoice() {
        view = View.CHOICE;
        room = null;
        show();
    }

    public void showJoin() {
        if (view != View.JOIN) code = "";
        view = View.JOIN;
        show();
    }

    public void showRoom(RoomSnapshot room, String localClientId) {
        this.room = room;
        this.localClientId = localClientId;
        view = View.WAITING;
        show();
    }

    private void show() {
        visible = true;
        root.setCullHint(CullHint.Never);
        rebuild();
    }

    public void hide() {
        visible = false;
        root.setCullHint(CullHint.Always);
    }

    public void setConnection(boolean connected, boolean connecting) {
        if (this.connected == connected && this.connecting == connecting) return;
        this.connected = connected;
        this.connecting = connecting;
        rebuild();
    }

    public void setBusy(boolean busy) {
        if (this.busy == busy) return;
        this.busy = busy;
        refreshButtons();
    }

    public void setStatus(String status) {
        this.status = status == null ? "" : status;
        if (statusLabel != null) {
            statusLabel.setText(wrapStatus(this.status));
            center(statusLabel, 107);
        }
    }

    public void inputCharacter(char character) {
        if (!acceptsRoomCode() || code.length() >= 6) return;
        char upper = Character.toUpperCase(character);
        if ((upper >= 'A' && upper <= 'Z') || (upper >= '2' && upper <= '9')) {
            code += upper;
            refreshCode();
        }
    }

    public void backspace() {
        if (!acceptsRoomCode() || code.isEmpty()) return;
        code = code.substring(0, code.length() - 1);
        refreshCode();
    }

    public boolean enabled(Action action) {
        return switch (action) {
            case INVITE_FRIENDS -> connected && !busy && room != null && room.phase() == RoomPhase.WAITING && room.players().size() < 2;
            case BACK -> !busy;
            case COPY_CODE -> room != null && view == View.WAITING;
            case CREATE_ROOM, SHOW_JOIN -> connected && !busy;
            case JOIN_ROOM -> connected && !busy && code.length() == 6;
            case LEAVE_ROOM -> connected && !busy && room != null;
            case TOGGLE_READY -> connected && !busy && room != null && room.phase() == RoomPhase.WAITING;
        };
    }

    public Optional<Action> actionAt(Vector2f screen) {
        if (!visible) return Optional.empty();
        float x = (screen.x - left) / scale;
        float y = (screen.y - bottom) / scale;
        for (Button button : buttons) {
            if (enabled(button.action) && x >= button.x && x <= button.x + button.width
                    && y >= button.y && y <= button.y + button.height) return Optional.of(button.action);
        }
        return Optional.empty();
    }

    public Vector2f buttonCenter(Action action) {
        for (Button button : buttons) {
            if (button.action == action) return new Vector2f(left + (button.x + button.width / 2) * scale,
                    bottom + (button.y + button.height / 2) * scale);
        }
        throw new IllegalArgumentException("Button not shown in this view: " + action);
    }

    public void update(Vector2f cursor) {
        if (!visible) return;
        Action next = actionAt(cursor).orElse(null);
        if (next == hovered) return;
        hovered = next;
        refreshButtons();
    }

    private void rebuild() {
        layout.detachAllChildren();
        buttons.clear();
        panel(0, 0, WIDTH, HEIGHT, new ColorRGBA(0.003f, 0.007f, 0.021f, 0.94f), 70);
        frame(0, 0, WIDTH, HEIGHT, materials.flat(Materials.rgb(GOLD)), 71);
        frame(8, 8, WIDTH - 16, HEIGHT - 16,
                materials.flat(new ColorRGBA(0.38f, 0.32f, 0.22f, 0.55f)), 71);
        diamond(WIDTH / 2, 548, GOLD);
        centered(display, view == View.WAITING ? "WAITING ROOM" : "PVP", 39, WIDTH / 2, 516, IVORY);
        centered(body, connecting ? "CONNECTING TO SERVER" : connected ? "SERVER CONNECTED" : "SERVER DISCONNECTED",
                10, WIDTH / 2, 468, connected ? CYAN : 0xB4B7C7);
        divider(445);
        if (view == View.CHOICE) {
            centered(display, "Two admirals. One ocean.", 23, WIDTH / 2, 404, IVORY);
            button(34, 304, 372, 56, "CREATE ROOM", Action.CREATE_ROOM, true);
            button(34, 232, 372, 56, "JOIN ROOM", Action.SHOW_JOIN, false);
            button(34, 28, 372, 42, "MAIN MENU", Action.BACK, false);
        } else if (view == View.JOIN) {
            centered(body, "ENTER ROOM CODE", 11, WIDTH / 2, 393, GOLD);
            panel(34, 290, 372, 72, new ColorRGBA(0.014f, 0.038f, 0.072f, 1), 72).setMesh(UiShapes.chamfered(372, 72, 9));
            frame(34, 290, 372, 72, materials.flat(Materials.rgb(0x516275)), 73);
            codeField.setContent(codeDisplay(), acceptsRoomCode()); codeField.setLocalTranslation(48, 290, 75); layout.attachChild(codeField);
            centered(body, "6 characters / Enter to join", 10, WIDTH / 2, 268, 0xAFBBD0);
            button(34, 182, 372, 56, "JOIN ROOM", Action.JOIN_ROOM, true);
            button(34, 28, 372, 42, "BACK", Action.BACK, false);
        } else {
            centered(body, "ROOM CODE", 10, WIDTH / 2, 414, GOLD);
            centered(body, room.roomCode(), 32, 192, 388, CYAN);
            button(314, 354, 72, 34, "COPY", Action.COPY_CODE, false);
            player(0, 286);
            player(1, 208);
            boolean ready = room.players().stream().anyMatch(player -> player.clientId().equals(localClientId) && player.ready());
            button(34, 132, 372, 48, ready ? "NOT READY" : "READY", Action.TOGGLE_READY, true);
            button(34, 28, 180, 42, "INVITE FRIENDS", Action.INVITE_FRIENDS, false);
            button(226, 28, 180, 42, "LEAVE ROOM", Action.LEAVE_ROOM, false);
        }
        statusLabel = centered(body, wrapStatus(status), 11, WIDTH / 2, 107, 0xB9C7DB);
        refreshButtons();
    }

    private void player(int index, float y) {
        RoomPlayer player = room.players().size() > index ? room.players().get(index) : null;
        panel(34, y, 372, 66, new ColorRGBA(0.012f, 0.028f, 0.052f, 0.95f), 72);
        text(body, "PLAYER " + (index + 1), 10, 48, y + 49, GOLD);
        String name = player == null ? "Waiting for opponent" : player.playerName();
        if (name.length() > 22) name = name.substring(0, 21) + ".";
        text(display, name, 23, 48, y + 29, player == null ? 0x8793A9 : IVORY);
        if (player != null) {
            String readiness = player.ready() ? "READY" : "NOT READY";
            text(body, readiness, 10, player.ready() ? 335 : 309, y + 29,
                    player.ready() ? CYAN : 0xA3ACC0);
        }
    }

    public void updateCaret(float delta) { codeField.setContent(codeDisplay(), acceptsRoomCode()); codeField.update(delta); }
    public boolean caretVisible() { return codeField.caretVisible(); }
    private void refreshCode() { codeField.setContent(codeDisplay(), acceptsRoomCode()); refreshButtons(); }
    private String codeDisplay() { return String.join(" ", code.split("")); }

    private void refreshButtons() {
        for (Button button : buttons) {
            boolean active = enabled(button.action);
            boolean highlight = active && (button.primary || button.action == hovered);
            button.fill.setColor("Color", highlight ? new ColorRGBA(0.035f, 0.075f, 0.117f, 0.98f)
                    : new ColorRGBA(0.009f, 0.022f, 0.044f, 0.94f));
            button.border.setColor("Color", Materials.rgb(!active ? 0x334050 : highlight ? GOLD : 0x516275));
            button.label.setColor(Materials.rgb(!active ? 0x69788D : highlight ? IVORY : 0xCFD5E1));
        }
    }

    private void button(float x, float y, float width, float height, String caption, Action action, boolean primary) {
        Material fill = materials.flat(ColorRGBA.Black);
        panel(x, y, width, height, fill, 72).setMesh(UiShapes.chamfered(width, height, 9));
        Material border = materials.flat(Materials.rgb(GOLD));
        frame(x, y, width, height, border, 73);
        BitmapText label = centered(body, caption, 12, x + width / 2, y, IVORY);
        UiTypography.centerIn(label, x, y, width, height);
        buttons.add(new Button(x, y, width, height, action, primary, fill, border, label));
    }

    private static String wrapStatus(String value) {
        if (value.length() <= 48) return value;
        int split = value.lastIndexOf(' ', 48);
        if (split < 1) split = 48;
        String tail = value.substring(split).strip();
        return value.substring(0, split) + "\n" + (tail.length() > 50 ? tail.substring(0, 47) + "..." : tail);
    }

    private void divider(float y) {
        Material muted = materials.flat(Materials.rgb(0x516275));
        line(28, y, WIDTH / 2 - 12, y, muted, 71);
        line(WIDTH / 2 + 12, y, WIDTH - 28, y, muted, 71);
        diamond(WIDTH / 2, y, GOLD);
    }

    private void diamond(float x, float y, int color) {
        Geometry diamond = panel(x, y - 3.5f, 5, 5, Materials.rgb(color), 72);
        diamond.rotate(0, 0, FastMath.QUARTER_PI);
    }

    private void frame(float x, float y, float width, float height, Material material, float depth) {
        line(x + 9, y, x + width - 9, y, material, depth);
        line(x + 9, y + height, x + width - 9, y + height, material, depth);
        line(x, y + 9, x, y + height - 9, material, depth);
        line(x + width, y + 9, x + width, y + height - 9, material, depth);
        for (int sx : new int[] {-1, 1}) for (int sy : new int[] {-1, 1}) {
            float px = sx < 0 ? x : x + width;
            float py = sy < 0 ? y : y + height;
            line(px, py - sy * 9, px - sx * 9, py, material, depth);
        }
    }

    private void line(float x, float y, float endX, float endY, Material material, float depth) {
        float dx = endX - x, dy = endY - y;
        Geometry geometry = panel(x, y, FastMath.sqrt(dx * dx + dy * dy), 0.8f, material, depth);
        geometry.rotate(0, 0, FastMath.atan2(dy, dx));
    }

    private BitmapText centered(BitmapFont font, String value, float size, float x, float y, int color) {
        BitmapText label = text(font, value, size, 0, y, color);
        label.setLocalTranslation(x - label.getLineWidth() / 2, y, 75);
        return label;
    }

    private BitmapText text(BitmapFont font, String value, float size, float x, float y, int color) {
        BitmapText label = new BitmapText(font);
        label.setText(UiTypography.normalize(value));
        label.setSize(font == body ? UiTypography.bodySize(size) : size);
        label.setColor(Materials.rgb(color));
        label.setLocalTranslation(x, y, 75);
        layout.attachChild(label);
        return label;
    }

    private void center(BitmapText label, float y) {
        label.setLocalTranslation((WIDTH - label.getLineWidth()) / 2, y, 75);
    }

    private Geometry panel(float x, float y, float width, float height, ColorRGBA color, float depth) {
        return panel(x, y, width, height, materials.flat(color), depth);
    }

    private Geometry panel(float x, float y, float width, float height, Material material, float depth) {
        Geometry geometry = new Geometry("Room menu detail", new Quad(width, height));
        material.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        geometry.setMaterial(material);
        geometry.setQueueBucket(Bucket.Gui);
        geometry.setLocalTranslation(x, y, depth);
        layout.attachChild(geometry);
        return geometry;
    }

    private record Button(float x, float y, float width, float height, Action action, boolean primary,
                          Material fill, Material border, BitmapText label) {}
}
