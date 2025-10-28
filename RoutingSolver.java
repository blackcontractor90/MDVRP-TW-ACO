import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ${user}blackcontractor@farid
 */
public class RoutingSolver {
    private List<Depot> depots;
    private List<Customer> customers;
    private List<Route> solutionRoutes;
    private CanvasPane canvasPane;
	private double penaltyWeight = 1000.0;

    public RoutingSolver() {
        this.depots = new ArrayList<>();
        this.customers = new ArrayList<>();
        this.solutionRoutes = new ArrayList<>();
    }

    public void setDepots(List<Depot> depots) {
        this.depots = depots;
    }

    public void setCustomers(List<Customer> customers) {
        this.customers = customers;
    }

    public void setCanvasPane(CanvasPane canvasPane) {
        this.canvasPane = canvasPane;
    }

    public List<Route> getRoutes() {
        return solutionRoutes;
    }

    public void solve() {
        solutionRoutes.clear();
        int[] routeCountPerDepot = new int[depots.size()];

        for (Customer customer : customers) {
            Depot nearestDepot = findNearestDepot(customer);
            boolean assigned = false;
            for (Route route : solutionRoutes) {
                if (route.depot == nearestDepot && route.canAddCustomer(customer)) {
                    route.addCustomer(customer);
                    assigned = true;
                    break;
                }
            }
            if (!assigned) {
                int depotIndex = depots.indexOf(nearestDepot);
                if (depotIndex >= 0 && routeCountPerDepot[depotIndex] < nearestDepot.maxVehicles) {
                    Route newRoute = new Route(nearestDepot);
                    if (newRoute.canAddCustomer(customer)) {
                        newRoute.addCustomer(customer);
                        solutionRoutes.add(newRoute);
                        routeCountPerDepot[depotIndex]++;
                        assigned = true;
                    }
                }
            }
            if (!assigned) {
                System.err.println(" Warning: Could not assign customer " + customer.name);
            }
        }
        double totalDistance = solutionRoutes.stream()
                .mapToDouble(Route::computeTotalDistance)
                .sum();
        System.out.printf(" Solution completed. Total distance: %.2f\n", totalDistance);

        if (canvasPane != null) {
            canvasPane.setSolutionRoutes(solutionRoutes);
            canvasPane.draw();
        }
    }

    private Depot findNearestDepot(Customer customer) {
        Depot nearestDepot = null;
        double minDist = Double.MAX_VALUE;
        for (Depot depot : depots) {
            double dist = distance(customer.x, customer.y, depot.x, depot.y);
            if (dist < minDist) {
                minDist = dist;
                nearestDepot = depot;
            }
        }
        return nearestDepot;
    }

    private double distance(double x1, double y1, double x2, double y2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public List<Route> decodeSolution(Solution solution) {
        List<Route> routes = new ArrayList<>();
        if (solution.chromosome == null) return routes;

        Map<Integer, Customer> customerMap = getCustomerMap();  // Assume you have this mapping
        List<Depot> depots = getDepots();                       // List of all depots

        // Create a route for each depot
        Map<Depot, Route> depotRoutes = new HashMap<>();
        for (Depot depot : depots) {
            depotRoutes.put(depot, new Route(depot));
        }

        for (int customerId : solution.chromosome) {
            Customer customer = customerMap.get(customerId);
            if (customer == null) continue;

            // Assign to the nearest feasible depot
            Depot bestDepot = null;
            double bestDistance = Double.MAX_VALUE;
            for (Depot depot : depots) {
                double dist = depot.distanceTo(customer);
                if (dist < bestDistance && depotRoutes.get(depot).canAddCustomer(customer)) {
                    bestDistance = dist;
                    bestDepot = depot;
                }
            }

            if (bestDepot != null) {
                depotRoutes.get(bestDepot).addCustomer(customer);
            }
        }

        // Finalize each route
        for (Route route : depotRoutes.values()) {
            route.finalizeRoute();
            routes.add(route);
        }

        return routes;
    }


    public List<Depot> getDepots() {
        return depots;
    }


    public Map<Integer, Customer> getCustomerMap() {
        Map<Integer, Customer> map = new HashMap<>();
        for (Depot depot : depots) {
            for (Customer customer : depot.customers) {
                map.put(customer.id, customer);
            }
        }
        return map;
    }


	public double getPenaltyWeight() {
        return penaltyWeight ;
    }
    
    public void setPenaltyWeight(double weight) {
        this.penaltyWeight = weight;
    }
}