package com.simulator.core;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * FCFS (FIFO) implementation of the ReadyQueue.
 * Wraps a LinkedBlockingQueue.
 */
public class FCFSQueue implements ReadyQueue {
    private final BlockingQueue<Process> queue = new LinkedBlockingQueue<>();

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
