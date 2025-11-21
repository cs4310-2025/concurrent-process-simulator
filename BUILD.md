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

# Running

```bash
java -jar build/process-injector.jar --workload workloads/workload.txt | \
    java -jar build/cpu-simulator.jar --cores 4 --scheduler FCFS --ipc pipe
```
