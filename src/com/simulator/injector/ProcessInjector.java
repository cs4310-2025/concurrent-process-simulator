package com.simulator.injector;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;


/**
 * A separate "spawner" process that injects new processes
 * into the running simulator via IPC.
 */
public class ProcessInjector {

    private final String workloadPath;

    public ProcessInjector(String workloadPath) {
        this.workloadPath = workloadPath;
    }

    public static void main(String[] args) {
        // Argument parsing
        String workloadPath = null;
        String ipcType = "pipe"; // Default to pipe for now

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--workload") && i + 1 < args.length) {
                workloadPath = args[i + 1];
                i++; // Skip next arg
            }
            // We will add --ipc parsing in a later milestone
        }

        if (workloadPath == null) {
            System.err.println("[Injector] ERROR: Missing required argument: --workload <FILE>");
            System.exit(1);
        }

        // Injection

        ProcessInjector injector = new ProcessInjector(workloadPath);
        
        System.err.println("[Injector] Starting injection with IPC: " + ipcType + 
                           ", Workload: " + injector.workloadPath);
        
        injector.runInjection();
        
        System.err.println("[Injector] Injection complete.");
    }

    /**
     * Reads the workload file and sends process data line-by-line
     * over stdout, respecting the arrival times.
     */
    private void runInjection() {
        long injectionStartTime = System.currentTimeMillis();
        long lastArrivalTime = 0;

        // Use the workloadPath field
        try (BufferedReader reader = new BufferedReader(new FileReader(this.workloadPath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue; // Skip comments and empty lines
                }

                long currentArrivalTime = 0;
                try {
                    // parts[1] is ArrivalTimeMs
                    currentArrivalTime = Long.parseLong(line.split(",")[1].trim());
                } catch (Exception e) {
                    System.err.println("[Injector] Skipping malformed line: " + line);
                    continue;
                }

                // Sleep to match the relative arrival time from the file
                long sleepTime = (injectionStartTime + currentArrivalTime) - (injectionStartTime + lastArrivalTime);
                if (sleepTime > 0) {
                    Thread.sleep(sleepTime);
                }
                
                sendProcessLine(line);
                lastArrivalTime = currentArrivalTime;
            }
        } catch (IOException e) {
            System.err.println("[Injector] ERROR: Could not read workload file '" + this.workloadPath + "': " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[Injector] ERROR: Injection sleep interrupted.");
        }

        // Signal to the simulator that no more processes are coming
        sendEOF();
    }

    /**
     * Sends the raw CSV string to stdout for the pipe.
     *
     * @param csvLine The raw CSV string to send.
     */
    private void sendProcessLine(String csvLine) {
        System.err.println("[Injector] Sending: " + csvLine);
        System.out.println(csvLine);
        System.out.flush(); // Force write to pipe
    }

    /**
     * Sends the "END" sentinel value.
     */
    private void sendEOF() {
        System.err.println("[Injector] Sending: END");
        System.out.println("END");
        System.out.flush();
    }
}
