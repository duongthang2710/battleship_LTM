package com.battleship.frontend.game;

import com.battleship.frontend.graphics.Materials;
import com.battleship.frontend.ui.UiTypography;
import com.battleship.frontend.ui.UiShapes;
import com.battleship.shared.game.GamePhase;
import com.battleship.shared.game.GameState;
import com.battleship.shared.game.ShipOrientation;
import com.battleship.shared.game.ShipType;
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
import com.jme3.scene.shape.Quad;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class PlacementHud {
    public enum Action {
        SELECT_CARRIER, SELECT_BATTLESHIP, SELECT_CRUISER, SELECT_SUBMARINE,
        SELECT_DESTROYER, ROTATE, REMOVE, RESET, RANDOM, READY, TOGGLE_BOARD, RESTART, LEAVE_ROOM
    }

    private static final int GOLD = 0xDEB663;
    private static final int IVORY = 0xF4EAD1;
    private static final int CYAN = 0x91CEFF;
    private final Node root = new Node("Placement HUD");
    private final Node layout = new Node("Placement controls");
    private final Materials materials;
    private final BitmapFont display;
    private final BitmapFont body;
    private final List<Button> buttons = new ArrayList<>();
    private GameState state;
    private ShipType selected;
    private ShipOrientation orientation;
    private String message = "";
    private String signature = "";
    private boolean enemyView;
    private boolean networkPlacement;
    private boolean networkLocked;
    private float scale = 1;
    private float top;
    private float panelHeight;

    public PlacementHud(AssetManager assets) {
        materials = new Materials(assets);
        display = UiTypography.display(assets);
        body = UiTypography.body(assets);
        root.attachChild(layout);
    }

    public Node root() { return root; }

    public void setNetworkPlacement(boolean value) {
        if (networkPlacement == value) return;
        networkPlacement = value;
        signature = "";
        rebuild();
    }

    public void setNetworkLocked(boolean value) {
        if (networkLocked == value) return;
        networkLocked = value;
        rebuild();
    }

    public void resize(int width, int height) {
        scale = UiTypography.scale(width, height);
        top = height / scale - 148;
        layout.setLocalScale(scale);
        rebuild();
    }

    public void update(GameState state, String message, boolean enemyView) {
        ShipType selected = state.placement().selectedType();
        ShipOrientation orientation = state.placement().orientation();
        int placedMask = 0;
        for (ShipType type : ShipType.values()) {
            if (state.player().board().ship(type) != null) placedMask |= 1 << type.ordinal();
        }
        String next = state.phase() + ":" + selected + ":" + orientation + ":"
                + placedMask + ":" + message + ":" + enemyView;
        this.state = state;
        this.selected = selected;
        this.orientation = orientation;
        this.message = message == null ? "" : message;
        this.enemyView = enemyView;
        if (!next.equals(signature)) {
            signature = next;
            rebuild();
        }
    }

    public boolean contains(Vector2f screen) {
        return state != null && screen.x / scale >= 36 && screen.x / scale <= 291
                && screen.y / scale >= top - panelHeight && screen.y / scale <= top;
    }

    public Optional<Action> actionAt(Vector2f screen) {
        float x = screen.x / scale, y = screen.y / scale;
        for (Button button : buttons) {
            if (button.enabled && x >= button.x && x <= button.x + button.width
                    && y >= button.y && y <= button.y + button.height) {
                return Optional.of(button.action);
            }
        }
        return Optional.empty();
    }

    private void rebuild() {
        layout.detachAllChildren();
        buttons.clear();
        if (state == null) return;
        boolean placing = state.phase() == GamePhase.PLACEMENT;
        panelHeight = placing ? 397 : 160;
        panel(36, top - panelHeight, 255, panelHeight,
                new ColorRGBA(0.003f, 0.007f, 0.021f, 0.89f));
        frame(36, top - panelHeight, 255, panelHeight, GOLD);
        text(display, placing ? "Assemble fleet"
                : state.phase() == GamePhase.GAME_OVER ? "Battle complete" : networkPlacement ? "PVP battle" : "Local battle", 25, 54, top - 18, IVORY);
        if (!placing) {
            text(body, enemyView ? "ENEMY WATERS" : "YOUR FLEET", 10, 55, top - 46, GOLD);
            button(54, top - 97, 219, 31, enemyView ? "Your fleet [Tab]" : "Target enemy [Tab]",
                    Action.TOGGLE_BOARD, true, false);
            text(body, state.phase() == GamePhase.GAME_OVER ? "Use Tab to inspect your fleet."
                    : "Click a target cell to fire.", 11, 55, top - 101, CYAN);
            if (networkPlacement) button(54, top - 151, 219, 28, "Leave", Action.LEAVE_ROOM, true, false);
            else if (state.phase() != GamePhase.GAME_OVER)
                text(body, "Local: fire both fleets in turn.", 10, 55, top - 137, 0xB6BDCF);
            return;
        }
        int placed = state.player().board().ships().size();
        text(body, placed + " / 5 SHIPS PLACED", 10, 55, top - 40, GOLD);
        ShipType[] types = ShipType.values();
        Action[] selectionActions = {Action.SELECT_CARRIER, Action.SELECT_BATTLESHIP,
                Action.SELECT_CRUISER, Action.SELECT_SUBMARINE, Action.SELECT_DESTROYER};
        for (int i = 0; i < types.length; i++) {
            ShipType type = types[i];
            float y = top - 90 - i * 33;
            button(54, y, 219, 28, "[" + (i + 1) + "] " + type.displayName(),
                    selectionActions[i], !networkLocked, type == selected);
            UiTypography.centerIn(text(body, state.player().board().ship(type) == null ? type.length() + " cells" : "Placed",
                    10, 209, y, state.player().board().ship(type) == null ? 0xB6BDCF : CYAN), 209, y, 64, 28);
        }
        text(body, orientation == ShipOrientation.HORIZONTAL ? "HORIZONTAL  /  T TO ROTATE" : "VERTICAL  /  T TO ROTATE",
                10, 55, top - 231, CYAN);
        button(54, top - 281, 106, 27, "Rotate [T]", Action.ROTATE, selected != null && !networkLocked, false);
        button(167, top - 281, 106, 27, "Remove", Action.REMOVE,
                selected != null && state.player().board().ship(selected) != null && !networkLocked, false);
        button(54, top - 313, 106, 27, "Reset [C]", Action.RESET, !networkLocked, false);
        button(167, top - 313, 106, 27, "Random [F]", Action.RANDOM, !networkLocked, false);
        if (networkPlacement) {
            button(54, top - 350, 106, 30, "Ready", Action.READY, state.canReady() && !networkLocked, false);
            button(167, top - 350, 106, 30, "Leave", Action.LEAVE_ROOM, true, false);
        } else {
            button(54, top - 350, 219, 30, "Ready", Action.READY, state.canReady(), false);
        }
        String note = message.isBlank() ? "Hover to preview. Click to place." : message;
        text(body, note, 10, 55, top - 369, 0xB6BDCF);
    }

    private void button(float x, float y, float width, float height, String label,
                        Action action, boolean enabled, boolean selected) {
        ColorRGBA fill = selected ? new ColorRGBA(0.09f, 0.105f, 0.115f, 0.98f)
                : new ColorRGBA(0.009f, 0.022f, 0.044f, enabled ? 0.9f : 0.55f);
        panel(x, y, width, height, fill).setMesh(UiShapes.chamfered(width, height, 7));
        frame(x, y, width, height, enabled ? (selected ? GOLD : 0x617D94) : 0x394659);
        boolean fleetChoice = action.name().startsWith("SELECT_");
        UiTypography.centerIn(text(body, label, 12, x, y, enabled ? IVORY : 0x6E7989),
                x, y, fleetChoice ? width - 64 : width, height);
        buttons.add(new Button(x, y, width, height, action, enabled));
    }

    private void frame(float x, float y, float width, float height, int color) {
        line(x + 7, y, x + width - 7, y, color);
        line(x + 7, y + height, x + width - 7, y + height, color);
        line(x, y + 7, x, y + height - 7, color);
        line(x + width, y + 7, x + width, y + height - 7, color);
        for (int sx : new int[] {-1, 1}) {
            for (int sy : new int[] {-1, 1}) {
                float px = sx < 0 ? x : x + width, py = sy < 0 ? y : y + height;
                line(px, py - sy * 7, px - sx * 7, py, color);
            }
        }
    }

    private void line(float x, float y, float endX, float endY, int color) {
        float dx = endX - x, dy = endY - y;
        Geometry geometry = panel(x, y, FastMath.sqrt(dx * dx + dy * dy), 0.7f, Materials.rgb(color));
        geometry.rotate(0, 0, FastMath.atan2(dy, dx));
        geometry.setLocalTranslation(x, y, 11);
    }

    private BitmapText text(BitmapFont font, String value, float size, float x, float y, int color) {
        BitmapText label = new BitmapText(font);
        label.setText(UiTypography.normalize(value));
        label.setSize(font == body ? UiTypography.bodySize(size) : size);
        label.setColor(Materials.rgb(color));
        label.setLocalTranslation(x, y, 12);
        if (size <= 11) UiTypography.fit(label, 219);
        layout.attachChild(label);
        return label;
    }

    private Geometry panel(float x, float y, float width, float height, ColorRGBA color) {
        Geometry geometry = new Geometry("Fleet control", new Quad(width, height));
        Material material = materials.flat(color);
        material.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        geometry.setMaterial(material);
        geometry.setQueueBucket(Bucket.Gui);
        geometry.setLocalTranslation(x, y, 10);
        layout.attachChild(geometry);
        return geometry;
    }

    private record Button(float x, float y, float width, float height, Action action, boolean enabled) {}
}
