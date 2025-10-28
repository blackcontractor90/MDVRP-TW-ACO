import javafx.application.Platform;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ant Colony Optimization (ACO) for MDVRPTW
 *
 * Hybridized with local search:
 *  - intra-route 2-opt
 *  - inter-route relocate
 *
 * Adaptation: If stagnation is detected OR population diversity drops below a threshold,
 * parameters are adapted to encourage exploration.
 */
/**
 * ${user}blackcontractor@farid
 */
public class AntColonyOptimization implements Runnable {

    private final MDVRPTWSolver solver;
    private final int numAnts;
    private final int maxIterations;

    private double alpha;
    private double beta;
    private double rho;
    private double q0;

    private double[][] pheromone;
    private Solution bestSolution;
    private double bestFitness = Double.MAX_VALUE;

    private final Random rnd = new Random();

    // --- Adaptation control ---
    private int stagnationCounter = 0;
    private double lastBestFitness = Double.MAX_VALUE;
    private final int stagnationLimit = 20; // iterations with no improvement before adapting

    private final double diversityThreshold = 0.15; // Fraction of chromosome length
    private final double defaultAlpha;
    private final double defaultBeta;
    private final double defaultRho;
    private final double defaultQ0;

    public AntColonyOptimization(MDVRPTWSolver solver,
                                 int numAnts,
                                 int maxIterations,
                                 double alpha,
                                 double beta,
                                 double rho,
                                 double q0) {
        this.solver = solver;
        this.numAnts = numAnts;
        this.maxIterations = maxIterations;
        this.alpha = alpha;
        this.beta = beta;
        this.rho = rho;
        this.q0 = q0;
        // Store initial values for potential reset
        this.defaultAlpha = alpha;
        this.defaultBeta = beta;
        this.defaultRho = rho;
        this.defaultQ0 = q0;
    }

    @Override
    public void run() {
        int numCustomers = Math.max(0, solver.getCustomerCount());
        if (numCustomers <= 0) {
            log("No customers found; aborting ACO.");
            return;
        }

        pheromone = new double[numCustomers][numCustomers];
        for (int i = 0; i < numCustomers; i++) Arrays.fill(pheromone[i], 1.0);

        for (int iter = 0; iter < maxIterations; iter++) {
            List<Solution> antSolutions = new ArrayList<>(numAnts);

            for (int k = 0; k < numAnts; k++) {
                Solution sol = constructSolution();
                if (sol == null) continue;

                try {
                    solver.evaluateSolution(sol);
                } catch (Throwable t) {
                    try {
                        Method m = solver.getClass().getMethod("evaluate", Solution.class);
                        m.invoke(solver, sol);
                    } catch (Exception ignore) {}
                }

                // --- Local search ---
                try {
                    localSearch(sol);
                } catch (Throwable t) {
                    log("Local search error: " + t.getMessage());
                }

                try {
                    solver.evaluateSolution(sol);
                } catch (Throwable t) {
                    try {
                        Method m = solver.getClass().getMethod("evaluate", Solution.class);
                        m.invoke(solver, sol);
                    } catch (Exception ignore) {}
                }

                antSolutions.add(sol);

                double fitness = (Double.isFinite(sol.fitness) && sol.fitness > 0) ? sol.fitness
                        : (sol.totalDistance + sol.totalPenalty);

                if (fitness < bestFitness) {
                    bestFitness = fitness;
                    bestSolution = new Solution(sol); // deep copy
                }
            }

            // --- Hybrid adaptation: stagnation or low diversity ---
            double avgDiversity = calculateDiversity(antSolutions);
            int chromLength = antSolutions.isEmpty() ? 0 : antSolutions.get(0).chromosome.length;
            double minAcceptable = chromLength * diversityThreshold;

            boolean didAdapt = false;

            // Stagnation-based
            if (Math.abs(bestFitness - lastBestFitness) < 1e-6) {
                stagnationCounter++;
            } else {
                stagnationCounter = 0;
                lastBestFitness = bestFitness;
            }

            if (stagnationCounter >= stagnationLimit) {
                adaptParametersForStagnation();
                stagnationCounter = 0;
                didAdapt = true;
                log(String.format("[ACO] Stagnation adaptation triggered: alpha=%.2f, beta=%.2f, rho=%.2f, q0=%.2f",
                        alpha, beta, rho, q0));
            }

            // Diversity-based
            if (avgDiversity < minAcceptable && chromLength > 0) {
                adaptParametersForDiversity();
                didAdapt = true;
                log(String.format("[ACO] Diversity adaptation triggered: avg=%.2f, min=%.2f, alpha=%.2f, beta=%.2f, rho=%.2f, q0=%.2f",
                        avgDiversity, minAcceptable, alpha, beta, rho, q0));
            }

            // Optionally, restore parameters if improvement resumes (not strictly required)
            // if (didAdapt && stagnationCounter == 0) restoreDefaultParameters();

            updatePheromones(antSolutions);

            final int finalIter = iter;
            final double finalBest = bestFitness;
            safeUpdateChart(finalIter, finalBest);
        }

        final Solution finalBestSol = (bestSolution == null) ? null : new Solution(bestSolution);
        final double finalFitness = bestFitness;

        Platform.runLater(() -> {
            safeShowFinalSolution(finalBestSol, finalFitness);
            safeSaveFinalChart(finalFitness);
        });
    }

