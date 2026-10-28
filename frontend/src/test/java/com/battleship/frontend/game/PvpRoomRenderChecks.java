package com.battleship.frontend.game;

import com.battleship.frontend.app.ScreenController;
import com.battleship.frontend.camera.OrbitCamera;
import com.battleship.frontend.settings.*;
import com.battleship.frontend.effects.SceneEffects;
import com.battleship.frontend.scene.BattleScene;
import com.battleship.frontend.menu.MainMenu;
import com.battleship.frontend.auth.AccountPanel;
import com.battleship.frontend.leaderboard.LeaderboardPanel;
import com.battleship.frontend.friends.FriendsPanel;
import com.battleship.shared.friends.FriendsAction;
import com.battleship.shared.leaderboard.LeaderboardSort;
import com.battleship.frontend.pvp.PvpRoomPanel;
import com.battleship.frontend.pvp.GameInvitePanel;
import com.battleship.frontend.chat.ChatPanel;
import com.battleship.shared.chat.ChatScope;
import com.jme3.input.event.MouseMotionEvent;
import com.battleship.shared.room.GameInviteStatus;
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
    private DisplaySettings display;
    private OrbitCamera orbit;
    private int uiWidth = 1440, uiHeight = 900;
    private String settingsClientId;
    private int stage;
    private int frames;
    private long started;
    private String code;
    private String capture;
    private int captureFrames;
    private com.jme3.scene.Node board;
    private UUID chatContextId;
    private String chatAnchor;
    private boolean chatBoardView;
    private long chatRevision, chatRemaining, chatFirstBattleAt;
    private long inviteCountdownStarted;
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
        var preferences = Boolean.getBoolean("battleship.settingsOnly") ? new SettingsStore().load().preferences() : ClientPreferences.defaults();
        settings.setResolution(preferences.width(), preferences.height());
        settings.setMinResolution(960, 600);
        settings.setResizable(true);
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
                        "-Dbattleship.settingsOnly=" + Boolean.getBoolean("battleship.settingsOnly"),
                        "-Dbattleship.settingsFile=" + journal.resolve(role + "-settings.properties"),
                        "-Dbattleship.accountOnly=" + Boolean.getBoolean("battleship.accountOnly"),
                        "-Dbattleship.disconnectOnly=" + Boolean.getBoolean("battleship.disconnectOnly"),
                        "-Dbattleship.leaderboardOnly=" + Boolean.getBoolean("battleship.leaderboardOnly"),
                        "-Dbattleship.friendsOnly=" + Boolean.getBoolean("battleship.friendsOnly"),
                        "-Dbattleship.gameInviteOnly=" + Boolean.getBoolean("battleship.gameInviteOnly"),
                        "-Dbattleship.chatOnly=" + Boolean.getBoolean("battleship.chatOnly"), "-cp",
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
            if (Boolean.getBoolean("battleship.settingsOnly")) {
                clients[0] = new ProcessBuilder(java, "--enable-native-access=ALL-UNNAMED",
                        "-Dorg.lwjgl.system.SharedLibraryExtractPath=" + journal.resolve("reload-natives"),
                        "-Dbattleship.settingsOnly=true", "-Dbattleship.settingsReload=true",
                        "-Dbattleship.settingsFile=" + journal.resolve("A-settings.properties"),
                        "-Dbattleship.port=" + System.getProperty("battleship.port", "2209"),
                        "-cp", System.getProperty("java.class.path"), PvpRoomRenderChecks.class.getName(), "A", journal.toString())
                        .redirectErrorStream(true).redirectOutput(journal.resolve("reload.log").toFile()).start();
                require(clients[0].waitFor(120, TimeUnit.SECONDS) && clients[0].exitValue() == 0, "Saved settings survive an actual desktop restart");
                System.out.println(Files.readString(journal.resolve("reload.log")));
            }
            System.out.println(Boolean.getBoolean("battleship.settingsOnly")
                    ? "PASS: desktop Settings, 960x600/1280x720, borderless fullscreen, save/reset/discard, live TCP session and application restart with restored preferences."
                    : Boolean.getBoolean("battleship.chatOnly")
                    ? "PASS: two desktop chat clients; login/friend invitation, waiting messages, safe scrolling, Battle input isolation and timer, read-only game over, rematch chat reset and Main Menu."
                    : Boolean.getBoolean("battleship.gameInviteOnly")
                    ? "PASS: two desktop invite clients; global decline, real 60-second expiry, reused room, accept/navigation, Ready/placement, TCP Battle shots and menus."
                    : Boolean.getBoolean("battleship.friendsOnly")
                    ? "PASS: two desktop Friends clients; search, TCP invitation push, accept, online lists, confirmation/cancel/remove, empty state and menu/Exit."
                    : Boolean.getBoolean("battleship.leaderboardOnly")
                    ? "PASS: two desktop leaderboard clients; real TCP rows, loading, three sorts, page navigation, empty/eligibility, error/retry and stale responses after Back."
                    : Boolean.getBoolean("battleship.disconnectOnly")
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
        orbit = new OrbitCamera(cam);
        scene.installOceanReflections(viewPort);
        SceneEffects.install(assetManager, viewPort, scene.keyLight());
        board = scene.beginLocalGameplay();
        screens = new ScreenController(assetManager, guiNode, inputManager, cam, orbit,
                board, "Admiral " + role, this::stop, Boolean.getBoolean("battleship.settingsOnly") ? (display = new DisplaySettings(this)) : null);
        screens.resize(1440, 900);
        started = System.nanoTime();
    }

    @Override public void simpleUpdate(float delta) {
        // Native minimized windows may report zero size; synthetic input still uses 1440x900.
        if (cam.getWidth() == 0 || cam.getHeight() == 0) cam.resize(1440, 900, true);
        if (Boolean.getBoolean("battleship.settingsOnly") && (cam.getWidth() != uiWidth || cam.getHeight() != uiHeight)) {
            uiWidth = cam.getWidth(); uiHeight = cam.getHeight(); screens.resize(uiWidth, uiHeight); orbit.update();
        }
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
            else if (Boolean.getBoolean("battleship.settingsOnly")) settingsScreens();
            else if (Boolean.getBoolean("battleship.friendsOnly")) friendsScreens();
            else if (Boolean.getBoolean("battleship.leaderboardOnly")) leaderboardScreens();
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
            if (Boolean.getBoolean("battleship.settingsReload")) {
                type(accountName(role)); key(KeyInput.KEY_TAB, '\t'); type("RenderCheckPassword2026!");
                click(panel.buttonCenter(AccountPanel.Action.SUBMIT)); stage = -3; return;
            }
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
            if (!Boolean.getBoolean("battleship.settingsOnly") && !Boolean.getBoolean("battleship.accountOnly") && !Boolean.getBoolean("battleship.leaderboardOnly") && !Boolean.getBoolean("battleship.friendsOnly")) click(screens.mainMenu().buttonCenter(MainMenu.Action.PVP));
            stage = 1;
        }
    }

    private void friendAction(FriendsPanel.Action action) {
        var panel = screens.friendsPanel();
        require(panel.enabled(action), "Friends action enabled: " + action);
        click(panel.buttonCenter(action));
    }
    private void friendsScreens() throws Exception {
        if (Boolean.getBoolean("battleship.chatOnly") && stage >= 200) { chatScreens(); return; }
        if (Boolean.getBoolean("battleship.chatOnly") && stage == 6) {
            if (role.equals("B")) { friendAction(FriendsPanel.Action.BACK); stage = 200; }
            else if (Files.exists(journal.resolve("friends-listed-B")) && capture == null) stage = 200;
            return;
        }
        if (Boolean.getBoolean("battleship.gameInviteOnly") && stage >= 100) { gameInviteScreens(); return; }
        if (Boolean.getBoolean("battleship.gameInviteOnly") && stage == 6) {
            if (role.equals("B")) { friendAction(FriendsPanel.Action.BACK); stage = 100; }
            else if (Files.exists(journal.resolve("friends-listed-B")) && capture == null) stage = 100;
            return;
        }
        var panel = screens.friendsPanel();
        var data = panel.data();
        if (stage == 1) {
            click(screens.mainMenu().buttonCenter(MainMenu.Action.FRIENDS));
            require(panel.visible() && panel.loading(), "Friends opens with a loading state");
            stage = 2; return;
        }
        if (stage == 2 && data != null) {
            require(data.entries().isEmpty(), "New account has no invented friends");
            Files.writeString(journal.resolve("friends-ready-" + role), "ok");
            if (!Files.exists(journal.resolve("friends-ready-" + (role.equals("A") ? "B" : "A")))) return;
            if (role.equals("A")) {
                click(panel.searchCenter()); type(accountName("B")); key(KeyInput.KEY_RETURN, '\n');
            } else friendAction(FriendsPanel.Action.RECEIVED);
            stage = 3; return;
        }
        if (role.equals("A")) {
            switch (stage) {
                case 3 -> {
                    if (data != null && data.action() == FriendsAction.SEARCH && !data.entries().isEmpty()) {
                        require(data.total() == 1 && data.entries().getFirst().username().equals(accountName("B").toLowerCase()), "Typed username maps to the correct account");
                        friendAction(FriendsPanel.Action.SEND); stage = 4;
                    }
                }
                case 4 -> {
                    if (data != null && data.entries().getFirst().relationship().equals("FRIEND")) {
                        friendAction(FriendsPanel.Action.FRIENDS); stage = 5;
                    }
                }
                case 5 -> {
                    if (data != null && data.action() == FriendsAction.FRIENDS && data.entries().size() == 1) {
                        require(data.entries().getFirst().online(), "Friend is online on client A");
                        capture = "friends-A.png"; Files.writeString(journal.resolve("friends-listed-A"), "ok"); stage = 6;
                    }
                }
                case 6 -> {
                    if (Files.exists(journal.resolve("friends-listed-B")) && capture == null) {
                        friendAction(FriendsPanel.Action.REMOVE);
                        require(panel.confirming() && panel.data().entries().size() == 1, "First click only asks for confirmation");
                        friendAction(FriendsPanel.Action.CANCEL_REMOVE);
                        require(!panel.confirming() && panel.data().entries().size() == 1, "Cancelling preserves the friendship");
                        friendAction(FriendsPanel.Action.REMOVE); friendAction(FriendsPanel.Action.CONFIRM_REMOVE); stage = 7;
                    }
                }
                case 7 -> {
                    if (data != null && data.entries().isEmpty() && Files.exists(journal.resolve("friends-removed-B"))) {
                        panel.setError("Friends service unavailable. Please retry.");
                        require(panel.status().contains("unavailable") && panel.enabled(FriendsPanel.Action.REFRESH), "Error component supports retry");
                        friendAction(FriendsPanel.Action.REFRESH); stage = 8;
                    }
                }
                case 8 -> { if (data != null && data.entries().isEmpty()) { friendAction(FriendsPanel.Action.BACK); stage = 9; } }
                case 9 -> { if (screens.mainMenu().visible()) { click(screens.mainMenu().buttonCenter(MainMenu.Action.EXIT)); stage = 10; } }
                default -> { }
            }
        } else {
            switch (stage) {
                case 3 -> {
                    if (data != null && data.action() == FriendsAction.RECEIVED && data.entries().size() == 1) {
                        require(data.entries().getFirst().requestedAt() != null, "Incoming invitation includes time");
                        friendAction(FriendsPanel.Action.ACCEPT); stage = 4;
                    }
                }
                case 4 -> { if (data != null && data.entries().isEmpty()) { friendAction(FriendsPanel.Action.FRIENDS); stage = 5; } }
                case 5 -> {
                    if (data != null && data.action() == FriendsAction.FRIENDS && data.entries().size() == 1) {
                        require(data.entries().getFirst().online(), "Friend is online on client B");
                        capture = "friends-B.png"; Files.writeString(journal.resolve("friends-listed-B"), "ok"); stage = 6;
                    }
                }
                case 6 -> {
                    if (data != null && data.entries().isEmpty()) {
                        require(!panel.loading(), "Server push refreshes both clients after removal");
                        Files.writeString(journal.resolve("friends-removed-B"), "ok"); friendAction(FriendsPanel.Action.BACK); stage = 7;
                    }
                }
                case 7 -> { if (screens.mainMenu().visible()) { click(screens.mainMenu().buttonCenter(MainMenu.Action.EXIT)); stage = 8; } }
                default -> { }
            }
        }
    }

    private void inviteAction(GameInvitePanel.Action action) { click(screens.gameInvitePanel().buttonCenter(action)); }
    private void gameInviteScreens() throws Exception {
        var invite = screens.gameInvites().current();
        var panel = screens.gameInvitePanel();
        var friends = screens.friendsPanel();
        var room = screens.pvp().room();
        if (role.equals("A")) {
            switch (stage) {
                case 100 -> {
                    if (Files.exists(journal.resolve("invite-menu-B"))) { friendAction(FriendsPanel.Action.INVITE); stage = 101; }
                }
                case 101 -> {
                    if (invite != null && invite.status() == GameInviteStatus.DECLINED && Files.exists(journal.resolve("invite-declined-B"))) {
                        require(room != null && room.players().size() == 1 && screens.roomPanel().visible(), "Sender auto-creates room and opens Waiting Room");
                        code = room.roomCode(); inviteAction(GameInvitePanel.Action.DISMISS); action(PvpRoomPanel.Action.INVITE_FRIENDS); stage = 102;
                    }
                }
                case 102 -> {
                    if (friends.visible() && friends.data() != null && !friends.loading()) { friendAction(FriendsPanel.Action.INVITE); stage = 103; }
                }
                case 103 -> {
                    if (invite != null && invite.status() == GameInviteStatus.PENDING) {
                        require(invite.roomCode().equals(code) && panel.seconds() > 0 && panel.seconds() <= 60, "Invitation reuses room and shows countdown");
                        inviteCountdownStarted = System.nanoTime(); capture = "game-invite-sent-A.png"; stage = 104;
                    }
                }
                case 104 -> {
                    if (invite != null && invite.status() == GameInviteStatus.EXPIRED) {
                        require(System.nanoTime() - inviteCountdownStarted >= TimeUnit.SECONDS.toNanos(59) && panel.seconds() == 0,
                                "Server expires the invitation after a real 60-second wait on sender");
                        require(room.players().size() == 1, "Expiry never joins the recipient");
                        inviteAction(GameInvitePanel.Action.DISMISS); action(PvpRoomPanel.Action.INVITE_FRIENDS); stage = 105;
                    }
                }
                case 105 -> {
                    if (friends.visible() && friends.data() != null && !friends.loading() && Files.exists(journal.resolve("invite-expired-B"))) {
                        friendAction(FriendsPanel.Action.INVITE); stage = 106;
                    }
                }
                default -> { }
            }
        } else {
            switch (stage) {
                case 100 -> { if (screens.mainMenu().visible()) { Files.writeString(journal.resolve("invite-menu-B"), "ok"); stage = 101; } }
                case 101 -> {
                    if (invite != null && invite.status() == GameInviteStatus.PENDING && panel.visible()) {
                        require(screens.mainMenu().visible() && screens.pvp().room() == null, "Recipient receives invitation globally without leaving Main Menu");
                        require(invite.inviter().displayName().equals(accountName("A")), "Invitation identifies the authenticated sender");
                        capture = "game-invite-received-B.png"; inviteAction(GameInvitePanel.Action.DECLINE); stage = 102;
                    }
                }
                case 102 -> {
                    if (invite != null && invite.status() == GameInviteStatus.DECLINED) {
                        inviteAction(GameInvitePanel.Action.DISMISS); Files.writeString(journal.resolve("invite-declined-B"), "ok"); stage = 103;
                    }
                }
                case 103 -> {
                    if (invite != null && invite.status() == GameInviteStatus.PENDING) {
                        require(panel.seconds() > 0 && panel.seconds() <= 60, "Recipient countdown starts from server duration");
                        inviteCountdownStarted = System.nanoTime(); stage = 104;
                    }
                }
                case 104 -> {
                    if (invite != null && invite.status() == GameInviteStatus.EXPIRED) {
                        require(System.nanoTime() - inviteCountdownStarted >= TimeUnit.SECONDS.toNanos(59) && panel.seconds() == 0,
                                "Recipient receives real server expiry, not a local winner/timeout decision");
                        require(screens.pvp().room() == null && screens.mainMenu().visible(), "Expired invitation leaves recipient outside the room");
                        capture = "game-invite-expired-B.png"; inviteAction(GameInvitePanel.Action.DISMISS);
                        Files.writeString(journal.resolve("invite-expired-B"), "ok"); stage = 105;
                    }
                }
                case 105 -> {
                    if (invite != null && invite.status() == GameInviteStatus.PENDING) { inviteAction(GameInvitePanel.Action.ACCEPT); stage = 106; }
                }
                default -> { }
            }
        }
        if (stage == 106 && invite != null && invite.status() == GameInviteStatus.ACCEPTED && room != null && room.players().size() == 2) {
            require(screens.roomPanel().visible() && room.roomCode().equals(invite.roomCode()), "Accepted invitation opens the correct room on " + role);
            require(room.players().getFirst().playerName().equals(accountName("A")) && room.players().get(1).playerName().equals(accountName("B")), "Both room names synchronized");
            capture = "game-invite-room-" + role + ".png"; inviteAction(GameInvitePanel.Action.DISMISS);
            action(PvpRoomPanel.Action.TOGGLE_READY); stage = 107;
        } else if (stage == 107 && screens.networkPlacement()) {
            checkPlacement(); screens.game().perform(PlacementHud.Action.READY); stage = 108;
        } else if (stage == 108 && screens.game().state().phase() == GamePhase.BATTLE) {
            Files.writeString(journal.resolve(role.toLowerCase() + "-battle"), "ok"); stage = 109;
        } else if (stage == 109) {
            fireWhenOurTurn();
            if (screens.pvp().match() != null && screens.pvp().match().turnNumber() >= 3 && !screens.pvp().pending()) {
                screens.pvp().returnToMainMenu(); stage = 110;
            }
        } else if (stage == 110 && screens.mainMenu().visible()) {
            click(screens.mainMenu().buttonCenter(MainMenu.Action.EXIT)); stage = 111;
        }
    }

    private void chatSend(String text, boolean sendButton) {
        var panel = screens.chatPanel();
        require(panel.visible() && panel.writable(), "Chat is visible and writable");
        if (panel.collapsed()) click(panel.buttonCenter(ChatPanel.Action.TOGGLE));
        click(panel.inputCenter()); require(panel.focused(), "Chat input receives focus");
        for (char c : text.toCharArray()) key(switch (c) {
            case 'F' -> KeyInput.KEY_F; case 'C' -> KeyInput.KEY_C; case 'T' -> KeyInput.KEY_T; case 'R' -> KeyInput.KEY_R; default -> 0;
        }, c);
        if (text.startsWith("FCTR")) key(KeyInput.KEY_TAB, '\t');
        if (sendButton) click(panel.buttonCenter(ChatPanel.Action.SEND)); else key(KeyInput.KEY_RETURN, '\n');
    }
    private void chatWheel(int delta) {
        var point = screens.chatPanel().listCenter();
        mouse.next = () -> inputManager.onMouseMotionEvent(new MouseMotionEvent((int) point.x, (int) point.y, 0, 0, delta, delta));
        inputManager.update(1 / 60f); screens.update(0, point);
    }
    private void chatScreens() throws Exception {
        var panel = screens.chatPanel(); var context = screens.chat().context();
        var invite = screens.gameInvites().current();
        switch (stage) {
            case 200 -> {
                if (role.equals("A") && Files.exists(journal.resolve("chat-menu-B"))) { friendAction(FriendsPanel.Action.INVITE); stage = 201; }
                else if (role.equals("B") && screens.mainMenu().visible()) { Files.writeString(journal.resolve("chat-menu-B"), "ok"); stage = 201; }
            }
            case 201 -> {
                if (role.equals("B") && invite != null && invite.status() == GameInviteStatus.PENDING && !screens.gameInvites().busy())
                    inviteAction(GameInvitePanel.Action.ACCEPT);
                if (invite != null && invite.status() == GameInviteStatus.ACCEPTED && screens.roomPanel().visible()
                        && context != null && context.scope() == ChatScope.WAITING_ROOM && context.canSend()) {
                    code = screens.pvp().room().roomCode(); chatContextId = context.contextId();
                    inviteAction(GameInvitePanel.Action.DISMISS); chatSend("waiting-" + role, role.equals("A")); stage = 202;
                }
            }
            case 202 -> {
                if (panel.messages().size() >= 2 && !panel.busy()) {
                    require(panel.messages().stream().map(m -> m.sender().username()).distinct().count() == 2, "Both authenticated names appear in waiting chat");
                    chatSend("Long message " + role + " " + "ocean ".repeat(47), false); stage = 203;
                }
            }
            case 203 -> { if (!panel.busy()) { chatSend("Another long message " + role + " " + "fleet ".repeat(45), false); stage = 204; } }
            case 204 -> {
                if (panel.messages().size() >= 6 && !panel.busy()) {
                    Files.writeString(journal.resolve("chat-waiting-" + role), "ok"); capture = "chat-waiting-" + role + ".png"; stage = 205;
                }
            }
            case 205 -> {
                if (role.equals("A") && Files.exists(journal.resolve("chat-waiting-B"))) {
                    var cameraBefore = cam.getLocation().clone(); chatWheel(120);
                    require(!panel.atBottom() && cameraBefore.equals(cam.getLocation()), "Scrolling older chat does not zoom camera");
                    chatAnchor = panel.firstVisibleText(); Files.writeString(journal.resolve("chat-scrolled-A"), "ok"); stage = 206;
                } else if (role.equals("B") && Files.exists(journal.resolve("chat-scrolled-A"))) {
                    chatSend("new-while-reading", false); stage = 206;
                }
            }
            case 206 -> {
                if (role.equals("A") && panel.messages().size() >= 7) {
                    require(chatAnchor.equals(panel.firstVisibleText()) && !panel.atBottom(), "New message preserves reader's scroll anchor");
                    click(panel.buttonCenter(ChatPanel.Action.LATEST)); require(panel.atBottom(), "Latest resumes following messages");
                    click(panel.inputCenter()); key(KeyInput.KEY_RETURN, '\n');
                    require(panel.status().contains("Enter a message"), "Empty input shows a clear error without sending");
                    Files.writeString(journal.resolve("chat-scroll-ok"), "ok"); action(PvpRoomPanel.Action.TOGGLE_READY); stage = 207;
                } else if (role.equals("B") && Files.exists(journal.resolve("chat-scroll-ok"))) { action(PvpRoomPanel.Action.TOGGLE_READY); stage = 207; }
            }
            case 207 -> {
                if (screens.networkPlacement() && screens.game().state().phase() == GamePhase.PLACEMENT) {
                    require(context != null && !context.contextId().equals(chatContextId) && !panel.visible() && panel.messages().isEmpty(), "Match starts a fresh chat context; placement input stays available");
                    chatContextId = context.contextId(); prepareReplay(); stage = 208;
                }
            }
            case 208 -> {
                if (screens.game().state().phase() == GamePhase.BATTLE && context != null && context.canSend()) {
                    require(panel.collapsed() && panel.messages().stream().allMatch(m -> m.contextId().equals(context.contextId()) && m.text().startsWith("FCTR")), "Battle chat starts collapsed with only live messages from this match");
                    chatBoardView = screens.game().enemyView(); chatRevision = screens.pvp().match().revision(); chatRemaining = screens.pvp().remainingTurnMillis(); chatFirstBattleAt = System.nanoTime();
                    var cameraBefore = cam.getRotation().clone(); chatSend("FCTR [] <tag> \\#ff0000# " + role, false);
                    require(chatBoardView == screens.game().enemyView() && cameraBefore.equals(cam.getRotation()), "F/C/T/R/Tab while typing never reach gameplay or camera");
                    stage = 209;
                }
            }
            case 209 -> {
                if (panel.messages().size() >= 2 && !panel.busy()) {
                    require(screens.pvp().match().revision() == chatRevision && screens.pvp().match().turnNumber() == 1
                            && screens.pvp().remainingTurnMillis() <= chatRemaining + 150, "Battle chat preserves shots, turn and countdown");
                    require(textShown(guiNode, "\\ #ff0000#"), "Color-tag input is rendered as safe literal text");
                    for (int row = 0; row < 10; row++) for (int column = 0; column < 10; column++) {
                        var projected = cam.getScreenCoordinates(new BoardCoordinates(board).worldCenter(new Coordinate(row, column), .185f));
                        require(!panel.contains(new Vector2f(projected.x, projected.y)), "Expanded chat does not cover a target cell at the player view");
                    }
                    capture = "chat-battle-" + role + ".png";
                    click(panel.buttonCenter(ChatPanel.Action.TOGGLE)); stage = 210;
                }
            }
            case 210 -> {
                if (playReplay()) {
                    require(context != null && !context.canSend() && panel.messages().size() == 2, "Finished match keeps displayed chat but disables sending");
                    click(panel.buttonCenter(ChatPanel.Action.TOGGLE)); capture = "chat-game-over-" + role + ".png"; stage = 211;
                }
            }
            case 211 -> { if (capture == null) { key(KeyInput.KEY_RETURN, '\n'); stage = 212; } }
            case 212 -> {
                if (screens.game().state().phase() == GamePhase.PLACEMENT) {
                    require(context != null && !context.contextId().equals(chatContextId) && panel.messages().isEmpty() && panel.draft().isEmpty(), "Rematch discards old messages and input state");
                    chatContextId = context.contextId(); prepareReplay(); stage = 213;
                }
            }
            case 213 -> { if (screens.game().state().phase() == GamePhase.BATTLE && context != null && context.canSend() && System.nanoTime() - chatFirstBattleAt >= TimeUnit.SECONDS.toNanos(5)) { chatSend("round-two-" + role, false); stage = 214; } }
            case 214 -> {
                if (panel.messages().size() >= 2 && !panel.busy()) {
                    require(panel.messages().stream().allMatch(m -> m.contextId().equals(chatContextId) && m.text().startsWith("round-two")), "Only fresh messages in the second match");
                    screens.pvp().returnToMainMenu(); stage = 215;
                }
            }
            case 215 -> {
                if (screens.mainMenu().visible()) {
                    require(!panel.visible() && panel.messages().isEmpty() && screens.chat().context() == null, "Leaving room clears chat");
                    click(screens.mainMenu().buttonCenter(MainMenu.Action.EXIT)); stage = 216;
                }
            }
            default -> { }
        }
    }

    private void leaderboardAction(LeaderboardPanel.Action action) {
        var panel = screens.leaderboardPanel();
        require(panel.enabled(action), "Leaderboard action enabled: " + action);
        click(panel.buttonCenter(action));
    }

    private void leaderboardScreens() {
        var panel = screens.leaderboardPanel();
        var data = panel.data();
        switch (stage) {
            case 1 -> {
                click(screens.mainMenu().buttonCenter(MainMenu.Action.LEADERBOARD));
                require(panel.visible() && panel.loading() && !screens.mainMenu().visible(), "Menu opens real loading state"); stage = 2;
            }
            case 2 -> {
                if (data != null) {
                    require(data.sort() == LeaderboardSort.WINS && data.page() == 0 && data.entries().size() <= 10, "Default bounded wins ranking");
                    require(data.entries().stream().allMatch(e -> e.matches() == e.wins() + e.losses()
                            && Math.abs(e.winRate() - 100.0 * e.wins() / e.matches()) < .001), "Rendered metrics are internally consistent");
                    capture = "leaderboard-wins-" + role + ".png"; stage = 20;
                }
            }
            case 20 -> {
                if (capture == null) {
                    if (panel.enabled(LeaderboardPanel.Action.NEXT)) { leaderboardAction(LeaderboardPanel.Action.NEXT); stage = 3; }
                    else { leaderboardAction(LeaderboardPanel.Action.MATCHES); stage = 5; }
                }
            }
            case 3 -> { if (data != null && data.page() == 1) { require(data.entries().getFirst().rank() == 11, "Global ranks continue on page two"); leaderboardAction(LeaderboardPanel.Action.PREVIOUS); stage = 4; } }
            case 4 -> { if (data != null && data.page() == 0) { leaderboardAction(LeaderboardPanel.Action.MATCHES); stage = 5; } }
            case 5 -> { if (data != null && data.sort() == LeaderboardSort.MATCHES) { leaderboardAction(LeaderboardPanel.Action.WIN_RATE); stage = 6; } }
            case 6 -> {
                if (data != null && data.sort() == LeaderboardSort.WIN_RATE) {
                    require(data.entries().stream().allMatch(e -> e.matches() >= data.minWinRateMatches()), "Win-rate minimum is enforced");
                    require(textShown(guiNode, "minimum " + data.minWinRateMatches()), "Qualification requirement is visible");
                    require(!data.entries().isEmpty() || !panel.status().isEmpty(), "Empty ranking explains its state");
                    // Component error display, followed by recovery through the real TCP request.
                    panel.setError("Could not load rankings. Please refresh.");
                    require(textShown(guiNode, "Could not load rankings") && panel.enabled(LeaderboardPanel.Action.REFRESH), "Error and retry are visible");
                    leaderboardAction(LeaderboardPanel.Action.REFRESH); stage = 7;
                }
            }
            case 7 -> {
                if (data != null) {
                    leaderboardAction(LeaderboardPanel.Action.REFRESH);
                    leaderboardAction(LeaderboardPanel.Action.BACK);
                    require(screens.mainMenu().visible(), "Back works while a request is pending");
                    click(screens.mainMenu().buttonCenter(MainMenu.Action.LEADERBOARD)); stage = 8;
                }
            }
            case 8 -> {
                if (data != null) {
                    require(data.sort() == LeaderboardSort.WINS && data.page() == 0, "Old response cannot overwrite reopened rankings");
                    leaderboardAction(LeaderboardPanel.Action.BACK);
                    System.out.println("PASS leaderboard UI: loading, real rows, sorts/pages, eligibility/empty state, error component/retry, stale-response guard, Main Menu.");
                    click(screens.mainMenu().buttonCenter(MainMenu.Action.EXIT)); stage = 9;
                }
            }
            default -> { }
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
                if (role.equals("A") && room != null && !Files.exists(journal.resolve("code"))) {
                    Files.writeString(journal.resolve("code.tmp"), room.roomCode());
                    Files.move(journal.resolve("code.tmp"), journal.resolve("code"), java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                }
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
                    require(!screens.pvp().connected() && screens.pvp().status().startsWith("Disconnected."),
                            "Login navigation preserves disconnect status without silently reconnecting");
                    System.out.println("PASS client B: explicit disconnect during Battle returns to login with the reason retained, without automatic reconnect."); stop(); stage = 7;
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

    private void settingsAction(SettingsPanel.Action action) {
        require(screens.settingsPanel().enabled(action), "Settings action enabled: " + action);
        click(screens.settingsPanel().buttonCenter(action));
    }
    private void chooseSize(int width, int height) {
        for (int i = 0; i < ClientPreferences.RESOLUTIONS.size(); i++) {
            var draft = screens.settingsPanel().draft();
            if (draft.width() == width && draft.height() == height) return;
            settingsAction(SettingsPanel.Action.SIZE_NEXT);
        }
        throw new AssertionError("Required demo resolution unavailable");
    }
    private void settingsScreens() throws Exception {
        var panel = screens.settingsPanel();
        var controller = screens.settings();
        switch (stage) {
            case 1 -> {
                if (!controller.ready()) return;
                settingsClientId = screens.pvp().clientId();
                click(screens.mainMenu().buttonCenter(MainMenu.Action.SETTINGS));
                require(panel.visible() && panel.enabled(SettingsPanel.Action.MODE), "Native display controls enabled");
                stage = 2;
            }
            case 2 -> {
                if (Boolean.getBoolean("battleship.settingsReload")) {
                    require(controller.current().equals(new ClientPreferences(1280, 720, true, 1.5f, .75f)), "Restart restores saved mode, size and sensitivities");
                    if (!display.fullscreen() || uiWidth < 1280 || uiHeight < 720) return;
                    capture = "settings-reloaded.png"; stage = 8; return;
                }
                chooseSize(960, 600); settingsAction(SettingsPanel.Action.SAVE); stage = 3;
            }
            case 3 -> {
                if (controller.saving() || uiWidth != 960 || uiHeight != 600) return;
                for (var action : SettingsPanel.Action.values()) {
                    var point = panel.buttonCenter(action);
                    require(point.x > 0 && point.x < uiWidth && point.y > 0 && point.y < uiHeight, "Settings hit target fits minimum window");
                    if (panel.enabled(action)) require(panel.actionAt(point).orElseThrow() == action, "Scaled Settings hit target works");
                }
                capture = "settings-minimum-" + role + ".png"; stage = 4;
            }
            case 4 -> {
                if (capture != null) return;
                if (role.equals("B")) org.lwjgl.glfw.GLFW.glfwMaximizeWindow(((com.jme3.system.lwjgl.LwjglWindow) getContext()).getWindowHandle());
                chooseSize(1280, 720);
                settingsAction(SettingsPanel.Action.ROTATION_UP); settingsAction(SettingsPanel.Action.ROTATION_UP);
                settingsAction(SettingsPanel.Action.ZOOM_DOWN);
                if (role.equals("A")) settingsAction(SettingsPanel.Action.MODE);
                settingsAction(SettingsPanel.Action.SAVE); stage = 5;
            }
            case 5 -> {
                if (controller.saving() || display.fullscreen() != role.equals("A")) return;
                var saved = controller.store().load();
                require(saved.message().isEmpty() && saved.preferences().equals(controller.current()), "Async atomic save matches active preferences");
                require(saved.preferences().rotation() == 1.5f && saved.preferences().zoom() == .75f, "Camera preferences saved");
                require(screens.pvp().connected() && screens.pvp().clientId().equals(settingsClientId), "Display changes preserve the authenticated TCP connection");
                if (role.equals("B")) {
                    require(org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(((com.jme3.system.lwjgl.LwjglWindow) getContext()).getWindowHandle(), org.lwjgl.glfw.GLFW.GLFW_MAXIMIZED) == org.lwjgl.glfw.GLFW.GLFW_FALSE,
                            "Selecting a saved window size restores a maximized window");
                    if (uiWidth != 1280 || uiHeight != 720) return;
                }
                if (role.equals("A") && uiWidth == 960) return;
                capture = "settings-saved-" + role + ".png"; stage = 6;
            }
            case 6 -> {
                if (capture != null) return;
                settingsAction(SettingsPanel.Action.ROTATION_DOWN); settingsAction(SettingsPanel.Action.RESET);
                require(panel.draft().equals(controller.current()), "Reset restores the last saved draft");
                settingsAction(SettingsPanel.Action.ROTATION_DOWN); key(KeyInput.KEY_ESCAPE, (char) 0);
                require(screens.mainMenu().visible() && !panel.visible(), "Escape returns to Main Menu");
                click(screens.mainMenu().buttonCenter(MainMenu.Action.SETTINGS));
                require(panel.draft().equals(controller.current()), "Back discards unsaved edits");
                if (role.equals("A")) {
                    settingsAction(SettingsPanel.Action.MODE); settingsAction(SettingsPanel.Action.SAVE); stage = 7;
                } else stage = 8;
            }
            case 7 -> {
                if (controller.saving() || display.fullscreen() || uiWidth != 1280 || uiHeight != 720) return;
                require(display.windowSize().equals(new ClientPreferences.Resolution(1280, 720)), "Fullscreen restores the chosen window size");
                settingsAction(SettingsPanel.Action.MODE); settingsAction(SettingsPanel.Action.SAVE); stage = 8;
            }
            case 8 -> {
                if (capture != null || controller.saving()) return;
                settingsAction(SettingsPanel.Action.BACK);
                require(screens.mainMenu().visible() && screens.pvp().connected(), "Back preserves the menu and session");
                click(screens.mainMenu().buttonCenter(MainMenu.Action.EXIT)); stage = 9;
                System.out.println("PASS Settings client " + role + ": working display/camera controls, save/discard, TCP continuity and Exit; reload=" + Boolean.getBoolean("battleship.settingsReload"));
            }
            default -> { }
        }
    }

    private void accountNavigation() {
        AccountPanel panel = screens.accountPanel();
        switch (stage) {
            case 1 -> {
                require(screens.mainMenu().visible() && MainMenu.Action.values().length == 6, "Authenticated menu has no Local Game action");
                click(screens.mainMenu().buttonCenter(MainMenu.Action.SETTINGS));
                require(screens.settingsPanel().visible() && !screens.mainMenu().visible(), "Settings opens a real screen");
                stage = 89;
            }
            case 89 -> {
                if (screens.settings().ready()) { key(KeyInput.KEY_ESCAPE, (char) 0); stage = 90; }
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
                    click(screens.mainMenu().buttonCenter(MainMenu.Action.PVP)); stage = 945;
                }
            }
            case 945 -> {
                if (screens.roomPanel().visible()) { action(PvpRoomPanel.Action.CREATE_ROOM); stage = 950; }
            }
            case 950 -> {
                if (screens.pvp().room() != null) { screens.pvp().returnToMainMenu(); stage = 96; }
            }
            case 96 -> {
                if (screens.mainMenu().visible()) {
                    System.out.println("PASS account screens: mandatory login, no Local Game or dummy actions, Settings Back, Profile logout, wrong-password gate, login again, room entry and Exit.");
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
        require(code.length() == 6, "Test must publish a complete room code before Join");
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
            int width = Boolean.getBoolean("battleship.settingsOnly") ? cam.getWidth() : 1440;
            int height = Boolean.getBoolean("battleship.settingsOnly") ? cam.getHeight() : 900;
            var buffer = BufferUtils.createByteBuffer(width * height * 4);
            renderer.getRenderer().readFrameBufferWithFormat(null, buffer, Image.Format.BGRA8);
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
            Screenshots.convertScreenShot(buffer, image);
            Path directory = journal.resolve("screenshots");
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
