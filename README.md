# MDVRP-TW-ACO
High-performance Java/JavaFX MDVRPTW solver with ACO integration, Cordeau/CSV parsers, visualization, and automatic route export.  a Java/JavaFX application that loads MDVRP/VRPTW instances (Cordeau-style or CSV), runs an Ant Colony Optimization (ACO) implementation via reflection, and visualizes results with charts and scatter plots. It provides CSV export of route summaries, runtime chart snapshots, and a legacy console-style API so it can be integrated with existing domain classes (Customer, Depot, Route, Solution, RouteExporter). The UI exposes ACO hyperparameters and saves progress and final outputs to a results folder.

Feature-focused description (for README About / longer repo “About”) MDVRPTWSolver is a Java application for Multi-Depot Vehicle Routing Problem with Time Windows (MDVRPTW):

Supports Cordeau MDVRP/VRPTW files and flexible CSV datasets (auto-detect).
- Integrates with Ant Colony Optimization implementations via reflection — tolerant to multiple constructor and setter signatures.
- Interactive JavaFX UI with ACO sliders (alpha, beta, rho, q0), progress LineChart, and route visualization (bar & scatter charts).
- Exports route summaries to CSV and saves chart snapshots to results/plots automatically.
- Legacy-style console API (evaluateSolution, decodeSolution, getDistance) for programmatic use and compatibility with patched domain classes.
