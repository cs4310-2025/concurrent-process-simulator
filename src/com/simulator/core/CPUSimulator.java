package com.simulator.core;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

public class CPUSimulator {

    private static long simulationStartTime;

    // === Configurable Settings ===
    private final int coreCount;
    private final String schedulerType;
    private final String ipcType;
    // Optional file paths
    private final String shmFilePath;
    private final String outputFilePath;

    // === Core Components ===
    private final ReadyQueue readyQueue;
    private final List<Thread> coreThreads;
    private final Queue<Process> completedProcesses; // Thread-safe queue for stats
    
    private volatile boolean ipcFinished = false;
    
    // === Statistics Metrics ===
    // These must be accessed in a synchronized block
    private long firstProcessStartTime = -1;
    private long lastProcessEndTime = -1;
    private final Object timeLock = new Object();

    public CPUSimulator(int coreCount, String schedulerType, String ipcType, String shmFilePath, String outputFilePath) {
        this.coreCount = coreCount;
        this.schedulerType = schedulerType;
        this.ipcType = ipcType;
        this.shmFilePath = shmFilePath;
        this.outputFilePath = outputFilePath;
        
        this.readyQueue = createReadyQueue(schedulerType);
        this.coreThreads = new ArrayList<>();
        this.completedProcesses = new ConcurrentLinkedQueue<>();
    }

    public static void main(String[] args) {
        // === Argument Parsing ===
        int cores = -1;
        String scheduler = null;
        String ipc = null;
        String shmFile = "./cpu_sim_shm.dat"; // Default
        String outputFile = null;

        try {
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--cores":
                        cores = Integer.parseInt(args[++i]);
                        break;
                    case "--scheduler":
                        scheduler = args[++i].toUpperCase();
                        break;
                    case "--ipc":
                        ipc = args[++i].toLowerCase();
                        break;
                    case "--shm-file":
                        shmFile = args[++i];
                        break;
                    case "--output":
                        outputFile = args[++i];
                        break;
                    default:
                        System.err.println("Unknown argument: " + args[i]);
                }
            }

