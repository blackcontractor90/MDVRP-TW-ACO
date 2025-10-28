/**
 * Customer class for MDVRP/VRPTW
 * Represents a demand node with time window and service constraints.
 * Compatible with Solution, Route, and solver algorithms.
 * @author blackcontractor
 */
public class Customer {
    public int id;
    public double x;
    public double y;
    public int demand;
    public double readyTime;
    public double dueTime;
    public double serviceTime;
    public double arrivalTime; // set dynamically during routing
    public String name;
    public int assignedDepotId = 0;

    // === Constructors ===

    /** Recommended constructor for manual creation (without explicit ID). */
    public Customer(double x, double y, String name, int demand,
                    double readyTime, double dueTime, double serviceTime) {
        this.id = -1; // Default until assigned
        this.x = x;
        this.y = y;
        this.name = (name != null && !name.isEmpty()) ? name : "C?";
        this.demand = demand;
        this.readyTime = readyTime;
        this.dueTime = dueTime;
        this.serviceTime = serviceTime;
        this.arrivalTime = 0;
    }

    /** Standard constructor for dataset loading (with ID). */
    public Customer(int id, double x, double y, int demand,
                    double readyTime, double dueTime, double serviceTime) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.demand = demand;
        this.readyTime = readyTime;
        this.dueTime = dueTime;
        this.serviceTime = serviceTime;
        this.arrivalTime = 0;
        this.name = "C" + id;
    }

    /**  Copy constructor (deep copy for routes/solutions). */
    public Customer(Customer c) {
        this.id = c.id;
        this.x = c.x;
        this.y = c.y;
        this.demand = c.demand;
        this.readyTime = c.readyTime;
        this.dueTime = c.dueTime;
        this.serviceTime = c.serviceTime;
        this.arrivalTime = c.arrivalTime;
        this.name = (c.name != null && !c.name.isEmpty()) ? c.name : "C" + c.id;
        this.assignedDepotId = c.assignedDepotId;
    }

    // === Core Methods ===

    /** Euclidean distance to another customer. */
    public double distanceTo(Customer other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /** Euclidean distance to a depot. */
    public double distanceTo(Depot depot) {
        double dx = this.x - depot.x;
        double dy = this.y - depot.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /** Returns service end time (arrival + service). */
    public double getServiceEndTime() {
        return arrivalTime + serviceTime;
    }

    /** Returns a safe string label (ID or name). */
    public String getLabel() {
        return (name != null && !name.isEmpty()) ? name : "C" + id;
    }

    /** Clone wrapper for convenience. */
    @Override
    public Customer clone() {
        return new Customer(this);
    }

    @Override
    public String toString() {
        return String.format("Customer %s (Demand=%d, TW=[%.1f, %.1f], Arrival=%.1f)",
                getLabel(), demand, readyTime, dueTime, arrivalTime);
    }
}
