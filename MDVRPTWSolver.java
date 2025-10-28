import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.prefs.Preferences;

import javax.imageio.ImageIO;



/**
 * MDVRPTWSolver (legacy-style, patched & modernized lightly)
 *
 * - Keeps legacy menu flow and console logging.
 * - Adds File → Load Instance… and File → Save Routes Summary…
 * - Supports Cordeau MDVRP/VRPTW dataset format (auto-detect) and CSV fallback.
 * - Integrates with ACO via reflection (constructor-agnostic).
 * - Maintains chart + ACO sliders from previous patch.
 *
 * NOTE:
 *   This class expects the patched domain classes:
 *     Customer, Depot, Vehicle, Route, Solution, RouteExporter
 */

/**
 * ${user}blackcontractor@farid
 */
public class MDVRPTWSolver extends Application {

    // ====== UI ======
    private LineChart<Number, Number> chart;
    private XYChart.Series<Number, Number> bestSeries;
    private Preferences prefs;

    // ACO Hyperparameter sliders
    private Slider alphaSlider, betaSlider, rhoSlider, q0Slider;

    // ====== Problem Data ======
    public final List<Customer> customers = new ArrayList<>();
    public final List<Depot> depots = new ArrayList<>();

    // Keep a reference to the "last solution" routes if you want to export summaries
    public final List<Route> lastSolutionRoutes = new ArrayList<>();

    @Override
    public void start(Stage primaryStage) {
        prefs = Preferences.userNodeForPackage(MDVRPTWSolver.class);

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        // ----- Menu (legacy-style) -----
        MenuBar menuBar = new MenuBar();
        Menu fileMenu = new Menu("File");
        MenuItem loadItem = new MenuItem("Load Instance...");
        MenuItem saveRoutesSummaryItem = new MenuItem("Save Routes Summary...");
        MenuItem exitItem = new MenuItem("Exit");
        fileMenu.getItems().addAll(loadItem, saveRoutesSummaryItem, new SeparatorMenuItem(), exitItem);

        Menu toolsMenu = new Menu("Tools");
        MenuItem runACOItem = new MenuItem("Run ACO");
        toolsMenu.getItems().addAll(runACOItem);

        menuBar.getMenus().addAll(fileMenu, toolsMenu);

        loadItem.setOnAction(e -> onLoadInstance(primaryStage));
        saveRoutesSummaryItem.setOnAction(e -> onSaveRoutesSummary(primaryStage));
        exitItem.setOnAction(e -> Platform.exit());
        runACOItem.setOnAction(e -> runACO());

        root.setTop(menuBar);

        // ----- Chart -----
        NumberAxis xAxis = new NumberAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("Iteration");
        yAxis.setLabel("Best Distance");
        chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle("ACO Optimization Progress");
        bestSeries = new XYChart.Series<>();
        bestSeries.setName("Best Distance");
        chart.getData().add(bestSeries);

        // ----- Right panel with ACO hyperparameters -----
        VBox rightPanel = new VBox(12);
        rightPanel.setPadding(new Insets(10));
        rightPanel.setAlignment(Pos.TOP_CENTER);
        rightPanel.setPrefWidth(300);
        rightPanel.setStyle("-fx-background-color: #f4f4f4; -fx-border-color: #ccc;");

        Label title = new Label("ACO Hyperparameters");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        alphaSlider = createSlider("alpha", 0, 5, 1.0, 0.1);
        betaSlider  = createSlider("beta",  0, 10, 2.0, 0.1);
        rhoSlider   = createSlider("rho",   0, 1,  0.5, 0.01);
        q0Slider    = createSlider("q0",    0, 1,  0.9, 0.01);

        rightPanel.getChildren().addAll(
            title,
            makeLabeledSlider("Alpha (α)", alphaSlider, 2),
            makeLabeledSlider("Beta (β)",  betaSlider,  2),
            makeLabeledSlider("Rho (ρ)",   rhoSlider,   3),
            makeLabeledSlider("Q₀",        q0Slider,    3),
            new Button("Run ACO") {{
                setOnAction(e -> runACO());
                setMaxWidth(Double.MAX_VALUE);
            }}
        );

        // Layout
        root.setCenter(chart);
        root.setRight(rightPanel);

        Scene scene = new Scene(root, 1200, 700);
        primaryStage.setScene(scene);
        primaryStage.setTitle("MDVRPTW Solver (ACO)");
        primaryStage.show();

        System.out.println("[MDVRPTWSolver] Ready. Use File → Load Instance… to import a dataset.");
    }

