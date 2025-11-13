package com.simulator.utils;

import java.util.Random;

/**
 * A standalone utility to generate complex workload files for experiments.
 * All output is to System.out.
 *
 * Usage:
 * java -cp bin com.simulator.utils.WorkloadGenerator --type <TYPE> --count <N> > workloads/filename.txt
 *
 * --type: exp1, exp2, exp3
 * --count: Number of processes to generate
 */
public class WorkloadGenerator {

    public static void main(String[] args) {
        String type = null;
        int count = 0;

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--type") && i + 1 < args.length) {
                type = args[++i];
            } else if (args[i].equals("--count") && i + 1 < args.length) {
                count = Integer.parseInt(args[++i]);
            }
        }

        if (type == null || count == 0) {
            System.err.println("Usage: java com.simulator.utils.WorkloadGenerator --type <exp1|exp2|exp3> --count <N>");
            return;
        }

        // Print CSV header
        System.out.println("# Name,ArrivalTimeMs,BurstTimeMs,Priority");
        
        switch (type) {
            case "exp1":
                generateExp1Workload(count);
                break;
            case "exp2":
                generateExp2Workload(count);
                break;
            case "exp3":
                generateExp3Workload(count);
                break;
            default:
                System.err.println("Unknown type: " + type);
        }
    }

    /**
     * Experiment 1: Sync Overhead
     * 5,000 identical processes, 0 arrival, 100ms burst
     */
    private static void generateExp1Workload(int count) {
        for (int i = 1; i <= count; i++) {
            System.out.println("P" + i + ",0,100,1");
        }
    }

    /**
     * Experiment 2: Scheduler Comparison
     * 1,000 mixed processes, uniform 10s arrival
     */
    private static void generateExp2Workload(int count) {
        Random rand = new Random();
        for (int i = 1; i <= count; i++) {
            long arrival = rand.nextInt(10000); // 0-10000ms
            int priority = rand.nextInt(5) + 1; // 1-5
            
            // 50% short jobs (10-50ms), 50% long jobs (200-500ms)
            int burst;
            if (rand.nextBoolean()) {
                burst = rand.nextInt(41) + 10; // 10-50
            } else {
                burst = rand.nextInt(301) + 200; // 200-500
            }
            
            System.out.println("P" + i + "," + arrival + "," + burst + "," + priority);
        }
    }

    /**
     * Experiment 3: IPC Overhead
     * 5,000 identical processes, 0 arrival, 1ms burst
     */
    private static void generateExp3Workload(int count) {
        for (int i = 1; i <= count; i++) {
            System.out.println("P" + i + ",0,1,1");
        }
    }
}
