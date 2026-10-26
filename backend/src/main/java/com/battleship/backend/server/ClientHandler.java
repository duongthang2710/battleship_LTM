package com.battleship.backend.server;

import com.battleship.shared.protocol.ConnectRequest;
import com.battleship.shared.auth.*;
import com.battleship.shared.leaderboard.*;
import com.battleship.shared.protocol.ConnectResponse;
import com.battleship.shared.room.RoomError;
import com.battleship.shared.protocol.Message;
import com.battleship.shared.protocol.MessageCodec;
import com.battleship.shared.protocol.MessageType;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.logging.Logger;
import java.util.concurrent.ArrayBlockingQueue;

public final class ClientHandler implements Runnable, AutoCloseable {
    private static final Logger LOG = Logger.getLogger(ClientHandler.class.getName());
    private final Socket socket;
    private final ConnectionManager connections;
    private final ClientSession session = new ClientSession();
    private final ArrayBlockingQueue<Message> outgoing = new ArrayBlockingQueue<>(128);
    private volatile Thread writer;

    public ClientHandler(Socket socket, ConnectionManager connections) {
        this.socket = socket;
        this.connections = connections;
    }

    public boolean connected() { return !socket.isClosed(); }
    public String id() { return session.id(); }
    public AccountProfile account() { return session.account(); }

