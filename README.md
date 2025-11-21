# Multi-Core CPU Scheduling Simulator

A Java-based simulator for analyzing CPU scheduling algorithms in multi-core environments. Built to generate empirical data on scheduling performance, synchronization overhead, and IPC costs.

# Overview

The simulator consists of two separate Java programs that communicate via IPC:

- **CPUSimulator**: The main "OS" process that manages worker threads (cores) and a ready queue
- **ProcessInjector**: A separate spawner process that submits processes to the simulator while it runs

# Building

```bash
# 1. Compile all Java files
javac -d build/classes \
    src/com/simulator/core/*.java \
    src/com/simulator/injector/*.java

# 2. Create JARs
jar cfe build/cpu-simulator.jar com.simulator.core.CPUSimulator \
    -C build/classes com/simulator/core
jar cfe build/process-injector.jar com.simulator.injector.ProcessInjector \
    -C build/classes com/simulator/injector
```

This creates two JAR files:
- `build/cpu-simulator.jar`
- `build/process-injector.jar`

> [!TIP]
> A convenient build script that runs the above commands is available at `build.sh`, for UNIX systems.

# Running

## Basic Usage (Pipe IPC)

```bash
java -jar build/process-injector.jar --workload workloads/workload.txt | \
  java -jar build/cpu-simulator.jar --cores 4 --scheduler FCFS --ipc pipe
```

## CPUSimulator Options

```
--cores <N>           Number of CPU cores to simulate (required)
--scheduler <TYPE>    Scheduling algorithm: FCFS, SJF, PRIORITY (required)
--ipc <TYPE>          IPC method: pipe (required)
--output <FILE>       Export per-process statistics to CSV (optional)
```

## ProcessInjector Options

```
--workload <FILE>     Path to workload file (required)
```

# Workload File Format

CSV format with header comments:

```csv
# Name,ArrivalTimeMs,BurstTimeMs,Priority
P1,0,100,3
P2,50,50,1
P3,100,200,2
```

- **Name**: Process identifier
- **ArrivalTimeMs**: Relative arrival time from injection start (milliseconds)
- **BurstTimeMs**: CPU time needed (milliseconds)
- **Priority**: Scheduling priority (lower = higher priority, used by PRIORITY scheduler)

Comments start with `#` and are ignored.

# Scheduling Algorithms

## FCFS (First-Come, First-Served)
Processes execute in arrival order (FIFO queue).

## SJF (Shortest Job First)
Processes with shortest burst time execute first. Minimizes average wait time for CPU-bound workloads.

## PRIORITY
Processes with lowest priority value execute first (lower number = higher priority).

# Architecture

## Process Class
Simple data object holding:
- Process metadata (name, burstTime, priority)
- Timing metrics (arrivalTime, startTime, endTime)
- Calculated metrics: `waitTime()`, `turnaroundTime()`

## CPUSimulator Components

**Ready Queue**: Thread-safe queue implementation varies by scheduler:
- FCFS: `LinkedBlockingQueue` (FIFO)
- SJF: `PriorityBlockingQueue` sorted by burst time
- PRIORITY: `PriorityBlockingQueue` sorted by priority value

**IPC Thread**: Reads process data from stdin, parses CSV, sets arrival time, adds to ready queue. Stops when "END" signal received.

**Core Threads**: Worker threads that continuously poll the ready queue. Each:
1. Polls queue with 100ms timeout (allows checking if IPC finished)
2. Records start time and logs wait time
3. Sleeps for `burstTime` milliseconds (simulates CPU work)
4. Records end time and logs turnaround time
5. Adds completed process to statistics queue

Threads exit when `ipcFinished == true` and `readyQueue.isEmpty()`.

## ProcessInjector Logic

1. Reads workload file line by line
2. Parses arrival time from CSV
3. Sleeps to match relative arrival times
4. Writes CSV line to stdout (pipe to simulator)
5. Sends "END" sentinel when complete

# Output

## Console Logs

Format: `[elapsed_ms] message`

Example:
```
[0ms] Simulator started: 4 cores, SJF scheduler, pipe IPC
[1ms] IPC listener started
[5ms] Process arrived: P1
[6ms] CORE-0 START: P1 (wait: 1ms)
[106ms] CORE-0 END: P1 (turnaround: 101ms)
...
[5000ms] All processes complete
[5000ms] ===== STATISTICS =====
[5000ms] Total processes: 100
[5000ms] Execution time: 4.95s
[5000ms] Throughput: 20.20 processes/sec
[5000ms] Avg wait time: 45.2ms
[5000ms] Avg turnaround time: 145.8ms
```

## CSV Export (Optional)

When `--output` is specified, writes per-process metrics:

```csv
Name,ArrivalTime,StartTime,EndTime,WaitTime,TurnaroundTime
P1,5,6,106,1,101
P2,55,56,106,1,51
...
```

All timestamps are relative to simulation start (milliseconds).

# Experiments

The simulator is designed to gather data for three experiments:

## Experiment 1: Synchronization Overhead
**Question**: Does 8x cores give 8x speed?

Generate 5000 identical processes (all arrive at t=0, burst=100ms). Run with 1, 2, 4, 8, 16 cores using FCFS. Plot throughput vs. cores to observe lock contention plateau.

## Experiment 2: Scheduler Comparison
**Question**: How do schedulers perform in multi-core environments?

Generate 1000 mixed processes (50% short jobs 10-50ms, 50% long jobs 200-500ms, uniform arrival over 10s). Compare FCFS vs. SJF average wait times with 8 cores.

## Experiment 3: IPC Overhead
**Question**: How much faster is shared memory than pipes?

Generate 5000 processes with 1ms burst time. Compare pipe IPC vs. shared memory IPC throughput.

**Note**: Shared memory IPC is not yet implemented (Milestone 5).

# Known Limitations

- **Non-preemptive**: Once a core starts a process, it runs to completion
- **No I/O simulation**: All processes are CPU-bound
- **No memory management**: Only CPU scheduling is simulated
- **Fixed workload format**: Arrival times must be pre-determined
