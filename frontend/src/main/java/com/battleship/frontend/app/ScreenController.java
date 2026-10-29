package com.battleship.frontend.app;

import com.battleship.frontend.camera.OrbitCamera;
import com.battleship.frontend.friends.*;
import com.battleship.frontend.chat.*;
import com.battleship.frontend.settings.*;
import com.battleship.frontend.pvp.GameInvitePanel;
import com.battleship.frontend.pvp.GameInviteController;
import com.battleship.frontend.pvp.GameInviteInput;
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
import com.battleship.frontend.input.NavigationInput;
import com.battleship.frontend.network.ConnectionNoticePanel;
import com.battleship.frontend.network.ConnectionNoticeInput;
import com.battleship.frontend.ui.ClipboardService;
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
    private enum Screen { MAIN_MENU, ACCOUNT, LEADERBOARD, FRIENDS, SETTINGS, PVP, PVP_PLACEMENT }
    private final InputManager input;
    private final OrbitCamera orbit;
    private final Runnable exit;
    private final MainMenu menu;
    private final SettingsPanel settingsPanel;
    private final SettingsController settings;
    private PointerInput settingsPointer;
    private SettingsInput settingsInput;
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
    private final GameInvitePanel gameInvitePanel;
    private final GameInviteController gameInvites;
    private final GameInviteInput gameInviteInput;
    private final ChatPanel chatPanel;
    private final ChatController chat;
    private final ChatInput chatInput;
    private final FriendsPanel friendsPanel;
    private final FriendsController friends;
    private PointerInput friendsPointer;
    private FriendsInput friendsInput;
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
    private final ConnectionNoticePanel connectionNotice;
    private final ConnectionNoticeInput connectionNoticeInput;
    private final NavigationInput navigationInput;
    private final ClipboardService clipboard = new ClipboardService();
    private boolean backRequested, connectionSettingsRequested;

    public ScreenController(AssetManager assets, Node gui, InputManager input, Camera camera, OrbitCamera orbit,
                            Node board, String player, Runnable exit) {
        this(assets, gui, input, camera, orbit, board, player, exit, null);
    }

    public ScreenController(AssetManager assets, Node gui, InputManager input, Camera camera, OrbitCamera orbit,
                            Node board, String player, Runnable exit, DisplaySettings display) {
        this.input = input;
        this.orbit = orbit;
        this.exit = exit;
        menu = new MainMenu(assets);
        settingsPanel = new SettingsPanel(assets);
        settings = new SettingsController(settingsPanel, orbit, display, this::requestMainMenu);
        gui.attachChild(settingsPanel.root());
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
        connectionNotice = new ConnectionNoticePanel(assets);
        gui.attachChild(connectionNotice.root());
        connectionNoticeInput = new ConnectionNoticeInput(input, connectionNotice, this::connectionAction);
        leaderboardPanel = new LeaderboardPanel(assets);
        gui.attachChild(leaderboardPanel.root());
        leaderboard = new LeaderboardController(pvp, leaderboardPanel, this::requestMainMenu);
        friendsPanel = new FriendsPanel(assets);
        gui.attachChild(friendsPanel.root());
        gameInvitePanel = new GameInvitePanel(assets);
        gui.attachChild(gameInvitePanel.root());
        gameInvites = new GameInviteController(pvp, gameInvitePanel);
        gameInviteInput = new GameInviteInput(input, gameInvitePanel, gameInvites::handle);
        chatPanel = new ChatPanel(assets);
        gui.attachChild(chatPanel.root());
        chat = new ChatController(pvp, chatPanel);
        chatInput = new ChatInput(input, chatPanel, chat::handle);
        navigationInput = new NavigationInput(input, () -> backRequested = true);
        friends = new FriendsController(pvp, friendsPanel, () -> {
            if (pvp.room() != null) roomRequested = true; else requestMainMenu();
        }, menu::friendsUpdated, gameInvites::send);
        pvp.setInviteFriendsAction(() -> pendingAction = MainMenu.Action.FRIENDS);
        showAccount();
    }

    public ConnectionNoticePanel connectionNotice() { return connectionNotice; }
    public SettingsPanel settingsPanel() { return settingsPanel; }
    public SettingsController settings() { return settings; }
    public ChatPanel chatPanel() { return chatPanel; }
    public ChatController chat() { return chat; }
    public GameInvitePanel gameInvitePanel() { return gameInvitePanel; }
    public GameInviteController gameInvites() { return gameInvites; }
    public FriendsPanel friendsPanel() { return friendsPanel; }
    public FriendsController friends() { return friends; }
    public LeaderboardPanel leaderboardPanel() { return leaderboardPanel; }
    public LeaderboardController leaderboard() { return leaderboard; }
    public AccountPanel accountPanel() { return accountPanel; }
    public MainMenu mainMenu() { return menu; }
    public GameController game() { return game; }
    public PvpController pvp() { return pvp; }
    public PvpRoomPanel roomPanel() { return roomPanel; }
    public boolean networkPlacement() { return screen == Screen.PVP_PLACEMENT; }
    public void toggleFullscreen() { settings.toggleFullscreen(); }

    public void requestMainMenu() { menuRequested = true; }

    public void resize(int width, int height) {
        connectionNotice.resize(width, height);
        menu.resize(width, height);
        settingsPanel.resize(width, height);
        leaderboardPanel.resize(width, height);
        friendsPanel.resize(width, height);
        gameInvitePanel.resize(width, height);
        chatPanel.resize(width, height);
        accountPanel.resize(width, height);
        hud.resize(width, height);
        placementHud.resize(width, height);
        minimap.resize(width, height);
        gameOver.resize(width, height);
        roomPanel.resize(width, height);
    }

    public void update(float delta, Vector2f cursor) {
        pvp.update();
        settings.update();
        leaderboard.update();
        friends.update();
        gameInvites.update();
        chat.update();
        clipboard.update();
        accountPanel.sync(pvp.account(), pvp.status(), pvp.pending(), pvp.connected());
        accountPanel.update(delta);
        friendsPanel.updateCaret(delta);
        roomPanel.updateCaret(delta);
        chatPanel.updateCaret(delta);
        connectionNotice.sync(pvp.connectionProblem(), pvp.connecting());
        if (connectionSettingsRequested) {
            connectionSettingsRequested = false;
            showAccount();
            if (!accountPanel.editingServer()) accountPanel.toggleServer();
        }
        if (backRequested) { backRequested = false; navigateBack(); }
        if (pvp.account() == null) {
            pendingAction = null;
            menuRequested = roomRequested = false;
            placementRequested = null;
            loginRequired = true;
            chatPanel.setMode(ChatPanel.Mode.HIDDEN);
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
            else if (action == MainMenu.Action.SETTINGS) showSettings();
            else if (action == MainMenu.Action.PROFILE) showAccount();
            else if (action == MainMenu.Action.FRIENDS) showFriends();
            else if (action == MainMenu.Action.LEADERBOARD) showLeaderboard();
            else if (action == MainMenu.Action.PVP) {
                showPvpRoom();
                pvp.open();
            }
        }
        boolean waitingChat = screen == Screen.PVP && pvp.room() != null && pvp.room().phase() == com.battleship.shared.room.RoomPhase.WAITING;
        boolean battleChat = screen == Screen.PVP_PLACEMENT && (game.state().phase() == com.battleship.shared.game.GamePhase.BATTLE
                || game.state().phase() == com.battleship.shared.game.GamePhase.GAME_OVER);
        chatPanel.setMode(waitingChat ? ChatPanel.Mode.WAITING : battleChat ? ChatPanel.Mode.BATTLE : ChatPanel.Mode.HIDDEN);
        if (screen == Screen.MAIN_MENU) menu.update(cursor);
        else if (screen == Screen.PVP) roomPanel.update(cursor);
        else if (screen == Screen.FRIENDS) friendsPanel.update(cursor);
        else if (screen == Screen.LEADERBOARD) leaderboardPanel.update(cursor);
        else if (screen != Screen.ACCOUNT && screen != Screen.SETTINGS) game.update(delta, cursor);
    }

    private void connectionAction(ConnectionNoticePanel.Action action) {
        switch (action) {
            case RECONNECT -> pvp.reconnect();
            case DISMISS -> pvp.dismissConnectionNotice();
            case SETTINGS -> { pvp.dismissConnectionNotice(); connectionSettingsRequested = true; }
        }
    }

    private void roomAction(PvpRoomPanel.Action action) {
        if (action != PvpRoomPanel.Action.COPY_CODE) { pvp.handle(action); return; }
        if (pvp.room() == null) return;
        String code = pvp.room().roomCode();
        clipboard.copy(code, status -> {
            if (pvp.room() != null && pvp.room().roomCode().equals(code)) roomPanel.setStatus(status);
        });
    }

    private void navigateBack() {
        if (screen == null) return;
        if (gameInvitePanel.visible()) { gameInvitePanel.hide(); return; }
        switch (screen) {
            case ACCOUNT -> {
                if (accountPanel.editingServer()) accountPanel.toggleServer();
                else if (accountPanel.registering()) accountPanel.switchMode();
                else if (pvp.account() != null) requestMainMenu();
            }
            case SETTINGS -> settings.handle(SettingsPanel.Action.BACK);
            case FRIENDS -> friends.handle(new FriendsPanel.Command(friendsPanel.confirming()
                    ? FriendsPanel.Action.CANCEL_REMOVE : FriendsPanel.Action.BACK, null));
            case LEADERBOARD -> leaderboard.handle(LeaderboardPanel.Action.BACK);
            case PVP -> {
                if (pvp.room() != null) pvp.handle(PvpRoomPanel.Action.LEAVE_ROOM);
                else pvp.handle(PvpRoomPanel.Action.BACK);
            }
            case PVP_PLACEMENT -> pvp.returnToMainMenu();
            case MAIN_MENU -> { } // Root screen; Esc never signs out or exits.
        }
    }

    private void menuClick(Vector2f cursor) {
        menu.actionAt(cursor).ifPresent(action -> {
            if (action == MainMenu.Action.SETTINGS || action == MainMenu.Action.FRIENDS || action == MainMenu.Action.LEADERBOARD || action == MainMenu.Action.PROFILE || action == MainMenu.Action.EXIT
                    || action == MainMenu.Action.PVP) pendingAction = action;
        });
    }

    private void showSettings() {
        closeInputs(); menu.hide(); roomPanel.hide(); setGameHudVisible(false);
        screen = Screen.SETTINGS; settings.show();
        settingsInput = new SettingsInput(input, settingsPanel, settings::handle);
        settingsPointer = new PointerInput(input, orbit, cursor -> settingsPanel.actionAt(cursor).ifPresent(settings::handle));
    }

    private void showFriends() {
        closeInputs(); menu.hide(); roomPanel.hide(); setGameHudVisible(false);
        screen = Screen.FRIENDS; friendsPanel.setInRoom(pvp.room() != null); friends.show();
        friendsInput = new FriendsInput(input, friendsPanel, friends::handle);
        friendsPointer = new PointerInput(input, orbit, cursor -> friendsPanel.click(cursor).ifPresent(friends::handle));
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
        boolean firstScreen = screen == null;
        closeInputs();
        menu.hide();
        roomPanel.hide();
        setGameHudVisible(false);
        screen = Screen.ACCOUNT;
        loginRequired = pvp.account() == null;
        pvp.openAccount(firstScreen);
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
        roomInput = new PointerInput(input, orbit, cursor -> roomPanel.actionAt(cursor).ifPresent(this::roomAction));
        pvp.showPanel();
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
        game.useNetworkPlacement(opponent, room.roomCode(), side, room.rules(), pvp);
        if (pvp.match() != null) game.applyNetworkState(pvp.match());
        setGameHudVisible(true);
        gameInput = new GameInput(input, game, orbit);
    }

    private void closeInputs() {
        settings.hide();
        if (settingsPointer != null) { settingsPointer.close(); settingsPointer = null; }
        if (settingsInput != null) { settingsInput.close(); settingsInput = null; }
        friends.hide();
        if (friendsPointer != null) { friendsPointer.close(); friendsPointer = null; }
        if (friendsInput != null) { friendsInput.close(); friendsInput = null; }
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
        navigationInput.close();
        connectionNoticeInput.close();
        clipboard.close();
        gameInviteInput.close();
        chatInput.close();
        settings.close();
        pvp.close();
    }
}