    @Override
    public void run() {
        try (socket;
             var input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
             var stream = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))) {
            writer = Thread.ofVirtual().name("server-writer-" + id()).start(() -> {
                try {
                    while (!socket.isClosed()) MessageCodec.write(stream, outgoing.take());
                } catch (IOException | InterruptedException exception) { close(); }
            });
            Message message;
            while ((message = MessageCodec.read(input)) != null) {
                if (message.type() == MessageType.DISCONNECT) break;
                if (session.player() == null && message.type() != MessageType.CONNECT) {
                    throw new IOException("CONNECT must be the first message");
                }
                switch (message.type()) {
                    case CONNECT -> {
                        if (session.player() != null) throw new IOException("Already connected");
                        ConnectRequest request = message.payloadAs(ConnectRequest.class);
                        String name = request.playerName();
                        if (name == null || name.isBlank() || name.length() > 32) {
                            throw new IOException("Player name must contain 1-32 characters");
                        }
                        session.connect(name.strip());
                        send(Message.reply(MessageType.CONNECT_OK,
                                message.requestId(), new ConnectResponse(session.player())));
                        LOG.info("CONNECT_OK player=" + session.player().playerName() + " id=" + id());
                    }
                    case PING -> {
                        send(Message.reply(MessageType.PONG, message.requestId(), null));
                        LOG.fine("PONG " + id());
                    }
                    case SEND_GAME_INVITE, ACCEPT_GAME_INVITE, DECLINE_GAME_INVITE -> gameInvite(message);
                    case FRIENDS_REQUEST -> friends(message);
                    case LEADERBOARD_REQUEST -> leaderboard(message);
                    case REGISTER, LOGIN, LOGOUT -> authenticate(message);
                    case CHAT_SEND, CREATE_ROOM, JOIN_ROOM, LEAVE_ROOM, READY, NOT_READY, SUBMIT_FLEET, ATTACK, REMATCH_REQUEST, REMATCH_DECLINE -> {
                        if (session.account() == null) send(Message.reply(MessageType.AUTH_FAILED, message.requestId(),
                                new AuthError("AUTH_REQUIRED", "Sign in before entering a PvP room.")));
                        else connections.rooms().handle(session.player(), message);
                    }
                    default -> send(Message.reply(MessageType.ERROR, message.requestId(),
                            new RoomError("INVALID_REQUEST", "Unsupported client message: " + message.type())));
                }
            }
        } catch (IOException | RuntimeException exception) {
            if (!socket.isClosed()) LOG.info("Connection ended " + id() + ": " + exception.getMessage());
        } finally {
            close();
            connections.remove(id());
        }
    }

    private void gameInvite(Message message) {
        if (session.account() == null) {
            send(Message.reply(MessageType.AUTH_FAILED, message.requestId(), new AuthError("AUTH_REQUIRED", "Sign in to invite friends.")));
            return;
        }
        try {
            if (message.type() == MessageType.SEND_GAME_INVITE) {
                var request = message.payloadAs(com.battleship.shared.room.SendGameInvite.class);
                if (request == null) throw new IllegalArgumentException("Choose a friend to invite.");
                // JDBC runs on this connection's worker, outside the room/timer lock.
                connections.friends().requireFriends(session.account().id(), request.friendId());
                connections.rooms().invite(session.player(), request.friendId(), message.requestId());
            } else {
                var request = message.payloadAs(com.battleship.shared.room.GameInviteResponse.class);
                if (request == null || request.inviteId() == null) throw new IllegalArgumentException("Missing invitation ID.");
                connections.rooms().answerInvite(session.player(), request.inviteId(),
                        message.type() == MessageType.ACCEPT_GAME_INVITE, message.requestId());
            }
        } catch (com.google.gson.JsonParseException | IllegalArgumentException exception) {
            String reason = exception instanceof com.google.gson.JsonParseException ? "Malformed invitation request." : exception.getMessage();
            send(Message.reply(MessageType.GAME_INVITE_ERROR, message.requestId(), new RoomError("INVITE_UNAVAILABLE", reason)));
        } catch (java.sql.SQLException exception) {
            LOG.warning("Could not verify friendship; SQL state=" + exception.getSQLState());
            send(Message.reply(MessageType.GAME_INVITE_ERROR, message.requestId(), new RoomError("DATABASE_UNAVAILABLE", "Could not verify friendship. Please retry.")));
        }
    }

    private void friends(Message message) {
        if (session.account() == null) {
            send(Message.reply(MessageType.AUTH_FAILED, message.requestId(), new AuthError("AUTH_REQUIRED", "Sign in to use Friends.")));
            return;
        }
        try {
            var request = message.payloadAs(com.battleship.shared.friends.FriendsRequest.class);
            var result = connections.friends().handle(session.account().id(), request);
            send(Message.reply(MessageType.FRIENDS_RESULT, message.requestId(), result));
            if (!request.action().listing()) connections.notifyFriends(java.util.List.of(session.account().id(), request.targetId()),
                    "Friends list or invitations updated.");
        } catch (com.google.gson.JsonParseException | IllegalArgumentException exception) {
            String reason = exception instanceof com.google.gson.JsonParseException ? "Malformed friends request." : exception.getMessage();
            send(Message.reply(MessageType.FRIENDS_ERROR, message.requestId(), new com.battleship.shared.friends.FriendsNotice(reason)));
        } catch (java.sql.SQLException exception) {
            LOG.warning("Friends request failed; SQL state=" + exception.getSQLState());
            send(Message.reply(MessageType.FRIENDS_ERROR, message.requestId(),
                    new com.battleship.shared.friends.FriendsNotice("Friends service unavailable. Please retry.")));
        }
    }

    private void leaderboard(Message message) {
        if (session.account() == null) {
            send(Message.reply(MessageType.AUTH_FAILED, message.requestId(), new AuthError("AUTH_REQUIRED", "Sign in to view rankings.")));
            return;
        }
        try {
            var page = connections.leaderboard().page(message.payloadAs(LeaderboardRequest.class));
            send(Message.reply(MessageType.LEADERBOARD_RESULT, message.requestId(), page));
        } catch (com.google.gson.JsonParseException | IllegalArgumentException exception) {
            send(Message.reply(MessageType.LEADERBOARD_ERROR, message.requestId(), new LeaderboardError("Invalid leaderboard sort or page.")));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            send(Message.reply(MessageType.LEADERBOARD_ERROR, message.requestId(), new LeaderboardError("Rankings unavailable. Please refresh.")));
        } catch (java.sql.SQLException | java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException
                | java.util.concurrent.RejectedExecutionException exception) {
            LOG.warning("Leaderboard request failed: " + exception.getClass().getSimpleName());
            send(Message.reply(MessageType.LEADERBOARD_ERROR, message.requestId(), new LeaderboardError("Could not load rankings. Please refresh.")));
        }
    }

    private void authenticate(Message message) {
        try {
            if (message.type() == MessageType.LOGOUT) {
                connections.rooms().disconnected(id());
                connections.auth().logout(id(), session.account());
                AccountProfile previous = session.account();
                session.authenticate(null);
                connections.presenceChanged(previous);
                send(Message.reply(MessageType.LOGOUT_OK, message.requestId(), null));
            } else if (session.account() != null) {
                send(Message.reply(MessageType.AUTH_FAILED, message.requestId(),
                        new AuthError("ALREADY_AUTHENTICATED", "Sign out before using another account.")));
            } else if (message.type() == MessageType.REGISTER) {
                AccountProfile account = connections.auth().register(message.payloadAs(RegisterRequest.class));
                send(Message.reply(MessageType.REGISTER_OK, message.requestId(), account));
            } else {
                AccountProfile account = connections.auth().login(id(), message.payloadAs(LoginRequest.class));
                session.authenticate(account);
                connections.presenceChanged(account);
                send(Message.reply(MessageType.AUTH_SUCCESS, message.requestId(), account));
            }
        } catch (com.google.gson.JsonParseException | IllegalArgumentException exception) {
            String reason = exception instanceof com.google.gson.JsonParseException ? "Malformed account request." : exception.getMessage();
            send(Message.reply(MessageType.AUTH_FAILED, message.requestId(), new AuthError("INVALID_CREDENTIALS", reason)));
        } catch (java.sql.SQLException exception) {
            LOG.warning("Account database request failed; SQL state=" + exception.getSQLState());
            send(Message.reply(MessageType.AUTH_FAILED, message.requestId(),
                    new AuthError("DATABASE_UNAVAILABLE", "Account service unavailable. Please try again.")));
        }
    }

    public void send(Message message) {
        // Never block another room/client on a slow socket. Disconnect an overloaded peer.
        if (!socket.isClosed() && !outgoing.offer(message)) close();
    }

    @Override
    public void close() {
        Thread activeWriter = writer;
        if (activeWriter != null) activeWriter.interrupt();
        try { socket.close(); }
        catch (IOException exception) { LOG.fine(exception.getMessage()); }
    }
}
