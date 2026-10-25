package com.battleship.frontend.app;

import com.battleship.frontend.camera.OrbitCamera;
import com.battleship.frontend.auth.AccountPanel;
import com.battleship.frontend.auth.AccountInput;
import com.battleship.frontend.leaderboard.LeaderboardPanel;
import com.battleship.frontend.leaderboard.LeaderboardController;
import com.battleship.frontend.effects.AttackEffectManager;
import com.battleship.frontend.graphics.Materials;
import com.battleship.frontend.game.BoardPicker;
import com.battleship.frontend.game.GameInput;
import com.battleship.frontend.game.GameController;
import com.battleship.frontend.pvp.PvpController;
import com.battleship.frontend.input.PointerInput;
import com.battleship.frontend.pvp.RoomCodeInput;
import com.battleship.frontend.game.FleetRenderer;
import com.battleship.frontend.game.PlacementFeedback;
import com.battleship.frontend.game.TargetingRenderer;
import com.battleship.frontend.game.BattleHud;
import com.battleship.frontend.game.GameOverOverlay;
import com.battleship.frontend.menu.MainMenu;
import com.battleship.frontend.game.OwnBoardMinimap;
import com.battleship.frontend.game.PlacementHud;
import com.battleship.frontend.pvp.PvpRoomPanel;
import com.battleship.shared.room.RoomSnapshot;
import com.battleship.shared.game.GameEngine;
import com.jme3.asset.AssetManager;
import com.jme3.input.InputManager;
import com.jme3.math.Vector2f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial.CullHint;

/** Owns screen navigation and input lifetimes; game rules remain in GameEngine. */
public final class ScreenController implements AutoCloseable {
    private enum Screen { MAIN_MENU, ACCOUNT, LEADERBOARD, PVP, PVP_PLACEMENT }
    private final InputManager input;
    private final OrbitCamera orbit;
    private final Runnable exit;
    private final MainMenu menu;
    private final BattleHud hud;
    private final PlacementHud placementHud;
    private final OwnBoardMinimap minimap;
    private final GameOverOverlay gameOver;
    private final GameController game;
    private final PvpRoomPanel roomPanel;
    private final PvpController pvp;
    private final LeaderboardPanel leaderboardPanel;
    private final LeaderboardController leaderboard;
    private PointerInput leaderboardInput;
    private final AccountPanel accountPanel;
    private AccountInput accountInput;
    private PointerInput accountPointer;
    private boolean loginRequired;
    private PointerInput menuInput;
    private PointerInput roomInput;
    private RoomCodeInput codeInput;
    private GameInput gameInput;
    private MainMenu.Action pendingAction;
    private boolean menuRequested;
    private boolean roomRequested;
    private RoomSnapshot placementRequested;
    private Screen screen;

    public ScreenController(AssetManager assets, Node gui, InputManager input, Camera camera, OrbitCamera orbit,
                            Node board, String player, Runnable exit) {
        this.input = input;
        this.orbit = orbit;
        this.exit = exit;
        menu = new MainMenu(assets);
        accountPanel = new AccountPanel(assets);
        gui.attachChild(accountPanel.root());
        hud = new BattleHud(assets, player);
        placementHud = new PlacementHud(assets);
        minimap = new OwnBoardMinimap(assets);
        gameOver = new GameOverOverlay(assets);
        gui.attachChild(menu.root());
        gui.attachChild(hud.root());
        gui.attachChild(placementHud.root());
        gui.attachChild(minimap.root());
        gui.attachChild(gameOver.root());
        roomPanel = new PvpRoomPanel(assets);
        gui.attachChild(roomPanel.root());
        Materials materials = new Materials(assets);
        game = new GameController(new GameEngine(player), new BoardPicker(camera, input, board),
                new FleetRenderer(board, materials), new PlacementFeedback(board, materials), placementHud, hud, minimap,
                new TargetingRenderer(board, materials), new AttackEffectManager(board, materials), gameOver,
                this::requestMainMenu);
        pvp = new PvpController(roomPanel, player, room -> {
            placementRequested = room;
            roomRequested = false;
        }, () -> {
            placementRequested = null;
            roomRequested = true;
        }, this::requestMainMenu, game::applyNetworkState, game::networkError);
        leaderboardPanel = new LeaderboardPanel(assets);
        gui.attachChild(leaderboardPanel.root());
        leaderboard = new LeaderboardController(pvp, leaderboardPanel, this::requestMainMenu);
        showAccount();
    }

