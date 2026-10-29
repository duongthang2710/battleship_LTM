package com.battleship.frontend.game;

import com.battleship.frontend.graphics.Materials;
import com.battleship.frontend.ui.UiTypography;
import com.battleship.frontend.ui.UiShapes;
import com.battleship.frontend.ui.NavalHudSkin;
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
        SELECT_DESTROYER, ROTATE, REMOVE, RESET, RANDOM, READY, TOGGLE_BOARD, RESTART, LEAVE_ROOM, TOGGLE_DRAW, MORE, SETTINGS
    }

    private static final int GOLD = 0xDEB663;
    private static final int IVORY = 0xF4EAD1;
    private static final int CYAN = 0x91CEFF;
    private final Node root = new Node("Placement HUD");
    private final Node layout = new Node("Placement controls");
    private final Materials materials;
    private final AssetManager assets;
    private final BitmapFont display;
    private final BitmapFont body;
    private final BitmapFont hudFont;
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
    private float logicalWidth = 1280, logicalHeight = 800;
    private boolean more;
    private final List<Region> regions = new ArrayList<>();
    private boolean designer, drawing, draft;
    private com.battleship.shared.game.GameRules rules = com.battleship.shared.game.GameRules.DEFAULT;
    public void setRules(com.battleship.shared.game.GameRules rules) { this.rules = rules; }
    private int draftCount;
    public void setDesigner(boolean designer, boolean drawing, int count, boolean draft) {
        this.designer = designer; this.drawing = drawing; this.draftCount = count; this.draft = draft;
    }

    public PlacementHud(AssetManager assets) {
        this.assets = assets;
        materials = new Materials(assets);
        display = UiTypography.display(assets);
        body = UiTypography.body(assets);
        hudFont = UiTypography.hud(assets);
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
        logicalWidth = width / scale; logicalHeight = height / scale;
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
                + placedMask + ":" + message + ":" + enemyView + ":" + designer + ":" + drawing + ":" + draftCount + ":" + draft + ":" + rules;
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
        return regions.stream().anyMatch(r -> r.contains(screen.x / scale, screen.y / scale));
    }

    public void toggleMore() { more = !more; rebuild(); }

    public Vector2f buttonCenter(Action action) {
        return buttons.stream().filter(b -> b.action == action).findFirst()
                .map(b -> new Vector2f((b.x + b.width / 2) * scale, (b.y + b.height / 2) * scale))
                .orElseThrow(() -> new IllegalArgumentException("Hidden action: " + action));
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
        layout.detachAllChildren(); buttons.clear(); regions.clear();
        if (state == null) return;
        var draw = new NavalHudSkin(assets, layout, 30);
        float w = logicalWidth, h = logicalHeight;
        draw.gear(w - 48, h - 44, 27);
        regions.add(new Region(w - 78, h - 74, 60, 60));
        buttons.add(new Button(w - 78, h - 74, 60, 60, Action.SETTINGS, true));
        if (state.phase() != GamePhase.PLACEMENT) {
            more = false;
            button(w / 2 - 110, 24, 180, 31, enemyView ? "Your fleet  [Tab]" : "Enemy waters  [Tab]", Action.TOGGLE_BOARD, true, false);
            if (networkPlacement) button(w / 2 + 80, 24, 72, 31, "Leave", Action.LEAVE_ROOM, true, false);
            regions.add(new Region(w / 2 - 110,24,262,31));
            return;
        }
        float left = com.battleship.frontend.ui.BattleSidebarLayout.flagLeft(w);
        float flagWidth = com.battleship.frontend.ui.BattleSidebarLayout.flagWidth(w);
        int placed = state.player().board().ships().size();
        draw.text("YOUR FLEET",17,left+6,h-282,flagWidth-12,30,IVORY).setUserData("uiRole", "fleet-tray-title");
        draw.text(placed+" / "+rules.shipCount()+" READY",12,left+6,h-306,flagWidth-12,24,CYAN);
        regions.add(new Region(left,70,flagWidth,h-70));
        for (int i=0;i<rules.shipCount();i++) shipCard(left+10,h-371-i*58,flagWidth-20,rules.shipTypes().get(i));
        boolean ready=state.canReady(rules)&&!networkLocked&&!draft;
        button(w/2-148,44,296,48,networkLocked?"WAITING...":"READY!",Action.READY,ready,ready);
        button(w/2+160,49,49,37,"...",Action.MORE,true,more);
        regions.add(new Region(w/2-148,44,357,48));
        float tipsX=w*.76f, tipsY=h-438;
        draw.panel(tipsX,tipsY,112,326,0x123C58); regions.add(new Region(tipsX,tipsY,112,326));
        draw.text(designer&&drawing?"Drag to\ndraw":"Drag to\nmove",19,tipsX+8,tipsY+242,96,62,IVORY);
        draw.text(designer&&drawing?"Right drag\nto erase":"T to rotate",14,tipsX+8,tipsY+198,96,45,CYAN);
        draw.moveArrows(tipsX+56,tipsY+153);
        button(tipsX+10,tipsY+79,92,36,"Rotate",Action.ROTATE,!networkLocked&&!draft,false);
        if(designer) button(tipsX+10,tipsY+24,92,36,drawing?"Draw [G]":"Move [G]",Action.TOGGLE_DRAW,!networkLocked,drawing);
        else draw.text("Click a cell\nto place",14,tipsX+8,tipsY+19,96,48,IVORY);
        float cy=tipsY-63, cx=tipsX+56;
        draw.arc("Random medallion",cx,cy,15,30,0,FastMath.TWO_PI,0x12557C);
        draw.arc("Random rim",cx,cy,30,2,0,FastMath.TWO_PI,GOLD);
        draw.refreshIcon(cx,cy);
        buttons.add(new Button(cx-31,cy-31,62,62,Action.RANDOM,!networkLocked));
        regions.add(new Region(cx-31,cy-31,62,62));
        draw.text("RANDOM [F]",12,cx-48,cy-55,96,20,IVORY);
        String hint=networkLocked?"Fleet locked. Waiting for opponent.":draft?draftCount+" / "+selected.length()+" cells | Enter to confirm":message;
        draw.text(hint,14,w*.26f,106,w*.48f,25,CYAN);
        if(more) {
            float mx=w/2-149,my=150;
            card(mx,my,298,networkPlacement?154:110);
            button(mx+14,my+62,130,34,"Rotate [T]",Action.ROTATE,!networkLocked&&!draft,false);
            button(mx+154,my+62,130,34,draft?"Clear draft":"Remove",Action.REMOVE,!networkLocked&&selected!=null&&(draft||state.player().board().ship(selected)!=null),false);
            button(mx+14,my+16,270,34,"Reset fleet [C]",Action.RESET,!networkLocked,false);
            if(networkPlacement) button(mx+14,my+110,270,30,"Leave room",Action.LEAVE_ROOM,true,false);
        }
    }

    private void shipCard(float x,float y,float width,ShipType type) {
        boolean placed=state.player().board().ship(type)!=null;
        button(x,y,width,51,"",Action.valueOf("SELECT_"+type.name()),!networkLocked,type==selected);
        BitmapText name=text(body,type.displayName(),11,x,y,IVORY);
        name.setUserData("uiRole","fleet-card-name"); UiTypography.centerIn(name,x,y+27,width,23);
        float length=12+type.length()*8;
        Geometry hull=panel(x+(width-length)/2,y+14,length,9,Materials.rgb(placed?CYAN:0x92A5B7));
        hull.setMesh(UiShapes.chamfered(length,9,4));
        float start=x+(width-(type.length()*8-2))/2;
        for(int i=0;i<type.length();i++) panel(start+i*8,y+5,6,3,Materials.rgb(placed?GOLD:0x4E6C86));
    }

    private void card(float x, float y, float width, float height) {
        panel(x, y, width, height, new ColorRGBA(.003f, .009f, .023f, .94f))
                .setMesh(UiShapes.chamfered(width, height, 7));
        frame(x, y, width, height, 0x8D7956);
        regions.add(new Region(x, y, width, height));
    }

    private void button(float x, float y, float width, float height, String label,
                        Action action, boolean enabled, boolean selected) {
        ColorRGBA fill = selected ? Materials.linear(0x175D85)
                : new ColorRGBA(0.009f, 0.022f, 0.044f, enabled ? 0.9f : 0.55f);
        panel(x, y, width, height, fill).setMesh(UiShapes.chamfered(width, height, 7));
        frame(x, y, width, height, enabled ? (selected ? GOLD : 0x617D94) : 0x394659);
        UiTypography.centerIn(text(action == Action.READY ? hudFont : body, label, action == Action.READY ? 25 : 12, x, y, enabled ? IVORY : 0x6E7989),
                x, y, width, height);
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
        geometry.setLocalTranslation(x, y, 31);
    }

    private BitmapText text(BitmapFont font, String value, float size, float x, float y, int color) {
        BitmapText label = new BitmapText(font);
        label.setText(UiTypography.normalize(value));
        label.setSize(font == body ? UiTypography.bodySize(size) : size);
        label.setColor(Materials.rgb(color));
        label.setLocalTranslation(x, y, 32);
        layout.attachChild(label);
        return label;
    }

    private Geometry panel(float x, float y, float width, float height, ColorRGBA color) {
        Geometry geometry = new Geometry("Fleet control", new Quad(width, height));
        Material material = materials.flat(color);
        material.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        geometry.setMaterial(material);
        geometry.setQueueBucket(Bucket.Gui);
        geometry.setLocalTranslation(x, y, 30);
        layout.attachChild(geometry);
        return geometry;
    }

    private record Region(float x, float y, float width, float height) {
        boolean contains(float px, float py) { return px >= x && px <= x + width && py >= y && py <= y + height; }
    }
    private record Button(float x, float y, float width, float height, Action action, boolean enabled) {}
}
