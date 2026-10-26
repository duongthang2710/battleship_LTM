package com.battleship.frontend.friends;

import com.battleship.frontend.graphics.Materials;
import com.battleship.shared.friends.*;
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
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class FriendsPanel {
    public enum Action { FRIENDS, RECEIVED, SENT, SEARCH, REFRESH, PREVIOUS, NEXT, BACK,
                         SEND, ACCEPT, DECLINE, CANCEL, REMOVE, CONFIRM_REMOVE, CANCEL_REMOVE }
    public record Command(Action action, UUID target) {}
    private static final float WIDTH = 1100, HEIGHT = 690;
    private static final int GOLD = 0xDEB663, IVORY = 0xF4EAD1, CYAN = 0x91CEFF, MUTED = 0xA3AEC2;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm").withZone(ZoneId.systemDefault());
    private final Node root = new Node("Friends");
    private final Node card = new Node("Admirals alliance register");
    private final Materials materials;
    private final BitmapFont display, body;
    private final List<Button> buttons = new ArrayList<>();
    private FriendsAction tab = FriendsAction.FRIENDS;
    private FriendsResult data;
    private String query = "", status = "";
    private boolean visible, loading, focused;
    private int page;
    private UUID confirmation;
    private String confirmationName;
    private float scale = 1, left, bottom;
    private Command hovered;

    public FriendsPanel(AssetManager assets) {
        materials = new Materials(assets);
        display = assets.loadFont("fonts/CormorantGaramond.fnt"); body = assets.loadFont("fonts/Manrope.fnt");
        root.attachChild(card); resize(1440, 900); rebuild(); hide();
    }
    public Node root() { return root; }
    public boolean visible() { return visible; }
    public boolean loading() { return loading; }
    public boolean focused() { return focused; }
    public boolean confirming() { return confirmation != null; }
    public FriendsResult data() { return data; }
    public String status() { return status; }
    public String query() { return query; }
    public FriendsAction tab() { return tab; }
    public void show() { visible = true; focused = false; confirmation = null; root.setCullHint(CullHint.Never); }
    public void hide() { visible = false; focused = false; confirmation = null; root.setCullHint(CullHint.Always); }
    public void setLoading(FriendsAction tab, int page) {
        this.tab = tab; this.page = page; loading = true; confirmation = null; data = null;
        status = "Loading admirals..."; rebuild();
    }
    public void setData(FriendsResult result) {
        data = result; loading = false; status = result.entries().isEmpty() ? emptyText() : ""; rebuild();
    }
    private String emptyText() {
        return switch (tab) {
            case SEARCH -> "No matching usernames. Try another search.";
            case FRIENDS -> "No friends yet. Search for an admiral to send an invitation.";
            case RECEIVED -> "No pending invitations.";
            default -> "No sent invitations yet.";
        };
    }
    public void setError(String message) { loading = false; status = message; rebuild(); }
    public void notice(String message) { status = message; rebuild(); }
    public void confirm(UUID id) {
        if (data == null) return;
        data.entries().stream().filter(e -> e.userId().equals(id)).findFirst().ifPresent(e -> {
            confirmation = id; confirmationName = e.username(); focused = false; rebuild();
        });
    }
    public void cancelConfirmation() { confirmation = null; rebuild(); }
    public void inputCharacter(char c) {
        if (focused && !loading && query.length() < 24 && (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_')) {
            query += c; rebuild();
        }
    }
    public void backspace() { if (focused && !loading && !query.isEmpty()) { query = query.substring(0, query.length() - 1); rebuild(); } }
    public void resize(int width, int height) {
        scale = Math.min(width / 1440f, height / 900f);
        left = (width - WIDTH * scale) / 2; bottom = (height - HEIGHT * scale) / 2;
        card.setLocalScale(scale); card.setLocalTranslation(left, bottom, 0);
    }
    public boolean enabled(Action action) {
        if (confirmation != null) return action == Action.CONFIRM_REMOVE || action == Action.CANCEL_REMOVE || action == Action.BACK;
        return switch (action) {
            case BACK -> true;
            case SEARCH -> !loading && !query.isBlank();
            case PREVIOUS -> !loading && page > 0;
            case NEXT -> !loading && data != null && (long) (page + 1) * data.pageSize() < data.total();
            default -> !loading;
        };
    }
    public Optional<Command> actionAt(Vector2f point) {
        if (!visible) return Optional.empty();
        float x = (point.x - left) / scale, y = (point.y - bottom) / scale;
        return buttons.stream().filter(b -> enabled(b.command.action()) && x >= b.x && x <= b.x + b.width && y >= b.y && y <= b.y + 36)
                .map(Button::command).findFirst();
    }
    public Optional<Command> click(Vector2f point) {
        float x = (point.x - left) / scale, y = (point.y - bottom) / scale;
        focused = visible && confirmation == null && !loading && x >= 32 && x <= 822 && y >= 507 && y <= 546;
        rebuild(); return actionAt(point);
    }
    public Vector2f searchCenter() { return new Vector2f(left + 300 * scale, bottom + 526 * scale); }
    public Vector2f buttonCenter(Action action) { return buttonCenter(action, null); }
    public Vector2f buttonCenter(Action action, UUID target) {
        Button b = buttons.stream().filter(v -> v.command.action() == action && (target == null || target.equals(v.command.target())))
                .findFirst().orElseThrow();
        return new Vector2f(left + (b.x + b.width / 2) * scale, bottom + (b.y + 18) * scale);
    }
    public void update(Vector2f cursor) {
        Command next = actionAt(cursor).orElse(null);
        if (!Objects.equals(next, hovered)) { hovered = next; refreshButtons(); }
    }
    private void rebuild() {
        card.detachAllChildren(); buttons.clear();
        rect(0, 0, WIDTH, HEIGHT, new ColorRGBA(.003f, .009f, .025f, .97f), 90);
        frame(0, 0, WIDTH, HEIGHT, GOLD); frame(8, 8, WIDTH - 16, HEIGHT - 16, 0x655638);
        text(body, "THE ADMIRALS' ALLIANCE REGISTER", 10, 32, 655, GOLD);
        text(display, "FRIENDS", 43, 30, 630, IVORY);
        text(body, "Find allies across the ocean", 12, 32, 579, MUTED);
        button(560, 601, 156, "FRIENDS", Action.FRIENDS, null);
        button(726, 601, 166, "RECEIVED", Action.RECEIVED, null);
        button(902, 601, 166, "SENT", Action.SENT, null);
        line(30, 556, 1040, GOLD);
        rect(32, 507, 790, 39, new ColorRGBA(.025f, .055f, .095f, 1), 91);
        frame(32, 507, 790, 39, focused ? CYAN : 0x516275);
        text(body, query.isEmpty() ? "Search by username..." : query + (focused ? "|" : ""), 13, 47, 532, query.isEmpty() ? MUTED : IVORY);
        button(838, 508, 230, "SEARCH", Action.SEARCH, null);
        text(body, tab == FriendsAction.SEARCH ? "SEARCH RESULTS" : tab.name(), 11, 40, 481, GOLD);
        text(body, "STATUS / INVITATION SENT", 10, 460, 481, GOLD);
        text(body, "ACTIONS", 10, 850, 481, GOLD);
        if (data != null) for (int i = 0; i < data.entries().size(); i++) row(data.entries().get(i), i);
        text(body, status, 12, 32, 115, CYAN);
        text(body, "Sent shows the latest invitation for each player. All dates use your local time.", 10, 32, 84, MUTED);
        button(32, 25, 180, "MAIN MENU", Action.BACK, null);
        button(228, 25, 120, "REFRESH", Action.REFRESH, null);
        button(730, 25, 100, "PREVIOUS", Action.PREVIOUS, null);
        button(968, 25, 100, "NEXT", Action.NEXT, null);
        long pages = data == null ? page + 1L : Math.max(1, (data.total() + data.pageSize() - 1) / data.pageSize());
        text(body, (page + 1) + " / " + pages, 12, 863, 47, IVORY);
        if (data != null) text(body, data.total() + " admirals", 11, 460, 47, MUTED);
        if (confirmation != null) {
            // Replace the list with an explicit confirmation, never remove on the first click.
            card.detachAllChildren(); buttons.clear();
            rect(275, 260, 550, 170, new ColorRGBA(.003f, .009f, .025f, 1), 90); frame(275, 260, 550, 170, GOLD);
            text(display, "Remove friend?", 30, 305, 400, IVORY);
            text(body, "Remove @" + confirmationName + " from your friends?", 13, 305, 356, CYAN);
            button(305, 280, 230, "REMOVE FRIEND", Action.CONFIRM_REMOVE, confirmation);
            button(555, 280, 230, "KEEP FRIEND", Action.CANCEL_REMOVE, null);
        }
        refreshButtons();
    }
    private void row(FriendEntry entry, int index) {
        float y = 414 - index * 38;
        rect(30, y, 1040, 36, new ColorRGBA(.03f, .075f, .13f, index % 2 == 0 ? .85f : .4f), 91);
        text(body, entry.displayName(), 13, 45, y + 30, IVORY);
        text(body, "@" + entry.username(), 9, 45, y + 12, MUTED);
        String state = entry.relationship();
        String caption = state.equals("FRIEND") ? (entry.online() ? "ONLINE" : "OFFLINE") : switch (state) {
            case "OUTGOING" -> "PENDING - SENT";
            case "INCOMING" -> "PENDING - RECEIVED";
            case "NONE" -> "NOT CONNECTED";
            default -> state;
        };
        text(body, caption, 11, 460, y + 29, state.equals("FRIEND") && entry.online() ? CYAN : GOLD);
        if (entry.requestedAt() != null) text(body, DATE.format(Instant.parse(entry.requestedAt())), 9, 460, y + 12, MUTED);
        switch (state) {
            case "FRIEND" -> button(913, y, 140, "REMOVE", Action.REMOVE, entry.userId());
            case "INCOMING" -> {
                button(768, y, 140, "ACCEPT", Action.ACCEPT, entry.userId());
                button(913, y, 140, "DECLINE", Action.DECLINE, entry.userId());
            }
            case "OUTGOING" -> button(913, y, 140, "CANCEL", Action.CANCEL, entry.userId());
            default -> { if (tab == FriendsAction.SEARCH) button(913, y, 140, "ADD FRIEND", Action.SEND, entry.userId()); }
        }
    }
    private void button(float x, float y, float width, String caption, Action action, UUID target) {
        Material fill = materials.flat(new ColorRGBA(.025f, .055f, .095f, 1));
        rect(x, y, width, 36, fill, 91); frame(x, y, width, 36, 0x516275);
        BitmapText label = text(body, caption, 11, 0, y + 23, IVORY);
        label.setLocalTranslation(x + (width - label.getLineWidth()) / 2, y + 23, 94);
        buttons.add(new Button(x, y, width, new Command(action, target), fill, label));
    }
    private void refreshButtons() {
        for (Button b : buttons) {
            boolean selected = b.command.action().name().equals(tab.name());
            boolean highlight = selected || b.command.equals(hovered);
            b.fill.setColor("Color", new ColorRGBA(.025f, .055f, .095f, !enabled(b.command.action()) ? .45f : highlight ? 1 : .7f));
            b.label.setColor(Materials.rgb(!enabled(b.command.action()) ? 0x69788D : highlight ? GOLD : IVORY));
        }
    }
    private void frame(float x, float y, float w, float h, int color) {
        Material m = materials.flat(Materials.rgb(color));
        rect(x, y, w, .8f, m, 92); rect(x, y + h, w, .8f, m, 92);
        rect(x, y, .8f, h, m, 92); rect(x + w, y, .8f, h, m, 92);
    }
    private void line(float x, float y, float w, int color) { rect(x, y, w, .8f, Materials.rgb(color), 92); }
    private Geometry rect(float x, float y, float w, float h, ColorRGBA color, float z) { return rect(x, y, w, h, materials.flat(color), z); }
    private Geometry rect(float x, float y, float w, float h, Material m, float z) {
        Geometry geometry = new Geometry("Friends detail", new Quad(w, h));
        m.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        geometry.setMaterial(m); geometry.setLocalTranslation(x, y, z); card.attachChild(geometry); return geometry;
    }
    private BitmapText text(BitmapFont font, String value, float size, float x, float y, int color) {
        BitmapText label = new BitmapText(font); label.setText(value); label.setSize(size); label.setColor(Materials.rgb(color));
        label.setLocalTranslation(x, y, 94); card.attachChild(label); return label;
    }
    private record Button(float x, float y, float width, Command command, Material fill, BitmapText label) {}
}
