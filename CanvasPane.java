import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * ${user}blackcontractor@farid
 */
public class CanvasPane extends Pane {
    private final Canvas canvas;
    private final GraphicsContext gc;

    private boolean addDepotMode = false;
    private boolean addCustomerMode = false;

    private List<Depot> depots = new ArrayList<>();
    private List<Customer> customers = new ArrayList<>();
    private List<Route> solutionRoutes = new ArrayList<>();

    private boolean showDepotLabels = true;
    private boolean showCustomerLabels = true;
    private boolean showTimeWindows = true;
    private boolean showRouteLabels = true;

    private final double offsetX = 20;
    private final double offsetY = 20;
    private double scaleX = 1.0;
    private double scaleY = 1.0;
    private double translateX = 0;
    private double translateY = 0;

    public CanvasPane(double width, double height) {
        canvas = new Canvas(width, height);
        gc = canvas.getGraphicsContext2D();
        getChildren().add(canvas);

        // keep canvas sized to the pane
        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());

        setOnMouseClicked(this::handleMouseClick);

        // redraw whenever canvas size changes
        canvas.widthProperty().addListener((obs, oldVal, newVal) -> draw());
        canvas.heightProperty().addListener((obs, oldVal, newVal) -> draw());
    }

    public void draw() {
        // Ensure drawing on FX thread
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::draw);
            return;
        }

        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        computeScaling();
        drawDepots();
        drawCustomers();
        drawRoutes();
    }

    public void drawWithGenerationOverlay(List<Route> routes, int generation) {
        setSolutionRoutes(routes);
        draw();
        gc.setGlobalAlpha(0.08);
        gc.setFill(Color.LIGHTGRAY);
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        gc.setGlobalAlpha(1.0);
        // Optionally overlay generation number:
        // gc.setFill(Color.BLACK);
        // gc.fillText("Generation: " + generation, 20, 40);
    }

    private void computeScaling() {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        boolean has = false;

        for (Depot d : depots) {
            if (d == null) continue;
            minX = Math.min(minX, d.x);
            minY = Math.min(minY, d.y);
            maxX = Math.max(maxX, d.x);
            maxY = Math.max(maxY, d.y);
            has = true;
        }
        for (Customer c : customers) {
            if (c == null) continue;
            minX = Math.min(minX, c.x);
            minY = Math.min(minY, c.y);
            maxX = Math.max(maxX, c.x);
            maxY = Math.max(maxY, c.y);
            has = true;
        }
        if (!has) {
            scaleX = scaleY = 1.0;
            translateX = translateY = 0;
            return;
        }

        double dataWidth = Math.max(1e-6, maxX - minX);
        double dataHeight = Math.max(1e-6, maxY - minY);

        double availW = Math.max(10, canvas.getWidth() - 2 * offsetX);
        double availH = Math.max(10, canvas.getHeight() - 2 * offsetY);

        double s = Math.min(availW / dataWidth, availH / dataHeight);
        scaleX = scaleY = s;
        translateX = offsetX - minX * scaleX;
        translateY = offsetY - minY * scaleY;
    }

    private void drawDepots() {
        for (Depot depot : depots) {
            if (depot == null) continue;
            double x = depot.x * scaleX + translateX;
            double y = depot.y * scaleY + translateY;
            gc.setFill(Color.BLACK);
            gc.fillRect(x - 4, y - 4, 8, 8);
            if (showDepotLabels && depot.name != null) {
                gc.setFill(Color.BLACK);
                gc.fillText(depot.name, x + 8, y - 8);
            }
        }
    }

    private void drawCustomers() {
        for (Customer customer : customers) {
            if (customer == null) continue;
            double x = customer.x * scaleX + translateX;
            double y = customer.y * scaleY + translateY;
            gc.setFill(Color.BLUE);
            gc.fillOval(x - 5, y - 5, 10, 10);
            if (showCustomerLabels && customer.name != null) {
                gc.setFill(Color.BLACK);
                gc.fillText(customer.name, x + 8, y - 8);
            }
            if (showTimeWindows) {
                gc.setFill(Color.DARKGRAY);
                double rt = Double.isFinite(customer.readyTime) ? customer.readyTime : 0.0;
                double dt = Double.isFinite(customer.dueTime) ? customer.dueTime : 0.0;
                gc.fillText(String.format("[%.1f - %.1f]", rt, dt), x + 8, y + 8);
            }
        }
    }

    private void drawRoutes() {
        int colorIndex = 0;
        for (Route route : solutionRoutes) {
            if (route == null || route.customers == null || route.customers.isEmpty() || route.depot == null) {
                colorIndex++;
                continue;
            }
            Color color = Color.GRAY;
            if (route.color instanceof Color) color = (Color) route.color;
            if (route.color == null) color = Color.GRAY;
            gc.setStroke(color);
            gc.setLineWidth(2);

            double lastX = route.depot.x * scaleX + translateX;
            double lastY = route.depot.y * scaleY + translateY;
            for (Customer customer : route.customers) {
                if (customer == null) continue;
                double x = customer.x * scaleX + translateX;
                double y = customer.y * scaleY + translateY;
                gc.strokeLine(lastX, lastY, x, y);
                lastX = x;
                lastY = y;
            }
            gc.strokeLine(lastX, lastY, route.depot.x * scaleX + translateX, route.depot.y * scaleY + translateY);

            if (showRouteLabels && !route.customers.isEmpty()) {
                Customer first = route.customers.get(0);
                double labelX = first.x * scaleX + translateX;
                double labelY = first.y * scaleY + translateY;
                gc.setFill(Color.BLACK);
                gc.fillText("R" + (colorIndex + 1), labelX, labelY - 12);
            }
            colorIndex++;
        }
    }

    private void handleMouseClick(MouseEvent e) {
        double rawX = (e.getX() - translateX) / scaleX;
        double rawY = (e.getY() - translateY) / scaleY;

        if (addDepotMode) {
            Depot depot = new Depot(rawX, rawY, "D" + (depots.size() + 1));
            depots.add(depot);
            draw();
        } else if (addCustomerMode) {
            Customer customer = new Customer(rawX, rawY, "C" + (customers.size() + 1), 10, 0, 100, 10);
            customers.add(customer);
            draw();
        }
    }

    public void setAddDepotMode() {
        addDepotMode = true;
        addCustomerMode = false;
    }

    public void setAddCustomerMode() {
        addDepotMode = false;
        addCustomerMode = true;
    }

    public void setSolutionRoutes(List<Route> routes) {
        Platform.runLater(() -> {
            this.solutionRoutes = (routes == null) ? new ArrayList<>() : routes;
            draw();
        });
    }

    public void setDepots(List<Depot> depots) {
        Platform.runLater(() -> {
            this.depots = (depots == null) ? new ArrayList<>() : depots;
            draw();
        });
    }

    public void setCustomers(List<Customer> customers) {
        Platform.runLater(() -> {
            this.customers = (customers == null) ? new ArrayList<>() : customers;
            draw();
        });
    }

    public void setShowDepotLabels(boolean value) {
        Platform.runLater(() -> {
            this.showDepotLabels = value;
            draw();
        });
    }
    public void setShowCustomerLabels(boolean value) {
        Platform.runLater(() -> {
            this.showCustomerLabels = value;
            draw();
        });
    }
    public void setShowTimeWindows(boolean value) {
        Platform.runLater(() -> {
            this.showTimeWindows = value;
            draw();
        });
    }
    public void setShowRouteLabels(boolean value) {
        Platform.runLater(() -> {
            this.showRouteLabels = value;
            draw();
        });
    }

    public void saveSnapshot(String label) {
        Platform.runLater(() -> {
            double w = canvas.getWidth();
            double h = canvas.getHeight();
            if (w <= 0 || h <= 0) return;
            SnapshotParameters params = new SnapshotParameters();
            params.setFill(Color.TRANSPARENT);
            WritableImage image = canvas.snapshot(params, null);
            File dir = new File("output");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, String.format("snapshot_%s.png", label));
            try {
                ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file);
            } catch (IOException e) {
                System.err.println("⚠ Failed to save PNG: " + e.getMessage());
            }
        });
    }
}