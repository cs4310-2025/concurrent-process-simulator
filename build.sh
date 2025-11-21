#!/usr/bin/env bash

echo "=== Building CPU Simulator ==="

# 1. Clean previous build
rm -rf build/
mkdir -p build/classes

# 2. Compile all Java files
echo "Compiling..."
javac -d build/classes \
    src/com/simulator/core/*.java \
    src/com/simulator/injector/*.java

# 3. Create JARs
echo "Creating JARs..."
jar cfe build/cpu-simulator.jar com.simulator.core.CPUSimulator \
    -C build/classes com/simulator/core
jar cfe build/process-injector.jar com.simulator.injector.ProcessInjector \
    -C build/classes com/simulator/injector

echo "=== Build Complete ==="
echo "Artifacts created:"
echo "  - build/cpu-simulator.jar"
echo "  - build/process-injector.jar"
echo ""
echo "Usage example:"
echo "  java -jar build/process-injector.jar --workload workloads/workload.txt | \\"
echo "    java -jar build/cpu-simulator.jar --cores 4 --scheduler FCFS --ipc pipe"
