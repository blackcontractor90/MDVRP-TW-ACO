# MDVRPTWSolver (Hybridized ACO)

A lightweight JavaFX GUI for running Ant Colony Optimization (ACO) on the Multi-Depot Vehicle Routing Problem with Time Windows (MDVRPTW).  
This project provides a legacy-style solver frontend (MDVRPTWSolver.java) that:

- Loads problem instances (Cordeau-like text format or flexible CSV).
- Integrates with an ACO implementation by reflection (constructor-agnostic).
- Visualizes ACO progress with a line chart and produces route visualizations (bar and scatter).
- Exports route summaries to CSV and saves charts/images to `results/` folders.

This README documents how to run, the expected formats, how the ACO integration works and where outputs are saved.

Table of Contents
- About
- Requirements
- Expected project classes
- Supported input formats
- Build & Run
- Usage (GUI)
- ACO integration (reflection details)
- Exports, visualization, output files
- Troubleshooting
- Contributing
- License
- Author / Contact

About
-----
MDVRPTWSolver.java is a JavaFX application that acts as a GUI and helper layer around legacy domain classes (Customer, Depot, Route, Solution) and an Ant Colony Optimization class (AntColonyOptimization). It keeps the original console-style API but adds modern conveniences like file loading, CSV export, chart saving and runtime parameter sliders.

Requirements
------------
- Java 11+ (OpenJDK recommended)
- JavaFX (OpenJFX) matching your Java version (JavaFX SDK 11+). JavaFX modules required at runtime: javafx.controls (and javafx.fxml if you extend).
- Optional: build tool like Maven or Gradle to handle JavaFX dependencies.

Expected project classes
------------------------
The solver relies on the presence of several domain classes in the same project/classpath:

- Customer — expected fields/methods: id, x, y, demand, ready, due, service, distanceTo(...)
- Depot — fields: id, x, y, vehicleCapacity, maxVehicles, maxDuration, customers (array)
- Vehicle — (if used elsewhere)
- Route — constructor Route(Depot) and fields: depot, customers (list). Methods: addCustomer(...)
- Solution — fields: chromosome (list/array of ints), routes (list), addRoute(Route), evaluate()
- RouteExporter — static helpers used by MDVRPTWSolver:
  - exportSummary(List<Route>, String pathOrNull)
  - exportToCSV(List<Route>, String pathOrNull)
  These helpers are used for saving route summaries. If you keep the existing RouteExporter, MDVRPTWSolver will call it.

Supported input formats
-----------------------
1. Cordeau-like text format (typical MDVRPTW datasets)
   - Header line usually has: m n [Q V ...]
     - m = #depots
     - n = #customers
     - Optional Q = vehicle capacity, V = vehicles per depot
   - Next m lines: depotId x y [capacity] [maxVehicles]
   - Next n lines: custId x y demand ready due service

2. CSV (flexible headers)
   - The first row is header. Column names are case-insensitive.
   - Recognized columns (examples):
     id, x, y, demand, ready, due, service, type, name, maxVehicles, vehicleCapacity, depotId
   - type: "depot" or "customer". If missing, the parser infers depot when demand==0 && service==0.
   - If CSV contains only customers, a default depot at (0,0) is added.

Build & Run
-----------

Using Maven (recommended)
- Add OpenJFX dependencies to your pom.xml (or use the javafx-maven-plugin).
- Example run:
  mvn clean package
  java --module-path /path/to/javafx/lib --add-modules javafx.controls,javafx.swing -cp target/your-jar.jar MDVRPTWSolver

Using Gradle
- Use the Java plugin and the `org.openjfx` plugin to add JavaFX dependencies.
- Run via `gradle run` or create a fat jar and run with module path options as above.

Plain javac/java (manual)
- Compile:
  javac --module-path /path/to/javafx/lib --add-modules javafx.controls -cp . MDVRPTWSolver.java
- Run:
  java --module-path /path/to/javafx/lib --add-modules javafx.controls -cp . MDVRPTWSolver

Notes:
- Replace `/path/to/javafx/lib` with the JavaFX SDK lib dir you downloaded for your platform.
- If you package a jar, you still need to supply the JavaFX modules unless using a bundled runtime (jlink).

Usage (GUI)
-----------
1. Start the application — a main window with:
   - Menu: File → Load Instance..., Save Routes Summary..., Exit
   - Tools → Run ACO
   - Right-side: ACO Hyperparameter sliders (alpha, beta, rho, q0) and Run button
   - Center: Line chart showing best distance vs iteration

