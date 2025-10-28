import java.util.*;
import javafx.scene.paint.Paint;
import javafx.scene.paint.Color;

/**
 * Route class for MDVRPTW / HFVRP
 * Handles depot, customers, heterogeneous vehicles, time windows, and costs.
 * Optimized with caching for distance/time evaluation.
 * @author blackcontractor
 */
public class Route {
    private static final List<Color> PRESET_COLORS = Arrays.asList(
        Color.RED, Color.BLUE, Color.GREEN, Color.ORANGE, Color.PURPLE,
        new Color(0.59, 0.29, 0.0, 1.0), // Brown
        Color.CYAN, Color.MAGENTA, Color.DARKGRAY, Color.PINK
    );

    Depot depot;
    List<Customer> customers = new ArrayList<>();
    private Vehicle vehicle;

    public double totalDistance = 0;
    public int totalLoad = 0;
    public double penalty = 0;
    public int timeWindowViolations = 0;
    public Paint color;
    public double waitTime = 0;

    // ==== HFVRP fields ====
    public boolean capacityViolation = false;
    public int vehicleId = -1;
    public int depotId = -1;

    // ==== Cache fields ====
    private boolean dirty = true;  // mark if recomputation needed
    private double cachedDuration = 0;
    private double cachedCost = 0;

    public Route(Depot depot) {
        this.depot = depot;
        if (depot != null) {
            this.color = PRESET_COLORS.get((depot.name.hashCode() & 0x7fffffff) % PRESET_COLORS.size());
            this.depotId = depot.id;
        } else {
            this.color = Color.GRAY; // fallback color
        }
    }

    /** Add a customer and mark route dirty */
    public void addCustomer(Customer customer) {
        if (customer == null) return;
        customers.add(customer);
        totalLoad += customer.demand;
        markDirty();
    }

    /** Check if a customer can be feasibly added */
    public boolean canAddCustomer(Customer customer) {
        if (customer == null) return false;

        int effectiveCapacity = (vehicle != null) ? vehicle.capacity :
                                (depot != null ? depot.vehicleCapacity : Integer.MAX_VALUE);
        double effectiveDuration = (depot != null ? depot.maxDuration : Double.MAX_VALUE);

        int newLoad = totalLoad + customer.demand;
        double newDuration = getRouteDurationWith(customer);

        return (newLoad <= effectiveCapacity) && (newDuration <= effectiveDuration);
    }

    /** Finalize route (forces recomputation) */
    public void finalizeRoute() {
        markDirty();
        recompute();
    }

    /** Mark route as dirty (forces recomputation on next access) */
    void markDirty() {
        dirty = true;
    }

    /** Ensure all cached fields are valid */
    public void recompute() {
        if (!dirty) return;
        computeTotalDistance();
        evaluateTimeWindows();
        cachedDuration = computeRouteDurationInternal();
        cachedCost = computeTotalCostInternal();
        dirty = false;
    }

    /** Check feasibility of current route */
    public boolean isFeasible() {
        recompute(); // ensure updated
        int effectiveCapacity = (vehicle != null) ? vehicle.capacity :
                                (depot != null ? depot.vehicleCapacity : Integer.MAX_VALUE);
        double effectiveDuration = (depot != null ? depot.maxDuration : Double.MAX_VALUE);

        capacityViolation = (totalLoad > effectiveCapacity);

        return !capacityViolation &&
               timeWindowViolations == 0 &&
               cachedDuration <= effectiveDuration;
    }

    /** Compute duration of this route (ensures recompute) */
    public double computeRouteDuration() {
        recompute();
        return cachedDuration;
    }

    /** Internal duration computation (without dirty check) */
    private double computeRouteDurationInternal() {
        double duration = 0;

        if (depot == null || customers.isEmpty()) return 0;

        // depot -> first customer
        duration += depot.distanceTo(customers.get(0));

        // between customers
        for (int i = 0; i < customers.size() - 1; i++) {
            duration += customers.get(i).distanceTo(customers.get(i + 1));
            duration += customers.get(i).serviceTime;
        }

        // last customer + return depot
        Customer last = customers.get(customers.size() - 1);
        duration += last.serviceTime;
        duration += last.distanceTo(depot);

        return duration;
    }

