package com.battleship.frontend.game;

import com.battleship.frontend.camera.OrbitCamera;
import com.battleship.frontend.effects.*;
import com.battleship.frontend.graphics.Materials;
import com.battleship.frontend.graphics.ship.ShipFactory;
import com.battleship.frontend.scene.BattleScene;
import com.battleship.shared.game.*;
import com.jme3.app.*;
import com.jme3.bounding.BoundingBox;
import com.jme3.font.BitmapText;
import com.jme3.math.*;
import com.jme3.renderer.RenderManager;
import com.jme3.scene.*;
import com.jme3.system.*;
import com.jme3.texture.Image;
import com.jme3.util.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Actual scene/meshes/effects; no authentication, server or database fixture. */
public final class CombatPresentationChecks extends SimpleApplication {
    private CombatPresentationChecks() { super(new StatsAppState()); }
    private BattleScene scene;
    private Node board;
    private BoardCoordinates coordinates;
    private FleetRenderer renderer;
    private AttackEffectManager effects;
    private OrbitCamera orbit;
    private Board fleet;
    private BattleHud hud;
    private OwnBoardMinimap minimap;
    private PlacementHud controls;
    private GameState placementState;
    private int stage, frame, impacts, completions;
    private static final int WIDTH = 1600, HEIGHT = 900;
    private final Coordinate target = new Coordinate(2, 2);

    public static void main(String[] args) {
        var app = new CombatPresentationChecks(); var settings = new AppSettings(true);
        settings.setResolution(WIDTH, HEIGHT); settings.setRenderer(AppSettings.LWJGL_OPENGL32);
        settings.setAudioRenderer(null); settings.setUseInput(false); settings.setSamples(4); settings.setGammaCorrection(true); settings.setFrameRate(30);
        app.setSettings(settings); app.setShowSettings(false); app.start(JmeContext.Type.OffscreenSurface);
    }

    @Override public void simpleInitApp() {
        setDisplayFps(false); setDisplayStatView(false); setPauseOnLostFocus(false);
        scene = new BattleScene(assetManager); rootNode.attachChild(scene.root()); board = scene.beginLocalGameplay();
        scene.installOceanReflections(viewPort); SceneEffects.install(assetManager, viewPort, scene.keyLight());
        var materials = new Materials(assetManager); renderer = new FleetRenderer(board, materials);
        effects = new AttackEffectManager(board, materials); coordinates = new BoardCoordinates(board); orbit = new OrbitCamera(cam);
        fleet = new Board();
        var shapes = List.of(List.of(new Coordinate(2,2),new Coordinate(3,2),new Coordinate(4,2),new Coordinate(4,3),new Coordinate(4,4)),
                List.of(new Coordinate(2,6),new Coordinate(2,7),new Coordinate(2,8),new Coordinate(3,7)),
                List.of(new Coordinate(6,2),new Coordinate(7,2),new Coordinate(7,3)),
                List.of(new Coordinate(6,6),new Coordinate(6,7),new Coordinate(6,8)),
                List.of(new Coordinate(8,4),new Coordinate(9,4)));
        for (ShipType type : ShipType.values()) fleet = fleet.withShip(new Ship(type, shapes.get(type.ordinal())));
        placementState = new GameState(GamePhase.PLACEMENT, new Player("self", "Captain Radiant", fleet),
                new Player("opponent", "Admiral Abyssal", new Board()), "self", null, null, PlacementState.initial(), false, 0);
        hud = new BattleHud(assetManager, "Captain Radiant"); hud.setOpponentName("Admiral Abyssal"); hud.setPlayerSide(Side.WHITE);
        controls = new PlacementHud(assetManager); controls.setRules(new GameRules(GameMode.DESIGNER, 5, TurnRule.ALTERNATING));
        controls.setDesigner(true, true, 0, false); controls.setNetworkPlacement(true);
        controls.update(placementState, "", false); checkCompactControls();
        hud.resize(WIDTH, HEIGHT); controls.resize(WIDTH, HEIGHT);
        hud.setGameStatus("Ship placement", "5 / 5 ships ready");
        hud.setOpponentReady(true);
        guiNode.attachChild(hud.root()); guiNode.attachChild(controls.root());
        minimap = new OwnBoardMinimap(assetManager); minimap.resize(WIDTH, HEIGHT); minimap.setSide(Side.WHITE);
        minimap.update(fleet); minimap.setBattleVisible(false); guiNode.attachChild(minimap.root());
        checkTimerArcs();
        for(long millis:new long[]{3000,2000,1000}) {
            hud.setStartCountdown(millis,false);
            require(hud.root().getChild("Match countdown").getUserData("countdownValue").equals(Long.toString(millis/1000)), "3-2-1 is rendered at center");
        }
        hud.setStartCountdown(0,true); require(hud.root().getChild("Match countdown").getUserData("countdownValue").equals("START"), "START follows 1");
        hud.setStartCountdown(0,false);
        hud.setGameStatus("Fleet confirmed","Waiting for opponent");
        var texts=new ArrayList<String>(); hud.root().depthFirstTraversal(n->{if(n instanceof BitmapText t) texts.add(t.getText());});
        require(texts.stream().noneMatch(t->t.contains("WAITING FOR OPPONENT")),"Waiting status strip removed");
        hud.setGameStatus("Ship placement","5 / 5 ships ready");
        renderer.display(fleet, true, ShipFactory.Fleet.RADIANT); rootNode.updateGeometricState(); checkLabels(); checkSources();
        orbit.setPlayerSide(Side.WHITE);
    }

