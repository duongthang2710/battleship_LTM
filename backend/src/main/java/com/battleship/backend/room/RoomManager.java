package com.battleship.backend.room;

import com.battleship.backend.server.ConnectionManager;
import com.battleship.backend.match.MatchService;
import com.battleship.backend.chat.ChatService;
import com.battleship.shared.chat.*;
import com.battleship.backend.match.MatchRepository;
import com.battleship.shared.game.Side;
import com.battleship.shared.game.GamePhase;
import com.battleship.shared.match.AttackRequest;
import com.battleship.shared.match.FleetSubmission;
import com.battleship.shared.room.JoinRoomRequest;
import com.battleship.shared.room.GameInviteStatus;
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
    private final MatchRepository history;
    private final GameInvites invitations;
    private final ChatService chat = new ChatService();
    private final ScheduledExecutorService clock = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon(true).name("match-clock").factory());

    public RoomManager(ConnectionManager connections, MatchRepository history) {
        this.connections = connections;
        this.history = history;
        invitations = new GameInvites(connections);
        clock.scheduleAtFixedRate(this::updateClocks, 200, 200, TimeUnit.MILLISECONDS);
    }

    private synchronized void updateClocks() {
        invitations.expire();
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
            case CHAT_SEND -> chat(player, message);
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
            case REMATCH_REQUEST, REMATCH_DECLINE -> rematch(player, message);
            default -> error(player, message, "INVALID_REQUEST", "Unsupported room command.");
        }
    }

    public synchronized void disconnected(String clientId) {
        invitations.invalidateClient(clientId, "Player signed out or disconnected. Invitation cancelled.");
        leave(clientId, null);
        chat.disconnected(clientId);
    }

    public synchronized void invite(PlayerIdentity player, java.util.UUID friendId, String requestId) {
        invitations.expire();
        var inviter = connections.account(player.clientId());
        if (inviter == null) throw new IllegalArgumentException("Sign in to invite friends.");
        PlayerIdentity recipient = connections.onlinePlayer(friendId);
        if (recipient == null) throw new IllegalArgumentException("This friend is offline.");
        Room room = rooms.get(memberships.get(player.clientId()));
        if (room != null && room.players.containsKey(recipient.clientId()))
            throw new IllegalArgumentException("You are already in the same room.");
        if (room != null && (room.phase != RoomPhase.WAITING || room.match != null))
            throw new IllegalArgumentException("This room has already started a match.");
        if (room != null && room.players.size() >= 2) throw new IllegalArgumentException("This room is full.");
        if (memberships.containsKey(recipient.clientId())) throw new IllegalArgumentException("This friend is already in another room.");
        invitations.checkCapacity(room, recipient.clientId());
        if (room == null) {
            create(player, Message.reply(MessageType.CREATE_ROOM, requestId, null));
            room = rooms.get(memberships.get(player.clientId()));
        }
        var recipientAccount = connections.account(recipient.clientId());
        if (recipientAccount == null || !recipientAccount.id().equals(friendId)) throw new IllegalArgumentException("This friend is offline.");
        var invite = invitations.add(room, player.clientId(), recipient.clientId(), inviter, recipientAccount);
        invitations.publish(invite, GameInviteStatus.PENDING, player.clientId(), requestId, "PvP invitation sent. Waiting for a response.");
    }

    public synchronized void answerInvite(PlayerIdentity player, java.util.UUID inviteId, boolean accept, String requestId) {
        invitations.expire();
        var account = connections.account(player.clientId());
        if (account == null) throw new IllegalArgumentException("Sign in to answer invitations.");
        var invite = invitations.forRecipient(inviteId, player.clientId(), account.id());
        if (!accept) {
            invitations.finish(invite, GameInviteStatus.DECLINED, player.clientId(), requestId, "PvP invitation declined.");
            return;
        }
        Room room = invite.room();
        var sender = connections.account(invite.sender());
        if (sender == null || !sender.id().equals(invite.inviter().id()) || rooms.get(room.code) != room
                || !room.code.equals(memberships.get(invite.sender())) || room.phase != RoomPhase.WAITING || room.match != null
                || room.players.size() >= 2 || memberships.containsKey(player.clientId())) {
            invitations.finish(invite, GameInviteStatus.INVALIDATED, player.clientId(), requestId,
                    "Invitation is no longer valid: player unavailable, room changed or room full.");
            return;
        }
        addPlayer(room, player, requestId);
        invitations.finish(invite, GameInviteStatus.ACCEPTED, player.clientId(), requestId, "Invitation accepted. Both admirals are in the waiting room.");
        invitations.invalidateRoom(room, "Room is now full. Invitation cancelled.");
        invitations.invalidateClient(player.clientId(), "Player joined another room. Invitation cancelled.");
    }

    private void create(PlayerIdentity player, Message message) {
        if (alreadyInRoom(player, message)) return;
        String code;
        do {
            StringBuilder builder = new StringBuilder(6);
            for (int i = 0; i < 6; i++) builder.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
            code = builder.toString();
        } while (rooms.containsKey(code));
        com.battleship.shared.game.GameRules rules;
        try {
            var payload = message.payload();
            // Parse enums explicitly: Gson maps unknown enum strings to null.
            var mode = payload.has("mode") ? com.battleship.shared.game.GameMode.valueOf(payload.get("mode").getAsString())
                    : com.battleship.shared.game.GameMode.CLASSIC;
            var turnRule = payload.has("turnRule") ? com.battleship.shared.game.TurnRule.valueOf(payload.get("turnRule").getAsString())
                    : com.battleship.shared.game.TurnRule.ALTERNATING;
            int count = 5;
            if (payload.has("shipCount")) {
                var value = payload.get("shipCount");
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException();
                count = value.getAsBigDecimal().intValueExact();
            }
            rules = new com.battleship.shared.game.GameRules(mode, count, turnRule);
        } catch (RuntimeException invalid) {
            error(player, message, "INVALID_ROOM_SETTINGS", "Choose Basic or Fleet designer, 3-5 ships and a valid turn rule.");
            return;
        }
        Room room = new Room(code, rules);
        room.players.put(player.clientId(), new RoomPlayer(player.clientId(), player.playerName(), false));
        rooms.put(code, room);
        memberships.put(player.clientId(), code);
        connections.send(player.clientId(), Message.reply(MessageType.ROOM_CREATED, message.requestId(), room.snapshot()));
        sendChatContext(room, player.clientId());
        invitations.invalidateClient(player.clientId(), "Player created another room. Invitation cancelled.");
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
        addPlayer(room, player, message.requestId());
        invitations.invalidateRoom(room, "Room is now full. Invitation cancelled.");
        invitations.invalidateClient(player.clientId(), "Player joined another room. Invitation cancelled.");
    }

    private void addPlayer(Room room, PlayerIdentity player, String requestId) {
        room.players.put(player.clientId(), new RoomPlayer(player.clientId(), player.playerName(), false));
        memberships.put(player.clientId(), room.code);
        RoomSnapshot snapshot = room.snapshot();
        connections.send(player.clientId(), Message.reply(MessageType.ROOM_JOINED, requestId, snapshot));
        sendChatContext(room, player.clientId());
        room.players.keySet().stream().filter(id -> !id.equals(player.clientId()))
                .forEach(id -> connections.send(id, Message.request(MessageType.PLAYER_JOINED, snapshot)));
        LOG.info("ROOM_JOINED code=" + room.code + " player=" + player.playerName());
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
            String whiteId = room.players.keySet().stream().skip(random.nextInt(2)).findFirst().orElseThrow();
            room.players.replaceAll((id, member) -> new RoomPlayer(id, member.playerName(), true,
                    id.equals(whiteId) ? Side.WHITE : Side.BLACK));
            startPlacement(room);
        }
    }

    private void startPlacement(Room room) {
        invitations.invalidateRoom(room, "Placement has started. Invitation cancelled.");
        room.phase = RoomPhase.PLACEMENT;
        room.chat = new RoomChat(ChatScope.MATCH, false);
        room.rematchRequests.clear();
        room.rematchDeclined = false;
        // A fresh match also discards the old deadline, shot history and winner.
        room.match = new MatchService(room.snapshot().players(), room.rules);
        room.historyId = null;
        room.historyFinished = false;
        room.accountIds = Map.of();
        broadcast(room, MessageType.START_PLACEMENT);
        room.players.keySet().forEach(id -> sendChatContext(room, id));
        LOG.info("START_PLACEMENT code=" + room.code);
    }

    private void rematch(PlayerIdentity player, Message message) {
        Room room = rooms.get(memberships.get(player.clientId()));
        if (room == null || !room.rematchState().available()) {
            error(player, message, "REMATCH_UNAVAILABLE", "Rematch requires two players and a finished match.");
            return;
        }
        if (message.type() == MessageType.REMATCH_DECLINE) {
            room.rematchRequests.clear();
            room.rematchDeclined = true;
        } else {
            room.rematchDeclined = false;
            room.rematchRequests.add(player.clientId());
        }
        publishRematch(room, player.clientId(), message.requestId());
        if (room.rematchRequests.size() == 2) startPlacement(room);
    }

    private void publishRematch(Room room, String actorId, String requestId) {
        var state = room.rematchState();
        for (String id : room.players.keySet()) connections.send(id, Message.reply(MessageType.REMATCH_UPDATED,
                id.equals(actorId) ? requestId : java.util.UUID.randomUUID().toString(), state));
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
        recordMatch(room);
        for (String id : room.players.keySet()) connections.send(id, Message.reply(event,
                id.equals(actorId) ? requestId : java.util.UUID.randomUUID().toString(), room.match.snapshot(id)));
        syncChat(room);
    }

    private void leave(String clientId, String requestId) {
        invitations.invalidateClient(clientId, "Player left the room. Invitation cancelled.");
        String code = memberships.remove(clientId);
        if (code == null) return;
        Room room = rooms.get(code);
        if (room.match != null && room.match.phase() == GamePhase.BATTLE && room.match.forfeit(clientId))
            publishMatch(room, MessageType.MATCH_UPDATED, null, null);
        boolean finished = room.match != null && room.match.phase() == GamePhase.GAME_OVER;
        room.players.remove(clientId);
        if (requestId != null) connections.send(clientId, Message.reply(MessageType.ROOM_LEFT, requestId, null));
        if (room.players.isEmpty()) {
            rooms.remove(code);
        } else if (finished) {
            room.rematchRequests.clear();
            room.rematchDeclined = true;
            publishRematch(room, null, null);
            broadcast(room, MessageType.PLAYER_LEFT);
        } else {
            room.phase = RoomPhase.WAITING;
            room.match = null;
            if (room.chat.scope != ChatScope.WAITING_ROOM) room.chat = new RoomChat(ChatScope.WAITING_ROOM, true);
            room.players.replaceAll((id, player) -> new RoomPlayer(id, player.playerName(), false));
            broadcast(room, MessageType.PLAYER_LEFT);
            room.players.keySet().forEach(id -> sendChatContext(room, id));
        }
        LOG.info("PLAYER_LEFT code=" + code + " player=" + clientId);
    }

    private void recordMatch(Room room) {
        if (room.historyId == null && room.match.phase() == GamePhase.BATTLE) {
            room.historyId = java.util.UUID.randomUUID();
            Map<String, java.util.UUID> accounts = new HashMap<>();
            var participants = new java.util.ArrayList<MatchRepository.Participant>();
            for (RoomPlayer player : room.players.values()) {
                var account = connections.account(player.clientId());
                if (account == null) throw new IllegalStateException("Room member is not authenticated");
                accounts.put(player.clientId(), account.id());
                participants.add(new MatchRepository.Participant(account.id(), player.side(), participants.size() + 1));
            }
            room.accountIds = Map.copyOf(accounts);
            history.start(room.historyId, java.time.Instant.now(), java.util.List.copyOf(participants));
        }
        if (room.historyId != null && !room.historyFinished && room.match.phase() == GamePhase.GAME_OVER) {
            room.historyFinished = true;
            history.finish(room.historyId, java.time.Instant.now(), room.match.endReason().name(),
                    room.match.winnerSlot(), room.match.totalTurns(), room.match.statistics(room.accountIds));
        }
    }

    private boolean chatAllowed(Room room) {
        return room.match == null ? room.phase == RoomPhase.WAITING : room.match.phase() == GamePhase.BATTLE;
    }

    private void sendChatContext(Room room, String clientId) {
        connections.send(clientId, Message.request(MessageType.CHAT_CONTEXT, room.chat.view(room.code)));
    }

    private void syncChat(Room room) {
        boolean writable = chatAllowed(room);
        if (room.chat.writable == writable) return;
        room.chat.writable = writable;
        room.players.keySet().forEach(id -> sendChatContext(room, id));
    }

    private void chat(PlayerIdentity player, Message message) {
        Room room = rooms.get(memberships.get(player.clientId()));
        var account = connections.account(player.clientId());
        if (room == null || !room.players.containsKey(player.clientId()) || account == null) {
            chatError(player, message, "NOT_IN_ROOM", "Join a room before sending a message."); return;
        }
        try {
            ChatSend request = message.payloadAs(ChatSend.class);
            if (request == null || !room.chat.id.equals(request.contextId())) {
                chatError(player, message, "CHAT_CONTEXT_CHANGED", "Chat belongs to an earlier room or match."); return;
            }
            if (!chatAllowed(room)) {
                chatError(player, message, "CHAT_READ_ONLY", "Chat is read-only until Battle starts, or after the match ends."); return;
            }
            String text = chat.accept(player.clientId(), request.text());
            var delivered = room.chat.message(account, text);
            for (String id : room.players.keySet()) connections.send(id, Message.reply(MessageType.CHAT_MESSAGE,
                    id.equals(player.clientId()) ? message.requestId() : java.util.UUID.randomUUID().toString(), delivered));
        } catch (com.google.gson.JsonParseException exception) {
            chatError(player, message, "INVALID_CHAT", "Malformed chat message.");
        } catch (IllegalArgumentException exception) {
            chatError(player, message, "INVALID_CHAT", exception.getMessage());
        } catch (IllegalStateException exception) {
            chatError(player, message, "CHAT_RATE_LIMIT", exception.getMessage());
        }
    }

    private void chatError(PlayerIdentity player, Message message, String code, String reason) {
        connections.send(player.clientId(), Message.reply(MessageType.CHAT_ERROR, message.requestId(), new RoomError(code, reason)));
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
