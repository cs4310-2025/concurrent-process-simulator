package com.simulator.core;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Multi-core CPU scheduling simulator.
 * Receives processes via IPC and schedules them across worker threads (cores).
 */
public class CPUSimulator {
    private final long startTime = System.currentTimeMillis();
    
    private final int coreCount;
    private final String schedulerType;
    private final String ipcType;
    private final String outputFilePath;
    
    private final BlockingQueue<Process> readyQueue;
    private final List<Thread> coreThreads = new ArrayList<>();
    private final ConcurrentLinkedQueue<Process> completedProcesses = new ConcurrentLinkedQueue<>();
    
    private volatile boolean ipcFinished = false;
    private final AtomicLong firstProcessStartTime = new AtomicLong(-1);
    private final AtomicLong lastProcessEndTime = new AtomicLong(-1);

    public CPUSimulator(int coreCount, String schedulerType, String ipcType, String outputFilePath) {
        this.coreCount = coreCount;
        this.schedulerType = schedulerType;
        this.ipcType = ipcType;
        this.outputFilePath = outputFilePath;
        this.readyQueue = createReadyQueue(schedulerType);
    }

    public static void main(String[] args) throws Exception {
        int cores = -1;
        String scheduler = null;
        String ipc = null;
        String outputFile = null;

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
                case "--output":
                    outputFile = args[++i];
                    break;
                default:
                    System.err.println("Unknown argument: " + args[i]);
                    printUsageAndExit();
            }
        }

        if (cores == -1 || scheduler == null || ipc == null) {
            printUsageAndExit();
        }
        if (!scheduler.equals("FCFS") && !scheduler.equals("SJF") && !scheduler.equals("PRIORITY")) {
            System.err.println("Invalid scheduler: " + scheduler);
            printUsageAndExit();
        }
        if (!ipc.equals("pipe")) {
            System.err.println("Only 'pipe' IPC is currently supported");
            printUsageAndExit();
        }

        CPUSimulator sim = new CPUSimulator(cores, scheduler, ipc, outputFile);
        sim.log("Simulator started: %d cores, %s scheduler, %s IPC", cores, scheduler, ipc);
        sim.run();
    }

    private static void printUsageAndExit() {
        System.err.println("Usage: java CPUSimulator --cores <N> --scheduler <FCFS|SJF|PRIORITY> --ipc <pipe> [--output <file>]");
        System.exit(1);
    }

    /**
     * Creates the appropriate ready queue based on scheduling algorithm.
     * - FCFS: LinkedBlockingQueue (FIFO)
     * - SJF: PriorityBlockingQueue sorted by burst time
     * - PRIORITY: PriorityBlockingQueue sorted by priority value
     */
    private BlockingQueue<Process> createReadyQueue(String schedulerType) {
        switch (schedulerType) {
            case "SJF":
                return new PriorityBlockingQueue<>(11, Comparator.comparingInt(p -> p.burstTime));
            case "PRIORITY":
                return new PriorityBlockingQueue<>(11, Comparator.comparingInt(p -> p.priority));
            default:
                return new LinkedBlockingQueue<>();
        }
    }

    private void run() throws Exception {
        Thread ipcThread = new Thread(this::runIpcPipeListener);
        ipcThread.setName("IPC-Thread");
        ipcThread.start();

        for (int i = 0; i < coreCount; i++) {
            Thread core = new Thread(new CoreWorker(i));
            core.setName("Core-" + i);
            coreThreads.add(core);
            core.start();
        }

        for (Thread core : coreThreads) {
            core.join();
        }

        if (!readyQueue.isEmpty()) {
            throw new IllegalStateException("Ready queue not empty after all cores finished");
        }

        log("All processes complete");
        printStatistics();

        if (outputFilePath != null) {
            writeCSV(outputFilePath);
        }
    }

    /**
     * IPC thread: reads process data from stdin (pipe) until "END" signal.
     */
    private void runIpcPipeListener() {
        log("IPC listener started");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.equals("END")) {
                    log("IPC received END signal");
                    ipcFinished = true;
                    break;
                }
                if (line.trim().isEmpty() || line.startsWith("#")) {
                    continue;
                }

                Process p = Process.fromCSV(line);
                p.arrivalTime = System.currentTimeMillis();
                readyQueue.put(p);
                log("Process arrived: %s", p.name);
            }
        } catch (Exception e) {
            if (!ipcFinished) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Core worker thread: pulls processes from ready queue and executes them.
     */
    private class CoreWorker implements Runnable {
        private final int coreId;

        CoreWorker(int coreId) {
            this.coreId = coreId;
        }

        @Override
        public void run() {
            try {
                while (!ipcFinished || !readyQueue.isEmpty()) {
                    // Poll with timeout so we can check ipcFinished periodically
                    Process p = readyQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (p == null) continue;

                    p.startTime = System.currentTimeMillis();
                    firstProcessStartTime.compareAndSet(-1, p.startTime);
                    
                    log("CORE-%d START: %s (wait: %dms)", coreId, p.name, p.waitTime());
                    
                    Thread.sleep(p.burstTime);
                    
                    p.endTime = System.currentTimeMillis();
                    lastProcessEndTime.updateAndGet(current -> Math.max(current, p.endTime));
                    
                    log("CORE-%d END: %s (turnaround: %dms)", coreId, p.name, p.turnaroundTime());
                    completedProcesses.add(p);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void printStatistics() {
        if (completedProcesses.isEmpty()) {
            log("No processes completed");
            return;
        }

        int total = completedProcesses.size();
        long totalWait = 0;
        long totalTurnaround = 0;

        for (Process p : completedProcesses) {
            totalWait += p.waitTime();
            totalTurnaround += p.turnaroundTime();
        }

        long execTime = lastProcessEndTime.get() - firstProcessStartTime.get();
        double execTimeSec = execTime / 1000.0;
        double throughput = total / execTimeSec;
        double avgWait = (double) totalWait / total;
        double avgTurnaround = (double) totalTurnaround / total;

        log("===== STATISTICS =====");
        log("Total processes: %d", total);
        log("Execution time: %.2fs", execTimeSec);
        log("Throughput: %.2f processes/sec", throughput);
        log("Avg wait time: %.2fms", avgWait);
        log("Avg turnaround time: %.2fms", avgTurnaround);
    }

    private void writeCSV(String path) throws Exception {
        log("Writing results to: %s", path);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(path))) {
            writer.write(Process.csvHeader());
            writer.newLine();
            for (Process p : completedProcesses) {
                writer.write(p.toCSV(startTime));
                writer.newLine();
            }
        }
        log("Wrote %d records to CSV", completedProcesses.size());
    }

    private void log(String format, Object... args) {
        long elapsed = System.currentTimeMillis() - startTime;
        String message = args.length == 0 ? format : String.format(format, args);
        System.out.printf("[%dms] %s%n", elapsed, message);
    }
}
