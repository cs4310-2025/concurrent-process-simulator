TODO Cross-platform instructions

# Building

```bash
# 1. Compile all Java files
javac -d build/classes \
    src/com/simulator/core/*.java \
    src/com/simulator/injector/*.java

# 2. Create JARs
cd build/classes
jar cfe ../cpu-simulator.jar com.simulator.core.CPUSimulator \
    com/simulator/core/*.class
jar cfe ../process-injector.jar com.simulator.injector.ProcessInjector \
    com/simulator/injector/*.class
```

# Running

```bash
java -jar build/process-injector.jar --workload workloads/workload.txt | \
    java -jar build/cpu-simulator.jar --cores 4 --scheduler FCFS --ipc pipe
```