    // =====================================================
    // =============== Legacy-style Console API ============
    // =====================================================

    public int getCustomerCount() {
        return customers.size();
    }

    public void decodeSolution(Solution sol) {
        sol.routes.clear();
        Route currentRoute = null;

        for (int gene : sol.chromosome) {
            if (gene == -1) {
                if (currentRoute != null && !currentRoute.customers.isEmpty()) {
                    sol.addRoute(currentRoute);
                }
                currentRoute = null;
            } else if (gene >= 0 && gene < customers.size()) {
                if (currentRoute == null) {
                    Depot defaultDepot = depots.isEmpty() ? null : depots.get(0);
                    currentRoute = new Route(defaultDepot);
                }
                currentRoute.addCustomer(customers.get(gene));
            } else if (gene >= customers.size()) {
                int depotIndex = gene - customers.size();
                if (depotIndex >= 0 && depotIndex < depots.size()) {
                    if (currentRoute != null && !currentRoute.customers.isEmpty()) {
                        sol.addRoute(currentRoute);
                    }
                    currentRoute = new Route(depots.get(depotIndex));
                }
            }
        }

        if (currentRoute != null && !currentRoute.customers.isEmpty()) {
            sol.addRoute(currentRoute);
        }
    }

    public void evaluateSolution(Solution sol) {
        decodeSolution(sol);
        sol.evaluate();
        // update lastSolutionRoutes reference (shallow copy of references)
        lastSolutionRoutes.clear();
        lastSolutionRoutes.addAll(sol.routes);
    }

    /** Distance helper (customers/depot[0] fallback) */
    public double getDistance(int fromId, int toId) {
        if (fromId < customers.size() && toId < customers.size()) {
            return customers.get(fromId).distanceTo(customers.get(toId));
        } else if (fromId < customers.size() && !depots.isEmpty()) {
            return customers.get(fromId).distanceTo(depots.get(0));
        } else if (toId < customers.size() && !depots.isEmpty()) {
            return customers.get(toId).distanceTo(depots.get(0));
        }
        return 0.0;
    }

    // =====================================================
    // =================== File → Load =====================
    // =====================================================

