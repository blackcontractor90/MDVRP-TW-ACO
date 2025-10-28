import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.chart.*;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javax.imageio.ImageIO;
import java.io.*;
import java.util.*;

/**
 * ${user}blackcontractor@farid
 */
public class MetricsChartViewer {

    public static void show(String csvPath) {
        Platform.runLater(() -> {
            Stage stage = new Stage();
            stage.setTitle("Metrics Scatter Plot");

            VBox root = new VBox(10);
            root.setStyle("-fx-padding: 10; -fx-background-color: white;");

            List<String> runLabels = new ArrayList<>();
            List<Double> fitness = new ArrayList<>();
            List<Double> distance = new ArrayList<>();
            List<Double> penalty = new ArrayList<>();
            List<Boolean> feasible = new ArrayList<>();
            List<String> tooltips = new ArrayList<>();

            int feasibleCount = 0, infeasibleCount = 0;
            double bestFitness = Double.POSITIVE_INFINITY;
            double worstFitness = Double.NEGATIVE_INFINITY;
            double totalFitness = 0;

            try (BufferedReader reader = new BufferedReader(new FileReader(csvPath))) {
                String header = reader.readLine(); // skip header
                String line;
                int runIndex = 1;
                while ((line = reader.readLine()) != null) {
                    String[] tokens = line.split(",");
                    if (tokens.length < 11) continue;

                    double fit = Double.parseDouble(tokens[5]);
                    double dist = Double.parseDouble(tokens[6]);
                    double pen = Double.parseDouble(tokens[7]);
                    boolean feas = Boolean.parseBoolean(tokens[9]);
                    String timestamp = tokens[10];

                    runLabels.add("Run " + runIndex);
                    fitness.add(fit);
                    distance.add(dist);
                    penalty.add(pen);
                    feasible.add(feas);

                    String tip = String.format(
                        "Fitness: %.2f\nDistance: %.2f\nPenalty: %.2f\nFeasible: %s\nTimestamp: %s",
                        fit, dist, pen, feas ? "YES" : "NO", timestamp
                    );
                    tooltips.add(tip);

                    if (fit < bestFitness) bestFitness = fit;
                    if (fit > worstFitness) worstFitness = fit;
                    totalFitness += fit;
                    if (feas) feasibleCount++; else infeasibleCount++;
                    runIndex++;
                }
            } catch (Exception e) {
                System.err.println(" Failed to load metrics CSV: " + e.getMessage());
                return;
            }

            int total = feasibleCount + infeasibleCount;
            double feasiblePct = total > 0 ? (100.0 * feasibleCount / total) : 0.0;
            double avgFitness = total > 0 ? totalFitness / total : 0.0;

            Label summaryLabel = new Label(String.format(
                "Best Fitness: %.2f   Avg Fitness: %.2f   Worst Fitness: %.2f\nFeasible: %d / %d (%.2f%%)",
                bestFitness, avgFitness, worstFitness, feasibleCount, total, feasiblePct
            ));

            // Create scatter chart for Fitness
            CategoryAxis xAxis = new CategoryAxis();
            NumberAxis yAxis = new NumberAxis();
            xAxis.setLabel("Run Index");
            yAxis.setLabel("Fitness");
            xAxis.setTickLabelRotation(60);

            ScatterChart<String, Number> scatterChart = new ScatterChart<>(xAxis, yAxis);
            scatterChart.setTitle("Fitness per Run (Feasible=Green, Infeasible=Red)");
            scatterChart.setLegendVisible(false);
            scatterChart.setMinHeight(350);

            XYChart.Series<String, Number> fitnessSeries = new XYChart.Series<>();
            for (int i = 0; i < runLabels.size(); i++) {
                XYChart.Data<String, Number> data = new XYChart.Data<>(runLabels.get(i), fitness.get(i));
                fitnessSeries.getData().add(data);
            }
            scatterChart.getData().add(fitnessSeries);

            // Style and tooltip for each point
            for (int i = 0; i < runLabels.size(); i++) {
                final int idx = i;
                XYChart.Data<String, Number> data = fitnessSeries.getData().get(idx);
                boolean feas = feasible.get(idx);
                String color = feas ? "green" : "red";
                data.nodeProperty().addListener((obs, oldNode, newNode) -> {
                    if (newNode != null)
                        newNode.setStyle("-fx-background-color: " + color + ", white; -fx-background-radius: 8px;");
                });
                // In case node already exists
                if (data.getNode() != null)
                    data.getNode().setStyle("-fx-background-color: " + color + ", white; -fx-background-radius: 8px;");
                Tooltip.install(data.getNode(), new Tooltip(tooltips.get(idx)));
            }

            // Also, add a multi-series line chart for comparison (optional)
            LineChart<String, Number> multiChart = createMultiLineChart(
                "Fitness, Distance, Penalty over Runs", "Run Index", "Value",
                runLabels, fitness, distance, penalty
            );

            root.getChildren().addAll(summaryLabel, scatterChart, multiChart);

            stage.setScene(new Scene(root, 1100, 800));
            stage.show();

            // Save combined PNG of the displayed charts and summary
            saveCombinedPng(root, "metrics_summary_combined");
        });
    }

    // Optional: Multi-series line chart for all metrics
    private static LineChart<String, Number> createMultiLineChart(
            String title, String xLabel, String yLabel,
            List<String> labels,
            List<Double> fitness, List<Double> distance, List<Double> penalty
    ) {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel(xLabel);
        yAxis.setLabel(yLabel);
        xAxis.setTickLabelRotation(60);

        LineChart<String, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle(title);
        chart.setLegendVisible(true);
        chart.setCreateSymbols(true);
        chart.setMinHeight(250);

        XYChart.Series<String, Number> fitnessSeries = new XYChart.Series<>();
        fitnessSeries.setName("Fitness");
        XYChart.Series<String, Number> distanceSeries = new XYChart.Series<>();
        distanceSeries.setName("Distance");
        XYChart.Series<String, Number> penaltySeries = new XYChart.Series<>();
        penaltySeries.setName("Penalty");

        for (int i = 0; i < labels.size(); i++) {
            fitnessSeries.getData().add(new XYChart.Data<>(labels.get(i), fitness.get(i)));
            distanceSeries.getData().add(new XYChart.Data<>(labels.get(i), distance.get(i)));
            penaltySeries.getData().add(new XYChart.Data<>(labels.get(i), penalty.get(i)));
        }

        chart.getData().addAll(fitnessSeries, distanceSeries, penaltySeries);
        return chart;
    }

    // Save VBox (whole chart area) as PNG
    private static void saveCombinedPng(VBox container, String filenameBase) {
        WritableImage image = container.snapshot(new SnapshotParameters() {{
            setFill(Color.TRANSPARENT);
        }}, null);

        File dir = new File("output/metrics_charts");
        if (!dir.exists()) dir.mkdirs();

        File file = new File(dir, filenameBase + ".png");
        try {
            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file);
            System.out.println(" Combined chart saved: " + file.getAbsolutePath());
        } catch (IOException e) {
            System.err.println(" Failed to save combined chart image: " + e.getMessage());
        }
    }
}