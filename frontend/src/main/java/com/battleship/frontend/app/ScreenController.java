package com.battleship.frontend.app;

import com.battleship.frontend.camera.OrbitCamera;
import com.battleship.frontend.auth.AccountPanel;
import com.battleship.frontend.auth.AccountInput;
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
    private enum Screen { MAIN_MENU, ACCOUNT, PVP, LOCAL_GAME, PVP_PLACEMENT }
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
    private final AccountPanel accountPanel;
    private AccountInput accountInput;
    private PointerInput accountPointer;
    private MainMenu.Action afterLogin;
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
        showMainMenu();
    }

    public AccountPanel accountPanel() { return accountPanel; }
    public MainMenu mainMenu() { return menu; }
    public GameController game() { return game; }
    public PvpController pvp() { return pvp; }
    public PvpRoomPanel roomPanel() { return roomPanel; }
    public boolean networkPlacement() { return screen == Screen.PVP_PLACEMENT; }

    public void requestMainMenu() { menuRequested = true; }

    public void resize(int width, int height) {
        menu.resize(width, height);
        accountPanel.resize(width, height);
        hud.resize(width, height);
        placementHud.resize(width, height);
        minimap.resize(width, height);
        gameOver.resize(width, height);
        roomPanel.resize(width, height);
    }

    public void update(float delta, Vector2f cursor) {
        pvp.update();
        accountPanel.sync(pvp.account(), pvp.status(), pvp.pending(), pvp.connected());
        if (screen == Screen.ACCOUNT && afterLogin != null && pvp.account() != null) {
            pendingAction = afterLogin;
            afterLogin = null;
        }
        if (screen == Screen.PVP && pvp.connected() && pvp.account() == null) {
            afterLogin = MainMenu.Action.PVP;
            showAccount();
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
            if (action == MainMenu.Action.LOCAL_GAME) startLocalGame();
            else if (action == MainMenu.Action.EXIT) { exit.run(); return; }
            else if (action == MainMenu.Action.PROFILE) { afterLogin = null; showAccount(); }
            else if (action == MainMenu.Action.PVP || action == MainMenu.Action.CREATE_ROOM
                    || action == MainMenu.Action.JOIN_ROOM) {
                if (pvp.account() == null) { afterLogin = action; showAccount(); }
                else { showPvpRoom(); pvp.open(action); }
            }
        }
        if (screen == Screen.MAIN_MENU) menu.update(cursor);
        else if (screen == Screen.PVP) roomPanel.update(cursor);
        else if (screen != Screen.ACCOUNT) game.update(delta, cursor);
    }

    private void menuClick(Vector2f cursor) {
        menu.actionAt(cursor).ifPresent(action -> {
            if (action == MainMenu.Action.PROFILE || action == MainMenu.Action.LOCAL_GAME || action == MainMenu.Action.EXIT
                    || action == MainMenu.Action.PVP || action == MainMenu.Action.CREATE_ROOM
                    || action == MainMenu.Action.JOIN_ROOM) pendingAction = action;
        });
    }

    private void startLocalGame() {
        closeInputs();
        menu.hide();
        pvp.hide();
        screen = Screen.LOCAL_GAME;
        game.useLocalMode();
        orbit.useDefaultView();
        game.restartLocalMatch();
        setGameHudVisible(true);
        gameInput = new GameInput(input, game, orbit);
    }

    private void showMainMenu() {
        closeInputs();
        pvp.hide();
        afterLogin = null;
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
        pvp.openAccount();
        accountPanel.show();
        accountInput = new AccountInput(input, accountPanel, this::accountAction);
        accountPointer = new PointerInput(input, orbit, cursor -> accountPanel.click(cursor).ifPresent(this::accountAction));
    }

    private void accountAction(AccountPanel.Action action) {
        if (!accountPanel.enabled(action)) return;
        switch (action) {
            case BACK -> { afterLogin = null; requestMainMenu(); }
            case SWITCH -> accountPanel.switchMode();
            case LOGOUT -> pvp.logout();
            case CONNECT -> pvp.openAccount();
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
