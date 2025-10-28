import java.util.ArrayList;
import java.util.List;

/**
 * Depot class for MDVRPTW / HFVRP
 * Stores depot coordinates, vehicle capacity, fleet size, and supported vehicles.
 * Compatible with ACO, GA, DE, MA solver variants.
 * 
 * @author blackcontractor
 */
public class Depot {
    public final double x;
    public final double y;
    public final String name;
    public int maxVehicles;
    public double maxDuration;
    public int vehicleCapacity;
    public int id = -1;
    public Customer[] customers;

    // ==== HFVRP: List of available vehicle types for this depot (or null for global pool) ====
    private List<Vehicle> vehicles = new ArrayList<>();

    // === Constructors ===

    /** Simple constructor with coordinates and name. */
    public Depot(double x, double y, String name) {
        this.id = -1;
        this.x = x;
        this.y = y;
        this.name = (name != null) ? name : "Depot";
        this.vehicleCapacity = 100;
        this.maxVehicles = 1;
        this.maxDuration = 9999.0;
    }

    /** Full parameter constructor for dataset-driven creation. */
    public Depot(int id, double x, double y, int vehicleCapacity, int maxVehicles) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.vehicleCapacity = vehicleCapacity;
        this.maxVehicles = maxVehicles;
        this.name = "D" + id;
        this.maxDuration = 9999.0;
    }

    /**  Copy constructor for deep copy of Depot. */
    public Depot(Depot d) {
        this.id = d.id;
        this.x = d.x;
        this.y = d.y;
        this.name = d.name;
        this.maxVehicles = d.maxVehicles;
        this.maxDuration = d.maxDuration;
        this.vehicleCapacity = d.vehicleCapacity;
        this.customers = (d.customers != null) ? d.customers.clone() : null;

        this.vehicles = new ArrayList<>();
        if (d.vehicles != null) {
            this.vehicles.addAll(d.vehicles); // shallow copy vehicles (assumed immutable)
        }
    }

    // === Distance Methods ===

    /** Distance between this depot and a customer. */
    public double distanceTo(Customer customer) {
        double dx = this.x - customer.x;
        double dy = this.y - customer.y;
        return Math.hypot(dx, dy);
    }

    /** Distance between two depots (for multi-depot scenarios). */
    public double distanceTo(Depot other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        return Math.hypot(dx, dy);
    }

    // === HFVRP Vehicle Handling ===

    /** HFVRP: Add a vehicle type to this depot's available fleet. */
    public void addVehicle(Vehicle vehicle) {
        if (vehicle != null) {
            vehicles.add(vehicle);
        }
    }

    /** HFVRP: Get available vehicles for this depot. */
    public List<Vehicle> getAvailableVehicles() {
        return new ArrayList<>(vehicles); // defensive copy
    }

    /** Wrapper for solver consistency. */
    public int getVehicleCapacity() {
        return vehicleCapacity;
    }

    // === Utility ===

    /** Clone wrapper for consistency with Customer/Route. */
    public Depot clone() {
        return new Depot(this);
    }

    @Override
    public String toString() {
        return String.format("%s (%.2f, %.2f), Capacity=%d, MaxVehicles=%d",
                name, x, y, vehicleCapacity, maxVehicles);
    }
}
