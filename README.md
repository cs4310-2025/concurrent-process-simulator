# Project: Multi-Core Process Management Simulator

# 1. Objective

To extend our single-core scheduling homework (Assignment 1) into a multi-core CPU simulator.

The goal is to build a simulator to generate our own data on system performance. We will analyze how different Scheduling algorithms (FCFS, SJF, Priority) perform in a multi-core environment. We will also directly measure the performance impact of Synchronization (lock contention) and different Inter-Process Communication (IPC) methods.

# 2. System Architecture

The simulator will consist of two Processes (separate Java programs) that must communicate.

- `CPUSimulator.java`: The main "OS" process. It manages a pool of "Core" threads and a `ReadyQueue`.
- `ProcessInjector.java`: A separate "spawner" process that submits new processes to the simulator *while it is running*.

We'll implement two distinct IPC methods from our proposal to compare their overhead:

1. **Shared Memory (Low-Overhead)**: Implemented in Java using a Memory-Mapped File (`java.nio.MappedByteBuffer`). This is a low-level, high-speed OS concept.
2. **Pipes (High-Overhead)**: Implemented using Standard I/O (System.out | System.in). This is a classic, kernel-buffered message stream. Every write() and read() operation is a system call, forcing a context switch and incurring overhead.

# 3. Core Components

## 3.1. `Process.java` (The Data Object)

A simple class holding process data with timing fields for measurement.

**Required Fields:**
```java
- String name              // Process identifier (e.g., "P1")
- int burstTime            // CPU time needed (milliseconds)
- int priority             // Scheduling priority (lower = higher priority)
- long arrivalTime         // When process was injected into simulator (System.currentTimeMillis())
- long startTime           // When a core began executing it (System.currentTimeMillis())
- long endTime             // When core finished executing it (System.currentTimeMillis())
```

**Calculated Metrics:**
```java
- waitTime = startTime - arrivalTime
- turnaroundTime = endTime - arrivalTime
```

**Implementation Notes:**
- Should implement `Comparable<Process>` to support PriorityBlockingQueue sorting
- Override `compareTo()` to sort by burstTime (SJF) or priority (Priority scheduler)
- Create a static factory method to parse from CSV line

## 3.2. `CPUSimulator.java` (The "Operating System")

This is the main simulation process. It must be configurable via command-line flags.
- Manages a pool of "CPU core" worker threads.
- Manages a swappable, thread-safe `ReadyQueue` for scheduling
- Manages an "IPC Thread" to receive new processes.

```
java CPUSimulator --cores <N> --scheduler <TYPE> --ipc <TYPE> [--shm-file <PATH>] [--output <FILE>]

  --cores <N>        (Required) The number of worker threads (CPU cores)
                     to simulate (e.g., 8).

  --scheduler <TYPE> (Required) The scheduling algorithm to use.
                     This selects the ReadyQueue implementation.
                     Valid types: FCFS, SJF, Priority.

  --ipc <TYPE>       (Required) The IPC method to listen for processes.
                     Valid types: shm, pipe.

  --shm-file <PATH>  (Optional) Path to shared memory file.
                     Default: "./cpu_sim_shm.dat"
                     Only used when --ipc shm.

  --output <FILE>    (Optional) Export per-process statistics to CSV file.
                     Makes graphing easier than parsing logs.
```

### Scheduler Logic

The ReadyQueue should be abstracted as an interface or abstract class, with three concrete implementations:

- **FCFS**: Use `java.util.concurrent.LinkedBlockingQueue` (FIFO order).
- **SJF**: Use `java.util.concurrent.PriorityBlockingQueue` with `Process.compareTo()` sorting by `burstTime` (ascending).
- **Priority**: Use `java.util.concurrent.PriorityBlockingQueue` with `Process.compareTo()` sorting by `priority` (ascending = higher priority).

### IPC Thread Logic

The IPC thread runs in a loop, receiving new processes and adding them to the ReadyQueue.

**Pipe Mode (`--ipc pipe`):**
```java
BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
while (true) {
    String line = reader.readLine(); // Blocks until data arrives
    if (line == null || line.equals("END")) break; // EOF signal
    Process p = Process.fromCSV(line);
    p.arrivalTime = System.currentTimeMillis();
    readyQueue.put(p);
    log("[IPC-PIPE] New process " + p.name + " arrived.");
}
ipcFinished = true; // Signal that no more processes will arrive
```

