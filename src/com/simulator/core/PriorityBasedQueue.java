package com.simulator.core;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Priority-based implementation of the ReadyQueue.
 * Wraps a PriorityBlockingQueue.
 * Requires Process.setSortStrategy(SortBy.PRIORITY) to be called
 * BEFORE this queue is instantiated.
 */
public class PriorityBasedQueue implements ReadyQueue {
    private final BlockingQueue<Process> queue = 
        new PriorityBlockingQueue<>(11, Process.PRIORITY_COMPARATOR);

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