    private void checkCompactControls() {
        for (int[] size : new int[][] {{1280,800}, {1600,900}, {1920,1080}}) {
            controls.resize(size[0], size[1]);
            controls.root().updateLogicalState(0); controls.root().updateGeometricState();
            var headings = new ArrayList<BoundingBox>(); var cardNames = new ArrayList<BoundingBox>();
            controls.root().depthFirstTraversal(s -> {
                if ("fleet-tray-title".equals(s.getUserData("uiRole"))) headings.add((BoundingBox) s.getWorldBound());
                if ("fleet-card-name".equals(s.getUserData("uiRole"))) cardNames.add((BoundingBox) s.getWorldBound());
            });
            require(headings.size() == 1 && cardNames.size() == 5 && cardNames.stream().noneMatch(b -> headings.getFirst().intersects(b)),
                    "Tray heading cannot overlap ship names");
            for (ShipType type : ShipType.values()) {
                var action = PlacementHud.Action.valueOf("SELECT_" + type.name());
                var center = controls.buttonCenter(action);
                require(controls.contains(center) && controls.actionAt(center).orElseThrow() == action, "Fleet card/scale hit target");
            }
            require(controls.actionAt(controls.buttonCenter(PlacementHud.Action.READY)).orElseThrow() == PlacementHud.Action.READY, "Complete fleet enables Ready");
            controls.toggleMore();
            for (var action : List.of(PlacementHud.Action.ROTATE, PlacementHud.Action.REMOVE, PlacementHud.Action.RESET, PlacementHud.Action.LEAVE_ROOM)) {
                var center = controls.buttonCenter(action);
                require(controls.contains(center) && controls.actionAt(center).orElseThrow() == action, "Foldout action remains accessible");
            }
            controls.toggleMore(); controls.setNetworkLocked(true);
            require(controls.actionAt(controls.buttonCenter(PlacementHud.Action.READY)).isEmpty()
                    && controls.actionAt(controls.buttonCenter(PlacementHud.Action.RANDOM)).isEmpty(), "Locked fleet cannot submit/change again");
            controls.setNetworkLocked(false);
        }
        controls.setDesigner(true, true, 2, true); controls.update(placementState, "", false);
        require(controls.actionAt(controls.buttonCenter(PlacementHud.Action.READY)).isEmpty(), "Draft cannot be submitted");
        controls.setDesigner(true, true, 0, false); controls.update(placementState, "", false);
        System.out.println("PASS compact UI: five fleet cards, Ready gating, foldout controls and hit targets at 3 window sizes.");
    }

