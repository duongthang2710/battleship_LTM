package com.battleship.frontend.game;

import com.battleship.frontend.app.ScreenController;
import com.battleship.frontend.camera.OrbitCamera;
import com.battleship.frontend.effects.SceneEffects;
import com.battleship.frontend.scene.BattleScene;
import com.battleship.frontend.menu.MainMenu;
import com.battleship.frontend.pvp.PvpRoomPanel;
import com.battleship.shared.room.RoomPlayer;
import com.battleship.shared.room.RoomSnapshot;
import com.battleship.shared.game.GamePhase;
import com.battleship.shared.game.Coordinate;
import com.battleship.shared.game.Board;
import com.battleship.shared.game.Side;
import com.jme3.app.FlyCamAppState;
import com.jme3.app.SimpleApplication;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.dummy.DummyKeyInput;
import com.jme3.input.dummy.DummyMouseInput;
import com.jme3.input.event.KeyInputEvent;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.math.Vector2f;
import com.jme3.renderer.RenderManager;
import com.jme3.system.AppSettings;
import com.jme3.texture.Image;
import com.jme3.util.BufferUtils;
import com.jme3.util.Screenshots;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

/** Two real desktop clients, using the production room UI and TCP adapter against a running server. */
public final class PvpRoomRenderChecks extends SimpleApplication {
    private final String role;
    private final Path journal;
    private ScreenController screens;
    private BattleScene scene;
    private InjectedMouse mouse;
    private int stage;
    private int frames;
    private long started;
    private String code;
    private String capture;
    private int captureFrames;
    private com.jme3.scene.Node board;
    private long fleetWaitStarted;

