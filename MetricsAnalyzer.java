import java.io.*;
import java.util.*;
import java.util.function.Consumer;

/**
 * ${user}blackcontractor@farid
 */
public class MetricsAnalyzer {

    private static final String METRICS_DIR = "metrics";
    private static final String SUMMARY_PREFIX = "summary"; // private static final String SUMMARY_PREFIX = "summary_all_";
    private static final String SUMMARY_SUFFIX = ".csv";

    public static void analyzeSummaryCSV(String summaryFilePath, Consumer<String> logger) {
        File file = new File(summaryFilePath);
        if (!file.exists()) {
            logger.accept("No summary file found at: " + summaryFilePath);
            return;
        }

        analyzeCSVFile(file, logger);
    }

    public static void analyzeLatestSummary(Consumer<String> logger) {
        File latest = findLatestSummaryFile();
        if (latest == null) {
            logger.accept("No summary_all_.csv file found in: " + METRICS_DIR);
        } else {
            logger.accept("Analyzing: " + latest.getName());
            analyzeCSVFile(latest, logger);
        }
    }

    private static void analyzeCSVFile(File file, Consumer<String> logger) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String header = reader.readLine(); // Skip header
            if (header == null || !header.contains("Fitness")) {
                logger.accept(" Invalid or missing CSV header.");
                return;
            }

            List<Double> fitnessValues = new ArrayList<>();
            List<Double> distances = new ArrayList<>();
            List<Double> penalties = new ArrayList<>();
            int feasibleCount = 0;
            int total = 0;

            String line;
            while ((line = reader.readLine()) != null) {
                String[] tokens = line.split(",");
                if (tokens.length < 11) continue;

                try {
                    double fitness = Double.parseDouble(tokens[5]);
                    double distance = Double.parseDouble(tokens[6]);
                    double penalty = Double.parseDouble(tokens[7]);
                    boolean feasible = Boolean.parseBoolean(tokens[9]);

                    fitnessValues.add(fitness);
                    distances.add(distance);
                    penalties.add(penalty);
                    if (feasible) feasibleCount++;
                    total++;
                } catch (NumberFormatException e) {
                    logger.accept(" Skipping malformed line: " + line);
                }
            }

            if (total == 0) {
                logger.accept(" No valid data found in summary.");
                return;
            }

            logger.accept("\n Aggregate Analysis:");
            logger.accept(String.format("  Total runs: %d", total));
            logger.accept(String.format("  Feasible: %d (%.2f%%)", feasibleCount, 100.0 * feasibleCount / total));
            logger.accept(String.format("  Best fitness: %.4f", Collections.min(fitnessValues)));
            logger.accept(String.format("  Worst fitness: %.4f", Collections.max(fitnessValues)));
            logger.accept(String.format("  Avg fitness: %.4f", fitnessValues.stream().mapToDouble(d -> d).average().orElse(0)));
            logger.accept(String.format("  Avg distance: %.4f", distances.stream().mapToDouble(d -> d).average().orElse(0)));
            logger.accept(String.format("  Avg penalty: %.4f", penalties.stream().mapToDouble(d -> d).average().orElse(0)));

        } catch (IOException e) {
            logger.accept(" Failed to read file: " + e.getMessage());
        }
    }

    private static File findLatestSummaryFile() {
        File dir = new File(METRICS_DIR);
        if (!dir.exists() || !dir.isDirectory()) return null;

        File[] candidates = dir.listFiles((d, name) -> name.startsWith(SUMMARY_PREFIX) && name.endsWith(SUMMARY_SUFFIX));
        if (candidates == null || candidates.length == 0) return null;

        Arrays.sort(candidates, Comparator.comparing(File::getName).reversed());
        return candidates[0];
    }
    
    public static File getLatestSummaryFile() {
        return findLatestSummaryFile();
    }

}
