import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import org.jfree.chart.*;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.*;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

/**
 * ${user}blackcontractor@farid
 */
public class RouteExporter {

    // ===== Per-Route Export =====
    public static void exportToCSV(List<Route> routes, String filename) throws IOException {
        File resultsDir = new File("results");
        if (!resultsDir.exists()) resultsDir.mkdirs();

        if (filename == null || filename.trim().isEmpty()) {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            filename = "routes_summary_" + timestamp + ".csv";
        }

        File outputFile = new File(resultsDir, filename);
        File latestFile = new File(resultsDir, "routes_summary_latest.csv");

        writeRoutesToFile(routes, outputFile);
        writeRoutesToFile(routes, latestFile);

        System.out.println(" Routes exported to: " + outputFile.getAbsolutePath());
        System.out.println(" Latest summary updated: " + latestFile.getAbsolutePath());

        // Generate plots automatically
        exportRouteScatterPlot(routes);
        exportRouteLayoutPlot(routes);

        // Update accumulated totals chart after every run
        exportAccumulatedDashboard();
    }

    private static void writeRoutesToFile(List<Route> routes, File file) throws IOException {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("RouteID,Depot,Customers,TotalDistance,Duration,Cost,Penalty\n");

            int routeNum = 1;
            double totalDistance = 0, totalDuration = 0, totalCost = 0, totalPenalty = 0;

            for (Route route : routes) {
                route.recompute();
                StringBuilder customerSeq = new StringBuilder();
                for (int i = 0; i < route.customers.size(); i++) {
                    customerSeq.append(route.customers.get(i).id);
                    if (i < route.customers.size() - 1) customerSeq.append("-");
                }

                totalDistance += route.totalDistance;
                totalDuration += route.getCachedDuration();
                totalCost += route.getCachedCost();
                totalPenalty += route.getPenalty();

                writer.write(String.format(
                        "%d,%s,%s,%.2f,%.2f,%.2f,%.2f\n",
                        routeNum++,
                        (route.depot != null ? route.depot.name : "NoDepot"),
                        customerSeq,
                        route.totalDistance,
                        route.getCachedDuration(),
                        route.getCachedCost(),
                        route.getPenalty()
                ));
            }

            // Totals row
            writer.write(String.format(
                    "TOTAL,-,-,%.2f,%.2f,%.2f,%.2f\n",
                    totalDistance, totalDuration, totalCost, totalPenalty
            ));
        }
    }

    // ===== Scatter Plot of Routes =====
    private static void exportRouteScatterPlot(List<Route> routes) {
        try {
            XYSeriesCollection dataset = new XYSeriesCollection();
            int routeIdx = 1;

            for (Route route : routes) {
                XYSeries series = new XYSeries("Route " + routeIdx++);
                if (route.depot != null) {
                    series.add(route.depot.x, route.depot.y);
                }
                for (Customer c : route.customers) {
                    series.add(c.x, c.y);
                }
                dataset.addSeries(series);
            }

            JFreeChart chart = ChartFactory.createScatterPlot(
                    "Route Scatter Plot",
                    "X", "Y",
                    dataset,
                    PlotOrientation.VERTICAL,
                    true, true, false
            );

            XYPlot plot = (XYPlot) chart.getPlot();
 // Fix: set orientation explicitly

            XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
            for (int i = 0; i < dataset.getSeriesCount(); i++) {
                renderer.setSeriesLinesVisible(i, true);
                renderer.setSeriesShapesVisible(i, true);
            }
            plot.setRenderer(renderer);

            savePlot(chart, "route_scatter");
        } catch (Exception e) {
            System.err.println("[RouteExporter] Error saving route scatter plot: " + e.getMessage());
        }
    }

    // ===== Route Layout Visualization =====
    private static void exportRouteLayoutPlot(List<Route> routes) {
        try {
            XYSeriesCollection dataset = new XYSeriesCollection();
            int routeIdx = 1;

            for (Route route : routes) {
                XYSeries series = new XYSeries("Route " + routeIdx++);
                if (route.depot != null) {
                    series.add(route.depot.x, route.depot.y);
                }
                for (Customer c : route.customers) {
                    series.add(c.x, c.y);
                }
                if (route.depot != null) {
                    series.add(route.depot.x, route.depot.y);
                }
                dataset.addSeries(series);
            }

            JFreeChart chart = ChartFactory.createXYLineChart(
                    "Route Layout Visualization",
                    "X", "Y",
                    dataset,
                    PlotOrientation.VERTICAL,
                    true, true, false
            );

            XYPlot plot = (XYPlot) chart.getPlot();
 // ✅ Fix: set orientation explicitly

            savePlot(chart, "route_layout");
        } catch (Exception e) {
            System.err.println("[RouteExporter] Error saving route layout plot: " + e.getMessage());
        }
    }

    // ===== All-Time Accumulated Dashboard =====
    private static void exportAccumulatedDashboard() {
        try {
            File resultsDir = new File("results");
            if (!resultsDir.exists()) resultsDir.mkdirs();

            double totalDistance = 0, totalDuration = 0, totalCost = 0, totalPenalty = 0;

            // Scan all per-route summaries
            File[] files = resultsDir.listFiles((dir, name) -> name.startsWith("routes_summary_") && name.endsWith(".csv"));
            if (files == null || files.length == 0) {
                System.err.println("[RouteExporter] No summary files found for accumulated dashboard.");
                return;
            }

            for (File f : files) {
                try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                    String line;
                    br.readLine(); // skip header
                    while ((line = br.readLine()) != null) {
                        String[] parts = line.split(",");
                        if (parts[0].equalsIgnoreCase("TOTAL")) {
                            totalDistance += Double.parseDouble(parts[3]);
                            totalDuration += Double.parseDouble(parts[4]);
                            totalCost += Double.parseDouble(parts[5]);
                            totalPenalty += Double.parseDouble(parts[6]);
                        }
                    }
                } catch (Exception ex) {
                    System.err.println("[RouteExporter] Error reading " + f.getName() + ": " + ex.getMessage());
                }
            }

            // Dataset with cumulative metrics
            DefaultCategoryDataset dataset = new DefaultCategoryDataset();
            dataset.addValue(totalDistance, "Metrics", "Distance");
            dataset.addValue(totalDuration, "Metrics", "Duration");
            dataset.addValue(totalCost, "Metrics", "Cost");
            dataset.addValue(totalPenalty, "Metrics", "Penalty");

            CategoryPlot plot = new CategoryPlot(dataset,
                    new CategoryAxis("Metrics"),
                    new NumberAxis("Cumulative Value"),
                    new BarRenderer());
            plot.setOrientation(PlotOrientation.VERTICAL);

            JFreeChart chart = new JFreeChart("All-Time Accumulated Performance",
                    JFreeChart.DEFAULT_TITLE_FONT, plot, false);

            savePlot(chart, "routes_accumulated_dashboard");

        } catch (IOException e) {
            System.err.println("[RouteExporter] Error generating accumulated dashboard: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ===== Helper to Save Plots =====
    private static void savePlot(JFreeChart chart, String prefix) throws IOException {
        File plotsDir = new File("results/plots");
        if (!plotsDir.exists()) plotsDir.mkdirs();

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File file = new File(plotsDir, prefix + "_" + timestamp + ".png");
        ChartUtilities.saveChartAsPNG(file, chart, 800, 600);

        File latest = new File(plotsDir, prefix + "_latest.png");
        ChartUtilities.saveChartAsPNG(latest, chart, 800, 600);

        System.out.println(" Plot saved: " + file.getAbsolutePath());
        System.out.println(" Latest plot updated: " + latest.getAbsolutePath());
    }

    // ===== Overall Totals Export =====
    public static void exportSummary(List<Route> routes, String filename) {
        try {
            File resultsDir = new File("results");
            if (!resultsDir.exists()) resultsDir.mkdirs();

            if (filename == null || filename.trim().isEmpty()) {
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                filename = "routes_overall_summary_" + timestamp + ".csv";
            }

            File outputFile = new File(resultsDir, filename);
            File latestFile = new File(resultsDir, "routes_overall_summary_latest.csv");

            int totalRoutes = routes.size();
            int totalCustomers = 0;
            double totalDistance = 0, totalDuration = 0, totalCost = 0, totalPenalty = 0;

            for (Route r : routes) {
                r.recompute();
                totalCustomers += r.customers.size();
                totalDistance += r.totalDistance;
                totalDuration += r.getCachedDuration();
                totalCost += r.getCachedCost();
                totalPenalty += r.getPenalty();
            }

            writeSummaryToFile(outputFile, totalRoutes, totalCustomers,
                    totalDistance, totalDuration, totalCost, totalPenalty);

            writeSummaryToFile(latestFile, totalRoutes, totalCustomers,
                    totalDistance, totalDuration, totalCost, totalPenalty);

            System.out.println(" Overall summary exported to: " + outputFile.getAbsolutePath());
            System.out.println(" Latest overall summary updated: " + latestFile.getAbsolutePath());

        } catch (IOException e) {
            System.err.println("[RouteExporter] Error exporting summary: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void writeSummaryToFile(File file,
                                           int totalRoutes, int totalCustomers,
                                           double totalDistance, double totalDuration,
                                           double totalCost, double totalPenalty) throws IOException {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("Metric,Value\n");
            writer.write("Total Routes," + totalRoutes + "\n");
            writer.write("Total Customers," + totalCustomers + "\n");
            writer.write("Total Distance," + String.format("%.2f", totalDistance) + "\n");
            writer.write("Total Duration," + String.format("%.2f", totalDuration) + "\n");
            writer.write("Total Cost," + String.format("%.2f", totalCost) + "\n");
            writer.write("Total Penalty," + String.format("%.2f", totalPenalty) + "\n");
        }
    }
}