    /** Compute route duration if another customer is appended */
    public double getRouteDurationWith(Customer nextCustomer) {
        double duration = 0;

        if (!customers.isEmpty()) {
            duration += (depot != null) ? depot.distanceTo(customers.get(0)) : 0;
            for (int i = 0; i < customers.size() - 1; i++) {
                duration += customers.get(i).distanceTo(customers.get(i + 1)) +
                            customers.get(i).serviceTime;
            }
            duration += customers.get(customers.size() - 1).serviceTime;

            if (nextCustomer != null) {
                duration += customers.get(customers.size() - 1).distanceTo(nextCustomer) +
                            nextCustomer.serviceTime;
                if (depot != null) duration += nextCustomer.distanceTo(depot);
            } else if (depot != null) {
                duration += customers.get(customers.size() - 1).distanceTo(depot);
            }
        } else if (nextCustomer != null && depot != null) {
            duration += depot.distanceTo(nextCustomer);
            duration += nextCustomer.serviceTime;
            duration += nextCustomer.distanceTo(depot);
        }

        return duration;
    }

    /** Compute total distance (ignores service times) */
    public double computeTotalDistance() {
        if (customers.isEmpty() || depot == null) {
            this.totalDistance = 0;
            return 0;
        }

        double dist = depot.distanceTo(customers.get(0));
        for (int i = 0; i < customers.size() - 1; i++) {
            dist += customers.get(i).distanceTo(customers.get(i + 1));
        }
        dist += customers.get(customers.size() - 1).distanceTo(depot);

        this.totalDistance = dist;
        return dist;
    }

    /** Compute total cost (cached) */
    public double computeTotalCost() {
        recompute();
        return cachedCost;
    }

    /** Internal cost computation (no caching check) */
    private double computeTotalCostInternal() {
        double dist = computeTotalDistance();
        return (vehicle != null) ? dist * vehicle.cost : dist;
    }

    /** Re-evaluate arrival times, penalties, and waiting times */
    public void evaluateTimeWindows() {
        this.penalty = 0;
        this.timeWindowViolations = 0;
        this.waitTime = 0;

        if (depot == null) return;

        double currentTime = 0;
        Customer prev = null;

        for (Customer customer : customers) {
            if (prev == null) {
                currentTime = depot.distanceTo(customer);
            } else {
                currentTime += prev.distanceTo(customer);
            }

            if (currentTime < customer.readyTime) {
                waitTime += customer.readyTime - currentTime;
                currentTime = customer.readyTime;
            }

            if (currentTime > customer.dueTime) {
                timeWindowViolations++;
                penalty += currentTime - customer.dueTime;
            }

            customer.arrivalTime = currentTime;
            currentTime += customer.serviceTime;
            prev = customer;
        }
    }

    @Override
    public String toString() {
        return (depot != null ? depot.name : "NoDepot") +
               " -> " + customers.size() +
               " customers | Load=" + totalLoad +
               " | TW Violations=" + timeWindowViolations +
               " | Distance=" + String.format("%.2f", totalDistance) +
               (vehicle != null ? (" | Vehicle=" + vehicle.typeName) : "");
    }

    /** Deep copy route (customers deep-copied, vehicle shallow copy unless immutable) */
    public Route deepCopy() {
        Route copy = new Route(this.depot);

        copy.totalDistance = this.totalDistance;
        copy.totalLoad = this.totalLoad;
        copy.penalty = this.penalty;
        copy.timeWindowViolations = this.timeWindowViolations;
        copy.waitTime = this.waitTime;
        copy.color = this.color;

        copy.capacityViolation = this.capacityViolation;
        copy.vehicleId = this.vehicleId;
        copy.depotId = this.depotId;

        // Shallow copy vehicle (Vehicle is immutable in your model)
        copy.vehicle = this.vehicle;

        // Deep copy customers
        copy.customers = new ArrayList<>();
        for (Customer c : this.customers) {
            copy.customers.add(new Customer(c));
        }

        copy.dirty = true; // ensure recalculation in copy
        return copy;
    }

    // === Getters for solver/ACO compatibility ===
    public List<Customer> getCustomers() { return customers; }
    public Depot getDepot() { return depot; }
    public Vehicle getVehicle() { return vehicle; }
    public double getPenalty() { return penalty; }
    public double getWaitTime() { return waitTime; }
    public double getCachedDuration() { recompute(); return cachedDuration; }
    public double getCachedCost() { recompute(); return cachedCost; }

    // Setter for vehicle assignment
    public void setVehicle(Vehicle vehicle) {
        this.vehicle = vehicle;
        markDirty();
    }
    
    public double getRouteDuration() {
        return computeRouteDuration();
    }

}
