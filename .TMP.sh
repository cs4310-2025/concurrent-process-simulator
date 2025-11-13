#!/usr/bin/env bash

# NOTE: This file will be used to come up with real tests once a proper build script is made.

# Yes, organization is poop right now.
cd ./src || exit
javac -d bin ../src/com/simulator/*/*.java

# TESTS (simple one core)

# # FCFS
# java -cp bin com.simulator.injector.ProcessInjector --workload ../workloads/workload.txt | java -cp bin com.simulator.core.CPUSimulator --cores 1 --scheduler FCFS --ipc pipe
#
# # SJF
# java -cp bin com.simulator.injector.ProcessInjector --workload ../workloads/workload-sjf.txt | java -cp bin com.simulator.core.CPUSimulator --cores 1 --scheduler SJF --ipc pipe
#
# # PRIO
# java -cp bin com.simulator.injector.ProcessInjector --workload ../workloads/workload-prio.txt | java -cp bin com.simulator.core.CPUSimulator --cores 1 --scheduler PRIORITY --ipc pipe

# Multicore
java -cp bin com.simulator.injector.ProcessInjector --workload ../workloads/workload-multicore.txt | java -cp bin com.simulator.core.CPUSimulator --cores 4 --scheduler PRIORITY --ipc pipe

# # Generate experiment data
# javac -d bin ../src/com/simulator/*/*.java; java -cp bin com.simulator.utils.WorkloadGenerator --type exp1 --count 25
# javac -d bin ../src/com/simulator/*/*.java; java -cp bin com.simulator.utils.WorkloadGenerator --type exp2 --count 25
# javac -d bin ../src/com/simulator/*/*.java; java -cp bin com.simulator.utils.WorkloadGenerator --type exp3 --count 25
