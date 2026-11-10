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
        SHOW_CREATE, CREATE_ROOM, SHOW_JOIN, JOIN_ROOM, BACK, INVITE_FRIENDS, TOGGLE_READY, LEAVE_ROOM, COPY_CODE, TOGGLE_MODE, FEWER_SHIPS, MORE_SHIPS, TOGGLE_TURN_RULE, MODE_BASIC, MODE_DESIGNER, FLEET_THREE, FLEET_FOUR, FLEET_FIVE, RULE_ALTERNATING, RULE_HIT_CONTINUES, PRESET_DUEL, PRESET_TACTICAL, PRESET_CREATIVE, REPORT_SHOW, REPORT_HIDE, COMBAT_CLASSIC, COMBAT_SKILLS
    }

    private enum View { CHOICE, CREATE, JOIN, WAITING }

    private static final float WIDTH = 440;
    private static final float HEIGHT = 580;
    private static final float WAITING_HEIGHT = 620;
    /** Room settings: one extra 88-unit row (combat) above the shared card height. */
    private static final float SETUP_ROW = 88, SETUP_HEIGHT = HEIGHT + SETUP_ROW;
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
    private com.battleship.shared.game.GameMode selectedMode = com.battleship.shared.game.GameMode.CLASSIC;
    private int selectedShipCount = 5;
    private boolean hideSunkInfo;
    public com.battleship.shared.room.RoomOptions selectedOptions() { return new com.battleship.shared.room.RoomOptions(hideSunkInfo); }
    private com.battleship.shared.game.TurnRule selectedTurnRule = com.battleship.shared.game.TurnRule.ALTERNATING;
    private com.battleship.shared.game.CombatMode selectedCombat = com.battleship.shared.game.CombatMode.CLASSIC;
    public com.battleship.shared.game.GameRules selectedRules() {
        return new com.battleship.shared.game.GameRules(selectedMode, selectedShipCount, selectedTurnRule, selectedCombat);
    }
    public boolean configuring() { return view == View.CREATE; }
    public void changeShipCount(int delta) {
        if (view != View.CREATE || busy) return;
        selectedShipCount = Math.max(3, Math.min(5, selectedShipCount + delta)); rebuild();
    }
    public void toggleTurnRule() {
        if (view != View.CREATE || busy) return;
        selectedTurnRule = selectedTurnRule == com.battleship.shared.game.TurnRule.ALTERNATING
                ? com.battleship.shared.game.TurnRule.HIT_CONTINUES : com.battleship.shared.game.TurnRule.ALTERNATING;
        rebuild();
    }
    public com.battleship.shared.game.GameMode selectedMode() { return selectedMode; }
    public void toggleMode() {
        if (view != View.CREATE || busy) return;
        selectedMode = selectedMode == com.battleship.shared.game.GameMode.CLASSIC ? com.battleship.shared.game.GameMode.DESIGNER : com.battleship.shared.game.GameMode.CLASSIC;
        rebuild();
    }
    private RoomSnapshot room;
    private String localClientId;
    private String code = "";
    private String status = "";
    private boolean connected;
    private boolean connecting;
    private boolean busy;
    private boolean visible;
    private int viewportWidth=1440, viewportHeight=900;
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
        viewportWidth=width; viewportHeight=height;
        scale = UiTypography.scale(width, height);
        left = view==View.CREATE ? (width-1040*scale)/2 : 56 * scale;
        bottom = (height - (view==View.WAITING?WAITING_HEIGHT:view==View.CREATE?SETUP_HEIGHT:HEIGHT) * scale) / 2;
        layout.setLocalScale(scale);
        layout.setLocalTranslation(left, bottom, 0);
    }

    public void showChoice() {
        view = View.CHOICE;
        room = null;
        show();
    }

    public void showCreate() {
        view = View.CREATE;
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
        visible = true; resize(viewportWidth,viewportHeight);
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
            positionStatus();
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

    public void pasteRoomCode(String value) {
        if(!acceptsRoomCode() || value==null) return;
        String normalized=value.strip().replaceAll("\\s+", "").toUpperCase(java.util.Locale.ROOT);
        if(!normalized.matches("[A-Z2-9]{6}")) { setStatus("Clipboard must contain a six-character room code."); return; }
        code=normalized; setStatus("Room code pasted. Press Enter to join."); refreshCode();
    }
    public void selectOption(Action action) {
        if(!enabled(action)) return;
        switch(action) {
            case REPORT_SHOW -> hideSunkInfo=false;
            case REPORT_HIDE -> hideSunkInfo=true;
            case MODE_BASIC -> selectedMode=com.battleship.shared.game.GameMode.CLASSIC;
            case MODE_DESIGNER -> selectedMode=com.battleship.shared.game.GameMode.DESIGNER;
            case FLEET_THREE -> selectedShipCount=3;
            case FLEET_FOUR -> selectedShipCount=4;
            case FLEET_FIVE -> selectedShipCount=5;
            case RULE_ALTERNATING -> selectedTurnRule=com.battleship.shared.game.TurnRule.ALTERNATING;
            case RULE_HIT_CONTINUES -> selectedTurnRule=com.battleship.shared.game.TurnRule.HIT_CONTINUES;
            case COMBAT_CLASSIC -> selectedCombat=com.battleship.shared.game.CombatMode.CLASSIC;
            case COMBAT_SKILLS -> selectedCombat=com.battleship.shared.game.CombatMode.SKILLS;
            case PRESET_DUEL, PRESET_TACTICAL, PRESET_CREATIVE -> {
                selectedMode=action==Action.PRESET_CREATIVE?com.battleship.shared.game.GameMode.DESIGNER:com.battleship.shared.game.GameMode.CLASSIC;
                selectedShipCount=action==Action.PRESET_DUEL?3:5;
                selectedTurnRule=action==Action.PRESET_DUEL?com.battleship.shared.game.TurnRule.ALTERNATING:com.battleship.shared.game.TurnRule.HIT_CONTINUES;
            }
            default -> { return; }
        }
        rebuild();
    }
    private void setupFrame(float x,float width) {
        panel(x,0,width,SETUP_HEIGHT,new ColorRGBA(.003f,.009f,.025f,.95f),70);
        frame(x,0,width,SETUP_HEIGHT,materials.flat(Materials.rgb(GOLD)),71);
        frame(x+7,7,width-14,SETUP_HEIGHT-14,materials.flat(Materials.rgb(0x39495A)),71);
    }
    private void setupText(String value,float size,float x,float y,int color,float width) {
        UiTypography.fit(text(body,value,size,x,y,color),width);
    }
    private void rebuildSetup() {
        float lift=SETUP_ROW; // Existing rows keep their spacing; the combat row takes the freed bottom slot.
        setupFrame(0,318); setupFrame(340,700);
        centered(display,"YOUR FLEET",31,159,530+lift,IVORY);
        centered(body,selectedMode==com.battleship.shared.game.GameMode.CLASSIC?"BASIC":"DESIGNER",13,159,488+lift,CYAN);
        for(int i=0;i<selectedShipCount;i++) {
            var type=selectedRules().shipTypes().get(i); float y=428+lift-i*61;
            setupText(type.displayName(),14,26,y,IVORY,266);
            float length=28*type.length();
            Geometry hull=panel(28,y-35,length,12,materials.flat(Materials.rgb(0x81B9CA)),73);
            hull.setMesh(UiShapes.chamfered(length,12,6));
            for(int cell=0;cell<type.length();cell++) panel(28+cell*12,y-46,9,3,Materials.rgb(GOLD),73);
        }
        centered(body,selectedShipCount+" SHIPS",13,159,132+lift,GOLD);
        if(selectedCombat==com.battleship.shared.game.CombatMode.SKILLS) centered(body,"SKILL BATTLE",13,159,132+lift-26,CYAN);
        button(24,62,270,44,"CREATE ROOM",Action.CREATE_ROOM,true);
        button(24,18,270,34,"BACK",Action.BACK,false);
        centered(display,"CREATE ROOM",36,690,534+lift,IVORY);
        setupText("GAME MODE",12,366,489+lift,GOLD,640);
        boolean basic=selectedMode==com.battleship.shared.game.GameMode.CLASSIC;
        button(366,420+lift,308,44,"BASIC",Action.MODE_BASIC,basic);
        button(694,420+lift,320,44,"FLEET DESIGNER",Action.MODE_DESIGNER,!basic);
        setupText("FLEET SIZE",12,366,401+lift,GOLD,640);
        button(366,332+lift,202,44,"3 SHIPS",Action.FLEET_THREE,selectedShipCount==3);
        button(589,332+lift,202,44,"4 SHIPS",Action.FLEET_FOUR,selectedShipCount==4);
        button(812,332+lift,202,44,"5 SHIPS",Action.FLEET_FIVE,selectedShipCount==5);
        setupText("FIRING RULE",12,366,313+lift,GOLD,640);
        boolean alternate=selectedTurnRule==com.battleship.shared.game.TurnRule.ALTERNATING;
        button(366,244+lift,308,44,"ALTERNATE SHOTS",Action.RULE_ALTERNATING,alternate);
        button(694,244+lift,320,44,"HIT / FIRE AGAIN",Action.RULE_HIT_CONTINUES,!alternate);
        setupText("SINKING INFORMATION",12,366,225+lift,GOLD,640);
        button(366,156+lift,308,44,"SHOW SUNK INFO",Action.REPORT_SHOW,!hideSunkInfo);
        button(694,156+lift,320,44,"HIDE SUNK INFO",Action.REPORT_HIDE,hideSunkInfo);
        setupText(hideSunkInfo ? "Hits and misses only. Reveal enemy ships after the match."
                : "Reveal enemy ships when they are sunk.",12,366,126+lift,CYAN,648);
        setupText("COMBAT",12,366,178,GOLD,640);
        boolean skills=selectedCombat==com.battleship.shared.game.CombatMode.SKILLS;
        button(366,109,308,44,"CLASSIC",Action.COMBAT_CLASSIC,!skills);
        button(694,109,320,44,"SKILL BATTLE",Action.COMBAT_SKILLS,skills);
        setupText(skills ? "Normal shots charge energy (max 8). Spend it on skills; a skill ends your turn."
                : "Normal shots only.",12,366,79,CYAN,648);
        statusLabel=text(body,wrapStatus(status),10,366,30,CYAN); positionStatus(); refreshButtons();
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
            case REPORT_SHOW, REPORT_HIDE, COMBAT_CLASSIC, COMBAT_SKILLS, MODE_BASIC, MODE_DESIGNER, FLEET_THREE, FLEET_FOUR, FLEET_FIVE, RULE_ALTERNATING, RULE_HIT_CONTINUES, PRESET_DUEL, PRESET_TACTICAL, PRESET_CREATIVE, TOGGLE_MODE, TOGGLE_TURN_RULE -> view == View.CREATE && connected && !busy;
            case FEWER_SHIPS -> view == View.CREATE && selectedShipCount > 3 && connected && !busy;
            case MORE_SHIPS -> view == View.CREATE && selectedShipCount < 5 && connected && !busy;
            case COPY_CODE -> room != null && view == View.WAITING;
            case SHOW_CREATE, SHOW_JOIN -> view == View.CHOICE && connected && !busy;
            case CREATE_ROOM -> view == View.CREATE && connected && !busy;
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
        if(view==View.CREATE) { rebuildSetup(); return; }
        float cardHeight=view==View.WAITING?WAITING_HEIGHT:HEIGHT, headerLift=cardHeight-HEIGHT;
        panel(0, 0, WIDTH, cardHeight, new ColorRGBA(0.003f, 0.007f, 0.021f, 0.94f), 70);
        frame(0, 0, WIDTH, cardHeight, materials.flat(Materials.rgb(GOLD)), 71);
        frame(8, 8, WIDTH - 16, cardHeight - 16,
                materials.flat(new ColorRGBA(0.38f, 0.32f, 0.22f, 0.55f)), 71);
        diamond(WIDTH / 2, 548+headerLift, GOLD);
        centered(display, view == View.WAITING ? "WAITING ROOM" : view == View.CREATE ? "ROOM SETTINGS" : "PVP", 39, WIDTH / 2, 524+headerLift, IVORY);
        centered(body, connecting ? "CONNECTING TO SERVER" : connected ? "SERVER CONNECTED" : "SERVER DISCONNECTED",
                10, WIDTH / 2, 468+headerLift, connected ? CYAN : 0xB4B7C7);
        divider(445+headerLift);
        if (view == View.CHOICE) {
            centered(display, "Two admirals. One ocean.", 23, WIDTH / 2, 404, IVORY);
            button(34, 304, 372, 56, "CREATE ROOM", Action.SHOW_CREATE, true);
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
            centered(body, (room.mode() == com.battleship.shared.game.GameMode.CLASSIC ? "BASIC" : "FLEET DESIGNER") + " / " + room.shipCount() + " SHIPS / ROOM CODE", 10, WIDTH / 2, 433+headerLift, GOLD);
            BitmapText roomCode = centered(body, room.roomCode(), 32, WIDTH / 2, 396+headerLift, CYAN);
            copyIcon(Math.min(WIDTH-52, roomCode.getLocalTranslation().x + roomCode.getLineWidth() + 6), 383+headerLift);
            player(0, 286+headerLift);
            player(1, 208+headerLift);
            centered(body, room.turnRule().label() + (room.rules().skills() ? " / Skill battle" : ""), 10, WIDTH / 2, 196+headerLift, CYAN);
            centered(body,room.options().hideSunkInfo()?"Sunk info hidden":"Sunk info visible",9,WIDTH/2,176+headerLift,IVORY);
            boolean ready = room.players().stream().anyMatch(player -> player.clientId().equals(localClientId) && player.ready());
            button(34, 120, 372, 48, ready ? "NOT READY" : "READY", Action.TOGGLE_READY, true);
            button(34, 28, 180, 42, "INVITE FRIENDS", Action.INVITE_FRIENDS, false);
            button(226, 28, 180, 42, "LEAVE ROOM", Action.LEAVE_ROOM, false);
        }
        statusLabel = centered(body, wrapStatus(status), view == View.CREATE ? 9 : 11, WIDTH / 2, view == View.CREATE ? 37 : 107, 0xB9C7DB);
        positionStatus();
        refreshButtons();
    }

    private void positionStatus() {
        if (statusLabel == null) return;
        if (view == View.WAITING) UiTypography.centerIn(statusLabel, 34, 76, 372, 38);
        else if(view==View.CREATE) UiTypography.centerIn(statusLabel,368,16,648,30);
        else center(statusLabel,107);
    }

    private void player(int index, float y) {
        RoomPlayer player = room.players().size() > index ? room.players().get(index) : null;
        panel(34, y, 372, 66, new ColorRGBA(0.012f, 0.028f, 0.052f, 0.95f), 72);
        text(body, "PLAYER " + (index + 1), 10, 48, y + 49, GOLD);
        String name = player == null ? "Waiting for opponent" : player.playerName();
        if (name.length() > 22) name = name.substring(0, 21) + ".";
        UiTypography.fit(text(display, name, 23, 48, y + 29, player == null ? 0x8793A9 : IVORY), 232);
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
            if (button.action == Action.COPY_CODE) {
                button.border.setColor("Color", Materials.rgb(active ? button.action == hovered ? IVORY : CYAN : 0x69788D));
                continue;
            }
            boolean highlight = active && (button.primary || button.action == hovered);
            button.fill.setColor("Color", button.action==Action.LEAVE_ROOM&&active?new ColorRGBA(.25f,.025f,.035f,.98f): highlight ? new ColorRGBA(0.035f, 0.075f, 0.117f, 0.98f)
                    : new ColorRGBA(0.009f, 0.022f, 0.044f, 0.94f));
            button.border.setColor("Color", Materials.rgb(!active ? 0x334050 : highlight ? GOLD : 0x516275));
            button.label.setColor(Materials.rgb(!active ? 0x69788D : highlight ? IVORY : 0xCFD5E1));
        }
    }

    private void copyIcon(float x, float y) {
        Material fill = materials.flat(new ColorRGBA(0,0,0,0));
        Material border = materials.flat(Materials.rgb(CYAN));
        for (int i=0;i<2;i++) {
            float px=x+3+i*4, py=y+3+i*4;
            line(px,py,px+10,py,border,74); line(px,py+12,px+10,py+12,border,74);
            line(px,py,px,py+12,border,74); line(px+10,py,px+10,py+12,border,74);
        }
        BitmapText label = text(body,"",10,x,y,IVORY);
        buttons.add(new Button(x,y,24,24,Action.COPY_CODE,false,fill,border,label));
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
        com.battleship.frontend.ui.UiShapes.frame(layout,x,y,width,height,9,2,material,depth);
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
