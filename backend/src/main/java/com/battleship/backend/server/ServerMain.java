package com.battleship.backend.server;


public final class ServerMain {
    private ServerMain() {}

    public static void main(String[] args) throws Exception {
        int port = args.length == 0 ? 2209 : Integer.parseInt(args[0]);
        try (TcpServer server = new TcpServer(port)) {
            Runtime.getRuntime().addShutdownHook(new Thread(server::close, "server-shutdown"));
            server.start();
        }
    }
}