    /** Adapt parameters for stagnation (encourage more exploration) */
    private void adaptParametersForStagnation() {
        q0 = Math.max(0.4, q0 - 0.1);     // Lower q0
        rho = Math.min(0.9, rho + 0.05);  // Increase evaporation
        alpha = Math.max(0.5, alpha - 0.1); // Slightly decrease alpha
        beta = Math.min(10.0, beta + 0.2); // Slightly increase beta
    }

    /** Adapt parameters for low diversity (encourage more exploration) */
    private void adaptParametersForDiversity() {
        q0 = Math.max(0.4, q0 - 0.1);     // Lower q0
        rho = Math.min(0.9, rho + 0.05);  // Increase evaporation
        alpha = Math.max(0.5, alpha - 0.1); // Slightly decrease alpha
        beta = Math.max(1.0, beta - 0.2); // Slightly decrease beta (less heuristic)
    }

    /** Calculate average pairwise normalized Hamming distance */
    private double calculateDiversity(List<Solution> population) {
        if (population.size() <= 1) return 0.0;
        int totalDist = 0;
        int count = 0;
        int chromLength = population.get(0).chromosome.length;
        for (int i = 0; i < population.size(); i++) {
            for (int j = i + 1; j < population.size(); j++) {
                totalDist += hammingDistance(population.get(i).chromosome, population.get(j).chromosome);
                count++;
            }
        }
        return count > 0 && chromLength > 0 ? (double) totalDist / (count * chromLength) : 0.0;
    }

    private int hammingDistance(int[] a, int[] b) {
        int dist = 0;
        int minLen = Math.min(a.length, b.length);
        for (int i = 0; i < minLen; i++) {
            if (a[i] != b[i]) dist++;
        }
        return dist + Math.abs(a.length - b.length);
    }

    private Solution constructSolution() {
        int n = solver.getCustomerCount();
        if (n <= 0) return null;

        List<Integer> unvisited = new ArrayList<>(n);
        for (int i = 0; i < n; i++) unvisited.add(i);

        int current = ThreadLocalRandom.current().nextInt(n);
        List<Integer> tour = new ArrayList<>(n);
        tour.add(current);
        unvisited.remove((Integer) current);

        while (!unvisited.isEmpty()) {
            int next = selectNextCustomer(current, unvisited);
            tour.add(next);
            unvisited.remove((Integer) next);

            pheromone[current][next] = (1.0 - rho) * pheromone[current][next] + rho * 1.0;
            current = next;
        }

        int[] chrom = tour.stream().mapToInt(Integer::intValue).toArray();
        Solution sol = new Solution(chrom);

        try {
            solver.decodeSolution(sol);
        } catch (Throwable ignore) {
        }

        try {
            solver.evaluateSolution(sol);
        } catch (Throwable t) {
            try {
                Method m = solver.getClass().getMethod("evaluate", Solution.class);
                m.invoke(solver, sol);
            } catch (Exception ignore) {
            }
        }

        return sol;
    }

