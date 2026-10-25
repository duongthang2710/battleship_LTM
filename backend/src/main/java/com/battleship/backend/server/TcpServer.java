package com.battleship.backend.server;

import java.io.IOException;
import com.battleship.backend.database.Database;
import com.battleship.backend.auth.AuthService;
import com.battleship.backend.auth.UserRepository;
import com.battleship.backend.match.MatchRepository;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public final class TcpServer implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(TcpServer.class.getName());
    private final ServerSocket server;
    private final ConnectionManager connections;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();

    public TcpServer(int port) throws IOException, java.sql.SQLException {
        Database database = Database.fromEnvironment();
        database.migrate();
        server = new ServerSocket(port);
        connections = new ConnectionManager(new AuthService(new UserRepository(database)), new MatchRepository(database),
                new com.battleship.backend.leaderboard.LeaderboardRepository(database));
    }
    public int port() { return server.getLocalPort(); }

    public void start() throws IOException {
        LOG.info("Battleship TCP server listening on port " + server.getLocalPort());
        while (!server.isClosed()) {
            try {
                Socket socket = server.accept();
                socket.setTcpNoDelay(true);
                socket.setSoTimeout(45_000);
                ClientHandler handler = new ClientHandler(socket, connections);
                connections.add(handler);
                workers.submit(handler);
            } catch (SocketException exception) {
                if (!server.isClosed()) throw exception;
            }
        }
    }

    @Override
    public void close() {
        try { server.close(); }
        catch (IOException exception) { LOG.fine(exception.getMessage()); }
        connections.close();
        workers.shutdownNow();
    }
}
