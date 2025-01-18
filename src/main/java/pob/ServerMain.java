package pob;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;

public class ServerMain {

    private static TransactionStatus status = TransactionStatus.IDLE;
    private static Integer currentValue = null;
    private static boolean prepared = false;

    // BŁĘDY
    private static volatile boolean timeoutError = false;
    private static volatile boolean networkError = false;
    private static volatile boolean dataError = false;

    private static final Random random = new Random();

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: java pob.ServerMain <port>");
            System.exit(1);
        }

        int port = Integer.parseInt(args[0]);
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("Server starting on port " + port);

        while (true) {
            try {
                Socket coordinator = serverSocket.accept();
                handleConnection(coordinator, port);
            } catch (Exception e) {
                System.out.println("Error accepting connection: " + e.getMessage());
            }
        }
    }

    private static void handleConnection(Socket coordinator, int port) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(coordinator.getInputStream()));
            PrintWriter out = new PrintWriter(coordinator.getOutputStream(), true)) {

            if (networkError) {
                coordinator.close();
                return;
            }

            String line = in.readLine();
            if (line == null) {
                return;
            }

            if (line.startsWith("PREPARE:")) {
                handlePrepareCommand(line, port, out);
            } else if (line.equals("GET_STATUS")) {
                out.println(buildStatusResponse());
            } else if (line.startsWith("ERROR")) {
                handleErrorCommand(line, out);
            } else {
                handleOtherCommands(line, port, out);
            }

        } catch (Exception e) {
            System.out.println("Error handling connection on port " + port + ": " + e.getMessage());
        }
    }

    private static void handlePrepareCommand(String line, int port, PrintWriter out) {
        try {
            if (timeoutError) {
                System.out.println("Server " + port + ": Timeout error active, delaying response...");
                Thread.sleep(10000);
                return;
            }

            if (dataError) {
                System.out.println("Server " + port + ": Data error active, rejecting...");
                throw new IllegalArgumentException("Data format error");
            }

            String[] parts = line.split(":");
            if (parts.length != 3) {
                System.out.println("Server " + port + ": Invalid command format");
                throw new IllegalArgumentException("Invalid PREPARE command format");
            }

            int value = Integer.parseInt(parts[1]);
            int successPerc = Integer.parseInt(parts[2]);

            System.out.println("Server " + port + ": Processing PREPARE");
            System.out.println("Value: " + value);
            System.out.println("Success %: " + successPerc);

            int randomValue = random.nextInt(100);
            System.out.println("Random value generated: " + randomValue);
            System.out.println("Success threshold: " + successPerc);

            if (randomValue < successPerc &&
                !timeoutError && !networkError && !dataError) {
                prepared = true;
                status = TransactionStatus.PREPARE;
                currentValue = value;
                out.println("OK");
                System.out.println("Server " + port + ": Prepare SUCCESS");
            } else {
                prepared = false;
                status = TransactionStatus.ROLLBACK;
                out.println("NO");
                System.out.println("Server " + port + ": Prepare FAILED");
                if (randomValue >= successPerc) {
                    System.out.println("Reason: Random check failed (" + randomValue + " >= " + successPerc + ")");
                }
            }

        } catch (Exception e) {
            prepared = false;
            status = TransactionStatus.ROLLBACK;
            out.println("NO");
            System.out.println("Server " + port + ": Error in PREPARE: " + e.getMessage());
        }
    }

    private static void handleErrorCommand(String line, PrintWriter out) {
        switch (line) {
            case "ERROR1" -> {
                timeoutError = true;
                out.println("ERROR1_SET");
            }
            case "ERROR2" -> {
                networkError = true;
                out.println("ERROR2_SET");
            }
            case "ERROR3" -> {
                dataError = true;
                out.println("ERROR3_SET");
            }
            case "ERROR_CLEAR" -> {
                timeoutError = false;
                networkError = false;
                dataError = false;
                out.println("ERRORS_CLEARED");
            }
        }
    }

    private static void handleOtherCommands(String line, int port, PrintWriter out) {
        if (line.equals("COMMIT")) {
            if (prepared && !timeoutError && !networkError && !dataError) {
                status = TransactionStatus.COMMIT;
                System.out.println("Server on port " + port + " committed. Value: " + currentValue);
            }
        } else if (line.equals("ROLLBACK")) {
            status = TransactionStatus.ROLLBACK;
            prepared = false;
            System.out.println("Server on port " + port + " rolled back.");
        }
    }

    private static String buildStatusResponse() {
        return String.format(
            "STATUS:%s;ERR1=%s;ERR2=%s;ERR3=%s;VALUE=%s",
            status.name(),
            timeoutError,
            networkError,
            dataError,
            currentValue == null ? "null" : currentValue
        );
    }
}