    // ---------- Local search ----------
    private void localSearch(Solution sol) {
        if (sol == null || sol.routes == null || sol.routes.isEmpty()) return;

        boolean improved = true;
        int relocateRounds = 0;
        final int MAX_RELOCATE_ROUNDS = 50;

        for (Route r : sol.routes) {
            twoOptRoute(r);
        }
        safeEvaluate(sol);

        while (improved && relocateRounds < MAX_RELOCATE_ROUNDS) {
            improved = false;
            relocateRounds++;

            for (int i = 0; i < sol.routes.size(); i++) {
                Route rFrom = sol.routes.get(i);
                for (int pos = 0; pos < rFrom.customers.size(); pos++) {
                    Customer c = rFrom.customers.get(pos);

                    for (int j = 0; j < sol.routes.size(); j++) {
                        if (j == i) continue;
                        Route rTo = sol.routes.get(j);

                        for (int ins = 0; ins <= rTo.customers.size(); ins++) {
                            boolean removed = false;
                            Customer removedCustomer = null;
                            try {
                                removedCustomer = rFrom.customers.remove(pos);
                                removed = true;
                                rTo.customers.add(ins, removedCustomer);

                                safeEvaluate(sol);
                                double currentFitness = (Double.isFinite(sol.fitness) && sol.fitness > 0) ? sol.fitness : (sol.totalDistance + sol.totalPenalty);

                                if (currentFitness + 1e-9 < bestFitness || currentFitness < sol.getFitness()) {
                                    if (currentFitness < bestFitness) {
                                        bestFitness = currentFitness;
                                        bestSolution = new Solution(sol);
                                    }
                                    improved = true;
                                    twoOptRoute(rFrom);
                                    twoOptRoute(rTo);
                                    safeEvaluate(sol);
                                    break;
                                } else {
                                    rTo.customers.remove(ins);
                                    rFrom.customers.add(pos, removedCustomer);
                                }
                            } catch (IndexOutOfBoundsException ex) {
                                if (removed && removedCustomer != null) {
                                    if (!rTo.customers.remove(removedCustomer)) {
                                        rFrom.customers.add(removedCustomer);
                                    } else {
                                        rFrom.customers.add(pos, removedCustomer);
                                    }
                                }
                            }
                        }
                        if (improved) break;
                    }
                    if (improved) break;
                }
                if (improved) break;
            }
        }

        safeEvaluate(sol);
    }

    private void twoOptRoute(Route route) {
        if (route == null || route.customers.size() < 4) return;

        boolean improved = true;
        while (improved) {
            improved = false;
            double bestDelta = 0;
            int bestI = -1, bestK = -1;

            int m = route.customers.size();
            for (int i = 0; i < m - 2; i++) {
                for (int k = i + 1; k < m; k++) {
                    double delta = twoOptDelta(route, i, k);
                    if (delta < bestDelta - 1e-9) {
                        bestDelta = delta;
                        bestI = i;
                        bestK = k;
                        improved = true;
                    }
                }
                if (improved) break;
            }

            if (improved && bestI >= 0 && bestK >= 0) {
                Collections.reverse(route.customers.subList(bestI + 1, bestK + 1));
                route.markDirty();
            }
        }
    }

    private double twoOptDelta(Route route, int i, int k) {
        Depot depot = route.depot;
        List<Customer> list = route.customers;
        int m = list.size();
        Customer A = (i >= 0) ? list.get(i) : null;
        Customer B = list.get(i + 1);
        Customer C = list.get(k);
        Customer D = (k + 1 < m) ? list.get(k + 1) : null;

        double before = 0, after = 0;

        if (A == null) {
            before += depot.distanceTo(B);
            after += depot.distanceTo(C);
        } else {
            before += A.distanceTo(B);
            after += A.distanceTo(C);
        }

        if (D == null) {
            before += C.distanceTo(depot);
            after += B.distanceTo(depot);
        } else {
            before += C.distanceTo(D);
            after += B.distanceTo(D);
        }

        return after - before;
    }

    // ---------- end local search ----------

