package com.simulator.injector;

import java.io.BufferedReader;
import java.io.FileReader;

/**
 * Process injector: reads a workload file and sends processes to the simulator via IPC.
 * Respects arrival times by sleeping between injections.
 */
public class ProcessInjector {
    private final String workloadPath;

    public ProcessInjector(String workloadPath) {
        this.workloadPath = workloadPath;
    }

    public static void main(String[] args) throws Exception {
        String workloadPath = null;

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--workload")) {
                workloadPath = args[++i];
            }
        }

        if (workloadPath == null) {
            System.err.println("Usage: java ProcessInjector --workload <file>");
            System.exit(1);
        }

        ProcessInjector injector = new ProcessInjector(workloadPath);
        System.err.println("[Injector] Starting injection from: " + workloadPath);
        injector.inject();
        System.err.println("[Injector] Injection complete");
    }

    /**
     * Reads workload file and sends processes via stdout (pipe).
     * Sleeps between injections to match arrival times specified in workload.
     */
    private void inject() throws Exception {
        long injectionStart = System.currentTimeMillis();
        long lastArrivalTime = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader(workloadPath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                // Parse arrival time from CSV: Name,ArrivalTimeMs,BurstTimeMs,Priority
                String[] parts = line.split(",");
                long arrivalTime = Long.parseLong(parts[1].trim());

                // Sleep to match relative arrival time
                long sleepTime = arrivalTime - lastArrivalTime;
                if (sleepTime > 0) {
                    Thread.sleep(sleepTime);
                }

                send(line);
                lastArrivalTime = arrivalTime;
            }
        }

        // Signal end of injection
        System.out.println("END");
        System.out.flush();
    }

    private void send(String csvLine) {
        System.err.println("[Injector] Sending: " + csvLine);
        System.out.println(csvLine);
        System.out.flush();
    }
}
