package pob;

import java.util.List;
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.Timer;

public class MainGuiApp extends JFrame {

    private static final int NUM_SERVERS = 6;
    private static final int[] SERVER_PORTS = {5000, 5001, 5002, 5003, 5004, 5005};
    private static final Color PREPARE_COLOR = new Color(255, 140, 0); // Pomarańczowy
    private static final Color COMMIT_COLOR = new Color(0, 150, 0);    // Zielony
    private static final Color ROLLBACK_COLOR = Color.RED;             // Czerwony
    private static final Color IDLE_COLOR = Color.BLACK;               // Czarny
    private static final int SERVER_TIMEOUT = 5000; // 5 sekund timeout

    private JTextField valueField;
    private JTextField successPercField;
    private List<ServerPanel> serverPanels;
    private JButton startTransactionButton;

    // Liczniki statystyk
    private int successCounter = 0;
    private int rollbackCounter = 0;
    private Map<String, Integer> errorCounters = new HashMap<>() {{
        put("ERROR1", 0); // Timeout
        put("ERROR2", 0); // Network
        put("ERROR3", 0); // Data
    }};

    // Etykiety statystyk
    private JLabel successCounterLabel;
    private JLabel rollbackCounterLabel;
    private JLabel timeoutErrorCounterLabel;
    private JLabel networkErrorCounterLabel;
    private JLabel dataErrorCounterLabel;

    private Timer refreshTimer;

    public MainGuiApp() {
        super("Two-Phase Commit Monitor");
        initializeUI();
    }

    private void initializeUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        JPanel mainContent = new JPanel(new BorderLayout(10, 10));
        mainContent.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        mainContent.add(createTopPanel(), BorderLayout.NORTH);
        mainContent.add(createServersPanel(), BorderLayout.CENTER);
        mainContent.add(createStatusPanel(), BorderLayout.SOUTH);

        add(mainContent);

        setSize(1000, 600);
        setLocationRelativeTo(null);