    private void checkTimerArcs() {
        hud.setTurnClock(true, 60_000);
        Geometry own=(Geometry)hud.root().getChild("Player turn arc"), rival=(Geometry)hud.root().getChild("Opponent turn arc");
        int full=own.getMesh().getVertexCount(); require(full>0 && Boolean.TRUE.equals(own.getUserData("active")), "Own active timer has full arc");
        hud.setTurnClock(true,30_000); require(own.getMesh().getVertexCount()<full, "Arc shrinks with actual remaining time");
        hud.setTurnClock(false,20_000); require(own.getMesh().getVertexCount()==0 && Boolean.TRUE.equals(rival.getUserData("active"))
                && (long)rival.getUserData("remainingMillis")==20_000, "Only correct player's arc is active");
        hud.setTurnClock(false,0); require(rival.getMesh().getVertexCount()==0, "Timeout empties arc");
        hud.hideTurnClock(); System.out.println("PASS portrait timers: full/half/zero, owner switch and 60-second server-relative progress.");
    }

    private AttackResult attack(AttackResultType type, Coordinate cell) {
        return new AttackResult(type, cell, type == AttackResultType.MISS ? null : ShipType.CARRIER, false, false,
                "authenticated-player-uuid", "opponent-uuid", null, List.of(cell));
    }

    private void checkLabels() {
        var labels = (Node) board.getChild("Unobstructed coordinate labels"); require(labels.getQuantity() == 40, "All 40 labels present");
        int reversed = 0;
        for (Spatial child : labels.getChildren()) {
            BitmapText text = (BitmapText) child;
            Vector3f center = board.worldToLocal(((BoundingBox) text.getWorldBound()).getCenter(), new Vector3f());
            boolean flip = center.x > 5 || center.z < -5;
            Vector3f top = text.getLocalRotation().mult(Vector3f.UNIT_Y);
            require(Math.abs(top.z - (flip ? 1 : -1)) < .001f, "Top/right reversed, bottom/left upright");
            String expected = Math.abs(center.x) > 5 ? "" + (char) ('A' + Math.round(4.5f - center.z))
                    : Integer.toString(Math.round(center.x + 4.5f) + 1);
            require(text.getText().equals(expected), "Rotating a label cannot alter coordinate identity");
            if (flip) reversed++;
        }
        require(reversed == 20, "Exactly two coordinate bands face each seat");
        System.out.println("PASS: 40 labels, 20 reversed, original A-J/1-10 cell mapping retained.");
    }

    private void checkSources() {
        for (Side side : Side.values()) {
            effects.play(attack(AttackResultType.HIT, target), List.of(), side, () -> impacts++, () -> completions++);
            effects.update(AttackEffectManager.AIM_DURATION); rootNode.updateGeometricState();
            var shell = board.getChild("Naval shell"); require(shell != null, "Shell exists");
            Vector3f source = shell.getWorldTranslation(); var distances = new ArrayList<Float>();
            rootNode.depthFirstTraversal(s -> { if (side.name().equals(s.getUserData("firingSide"))) distances.add(s.getWorldTranslation().distance(source)); });
            require(!distances.isEmpty() && distances.stream().min(Float::compare).orElseThrow() < .001f, "Shell starts at an actual same-faction ocean cannon");
            Vector3f local = board.worldToLocal(source, new Vector3f()); require(Math.abs(local.x) > 5 || Math.abs(local.z) > 5, "Launch is outside the grid");
            require(((BoundingBox) shell.getWorldBound()).getXExtent() > .12f, "Larger shell is visible in world scale");
            effects.clear();
        }
        System.out.println("PASS: both factions/UUID shooter IDs launch from real ocean-vessel transforms; larger shell.");
    }

