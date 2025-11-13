package com.simulator.core;

import java.util.concurrent.TimeUnit;

/**
 * Interface abstracting the scheduler's ready queue.
 * This allows swapping schedulers (FCFS, SJF, Priority) easily.
 */
public interface ReadyQueue {
    /**
     * Adds a process to the queue, blocking if full.
     */
    void put(Process p) throws InterruptedException;

    /**
     * Retrieves and removes the head of the queue, waiting up to the
     * specified time if necessary for an element to become available.
     *
     * @return The head of this queue, or null if the timeout elapses.
     */
    Process poll(long timeout, TimeUnit unit) throws InterruptedException;

    /**
     * @return true if this queue contains no elements.
     */
    boolean isEmpty();
}