2. Load data:
   - File → Load Instance... and select a .txt/.dat (Cordeau-like) or .csv file.

3. Run ACO:
   - Either click the "Run ACO" button in the right panel or Tools → Run ACO.
   - The UI provides sliders for α (alpha), β (beta), ρ (rho evaporation) and q0 (exploration vs exploitation).
   - The app will instantiate an AntColonyOptimization class (see reflection details below) and attempt to run it in a background thread.

4. Export:
   - After a run finishes (if routes are available), MDVRPTWSolver attempts to automatically export via RouteExporter and shows an alert pointing to `results/routes_summary_latest.csv`.
   - You may also explicitly use File → Save Routes Summary... to pick a location.

ACO integration (reflection details)
------------------------------------
MDVRPTWSolver uses reflection to integrate with a class named `AntColonyOptimization` found on the classpath. Supported instantiation patterns (in order of preference):

Constructors MDVRPTWSolver will try:
1. AntColonyOptimization(MDVRPTWSolver solver, int numAnts, int maxIterations, double alpha, double beta, double rho, double q0)
2. AntColonyOptimization(MDVRPTWSolver solver, int numAnts, int maxIterations) — then MDVRPTWSolver will attempt to call setters:
   - setAlpha(double), setBeta(double), setRho(double), setQ0(double), setConvergenceSeries(XYChart.Series)
3. AntColonyOptimization(int numAnts, int maxIterations, double alpha, double beta, double rho, double q0)
4. Default constructor AntColonyOptimization() — then MDVRPTWSolver will attempt setters:
   - setSolver(MDVRPTWSolver)
   - setNumAnts(int), setMaxIterations(int)
   - setAlpha(double), setBeta(double), setRho(double), setQ0(double)
   - setConvergenceSeries(XYChart.Series)

Execution:
- If the instantiated ACO instance implements Runnable, MDVRPTWSolver will call run() directly.
- Otherwise MDVRPTWSolver will try to reflectively call a run() method.

Integration tips:
- Implementers of AntColonyOptimization should:
  - Offer at least one of the constructor/signature patterns above, or
  - Provide setters listed above so MDVRPTWSolver can configure the instance.
  - During the ACO run, call MDVRPTWSolver.updateChart(iteration, bestValue) when appropriate to populate the chart and to permit final chart saving.
  - When a best solution is evaluated, call MDVRPTWSolver.evaluateSolution(Solution) so lastSolutionRoutes are populated for export/visualization.

Exports & visualization
-----------------------
- RouteExporter.exportSummary(...) or exportToCSV(...) is used to save CSV summaries.
- Default exports are placed under `results/` (e.g. `results/routes_summary_latest.csv`).
- Charts are saved to:
  - `results/` — general snapshots (`aco_chart_YYYYMMDD_HHMMSS.png`)
  - `results/plots` — `saveFinalChart` writes labeled final charts and a `_latest.png`.
- After export, MDVRPTWSolver will attempt to visualize:
  - Bar chart of route distances (RouteID vs TotalDistance) by reading the CSV.
  - Scatter plot of routes (one series per route including depot positions).

CSV route summary expectations (RouteExporter output)
- Columns should include at least route id and total distance. MDVRPTWSolver's visualizer looks for `RouteID` and `TotalDistance` headers (case-insensitive), and has fallbacks if they are missing.

Troubleshooting
---------------
- JavaFX runtime errors:
  - Ensure the JavaFX SDK version matches your JDK and you pass the `--module-path` and `--add-modules` flags.
- ClassNotFoundException: AntColonyOptimization or RouteExporter
  - Make sure those classes are compiled and on the classpath at runtime.
- No routes exported / lastSolutionRoutes empty:
  - Ensure the ACO implementation calls back into MDVRPTWSolver (evaluateSolution or sets lastSolutionRoutes) or that a Solution is evaluated and routes are added with Solution.addRoute(...).
- CSV parsing differences:
  - If your CSV header uses different names, either adapt RouteExporter or the CSV to match the expected fields (id,x,y,demand,ready,due,service,type).


Author / Contact
----------------
Created for the code file MDVRPTWSolver.java provided by blackcontractor90 (https://www.linkedin.com/in/farid-morsidi-372083141/).

Acknowledgements
----------------
This project is intended to modernize a legacy MDVRPTW solver GUI with minimal invasive changes while adding useful features: file I/O, chart snapshotting and ACO integration by reflection.
