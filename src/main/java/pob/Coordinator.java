package pob;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

public class Coordinator {

    private static final Logger logger = Logger.getLogger(Coordinator.class.getName());
    private static final int NUM_SERVERS = 6;
    private static final int[] SERVER_PORTS = {5000, 5001, 5002, 5003, 5004, 5005};
    private static final int TIMEOUT_MS = 5000; // 5 second timeout

    private final ExecutorService executorService;
    private final ConcurrentHashMap<Integer, ServerStatus> serverStatuses;

    public Coordinator() {
        this.executorService = Executors.newFixedThreadPool(NUM_SERVERS);
        this.serverStatuses = new ConcurrentHashMap<>();
        initializeLogging();
    }

    private void initializeLogging() {
        try {
            FileHandler fh = new FileHandler("coordinator.log");
            logger.addHandler(fh);
            SimpleFormatter formatter = new SimpleFormatter();
            fh.setFormatter(formatter);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean startTwoPhaseCommit(int value, int successPerc) {
        List<Future<Boolean>> prepareResults = new ArrayList<>();
        Map<Integer, Socket> connections = new HashMap<>();

        try {
            logger.info("Starting PREPARE phase for value: " + value);
            for (int port : SERVER_PORTS) {
                Future<Boolean> result = executorService.submit(() ->
                                                                    prepareServer(port, value, successPerc, connections));
                prepareResults.add(result);
            }

            boolean allPrepared = waitForAllResults(prepareResults);

            String command = allPrepared ? "COMMIT" : "ROLLBACK";
            logger.info("Starting " + command + " phase");

            // Send commit/rollback to all servers in parallel
            List<Future<Void>> commitResults = new ArrayList<>();
            for (Map.Entry<Integer, Socket> conn : connections.entrySet()) {
                Future<Void> result = executorService.submit(() -> {
                    sendCommand(conn.getValue(), command);
                    return null;
                });
                commitResults.add(result);
            }

            // Wait for all commits/rollbacks to complete
            waitForAllCommands(commitResults);

            return allPrepared;

        } catch (Exception e) {
            logger.severe("Transaction failed: " + e.getMessage());
            return false;
        } finally {
            // Close all connections
            connections.values().forEach(socket -> {
                try {
                    socket.close();
                } catch (IOException e) {
                    logger.warning("Error closing socket: " + e.getMessage());
                }
            });
        }
    }

    private boolean prepareServer(int port, int value, int successPerc,
                                  Map<Integer, Socket> connections) throws IOException {
        Socket socket = new Socket("localhost", port);
        connections.put(port, socket);

        try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            socket.setSoTimeout(TIMEOUT_MS);
            out.println("PREPARE:" + value + ":" + successPerc);
            String response = in.readLine();

            boolean success = "OK".equals(response);
            serverStatuses.put(port, new ServerStatus(port, success ? "PREPARED" : "FAILED"));

            return success;
        }
    }

    private void sendCommand(Socket socket, String command) throws IOException {
        try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            out.println(command);
        }
    }

    private boolean waitForAllResults(List<Future<Boolean>> results) {
        boolean allSuccess = true;
        for (Future<Boolean> result : results) {
            try {
                allSuccess &= result.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                logger.warning("Server prepare failed: " + e.getMessage());
                allSuccess = false;
            }
        }
        return allSuccess;
    }

    private void waitForAllCommands(List<Future<Void>> results) {
        for (Future<Void> result : results) {
            try {
                result.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                logger.warning("Server commit/rollback failed: " + e.getMessage());
            }
        }
    }

    public Map<Integer, ServerStatus> getServerStatuses() {
        return new HashMap<>(serverStatuses);
    }

    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }

    static class ServerStatus {

        final int port;
        final String status;

        ServerStatus(int port, String status) {
            this.port = port;
            this.status = status;
        }
    }
}