**Shared Memory Mode (`--ipc shm`):**
```java
// See Section 3.5 for detailed SHM protocol
while (true) {
    int messageCount = mappedBuffer.getInt(0); // Read counter from first 4 bytes
    if (messageCount > lastProcessedCount) {
        // New data available
        int offset = 4;
        while (offset < mappedBuffer.capacity() && mappedBuffer.get(offset) != 0) {
            String line = readNullTerminatedString(mappedBuffer, offset);
            if (line.equals("END")) {
                ipcFinished = true;
                return;
            }
            Process p = Process.fromCSV(line);
            p.arrivalTime = System.currentTimeMillis();
            readyQueue.put(p);
            log("[IPC-SHM] New process " + p.name + " arrived.");
            offset += line.length() + 1; // +1 for null terminator
        }
        lastProcessedCount = messageCount;
    }
    Thread.sleep(1); // Poll interval (1ms)
}
```

### Core Thread Logic

Each core is a worker thread that continuously pulls processes from the ReadyQueue and "executes" them.

**Pseudocode:**
```java
while (simulatorRunning || !readyQueue.isEmpty()) {
    try {
        Process p = readyQueue.poll(100, TimeUnit.MILLISECONDS); // Wait up to 100ms
        if (p == null) {
            if (ipcFinished && readyQueue.isEmpty()) break; // No more work
            continue; // Keep waiting
        }
        
        p.startTime = System.currentTimeMillis();
        long waitTime = p.startTime - p.arrivalTime;
        log("[CORE-" + coreId + "] START: " + p.name + " (Wait: " + waitTime + "ms)");
        
        Thread.sleep(p.burstTime); // Simulate CPU work
        
        p.endTime = System.currentTimeMillis();
        long turnaroundTime = p.endTime - p.arrivalTime;
        log("[CORE-" + coreId + "] END: " + p.name + " (Turnaround: " + turnaroundTime + "ms)");
        
        completedProcesses.add(p); // Thread-safe collection for statistics
    } catch (InterruptedException e) {
        break;
    }
}
```

### Shutdown Logic

```java
1. IPC Thread detects "END" message and sets ipcFinished = true
2. Core threads continue until readyQueue.isEmpty()
3. Main thread joins all core threads (waits for them to finish)
4. Assert readyQueue.isEmpty() (sanity check)
5. Calculate and print final statistics
6. If --output specified, write CSV file
```

## 3.3. `ProcessInjector.java` (The Process Spawner)

A separate process that "injects" new processes into the running simulator.
- Connects to the simulator using the specified IPC method.
- Reads a workload file, sleeps to match arrival times, and injects processes.

```
java ProcessInjector --ipc <TYPE> --workload <FILE> [--shm-file <PATH>]

  --ipc <TYPE>       (Required) The IPC method to use for sending.
                     Must match the simulator's.
                     Valid types: shm, pipe.

  --workload <FILE>  (Required) Path to the workload file containing
                     processes to inject (e.g., "workload.txt").
                     Format (CSV): Name,ArrivalTimeMs,BurstTimeMs,Priority

  --shm-file <PATH>  (Optional) Path to shared memory file.
                     Default: "./cpu_sim_shm.dat"
                     Only used when --ipc shm.
```

### Workload File Format

```csv
# Workload Format (CSV)
# Name,ArrivalTimeMs,BurstTimeMs,Priority
P1,0,100,3
P2,50,50,1
P3,100,200,2
P4,150,75,2
```

**Notes:**
- Lines starting with `#` are comments (ignored)
- ArrivalTimeMs is relative to the start of injection (not absolute timestamps)
- Priority: lower values = higher priority

### Injection Logic

The injector reads the workload file and sleeps between process injections to simulate realistic arrival patterns.

**Pseudocode:**
```java
List<Process> workload = parseWorkloadFile(workloadPath);
long startTime = System.currentTimeMillis();
long lastArrivalTime = 0;

for (Process p : workload) {
    long sleepTime = p.arrivalTime - lastArrivalTime;
    if (sleepTime > 0) {
        Thread.sleep(sleepTime);
    }
    sendProcess(p); // Via pipe or SHM
    lastArrivalTime = p.arrivalTime;
}

sendEOF(); // Send "END" sentinel to signal completion
```

**Pipe Mode:**
```java
void sendProcess(Process p) {
    String csv = p.name + "," + p.arrivalTime + "," + p.burstTime + "," + p.priority;
    System.out.println(csv);
    System.out.flush(); // Important: force pipe write
}

void sendEOF() {
    System.out.println("END");
    System.out.flush();
}
```

**Shared Memory Mode:**
```java
void sendProcess(Process p) {
    String csv = p.name + "," + p.arrivalTime + "," + p.burstTime + "," + p.priority;
    writeNullTerminatedString(mappedBuffer, currentOffset, csv);
    currentOffset += csv.length() + 1;
}

void sendEOF() {
    writeNullTerminatedString(mappedBuffer, currentOffset, "END");
    mappedBuffer.putInt(0, messageCount++); // Increment counter to signal new data
}
```