        refreshTimer = new Timer(500, e -> refreshAllServerStatuses());
        refreshTimer.start();
    }

    private JPanel createTopPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder("Transaction Control"),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));

        panel.add(new JLabel("Value:"));
        valueField = new JTextField("20", 5);
        panel.add(valueField);

        panel.add(new JLabel("Success %:"));
        successPercField = new JTextField("100", 5);
        panel.add(successPercField);

        startTransactionButton = new JButton("Start 2PC");
        startTransactionButton.setBackground(new Color(100, 180, 100));
        startTransactionButton.setForeground(Color.WHITE);
        startTransactionButton.addActionListener(e -> startTransaction());
        panel.add(startTransactionButton);

        return panel;
    }

    private JPanel createServersPanel() {
        JPanel wrapperPanel = new JPanel(new BorderLayout());
        wrapperPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder("Servers Status"),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));

        JPanel gridPanel = new JPanel(new GridLayout(NUM_SERVERS, 1, 5, 5));
        serverPanels = new ArrayList<>();

        for (int i = 0; i < NUM_SERVERS; i++) {
            ServerPanel sp = new ServerPanel("Server " + i, SERVER_PORTS[i]);
            serverPanels.add(sp);
            gridPanel.add(sp);
        }

        wrapperPanel.add(new JScrollPane(gridPanel));
        return wrapperPanel;
    }

    private JPanel createStatusPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 3, 10, 5));
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder("Statistics"),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));

        successCounterLabel = new JLabel("Successful commits: 0", SwingConstants.CENTER);
        rollbackCounterLabel = new JLabel("Total rollbacks: 0", SwingConstants.CENTER);
        panel.add(successCounterLabel);
        panel.add(rollbackCounterLabel);
        panel.add(new JLabel());

        timeoutErrorCounterLabel = new JLabel("Timeout errors: 0", SwingConstants.CENTER);
        networkErrorCounterLabel = new JLabel("Network errors: 0", SwingConstants.CENTER);
        dataErrorCounterLabel = new JLabel("Data errors: 0", SwingConstants.CENTER);
        panel.add(timeoutErrorCounterLabel);
        panel.add(networkErrorCounterLabel);
        panel.add(dataErrorCounterLabel);

        return panel;
    }

    private class ServerPanel extends JPanel {

        private final String serverName;
        private final int serverPort;
        private final JLabel statusLabel;
        private final Map<String, JToggleButton> errorButtons;
        private final JProgressBar progressBar;
        private Timer networkRecoveryTimer;
        private boolean isNetworkError = false;

        public ServerPanel(String serverName, int serverPort) {
            this.serverName = serverName;
            this.serverPort = serverPort;
            this.errorButtons = new HashMap<>();

            setLayout(new BorderLayout(5, 0));
            setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEtchedBorder(),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)
            ));

            JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            statusPanel.add(new JLabel(String.format("%-12s (port: %d)", serverName, serverPort)));

            statusLabel = new JLabel("PREPARE");
            statusLabel.setFont(new Font("Arial", Font.BOLD, 12));
            statusLabel.setForeground(PREPARE_COLOR);
            statusLabel.setPreferredSize(new Dimension(80, 20));
            statusPanel.add(statusLabel);

            progressBar = new JProgressBar();
            progressBar.setPreferredSize(new Dimension(100, 20));
            progressBar.setStringPainted(true);
            progressBar.setString("");
            statusPanel.add(progressBar);

            add(statusPanel, BorderLayout.CENTER);
            add(createErrorControlPanel(), BorderLayout.EAST);
        }

        private JPanel createErrorControlPanel() {
            JPanel errorPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

            String[][] errorConfigs = {
                {"Timeout", "ERROR1", "Symuluje timeout serwera (10s opóźnienie)"},
                {"Network", "ERROR2", "Symuluje błąd sieci (server offline na 10s)"},
                {"Data", "ERROR3", "Symuluje błąd danych"}
            };

            for (String[] config : errorConfigs) {
                JToggleButton errorButton = new JToggleButton(config[0]);
                errorButton.setToolTipText(config[2]);
                errorButton.setPreferredSize(new Dimension(80, 25));
                errorButton.addActionListener(e -> toggleError(config[1], errorButton.isSelected()));
                errorButtons.put(config[1], errorButton);
                errorPanel.add(errorButton);
            }

            return errorPanel;
        }

        private void toggleError(String errorType, boolean enabled) {
            try (Socket socket = new Socket("localhost", serverPort);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                socket.setSoTimeout(SERVER_TIMEOUT);

                if (errorType.equals("ERROR2") && enabled) {
                    handleNetworkError();
                } else {
                    out.println(enabled ? errorType : "ERROR_CLEAR");
                    String response = in.readLine();

                    if (enabled) {
                        updateStatistics(errorType);
                    }
                }

                refreshStatus();

            } catch (IOException ex) {
                setStatus("OFFLINE", Color.GRAY);
            }
        }

        private void handleNetworkError() {
            isNetworkError = true;
            setStatus("OFFLINE", Color.GRAY);
            progressBar.setString("Server offline");
            updateStatistics("ERROR2");

            if (networkRecoveryTimer != null && networkRecoveryTimer.isRunning()) {
                networkRecoveryTimer.restart();
            } else {
                networkRecoveryTimer = new Timer(10000, e -> {
                    isNetworkError = false;
                    ((Timer) e.getSource()).stop();
                    errorButtons.get("ERROR2").setSelected(false);
                    try (Socket socket = new Socket("localhost", serverPort);
                        PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                        out.println("ERROR_CLEAR");
                        setStatus("PREPARE", PREPARE_COLOR);
                        progressBar.setString("");
                    } catch (IOException ex) {
                        setStatus("OFFLINE", Color.GRAY);
                    }
                });
                networkRecoveryTimer.setRepeats(false);
                networkRecoveryTimer.start();
            }
        }

        public void setProgress(int progress, String message) {
            progressBar.setValue(progress);
            progressBar.setString(message);
        }

        public void setStatus(String status, Color color) {
            statusLabel.setText(status);
            statusLabel.setForeground(color);
        }

        public void refreshStatus() {
            if (isNetworkError) {
                setStatus("OFFLINE", Color.GRAY);
                return;
            }

            try (Socket socket = new Socket("localhost", serverPort);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                socket.setSoTimeout(SERVER_TIMEOUT);
                out.println("GET_STATUS");
                String response = in.readLine();
                if (response != null && response.startsWith("STATUS:")) {
                    updateStatusFromResponse(response.substring(7));
                }
            } catch (IOException e) {
                setStatus("OFFLINE", Color.GRAY);
            }
        }

        private void updateStatusFromResponse(String status) {
            Map<String, String> statusMap = parseStatusResponse(status);
            String mainStatus = statusMap.get("status");

            if (mainStatus != null) {
                switch (mainStatus) {
                    case "PREPARE" -> setStatus("PREPARE", PREPARE_COLOR);
                    case "COMMIT" -> setStatus("COMMIT", COMMIT_COLOR);
                    case "ROLLBACK" -> setStatus("ROLLBACK", ROLLBACK_COLOR);
                    case "IDLE" -> setStatus("IDLE", IDLE_COLOR);
                }
            }

            updateErrorButtons(statusMap);
        }

        private Map<String, String> parseStatusResponse(String status) {
            Map<String, String> statusMap = new HashMap<>();
            for (String part : status.split(";")) {
                String[] keyValue = part.split("=");
                if (keyValue.length == 2) {
                    statusMap.put(keyValue[0].toLowerCase(), keyValue[1]);
                }
            }
            return statusMap;
        }

        private void updateErrorButtons(Map<String, String> statusMap) {
            errorButtons.get("ERROR1").setSelected("true".equals(statusMap.get("err1")));
            errorButtons.get("ERROR2").setSelected("true".equals(statusMap.get("err2")));
            errorButtons.get("ERROR3").setSelected("true".equals(statusMap.get("err3")));
        }
    }

    private void updateStatistics(String type) {
        SwingUtilities.invokeLater(() -> {
            switch (type) {
                case "SUCCESS" -> {
                    successCounter++;
                    successCounterLabel.setText("Successful commits: " + successCounter);
                }
                case "ROLLBACK" -> {
                    rollbackCounter++;
                    rollbackCounterLabel.setText("Total rollbacks: " + rollbackCounter);
                }
                case "ERROR1", "ERROR2", "ERROR3" -> {
                    errorCounters.put(type, errorCounters.get(type) + 1);
                    updateErrorCounters();
                }
            }
        });
    }

    private void updateErrorCounters() {
        timeoutErrorCounterLabel.setText("Timeout errors: " + errorCounters.get("ERROR1"));
        networkErrorCounterLabel.setText("Network errors: " + errorCounters.get("ERROR2"));
        dataErrorCounterLabel.setText("Data errors: " + errorCounters.get("ERROR3"));
    }

    private void startTransaction() {
        startTransactionButton.setEnabled(false);

        int value;
        int successPerc;

        try {
            value = Integer.parseInt(valueField.getText());
            successPerc = Integer.parseInt(successPercField.getText());
            if (successPerc < 0 || successPerc > 100) {
                throw new IllegalArgumentException("Success percentage must be between 0 and 100");
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Invalid number format!");
            startTransactionButton.setEnabled(true);
            return;
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage());
            startTransactionButton.setEnabled(true);
            return;
        }

        serverPanels.forEach(sp -> {
            sp.setStatus("IDLE", IDLE_COLOR);
            sp.setProgress(0, "");
        });

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                executeTwoPhaseCommit(value, successPerc);
                return null;
            }

            @Override
            protected void done() {
                startTransactionButton.setEnabled(true);
            }
        }.execute();
    }

    private void executeTwoPhaseCommit(int value, int successPerc) {
        try {
            boolean transactionSuccessful = true;

            for (int i = 0; i < NUM_SERVERS && transactionSuccessful; i++) {
                final int serverIndex = i;
                ServerPanel currentPanel = serverPanels.get(i);

                SwingUtilities.invokeLater(() -> {
                    currentPanel.setProgress(30, "Preparing...");
                    currentPanel.setStatus("PREPARE", PREPARE_COLOR);
                });

                Thread.sleep(500);

                boolean serverPrepared = prepareServer(i, value, successPerc);

                if (serverPrepared) {
                    SwingUtilities.invokeLater(() -> {
                        currentPanel.setProgress(100, "Committed");
                        currentPanel.setStatus("COMMIT", COMMIT_COLOR);
                    });
                } else {
                    transactionSuccessful = false;
                    int finalI = i;
                    SwingUtilities.invokeLater(() -> {
                        currentPanel.setProgress(100, "Rolled back");
                        currentPanel.setStatus("ROLLBACK", ROLLBACK_COLOR);

                        // Rollback previous servers
                        for (int j = 0; j < finalI; j++) {
                            ServerPanel prevPanel = serverPanels.get(j);
                            prevPanel.setStatus("ROLLBACK", ROLLBACK_COLOR);
                            prevPanel.setProgress(100, "Rolled back");
                        }
                    });

                    for (int j = 0; j <= i; j++) {
                        rollbackServer(j);
                    }

                    updateStatistics("ROLLBACK");
                }

                Thread.sleep(500);
            }

            if (transactionSuccessful) {
                updateStatistics("SUCCESS");
            }

            // Reset progress bars after completion
            Thread.sleep(1000);
            SwingUtilities.invokeLater(() -> {
                for (ServerPanel panel : serverPanels) {
                    panel.setProgress(0, "");
                    panel.setStatus("PREPARE", PREPARE_COLOR);
                }
            });

        } catch (Exception ex) {
            ex.printStackTrace();
            updateStatistics("ROLLBACK");
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(
                    this,
                    "Transaction error: " + ex.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE
                );
            });
        }
    }

    private boolean prepareServer(int serverIndex, int value, int successPerc) {
        try (Socket socket = new Socket("localhost", SERVER_PORTS[serverIndex]);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            socket.setSoTimeout(SERVER_TIMEOUT);
            out.println("PREPARE:" + value + ":" + successPerc);
            String response = in.readLine();
            return "OK".equals(response);

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void rollbackServer(int serverIndex) {
        try (Socket socket = new Socket("localhost", SERVER_PORTS[serverIndex]);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            socket.setSoTimeout(SERVER_TIMEOUT);
            out.println("ROLLBACK");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void refreshAllServerStatuses() {
        serverPanels.forEach(ServerPanel::refreshStatus);
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {
            MainGuiApp app = new MainGuiApp();
            app.setVisible(true);
        });
    }
}