    private int selectNextCustomer(int current, List<Integer> unvisited) {
        try {
            this.alpha = safeGetDoubleFromSolver("getAlpha", this.alpha);
            this.beta  = safeGetDoubleFromSolver("getBeta", this.beta);
            this.rho   = safeGetDoubleFromSolver("getRho", this.rho);
            this.q0    = safeGetDoubleFromSolver("getQ0", this.q0);
        } catch (Exception ignore) {}

        double q = ThreadLocalRandom.current().nextDouble();
        if (q < q0) {
            return unvisited.stream()
                    .max(Comparator.comparingDouble(j -> Math.pow(pheromone[current][j], alpha) *
                            Math.pow(1.0 / (solver.getDistance(current, j) + 1e-9), beta)))
                    .orElse(unvisited.get(0));
        } else {
            double[] vals = new double[unvisited.size()];
            double sum = 0.0;
            for (int i = 0; i < unvisited.size(); i++) {
                int j = unvisited.get(i);
                double h = Math.pow(1.0 / (solver.getDistance(current, j) + 1e-9), beta);
                double p = Math.pow(pheromone[current][j], alpha) * h;
                vals[i] = p;
                sum += p;
            }
            if (sum <= 0) return unvisited.get(ThreadLocalRandom.current().nextInt(unvisited.size()));
            double r = ThreadLocalRandom.current().nextDouble() * sum;
            double cum = 0.0;
            for (int i = 0; i < vals.length; i++) {
                cum += vals[i];
                if (r <= cum) return unvisited.get(i);
            }
            return unvisited.get(unvisited.size() - 1);
        }
    }