    public LeaderboardPanel leaderboardPanel() { return leaderboardPanel; }
    public LeaderboardController leaderboard() { return leaderboard; }
    public AccountPanel accountPanel() { return accountPanel; }
    public MainMenu mainMenu() { return menu; }
    public GameController game() { return game; }
    public PvpController pvp() { return pvp; }
    public PvpRoomPanel roomPanel() { return roomPanel; }
    public boolean networkPlacement() { return screen == Screen.PVP_PLACEMENT; }

    public void requestMainMenu() { menuRequested = true; }

    public void resize(int width, int height) {
        menu.resize(width, height);
        leaderboardPanel.resize(width, height);
        accountPanel.resize(width, height);
        hud.resize(width, height);
        placementHud.resize(width, height);
        minimap.resize(width, height);
        gameOver.resize(width, height);
        roomPanel.resize(width, height);
    }

    public void update(float delta, Vector2f cursor) {
        pvp.update();
        leaderboard.update();
        accountPanel.sync(pvp.account(), pvp.status(), pvp.pending(), pvp.connected());
        if (pvp.account() == null) {
            pendingAction = null;
            menuRequested = roomRequested = false;
            placementRequested = null;
            loginRequired = true;
            if (screen != Screen.ACCOUNT) {
                game.useLocalMode();
                game.restartLocalMatch();
                showAccount();
            }
            return;
        }
        if (screen == Screen.ACCOUNT && loginRequired) {
            loginRequired = false;
            menuRequested = true;
        }
        // Switch listeners after input dispatch, so the release cannot click the next screen.
        if (menuRequested) {
            menuRequested = false;
            pendingAction = null;
            roomRequested = false;
            placementRequested = null;
            showMainMenu();
        } else if (roomRequested) {
            roomRequested = false;
            showPvpRoom();
        } else if (placementRequested != null) {
            RoomSnapshot room = placementRequested;
            placementRequested = null;
            startNetworkPlacement(room);
        } else if (pendingAction != null) {
            MainMenu.Action action = pendingAction;
            pendingAction = null;
            if (action == MainMenu.Action.EXIT) { exit.run(); return; }
            else if (action == MainMenu.Action.PROFILE) showAccount();
            else if (action == MainMenu.Action.LEADERBOARD) showLeaderboard();
            else if (action == MainMenu.Action.PVP || action == MainMenu.Action.CREATE_ROOM
                    || action == MainMenu.Action.JOIN_ROOM) {
                showPvpRoom();
                pvp.open(action);
            }
        }
        if (screen == Screen.MAIN_MENU) menu.update(cursor);
        else if (screen == Screen.PVP) roomPanel.update(cursor);
        else if (screen == Screen.LEADERBOARD) leaderboardPanel.update(cursor);
        else if (screen != Screen.ACCOUNT) game.update(delta, cursor);
    }

    private void menuClick(Vector2f cursor) {
        menu.actionAt(cursor).ifPresent(action -> {
            if (action == MainMenu.Action.LEADERBOARD || action == MainMenu.Action.PROFILE || action == MainMenu.Action.EXIT
                    || action == MainMenu.Action.PVP || action == MainMenu.Action.CREATE_ROOM
                    || action == MainMenu.Action.JOIN_ROOM) pendingAction = action;
        });
    }

    private void showLeaderboard() {
        closeInputs();
        menu.hide(); roomPanel.hide(); setGameHudVisible(false);
        screen = Screen.LEADERBOARD;
        leaderboard.show();
        leaderboardInput = new PointerInput(input, orbit, cursor -> leaderboardPanel.actionAt(cursor).ifPresent(leaderboard::handle));
    }

