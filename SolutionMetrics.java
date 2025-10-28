import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * ${user}blackcontractor@farid
 */
public class SolutionMetrics {
    private static final String OUTPUT_DIR = "metrics";
    private static final String SUMMARY_FILE = OUTPUT_DIR + "/summary.csv";
    private static final String CUMULATIVE_PREFIX = "summary_all";

    public static void saveRunToCSV(Solution solution, String algorithm, int populationSize, int generations, double F, double CR) {
        try {
            Files.createDirectories(Paths.get(OUTPUT_DIR));

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = OUTPUT_DIR + "/run_" + timestamp + ".csv";

            try (PrintWriter out = new PrintWriter(filename)) {
                out.println("Algorithm,PopulationSize,Generations,F,CR,Fitness,Distance,Penalty,TWViolations,Feasible");
                out.printf("%s,%d,%d,%.2f,%.2f,%.4f,%.4f,%.4f,%d,%s%n",
                        algorithm, populationSize, generations, F, CR,
                        solution.fitness, solution.totalDistance,
                        solution.totalPenalty, solution.timeWindowViolations,
                        solution.isFeasible());
            }

            // Append to summary.csv (fixed file)
            File summaryFile = new File(SUMMARY_FILE);
            boolean writeHeader = !summaryFile.exists();

            try (PrintWriter out = new PrintWriter(new FileWriter(SUMMARY_FILE, true))) {
                if (writeHeader) {
                    out.println("Algorithm,PopulationSize,Generations,F,CR,Fitness,Distance,Penalty,TWViolations,Feasible,Timestamp");
                }
                out.printf("%s,%d,%d,%.2f,%.2f,%.4f,%.4f,%.4f,%d,%s,%s%n",
                        algorithm, populationSize, generations, F, CR,
                        solution.fitness, solution.totalDistance,
                        solution.totalPenalty, solution.timeWindowViolations,
                        solution.isFeasible(), timestamp);
            }

            // Append to incrementally named summary_all_###.csv
            File cumulativeFile = findNextAvailableCumulativeFile();
            try (PrintWriter out = new PrintWriter(new FileWriter(cumulativeFile, false))) {
                out.println("Algorithm,PopulationSize,Generations,F,CR,Fitness,Distance,Penalty,TWViolations,Feasible,Timestamp");
                out.printf("%s,%d,%d,%.2f,%.2f,%.4f,%.4f,%.4f,%d,%s,%s%n",
                        algorithm, populationSize, generations, F, CR,
                        solution.fitness, solution.totalDistance,
                        solution.totalPenalty, solution.timeWindowViolations,
                        solution.isFeasible(), timestamp);
            }

        } catch (IOException e) {
            System.err.println(" Error writing metrics: " + e.getMessage());
        }
    }

    private static File findNextAvailableCumulativeFile() {
        for (int i = 1; i <= 1000; i++) {
            String filename = String.format("%s/%s_%03d.csv", OUTPUT_DIR, CUMULATIVE_PREFIX, i);
            File file = new File(filename);
            if (!file.exists()) {
                return file;
            }
        }
        // fallback
        String fallbackName = String.format("%s/%s_backup_%s.csv", OUTPUT_DIR, CUMULATIVE_PREFIX,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));
        return new File(fallbackName);
    }

    public static void saveRunToCSV(Solution bestSolution, String algorithm, int populationSize, int maxGenerations,
                                    double scalingFactor, double crossoverRate, String runId) {
        // Optional overload for custom tracking (e.g. by runId)
        saveRunToCSV(bestSolution, algorithm, populationSize, maxGenerations, scalingFactor, crossoverRate);
    }
}
