/**
 * Vehicle class for MDVRPTW / HFVRP
 * Supports capacity, cost, and typeName for heterogeneous fleets.
 * Compatible with evolutionary solvers and Depot integration.
 * 
 * @author blackcontractor
 */
public class Vehicle {
    public final int id;
    public final int capacity;
    public final double cost;     // Cost per distance unit (or fixed, depending on solver use)
    public final String typeName;

    // === Constructors ===

    /** Full constructor */
    public Vehicle(int id, int capacity, double cost, String typeName) {
        this.id = id;
        this.capacity = capacity;
        this.cost = cost;
        this.typeName = (typeName != null) ? typeName : "Generic";
    }

    /** Default constructor (safety for serialization / dataset loading) */
    public Vehicle() {
        this(0, 100, 1.0, "Generic");
    }

    /** ✅ Copy constructor */
    public Vehicle(Vehicle v) {
        this(v.id, v.capacity, v.cost, v.typeName);
    }

    // === Utility Methods ===

    public int getCapacity() {
        return capacity;
    }

    public double getCost() {
        return cost;
    }

    public String getTypeName() {
        return typeName;
    }

    /** Clone wrapper for evolutionary algorithms */
    @Override
    public Vehicle clone() {
        return new Vehicle(this);
    }

    @Override
    public String toString() {
        return String.format("Vehicle[%d] (Type=%s, Cap=%d, Cost=%.2f)",
                id, typeName, capacity, cost);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Vehicle)) return false;
        Vehicle v = (Vehicle) o;
        return id == v.id &&
               capacity == v.capacity &&
               Double.compare(v.cost, cost) == 0 &&
               typeName.equals(v.typeName);
    }

    @Override
    public int hashCode() {
        int result = Integer.hashCode(id);
        result = 31 * result + Integer.hashCode(capacity);
        long temp = Double.doubleToLongBits(cost);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + typeName.hashCode();
        return result;
    }
}