    private void onLoadInstance(Stage stage) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Load Instance (Cordeau .txt/.dat or CSV)");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Supported", "*.txt", "*.dat", "*.csv"),
                new FileChooser.ExtensionFilter("Text", "*.txt"),
                new FileChooser.ExtensionFilter("Data", "*.dat"),
                new FileChooser.ExtensionFilter("CSV", "*.csv"),
                new FileChooser.ExtensionFilter("All files", "*.*")
        );
        File f = fc.showOpenDialog(stage);
        if (f == null) return;
        try {
            loadInstance(f);
            System.out.println("[MDVRPTWSolver] Loaded instance from " + f.getAbsolutePath() +
                    " | depots=" + depots.size() + " customers=" + customers.size());
        } catch (Exception ex) {
            System.out.println("[MDVRPTWSolver] ERROR loading instance: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void loadInstance(File file) throws IOException {
        // clear current data
        depots.clear();
        customers.clear();

        String name = file.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".csv")) {
            parseCSV(file);
        } else {
            parseCordeauLike(file);
        }
    }

    /** CSV format (flexible headers). Expected columns:
     * id,x,y,demand,ready,due,service,type,name,maxVehicles,vehicleCapacity,depotId
     * - type: "depot" or "customer" (case-insensitive). If missing, infer by demand==0 && service==0.
     */
    private void parseCSV(File file) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String header = br.readLine();
            if (header == null) return;
            String[] cols = header.split("\\s*,\\s*");
            Map<String,Integer> map = new HashMap<>();
            for (int i = 0; i < cols.length; i++) map.put(cols[i].toLowerCase(Locale.ROOT), i);

            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] t = line.split("\\s*,\\s*");
                String type = get(t, map, "type", "");
                String name = get(t, map, "name", null);
                int id = asInt(get(t, map, "id", "-1"));
                double x = asDbl(get(t, map, "x", "0"));
                double y = asDbl(get(t, map, "y", "0"));
                int demand = asInt(get(t, map, "demand", "0"));
                double ready = asDbl(get(t, map, "ready", "0"));
                double due = asDbl(get(t, map, "due", "999999"));
                double service = asDbl(get(t, map, "service", "0"));
                int maxVehicles = asInt(get(t, map, "maxvehicles", "1"));
                int vehicleCapacity = asInt(get(t, map, "vehiclecapacity", "100"));
                int depotId = asInt(get(t, map, "depotid", "-1"));

                boolean isDepot = type.equalsIgnoreCase("depot");
                if (!isDepot && type.isEmpty()) {
                    // infer
                    isDepot = (demand == 0 && service == 0);
                }

                if (isDepot) {
                    Depot d = new Depot(id >= 0 ? id : (depots.size()+1), x, y, vehicleCapacity, maxVehicles);
                    d.maxDuration = 9999.0;
                    d.customers = new Customer[0];
                    depots.add(d);
                } else {
                    Customer c = new Customer(id >= 0 ? id : customers.size()+1, x, y, demand, ready, due, service);
                    c.name = (name != null) ? name : ("C" + c.id);
                    if (depotId >= 0) c.assignedDepotId = depotId;
                    customers.add(c);
                }
            }
        }
        if (depots.isEmpty()) {
            // add a default depot at origin if CSV had only customers
            depots.add(new Depot(1, 0, 0, 100, 1));
        }
    }

    /**
     * Cordeau-like parser (robust):
     * Expected (common) layout:
     *   line1: m n (and optionally Q V ...)
     *   next m lines: depotId x y (optionally: maxVehicles capacity or similar)
     *   next n lines: custId x y demand ready due service
     */
    private void parseCordeauLike(File file) throws IOException {
        List<String> raw = readNonEmptyNonCommentLines(file);

        if (raw.isEmpty()) return;

        // header
        String[] hdrTok = raw.get(0).trim().split("\\s+");
        int m = 0, n = 0;
        Integer Q = null;  // capacity
        Integer V = null;  // vehicles per depot (if provided)

        // Try to detect m, n, (Q), (V)
        try {
            m = Integer.parseInt(hdrTok[0]);
            n = Integer.parseInt(hdrTok[1]);
            if (hdrTok.length >= 3) {
                Q = tryParseInt(hdrTok[2]);
            }
            if (hdrTok.length >= 4) {
                V = tryParseInt(hdrTok[3]);
            }
        } catch (Exception ex) {
            throw new IOException("Unrecognized Cordeau header: \"" + raw.get(0) + "\"");
        }

        int line = 1;

        // read depot lines (m lines)
        for (int i = 0; i < m && line < raw.size(); i++, line++) {
            String[] t = raw.get(line).split("\\s+");
            int id = asIntSafe(t, 0, (i + 1));
            double x = asDblSafe(t, 1, 0);
            double y = asDblSafe(t, 2, 0);
            int cap = (Q != null) ? Q : asIntSafe(t, 3, 100);
            int maxV = (V != null) ? V : asIntSafe(t, 4, 1);

            Depot d = new Depot(id, x, y, cap, maxV);
            d.maxDuration = 9999.0;
            depots.add(d);
        }

        // read customer lines (n lines)
        for (int i = 0; i < n && line < raw.size(); i++, line++) {
            String[] t = raw.get(line).split("\\s+");
            int id = asIntSafe(t, 0, (i + 1));
            double x = asDblSafe(t, 1, 0);
            double y = asDblSafe(t, 2, 0);
            int demand = asIntSafe(t, 3, 0);
            double ready = asDblSafe(t, 4, 0);
            double due = asDblSafe(t, 5, 999999);
            double service = asDblSafe(t, 6, 0);
            Customer c = new Customer(id, x, y, demand, ready, due, service);
            customers.add(c);
        }

        if (depots.isEmpty()) {
            depots.add(new Depot(1, 0, 0, (Q != null ? Q : 100), (V != null ? V : 1)));
        }
    }

    private static List<String> readNonEmptyNonCommentLines(File file) throws IOException {
        List<String> out = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                String s = line.trim();
                if (s.isEmpty()) continue;
                if (s.startsWith("#") || s.startsWith("//")) continue;
                out.add(s);
            }
        }
        return out;
    }

    private static Integer tryParseInt(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return null; }
    }

    private static String get(String[] t, Map<String,Integer> map, String key, String def) {
        Integer idx = map.get(key);
        if (idx == null || idx < 0 || idx >= t.length) return def;
        return t[idx];
    }

    private static int asInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    private static int asIntSafe(String[] t, int idx, int def) {
        if (idx < 0 || idx >= t.length) return def;
        try { return Integer.parseInt(t[idx]); } catch (Exception e) { return def; }
    }

    private static double asDbl(String s) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return 0.0; }
    }

    private static double asDblSafe(String[] t, int idx, double def) {
        if (idx < 0 || idx >= t.length) return def;
        try { return Double.parseDouble(t[idx]); } catch (Exception e) { return def; }
    }

    // =====================================================
    // ============== File → Save Routes Summary ===========
    // =====================================================

    private void onSaveRoutesSummary(Stage stage) {
        List<Route> target = lastSolutionRoutes;
        if (target.isEmpty()) {
            log("No evaluated solution in memory; exporting empty summary.");
            return;
        }

        FileChooser fc = new FileChooser();
        fc.setTitle("Save Routes Summary CSV");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV", "*.csv")
        );
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        fc.setInitialFileName("routes_summary_" + ts + ".csv");

        File chosen = fc.showSaveDialog(stage);

        if (chosen != null) {
            // Explicit path chosen by user
            RouteExporter.exportSummary(target, chosen.getAbsolutePath());
            log("Routes summary saved to " + chosen.getAbsolutePath());

            try {
                visualizeRoutesFromCSV(chosen);
            } catch (Exception ex) {
                log("Could not visualize CSV: " + ex.getMessage());
            }
            visualizeScatterFromRoutes(target);

        } else {
            // Fallback to default results/ folder (RouteExporter handles timestamping + latest copy)
            RouteExporter.exportSummary(target, null);
            log("Routes summary saved to default results folder.");

            File latest = new File("results/routes_summary_latest.csv");
            if (latest.exists()) {
                try {
                    visualizeRoutesFromCSV(latest);
                } catch (Exception ex) {
                    log("Could not visualize CSV: " + ex.getMessage());
                }
            }
            visualizeScatterFromRoutes(target);
        }
    }


    // =====================================================
    // =================== Run ACO (Tools) =================
    // =====================================================

    private void runACO() {
        double alpha = alphaSlider.getValue();
        double beta  = betaSlider.getValue();
        double rho   = rhoSlider.getValue();
        double q0    = q0Slider.getValue();

        bestSeries.getData().clear();

        int numAnts = 50;
        int maxIterations = 200;

        try {
            Object acoInstance = instantiateACO(numAnts, maxIterations, alpha, beta, rho, q0);
            if (acoInstance == null) {
                log("Failed to instantiate AntColonyOptimization: no compatible constructor found.");
                return;
            }

            Runnable runTask = () -> {
                try {
                    if (acoInstance instanceof Runnable) {
                        ((Runnable) acoInstance).run();
                    } else {
                        Method runMethod = null;
                        try {
                            runMethod = acoInstance.getClass().getMethod("run");
                        } catch (NoSuchMethodException ignored) {}
                        if (runMethod != null) {
                            runMethod.invoke(acoInstance);
                        }
                    }

                    // After ACO finishes, export results automatically if there are routes
                    if (!lastSolutionRoutes.isEmpty()) {
                        log("ACO finished. Exporting results automatically...");
                        try {
                            RouteExporter.exportToCSV(lastSolutionRoutes, null);

                            File latestFile = new File("results/routes_summary_latest.csv");
                            Platform.runLater(() -> {
                                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                                alert.setTitle("Export Complete");
                                alert.setHeaderText("ACO Run Finished");
                                alert.setContentText("Routes exported to:\n" 
                                        + latestFile.getAbsolutePath());
                                alert.showAndWait();
                                // visualize automatically after export
                                try {
                                    visualizeRoutesFromCSV(latestFile);
                                } catch (Exception ex) {
                                    log("Could not visualize CSV: " + ex.getMessage());
                                }
                                visualizeScatterFromRoutes(lastSolutionRoutes);

                                // --- Save ACO Optimization Progress chart as image ---
                                saveACOChartAsImage();
                            });

                        } catch (Exception e) {
                            log("Error exporting results: " + e.getMessage());
                        }
                    } else {
                        log("ACO finished but no routes were evaluated; nothing to export.");
                        // --- Save ACO Optimization Progress chart as image anyway (may be empty) ---
                        Platform.runLater(() -> saveACOChartAsImage());
                    }

                } catch (Exception ex) {
                    log("Error while running ACO: " + ex.getMessage());
                    ex.printStackTrace();
                }
            };

            Thread t = new Thread(runTask);
            t.setDaemon(true);
            t.start();

        } catch (Exception ex) {
            log("Error while starting ACO: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    // =====================================================
    // ============== Reflection helpers for ACO ===========
    // =====================================================

    private Object instantiateACO(int numAnts, int maxIterations,
                                  double alpha, double beta, double rho, double q0) {
        try {
            Class<?> acoClass = Class.forName("AntColonyOptimization");

            // (1) Constructor with (MDVRPTWSolver, int, int, double, double, double, double)
            try {
                Constructor<?> c = acoClass.getConstructor(MDVRPTWSolver.class, int.class, int.class,
                        double.class, double.class, double.class, double.class);
                return c.newInstance(this, numAnts, maxIterations, alpha, beta, rho, q0);
            } catch (NoSuchMethodException ignored) {}

            // (2) Constructor with (MDVRPTWSolver, int, int) plus setters
            try {
                Constructor<?> c = acoClass.getConstructor(MDVRPTWSolver.class, int.class, int.class);
                Object inst = c.newInstance(this, numAnts, maxIterations);
                tryInvokeSetter(inst, "setAlpha", alpha);
                tryInvokeSetter(inst, "setBeta", beta);
                tryInvokeSetter(inst, "setRho", rho);
                tryInvokeSetter(inst, "setQ0", q0);
                tryInvokeSetter(inst, "setConvergenceSeries", bestSeries);
                return inst;
            } catch (NoSuchMethodException ignored) {}

            // (3) Constructor with (int, int, double, double, double, double)
            try {
                Constructor<?> c = acoClass.getConstructor(int.class, int.class,
                        double.class, double.class, double.class, double.class);
                return c.newInstance(numAnts, maxIterations, alpha, beta, rho, q0);
            } catch (NoSuchMethodException ignored) {}

            // (4) Default constructor + setters fallback
            try {
                Constructor<?> c = acoClass.getConstructor();
                Object inst = c.newInstance();
                tryInvokeSetter(inst, "setSolver", this);
                tryInvokeSetter(inst, "setNumAnts", numAnts);
                tryInvokeSetter(inst, "setMaxIterations", maxIterations);
                tryInvokeSetter(inst, "setAlpha", alpha);
                tryInvokeSetter(inst, "setBeta", beta);
                tryInvokeSetter(inst, "setRho", rho);
                tryInvokeSetter(inst, "setQ0", q0);
                tryInvokeSetter(inst, "setConvergenceSeries", bestSeries);
                return inst;
            } catch (NoSuchMethodException ignored) {}

            return null;

        } catch (Exception e) {
            log("Failed to instantiate ACO: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private void tryInvokeSetter(Object obj, String methodName, Object value) {
        if (obj == null) return;
        try {
            Method m = obj.getClass().getMethod(methodName, value.getClass());
            m.invoke(obj, value);
        } catch (NoSuchMethodException nsme) {
            try {
                if (value instanceof Double) {
                    Method m2 = obj.getClass().getMethod(methodName, double.class);
                    m2.invoke(obj, ((Double) value));
                } else if (value instanceof Integer) {
                    Method m2 = obj.getClass().getMethod(methodName, int.class);
                    m2.invoke(obj, ((Integer) value));
                } else if (value instanceof XYChart.Series) {
                    for (Method mm : obj.getClass().getMethods()) {
                        if (mm.getName().equals(methodName) && mm.getParameterCount() == 1) {
                            mm.invoke(obj, value);
                            return;
                        }
                    }
                }
            } catch (Exception ignore) {}
        } catch (Exception e) {}
    }

    // =====================================================
    // ===================== UI Helpers ====================
    // =====================================================

    private Slider createSlider(String key, double min, double max, double defaultVal, double step) {
        double saved = prefs.getDouble("aco_" + key, defaultVal);

        Slider slider = new Slider(min, max, saved);
        slider.setShowTickMarks(true);
        slider.setShowTickLabels(true);
        slider.setMajorTickUnit((max - min) / 4.0);
        slider.setBlockIncrement(step);
        slider.setSnapToTicks(true);

        slider.valueProperty().addListener((obs, oldV, newV) -> {
            double scaled = Math.round(newV.doubleValue() / step) * step;
            if (Math.abs(slider.getValue() - scaled) > 1e-12) {
                slider.setValue(scaled);
            }
            prefs.putDouble("aco_" + key, scaled);
        });

        return slider;
    }

    private VBox makeLabeledSlider(String name, Slider slider, int decimals) {
        Label lbl = new Label();
        lbl.setText(name + ": " + String.format("%." + decimals + "f", slider.getValue()));
        slider.valueProperty().addListener((obs, oldVal, newVal) ->
                lbl.setText(name + ": " + String.format("%." + decimals + "f", newVal.doubleValue()))
        );
        VBox box = new VBox(5, lbl, slider);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }


    public void updateChart(String tag, int iteration, double bestValue) {
        updateChart(iteration, bestValue);
    }

    public void updateChart(int iteration, double bestValue) {
        Platform.runLater(() -> {
            bestSeries.getData().add(new XYChart.Data<>(iteration, bestValue));
        });
    }

    /**
     * Save the final chart snapshot (called once at the end of a run).
     */
    public void saveFinalChartSnapshot() {
        try {
            File dir = new File("results");
            if (!dir.exists()) dir.mkdirs();

            String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss")
                    .format(new java.util.Date());
            File outFile = new File(dir, "chart_final_" + timestamp + ".png");

            WritableImage image = chart.snapshot(null, null);
            javax.imageio.ImageIO.write(
                    javafx.embed.swing.SwingFXUtils.fromFXImage(image, null),
                    "png",
                    outFile
            );
            log(" Final chart snapshot saved: " + outFile.getAbsolutePath());
        } catch (Exception e) {
            log(" Error saving chart snapshot: " + e.getMessage());
        }
    }

    /**
     * Directly save the ACO Optimization Progress chart as an image.
     */
    public void saveACOChartAsImage() {
        try {
            File dir = new File("results");
            if (!dir.exists()) dir.mkdirs();

            String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss")
                    .format(new java.util.Date());
            File outFile = new File(dir, "aco_chart_" + timestamp + ".png");

            WritableImage image = chart.snapshot(null, null);
            javax.imageio.ImageIO.write(
                    javafx.embed.swing.SwingFXUtils.fromFXImage(image, null),
                    "png",
                    outFile
            );
            log("ACO chart snapshot saved: " + outFile.getAbsolutePath());
        } catch (Exception e) {
            log("Error saving ACO chart snapshot: " + e.getMessage());
        }
    }


    public double getAlpha() { return alphaSlider.getValue(); }
    public double getBeta()  { return betaSlider.getValue(); }
    public double getRho()   { return rhoSlider.getValue(); }
    public double getQ0()    { return q0Slider.getValue(); }

    private static void log(String msg) {
        System.out.println("[MDVRPTWSolver] " + msg);
    }

    // =====================================================
    // =========== Visualization helpers (added) ==========
    // =====================================================

    /**
     * Read CSV file that was produced by RouteExporter and create a bar chart:
     * X = RouteID (category), Y = TotalDistance
     */
    private void visualizeRoutesFromCSV(File csvFile) {
        if (csvFile == null || !csvFile.exists()) {
            log("CSV for visualization not found: " + (csvFile == null ? "null" : csvFile.getAbsolutePath()));
            return;
        }

        // Parse CSV: we look for header columns "RouteID" and "TotalDistance"
        List<String> routeLabels = new ArrayList<>();
        List<Double> distances = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(csvFile), StandardCharsets.UTF_8))) {
            String header = br.readLine();
            if (header == null) return;
            String[] cols = header.split("\\s*,\\s*");
            Map<String,Integer> map = new HashMap<>();
            for (int i = 0; i < cols.length; i++) map.put(cols[i].toLowerCase(Locale.ROOT), i);

            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] t = line.split("\\s*,\\s*");
                String rid = (map.containsKey("routeid") && map.get("routeid") < t.length) ? t[map.get("routeid")] : null;
                String td = (map.containsKey("totaldistance") && map.get("totaldistance") < t.length) ? t[map.get("totaldistance")] : null;

                if (rid == null && t.length > 0) rid = t[0];
                if (td == null && t.length > 3) td = t[3]; // fallback to 4th column

                try {
                    double d = td != null ? Double.parseDouble(td) : 0.0;
                    routeLabels.add(rid != null ? rid : ("R" + (routeLabels.size()+1)));
                    distances.add(d);
                } catch (Exception ignored) {}
            }
        } catch (IOException e) {
            log("Error reading CSV for visualization: " + e.getMessage());
            return;
        }

        // Build BarChart on FX thread
        Platform.runLater(() -> {
            CategoryAxis xAxis = new CategoryAxis();
            NumberAxis yAxis = new NumberAxis();
            xAxis.setLabel("Route");
            yAxis.setLabel("Distance");
            BarChart<String, Number> barChart = new BarChart<>(xAxis, yAxis);
            barChart.setTitle("Route Distances");

            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName("Route Distance");

            for (int i = 0; i < routeLabels.size(); i++) {
                series.getData().add(new XYChart.Data<>(routeLabels.get(i), distances.get(i)));
            }
            barChart.getData().add(series);

            Stage stage = new Stage();
            stage.setTitle("Route Distances (Bar Chart)");
            stage.setScene(new Scene(barChart, 800, 500));
            stage.show();
        });
    }

    /**
     * Scatter plot: depots (larger points) and customers, grouped/colored by route.
     * Creates one series per route (including depot).
     */
    private void visualizeScatterFromRoutes(List<Route> routes) {
        if (routes == null || routes.isEmpty()) {
            log("No routes to visualize (scatter).");
            return;
        }

        Platform.runLater(() -> {
            NumberAxis xAxis = new NumberAxis();
            NumberAxis yAxis = new NumberAxis();
            xAxis.setLabel("X Coordinate");
            yAxis.setLabel("Y Coordinate");

            ScatterChart<Number, Number> scatterChart = new ScatterChart<>(xAxis, yAxis);
            scatterChart.setTitle("Route Layout Visualization");

            int idx = 1;
            for (Route route : routes) {
                XYChart.Series<Number, Number> series = new XYChart.Series<>();
                series.setName("Route " + idx);

                // add depot as first point if available (use negative marker by tooltip)
                if (route.depot != null) {
                    series.getData().add(new XYChart.Data<>(route.depot.x, route.depot.y));
                }
                // add customers
                for (Customer c : route.customers) {
                    series.getData().add(new XYChart.Data<>(c.x, c.y));
                }
                scatterChart.getData().add(series);
                idx++;
            }

            Stage stage = new Stage();
            stage.setTitle("Scatter Plot of Routes");
            stage.setScene(new Scene(scatterChart, 900, 600));
            stage.show();
        });
    }

    // =====================================================
    // =====================================================

    public static void main(String[] args) {
        launch(args);
    }
    
    public static void saveFinalChart(LineChart<Number, Number> chart, String label, Double finalFitness) {
        if (chart == null) {
            log(" No chart provided to saveFinalChart.");
            return;
        }
        try {
            File plotsDir = new File("results/plots");
            if (!plotsDir.exists()) {
                plotsDir.mkdirs();
            }

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String safeLabel = (label == null || label.trim().isEmpty())
                    ? "chart_final"
                    : "chart_final_" + label.replaceAll("[^a-zA-Z0-9_\\-]", "_");

            File outFile = new File(plotsDir, safeLabel + "_" + timestamp + ".png");
            File latestFile = new File(plotsDir, safeLabel + "_latest.png");

            // Take snapshot of the chart
            WritableImage image = chart.snapshot(new SnapshotParameters(), null);
            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", outFile);
            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", latestFile);

            // Log info
            if (label != null && finalFitness != null) {
                log(" Final chart saved: " + outFile.getAbsolutePath() +
                    " [Label=" + label + ", Fitness=" + finalFitness + "]");
            } else {
                log(" Final chart saved: " + outFile.getAbsolutePath());
            }
        } catch (IOException e) {
            log(" Error saving final chart: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /*  new edits */

}