## 3.4. `ReadyQueue` Interface

To make scheduler swapping easy, define a common interface:

```java
interface ReadyQueue {
    void put(Process p) throws InterruptedException;
    Process poll(long timeout, TimeUnit unit) throws InterruptedException;
    boolean isEmpty();
}
```

**Implementations:**
- `FCFSQueue` wraps `LinkedBlockingQueue<Process>`
- `SJFQueue` wraps `PriorityBlockingQueue<Process>` (sorted by burstTime)
- `PriorityQueue` wraps `PriorityBlockingQueue<Process>` (sorted by priority)

## 3.5. Shared Memory IPC Protocol

Because Java's `MappedByteBuffer` doesn't have built-in synchronization, we need a simple protocol.

### File Structure

```
Byte 0-3:   Message Counter (int) - incremented each time new data is written
Byte 4+:    Process data (null-terminated CSV strings)
```

### Writer (ProcessInjector) Protocol

```java
1. Open/create memory-mapped file (e.g., 1MB size)
2. Write first process as null-terminated string starting at offset 4
3. Write subsequent processes sequentially
4. After all processes written, write "END\0"
5. Increment message counter at offset 0
6. Close file
```

### Reader (CPUSimulator) Protocol

```java
1. Open memory-mapped file
2. Poll counter at offset 0 in a loop (sleep 1ms between checks)
3. When counter increases:
   - Read all null-terminated strings from offset 4 onward
   - Parse each as a Process and add to ReadyQueue
   - If string is "END", set ipcFinished = true and exit loop
4. Continue polling until "END" received
```

### Implementation Helpers

```java
void writeNullTerminatedString(MappedByteBuffer buffer, int offset, String str) {
    buffer.position(offset);
    buffer.put(str.getBytes(StandardCharsets.UTF_8));
    buffer.put((byte) 0); // Null terminator
}

String readNullTerminatedString(MappedByteBuffer buffer, int offset) {
    buffer.position(offset);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    byte b;
    while ((b = buffer.get()) != 0) {
        baos.write(b);
    }
    return baos.toString(StandardCharsets.UTF_8);
}
```

# 4. Our Experiments (The Core of the Project)

Our final presentation will be based on the data and graphs from these three experiments.

## Experiment 1: Measuring Synchronization Overhead

**Question**: Does 8x the cores give 8x the speed?

**Hypothesis**: Performance will plateau due to ReadyQueue lock contention.