    @Override public void simpleUpdate(float delta) { scene.update(0, cam); }

    @Override public void simpleRender(RenderManager manager) {
        if (++frame < 5) return;
        frame = 0;
        try {
            capture(manager, new String[] {"designer-white-seat", "designer-black-seat", "ocean-projectile", "tile-explosion", "tile-water-splash", "custom-wreck-white", "custom-wreck-black", "classic-wreck"}[stage]);
            switch (++stage) {
                case 1 -> { renderer.display(fleet, true, ShipFactory.Fleet.ABYSSAL); orbit.setPlayerSide(Side.BLACK); hud.setPlayerSide(Side.BLACK); }
                case 2 -> {
                    hud.setPlayerSide(Side.WHITE);
                    var battle = new GameState(GamePhase.BATTLE, placementState.player(), placementState.enemy(), "opponent", null, null, PlacementState.initial(), true, 2);
                    hud.setBattleState(battle, GameRules.DEFAULT); minimap.setBattleVisible(true);
                    hud.setGameStatus("Your turn", "Target C3"); hud.setTurnClock(false, 60_000);
                    var captions=new ArrayList<String>(); hud.root().depthFirstTraversal(n->{if(n instanceof BitmapText t) captions.add(t.getText());});
                    require(!captions.contains("YOUR FLEET")&&!captions.contains("YOUR TURN"),"Battle header strips removed");
                    require(board.getChild("Fleet translucent backing")==null && hud.root().getChild("Fleet translucent backing")!=null,"White haze stays in UI, not world scene");
                    hud.root().updateLogicalState(0); hud.root().updateGeometricState();
                    var backing=(BoundingBox)hud.root().getChild("Fleet translucent backing").getWorldBound();
                    var opponent=(BoundingBox)hud.root().getChild("Enemy commander portrait").getWorldBound();
                    require(backing.getCenter().y-backing.getYExtent()<opponent.getCenter().y+opponent.getYExtent(),"Backing joins behind rival portrait");
                    require(backing.getCenter().z<opponent.getCenter().z,"Backing cannot wash over portrait");
                    var underlay=(BoundingBox)hud.root().getChild("Enemy portrait underlay").getWorldBound();
                    require(underlay.getCenter().z>backing.getCenter().z && underlay.getCenter().z<opponent.getCenter().z,
                            "Opaque circular underlay blocks fleet haze through transparent portrait pixels");
                    float portraitDepth=opponent.getCenter().z;
                    hud.root().getChild("Enemy fleet status").depthFirstTraversal(n->{
                        if(n instanceof Geometry g) require(g.getWorldTranslation().z<portraitDepth,
                                "Every fleet ornament and border stays behind opponent portrait");
                    });
                    require("opponent".equals(hud.root().getChild("Enemy fleet status").getUserData("fleetOwner"))
                            && "self".equals(hud.root().getChild("Own commander portrait").getUserData("commanderRole")),"Own/enemy portrait and fleet roles stay distinct");
                    var borderCount=new int[1]; hud.root().depthFirstTraversal(s->{if("Enemy ship cell border".equals(s.getName()))borderCount[0]++;});
                    require(borderCount[0]==17,"Each configured enemy ship cell has white outline");
                    controls.update(new GameState(GamePhase.BATTLE, placementState.player(), placementState.enemy(), "opponent", null, null, PlacementState.initial(), true, 2), "", true);
                    cam.setLocation(new Vector3f(-18, 22, 28)); cam.lookAt(Vector3f.ZERO, Vector3f.UNIT_Y);
                    effects.play(attack(AttackResultType.HIT, target), List.of(), Side.WHITE, () -> impacts++, () -> completions++);
                    effects.update(AttackEffectManager.AIM_DURATION + .25f);
                }
                case 3 -> {
                    cam.setLocation(new Vector3f(12, 15, 12)); cam.lookAt(new Vector3f(0,.5f,0), Vector3f.UNIT_Y);
                    effects.update(AttackEffectManager.IMPACT_TIME - AttackEffectManager.AIM_DURATION - .25f + .22f);
                    require(board.getChild("Hit explosion") != null && board.getChild("Miss water splash") == null && impacts == 1, "Hit creates explosion exactly once");
                    hud.setGameStatus("HIT", "Target C3 / resolving volley"); hud.setShotResult("HIT!", true);
                }
                case 4 -> {
                    effects.update(AttackEffectManager.IMPACT_LIFETIME);
                    require(effects.isBusy() && effects.hasImpacted() && completions == 0, "Result hold persists after burst fades");
                    effects.update(AttackEffectManager.TOTAL_DURATION); require(completions == 1, "Impact/complete lifecycle");
                    effects.play(attack(AttackResultType.MISS, new Coordinate(4,8)), List.of(), Side.BLACK, () -> impacts++, () -> completions++);
                    effects.update(AttackEffectManager.IMPACT_TIME + .1f);
                    require(board.getChild("Miss water splash") != null && board.getChild("Hit explosion") == null, "Miss creates only water");
                    hud.setGameStatus("MISS", "Target I5 / resolving volley"); hud.setShotResult("MISS", false);
                }
                case 5 -> {
                    effects.update(AttackEffectManager.TOTAL_DURATION); effects.update(1);
                    require(!effects.isBusy() && impacts == 2 && completions == 2, "No duplicate callbacks or lingering effects");
                    showWreck(false, ShipFactory.Fleet.RADIANT);
                }
                case 6 -> showWreck(false, ShipFactory.Fleet.ABYSSAL);
                case 7 -> showWreck(true, ShipFactory.Fleet.RADIANT);
                case 8 -> {
                    renderer.display(fleet, true, ShipFactory.Fleet.RADIANT);
                    require(renderer.root().getChild("Sunk ship damage") == null && renderer.root().getQuantity() == 5, "New fleet restores intact pieces without old damage");
                    System.out.println("PASS OpenGL: compact placement/battle UI, result hold, both faction custom/classic wrecks, hidden-ship guard, fresh fleet reset; projectile/HIT/MISS callbacks."); stop();
                }
            }
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private void showWreck(boolean classic, ShipFactory.Fleet faction) {
        Ship intact = classic ? new Ship(ShipType.CARRIER, new Coordinate(3,2), ShipOrientation.HORIZONTAL) : fleet.ship(ShipType.CARRIER);
        Ship sunk = intact;
        for (Coordinate cell : intact.occupiedCells()) sunk = sunk.withHit(cell);
        Board wreck = new Board().withShip(sunk).withShip(fleet.ship(ShipType.DESTROYER));
        renderer.display(wreck, false, faction);
        require(renderer.root().getQuantity() == 1 && Boolean.TRUE.equals(renderer.root().getChild(0).getUserData("wreck")), "Only confirmed sunk ship is revealed as wreck");
        require(renderer.root().getChild("Sunk ship damage") != null && renderer.root().getChild("Wreck smoke") != null, "Wreck includes broken armor/scorch/smoke");
        hud.setGameStatus("Carrier sunk", "Wreck revealed / enemy fleet hidden");
        cam.setLocation(new Vector3f(12, 15, 12)); cam.lookAt(new Vector3f(0,.5f,0), Vector3f.UNIT_Y);
    }

    private void capture(RenderManager manager, String name) throws Exception {
        var buffer = BufferUtils.createByteBuffer(WIDTH * HEIGHT * 4);
        manager.getRenderer().readFrameBufferWithFormat(null, buffer, Image.Format.BGRA8);
        var image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_4BYTE_ABGR); Screenshots.convertScreenShot(buffer, image);
        var folder = Path.of("build/combat-review"); Files.createDirectories(folder); ImageIO.write(image, "png", folder.resolve(name + ".png").toFile());
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    @Override public void handleError(String message, Throwable error) { if (error != null) error.printStackTrace(); System.exit(1); }
}
