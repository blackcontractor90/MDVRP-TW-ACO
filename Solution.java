import java.util.*;

/**
 * Solution representation for MDVRPTW / HFVRP
 * Supports route caching and efficient evaluation.
 * Compatible with patched Route class.
 * @author blackcontractor
 */
public class Solution {
    public int[] chromosome;
    public List<Route> routes = new ArrayList<>();
    public double fitness = Double.MAX_VALUE;
    public double totalDistance = 0.0;
    public double totalPenalty = 0.0;
    public int timeWindowViolations = 0;
    public double penalty = 0.0;
    public boolean feasible = false;

    // ==== HFVRP fields: total cost (sum of all route costs with respect to assigned vehicles) ====
    public double totalCost = 0.0;

    // Constructor with chromosome initialization
    public Solution(int[] chromosome) {
        this.chromosome = (chromosome != null) ? Arrays.copyOf(chromosome, chromosome.length) : null;
    }

    // Deep copy constructor
    public Solution(Solution other) {
        this.chromosome = (other.chromosome != null)
                ? Arrays.copyOf(other.chromosome, other.chromosome.length)
                : null;

        this.fitness = other.fitness;
        this.totalDistance = other.totalDistance;
        this.totalPenalty = other.totalPenalty;
        this.timeWindowViolations = other.timeWindowViolations;
        this.penalty = other.penalty;
        this.feasible = other.feasible;
        this.totalCost = other.totalCost;

        this.routes = new ArrayList<>();
        if (other.routes != null) {
            for (Route r : other.routes) {
                this.routes.add(r.deepCopy());
            }
        }
    }

    // Clone utility (calls deep copy constructor)
    @Override
    public Solution clone() {
        return new Solution(this);
    }

    /** Check feasibility across all routes */
    public boolean isFeasible() {
        if (routes == null || routes.isEmpty()) return false;
        for (Route r : routes) {
            if (!r.isFeasible()) return false;
        }
        return true;
    }

    public double getFitness() {
        return fitness;
    }

    /** Recompute total cost from all routes */
    public void computeTotalCost() {
        totalCost = 0.0;
        if (routes == null) return;
        for (Route r : routes) {
            r.recompute(); // ensure cached values updated
            totalCost += r.computeTotalCost();
        }
    }

    /** Recompute total distance from all routes */
    public void computeTotalDistance() {
        totalDistance = 0.0;
        if (routes == null) return;
        for (Route r : routes) {
            r.recompute();
            totalDistance += r.totalDistance;
        }
    }

    /** Recompute penalties and TW violations */
    public void computePenalty() {
        totalPenalty = 0.0;
        timeWindowViolations = 0;
        if (routes == null) return;
        for (Route r : routes) {
            r.recompute();
            totalPenalty += r.getPenalty();
            timeWindowViolations += r.timeWindowViolations;
        }
    }

    // Utility methods for safe handling
    public void addRoute(Route r) {
        if (r != null) routes.add(r);
    }

    public void clearRoutes() {
        routes.clear();
    }

    public List<Route> getRoutes() {
        return routes;
    }

    /**
     * Evaluate solution fitness:
     *  - Forces each route to recompute if dirty
     *  - Aggregates distance, cost, and penalties
     *  - Updates feasibility and fitness
     */
    public void evaluate() {
        totalDistance = 0.0;
        totalCost = 0.0;
        totalPenalty = 0.0;
        timeWindowViolations = 0;

        if (routes != null) {
            for (Route r : routes) {
                r.recompute();
                totalDistance += r.totalDistance;
                totalCost += r.computeTotalCost();
                totalPenalty += r.getPenalty();
                timeWindowViolations += r.timeWindowViolations;
            }
        }

        fitness = totalDistance + totalPenalty; // baseline cost+penalty
        feasible = isFeasible();
    }

    @Override
    public String toString() {
        return "Fitness: " + String.format("%.2f", fitness) +
                ", Distance: " + String.format("%.2f", totalDistance) +
                ", Cost: " + String.format("%.2f", totalCost) +
                ", Penalty: " + String.format("%.2f", totalPenalty) +
                ", TW Violations: " + timeWindowViolations +
                ", Feasible: " + feasible;
    }
}
