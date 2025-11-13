/**
 * A stubbed data object for M1. It only contains the burstTime,
 * which is the minimum data needed for the smoke test.
 */
public class Process {
    public final int burstTime;

    public Process(int burstTime) {
        this.burstTime = burstTime;
    }

    /**
     * Minimal CSV parser for M1.
     * Assumes CSV format: Name,ArrivalTimeMs,BurstTimeMs,Priority
     * Brittle: Will fail if format is wrong, which is fine for M1.
     */
    public static Process fromCSV(String csvLine) {
        try {
            String[] parts = csvLine.split(",");
            int burst = Integer.parseInt(parts[2].trim());
            return new Process(burst);
        } catch (Exception e) {
            // In M1, we log to stderr and return a "dummy" process
            System.err.println("M1 STUB: Failed to parse line: " + csvLine);
            return new Process(1); // Return a 1ms burst process
        }
    }
}
