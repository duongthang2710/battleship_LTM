package com.battleship.frontend.leaderboard;

import com.battleship.frontend.graphics.Materials;
import com.battleship.frontend.ui.UiTypography;
import com.battleship.shared.leaderboard.*;
import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector2f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial.CullHint;
import com.jme3.scene.shape.Quad;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class LeaderboardPanel {
    public enum Action { WINS, WIN_RATE, MATCHES, PREVIOUS, NEXT, REFRESH, BACK }
    private static final float WIDTH = 1100, HEIGHT = 690;
    private static final int GOLD = 0xDEB663, IVORY = 0xF4EAD1, CYAN = 0x91CEFF, MUTED = 0xA3AEC2;
    private final Node root = new Node("Leaderboard");
    private final Node card = new Node("Admirals roll of honor");
    private final Materials materials;
    private final BitmapFont display, body;
    private final List<Button> buttons = new ArrayList<>();
    private LeaderboardPage data;
    private LeaderboardSort sort = LeaderboardSort.WINS;
    private int page;
    private String status = "";
    private boolean visible, loading;
    private Action hovered;
    private float scale = 1, left, bottom;

    public LeaderboardPanel(AssetManager assets) {
        materials = new Materials(assets);
        display = UiTypography.display(assets);
        body = UiTypography.body(assets);
        root.attachChild(card); resize(1440, 900); rebuild(); hide();
    }
    public Node root() { return root; }
    public boolean visible() { return visible; }
    public boolean loading() { return loading; }
    public LeaderboardPage data() { return data; }
    public String status() { return status; }
    public void show() { visible = true; root.setCullHint(CullHint.Never); }
    public void hide() { visible = false; root.setCullHint(CullHint.Always); }
    public void setLoading(LeaderboardSort sort, int page) {
        this.sort = sort; this.page = page; data = null; loading = true; status = "Loading server rankings..."; rebuild();
    }
    public void setPage(LeaderboardPage result) {
        data = result; loading = false;
        status = result.entries().isEmpty() ? (result.totalPlayers() == 0 ? "No eligible completed PvP matches yet." : "No players on this page.") : "";
        rebuild();
    }
    public void setError(String message) { data = null; loading = false; status = message; rebuild(); }
    public void resize(int width, int height) {
        scale = UiTypography.scale(width, height);
        left = (width - WIDTH * scale) / 2; bottom = (height - HEIGHT * scale) / 2;
        card.setLocalScale(scale); card.setLocalTranslation(left, bottom, 0);
    }
    public boolean enabled(Action action) {
        return switch (action) {
            case BACK -> true;
            case PREVIOUS -> !loading && page > 0;
            case NEXT -> !loading && data != null && (long) (page + 1) * data.pageSize() < data.totalPlayers();
            default -> !loading;
        };
    }
    public Optional<Action> actionAt(Vector2f point) {
        if (!visible) return Optional.empty();
        float x = (point.x - left) / scale, y = (point.y - bottom) / scale;
        return buttons.stream().filter(b -> enabled(b.action) && x >= b.x && x <= b.x + b.width && y >= b.y && y <= b.y + 36)
                .map(Button::action).findFirst();
    }
    public Vector2f buttonCenter(Action action) {
        Button b = buttons.stream().filter(button -> button.action == action).findFirst().orElseThrow();
        return new Vector2f(left + (b.x + b.width / 2) * scale, bottom + (b.y + 18) * scale);
    }
    public void update(Vector2f cursor) {
        if (!visible) return;
        Action next = actionAt(cursor).orElse(null);
        if (next != hovered) { hovered = next; refreshButtons(); }
    }
    private void rebuild() {
        card.detachAllChildren(); buttons.clear();
        rect(0, 0, WIDTH, HEIGHT, new ColorRGBA(.003f, .009f, .025f, .97f), 90);
        frame(0, 0, WIDTH, HEIGHT, GOLD); frame(8, 8, WIDTH - 16, HEIGHT - 16, 0x655638);
        text(body, "THE ADMIRALS' ROLL OF HONOR", 10, 32, 655, GOLD);
        text(display, "LEADERBOARD", 43, 30, 630, IVORY);
        text(body, "Completed PvP battles across the server", 12, 32, 579, MUTED);
        button(650, 601, 130, "WINS", Action.WINS);
        button(790, 601, 130, "WIN RATE", Action.WIN_RATE);
        button(930, 601, 138, "MATCHES", Action.MATCHES);
        line(30, 556, 1040, GOLD);
        String[] titles = {"RANK", "ADMIRAL / USERNAME", "MATCHES", "WINS", "LOSSES", "WIN RATE", "STREAK"};
        float[] columns = {43, 132, 494, 616, 714, 825, 980};
        for (int i = 0; i < titles.length; i++) text(body, titles[i], 10, columns[i], 536, GOLD);
        if (data != null) for (int i = 0; i < data.entries().size(); i++) row(data.entries().get(i), i);
        if (!status.isEmpty()) {
            BitmapText label = text(display, status, 23, 0, 340, loading ? IVORY : CYAN);
            label.setLocalTranslation((WIDTH - label.getLineWidth()) / 2, 340, 94);
        }
        int minimum = data == null ? 10 : data.minWinRateMatches();
        text(body, sort == LeaderboardSort.WIN_RATE ? "WIN RATE RANKING: minimum " + minimum + " completed PvP matches."
                : "Only fully recorded, completed PvP matches count.", 11, 32, 105, CYAN);
        text(body, "Streak: consecutive wins. A dash means history cannot establish their order.", 10, 32, 84, MUTED);
        button(32, 25, 180, "MAIN MENU", Action.BACK);
        button(228, 25, 120, "REFRESH", Action.REFRESH);
        button(730, 25, 100, "PREVIOUS", Action.PREVIOUS);
        button(968, 25, 100, "NEXT", Action.NEXT);
        long pages = data == null ? page + 1L : Math.max(1, (data.totalPlayers() + data.pageSize() - 1) / data.pageSize());
        text(body, (page + 1) + " / " + pages, 12, 863, 47, IVORY);
        if (data != null) text(body, data.totalPlayers() + " ranked admirals", 11, 450, 47, MUTED);
        refreshButtons();
    }
    private void row(LeaderboardEntry entry, int index) {
        float y = 474 - index * 40;
        int accent = entry.rank() == 1 ? GOLD : entry.rank() == 2 ? 0xC1CEDD : entry.rank() == 3 ? 0xBC956A : MUTED;
        rect(30, y, 1040, 38, new ColorRGBA(.03f, .075f, .13f, index % 2 == 0 ? .85f : .4f), 91);
        if (entry.rank() <= 3) {
            rect(30, y, 2, 38, Materials.rgb(accent), 92);
            Geometry crest = rect(73, y + 5, 17, 17, new ColorRGBA(.12f, .17f, .23f, 1), 92);
            crest.rotate(0, 0, FastMath.QUARTER_PI);
        }
        BitmapText rank = text(body, Long.toString(entry.rank()), 17, 0, y + 29, accent);
        rank.setLocalTranslation(73 - rank.getLineWidth() / 2, y + 29, 94);
        UiTypography.fit(text(body, entry.displayName(), 13, 132, y + 36, IVORY), 338);
        UiTypography.fit(text(body, "@" + entry.username(), 9, 132, y + 16, MUTED), 338);
        number(entry.matches(), 526, y); number(entry.wins(), 634, y); number(entry.losses(), 738, y);
        text(body, String.format(Locale.ROOT, "%.1f%%", entry.winRate()), 14, 825, y + 25, CYAN);
        text(body, entry.currentWinStreak() == null ? "-" : Long.toString(entry.currentWinStreak()), 14, 997, y + 25, accent);
    }
    private void number(long value, float x, float y) { text(body, Long.toString(value), 14, x, y + 25, IVORY); }
    private void button(float x, float y, float width, String caption, Action action) {
        Material fill = materials.flat(new ColorRGBA(.025f, .055f, .095f, 1));
        rect(x, y, width, 36, fill, 91); frame(x, y, width, 36, 0x516275);
        BitmapText label = text(body, caption, 11, 0, y + 23, IVORY);
        UiTypography.centerIn(label, x, y, width, 36);
        buttons.add(new Button(x, y, width, action, fill, label));
    }
    private void refreshButtons() {
        for (Button b : buttons) {
            boolean selected = b.action.name().equals(sort.name());
            b.fill.setColor("Color", new ColorRGBA(.025f, .055f, .095f, !enabled(b.action) ? .45f : b.action == hovered || selected ? 1 : .7f));
            b.label.setColor(Materials.rgb(!enabled(b.action) ? 0x69788D : selected || b.action == hovered ? GOLD : IVORY));
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
        Geometry geometry = new Geometry("Leaderboard detail", new Quad(w, h));
        m.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        geometry.setMaterial(m); geometry.setLocalTranslation(x, y, z); card.attachChild(geometry); return geometry;
    }
    private BitmapText text(BitmapFont font, String value, float size, float x, float y, int color) {
        BitmapText label = new BitmapText(font); label.setText(UiTypography.normalize(value)); label.setSize(font == body ? UiTypography.bodySize(size) : size); label.setColor(Materials.rgb(color));
        label.setLocalTranslation(x, y, 94); card.attachChild(label); return label;
    }
    private record Button(float x, float y, float width, Action action, Material fill, BitmapText label) {}
}
