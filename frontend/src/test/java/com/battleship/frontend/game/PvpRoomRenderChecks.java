package com.battleship.frontend.game;

import com.battleship.frontend.app.ScreenController;
import com.battleship.frontend.camera.OrbitCamera;
import com.battleship.frontend.effects.SceneEffects;
import com.battleship.frontend.scene.BattleScene;
import com.battleship.frontend.menu.MainMenu;
import com.battleship.frontend.auth.AccountPanel;
import com.battleship.frontend.pvp.PvpRoomPanel;
import com.battleship.shared.room.RoomPlayer;
import com.battleship.shared.room.RoomSnapshot;
import com.battleship.shared.game.GamePhase;
import com.battleship.shared.game.Coordinate;
import com.battleship.shared.game.Board;
import com.battleship.shared.game.Side;
import com.battleship.shared.game.ShipType;
import com.battleship.shared.game.Ship;
import com.battleship.shared.game.ShipOrientation;
import com.battleship.shared.game.AttackResultType;
import com.battleship.shared.game.CellState;
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
    private long finalTurnStarted;
    private int replayShots;
    private long replayRevision = -1;
    private final java.util.EnumSet<AttackResultType> replayResults = java.util.EnumSet.noneOf(AttackResultType.class);

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
                        "-Dorg.lwjgl.system.SharedLibraryExtractPath=" + journal.resolve(role + "-natives"),
                        "-Dbattleship.accountOnly=" + Boolean.getBoolean("battleship.accountOnly"),
                        "-Dbattleship.disconnectOnly=" + Boolean.getBoolean("battleship.disconnectOnly"), "-cp",
                        System.getProperty("java.class.path"), "-Dbattleship.port=" + System.getProperty("battleship.port", "2209"),
                        PvpRoomRenderChecks.class.getName(), role, journal.toString())
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
            System.out.println(Boolean.getBoolean("battleship.disconnectOnly")
                    ? "PASS: two desktop clients, editable server address, opponent departure, private final victory/reason, rematch disabled, menu/login."
                    : Boolean.getBoolean("battleship.accountOnly") ? "PASS: two desktop account flows; registration, login, logout, session navigation and authenticated room entry."
                    : "PASS: two desktop TCP clients; authenticated placement/retry, real timeout, HIT/MISS/SUNK, rematch/reset, fleet destruction, both menus.");
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
        // Native minimized windows may report zero size; synthetic input still uses 1440x900.
        if (cam.getWidth() == 0 || cam.getHeight() == 0) cam.resize(1440, 900, true);
        scene.update(delta, cam);
        screens.update(delta, inputManager.getCursorPosition());
        if (++frames < 20) return;
        require(System.nanoTime() - started < TimeUnit.SECONDS.toNanos(210), "Client " + role + " timed out at stage " + stage);
        try {
            if (stage == 0) {
                require(screens.accountPanel().visible() && !screens.mainMenu().visible(), "Startup is gated by login");
                screens.requestMainMenu();
                screens.update(0, inputManager.getCursorPosition());
                require(!screens.mainMenu().visible(), "Requesting Main Menu cannot bypass login");
                stage = -1;
            } else if (stage < 0) authenticate();
            else if (Boolean.getBoolean("battleship.disconnectOnly")) disconnectBattle();
            else if (Boolean.getBoolean("battleship.accountOnly")) accountNavigation();
            else if (role.equals("A")) stepA();
            else stepB();
        } catch (Exception error) { throw new RuntimeException(error); }
    }

    private String accountName(String playerRole) {
        return "qa_" + journal.getFileName().toString().substring(0, 8) + "_" + playerRole;
    }

    private void type(String value) { for (char character : value.toCharArray()) key(0, character); }

    private void authenticate() {
        AccountPanel panel = screens.accountPanel();
        if (stage == -1 && panel.visible() && screens.pvp().connected()) {
            click(panel.buttonCenter(AccountPanel.Action.SERVER));
            require(panel.editingServer(), "Server address form is reachable before login");
            for (int i = panel.serverHost().length(); i > 0; i--) key(KeyInput.KEY_BACK, '\b');
            type("127.0.0.1"); key(KeyInput.KEY_TAB, '\t');
            for (int i = panel.serverPort().length(); i > 0; i--) key(KeyInput.KEY_BACK, '\b');
            type("0"); click(panel.buttonCenter(AccountPanel.Action.CONNECT));
            require(panel.editingServer() && screens.pvp().status().contains("1 to 65535"), "Invalid TCP port does not reconnect");
            key(KeyInput.KEY_BACK, '\b'); type(System.getProperty("battleship.port", "2209"));
            click(panel.buttonCenter(AccountPanel.Action.CONNECT));
            stage = -4;
        } else if (stage == -4 && screens.pvp().connected()) {
            require(!panel.editingServer(), "Valid endpoint reconnects asynchronously and returns to login");
            click(panel.buttonCenter(AccountPanel.Action.SWITCH));
            type(accountName(role)); key(KeyInput.KEY_TAB, '\t');
            type("RenderCheckPassword2026!"); key(KeyInput.KEY_TAB, '\t');
            type("RenderCheckPassword2026!");
            capture = "account-register-" + role + ".png";
            click(panel.buttonCenter(AccountPanel.Action.SUBMIT));
            stage = -2;
        } else if (stage == -2 && !screens.pvp().pending() && screens.pvp().status().startsWith("Account created")) {
            click(panel.buttonCenter(AccountPanel.Action.SWITCH));
            key(KeyInput.KEY_TAB, '\t'); type("RenderCheckPassword2026!");
            capture = "account-login-" + role + ".png";
            click(panel.buttonCenter(AccountPanel.Action.SUBMIT));
            stage = -3;
        } else if (stage == -3 && screens.pvp().account() != null && screens.mainMenu().visible()) {
            require(screens.pvp().account().displayName().equals(accountName(role)), "Authenticated account controls displayed identity");
            if (!Boolean.getBoolean("battleship.accountOnly")) click(screens.mainMenu().buttonCenter(MainMenu.Action.PVP));
            stage = 1;
        }
    }

    private void disconnectBattle() throws Exception {
        var room = screens.pvp().room();
        switch (stage) {
            case 1 -> {
                if (role.equals("A")) { action(PvpRoomPanel.Action.CREATE_ROOM); stage = 2; }
                else if (Files.exists(journal.resolve("code"))) { join(Files.readString(journal.resolve("code"))); stage = 2; }
            }
            case 2 -> {
                if (role.equals("A") && room != null) Files.writeString(journal.resolve("code"), room.roomCode());
                if (twoPlayers() && !screens.pvp().pending()) { action(PvpRoomPanel.Action.TOGGLE_READY); stage = 3; }
            }
            case 3 -> {
                if (screens.networkPlacement()) {
                    screens.game().perform(PlacementHud.Action.RANDOM);
                    screens.game().perform(PlacementHud.Action.READY); stage = 4;
                }
            }
            case 4 -> {
                if (screens.game().state().phase() == GamePhase.BATTLE) {
                    Files.writeString(journal.resolve(role + "-in-battle"), "ok");
                    if (role.equals("A")) stage = 5;
                    else if (Files.exists(journal.resolve("A-in-battle"))) {
                        screens.pvp().disconnect(); stage = 5;
                    }
                }
            }
            case 5 -> {
                if (role.equals("A") && screens.game().state().phase() == GamePhase.GAME_OVER) {
                    require(screens.pvp().match().endReason() == com.battleship.shared.match.MatchEndReason.PLAYER_LEFT
                            && screens.game().state().winnerId().equals(screens.game().state().player().id())
                            && textShown(guiNode, "VICTORY") && textShown(guiNode, "PLAYER LEFT THE MATCH")
                            && !screens.roomPanel().visible(), "Disconnected opponent yields visible victory/reason on current Battle screen");
                    require(screens.pvp().room().players().size() == 1 && !screens.pvp().rematch().available(), "No rematch with an absent player");
                    capture = "pvp-disconnect-victory.png";
                    Files.writeString(journal.resolve("A-disconnect-victory"), "ok");
                    stage = 50;
                } else if (role.equals("B") && Files.exists(journal.resolve("A-disconnect-victory"))) {
                    require(screens.accountPanel().visible() && !screens.mainMenu().visible(), "Disconnected client returns to mandatory login");
                    System.out.println("PASS client B: explicit disconnect during Battle returns to login."); stop(); stage = 7;
                }
            }
            case 50 -> { if (capture == null) { screens.pvp().returnToMainMenu(); stage = 6; } }
            case 6 -> {
                if (screens.mainMenu().visible()) {
                    System.out.println("PASS client A: opponent disconnect ends Battle, victory/reason, cancelled rematch and Main Menu."); stop(); stage = 7;
                }
            }
            default -> { }
        }
    }

    private void accountNavigation() {
        AccountPanel panel = screens.accountPanel();
        switch (stage) {
            case 1 -> {
                require(screens.mainMenu().visible() && MainMenu.Action.values().length == 9, "Authenticated menu has no Local Game action");
                for (MainMenu.Action action : new MainMenu.Action[] {MainMenu.Action.PVE, MainMenu.Action.FRIENDS, MainMenu.Action.SETTINGS}) {
                    click(screens.mainMenu().buttonCenter(action));
                    require(screens.mainMenu().visible() && screens.pvp().room() == null, "Unimplemented menu action stays inert");
                }
                stage = 90;
            }
            case 90 -> {
                if (screens.mainMenu().visible()) {
                    require(screens.pvp().account() != null, "Main Menu preserves account session");
                    click(screens.mainMenu().buttonCenter(MainMenu.Action.PROFILE)); stage = 91;
                }
            }
            case 91 -> {
                if (panel.visible()) { click(panel.buttonCenter(AccountPanel.Action.LOGOUT)); stage = 92; }
            }
            case 92 -> {
                if (!screens.pvp().pending() && screens.pvp().account() == null) {
                    require(screens.pvp().status().equals("Signed out."), "Logout acknowledged by server");
                    screens.requestMainMenu();
                    screens.update(0, inputManager.getCursorPosition());
                    require(panel.visible() && !screens.mainMenu().visible() && !panel.enabled(AccountPanel.Action.BACK),
                            "Logout returns to mandatory login without a backdoor");
                    stage = 94;
                }
            }
            case 94 -> {
                if (panel.visible() && screens.pvp().connected()) {
                    require(screens.pvp().room() == null, "Logged out player cannot enter a room");
                    key(KeyInput.KEY_TAB, '\t'); type("IncorrectPassword2026!");
                    click(panel.buttonCenter(AccountPanel.Action.SUBMIT)); stage = 940;
                }
            }
            case 940 -> {
                if (!screens.pvp().pending() && screens.pvp().status().startsWith("Incorrect")) {
                    require(!screens.mainMenu().visible() && panel.visible(), "Wrong password keeps the app locked");
                    type("RenderCheckPassword2026!");
                    click(panel.buttonCenter(AccountPanel.Action.SUBMIT)); stage = 95;
                }
            }
            case 95 -> {
                if (screens.mainMenu().visible()) {
                    require(screens.pvp().account() != null && screens.pvp().room() == null, "Login returns to menu without creating a room");
                    click(screens.mainMenu().buttonCenter(MainMenu.Action.CREATE_ROOM)); stage = 950;
                }
            }
            case 950 -> {
                if (screens.pvp().room() != null) { screens.pvp().returnToMainMenu(); stage = 96; }
            }
            case 96 -> {
                if (screens.mainMenu().visible()) {
                    System.out.println("PASS account screens: mandatory login, no Local Game, inert planned actions, Profile logout, wrong-password gate, login again, room entry and Exit.");
                    click(screens.mainMenu().buttonCenter(MainMenu.Action.EXIT)); stage = 97;
                }
            }
            default -> { }
        }
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
                    require(screens.pvp().remainingTurnMillis() > 57_000, "Valid second shot reset turn clock to 60 seconds");
                    finalTurnStarted = System.nanoTime();
                    capture = "pvp-turn-timer.png";
                    stage = 72;
                } else fireWhenOurTurn();
            }
            case 72 -> {
                if (screens.game().state().phase() == GamePhase.GAME_OVER) {
                    require(screens.pvp().match().timedOut() && screens.pvp().match().revision() == 6
                            && screens.game().state().winnerId().equals(screens.game().state().enemy().id()),
                            "Idle Player 1 loses exactly once to server timeout");
                    require(System.nanoTime() - finalTurnStarted >= TimeUnit.SECONDS.toNanos(57), "Real 60-second turn elapsed");
                    require(textShown(guiNode, "DEFEAT"), "Existing defeat overlay is visible");
                    require(screens.pvp().attack(new Coordinate(7, 7)), "Probe server guard after timeout");
                    stage = 73;
                } else {
                    require(screens.pvp().remainingTurnMillis() <= 60_000, "Clock never exceeds limit");
                    if (System.nanoTime() - finalTurnStarted > TimeUnit.SECONDS.toNanos(3))
                        require(!screens.game().attackAnimating(), "Timer events do not replay attack animation");
                }
            }
            case 73 -> {
                if (!screens.pvp().pending() && screens.pvp().status().contains("GAME_FINISHED")) {
                    require(screens.pvp().match().revision() == 6, "Late shot cannot change final result");
                    capture = "pvp-timeout-defeat.png";
                    Files.writeString(journal.resolve("a-timeout-checked"), "ok");
                    stage = 80;
                }
            }
            case 80 -> { click(new Vector2f(618, 386)); stage = 81; }
            case 81 -> {
                if (screens.pvp().rematch() != null && screens.pvp().rematch().acceptedPlayerIds().contains(screens.pvp().clientId())) {
                    require(screens.game().state().phase() == GamePhase.GAME_OVER && textShown(guiNode, "Waiting for opponent..."),
                            "One Play Again keeps the result and displays waiting");
                    Files.writeString(journal.resolve("a-rematch-requested"), "ok"); stage = 82;
                }
            }
            case 82 -> {
                if (screens.pvp().rematch() != null && screens.pvp().rematch().declined()) {
                    require(screens.game().state().winnerId().equals(screens.game().state().enemy().id())
                            && textShown(guiNode, "DEFEAT"), "Decline preserves old outcome");
                    Files.writeString(journal.resolve("a-saw-decline"), "ok");
                    click(new Vector2f(618, 386)); stage = 83;
                }
            }
            case 83 -> {
                if (screens.game().state().phase() == GamePhase.PLACEMENT) {
                    prepareReplay(); stage = 84;
                }
            }
            case 84 -> {
                if (playReplay()) {
                    require(textShown(guiNode, "VICTORY"), "Second round replaces defeat with victory");
                    click(new Vector2f(618, 386)); stage = 85;
                }
            }
            case 85 -> {
                if (screens.pvp().rematch() != null && screens.pvp().rematch().acceptedPlayerIds().contains(screens.pvp().clientId())) {
                    Files.writeString(journal.resolve("a-menu-rematch"), "ok"); stage = 86;
                }
            }
            case 86 -> {
                if (room != null && room.players().size() == 1) {
                    require(screens.game().state().phase() == GamePhase.GAME_OVER && textShown(guiNode, "VICTORY")
                            && !screens.pvp().rematch().available(), "Peer menu cancels rematch and preserves survivor result/room");
                    capture = "pvp-rematch-cancelled.png";
                    // Enter and MAIN MENU may arrive in the same input dispatch, before a server rejection.
                    screens.game().perform(PlacementHud.Action.RESTART);
                    require(screens.pvp().pending(), "Unavailable rematch request awaits server response");
                    screens.game().primaryClick(new Vector2f(821, 386));
                    require(screens.pvp().returningToMenu(), "Main Menu intent is queued behind the pending request");
                    stage = 87;
                }
            }
            case 87 -> {
                require(screens.mainMenu().visible() || screens.pvp().pending() || screens.pvp().returningToMenu(),
                        "Server rejection must not discard the queued Main Menu action");
                if (screens.mainMenu().visible()) {
                    require(room == null && !screens.game().attackAnimating(), "Main Menu clears local match presentation");
                    System.out.println("PASS Client A: rematch waiting/decline, clean second round, victory, peer menu cancellation and own menu.");
                    stop(); stage = 88;
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
                if (Files.exists(journal.resolve("a-saw-both-attacks"))) { stage = 42; }
                else if (Files.exists(journal.resolve("a-battle"))) fireWhenOurTurn();
            }
            case 42 -> {
                if (screens.game().state().phase() == GamePhase.GAME_OVER) {
                    require(screens.pvp().match().timedOut() && screens.pvp().match().revision() == 6
                            && screens.game().state().winnerId().equals(screens.game().state().player().id()),
                            "Opponent receives same timeout and victory");
                    require(textShown(guiNode, "VICTORY"), "Existing victory overlay is visible");
                    capture = "pvp-timeout-victory.png";
                    if (Files.exists(journal.resolve("a-rematch-requested"))) {
                        click(new Vector2f(570, 414)); stage = 43;
                    }
                }
            }
            case 43 -> {
                if (Files.exists(journal.resolve("a-saw-decline")) && screens.pvp().rematch() != null
                        && screens.pvp().rematch().acceptedPlayerIds().size() == 1) {
                    require(textShown(guiNode, "VICTORY"), "Declining does not replace victory");
                    click(new Vector2f(618, 386)); stage = 44;
                }
            }
            case 44 -> {
                if (screens.game().state().phase() == GamePhase.PLACEMENT) {
                    prepareReplay(); stage = 45;
                }
            }
            case 45 -> {
                if (playReplay()) {
                    require(textShown(guiNode, "DEFEAT"), "Second round replaces victory with defeat");
                    stage = 46;
                }
            }
            case 46 -> {
                if (Files.exists(journal.resolve("a-menu-rematch"))) { click(new Vector2f(821, 386)); stage = 47; }
            }
            case 47 -> {
                if (screens.mainMenu().visible()) {
                    require(room == null, "Main Menu leaves only this player from the room");
                    System.out.println("PASS Client B: decline, accept new request, clean second round, defeat, Main Menu.");
                    stop(); stage = 48;
                }
            }
            default -> { }
        }
    }

    private void prepareReplay() {
        var state = screens.game().state();
        require(screens.pvp().room().roomCode().equals(code) && state.player().board().ships().isEmpty()
                && state.player().board().attackedCells().isEmpty() && state.enemy().board().attackedCells().isEmpty()
                && state.winnerId() == null && state.lastAttack() == null && state.turnNumber() == 0
                && screens.pvp().remainingTurnMillis() == 0, "Rematch clears fleet/shots/winner/turn/deadline in same room");
        for (int i = 0; i < ShipType.values().length; i++) {
            screens.game().perform(PlacementHud.Action.values()[i]);
            pointAt(new Coordinate(i * 2, 0));
        }
        require(screens.game().state().canReady(), "Ships can be placed again through existing 3D input");
        screens.game().perform(PlacementHud.Action.READY);
    }

    private boolean playReplay() {
        // Advance only presentation effects; server deadlines still use real monotonic time.
        if (screens.game().attackAnimating()) screens.game().update(3, new Vector2f(-1, -1));
        var state = screens.game().state();
        var snapshot = screens.pvp().match();
        if (snapshot != null && snapshot.lastAttack() != null && snapshot.revision() != replayRevision) {
            replayRevision = snapshot.revision();
            var attack = snapshot.lastAttack();
            replayResults.add(attack.type());
            Board defender = attack.defenderId().equals(state.player().id()) ? state.player().board() : state.enemy().board();
            CellState expected = attack.type() == AttackResultType.MISS ? CellState.MISS
                    : attack.sunk() ? CellState.SUNK : CellState.HIT;
            require(defender.cell(attack.coordinate()).state() == expected, "3D client applies authoritative shot to correct board");
            if (attack.sunk()) require(attack.affectedCells().stream().allMatch(cell -> defender.cell(cell).state() == CellState.SUNK),
                    "All sunk cells agree with the server result");
        }
        if (state.phase() == GamePhase.GAME_OVER) {
            require(replayResults.containsAll(java.util.List.of(AttackResultType.MISS, AttackResultType.HIT,
                    AttackResultType.SUNK, AttackResultType.GAME_OVER)), "Both clients observed MISS/HIT/SUNK/fleet destruction");
            return true;
        }
        if (state.phase() != GamePhase.BATTLE || screens.pvp().pending() || !state.currentPlayerId().equals(state.player().id())) return false;
        if (!screens.game().enemyView()) screens.game().perform(PlacementHud.Action.TOGGLE_BOARD);
        var cells = java.util.stream.IntStream.range(0, ShipType.values().length)
                .mapToObj(i -> new Ship(ShipType.values()[i], new Coordinate(i * 2, 0), ShipOrientation.HORIZONTAL))
                .flatMap(ship -> ship.occupiedCells().stream()).toList();
        pointAt(replayShots == 0 ? new Coordinate(5, 5) : cells.get(replayShots - 1));
        replayShots++;
        require(screens.pvp().pending(), "Replay click sends an authoritative TCP shot");
        return false;
    }

    private void pointAt(Coordinate cell) {
        var screen = cam.getScreenCoordinates(new BoardCoordinates(board).worldCenter(cell, 0.185f));
        screens.game().primaryClick(new Vector2f(screen.x, screen.y));
    }

    private boolean twoPlayers() {
        RoomSnapshot room = screens.pvp().room();
        if (room == null || room.players().size() != 2) return false;
        require(room.players().get(0).playerName().equals(accountName("A"))
                && room.players().get(1).playerName().equals(accountName("B")), "Both clients see the same two player names");
        return true;
    }

    private boolean ready(String role) {
        RoomSnapshot room = screens.pvp().room();
        return room != null && room.players().stream().anyMatch(player -> player.playerName().equals(accountName(role)) && player.ready());
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
        require(match.timer().remainingMillis() > 59_000 && match.timer().remainingMillis() <= 60_000,
                "Both clients receive initial 60-second deadline");
        require(textShown(guiNode, "01:00") && textShown(guiNode, "TURN: " + accountName("A")), "Visible countdown names the current player");
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
