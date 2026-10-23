package com.battleship.frontend.network;

import com.battleship.shared.protocol.Message;
import com.battleship.shared.protocol.MessageCodec;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public final class NetworkClient implements AutoCloseable {
    private final Socket socket = new Socket();
    private final MessageHandler handler;
    private DataOutputStream output;

    public NetworkClient(MessageHandler handler) { this.handler = handler; }

    public void connectAndRead(String host, int port, Message connectMessage) {
        String reason = "Server closed the connection";
        try {
            socket.connect(new InetSocketAddress(host, port), 3_000);
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(30_000);
            var input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            synchronized (this) {
                output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                send(connectMessage);
            }
            Message message;
            while ((message = MessageCodec.read(input)) != null) {
                if (message.type() == com.battleship.shared.protocol.MessageType.DISCONNECT) break;
                handler.onMessage(message);
            }
        } catch (IOException | RuntimeException exception) {
            reason = exception.getMessage();
        } finally {
            close();
            handler.onDisconnected(reason);
        }
    }

    public synchronized void send(Message message) throws IOException {
        if (output == null || socket.isClosed()) throw new IOException("Not connected");
        MessageCodec.write(output, message);
    }

    @Override
    public void close() {
        // Closing the socket also interrupts a blocked reader; never wait for its send lock.
        try { socket.close(); }
        catch (IOException ignored) { }
    }
}
