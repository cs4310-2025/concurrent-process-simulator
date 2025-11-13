package com.simulator.core;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * The main "Operating System" process.
 * This minimal version hardcodes a single FCFS core and pipe IPC
 * to prove the end-to-end communication works.
 */
public class CPUSimulator {

    private static long simulationStartTime;
    private final BlockingQueue<Process> readyQueue;
    private final List<Thread> coreThreads;
    
    // This flag signals to core threads that no new processes are coming.
    private volatile boolean ipcFinished = false;

    // Hardcoded settings for the initial implementation
    private final int coreCount = 1;
    private final String schedulerType = "FCFS";
    private final String ipcType = "pipe";

    public CPUSimulator() {
        // Use LinkedBlockingQueue for FCFS (FIFO order)
        this.readyQueue = new LinkedBlockingQueue<>();
        this.coreThreads = new ArrayList<>();
    }

    public static void main(String[] args) {
        // We will parse args in a later milestone
        simulationStartTime = System.currentTimeMillis();
        CPUSimulator simulator = new CPUSimulator();
        
        log("SIM", "Multi-Core Simulator Started. Cores: " + simulator.coreCount + 
                   ". Scheduler: " + simulator.schedulerType + ". IPC: " + simulator.ipcType);
        
        simulator.runSimulation();
    }

    private void runSimulation() {
        startIpcThread();
        startCoreThreads();
        waitForCompletion();
        
        log("SIM", "=== ALL PROCESSES COMPLETE ===");
    }

    /**
     * Starts the IPC listener thread.
     */
    private void startIpcThread() {
        // Hardcoded to Pipe logic for now
        Thread ipcThread = new Thread(new IpcPipeThread());
        ipcThread.setName("IPC-Thread");
        ipcThread.start();
    }

    /**
     * Starts the specified number of core worker threads.
     */
    private void startCoreThreads() {
        for (int i = 0; i < this.coreCount; i++) {
            Thread coreThread = new Thread(new CoreThread(i));
            coreThread.setName("Core-" + i);
            coreThreads.add(coreThread);
            coreThread.start();
        }
    }

    /**
     * Waits for all core threads to complete their work.
     */
    private void waitForCompletion() {
        for (Thread coreThread : coreThreads) {
            try {
                coreThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log("SIM-ERROR", "Main thread interrupted while waiting for cores.");
            }
        }
    }

    /**
     * System-wide logging utility. Prepends elapsed time and a tag.
     *
     * @param tag     The component logging the message (e.g., "SIM", "CORE-0").
     * @param message The log message.
     */
    public static void log(String tag, String message) {
        long elapsed = System.currentTimeMillis() - simulationStartTime;
        System.out.println("[" + elapsed + "ms] [" + tag + "] " + message);
    }

    // --- Inner Class for IPC Thread ---

    /**
     * A Runnable that reads from System.in (the pipe) and adds
     * processes to the shared readyQueue.
     */
    private class IpcPipeThread implements Runnable {
        @Override
        public void run() {
            log("IPC-PIPE", "IPC listener thread started, waiting for input...");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.equals("END")) {
                        log("IPC-PIPE", "Received END signal.");
                        ipcFinished = true;
                        break;
                    }
                    if (line.trim().isEmpty() || line.startsWith("#")) {
                        continue; // Skip empty lines or comments
                    }

                    Process p = Process.fromCSV(line);
                    
                    // We log the raw line since the Process object is minimal
                    log("IPC-PIPE", "New process arrived: " + line);
                    readyQueue.put(p);
                }
            } catch (Exception e) {
                log("IPC-PIPE-ERROR", "Error in IPC thread: " + e.getMessage());
            }
            log("IPC-PIPE", "IPC listener thread shutting down.");
        }
    }

    // --- Inner Class for Core Thread ---

    /**
     * A Runnable that simulates a single CPU core.
     * It pulls processes from the readyQueue and "executes" them.
     */
    private class CoreThread implements Runnable {
        private final int coreId;

        public CoreThread(int coreId) {
            this.coreId = coreId;
        }

        @Override
        public void run() {
            String tag = "CORE-" + this.coreId;
            log(tag, "Core thread started.");

            try {
                // Loop as long as the simulation is running OR there is work left
                while (!ipcFinished || !readyQueue.isEmpty()) {
                    // Poll with a timeout to prevent a hard block
                    Process p = readyQueue.poll(100, TimeUnit.MILLISECONDS);

                    if (p == null) {
                        // Queue was empty, loop again to re-check condition
                        continue;
                    }

                    log(tag, "START: Process (Burst: " + p.burstTime + "ms)");

                    // Simulate CPU work
                    Thread.sleep(p.burstTime);

                    log(tag, "END: Process (Burst: " + p.burstTime + "ms)");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log(tag, "Core thread interrupted, shutting down.");
            }
            log(tag, "Core thread finished.");
        }
    }
}
