package com.battleship.backend.server;

import java.net.InetAddress;

public final class ServerMain {
    private ServerMain() {}

    public static void main(String[] args) throws Exception {
        int port = args.length == 0 ? 2209 : Integer.parseInt(args[0]);
        InetAddress bindAddress = args.length < 2 ? null : InetAddress.getByName(args[1]);
        try (TcpServer server = new TcpServer(port, bindAddress)) {
            Runtime.getRuntime().addShutdownHook(new Thread(server::close, "server-shutdown"));
            server.start();
        }
    }
}