    private void showMainMenu() {
        if (pvp.account() == null) { showAccount(); return; }
        closeInputs();
        pvp.hide();
        screen = Screen.MAIN_MENU;
        game.useLocalMode();
        orbit.useDefaultView();
        game.restartLocalMatch();
        setGameHudVisible(false);
        menu.show();
        if (menuInput == null) menuInput = new PointerInput(input, orbit, this::menuClick);
    }

    private void showAccount() {
        closeInputs();
        menu.hide();
        roomPanel.hide();
        setGameHudVisible(false);
        screen = Screen.ACCOUNT;
        loginRequired = pvp.account() == null;
        pvp.openAccount();
        accountPanel.show();
        accountInput = new AccountInput(input, accountPanel, this::accountAction);
        accountPointer = new PointerInput(input, orbit, cursor -> accountPanel.click(cursor).ifPresent(this::accountAction));
    }

    private void accountAction(AccountPanel.Action action) {
        if (!accountPanel.enabled(action)) return;
        switch (action) {
            case BACK -> requestMainMenu();
            case EXIT -> exit.run();
            case SWITCH -> accountPanel.switchMode();
            case LOGOUT -> pvp.logout();
            case SERVER -> accountPanel.toggleServer();
            case CONNECT -> {
                if (accountPanel.editingServer()) {
                    if (pvp.connectTo(accountPanel.serverHost(), accountPanel.serverPort())) accountPanel.applyServer();
                } else pvp.openAccount();
            }
            case SUBMIT -> {
                if (!pvp.connected()) pvp.openAccount();
                else if (accountPanel.registering()) pvp.register(accountPanel.username(), accountPanel.password(), accountPanel.confirmation());
                else pvp.login(accountPanel.username(), accountPanel.password());
                accountPanel.clearSecrets();
            }
        }
    }

    private void showPvpRoom() {
        closeInputs();
        menu.hide();
        game.useLocalMode();
        game.restartLocalMatch();
        setGameHudVisible(false);
        screen = Screen.PVP;
        // Consume room-code letters before the camera sees shortcuts such as R.
        codeInput = new RoomCodeInput(input, roomPanel, pvp::handle);
        roomInput = new PointerInput(input, orbit, cursor -> roomPanel.actionAt(cursor).ifPresent(pvp::handle));
    }

    private void startNetworkPlacement(RoomSnapshot room) {
        closeInputs();
        menu.hide();
        roomPanel.hide();
        screen = Screen.PVP_PLACEMENT;
        game.restartLocalMatch();
        String opponent = room.players().stream().filter(player -> !player.clientId().equals(pvp.clientId()))
                .map(player -> player.playerName()).findFirst().orElse("Opponent");
        var side = room.players().stream().filter(player -> player.clientId().equals(pvp.clientId()))
                .findFirst().orElseThrow().side();
        orbit.setPlayerSide(side);
        game.useNetworkPlacement(opponent, room.roomCode(), side, pvp);
        if (pvp.match() != null) game.applyNetworkState(pvp.match());
        setGameHudVisible(true);
        gameInput = new GameInput(input, game, orbit);
    }

    private void closeInputs() {
        leaderboard.hide();
        if (leaderboardInput != null) { leaderboardInput.close(); leaderboardInput = null; }
        accountPanel.hide();
        if (accountInput != null) { accountInput.close(); accountInput = null; }
        if (accountPointer != null) { accountPointer.close(); accountPointer = null; }
        if (menuInput != null) { menuInput.close(); menuInput = null; }
        if (gameInput != null) { gameInput.close(); gameInput = null; }
        if (roomInput != null) { roomInput.close(); roomInput = null; }
        if (codeInput != null) { codeInput.close(); codeInput = null; }
    }

    private void setGameHudVisible(boolean visible) {
        CullHint hint = visible ? CullHint.Inherit : CullHint.Always;
        hud.root().setCullHint(hint);
        placementHud.root().setCullHint(hint);
        minimap.root().setCullHint(hint);
        gameOver.hide();
    }

    @Override public void close() {
        closeInputs();
        pvp.close();
    }
}
