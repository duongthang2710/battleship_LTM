package com.battleship.frontend.chat;

import com.battleship.frontend.graphics.Materials;
import com.battleship.frontend.ui.UiTypography;
import com.battleship.frontend.ui.UiTextField;
import com.battleship.shared.chat.*;
import com.jme3.asset.AssetManager;
import com.jme3.font.*;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.*;
import com.jme3.scene.*;
import com.jme3.scene.shape.Quad;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class ChatPanel {
    public enum Mode { HIDDEN, WAITING, BATTLE }
    public enum Action { SEND, TOGGLE, LATEST }
    private record Line(String text, int color) {}
    private record Button(Action action, float x, float y, float width, float height) {}
    private static final int GOLD = 0xDEB663, IVORY = 0xF4EAD1, CYAN = 0x91CEFF, MUTED = 0xA3AEC2;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private final Node root = new Node("Room and battle chat");
    private final Node card = new Node("Admirals dispatches");
    private final Materials materials;
    private final BitmapFont display, body;
    private final BitmapText measure;
    private final UiTextField entry;
    private final List<ChatMessage> messages = new ArrayList<>();
    private final List<Line> lines = new ArrayList<>();
    private final List<Button> buttons = new ArrayList<>();
    private ChatContext context;
    private Mode mode = Mode.HIDDEN;
    private String draft = "", status = "";
    private boolean focused, collapsed, busy, scrollDragging;
    private float thumbGrab;
    private int firstLine, unread;
    private int viewportWidth = 1440, viewportHeight = 900;
    private float scale = 1, left, bottom, width = 550, height = 390;

    public ChatPanel(AssetManager assets) {
        materials = new Materials(assets); display = UiTypography.display(assets); body = UiTypography.body(assets);
        measure = new BitmapText(body); measure.setSize(UiTypography.bodySize(12));
        entry = new UiTextField(body, materials, 442, 34, UiTypography.bodySize(12), "Message...", false);
        root.attachChild(card); setMode(Mode.HIDDEN); rebuild();
    }
    public Node root() { return root; }
    public Mode mode() { return mode; }
    public boolean visible() { return mode != Mode.HIDDEN && context != null; }
    public boolean focused() { return focused; }
    public boolean collapsed() { return collapsed; }
    public boolean busy() { return busy; }
    public boolean writable() { return context != null && context.canSend(); }
    public String draft() { return draft; }
    public String status() { return status; }
    public int firstVisibleLine() { return firstLine; }
    public String firstVisibleText() { return lines.isEmpty() ? "" : lines.get(Math.min(firstLine, lines.size() - 1)).text; }
    public boolean atBottom() { return firstLine >= maxFirst(); }
    public List<ChatMessage> messages() { return List.copyOf(messages); }

    public void setContext(ChatContext next) {
        if (context == null || !context.contextId().equals(next.contextId())) reset();
        context = next;
        if (!next.canSend()) { focused = false; busy = false; }
        rebuild();
    }
    public void clear() { context = null; reset(); rebuild(); }
    private void reset() {
        scrollDragging = false;
        messages.clear(); lines.clear(); draft = status = ""; focused = busy = false; firstLine = unread = 0;
    }
    public void setMode(Mode value) {
        if (mode != value) {
            boolean follow = atBottom();
            scrollDragging = false;
            mode = value; focused = false; collapsed = value == Mode.BATTLE; layout();
            reflowLines(); firstLine = follow ? maxFirst() : Math.min(firstLine, maxFirst()); rebuild();
        }
        root.setCullHint(visible() ? Spatial.CullHint.Never : Spatial.CullHint.Always);
    }
    public void resize(int w, int h) { scrollDragging = false; viewportWidth = w; viewportHeight = h; layout(); rebuild(); }
    private void layout() {
        scale = UiTypography.scale(viewportWidth, viewportHeight);
        width = mode == Mode.BATTLE ? 255 : 550; height = mode == Mode.BATTLE ? 280 : 390;
        left = mode == Mode.BATTLE ? 36 * scale : viewportWidth - (width + 56) * scale;
        bottom = (mode == Mode.BATTLE ? viewportHeight / scale - 148 - 160 - 24 - height : 170) * scale;
        card.setLocalScale(scale); card.setLocalTranslation(left, bottom, 0);
    }
    public void append(ChatMessage message) {
        boolean follow = atBottom(); int oldStart = firstLine;
        messages.add(message);
        int removedLines = 0;
        if (messages.size() > 100) removedLines = messageLines(messages.removeFirst()).size();
        reflowLines();
        firstLine = follow ? maxFirst() : Math.max(0, Math.min(oldStart - removedLines, maxFirst()));
        if (collapsed || !follow) unread++;
        rebuild();
    }
    public void setBusy(boolean value) { busy = value; status = value ? "Sending..." : ""; rebuild(); }
    public void sent() { busy = false; draft = status = ""; rebuild(); }
    public void error(String message) { busy = false; status = message; rebuild(); }
    public void focus() {
        if (!visible() || !writable()) return;
        if (collapsed) { collapsed = false; firstLine = maxFirst(); unread = 0; }
        focused = true; rebuild();
    }
    public void blur() { if (focused) { focused = false; rebuild(); } }
    public void character(char c) {
        if (focused && !busy && !ChatText.forbidden(c) && draft.codePointCount(0, draft.length()) < ChatText.MAX_LENGTH) { draft += c; status = ""; rebuild(); }
    }
    public void backspace() {
        if (focused && !busy && !draft.isEmpty()) { draft = draft.substring(0, draft.offsetByCodePoints(draft.length(), -1)); rebuild(); }
    }
    public void toggle() {
        if (mode != Mode.BATTLE) return;
        collapsed = !collapsed; focused = false; scrollDragging = false;
        if (!collapsed && atBottom()) unread = 0;
        rebuild();
    }
    public void updateCaret(float delta) { entry.setContent(safe(draft), visible() && !collapsed && focused && !busy && writable()); entry.update(delta); }
    public boolean caretVisible() { return entry.caretVisible(); }
    public void scroll(int amount) { firstLine = Math.max(0, Math.min(maxFirst(), firstLine + amount)); if (atBottom()) unread = 0; rebuild(); }
    private float trackBottom() { return 132; }
    private float trackHeight() { return height - 174; }
    private float thumbHeight() { return Math.min(trackHeight(), Math.max(26, trackHeight() * capacity() / Math.max(capacity(), lines.size()))); }
    private float thumbBottom() {
        return trackBottom() + (trackHeight() - thumbHeight()) * (maxFirst() == 0 ? 0 : 1 - (float) firstLine / maxFirst());
    }
    public Vector2f scrollThumbCenter() {
        return new Vector2f(left + (width - 17) * scale, bottom + (thumbBottom() + thumbHeight() / 2) * scale);
    }
    public boolean beginScrollDrag(Vector2f point) {
        if (!contains(point) || collapsed || maxFirst() == 0) return false;
        float x = (point.x - left) / scale, y = (point.y - bottom) / scale;
        if (x < width - 27 || x > width - 7 || y < trackBottom() || y > trackBottom() + trackHeight()) return false;
        thumbGrab = y >= thumbBottom() && y <= thumbBottom() + thumbHeight() ? y - thumbBottom() : thumbHeight() / 2;
        scrollDragging = true;
        dragScroll(point);
        return true;
    }
    public void dragScroll(Vector2f point) {
        if (!scrollDragging || !visible() || collapsed) return;
        float travel = trackHeight() - thumbHeight();
        if (travel <= 0) return;
        float y = (point.y - bottom) / scale - thumbGrab;
        int next = Math.round((1 - FastMath.clamp((y - trackBottom()) / travel, 0, 1)) * maxFirst());
        if (next != firstLine) { firstLine = next; if (atBottom()) unread = 0; rebuild(); }
    }
    public void endScrollDrag() { if (scrollDragging) { scrollDragging = false; rebuild(); } }
    public void latest() { firstLine = maxFirst(); unread = 0; rebuild(); }
    public boolean contains(Vector2f point) {
        return visible() && point.x >= left && point.x <= left + width * scale && point.y >= bottom
                && point.y <= bottom + (collapsed ? 34 : height) * scale;
    }
    public Optional<Action> click(Vector2f point) {
        if (!contains(point)) return Optional.empty();
        float x = (point.x - left) / scale, y = (point.y - bottom) / scale;
        if (!collapsed && x >= 12 && x <= width - 84 && y >= 14 && y <= 48) { focus(); return Optional.empty(); }
        return buttons.stream().filter(b -> x >= b.x && x <= b.x + b.width && y >= b.y && y <= b.y + b.height).map(Button::action).findFirst();
    }
    public Vector2f inputCenter() { return new Vector2f(left + 80 * scale, bottom + 30 * scale); }
    public Vector2f listCenter() { return new Vector2f(left + width / 2 * scale, bottom + height / 2 * scale); }
    public Vector2f buttonCenter(Action action) {
        var b = buttons.stream().filter(v -> v.action == action).findFirst().orElseThrow();
        return new Vector2f(left + (b.x + b.width / 2) * scale, bottom + (b.y + b.height / 2) * scale);
    }
    private int capacity() { return (int) (height - 170) / 24; }
    private int maxFirst() { return Math.max(0, lines.size() - capacity()); }
    private void reflowLines() { lines.clear(); for (ChatMessage message : messages) lines.addAll(messageLines(message)); }
    private List<Line> messageLines(ChatMessage message) {
        List<Line> result = new ArrayList<>();
        for (String header : wrap(TIME.format(Instant.parse(message.sentAt())) + "  " + safe(message.sender().displayName()), width - 46)) result.add(new Line(header, GOLD));
        for (String line : wrap(safe(message.text()), width - 46)) result.add(new Line(line, IVORY));
        return result;
    }
    private String safe(String text) {
        // jME interprets backslash/hash color tags. Break that delimiter before passing untrusted text to BitmapText.
        String escaped = UiTypography.normalize(text).replace("\\#", "\\ #");
        StringBuilder supported = new StringBuilder();
        escaped.codePoints().forEach(c -> supported.appendCodePoint(body.getCharSet().getCharacter(c) == null ? '?' : c));
        return supported.toString();
    }
    private List<String> wrap(String value, float maxWidth) {
        List<String> result = new ArrayList<>(); StringBuilder line = new StringBuilder();
        for (int cp : value.codePoints().toArray()) {
            String candidate = line.toString() + new String(Character.toChars(cp)); measure.setText(candidate);
            if (measure.getLineWidth() > maxWidth && !line.isEmpty()) {
                int space = line.lastIndexOf(" ");
                if (space > 0) {
                    result.add(line.substring(0, space));
                    String tail = line.substring(space + 1); line.setLength(0); line.append(tail);
                } else { result.add(line.toString()); line.setLength(0); }
            }
            line.appendCodePoint(cp);
        }
        if (!line.isEmpty()) result.add(line.toString());
        return result;
    }
    private void rebuild() {
        root.setCullHint(visible() ? Spatial.CullHint.Never : Spatial.CullHint.Always);
        card.detachAllChildren(); buttons.clear();
        float h = collapsed ? 34 : height;
        rect(0, 0, width, h, new ColorRGBA(.003f, .009f, .025f, .96f)); frame(0, 0, width, h, GOLD);
        text(display, "CHAT" + (unread > 0 ? "  (" + unread + ")" : ""), 21, 12, h - 9, IVORY);
        if (mode == Mode.BATTLE) button(Action.TOGGLE, width - 42, h - 27, 30, 22, collapsed ? "+" : "-");
        if (collapsed) return;
        rect(width - 22, trackBottom(), 10, trackHeight(), new ColorRGBA(.025f, .055f, .095f, 1));
        rect(width - 22, thumbBottom(), 10, thumbHeight(), Materials.rgb(maxFirst() == 0 ? 0x334050 : scrollDragging ? GOLD : 0x739BB6));
        if (maxFirst() > 0) rect(width - 19, thumbBottom() + thumbHeight() / 2 - 1, 4, 2, Materials.rgb(IVORY));
        for (int i = firstLine; i < Math.min(lines.size(), firstLine + capacity()); i++) {
            Line line = lines.get(i); text(body, line.text, 12, 12, h - 54 - (i - firstLine) * 24, line.color);
        }
        if (lines.isEmpty()) text(body, "No messages yet.", 11, 12, h - 53, MUTED);
        String info = !status.isBlank() ? status : !writable() ? "Read-only. Match chat is closed." : "Enter: send / Esc: unfocus";
        var infoLines = wrap(info, width - 24);
        for (int i = 0; i < Math.min(2, infoLines.size()); i++) text(body, infoLines.get(i), 10, 12, 108 - i * 20, CYAN);
        if (unread > 0 && !atBottom()) button(Action.LATEST, width - 82, 51, 70, 24, "NEW " + unread);
        rect(12, 14, width - 96, 34, new ColorRGBA(.025f, .055f, .095f, 1)); frame(12, 14, width - 96, 34, focused ? CYAN : 0x516275);
        entry.layout(width - 108, 34); entry.setContent(safe(draft), focused && !busy && writable());
        entry.setLocalTranslation(18, 14, 115); card.attachChild(entry);
        text(body, draft.codePointCount(0, draft.length()) + "/300", 9, 12, 67, MUTED);
        button(Action.SEND, width - 76, 14, 64, 34, "SEND");
    }
    private void button(Action action, float x, float y, float w, float h, String value) {
        buttons.add(new Button(action, x, y, w, h)); rect(x, y, w, h, new ColorRGBA(.025f, .055f, .095f, 1)); frame(x, y, w, h, 0x516275);
        UiTypography.centerIn(text(body, value, h < 25 ? 8 : 10, x, y, action == Action.SEND && (!writable() || busy) ? MUTED : IVORY), x, y, w, h);
    }
    private void frame(float x, float y, float w, float h, int color) {
        ColorRGBA c = Materials.rgb(color); rect(x, y, w, .7f, c); rect(x, y + h, w, .7f, c); rect(x, y, .7f, h, c); rect(x + w, y, .7f, h, c);
    }
    private void rect(float x, float y, float w, float h, ColorRGBA color) {
        var material = materials.flat(color); material.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        var geometry = new Geometry("Chat detail", new Quad(w, h)); geometry.setMaterial(material); geometry.setLocalTranslation(x, y, 110 + card.getQuantity() * .001f); card.attachChild(geometry);
    }
    private BitmapText text(BitmapFont font, String value, float size, float x, float y, int color) {
        var label = new BitmapText(font); label.setText(UiTypography.normalize(value)); label.setSize(font == body ? UiTypography.bodySize(size) : size); label.setColor(Materials.rgb(color)); label.setLocalTranslation(x, y, 115); card.attachChild(label); return label;
    }
}