    private void updatePheromones(List<Solution> antSolutions) {
        int n = pheromone.length;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                pheromone[i][j] *= (1.0 - rho);
                if (pheromone[i][j] < 1e-12) pheromone[i][j] = 1e-12;
            }
        }

        for (Solution sol : antSolutions) {
            double fitness = (Double.isFinite(sol.fitness) && sol.fitness > 0) ? sol.fitness
                    : (sol.totalDistance + sol.totalPenalty + 1e-9);
            double contribution = 1.0 / (fitness + 1e-9);

            if (sol.chromosome != null && sol.chromosome.length >= 2) {
                for (int k = 0; k < sol.chromosome.length - 1; k++) {
                    int a = sol.chromosome[k];
                    int b = sol.chromosome[k + 1];
                    if (a >= 0 && a < n && b >= 0 && b < n) {
                        pheromone[a][b] += contribution;
                        pheromone[b][a] += contribution;
                    }
                }
            } else {
                if (sol.routes != null) {
                    for (Route route : sol.routes) {
                        for (int i = 0; i < route.customers.size() - 1; i++) {
                            Customer A = route.customers.get(i);
                            Customer B = route.customers.get(i + 1);
                            int ai = indexOfCustomer(A);
                            int bi = indexOfCustomer(B);
                            if (ai >= 0 && bi >= 0 && ai < n && bi < n) {
                                pheromone[ai][bi] += contribution;
                                pheromone[bi][ai] += contribution;
                            }
                        }
                    }
                }
            }
        }
    }

    private int indexOfCustomer(Customer c) {
        if (c == null) return -1;
        try {
            Method m = solver.getClass().getMethod("getCustomerIndex", Customer.class);
            Object res = m.invoke(solver, c);
            if (res instanceof Integer) return (Integer) res;
        } catch (Exception ignored) {}

        try {
            Method getCustomers = solver.getClass().getMethod("getCustomers");
            Object listObj = getCustomers.invoke(solver);
            if (listObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Customer> list = (List<Customer>) listObj;
                for (int i = 0; i < list.size(); i++) {
                    Customer sc = list.get(i);
                    if (sc != null && sc.x == c.x && sc.y == c.y) return i;
                }
            }
        } catch (Exception ignored) {}

        return -1;
    }

    private void safeUpdateChart(int iter, double bestValue) {
        Platform.runLater(() -> {
            try {
                Method m = solver.getClass().getMethod("updateChart", String.class, int.class, double.class);
                m.invoke(solver, "ACO", iter, bestValue);
                return;
            } catch (Exception ignored) {}
            try {
                Method m2 = solver.getClass().getMethod("updateChart", int.class, double.class);
                m2.invoke(solver, iter, bestValue);
                return;
            } catch (Exception ignored) {}
            try {
                Method getSeries = solver.getClass().getMethod("getSeries", String.class);
                Object series = getSeries.invoke(solver, "ACO");
                if (series instanceof XYChart.Series) {
                    @SuppressWarnings("unchecked")
                    XYChart.Series<Number, Number> s = (XYChart.Series<Number, Number>) series;
                    s.getData().add(new XYChart.Data<>(iter, bestValue));
                }
            } catch (Exception ignored) {}
        });
    }

    private void safeShowFinalSolution(Solution sol, double fitness) {
        try {
            Method m = solver.getClass().getMethod("showFinalSolution", Solution.class, double.class);
            m.invoke(solver, sol, fitness);
            return;
        } catch (Exception ignored) {}

        try {
            Method m2 = solver.getClass().getMethod("drawSolution", Solution.class);
            m2.invoke(solver, sol);
            return;
        } catch (Exception ignored) {}

        try {
            safeUpdateChart(maxIterations, fitness);
        } catch (Exception ignored) {}
    }

    private double safeGetDoubleFromSolver(String methodName, double defaultVal) {
        try {
            Method m = solver.getClass().getMethod(methodName);
            Object r = m.invoke(solver);
            if (r instanceof Number) return ((Number) r).doubleValue();
        } catch (Exception ignored) {}
        return defaultVal;
    }

    private void log(String s) {
        try {
            Method m = solver.getClass().getMethod("log", String.class);
            m.invoke(solver, s);
        } catch (Exception e) {
            System.out.println("[ACO] " + s);
        }
    }

    private void safeEvaluate(Solution sol) {
        try {
            solver.evaluateSolution(sol);
        } catch (Throwable t) {
            try {
                Method m = solver.getClass().getMethod("evaluate", Solution.class);
                m.invoke(solver, sol);
            } catch (Exception ignore) {}
        }
    }

    @SuppressWarnings("unchecked")
    private void safeSaveFinalChart(double finalFitness) {
        try {
            try {
                Method m = solver.getClass().getMethod("saveFinalChart", String.class, double.class);
                m.invoke(solver, "ACO", finalFitness);
                return;
            } catch (NoSuchMethodException ignored) {}

            LineChart<Number, Number> chartObj = null;
            try {
                Method getter = solver.getClass().getMethod("getChart");
                Object obj = getter.invoke(solver);
                if (obj instanceof LineChart) chartObj = (LineChart<Number, Number>) obj;
            } catch (Exception ignored) {}

            if (chartObj == null) {
                try {
                    Field f = solver.getClass().getDeclaredField("chart");
                    f.setAccessible(true);
                    Object obj = f.get(solver);
                    if (obj instanceof LineChart) chartObj = (LineChart<Number, Number>) obj;
                } catch (Exception ignored) {}
            }

            if (chartObj != null) {
                try {
                    Method m2 = solver.getClass().getMethod("saveFinalChart", LineChart.class, String.class, double.class);
                    m2.invoke(solver, chartObj, "ACO", finalFitness);
                    return;
                } catch (NoSuchMethodException ignored) {}
                try {
                    Method m2 = solver.getClass().getMethod("saveFinalChart", LineChart.class, String.class, Double.class);
                    m2.invoke(solver, chartObj, "ACO", Double.valueOf(finalFitness));
                    return;
                } catch (NoSuchMethodException ignored) {}
            }

            if (chartObj != null) {
                try {
                    Method m3 = MDVRPTWSolver.class.getMethod("saveFinalChart", LineChart.class, String.class, Double.class);
                    m3.invoke(null, chartObj, "ACO", Double.valueOf(finalFitness));
                    return;
                } catch (NoSuchMethodException ignored) {}
                try {
                    Method m3 = MDVRPTWSolver.class.getMethod("saveFinalChart", LineChart.class);
                    m3.invoke(null, chartObj);
                    return;
                } catch (NoSuchMethodException ignored) {}
            }

            try {
                Method m4 = solver.getClass().getMethod("saveFinalChart", LineChart.class);
                if (chartObj == null) {
                    try {
                        Field f = solver.getClass().getDeclaredField("chart");
                        f.setAccessible(true);
                        Object obj = f.get(solver);
                        if (obj instanceof LineChart) chartObj = (LineChart<Number, Number>) obj;
                    } catch (Exception ignored) {}
                }
                if (chartObj != null) {
                    m4.invoke(solver, chartObj);
                    return;
                }
            } catch (NoSuchMethodException ignored) {}

            log("No compatible saveFinalChart method found on solver; final chart not saved.");
        } catch (Exception e) {
            log("Error while attempting to save final chart: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public Solution getBestSolution() {
        return bestSolution;
    }

    public double getBestFitness() {
        return bestFitness;
    }
}