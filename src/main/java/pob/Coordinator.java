package pob;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.*;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Coordinator {

    private static final Logger logger = LoggerFactory.getLogger(Coordinator.class);

    private static final int NUM_SERVERS = 6;

    private static final Semaphore semaphore = new Semaphore(1);

    // Porty serwerów
    private static final int[] SERVER_PORTS = {5000, 5001, 5002, 5003, 5004, 5005};

    public static void main(String[] args) {

        logger.info("Starting Coordinator with user-defined success percentage");

        Scanner scanner = new Scanner(System.in);
        while (true) {
            logger.info("\nEnter a value to propose (or 'exit' to quit):");
            String input = scanner.nextLine().trim();
            if ("exit".equalsIgnoreCase(input)) {
                logger.info("Exiting...");
                System.exit(0);
            }

            int value;
            try {
                value = Integer.parseInt(input);
            } catch (NumberFormatException e) {
                logger.info("Invalid input. Please enter a number or 'exit'.");
                continue;
            }

            logger.info("Enter success percentage (0-100):");
            String percInput = scanner.nextLine().trim();
            int successPerc;
            try {
                successPerc = Integer.parseInt(percInput);
                if (successPerc < 0 || successPerc > 100) {
                    logger.info("Percentage must be between 0 and 100.");
                    continue;
                }
            } catch (NumberFormatException e) {
                logger.info("Invalid percentage. Please enter a number between 0 and 100.");
                continue;
            }

            boolean success = startTwoPhaseCommit(value, successPerc);

            if (success) {
                logger.info("Transaction committed successfully!");
            } else {
                logger.info("Transaction rolled back due to failure.");
            }
        }
    }

    private static boolean startTwoPhaseCommit(int value, int successPerc) {
        try {
            semaphore.acquire();
            logger.info("Starting Two-Phase Commit for value: {} with successPerc={}", value, successPerc);

            boolean allAgreed = true;
            Socket[] sockets = new Socket[NUM_SERVERS];
            PrintWriter[] outs = new PrintWriter[NUM_SERVERS];
            BufferedReader[] ins = new BufferedReader[NUM_SERVERS];

            for (int i = 0; i < NUM_SERVERS; i++) {
                sockets[i] = new Socket("localhost", SERVER_PORTS[i]);
                outs[i] = new PrintWriter(sockets[i].getOutputStream(), true);
                ins[i] = new BufferedReader(new InputStreamReader(sockets[i].getInputStream()));
                outs[i].println("PREPARE:" + value + ":" + successPerc);
                String response = ins[i].readLine();
                if (!"OK".equals(response)) {
                    allAgreed = false;
                }
            }

            String finalCommand = allAgreed ? "COMMIT" : "ROLLBACK";
            for (int i = 0; i < NUM_SERVERS; i++) {
                outs[i].println(finalCommand);
            }

            for (int i = 0; i < NUM_SERVERS; i++) {
                sockets[i].close();
            }

            return allAgreed;
        } catch (InterruptedException | IOException e) {
            logger.error("Error during 2PC: {}", e.getMessage());
            return false;
        } finally {
            semaphore.release();
        }
    }
}