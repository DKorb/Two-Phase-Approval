package pob;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;


public class ServerMain {

    private static TransactionStatus status = TransactionStatus.ROLLBACK;

    private static Integer currentValue = null;

    private static boolean prepared = false;

    private static final Random random = new Random();

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: java ServerMain <port>");
            System.exit(1);
        }

        int port = Integer.parseInt(args[0]);
        System.out.println("Server starting on port " + port);
        ServerSocket serverSocket = new ServerSocket(port);

        while (true) {
            System.out.println("Server on port " + port + " waiting for connection...");
            Socket coordinator = serverSocket.accept();
            System.out.println("Coordinator connected on port " + port);

            try (
                    BufferedReader in = new BufferedReader(new InputStreamReader(coordinator.getInputStream()));
                    PrintWriter out = new PrintWriter(coordinator.getOutputStream(), true)
            ) {

                String line = in.readLine();
                if (line != null && line.startsWith("PREPARE:")) {
                    String[] parts = line.split(":");
                    int value = Integer.parseInt(parts[1]);
                    int successPerc = Integer.parseInt(parts[2]);

                    // Losujemy czy się uda czy nie
                    if (random.nextInt(100) < successPerc) {
                        // Sukces
                        prepared = true;
                        status = TransactionStatus.PREPARE;
                        System.out.println("Server on port " + port + " prepared for value: " + value + " with successPerc=" + successPerc);
                        out.println("OK");
                    } else {
                        // Porażka
                        prepared = false;
                        status = TransactionStatus.ROLLBACK;
                        System.out.println("Server on port " + port + " failed to prepare for value: " + value + " with successPerc=" + successPerc);
                        out.println("NO");
                    }
                }

                line = in.readLine();
                if (line != null) {
                    if (line.equals("COMMIT")) {
                        if (prepared && status == TransactionStatus.PREPARE) {
                            status = TransactionStatus.COMMIT;
                            currentValue = (currentValue == null) ? 1 : currentValue + 1;
                            System.out.println("Server on port " + port + " committed. New value: " + currentValue);
                        }
                    } else if (line.equals("ROLLBACK")) {
                        if (prepared) {
                            status = TransactionStatus.ROLLBACK;
                            System.out.println("Server on port " + port + " rolled back.");
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("Error handling coordinator on port " + port + ": " + e.getMessage());
            }
        }
    }
}