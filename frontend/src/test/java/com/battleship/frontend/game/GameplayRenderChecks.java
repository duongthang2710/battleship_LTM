package com.battleship.frontend.game;

import com.battleship.frontend.app.ScreenController;
import com.battleship.frontend.camera.OrbitCamera;
import com.battleship.frontend.effects.AttackEffectManager;
import com.battleship.frontend.effects.SceneEffects;
import com.battleship.frontend.graphics.Materials;
import com.battleship.frontend.input.PointerInput;
import com.battleship.frontend.scene.BattleScene;
import com.battleship.shared.game.GameEngine;
import com.battleship.shared.game.GamePhase;
import com.battleship.shared.game.GameState;
import com.battleship.shared.game.*;
import com.jme3.app.FlyCamAppState;
import com.jme3.app.SimpleApplication;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.dummy.DummyKeyInput;
import com.jme3.input.dummy.DummyMouseInput;
import com.jme3.input.event.KeyInputEvent;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.math.FastMath;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.RenderManager;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeContext;
import com.jme3.texture.Image;
import com.jme3.util.BufferUtils;
import com.jme3.util.Screenshots;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Random;
import javax.imageio.ImageIO;

/** Small native render acceptance check; deliberately separate from the headless build checks. */
public final class GameplayRenderChecks extends SimpleApplication {
    private BattleScene scene;
    private Node board;
    private BoardCoordinates coordinates;
    private GameController game;
    private GameInput gameInput;
    private OwnBoardMinimap minimap;
    private GameOverOverlay gameOver;
    private Coordinate firstHit;
    private InjectedMouse mouse;
    private int frame;
    private Vector3f radiantAnchor;
    private Vector3f abyssalAnchor;
    private boolean returnedToMenu;

    public static void main(String[] args) {
        GameplayRenderChecks app = new GameplayRenderChecks();
        AppSettings settings = new AppSettings(true);
        settings.setRenderer(AppSettings.LWJGL_OPENGL32);
        settings.setResolution(1440, 900);
        settings.setSamples(4);
        settings.setGammaCorrection(true);
        settings.setAudioRenderer(null);
        settings.setUseInput(false);
        app.setSettings(settings);
        app.setShowSettings(false);
        app.start(JmeContext.Type.OffscreenSurface);
    }

    @Override public void simpleInitApp() {
        setDisplayFps(false);
        setDisplayStatView(false);
        // Synthetic input must not initialize a second camera controller after simpleInitApp.
        stateManager.detach(stateManager.getState(FlyCamAppState.class));
        if (flyCam != null) flyCam.setEnabled(false);
        scene = new BattleScene(assetManager);
        rootNode.attachChild(scene.root());
        mouse = new InjectedMouse();
        DummyKeyInput keyboard = new DummyKeyInput();
        mouse.initialize();
        keyboard.initialize();
        InputManager input = new InputManager(mouse, keyboard, null, null);
        inputManager = input; // Default app states also need a valid input manager during cleanup.
        OrbitCamera orbit = new OrbitCamera(cam);
        PointerInput setupInput = new PointerInput(input, orbit, cursor -> { });
        checkCamera();
        setupInput.close();
        scene.installOceanReflections(viewPort);
        SceneEffects.install(assetManager, viewPort, scene.keyLight());
        BattleHud hud = new BattleHud(assetManager, "Captain");
        PlacementHud controls = new PlacementHud(assetManager);
        minimap = new OwnBoardMinimap(assetManager);
        gameOver = new GameOverOverlay(assetManager);
        hud.resize(1440, 900);
        controls.resize(1440, 900);
        minimap.resize(1440, 900);
        gameOver.resize(1440, 900);
        guiNode.attachChild(hud.root());
        guiNode.attachChild(controls.root());
        guiNode.attachChild(minimap.root());
        guiNode.attachChild(gameOver.root());
        board = scene.beginLocalGameplay();
        coordinates = new BoardCoordinates(board);
        Materials materials = new Materials(assetManager);
        game = new GameController(new GameEngine("Captain", new Random(83)), new BoardPicker(cam, input, board),
                new FleetRenderer(board, materials), new PlacementFeedback(board, materials), controls, hud, minimap,
                new TargetingRenderer(board, materials), new AttackEffectManager(board, materials), gameOver,
                () -> returnedToMenu = true);
        gameInput = new GameInput(input, game, orbit);
    }

