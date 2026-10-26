package com.battleship.frontend.pvp;

import com.battleship.frontend.network.ConnectionManager;
import com.battleship.frontend.menu.MainMenu;
import com.battleship.shared.room.JoinRoomRequest;
import com.battleship.shared.room.RoomError;
import com.battleship.shared.room.RoomPhase;
import com.battleship.shared.room.RoomSnapshot;
import com.battleship.shared.room.RematchState;
import com.battleship.shared.protocol.Message;
import com.battleship.shared.protocol.MessageType;
import com.battleship.shared.match.MatchSnapshot;
import com.battleship.shared.match.TurnTimer;
import com.battleship.shared.match.FleetSubmission;
import com.battleship.shared.match.ShipPlacement;
import com.battleship.shared.match.AttackRequest;
import com.battleship.shared.game.Board;
import com.battleship.shared.auth.*;
import com.battleship.shared.leaderboard.LeaderboardRequest;
import com.battleship.shared.game.Coordinate;
import java.util.function.Consumer;
import java.util.logging.Logger;

/** Coordinates room UI with the TCP adapter; the server owns membership and Ready. */
public final class PvpController implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(PvpController.class.getName());
    private final PvpRoomPanel panel;
    private final ConnectionManager connection;
    private final Consumer<RoomSnapshot> startPlacement;
    private final Runnable showWaiting;
    private final Runnable mainMenu;
    private final Consumer<MatchSnapshot> matchUpdates;
    private final Consumer<String> matchErrors;
    private final String playerName;
    private Consumer<Message> leaderboardListener = message -> { };
    private Consumer<Message> friendsListener = message -> { };
    private Consumer<Message> gameInviteListener = message -> { };
    private Runnable inviteFriendsAction = () -> { };
    private String inviteRoomRequest;
    private Consumer<Message> chatListener = message -> { };
    private String host;
    private int port;
    private RoomSnapshot room;
    private String clientId;
    private AccountProfile account;
    private String pendingRequest;
    private PvpRoomPanel.Action afterConnect;
    private boolean active;
    private boolean accountView;
    private boolean joining;
    private boolean leaving;
    private String status = "Disconnected";
    private MatchSnapshot match;
    private RematchState rematch;
    private String rematchStatus = "";
    private boolean menuAfterLeave;
    private TurnTimer turnTimer;
    private long clockReceivedAt;
    private long sentAt;

    public PvpController(PvpRoomPanel panel, String playerName, Consumer<RoomSnapshot> startPlacement,
                         Runnable showWaiting, Runnable mainMenu, Consumer<MatchSnapshot> matchUpdates,
                         Consumer<String> matchErrors) {
        this.panel = panel;
        this.playerName = playerName;
        this.startPlacement = startPlacement;
        this.showWaiting = showWaiting;
        this.mainMenu = mainMenu;
        this.matchUpdates = matchUpdates;
        this.matchErrors = matchErrors;
        host = System.getProperty("battleship.host", "localhost");
        port = Integer.getInteger("battleship.port", 2209);
        connection = new ConnectionManager(this::onMessage, this::onStatus);
    }

    public void setChatListener(Consumer<Message> listener) { chatListener = listener; }
    public String requestChat(com.battleship.shared.chat.ChatSend request) {
        if (account == null || room == null) return null;
        Message message = Message.request(MessageType.CHAT_SEND, request);
        return connection.send(message) ? message.requestId() : null;
    }

    public void setGameInviteListener(Consumer<Message> listener) { gameInviteListener = listener; }
    public void setInviteFriendsAction(Runnable action) { inviteFriendsAction = action; }
    public String requestGameInvite(MessageType type, Object payload) {
        if (account == null || pending()) return null;
        Message message = Message.request(type, payload);
        if (!connection.send(message)) return null;
        if (type == MessageType.SEND_GAME_INVITE || type == MessageType.ACCEPT_GAME_INVITE) inviteRoomRequest = message.requestId();
        return message.requestId();
    }
    public void showInvitedRoom() { if (room != null && room.phase() == RoomPhase.WAITING) showWaiting.run(); }

    public void setFriendsListener(Consumer<Message> listener) { friendsListener = listener; }
    public String requestFriends(com.battleship.shared.friends.FriendsRequest request) {
        if (account == null) return null;
        Message message = Message.request(MessageType.FRIENDS_REQUEST, request);
        return connection.send(message) ? message.requestId() : null;
    }

    public void setLeaderboardListener(Consumer<Message> listener) { leaderboardListener = listener; }
    public String requestLeaderboard(LeaderboardRequest request) {
        if (account == null) return null;
        Message message = Message.request(MessageType.LEADERBOARD_REQUEST, request);
        return connection.send(message) ? message.requestId() : null;
    }

    public AccountProfile account() { return account; }
    public void openAccount() {
        active = true;
        accountView = true;
        if (!connected()) connect();
    }
    public boolean connectTo(String serverHost, String serverPort) {
        if (account != null || room != null || pending()) return false;
        String nextHost = serverHost == null ? "" : serverHost.strip();
        if (nextHost.isEmpty() || nextHost.length() > 253 || nextHost.chars().anyMatch(Character::isWhitespace)) {
            setStatus("Enter a server IP address or hostname.");
            return false;
        }
        int nextPort;
        try { nextPort = Integer.parseInt(serverPort); }
        catch (NumberFormatException exception) { setStatus("Port must be a number from 1 to 65535."); return false; }
        if (nextPort < 1 || nextPort > 65535) { setStatus("Port must be a number from 1 to 65535."); return false; }
        host = nextHost;
        port = nextPort;
        connect();
        return true;
    }

    public void login(String username, String password) { send(MessageType.LOGIN, new LoginRequest(username, password)); }
    public void register(String username, String password, String confirmation) {
        send(MessageType.REGISTER, new RegisterRequest(username, password, confirmation));
    }
    public void logout() { if (room == null) send(MessageType.LOGOUT, null); }
    public void hide() { panel.hide(); }

    public RoomSnapshot room() { return room; }
    public String clientId() { return clientId; }
    public boolean connected() { return connection.connected(); }
    public String status() { return status; }
    public MatchSnapshot match() { return match; }
    public boolean pending() { return pendingRequest != null; }
    public RematchState rematch() { return rematch; }
    public String rematchStatus() { return rematchStatus; }
    public boolean returningToMenu() { return menuAfterLeave; }

    public void requestRematch() {
        if (match != null && match.phase() == com.battleship.shared.game.GamePhase.GAME_OVER
                && send(MessageType.REMATCH_REQUEST, null)) rematchStatus = "Requesting a rematch...";
    }

    public void declineRematch() {
        if (match != null && match.phase() == com.battleship.shared.game.GamePhase.GAME_OVER
                && send(MessageType.REMATCH_DECLINE, null)) rematchStatus = "Declining rematch...";
    }

    public void returnToMainMenu() {
        menuAfterLeave = true;
        rematchStatus = "Returning to main menu...";
        leaveForMenu();
    }

    private void leaveForMenu() {
        if (!menuAfterLeave || leaving || pendingRequest != null) return;
        if (room == null) { menuAfterLeave = false; mainMenu.run(); return; }
        leaving = true;
        send(MessageType.LEAVE_ROOM, null);
    }

    public long remainingTurnMillis() {
        if (match == null || match.phase() != com.battleship.shared.game.GamePhase.BATTLE || turnTimer == null) return 0;
        long elapsed = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - clockReceivedAt);
        return Math.max(0, turnTimer.remainingMillis() - elapsed);
    }

    public boolean submitFleet(Board board) {
        return send(MessageType.SUBMIT_FLEET, new FleetSubmission(board.ships().stream().map(ShipPlacement::from).toList()));
    }

    public boolean attack(Coordinate cell) { return send(MessageType.ATTACK, new AttackRequest(cell)); }

    public void open(MainMenu.Action action) {
        active = true;
        accountView = false;
        joining = action == MainMenu.Action.JOIN_ROOM;
        if (joining) panel.showJoin(); else panel.showChoice();
        afterConnect = action == MainMenu.Action.CREATE_ROOM ? PvpRoomPanel.Action.CREATE_ROOM : null;
        if (connection.connected()) {
            panel.setConnection(true, false);
            if (account != null && afterConnect != null) { afterConnect = null; handle(PvpRoomPanel.Action.CREATE_ROOM); }
        } else connect();
    }

    public void update() {
        connection.update();
        leaveForMenu();
        if (pendingRequest != null && System.nanoTime() - sentAt > java.util.concurrent.TimeUnit.SECONDS.toNanos(15)) {
            disconnect();
            setStatus("Server did not respond. Reconnect to continue.");
        }
    }

    public void handle(PvpRoomPanel.Action action) {
        if (!active || !panel.enabled(action)) return;
        switch (action) {
            case INVITE_FRIENDS -> inviteFriendsAction.run();
            case CONNECT -> connect();
            case DISCONNECT -> disconnect();
            case SHOW_JOIN -> { joining = true; panel.showJoin(); setStatus("Enter a six-character room code."); }
            case BACK -> {
                if (joining) { joining = false; panel.showChoice(); setStatus(connected() ? "Connected" : "Disconnected"); }
                else { hide(); mainMenu.run(); }
            }
            case CREATE_ROOM -> { if (account != null) send(MessageType.CREATE_ROOM, null); }
            case JOIN_ROOM -> { if (account != null) send(MessageType.JOIN_ROOM, new JoinRoomRequest(panel.roomCode())); }
            case LEAVE_ROOM -> { leaving = true; send(MessageType.LEAVE_ROOM, null); }
            case TOGGLE_READY -> {
                if (room == null) return;
                boolean ready = room.players().stream().anyMatch(player -> player.clientId().equals(clientId) && player.ready());
                send(ready ? MessageType.NOT_READY : MessageType.READY, null);
            }
        }
    }

    public void leaveRoom() {
        if (room != null && pendingRequest == null) { leaving = true; send(MessageType.LEAVE_ROOM, null); }
    }

    private void connect() {
        pendingRequest = null;
        panel.setBusy(false);
        connection.connect(host, port, playerName);
    }

    public void disconnect() {
        connection.disconnect();
        account = null;
        inviteRoomRequest = null;
        room = null;
        match = null;
        rematch = null;
        menuAfterLeave = false;
        clientId = null;
        pendingRequest = null;
        afterConnect = null;
        leaving = false;
        joining = false;
        panel.setBusy(false);
        panel.setConnection(false, false);
        panel.showChoice();
        setStatus("Disconnected. Connect to find a room.");
        if (!accountView) showWaiting.run();
    }

    private boolean send(MessageType type, Object payload) {
        if (pendingRequest != null) return false;
        Message request = Message.request(type, payload);
        if (connection.send(request)) {
            pendingRequest = request.requestId();
            sentAt = System.nanoTime();
            panel.setBusy(true);
            setStatus("Waiting for server...");
            return true;
        } else {
            leaving = false;
            setStatus("Connect to the server first.");
            return false;
        }
    }

    private void onStatus(ConnectionManager.Status state) {
        if (!active) return;
        panel.setConnection(state.connected(), state.connecting());
        setStatus(state.detail());
        if (state.connected()) {
            clientId = state.player().clientId();
            if (account != null && afterConnect != null) {
                PvpRoomPanel.Action action = afterConnect;
                afterConnect = null;
                handle(action);
            }
        } else if (!state.connecting()) {
            account = null;
            inviteRoomRequest = null;
            room = null;
            match = null;
            rematch = null;
            turnTimer = null;
            rematchStatus = "";
            menuAfterLeave = false;
            clientId = null;
            pendingRequest = null;
            afterConnect = null;
            leaving = false;
            panel.setBusy(false);
            if (joining) panel.showJoin(); else panel.showChoice();
            if (!accountView) showWaiting.run();
        }
    }

    private void onMessage(Message message) {
        if (!active) return;
        if (message.type() == MessageType.CHAT_CONTEXT || message.type() == MessageType.CHAT_MESSAGE || message.type() == MessageType.CHAT_ERROR) {
            chatListener.accept(message); return;
        }
        if (message.type() == MessageType.GAME_INVITE_UPDATED || message.type() == MessageType.GAME_INVITE_ERROR) {
            if (message.requestId().equals(inviteRoomRequest)) inviteRoomRequest = null;
            gameInviteListener.accept(message); return;
        }
        if (message.type() == MessageType.FRIENDS_RESULT || message.type() == MessageType.FRIENDS_ERROR || message.type() == MessageType.FRIENDS_CHANGED) {
            friendsListener.accept(message); return;
        }
        if (message.type() == MessageType.LEADERBOARD_RESULT || message.type() == MessageType.LEADERBOARD_ERROR) {
            leaderboardListener.accept(message);
            return;
        }
        if (message.requestId().equals(pendingRequest)) {
            pendingRequest = null;
            panel.setBusy(false);
        }
        switch (message.type()) {
            case AUTH_SUCCESS -> {
                account = message.payloadAs(AccountProfile.class);
                setStatus("Signed in as " + account.displayName() + ".");
            }
            case REGISTER_OK -> setStatus("Account created. Sign in to continue.");
            case LOGOUT_OK -> { account = null; setStatus("Signed out."); }
            case AUTH_FAILED -> {
                leaderboardListener.accept(message);
                friendsListener.accept(message);
                gameInviteListener.accept(message);
                chatListener.accept(message);
                AuthError error = message.payloadAs(AuthError.class);
                if (error.code().equals("AUTH_REQUIRED")) account = null;
                setStatus(error.message());
            }
            case ROOM_CREATED, ROOM_JOINED, PLAYER_JOINED, ROOM_UPDATED, PLAYER_LEFT -> {
                room = message.payloadAs(RoomSnapshot.class);
                if (message.type() == MessageType.PLAYER_LEFT && match != null
                        && match.phase() == com.battleship.shared.game.GamePhase.GAME_OVER) {
                    rematchStatus = "Opponent left. Rematch cancelled.";
                    return;
                }
                joining = false;
                panel.showRoom(room, clientId);
                setStatus(room.players().size() < 2 ? "Waiting for another admiral." : "Both admirals must be ready.");
                if (message.requestId().equals(inviteRoomRequest)
                        && (message.type() == MessageType.ROOM_CREATED || message.type() == MessageType.ROOM_JOINED)) {
                    inviteRoomRequest = null;
                    showWaiting.run();
                }
                LOG.info(message.type() + " code=" + room.roomCode() + " players=" + room.players().size());
                if (message.type() == MessageType.PLAYER_LEFT) {
                    match = null;
                    setStatus("Opponent left. Waiting for another admiral.");
                    showWaiting.run();
                }
            }
            case ROOM_LEFT -> {
                room = null;
                chatListener.accept(message);
                match = null;
                rematch = null;
                leaving = false;
                if (menuAfterLeave) {
                    menuAfterLeave = false;
                    mainMenu.run();
                    return;
                }
                panel.showChoice();
                setStatus("Room left. Create or join another room.");
                showWaiting.run();
            }
            case START_PLACEMENT -> {
                if (leaving || menuAfterLeave) return;
                room = message.payloadAs(RoomSnapshot.class);
                match = null;
                turnTimer = null;
                rematch = null;
                rematchStatus = "";
                pendingRequest = null;
                panel.setBusy(false);
                if (room.phase() == RoomPhase.PLACEMENT && room.players().stream().anyMatch(player -> player.clientId().equals(clientId))) {
                    LOG.info("START_PLACEMENT code=" + room.roomCode() + " player=" + playerName);
                    startPlacement.accept(room);
                }
            }
            case REMATCH_UPDATED -> {
                if (room == null || leaving || menuAfterLeave || match == null
                        || match.phase() != com.battleship.shared.game.GamePhase.GAME_OVER) return;
                RematchState next = message.payloadAs(RematchState.class);
                if (!next.roomCode().equals(room.roomCode())) return;
                rematch = next;
                rematchStatus = !next.available() ? "Opponent left. Rematch cancelled."
                        : next.declined() ? "Rematch declined."
                        : next.acceptedPlayerIds().contains(clientId) ? "Waiting for opponent..."
                        : "Opponent requests a rematch.";
            }
            case FLEET_ACCEPTED, START_BATTLE, MATCH_UPDATED -> {
                if (leaving || room == null) return;
                MatchSnapshot next = message.payloadAs(MatchSnapshot.class);
                if (!next.playerId().equals(clientId) || (match != null && next.revision() <= match.revision())) return;
                match = next;
                if (next.phase() == com.battleship.shared.game.GamePhase.GAME_OVER) {
                    pendingRequest = null;
                    panel.setBusy(false);
                }
                turnTimer = next.timer();
                clockReceivedAt = System.nanoTime();
                matchUpdates.accept(next);
            }
            case TURN_TIMER -> {
                if (leaving || room == null || match == null || match.phase() != com.battleship.shared.game.GamePhase.BATTLE) return;
                TurnTimer timer = message.payloadAs(TurnTimer.class);
                if (timer.matchRevision() != match.revision() || timer.turnNumber() != match.turnNumber()
                        || !timer.currentPlayerId().equals(match.currentPlayerId())) return;
                turnTimer = timer;
                clockReceivedAt = System.nanoTime();
            }
            case ERROR -> {
                // A rejected earlier request must not cancel a queued Main Menu action.
                if (leaving) menuAfterLeave = false;
                leaving = false;
                RoomError error = message.payloadAs(RoomError.class);
                rematchStatus = error.message();
                setStatus(error.message());
                matchErrors.accept(error.message());
                LOG.info("Room request rejected: " + error.code());
            }
            default -> { }
        }
    }

    private void setStatus(String text) { status = text; panel.setStatus(text); }

    @Override public void close() {
        active = false;
        connection.close();
        account = null;
        inviteRoomRequest = null;
        room = null;
        match = null;
        rematch = null;
        rematchStatus = "";
        menuAfterLeave = false;
        pendingRequest = null;
        afterConnect = null;
        clientId = null;
        leaving = false;
        joining = false;
        panel.setBusy(false);
        panel.setConnection(false, false);
        panel.hide();
    }
}
