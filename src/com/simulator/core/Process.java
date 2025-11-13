package com.simulator.core;

import java.util.Comparator;

/**
 * Represents a single process in the simulation.
 * Holds all data and timing metrics.
 * Implements Comparable to work with PriorityBlockingQueue.
 */
public class Process implements Comparable<Process> {

    /**
     * Comparator for SJF: Sorts by burstTime (ascending).
     */
    public static final Comparator<Process> SJF_COMPARATOR = 
        Comparator.comparingInt(p -> p.burstTime);

    /**
     * Comparator for Priority: Sorts by priority value (ascending).
     */
    public static final Comparator<Process> PRIORITY_COMPARATOR = 
        Comparator.comparingInt(p -> p.priority);

    /**
     * Defines the sorting strategy for the static comparator.
     */
    public enum SortBy {
        BURST_TIME, // For SJF
        PRIORITY    // For Priority Scheduling
    }

    /**
     * The static comparator used by PriorityBlockingQueue.
     * Its behavior is set by setSortStrategy().
     */
    public static Comparator<Process> sortComparator = Comparator.comparingInt(p -> p.burstTime);

    // === Fields from README ===
    public final String name;
    public final int burstTime;
    public final int priority;

    // === Timing Metrics ===
    public long arrivalTime; // Set by IPC thread when process is received
    public long startTime;   // Set by Core thread when execution begins
    public long endTime;     // Set by Core thread when execution finishes

    public Process(String name, int burstTime, int priority) {
        this.name = name;
        this.burstTime = burstTime;
        this.priority = priority;
    }

    /**
     * Factory method to parse a full CSV line.
     * Format: Name,ArrivalTimeMs,BurstTimeMs,Priority
     *
     * @param csvLine The raw CSV string.
     * @return A new Process object.
     * @throws NumberFormatException if numeric fields are invalid.
     */
    public static Process fromCSV(String csvLine) throws NumberFormatException {
        try {
            String[] parts = csvLine.split(",");
            if (parts.length < 4) {
                throw new IllegalArgumentException("CSV line must have 4 fields.");
            }
            String name = parts[0].trim();
            // parts[1] (ArrivalTimeMs) is used by Injector, not Simulator
            int burstTime = Integer.parseInt(parts[2].trim());
            int priority = Integer.parseInt(parts[3].trim());
            
            return new Process(name, burstTime, priority);
        } catch (Exception e) {
            // Re-throw as a more specific exception
            throw new NumberFormatException("Failed to parse CSV line: " + csvLine + " - " + e.getMessage());
        }
    }

    // === Calculated Metrics ===
    
    /**
     * @return The total time the process spent waiting in the ready queue.
     */
    public long getWaitTime() {
        // Ensure startTime has been set
        if (startTime == 0 || arrivalTime == 0) return 0;
        return startTime - arrivalTime;
    }

    /**
     * @return The total time from process arrival to its completion.
     */
    public long getTurnaroundTime() {
        // Ensure endTime has been set
        if (endTime == 0 || arrivalTime == 0) return 0;
        return endTime - arrivalTime;
    }

    /**
     * Default comparison for Comparable.
     * We'll make it default to SJF, though it shouldn't be relied upon.
     */
    @Override
    public int compareTo(Process other) {
        return SJF_COMPARATOR.compare(this, other);
    }

    @Override
    public String toString() {
        return name + " (Burst: " + burstTime + "ms, Prio: " + priority + ")";
    }
}