    @Override public void simpleUpdate(float delta) {
        scene.update(delta, cam);
        game.update(0, inputManager.getCursorPosition()); // Advance FX at scripted stages for stable screenshots.
        if (++frame == 25) {
            radiantAnchor = castle("RadiantCastleRoot");
            abyssalAnchor = castle("AbyssalCastleRoot");
            game.perform(PlacementHud.Action.READY);
            require(game.state().phase() == GamePhase.PLACEMENT, "Empty fleet cannot Ready");
            checkCamera();
            Board beforeDrag = fleet();
            Vector2f cell = screen(new Coordinate(0, 0));
            emit(() -> {
                inputManager.onMouseButtonEvent(new MouseButtonEvent(MouseInput.BUTTON_LEFT, true, (int) cell.x, (int) cell.y));
                inputManager.onMouseMotionEvent(new MouseMotionEvent((int) cell.x, (int) cell.y, 12, 0, 0, 0));
                inputManager.onMouseButtonEvent(new MouseButtonEvent(MouseInput.BUTTON_LEFT, false, (int) cell.x, (int) cell.y));
            });
            require(fleet() == beforeDrag, "Left drag over board never places a ship");
            resetCamera();
            PlacementHud.Action[] selections = {PlacementHud.Action.SELECT_CARRIER,
                    PlacementHud.Action.SELECT_BATTLESHIP, PlacementHud.Action.SELECT_CRUISER,
                    PlacementHud.Action.SELECT_SUBMARINE, PlacementHud.Action.SELECT_DESTROYER};
            for (int i = 0; i < selections.length; i++) {
                game.perform(selections[i]);
                click(new Coordinate(i * 2, 0));
            }
            require(fleet().fleet().isComplete() && fleet().ships().size() == 5, "Five ships placed by raycast");
            require(fleet().ships().stream().mapToInt(ship -> ship.occupiedCells().size()).sum() == 17, "17 occupied cells");
            requireMinimap(new Coordinate(0, 0), 0x397B97);
            require(((Node) minimap.root().getChild("Minimap fleet")).getQuantity() == 5,
                    "Minimap shows five continuous ship silhouettes");
            Vector3f first = minimapCell(new Coordinate(0, 0)).getLocalTranslation();
            require(minimapCell(new Coordinate(1, 0)).getLocalTranslation().x > first.x
                    && screen(new Coordinate(1, 0)).x > screen(new Coordinate(0, 0)).x,
                    "Minimap row direction matches player camera after 90-degree rotation");
            require(minimapCell(new Coordinate(0, 1)).getLocalTranslation().y > first.y
                    && screen(new Coordinate(0, 1)).y > screen(new Coordinate(0, 0)).y,
                    "Minimap column direction matches player camera after 90-degree rotation");
            Board beforeMinimapClick = fleet();
            game.primaryClick(new Vector2f(1200, 350));
            require(fleet() == beforeMinimapClick, "Display-only minimap blocks click-through");
            Board beforeClickMove = fleet();
            click(new Coordinate(0, 2));
            click(new Coordinate(1, 2));
            require(fleet() == beforeClickMove && !game.state().placement().moving(),
                    "Clicking a placed ship selects it without click-to-move");
            dragPlacedShip(new Coordinate(0, 2), new Coordinate(1, 0), false);
            require(fleet().ship(ShipType.CARRIER).position().equals(new Coordinate(1, 0)),
                    "Drag preserves grab offset: " + fleet().ship(ShipType.CARRIER).position());
            Board before = fleet();
            dragPlacedShip(new Coordinate(1, 2), new Coordinate(2, 0), false);
            require(fleet() == before, "Overlap drop preserves authoritative board");
            dragPlacedShip(new Coordinate(1, 2), new Coordinate(1, 9), false);
            require(fleet() == before, "Outside-board drop preserves authoritative board");
            beginPlacedDrag(new Coordinate(1, 2));
            resetCamera();
            require(fleet() == before && !game.state().placement().moving(), "R cancels ship drag");
            game.perform(PlacementHud.Action.ROTATE);
            require(fleet() == before, "Blocked rotation preserves authoritative board");
            dragPlacedShip(new Coordinate(1, 2), new Coordinate(1, 8), true);
            require(fleet().ship(ShipType.CARRIER).orientation() == ShipOrientation.VERTICAL
                    && fleet().ship(ShipType.CARRIER).position().equals(new Coordinate(1, 8)), "Drag rotation and reposition");
            game.perform(PlacementHud.Action.SELECT_DESTROYER);
            game.perform(PlacementHud.Action.REMOVE);
            game.perform(PlacementHud.Action.READY);
            require(game.state().phase() == GamePhase.PLACEMENT && fleet().ships().size() == 4, "Incomplete Ready gated");
            click(new Coordinate(8, 0));
            require(fleet().fleet().isComplete(), "Removed ship can be replaced");
            game.perform(PlacementHud.Action.SELECT_CARRIER);
            game.update(screen(new Coordinate(1, 8)));
        } else if (frame == 45) {
            game.perform(PlacementHud.Action.RESET);
            require(fleet().ships().isEmpty() && visibleShips() == 0, "Reset clears models and fleet");
            game.perform(PlacementHud.Action.RANDOM);
            var occupied = new HashSet<Coordinate>();
            for (Ship ship : fleet().ships()) for (Coordinate cell : ship.occupiedCells())
                require(Board.inBounds(cell) && occupied.add(cell), "Random placement bounds/overlap");
            require(occupied.size() == 17 && fleet().fleet().isComplete(), "Random complete fleet");
            game.perform(PlacementHud.Action.READY);
            require(game.state().phase() == GamePhase.BATTLE && game.state().enemy().board().fleet().isComplete(), "Ready starts local battle");
            require(game.enemyView() && visibleShips() == 0, "Enemy fleet stays hidden");
            checkCamera();
            firstHit = game.state().enemy().board().ship(ShipType.CARRIER).occupiedCells().getFirst();
            game.update(screen(firstHit));
            require(board.getChild("Attack targeting reticle").getLocalCullHint() == com.jme3.scene.Spatial.CullHint.Inherit,
                    "Unattacked enemy cell has reticle");
            click(firstHit);
            require(game.state().lastAttack().type() == AttackResultType.HIT
                    && game.state().currentPlayerId().equals(game.state().enemy().id()), "Rendered click hits and changes turn");
            GameState afterAttack = game.state();
            click(firstHit);
            require(game.state() == afterAttack && game.attackAnimating(), "Busy attack blocks repeat click");
            require(((Node) board.getChild("Gameplay attack markers")).getQuantity() == 0, "Markers wait for impact");
        } else if (frame == 55) {
            game.update(0.4f, screen(firstHit));
            require(board.getChild("Board-local projectile").getLocalCullHint() == com.jme3.scene.Spatial.CullHint.Inherit,
                    "Projectile flies before impact");
            checkCamera();
        } else if (frame == 65) {
            game.update(0.4f, screen(firstHit));
            require(board.getChild("Hit light burst") != null, "Hit burst exists at impact");
            require(((Node) board.getChild("Gameplay attack markers")).getQuantity() == 2, "Hit cross marker committed at impact");
        } else if (frame == 75) {
            game.update(1, screen(firstHit));
            require(!game.attackAnimating() && !game.enemyView(), "Completion opens manual Abyssal turn");
            require(visibleShips() == 5, "Own fleet view preserves existing models");
            Coordinate miss = emptyCell(fleet());
            click(miss);
            require(game.state().lastAttack().type() == AttackResultType.MISS, "Other player manually fires a miss");
            game.update(0.8f, screen(miss));
            require(board.getChild("Miss water splash") != null && board.getChild("Hit light burst") == null, "Miss has splash without explosion");
            requireMinimap(miss, 0x071F38);
        } else if (frame == 85) {
            game.update(1, screen(firstHit));
            GameState beforeRepeat = game.state();
            click(firstHit);
            require(game.state() == beforeRepeat && !game.attackAnimating(), "Previously attacked cell cannot animate again");
            game.update(screen(firstHit));
            require(board.getChild("Attack targeting reticle").getCullHint() == com.jme3.scene.Spatial.CullHint.Always,
                    "Previously attacked cell has no fresh reticle");
            game.perform(PlacementHud.Action.TOGGLE_BOARD);
            require(!game.enemyView() && visibleShips() == 5, "Tab still inspects own fleet");
            game.perform(PlacementHud.Action.TOGGLE_BOARD);
            requireMinimap(fleet().ship(ShipType.CARRIER).position(), 0x397B97);
            checkMinimapMarkers();
            finishLocalMatch();
            require(game.state().phase() == GamePhase.GAME_OVER && game.state().winnerId().equals("player"), "Rendered local match ends in victory");
            require(gameOver.visible() && visibleShips() == 5, "Victory overlay and all sunk enemy ships revealed");
            GameState terminal = game.state();
            Coordinate afterGame = new Coordinate(0, 0);
            require(!gameOver.contains(screen(afterGame)), "Post-game attack check avoids Restart card");
            click(afterGame);
            require(game.state() == terminal && !game.attackAnimating(), "Game over rejects further attacks");
            checkCamera();
        } else if (frame == 95) {
            require(gameOver.mainMenuAt(new Vector2f(821, 386)), "Main Menu hit target");
            clickScreen(new Vector2f(821, 386));
            require(returnedToMenu && game.state().phase() == GamePhase.GAME_OVER,
                    "Game Over Main Menu forwards navigation without restarting match");
            require(gameOver.restartAt(new Vector2f(618, 386)), "Restart hit target");
            emit(() -> {
                inputManager.onMouseButtonEvent(new MouseButtonEvent(MouseInput.BUTTON_LEFT, true, 618, 386));
                inputManager.onMouseButtonEvent(new MouseButtonEvent(MouseInput.BUTTON_LEFT, false, 618, 386));
            });
            require(game.state().phase() == GamePhase.PLACEMENT && fleet().ships().isEmpty() && !gameOver.visible(),
                    "Restart button restores placement and clears overlay");
        }
    }