**Test Setup**:
- Generate a workload of 5,000 identical processes:
  - ArrivalTime: 0ms (all arrive instantly)
  - BurstTime: 100ms (uniform, so lock is the only variable)
  - Priority: 1 (doesn't matter for FCFS)
- Use `--ipc shm` (fast IPC to avoid IPC bottleneck)
- Use `--scheduler FCFS` (simplest, most predictable)
- Run with `--cores 1`, then 2, 4, 8, and 16

**Measurements**:
- Total execution time (first process start → last process end)
- Throughput = 5000 / total_time (processes/second)

**Analysis**: 
Plot **Throughput vs. Number of Cores**. We expect:
- Linear scaling from 1→2→4 cores
- Flattening at 8+ cores as the ReadyQueue lock becomes the bottleneck
- This proves that synchronization overhead limits parallel performance

## Experiment 2: Comparing Scheduling Algorithms

**Question**: How does a multi-core environment affect scheduler choice?

**Hypothesis**: SJF will minimize average wait time, even with multiple cores.

**Test Setup**:
- Generate a mixed workload of 1,000 processes:
  - 50% short jobs (BurstTime: 10-50ms)
  - 50% long jobs (BurstTime: 200-500ms)
  - ArrivalTime: Uniform distribution over 10 seconds
  - Priority: Random (1-5)
- Use `--cores 8` (realistic multi-core system)
- Use `--ipc shm` (avoid IPC overhead)
- Run twice: once with `--scheduler FCFS`, once with `--scheduler SJF`

**Measurements**:
- Average wait time per process
- Average turnaround time per process

**Analysis**:
Plot **Average Wait Time** for FCFS vs. SJF. We expect:
- FCFS: High wait time (long jobs block short jobs)
- SJF: Low wait time (short jobs execute first)
- This recreates our homework results in a multi-core context

## Experiment 3: Measuring IPC Overhead

**Question**: How much faster is shared memory than a pipe?

**Hypothesis**: Pipes have ~100x higher overhead due to system calls.

**Test Setup**:
- Generate a workload of 5,000 processes:
  - ArrivalTime: 0ms (all injected as fast as possible)
  - BurstTime: 1ms (very short, so IPC overhead dominates)
  - Priority: 1
- Use `--cores 8` and `--scheduler FCFS`
- **Run 1**: `--ipc shm` (both simulator and injector)
- **Run 2**: `--ipc pipe` (both simulator and injector)

**Measurements**:
- Total execution time
- IPC throughput = 5000 / total_time

**Analysis**:
Plot **IPC Throughput** (processes/sec) for SHM vs. Pipe. We expect:
- SHM: ~50,000+ processes/sec (memory-speed communication)
- Pipe: ~500-1,000 processes/sec (limited by kernel context switches)
- This proves that shared memory is orders of magnitude faster

# 5. Running and Logging

## Running the Simulator

### Pipe Mode (Single Command)

The `|` operator pipes the injector's stdout to the simulator's stdin.

```bash
java ProcessInjector --ipc pipe --workload "workload.txt" | java CPUSimulator --ipc pipe --cores 8 --scheduler SJF
```

### Shared Memory Mode (Two Terminals)

**Terminal 1** (Simulator - starts first and waits):
```bash
java CPUSimulator --ipc shm --cores 8 --scheduler SJF --shm-file "sim.dat"
```

**Terminal 2** (Injector - starts second):
```bash
java ProcessInjector --ipc shm --workload "workload.txt" --shm-file "sim.dat"
```

## Log Output Format

The `CPUSimulator` logs all events to stdout (console). This is our primary data source.

### Log Format

```
[relative_time_ms] [TAG] Message
```

**relative_time_ms**: Milliseconds since simulator started (makes logs readable)

**Tags:**
- `[SIM]`: Simulator lifecycle events
- `[IPC-PIPE]` or `[IPC-SHM]`: Process arrivals via IPC
- `[CORE-N]`: Core thread events (N = core ID)

**Implementation Tip:**
```java
long startTime = System.currentTimeMillis(); // Store at simulator startup
void log(String message) {
    long elapsed = System.currentTimeMillis() - startTime;
    System.out.println("[" + elapsed + "ms] " + message);
}
```

### Example Log

```
[0ms] [SIM] Multi-Core Simulator Started. Cores: 8. Scheduler: SJF. IPC: pipe.
[1ms] [IPC-PIPE] New process P1 (Burst: 100ms, Prio: 3) arrived. Added to ReadyQueue.
[2ms] [CORE-1] START: P1 (Wait: 1ms)
[50ms] [IPC-PIPE] New process P2 (Burst: 50ms, Prio: 1) arrived. Added to ReadyQueue.
[51ms] [CORE-2] START: P2 (Wait: 1ms)
[101ms] [CORE-2] END: P2 (Turnaround: 51ms)
[102ms] [CORE-1] END: P1 (Turnaround: 101ms)
[150ms] [IPC-PIPE] New process P3 (Burst: 200ms, Prio: 2) arrived. Added to ReadyQueue.
[151ms] [CORE-1] START: P3 (Wait: 1ms)
...
[12500ms] [SIM] === ALL PROCESSES COMPLETE ===
[12500ms] [SIM] Total Execution Time: 12.5s
[12500ms] [SIM] Throughput: 80.0 processes/sec
[12500ms] [SIM] Avg. Wait Time: 45ms
[12500ms] [SIM] Avg. Turnaround Time: 145ms
```

### Final Statistics (Printed at End)

```
Total Execution Time = lastProcessEndTime - firstProcessStartTime
Throughput = totalProcesses / totalExecutionTime (processes/sec)
Avg. Wait Time = sum(waitTime) / totalProcesses
Avg. Turnaround Time = sum(turnaroundTime) / totalProcesses
```

## CSV Export (Optional)

If `--output stats.csv` is specified, write a CSV file with per-process data:

```csv
Name,ArrivalTime,StartTime,EndTime,WaitTime,TurnaroundTime
P1,1,2,102,1,101
P2,50,51,101,1,51
P3,150,151,351,1,201
...
```

**Note**: All times in CSV are relative milliseconds (same as log timestamps) for easier analysis.

# Known Limitations

- **No preemption**: Once a core starts a process, it runs to completion (non-preemptive scheduling)
- **No I/O simulation**: All processes are CPU-bound
- **No memory management**: We're only simulating CPU scheduling
- **Shared memory file size**: Fixed at 1MB (supports ~10,000 processes)
- **No process priorities in FCFS/SJF**: Priority field is only used by Priority scheduler
