package com.battleship.shared.protocol;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.Objects;
import java.util.UUID;

public record Message(MessageType type, String requestId, JsonObject payload) {
    private static final Gson JSON = new Gson();

    public Message {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(payload, "payload");
        if (requestId.isBlank()) throw new IllegalArgumentException("Empty requestId");
    }

    public static Message request(MessageType type, Object payload) {
        return reply(type, UUID.randomUUID().toString(), payload);
    }

    public static Message reply(MessageType type, String requestId, Object payload) {
        JsonObject body = payload == null ? new JsonObject() : JSON.toJsonTree(payload).getAsJsonObject();
        return new Message(type, requestId, body);
    }

    public <T> T payloadAs(Class<T> type) {
        return JSON.fromJson(payload, type);
    }
}
