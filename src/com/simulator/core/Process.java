package com.simulator.core;

/**
 * Represents a process in the simulation.
 * Tracks timing metrics for calculating wait time and turnaround time.
 */
public class Process {
    public final String name;
    public final int burstTime;
    public final int priority;
    
    public long arrivalTime;  // Set when process enters ready queue
    public long startTime;    // Set when core begins execution
    public long endTime;      // Set when core finishes execution

    public Process(String name, int burstTime, int priority) {
        this.name = name;
        this.burstTime = burstTime;
        this.priority = priority;
    }

    /**
     * Parses a CSV line: Name,ArrivalTimeMs,BurstTimeMs,Priority
     * Note: ArrivalTimeMs from file is ignored - actual arrival time is set by IPC thread.
     */
    public static Process fromCSV(String line) {
        String[] parts = line.split(",");
        if (parts.length < 4) {
            throw new IllegalArgumentException("Invalid CSV format: " + line);
        }
        return new Process(
            parts[0].trim(),
            Integer.parseInt(parts[2].trim()),
            Integer.parseInt(parts[3].trim())
        );
    }

    public long waitTime() {
        return startTime - arrivalTime;
    }

    public long turnaroundTime() {
        return endTime - arrivalTime;
    }

    /**
     * Exports process metrics as CSV row with timestamps relative to simulation start.
     */
    public String toCSV(long simulationStartTime) {
        return String.format("%s,%d,%d,%d,%d,%d",
            name,
            arrivalTime - simulationStartTime,
            startTime - simulationStartTime,
            endTime - simulationStartTime,
            waitTime(),
            turnaroundTime()
        );
    }

    public static String csvHeader() {
        return "Name,ArrivalTime,StartTime,EndTime,WaitTime,TurnaroundTime";
    }

    @Override
    public String toString() {
        return String.format("%s (Burst: %dms, Priority: %d)", name, burstTime, priority);
    }
}
