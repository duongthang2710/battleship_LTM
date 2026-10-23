package com.battleship.frontend.pvp;

import com.battleship.frontend.network.ConnectionManager;
import com.battleship.frontend.menu.MainMenu;
import com.battleship.shared.room.JoinRoomRequest;
import com.battleship.shared.room.RoomError;
import com.battleship.shared.room.RoomPhase;
import com.battleship.shared.room.RoomSnapshot;
import com.battleship.shared.protocol.Message;
import com.battleship.shared.protocol.MessageType;
import com.battleship.shared.match.MatchSnapshot;
import com.battleship.shared.match.FleetSubmission;
import com.battleship.shared.match.ShipPlacement;
import com.battleship.shared.match.AttackRequest;
import com.battleship.shared.game.Board;
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
    private final String host;
    private final int port;
    private RoomSnapshot room;
    private String clientId;
    private String pendingRequest;
    private PvpRoomPanel.Action afterConnect;
    private boolean active;
    private boolean joining;
    private boolean leaving;
    private String status = "Disconnected";
    private MatchSnapshot match;
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

    public RoomSnapshot room() { return room; }
    public String clientId() { return clientId; }
    public boolean connected() { return connection.connected(); }
    public String status() { return status; }
    public MatchSnapshot match() { return match; }
    public boolean pending() { return pendingRequest != null; }

    public boolean submitFleet(Board board) {
        return send(MessageType.SUBMIT_FLEET, new FleetSubmission(board.ships().stream().map(ShipPlacement::from).toList()));
    }

    public boolean attack(Coordinate cell) { return send(MessageType.ATTACK, new AttackRequest(cell)); }

    public void open(MainMenu.Action action) {
        active = true;
        joining = action == MainMenu.Action.JOIN_ROOM;
        if (joining) panel.showJoin(); else panel.showChoice();
        afterConnect = action == MainMenu.Action.CREATE_ROOM ? PvpRoomPanel.Action.CREATE_ROOM : null;
        if (connection.connected()) {
            panel.setConnection(true, false);
            if (afterConnect != null) { afterConnect = null; handle(PvpRoomPanel.Action.CREATE_ROOM); }
        } else connect();
    }

    public void update() {
        connection.update();
        if (pendingRequest != null && System.nanoTime() - sentAt > java.util.concurrent.TimeUnit.SECONDS.toNanos(15)) {
            disconnect();
            setStatus("Server did not respond. Reconnect to continue.");
        }
    }

    public void handle(PvpRoomPanel.Action action) {
        if (!active || !panel.enabled(action)) return;
        switch (action) {
            case CONNECT -> connect();
            case DISCONNECT -> disconnect();
            case SHOW_JOIN -> { joining = true; panel.showJoin(); setStatus("Enter a six-character room code."); }
            case BACK -> {
                if (joining) { joining = false; panel.showChoice(); setStatus(connected() ? "Connected" : "Disconnected"); }
                else { close(); mainMenu.run(); }
            }
            case CREATE_ROOM -> send(MessageType.CREATE_ROOM, null);
            case JOIN_ROOM -> send(MessageType.JOIN_ROOM, new JoinRoomRequest(panel.roomCode()));
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
        room = null;
        match = null;
        clientId = null;
        pendingRequest = null;
        afterConnect = null;
        leaving = false;
        joining = false;
        panel.setBusy(false);
        panel.setConnection(false, false);
        panel.showChoice();
        setStatus("Disconnected. Connect to find a room.");
        showWaiting.run();
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
            if (afterConnect != null) {
                PvpRoomPanel.Action action = afterConnect;
                afterConnect = null;
                handle(action);
            }
        } else if (!state.connecting()) {
            room = null;
            clientId = null;
            pendingRequest = null;
            afterConnect = null;
            leaving = false;
            panel.setBusy(false);
            if (joining) panel.showJoin(); else panel.showChoice();
            showWaiting.run();
        }
    }

    private void onMessage(Message message) {
        if (!active) return;
        if (message.requestId().equals(pendingRequest)) {
            pendingRequest = null;
            panel.setBusy(false);
        }
        switch (message.type()) {
            case ROOM_CREATED, ROOM_JOINED, PLAYER_JOINED, ROOM_UPDATED, PLAYER_LEFT -> {
                room = message.payloadAs(RoomSnapshot.class);
                joining = false;
                panel.showRoom(room, clientId);
                setStatus(room.players().size() < 2 ? "Waiting for another admiral." : "Both admirals must be ready.");
                LOG.info(message.type() + " code=" + room.roomCode() + " players=" + room.players().size());
                if (message.type() == MessageType.PLAYER_LEFT) {
                    match = null;
                    setStatus("Opponent left. Waiting for another admiral.");
                    showWaiting.run();
                }
            }
            case ROOM_LEFT -> {
                room = null;
                match = null;
                leaving = false;
                panel.showChoice();
                setStatus("Room left. Create or join another room.");
                showWaiting.run();
            }
            case START_PLACEMENT -> {
                if (leaving) return;
                room = message.payloadAs(RoomSnapshot.class);
                match = null;
                pendingRequest = null;
                panel.setBusy(false);
                if (room.phase() == RoomPhase.PLACEMENT && room.players().stream().anyMatch(player -> player.clientId().equals(clientId))) {
                    LOG.info("START_PLACEMENT code=" + room.roomCode() + " player=" + playerName);
                    startPlacement.accept(room);
                }
            }
            case FLEET_ACCEPTED, START_BATTLE, MATCH_UPDATED -> {
                if (leaving || room == null) return;
                MatchSnapshot next = message.payloadAs(MatchSnapshot.class);
                if (!next.playerId().equals(clientId) || (match != null && next.revision() <= match.revision())) return;
                match = next;
                matchUpdates.accept(next);
            }
            case ERROR -> {
                leaving = false;
                RoomError error = message.payloadAs(RoomError.class);
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
        room = null;
        match = null;
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
