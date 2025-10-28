import java.util.*;

/**
 * Decodes a chromosome into a solution for the MDVRPTW.
 * Supports flexible depot assignment and capacity-constrained route splitting.
 *
 * @param chromosome       Permutation of customer indices.
 * @param depotAssignment  [Optional] For each customer, the depot id to assign. If null, assign to nearest depot.
 * @param customers        List of customers.
 * @param depots           List of depots.
 * @return Solution object with routes, distance, penalties, and fitness.
 */

/**
 * ${user}blackcontractor@farid
 */
public class Decoder {

    public static Solution decode(
            int[] chromosome,
            int[] depotAssignment,
            List<Customer> customers,
            List<Depot> depots
    ) {
        Solution solution = new Solution(chromosome);

        if (chromosome == null || customers == null || depots == null) {
            return solution;
        }

        // 1) Assign customers to depots
        Map<Integer, List<Customer>> depotCustomerMap = new HashMap<>();
        for (Depot depot : depots) {
            depotCustomerMap.put(depot.id, new ArrayList<>());
        }

        for (int gene : chromosome) {
            if (gene < 0 || gene >= customers.size()) continue;
            Customer c = customers.get(gene);
            int depotId;
            if (depotAssignment != null && depotAssignment.length > gene) {
                depotId = depotAssignment[gene];
                if (!depotCustomerMap.containsKey(depotId)) {
                    depotId = findNearestDepotId(c, depots);
                }
            } else {
                depotId = findNearestDepotId(c, depots);
            }
            depotCustomerMap.get(depotId).add(c);
        }

        // 2) Split into feasible routes per depot
        double totalDistance = 0.0, totalPenalty = 0.0;
        int totalTWViolations = 0;
        int vehicleLimitPenalty = 0;

        for (Depot depot : depots) {
            List<Customer> depotCusts = depotCustomerMap.getOrDefault(depot.id, new ArrayList<>());

            depotCusts.sort(Comparator.comparingInt(c -> {
                int idx = indexInChromosome(chromosome, c.id);
                return idx >= 0 ? idx : Integer.MAX_VALUE / 2;
            }));

            List<Route> routes = splitIntoFeasibleRoutes(depot, depotCusts);

            if (routes.size() > depot.maxVehicles) {
                vehicleLimitPenalty += (routes.size() - depot.maxVehicles);
            }

            for (Route route : routes) {
                twoOpt(route);

                route.computeTotalDistance();
                route.evaluateTimeWindows();

                solution.routes.add(route);

                totalDistance += route.totalDistance;
                totalPenalty += route.penalty;
                totalTWViolations += route.timeWindowViolations;
            }
        }

        // 3) Aggregate and evaluate
        double penaltyWeight = 10000.0; // align with your solver if different
        double fitness = totalDistance + penaltyWeight * (totalPenalty + vehicleLimitPenalty + totalTWViolations);

        solution.totalDistance = totalDistance;
        solution.totalPenalty = totalPenalty + vehicleLimitPenalty;
        solution.timeWindowViolations = totalTWViolations;
        solution.fitness = fitness;

        solution.computeTotalDistance();
        solution.computePenalty();
        solution.computeTotalCost();
        solution.feasible = solution.isFeasible();

        return solution;
    }

    private static int findNearestDepotId(Customer c, List<Depot> depots) {
        if (depots == null || depots.isEmpty()) return -1;
        Depot best = depots.get(0);
        double bestDist = distance(best, c);
        for (int i = 1; i < depots.size(); i++) {
            Depot d = depots.get(i);
            double dist = distance(d, c);
            if (dist < bestDist) {
                bestDist = dist;
                best = d;
            }
        }
        return best.id;
    }

    private static int indexInChromosome(int[] chromosome, int customerId) {
        if (chromosome == null) return -1;
        for (int i = 0; i < chromosome.length; i++) {
            if (chromosome[i] == customerId) return i;
        }
        return -1;
    }

    private static List<Route> splitIntoFeasibleRoutes(Depot depot, List<Customer> customers) {
        List<Route> routes = new ArrayList<>();
        Route currentRoute = new Route(depot);
        double currentLoad = 0.0;

        for (Customer c : customers) {
            if ((currentLoad + c.demand > depot.vehicleCapacity) && !currentRoute.customers.isEmpty()) {
                routes.add(currentRoute);
                currentRoute = new Route(depot);
                currentLoad = 0.0;
            }
            currentRoute.addCustomer(c);
            currentLoad += c.demand;
        }
        if (!currentRoute.customers.isEmpty()) routes.add(currentRoute);
        return routes;
    }

    private static void twoOpt(Route route) {
        if (route == null || route.customers.size() < 3) return;

        boolean improved;
        do {
            improved = false;
            double bestDelta = 0;
            int bestI = -1, bestK = -1;
            int n = route.customers.size();
            for (int i = 0; i < n - 1; i++) {
                for (int k = i + 1; k < n; k++) {
                    double delta = compute2OptDelta(route, i, k);
                    if (delta < bestDelta - 1e-9) {
                        bestDelta = delta;
                        bestI = i;
                        bestK = k;
                        improved = true;
                    }
                }
            }
            if (improved && bestI >= 0 && bestK >= 0) {
                Collections.reverse(route.customers.subList(bestI, bestK + 1));
            }
        } while (improved);
    }

    /**
     * Change in distance for reversing [i..k] segment.
     */
    private static double compute2OptDelta(Route route, int i, int k) {
        Depot depot = route.depot;
        int n = route.customers.size();

        Customer A_prev = (i - 1 >= 0) ? route.customers.get(i - 1) : null;
        Customer A = route.customers.get(i);
        Customer B = route.customers.get(k);
        Customer B_next = (k + 1 < n) ? route.customers.get(k + 1) : null;

        double before = 0.0, after = 0.0;

        // Edge entering segment
        if (A_prev == null) {
            before += distance(depot, A);
            after += distance(depot, B);
        } else {
            before += distance(A_prev, A);
            after += distance(A_prev, B);
        }

        // Edge leaving segment
        if (B_next == null) {
            before += distance(depot, B);   // FIXED: depot first
            after += distance(depot, A);    // FIXED: depot first
        } else {
            before += distance(B, B_next);
            after += distance(A, B_next);
        }

        return after - before;
    }

    private static double distance(Depot depot, Customer customer) {
        if (depot == null || customer == null) return Double.POSITIVE_INFINITY;
        return Math.hypot(depot.x - customer.x, depot.y - customer.y);
    }

    private static double distance(Customer c1, Customer c2) {
        if (c1 == null || c2 == null) return Double.POSITIVE_INFINITY;
        return Math.hypot(c1.x - c2.x, c1.y - c2.y);
    }
}
