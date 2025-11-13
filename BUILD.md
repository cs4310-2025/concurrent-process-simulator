# Temporary File

The build system is very messy right now.

## Build

```bash
# Yes, organization is poop right now.
cd ./src

# Build
javac -d bin ../src/com/simulator/*/*.java

# Run
java -cp bin com.simulator.injector.ProcessInjector --workload ../workloads/workload.txt | java -cp bin com.simulator.core.CPUSimulator
```