            if (cores == -1 || scheduler == null || ipc == null) {
                printUsageAndExit();
            }
            if (!scheduler.equals("FCFS") && !scheduler.equals("SJF") && !scheduler.equals("PRIORITY")) {
                System.err.println("Invalid scheduler type. Must be FCFS, SJF, or Priority.");
                printUsageAndExit();
            }
            if (!ipc.equals("pipe") && !ipc.equals("shm")) {
                System.err.println("Invalid IPC type. Must be pipe or shm.");
                printUsageAndExit();
            }

        } catch (Exception e) {
            System.err.println("Error parsing arguments: " + e.getMessage());
            printUsageAndExit();
        }

        // === Simulation Start ===
        simulationStartTime = System.currentTimeMillis();
        CPUSimulator simulator = new CPUSimulator(cores, scheduler, ipc, shmFile, outputFile);
        
        log("SIM", "Multi-Core Simulator Started. Cores: " + simulator.coreCount + 
                   ". Scheduler: " + simulator.schedulerType + ". IPC: " + simulator.ipcType);
        
        simulator.runSimulation();
    }

    private static void printUsageAndExit() {
        System.err.println("Usage: java com.simulator.core.CPUSimulator --cores <N> --scheduler <TYPE> --ipc <TYPE> [--shm-file <PATH>] [--output <FILE>]");
        System.err.println("  --scheduler <TYPE>: FCFS, SJF, Priority");
        System.err.println("  --ipc <TYPE>:       pipe, shm");
        System.exit(1);
    }
    
    /**
     * Factory method to create the correct ReadyQueue implementation
     */
    private ReadyQueue createReadyQueue(String schedulerType) {
        switch (schedulerType) {
            case "SJF":
                log("SIM", "Using SJF scheduler (sorting by burst time).");
                return new SJFQueue();
            case "PRIORITY":
                log("SIM", "Using Priority scheduler (sorting by priority value).");
                return new PriorityBasedQueue();
            case "FCFS":
            default:
                log("SIM", "Using FCFS scheduler (FIFO order).");
                return new FCFSQueue();
        }
    }

    private void runSimulation() {
        startIpcThread();
        startCoreThreads();
        waitForCompletion();
        
        log("SIM", "=== ALL PROCESSES COMPLETE ===");
        printFinalStatistics();
        
        if (outputFilePath != null) {
            // We will implement this in a future milestone
            log("SIM", "CSV output file specified (implementation pending): " + outputFilePath);
        }
    }

    private void startIpcThread() {
        // We only support pipe for now
        if (!ipcType.equals("pipe")) {
            log("SIM-ERROR", "IPC type '" + ipcType + "' is not yet supported.");
            return;
        }
        
        Thread ipcThread = new Thread(new IpcPipeThread());
        ipcThread.setName("IPC-Thread");
        ipcThread.start();
    }

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
     * Calculates and prints all final summary statistics
     * as specified in the README.
     */
    private void printFinalStatistics() {
        if (completedProcesses.isEmpty()) {
            log("SIM", "No processes were completed. No statistics to show.");
            return;
        }

        int totalProcesses = completedProcesses.size();
        long totalWaitTime = 0;
        long totalTurnaroundTime = 0;

        for (Process p : completedProcesses) {
            totalWaitTime += p.getWaitTime();
            totalTurnaroundTime += p.getTurnaroundTime();
        }

        double avgWaitTime = (double) totalWaitTime / totalProcesses;
        double avgTurnaroundTime = (double) totalTurnaroundTime / totalProcesses;

        // Calculate total execution time and throughput
        long executionTimeMs = -1;
        double throughput = 0;
        
        synchronized (timeLock) {
            if (firstProcessStartTime != -1 && lastProcessEndTime != -1) {
                executionTimeMs = lastProcessEndTime - firstProcessStartTime;
            }
        }

        if (executionTimeMs > 0) {
            double executionTimeSec = executionTimeMs / 1000.0;
            throughput = totalProcesses / executionTimeSec;
            log("SIM", String.format("Total Execution Time: %.2fs", executionTimeSec));
            log("SIM", String.format("Throughput: %.2f processes/sec", throughput));
        } else {
            log("SIM", "Total Execution Time: < 1ms (or no processes run)");
            log("SIM", "Throughput: N/A");
        }

        log("SIM", String.format("Avg. Wait Time: %.2fms", avgWaitTime));
        log("SIM", String.format("Avg. Turnaround Time: %.2fms", avgTurnaroundTime));
    }

    /**
     * System-wide logging utility.
     */
    public static void log(String tag, String message) {
        long elapsed = System.currentTimeMillis() - simulationStartTime;
        System.out.println("[" + elapsed + "ms] [" + tag + "] " + message);
    }
    
    // === Inner Class for IPC Thread ===

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
                        continue;
                    }

                    try {
                        Process p = Process.fromCSV(line);
                        p.arrivalTime = System.currentTimeMillis(); // Set arrival time *now*
                        
                        readyQueue.put(p);
                        log("IPC-PIPE", "New process " + p.name + " (Burst: " + p.burstTime + "ms, Prio: " + p.priority + ") arrived.");
                    
                    } catch (NumberFormatException e) {
                        log("IPC-PIPE-ERROR", "Skipping malformed process line: " + line);
                    }
                }
            } catch (Exception e) {
                if (!ipcFinished) {
                    log("IPC-PIPE-ERROR", "Error in IPC thread: " + e.getMessage());
                }
            }
            log("IPC-PIPE", "IPC listener thread shutting down.");
        }
    }

    // === Inner Class for Core Thread ===

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
                while (!ipcFinished || !readyQueue.isEmpty()) {
                    Process p = readyQueue.poll(100, TimeUnit.MILLISECONDS);

                    if (p == null) {
                        // Queue was empty, loop again to re-check condition
                        continue;
                    }

                    // === Full process logging and stats ===
                    p.startTime = System.currentTimeMillis();
                    
                    // Update global simulation start time (thread-safe)
                    synchronized (timeLock) {
                        if (firstProcessStartTime == -1) {
                            firstProcessStartTime = p.startTime;
                        }
                    }
                    
                    long waitTime = p.getWaitTime();
                    log(tag, "START: " + p.name + " (Wait: " + waitTime + "ms)");
                    
                    Thread.sleep(p.burstTime); // Simulate CPU work
                    
                    p.endTime = System.currentTimeMillis();
                    
                    // Update global simulation end time (thread-safe)
                    synchronized (timeLock) {
                        lastProcessEndTime = Math.max(lastProcessEndTime, p.endTime);
                    }
                    
                    long turnaroundTime = p.getTurnaroundTime();
                    log(tag, "END: " + p.name + " (Turnaround: " + turnaroundTime + "ms)");
                    
                    completedProcesses.add(p); // Add to stats queue
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log(tag, "Core thread interrupted, shutting down.");
            }
            log(tag, "Core thread finished.");
        }
    }
}