    private PvpRoomRenderChecks(String role, Path journal) { this.role = role; this.journal = journal; }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) { runPair(); return; }
        PvpRoomRenderChecks app = new PvpRoomRenderChecks(args[0], Path.of(args[1]));
        AppSettings settings = new AppSettings(true);
        settings.setTitle("Battleship TCP verification - Client " + args[0]);
        settings.setRenderer(AppSettings.LWJGL_OPENGL32);
        settings.setResolution(1440, 900);
        settings.setSamples(4);
        settings.setGammaCorrection(true);
        settings.setVSync(true);
        settings.setFrameRate(60);
        settings.setAudioRenderer(null);
        settings.setUseInput(false);
        app.setSettings(settings);
        app.setShowSettings(false);
        app.start();
    }

    private static void runPair() throws Exception {
        Path journal = Path.of("build", "pvp-verification", UUID.randomUUID().toString()).toAbsolutePath();
        Files.createDirectories(journal);
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process[] clients = new Process[2];
        try {
            for (int i = 0; i < 2; i++) {
                String role = i == 0 ? "A" : "B";
                clients[i] = new ProcessBuilder(java, "--enable-native-access=ALL-UNNAMED",
                        "-Dorg.lwjgl.system.SharedLibraryExtractPath=" + journal.resolve(role + "-natives"), "-cp",
                        System.getProperty("java.class.path"), PvpRoomRenderChecks.class.getName(), role, journal.toString())
                        .redirectErrorStream(true).redirectOutput(journal.resolve(role + ".log").toFile()).start();
            }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(240);
            while (clients[0].isAlive() || clients[1].isAlive()) {
                for (Process client : clients)
                    if (!client.isAlive() && client.exitValue() != 0) throw new AssertionError("PVP client failed");
                if (System.nanoTime() > deadline) throw new AssertionError("PVP clients timed out");
                Thread.sleep(100);
            }
            require(clients[0].exitValue() == 0 && clients[1].exitValue() == 0, "Both desktop clients completed");
            System.out.println("PASS: two desktop TCP clients; room/Ready, editable placement, rejected fleet/retry, waiting/locked fleet, private START_BATTLE with Player 1 first, two synchronized attacks, disconnect/reconnect.");
        } finally {
            for (Process client : clients) if (client != null && client.isAlive()) client.destroyForcibly();
            for (String role : new String[] {"A", "B"}) {
                Path log = journal.resolve(role + ".log");
                if (Files.exists(log)) System.out.println(Files.readString(log));
            }
        }
    }

    @Override public void simpleInitApp() {
        setPauseOnLostFocus(false);
        setDisplayFps(false);
        setDisplayStatView(false);
        stateManager.detach(stateManager.getState(FlyCamAppState.class));
        mouse = new InjectedMouse();
        DummyKeyInput keyboard = new DummyKeyInput();
        mouse.initialize();
        keyboard.initialize();
        inputManager = new InputManager(mouse, keyboard, null, null);
        scene = new BattleScene(assetManager);
        rootNode.attachChild(scene.root());
        OrbitCamera orbit = new OrbitCamera(cam);
        scene.installOceanReflections(viewPort);
        SceneEffects.install(assetManager, viewPort, scene.keyLight());
        board = scene.beginLocalGameplay();
        screens = new ScreenController(assetManager, guiNode, inputManager, cam, orbit,
                board, "Admiral " + role, this::stop);
        screens.resize(1440, 900);
        started = System.nanoTime();
    }

    @Override public void simpleUpdate(float delta) {
        scene.update(delta, cam);
        screens.update(delta, inputManager.getCursorPosition());
        if (++frames < 20) return;
        require(System.nanoTime() - started < TimeUnit.SECONDS.toNanos(150), "Client " + role + " timed out at stage " + stage);
        try {
            if (stage == 0) {
                click(screens.mainMenu().buttonCenter(MainMenu.Action.PVP));
                stage = 1;
            } else if (role.equals("A")) stepA();
            else stepB();
        } catch (Exception error) { throw new RuntimeException(error); }
    }

    private void stepA() throws Exception {
        RoomSnapshot room = screens.pvp().room();
        switch (stage) {
            case 1 -> { if (screens.roomPanel().enabled(PvpRoomPanel.Action.CREATE_ROOM)) { action(PvpRoomPanel.Action.CREATE_ROOM); stage = 2; } }
            case 2 -> {
                if (room != null) {
                    code = room.roomCode(); Files.writeString(journal.resolve("code"), code);
                    loseFocus(); // Keep receiving while the other desktop client owns focus.
                    stage = 3;
                }
            }
            case 3 -> {
                if (twoPlayers()) { capture = "pvp-a-waiting.png"; stage = 4; }
            }
            case 4 -> { action(PvpRoomPanel.Action.TOGGLE_READY); stage = 5; }
            case 5 -> {
                if (ready("A") && Files.exists(journal.resolve("b-saw-ready"))) {
                    action(PvpRoomPanel.Action.TOGGLE_READY); stage = 6;
                }
            }
            case 6 -> {
                if (!ready("A") && ready("B")) { action(PvpRoomPanel.Action.TOGGLE_READY); stage = 7; }
            }
            case 7 -> {
                if (screens.networkPlacement()) {
                    checkPlacement();
                    screens.game().perform(PlacementHud.Action.READY);
                    stage = 69;
                }
            }
            case 69 -> {
                var match = screens.pvp().match();
                if (match != null && match.fleetSubmitted()) {
                    require(match.phase() == GamePhase.PLACEMENT && !match.opponentSubmitted()
                            && match.enemyBoard().ships().isEmpty(), "First Ready waits without receiving enemy positions");
                    Board before = screens.game().state().player().board();
                    screens.game().perform(PlacementHud.Action.RESET);
                    screens.game().perform(PlacementHud.Action.RANDOM);
                    screens.game().perform(PlacementHud.Action.READY);
                    require(screens.game().state().player().board() == before && !screens.pvp().pending(),
                            "Accepted fleet stays locked; repeated Ready cannot resubmit");
                    require(textShown(guiNode, "Waiting for opponent"), "Waiting state is visible in HUD");
                    capture = "pvp-a-placement.png";
                    Files.writeString(journal.resolve("a-fleet-accepted"), "ok");
                    stage = 70;
                }
            }
            case 70 -> { if (battle()) { Files.writeString(journal.resolve("a-battle"), "ok"); stage = 71; } }
            case 71 -> {
                if (Files.exists(journal.resolve("b-battle")) && Files.exists(journal.resolve("a-fired"))
                        && Files.exists(journal.resolve("b-fired")) && screens.game().state().turnNumber() == 3) {
                    require(screens.pvp().match().revision() == 5, "Both clients processed two server attacks");
                    Files.writeString(journal.resolve("a-saw-both-attacks"), "ok");
                    stage = 8;
                } else fireWhenOurTurn();
            }
            case 8 -> {
                if (room != null && room.players().size() == 1 && !screens.networkPlacement()) {
                    require(!room.players().getFirst().ready() && screens.roomPanel().visible(), "Disconnect resets survivor to waiting/not-ready");
                    Files.writeString(journal.resolve("a-saw-disconnect"), "ok"); stage = 9;
                }
            }
            case 9 -> {
                if (twoPlayers()) { action(PvpRoomPanel.Action.LEAVE_ROOM); stage = 10; }
            }
            case 10 -> {
                if (room == null) { Files.writeString(journal.resolve("a-left"), "ok"); stage = 11; }
            }
            case 11 -> {
                if (Files.exists(journal.resolve("b-left"))) { join(code); stage = 12; }
            }
            case 12 -> {
                if (screens.pvp().status().equals("Room not found.")) {
                    require(screens.pvp().connected() && room == null, "Deleted-room error preserves connection");
                    action(PvpRoomPanel.Action.BACK);
                    action(PvpRoomPanel.Action.DISCONNECT);
                    require(!screens.pvp().connected(), "Disconnect button closes connection");
                    System.out.println("PASS Client A: room membership, Ready/Not Ready, placement, peer disconnect, leave, room deletion.");
                    stop(); stage = 13;
                }
            }
            default -> { }
        }
    }

    private void stepB() throws Exception {
        RoomSnapshot room = screens.pvp().room();
        switch (stage) {
            case 1 -> {
                Path path = journal.resolve("code");
                if (screens.roomPanel().enabled(PvpRoomPanel.Action.SHOW_JOIN)
                        && Files.exists(path) && Files.readString(path).length() == 6) {
                    code = Files.readString(path); join(code); stage = 2;
                }
            }
            case 2 -> {
                if (twoPlayers() && ready("A")) {
                    capture = "pvp-b-waiting.png";
                    Files.writeString(journal.resolve("b-saw-ready"), "ok"); stage = 3;
                }
            }
            case 3 -> { if (room != null && !ready("A")) { action(PvpRoomPanel.Action.TOGGLE_READY); stage = 4; } }
            case 4 -> {
                if (screens.networkPlacement()) { checkPlacement(); stage = 38; }
            }
            case 38 -> {
                if (Files.exists(journal.resolve("a-fleet-accepted")) && screens.pvp().match() != null) {
                    require(screens.game().state().phase() == GamePhase.PLACEMENT, "Other client remains in placement");
                    require(screens.pvp().match() != null && !screens.pvp().match().fleetSubmitted()
                            && screens.pvp().match().enemyBoard().ships().isEmpty(), "Accepted opponent fleet stays private");
                    require(screens.pvp().submitFleet(new Board()), "Send invalid fleet through production TCP adapter");
                    stage = 39;
                }
            }
            case 39 -> {
                if (!screens.pvp().pending() && textShown(guiNode, "Exactly five ships required.")) {
                    require(screens.game().state().phase() == GamePhase.PLACEMENT, "Rejected fleet stays in placement");
                    screens.game().perform(PlacementHud.Action.REMOVE);
                    require(screens.game().state().player().board().ships().size() == 4, "Rejected fleet unlocks editing");
                    screens.game().perform(PlacementHud.Action.RANDOM);
                    fleetWaitStarted = System.nanoTime();
                    stage = 390;
                }
            }
            case 390 -> {
                if (System.nanoTime() - fleetWaitStarted > TimeUnit.MILLISECONDS.toNanos(500)) {
                    require(screens.game().state().phase() == GamePhase.PLACEMENT, "No premature Battle while second player edits");
                    screens.game().perform(PlacementHud.Action.READY);
                    capture = "pvp-b-placement.png";
                    stage = 40;
                }
            }
            case 40 -> { if (battle()) { Files.writeString(journal.resolve("b-battle"), "ok"); stage = 41; } }
            case 41 -> {
                if (Files.exists(journal.resolve("a-saw-both-attacks"))) { stage = 5; }
                else if (Files.exists(journal.resolve("a-battle"))) fireWhenOurTurn();
            }
            case 5 -> { screens.pvp().disconnect(); stage = 6; }
            case 6 -> {
                if (Files.exists(journal.resolve("a-saw-disconnect"))) { action(PvpRoomPanel.Action.CONNECT); stage = 7; }
            }
            case 7 -> { if (screens.roomPanel().enabled(PvpRoomPanel.Action.SHOW_JOIN)) { join(code); stage = 8; } }
            case 8 -> {
                if (room != null && room.players().size() == 1 && Files.exists(journal.resolve("a-left"))) {
                    require(!room.players().getFirst().ready(), "Leave resets readiness");
                    action(PvpRoomPanel.Action.LEAVE_ROOM); stage = 9;
                }
            }
            case 9 -> {
                if (room == null) {
                    Files.writeString(journal.resolve("b-left"), "ok");
                    System.out.println("PASS Client B: typed room code, both players, shared ready state, START_PLACEMENT, reconnect and leave.");
                    stop(); stage = 10;
                }
            }
            default -> { }
        }
    }

    private boolean twoPlayers() {
        RoomSnapshot room = screens.pvp().room();
        if (room == null || room.players().size() != 2) return false;
        require(room.players().get(0).playerName().equals("Admiral A")
                && room.players().get(1).playerName().equals("Admiral B"), "Both clients see the same two player names");
        return true;
    }

    private boolean ready(String role) {
        RoomSnapshot room = screens.pvp().room();
        return room != null && room.players().stream().anyMatch(player -> player.playerName().equals("Admiral " + role) && player.ready());
    }

    private void checkPlacement() {
        require(screens.pvp().room().players().stream().allMatch(RoomPlayer::ready), "Both ready before START_PLACEMENT");
        require(screens.game().state().phase() == GamePhase.PLACEMENT && !screens.roomPanel().visible(), "Server starts real placement scene");
        Side side = screens.pvp().room().players().stream().filter(player -> player.clientId().equals(screens.pvp().clientId()))
                .findFirst().orElseThrow().side();
        require(side == Side.WHITE ? cam.getLocation().x < 0 : cam.getLocation().x > 0,
                "Camera faces opponent castle from assigned side");
        screens.game().perform(PlacementHud.Action.RANDOM);
        require(screens.game().state().canReady(), "Existing random placement produces a complete fleet");
    }

    private boolean battle() {
        if (screens.game().state().phase() != GamePhase.BATTLE) return false;
        var match = screens.pvp().match();
        require(match.enemyBoard().ships().isEmpty(), "No enemy placement leaked");
        String firstId = screens.pvp().room().players().getFirst().clientId();
        require(match.currentPlayerId().equals(firstId) && match.turnNumber() == 1, "Player 1 starts on both clients");
        return true;
    }

    private static boolean textShown(com.jme3.scene.Node node, String value) {
        for (var child : node.getChildren()) {
            if (child instanceof com.jme3.font.BitmapText text && text.getText().contains(value)) return true;
            if (child instanceof com.jme3.scene.Node nested && textShown(nested, value)) return true;
        }
        return false;
    }

    private void fireWhenOurTurn() throws Exception {
        if (!Files.exists(journal.resolve("a-battle")) || !Files.exists(journal.resolve("b-battle"))) return;
        var state = screens.game().state();
        if (Files.exists(journal.resolve(role.toLowerCase() + "-fired")) || screens.game().attackAnimating()
                || screens.pvp().pending() || !state.currentPlayerId().equals(state.player().id())) return;
        if (!screens.game().enemyView()) screens.game().perform(PlacementHud.Action.TOGGLE_BOARD);
        var screen = cam.getScreenCoordinates(new BoardCoordinates(board).worldCenter(new Coordinate(4, 4), 0.185f));
        screens.game().primaryClick(new Vector2f(screen.x, screen.y));
        require(screens.pvp().pending(), "3D click sends TCP attack");
        Files.writeString(journal.resolve(role.toLowerCase() + "-fired"), "ok");
    }

    private void join(String code) {
        action(PvpRoomPanel.Action.SHOW_JOIN);
        for (char character : code.toCharArray()) key(character == 'R' ? KeyInput.KEY_R : 0, character);
        require(screens.roomPanel().roomCode().equals(code), "Room code typed through RoomCodeInput");
        key(KeyInput.KEY_RETURN, '\n');
    }

    private void action(PvpRoomPanel.Action action) {
        require(screens.roomPanel().enabled(action), "Enabled room action " + action + " at stage " + stage);
        click(screens.roomPanel().buttonCenter(action));
    }

    private void click(Vector2f cursor) {
        mouse.next = () -> {
            inputManager.onMouseButtonEvent(new MouseButtonEvent(MouseInput.BUTTON_LEFT, true, (int) cursor.x, (int) cursor.y));
            inputManager.onMouseButtonEvent(new MouseButtonEvent(MouseInput.BUTTON_LEFT, false, (int) cursor.x, (int) cursor.y));
        };
        inputManager.update(1 / 60f);
        screens.update(0, cursor);
    }

    private void key(int code, char character) {
        mouse.next = () -> {
            inputManager.onKeyEvent(new KeyInputEvent(code, character, true, false));
            inputManager.onKeyEvent(new KeyInputEvent(code, character, false, false));
        };
        inputManager.update(1 / 60f);
        screens.update(0, inputManager.getCursorPosition());
    }

    @Override public void simpleRender(RenderManager renderer) {
        if (capture == null) return;
        if (++captureFrames < 3) return;
        try {
            var buffer = BufferUtils.createByteBuffer(1440 * 900 * 4);
            renderer.getRenderer().readFrameBufferWithFormat(null, buffer, Image.Format.BGRA8);
            BufferedImage image = new BufferedImage(1440, 900, BufferedImage.TYPE_4BYTE_ABGR);
            Screenshots.convertScreenShot(buffer, image);
            Path directory = Path.of("docs");
            Files.createDirectories(directory);
            ImageIO.write(image, "png", directory.resolve(capture).toFile());
            capture = null;
            captureFrames = 0;
        } catch (Exception error) { throw new RuntimeException(error); }
    }

    private static final class InjectedMouse extends DummyMouseInput {
        private Runnable next;
        @Override public void update() {
            if (next == null) return;
            Runnable events = next; next = null; events.run();
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    @Override public void handleError(String message, Throwable error) {
        System.err.println(message);
        if (error != null) error.printStackTrace();
        System.exit(1);
    }

    @Override public void destroy() {
        if (screens != null) screens.close();
        super.destroy();
    }
}