    @Override public void simpleRender(RenderManager renderer) {
        try {
            if (frame == 35) capture(renderer, "local-placement.png");
            if (frame == 55) capture(renderer, "attack-projectile.png");
            if (frame == 65) capture(renderer, "attack-hit.png");
            if (frame == 75) capture(renderer, "attack-miss.png");
            if (frame == 85) capture(renderer, "local-victory.png");
            if (frame == 95) {
                require(castle("RadiantCastleRoot").distance(radiantAnchor) < 0.001f
                        && castle("AbyssalCastleRoot").distance(abyssalAnchor) < 0.001f, "Locked castle anchors unchanged");
                capture(renderer, "local-battle.png");
                System.out.println("PASS: complete manual local match; reticle, projectile, HIT/MISS effects, SUNK reveal, turns/repeat guards, victory/Restart; camera/minimap/placement preserved.");
                stop();
            }
        } catch (Exception error) { throw new RuntimeException(error); }
    }

    private Board fleet() { return game.state().player().board(); }
    private void finishLocalMatch() {
        boolean enemyHitChecked = false;
        for (Ship ship : game.state().enemy().board().ships()) {
            for (Coordinate cell : ship.occupiedCells()) {
                if (game.state().enemy().board().wasAttacked(cell)) continue;
                click(cell);
                require(game.attackAnimating() && game.state().lastAttack().accepted(), "Player shot accepted");
                game.update(0.8f, screen(cell));
                if (game.state().lastAttack().sunk())
                    require(visibleShips() == game.state().enemy().board().ships().stream().filter(Ship::isSunk).count(),
                            "Sunk ships revealed using existing models");
                game.update(1, screen(cell));
                if (game.state().phase() == GamePhase.GAME_OVER) return;
                Coordinate response = enemyHitChecked ? emptyCell(fleet()) : fleet().ship(ShipType.CARRIER).occupiedCells().getFirst();
                click(response);
                require(game.attackAnimating() && game.state().lastAttack().attackerId().equals("enemy"), "Manual enemy shot accepted");
                game.update(0.8f, screen(response));
                if (!enemyHitChecked) {
                    requireMinimap(response, 0x743440);
                    enemyHitChecked = true;
                }
                game.update(1, screen(response));
            }
        }
    }
    private Coordinate emptyCell(Board board) {
        return java.util.stream.IntStream.range(0, Board.SIZE * Board.SIZE)
                .mapToObj(i -> new Coordinate(i / Board.SIZE, i % Board.SIZE))
                .filter(cell -> board.cell(cell).state() == CellState.EMPTY).findFirst().orElseThrow();
    }
    private void checkCamera() {
        resetCamera();
        Vector3f initial = cam.getLocation().clone();
        float radius = FastMath.sqrt(initial.x * initial.x + initial.z * initial.z);
        for (int i = 1; i <= 4; i++) {
            drag(MouseInput.BUTTON_LEFT);
            float angle = FastMath.HALF_PI - i * 1.92f;
            require(cam.getLocation().distance(new Vector3f(radius * FastMath.sin(angle), initial.y,
                    radius * FastMath.cos(angle))) < 0.005f, "Left drag crosses full 360 without yaw clamp");
        }
        Vector3f beforeRight = cam.getLocation().clone();
        drag(MouseInput.BUTTON_RIGHT);
        require(cam.getLocation().distance(beforeRight) < 0.001f, "Right mouse no longer orbits camera");
        emit(() -> inputManager.onMouseMotionEvent(new MouseMotionEvent(1200, 350, 0, 0, 5000, 5000)));
        require(Math.abs(cam.getLocation().distance(new Vector3f(0, 0.2f, 0)) - 20) < 0.001f, "Zoom minimum");
        emit(() -> inputManager.onMouseMotionEvent(new MouseMotionEvent(1200, 350, 0, 0, 0, -5000)));
        require(Math.abs(cam.getLocation().distance(new Vector3f(0, 0.2f, 0)) - 27) < 0.001f, "Zoom maximum");
        resetCamera();
        require(cam.getLocation().distance(initial) < 0.001f, "R restores default camera");
        MouseMotionEvent motion = new MouseMotionEvent(1200, 350, 8, 0, 0, 0);
        MouseButtonEvent down = new MouseButtonEvent(MouseInput.BUTTON_LEFT, true, 1200, 350);
        MouseButtonEvent up = new MouseButtonEvent(MouseInput.BUTTON_LEFT, false, 1200, 350);
        down.setTime(10); motion.setTime(20); up.setTime(30);
        emit(() -> {
            inputManager.onMouseMotionEvent(motion);
            inputManager.onMouseButtonEvent(down);
            inputManager.onMouseButtonEvent(up);
        });
        require(cam.getLocation().distance(initial) > 0.1f,
                "Small real GLFW motion-before-button batch still orbits over HUD");
        emit(() -> inputManager.onMouseButtonEvent(new MouseButtonEvent(MouseInput.BUTTON_LEFT, true, 1200, 350)));
        require(inputManager.isCursorVisible(), "Left press preserves cursor");
        emit(() -> inputManager.onMouseMotionEvent(new MouseMotionEvent(1200, 350, 8, 0, 0, 0)));
        require(inputManager.isCursorVisible(), "Left drag keeps cursor visible after threshold");
        resetCamera();
        require(inputManager.isCursorVisible() && cam.getLocation().distance(initial) < 0.001f,
                "R restores camera and releases a held drag");
    }
    private void drag(int button) {
        emit(() -> {
            inputManager.onMouseButtonEvent(new MouseButtonEvent(button, true, 1200, 350));
            inputManager.onMouseMotionEvent(new MouseMotionEvent(1200, 350, 1024, 0, 0, 0));
            inputManager.onMouseButtonEvent(new MouseButtonEvent(button, false, 1200, 350));
        });
    }
    private void resetCamera() {
        emit(() -> {
            inputManager.onKeyEvent(new KeyInputEvent(KeyInput.KEY_R, 'r', true, false));
            inputManager.onKeyEvent(new KeyInputEvent(KeyInput.KEY_R, 'r', false, false));
        });
    }
    private void emit(Runnable events) {
        mouse.next = events;
        inputManager.update(1 / 60f);
    }
    private static final class InjectedMouse extends DummyMouseInput {
        private Runnable next;
        @Override public void update() {
            if (next == null) return;
            Runnable events = next;
            next = null;
            events.run(); // jME accepts queued events only while updating input devices.
        }
    }
    private void requireMinimap(Coordinate cell, int color) {
        Geometry fill = minimapCell(cell);
        require(fill.getMaterial().getParam("Color").getValue().equals(Materials.rgb(color)), "Minimap cell follows shared Board");
    }
    private Geometry minimapCell(Coordinate cell) {
        return (Geometry) minimap.root().getChild("Own cell " + cell.row() + ":" + cell.column());
    }
    private void checkMinimapMarkers() {
        Board fixture = new Board().withPlacement(ShipType.DESTROYER, new Coordinate(0, 0), ShipOrientation.HORIZONTAL);
        minimap.update(fixture.withAttack(new Coordinate(0, 0)).withAttack(new Coordinate(9, 9)));
        requireMinimap(new Coordinate(0, 0), 0x743440);
        requireMinimap(new Coordinate(9, 9), 0x071F38);
        require(((Node) minimap.root().getChild("Own cell marker 9:9")).getQuantity() == 1, "Miss marker visible");
        minimap.update(fixture.withAttack(new Coordinate(0, 0)).withAttack(new Coordinate(0, 1)));
        requireMinimap(new Coordinate(0, 0), 0x4D253A);
        requireMinimap(new Coordinate(0, 1), 0x4D253A);
        minimap.resize(960, 600);
        requireMinimap(new Coordinate(0, 1), 0x4D253A);
        minimap.resize(1440, 900);
        minimap.update(fleet());
    }
    private int visibleShips() { return ((Node) board.getChild("Gameplay fleet")).getQuantity(); }
    private Vector3f castle(String name) { return scene.root().getChild(name).getWorldTranslation().clone(); }
    private Vector2f screen(Coordinate cell) {
        return screen(cell, 0.185f);
    }
    private Vector2f screen(Coordinate cell, float height) {
        rootNode.updateGeometricState();
        Vector3f screen = cam.getScreenCoordinates(coordinates.worldCenter(cell, height));
        return new Vector2f(screen.x, screen.y);
    }
    private BoardPicker.ShipHit beginPlacedDrag(Coordinate cell) {
        Vector2f start = screen(cell, .72f);
        start.set((int) start.x, (int) start.y);
        var picker = new BoardPicker(cam, inputManager, board);
        var hit = picker.pickShip(start, (Node) board.getChild("Gameplay fleet")).orElseThrow();
        require(hit.type() == ShipType.CARRIER, "Press ray hits actual carrier geometry");
        Vector3f beforeCamera = cam.getLocation().clone();
        var beforeRotation = cam.getRotation().clone();
        Board beforeBoard = fleet();
        emit(() -> {
            inputManager.onMouseButtonEvent(new MouseButtonEvent(MouseInput.BUTTON_LEFT, true, (int) start.x, (int) start.y));
            inputManager.onMouseMotionEvent(new MouseMotionEvent((int) start.x + 8, (int) start.y, 8, 0, 0, 0));
        });
        require(game.state().placement().moving() && fleet() == beforeBoard
                && cam.getLocation().distance(beforeCamera) < .001f && cam.getRotation().equals(beforeRotation)
                && inputManager.isCursorVisible(),
                "Ship drag previews without mutating board, orbiting or hiding cursor");
        return hit;
    }
    private void dragPlacedShip(Coordinate cell, Coordinate anchor, boolean rotate) {
        Board before = fleet();
        BoardPicker.ShipHit hit = beginPlacedDrag(cell);
        if (rotate) game.perform(PlacementHud.Action.ROTATE);
        Coordinate offset = game.state().placement().grabOffset();
        Vector2f destination = screen(anchor.offset(offset.row(), offset.column()), hit.height());
        Vector2f start = screen(cell, .72f);
        emit(() -> inputManager.onMouseMotionEvent(new MouseMotionEvent((int) destination.x, (int) destination.y,
                (int) (destination.x - start.x), (int) (destination.y - start.y), 0, 0)));
        require(fleet() == before, "Dragging only commits on release");
        emit(() -> inputManager.onMouseButtonEvent(new MouseButtonEvent(MouseInput.BUTTON_LEFT, false,
                (int) destination.x, (int) destination.y)));
        require(!game.state().placement().moving(), "Drop ends move mode");
        Node model = (Node) board.getChild("CARRIER grid placement");
        Vector3f expected = new Vector3f();
        for (Coordinate occupied : fleet().ship(ShipType.CARRIER).occupiedCells())
            expected.addLocal(coordinates.localCenter(occupied, .235f));
        expected.divideLocal(ShipType.CARRIER.length());
        require(model.getLocalTranslation().distance(expected) < .001f, "Dropped or cancelled model matches board state");
    }
    private void click(Coordinate cell) {
        Vector2f screen = screen(cell);
        clickScreen(screen);
    }
    private void clickScreen(Vector2f screen) {
        emit(() -> {
            inputManager.onMouseButtonEvent(new MouseButtonEvent(MouseInput.BUTTON_LEFT, true, (int) screen.x, (int) screen.y));
            inputManager.onMouseButtonEvent(new MouseButtonEvent(MouseInput.BUTTON_LEFT, false, (int) screen.x, (int) screen.y));
        });
    }
    private void capture(RenderManager renderer, String name) throws Exception {
        var buffer = BufferUtils.createByteBuffer(1440 * 900 * 4);
        renderer.getRenderer().readFrameBufferWithFormat(null, buffer, Image.Format.BGRA8);
        BufferedImage image = new BufferedImage(1440, 900, BufferedImage.TYPE_4BYTE_ABGR);
        Screenshots.convertScreenShot(buffer, image);
        Path directory = Path.of(System.getProperty("battleship.previewDir", "docs"));
        Files.createDirectories(directory);
        ImageIO.write(image, "png", directory.resolve(name).toFile());
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    @Override public void handleError(String message, Throwable error) {
        System.err.println(message);
        if (error != null) error.printStackTrace();
        System.exit(1);
    }
    @Override public void destroy() {
        if (gameInput != null) gameInput.close();
        super.destroy();
    }
}
