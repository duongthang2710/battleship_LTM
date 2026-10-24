package com.battleship.backend.room;

import com.battleship.backend.server.ConnectionManager;
import com.battleship.backend.match.MatchService;
import com.battleship.shared.game.Side;
import com.battleship.shared.game.GamePhase;
import com.battleship.shared.match.AttackRequest;
import com.battleship.shared.match.FleetSubmission;
import com.battleship.shared.room.JoinRoomRequest;
import com.battleship.shared.room.RoomError;
import com.battleship.shared.room.RoomPhase;
import com.battleship.shared.room.RoomPlayer;
import com.battleship.shared.room.RoomSnapshot;
import com.battleship.shared.game.PlayerIdentity;
import com.battleship.shared.protocol.Message;
import com.battleship.shared.protocol.MessageType;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class RoomManager implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(RoomManager.class.getName());
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Room> rooms = new HashMap<>();
    private final Map<String, String> memberships = new HashMap<>();
    private final ConnectionManager connections;
    private final ScheduledExecutorService clock = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon(true).name("match-clock").factory());

    public RoomManager(ConnectionManager connections) {
        this.connections = connections;
        clock.scheduleAtFixedRate(this::updateClocks, 200, 200, TimeUnit.MILLISECONDS);
    }

    private synchronized void updateClocks() {
        for (Room room : rooms.values()) {
            if (room.match == null) continue;
            if (room.match.expireTurn()) {
                publishMatch(room, MessageType.MATCH_UPDATED, null, null);
            } else {
                var timer = room.match.clockUpdate();
                if (timer != null) {
                    Message update = Message.request(MessageType.TURN_TIMER, timer);
                    room.players.keySet().forEach(id -> connections.send(id, update));
                }
            }
        }
    }

    @Override public void close() { clock.shutdownNow(); }

    // Keep mutations and notifications ordered even when both clients act together.
    public synchronized void handle(PlayerIdentity player, Message message) {
        switch (message.type()) {
            case CREATE_ROOM -> create(player, message);
            case JOIN_ROOM -> join(player, message);
            case LEAVE_ROOM -> {
                if (!memberships.containsKey(player.clientId())) {
                    error(player, message, "NOT_IN_ROOM", "You are not in a room.");
                    return;
                }
                leave(player.clientId(), message.requestId());
            }
            case READY, NOT_READY -> ready(player, message);
            case SUBMIT_FLEET, ATTACK -> matchCommand(player, message);
            default -> error(player, message, "INVALID_REQUEST", "Unsupported room command.");
        }
    }

    public synchronized void disconnected(String clientId) {
        leave(clientId, null);
    }

    private void create(PlayerIdentity player, Message message) {
        if (alreadyInRoom(player, message)) return;
        String code;
        do {
            StringBuilder builder = new StringBuilder(6);
            for (int i = 0; i < 6; i++) builder.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
            code = builder.toString();
        } while (rooms.containsKey(code));
        Room room = new Room(code);
        room.players.put(player.clientId(), new RoomPlayer(player.clientId(), player.playerName(), false));
        rooms.put(code, room);
        memberships.put(player.clientId(), code);
        connections.send(player.clientId(), Message.reply(MessageType.ROOM_CREATED, message.requestId(), room.snapshot()));
        LOG.info("ROOM_CREATED code=" + code + " player=" + player.playerName());
    }

    private void join(PlayerIdentity player, Message message) {
        if (alreadyInRoom(player, message)) return;
        String code;
        try {
            JoinRoomRequest request = message.payloadAs(JoinRoomRequest.class);
            code = request.roomCode() == null ? "" : request.roomCode().strip().toUpperCase(Locale.ROOT);
        } catch (RuntimeException exception) {
            error(player, message, "INVALID_ROOM_CODE", "Enter a six-character room code.");
            return;
        }
        if (code.length() != 6 || !code.chars().allMatch(value -> CODE_ALPHABET.indexOf(value) >= 0)) {
            error(player, message, "INVALID_ROOM_CODE", "Enter a six-character room code.");
            return;
        }
        Room room = rooms.get(code);
        if (room == null) {
            error(player, message, "ROOM_NOT_FOUND", "Room not found.");
            return;
        }
        if (room.phase != RoomPhase.WAITING) {
            error(player, message, "ROOM_STARTED", "This room has already started placement.");
            return;
        }
        if (room.players.size() >= 2) {
            error(player, message, "ROOM_FULL", "This room already has two players.");
            return;
        }
        room.players.put(player.clientId(), new RoomPlayer(player.clientId(), player.playerName(), false));
        memberships.put(player.clientId(), code);
        RoomSnapshot snapshot = room.snapshot();
        connections.send(player.clientId(), Message.reply(MessageType.ROOM_JOINED, message.requestId(), snapshot));
        room.players.keySet().stream().filter(id -> !id.equals(player.clientId()))
                .forEach(id -> connections.send(id, Message.request(MessageType.PLAYER_JOINED, snapshot)));
        LOG.info("ROOM_JOINED code=" + code + " player=" + player.playerName());
    }

    private void ready(PlayerIdentity player, Message message) {
        Room room = rooms.get(memberships.get(player.clientId()));
        if (room == null) {
            error(player, message, "NOT_IN_ROOM", "You are not in a room.");
            return;
        }
        if (room.phase != RoomPhase.WAITING) {
            error(player, message, "ROOM_STARTED", "Placement has already started.");
            return;
        }
        room.players.put(player.clientId(), new RoomPlayer(player.clientId(), player.playerName(),
                message.type() == MessageType.READY));
        RoomSnapshot waiting = room.snapshot();
        room.players.keySet().forEach(id -> connections.send(id, id.equals(player.clientId())
                ? Message.reply(MessageType.ROOM_UPDATED, message.requestId(), waiting)
                : Message.request(MessageType.ROOM_UPDATED, waiting)));
        if (room.players.size() == 2 && room.players.values().stream().allMatch(RoomPlayer::ready)) {
            room.phase = RoomPhase.PLACEMENT;
            String whiteId = room.players.keySet().stream().skip(random.nextInt(2)).findFirst().orElseThrow();
            room.players.replaceAll((id, member) -> new RoomPlayer(id, member.playerName(), true,
                    id.equals(whiteId) ? Side.WHITE : Side.BLACK));
            room.match = new MatchService(room.snapshot().players());
            broadcast(room, MessageType.START_PLACEMENT);
            LOG.info("START_PLACEMENT code=" + room.code);
        }
    }

    private void matchCommand(PlayerIdentity player, Message message) {
        Room room = rooms.get(memberships.get(player.clientId()));
        if (room == null || room.match == null) {
            error(player, message, "MATCH_NOT_STARTED", "No active match.");
            return;
        }
        try {
            MessageType event;
            if (message.type() == MessageType.SUBMIT_FLEET) {
                room.match.submit(player.clientId(), message.payloadAs(FleetSubmission.class));
                event = room.match.phase() == GamePhase.BATTLE ? MessageType.START_BATTLE : MessageType.FLEET_ACCEPTED;
            } else {
                AttackRequest request = message.payloadAs(AttackRequest.class);
                GamePhase before = room.match.phase();
                var result = room.match.attack(player.clientId(), request == null ? null : request.coordinate());
                if (!result.accepted()) {
                    if (before == GamePhase.BATTLE && room.match.phase() == GamePhase.GAME_OVER)
                        publishMatch(room, MessageType.MATCH_UPDATED, null, null);
                    error(player, message, result.type().name(), "Attack rejected: " + result.type());
                    return;
                }
                event = MessageType.MATCH_UPDATED;
            }
            publishMatch(room, event, player.clientId(), message.requestId());
        } catch (com.google.gson.JsonParseException exception) {
            error(player, message, "INVALID_MATCH_COMMAND", "Malformed match data.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            error(player, message, message.type() == MessageType.SUBMIT_FLEET ? "INVALID_FLEET" : "INVALID_MATCH_COMMAND",
                    exception.getMessage());
        }
    }

    private void publishMatch(Room room, MessageType event, String actorId, String requestId) {
        for (String id : room.players.keySet()) connections.send(id, Message.reply(event,
                id.equals(actorId) ? requestId : java.util.UUID.randomUUID().toString(), room.match.snapshot(id)));
    }

    private void leave(String clientId, String requestId) {
        String code = memberships.remove(clientId);
        if (code == null) return;
        Room room = rooms.get(code);
        room.players.remove(clientId);
        if (requestId != null) connections.send(clientId, Message.reply(MessageType.ROOM_LEFT, requestId, null));
        if (room.players.isEmpty()) {
            rooms.remove(code);
        } else {
            room.phase = RoomPhase.WAITING;
            room.match = null;
            room.players.replaceAll((id, player) -> new RoomPlayer(id, player.playerName(), false));
            broadcast(room, MessageType.PLAYER_LEFT);
        }
        LOG.info("PLAYER_LEFT code=" + code + " player=" + clientId);
    }

    private boolean alreadyInRoom(PlayerIdentity player, Message message) {
        if (!memberships.containsKey(player.clientId())) return false;
        error(player, message, "ALREADY_IN_ROOM", "Leave your current room first.");
        return true;
    }

    private void error(PlayerIdentity player, Message message, String code, String text) {
        connections.send(player.clientId(), Message.reply(MessageType.ERROR, message.requestId(), new RoomError(code, text)));
    }

    private void broadcast(Room room, MessageType type) {
        Message message = Message.request(type, room.snapshot());
        room.players.keySet().forEach(id -> connections.send(id, message));
    }

}
