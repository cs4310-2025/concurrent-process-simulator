package com.simulator.core;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * SJF (Shortest Job First) implementation of the ReadyQueue.
 * Wraps a PriorityBlockingQueue.
 * Requires Process.setSortStrategy(SortBy.BURST_TIME) to be called
 * BEFORE this queue is instantiated.
 */
public class SJFQueue implements ReadyQueue {
    private final BlockingQueue<Process> queue = 
        new PriorityBlockingQueue<>(11, Process.SJF_COMPARATOR);

    @Override
    public void put(Process p) throws InterruptedException {
        queue.put(p);
    }

    @Override
    public Process poll(long timeout, TimeUnit unit) throws InterruptedException {
        return queue.poll(timeout, unit);
    }

    @Override
    public boolean isEmpty() {
        return queue.isEmpty();
    }
}
