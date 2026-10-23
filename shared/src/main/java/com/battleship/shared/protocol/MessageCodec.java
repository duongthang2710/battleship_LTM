package com.battleship.shared.protocol;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class MessageCodec {
    private static final int MAX_FRAME_BYTES = 65_536;
    private static final Gson JSON = new Gson();

    private MessageCodec() {}

    public static Message read(DataInputStream input) throws IOException {
        int first = input.read();
        if (first == -1) return null;
        // Four-byte big-endian length. Partial headers/bodies are errors, not a clean EOF.
        int length = (first << 24) | (input.readUnsignedByte() << 16)
                | (input.readUnsignedByte() << 8) | input.readUnsignedByte();
        if (length < 1 || length > MAX_FRAME_BYTES) throw new IOException("Invalid frame length: " + length);
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) throw new EOFException("Truncated JSON frame");
        try {
            Message message = JSON.fromJson(new String(bytes, StandardCharsets.UTF_8), Message.class);
            if (message == null) throw new IOException("Empty message");
            return message;
        } catch (JsonParseException | IllegalArgumentException exception) {
            throw new IOException("Invalid JSON message", exception);
        }
    }

    public static void write(DataOutputStream output, Message message) throws IOException {
        byte[] bytes = JSON.toJson(message).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_FRAME_BYTES) throw new IOException("Message exceeds frame limit");
        output.writeInt(bytes.length);
        output.write(bytes);
        output.flush();
    }
}
