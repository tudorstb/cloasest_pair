import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ClosestPair extends JFrame {

    private ArrayList<Point> points; // List to store all generated points.
    private Point closestPoint1; // One of the closest points.
    private Point closestPoint2; // The other closest point.
    private boolean isHighlighted; // Flag to indicate if the closest pair is highlighted.
    private Random random; // Random generator for point coordinates.
    private boolean allowDuplicates; // Flag to allow or disallow duplicate points.
    private long computationTime; // Time taken to compute the closest pair.

    public ClosestPair() {
        // Initialize the JFrame with a title and a default close operation.
        setTitle("Closest Pair of Points");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        random = new Random(); // Initialize the random generator.
        isHighlighted = false; // Initially, no pair is highlighted.
        allowDuplicates = true; // Default setting is to allow duplicate points.

        // Set the icon for the application (optional, add an icon file named "icon.png" in your project directory).
        setIconImage(new ImageIcon("icon.png").getImage());

        // Create an input panel at the top of the window.
        JPanel inputPanel = new JPanel();
        JLabel label = new JLabel("Enter number of points:");
        JTextField textField = new JTextField(10); // Input field for the number of points.
        JButton generateButton = new JButton("Generate Points"); // Button to trigger point generation.

        // Dropdown to allow or disallow duplicate points.
        String[] options = {"Allow Duplicates", "Disallow Duplicates"};
        JComboBox<String> duplicateDropdown = new JComboBox<>(options);
        duplicateDropdown.addActionListener(e -> {
            // Update the allowDuplicates variable based on the dropdown selection.
            allowDuplicates = duplicateDropdown.getSelectedIndex() == 0;
        });

        // Add components to the input panel.
        inputPanel.add(label);
        inputPanel.add(textField);
        inputPanel.add(generateButton);
        inputPanel.add(duplicateDropdown);

        // Display the number of available CPU cores in the system.
        int cpuCores = Runtime.getRuntime().availableProcessors(); // Fetch available CPU cores.
        JLabel coresLabel = new JLabel("CPU Cores: " + cpuCores);
        inputPanel.add(coresLabel);

        // Add the input panel to the top of the frame.
        add(inputPanel, BorderLayout.NORTH);

        // Create a custom canvas for displaying points and closest pair highlights.
        PointCanvas canvas = new PointCanvas();
        add(canvas, BorderLayout.CENTER);

        // Button to highlight the closest pair of points.
        JButton highlightButton = new JButton("Highlight Closest Pair");
        highlightButton.setEnabled(false); // Initially disabled until points are generated.
        add(highlightButton, BorderLayout.SOUTH);

        // Event listener for the "Generate Points" button.
        generateButton.addActionListener(e -> {
            try {
                // Parse user input for the number of points to generate.
                int numPoints = Integer.parseInt(textField.getText());
                if (numPoints < 2) {
                    // Ensure at least 2 points are entered.
                    throw new IllegalArgumentException("You must enter at least 2 points.");
                }
                points = new ArrayList<>(); // Initialize the points list.
                Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize(); // Get screen dimensions.

                // Use multithreading to generate points in parallel for performance optimization.
                ExecutorService executor = Executors.newFixedThreadPool(cpuCores);
                List<Future<List<Point>>> futures = new ArrayList<>();
                int chunkSize = numPoints / cpuCores; // Divide work equally among threads.

                for (int i = 0; i < cpuCores; i++) {
                    int start = i * chunkSize;
                    int end = (i == cpuCores - 1) ? numPoints : start + chunkSize;
                    futures.add(executor.submit(() -> {
                        // Each thread generates a subset of points.
                        List<Point> localPoints = new ArrayList<>();
                        Set<Point> localSet = allowDuplicates ? null : new HashSet<>(); // Track duplicates if disallowed.
                        Random localRandom = new Random();
                        for (int j = start; j < end; j++) {
                            Point point;
                            do {
                                // Generate random x and y coordinates within the screen dimensions.
                                int x = localRandom.nextInt(screenSize.width);
                                int y = localRandom.nextInt(screenSize.height);
                                point = new Point(x, y);
                            } while (!allowDuplicates && !localSet.add(point)); // Avoid duplicates if disallowed.
                            localPoints.add(point);
                        }
                        return localPoints;
                    }));
                }

                points.clear(); // Clear existing points.
                for (Future<List<Point>> future : futures) {
                    // Collect points generated by all threads.
                    points.addAll(future.get());
                }

                executor.shutdown(); // Shutdown the thread pool.

                // Reset state after generating points.
                closestPoint1 = null;
                closestPoint2 = null;
                computationTime = 0;
                isHighlighted = false;
                canvas.repaint(); // Trigger repaint to display generated points.
                highlightButton.setEnabled(true); // Enable the highlight button.
            } catch (NumberFormatException ex) {
                // Handle invalid number format input (e.g., non-integer values).
                JOptionPane.showMessageDialog(this, "Please enter a valid positive integer.", "Invalid Input", JOptionPane.ERROR_MESSAGE);
            } catch (IllegalArgumentException ex) {
                // Handle invalid arguments like fewer than 2 points.
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Invalid Input", JOptionPane.ERROR_MESSAGE);
            } catch (Exception ex) {
                // Handle other exceptions like thread interruptions.
                ex.printStackTrace();
            }
        });

        // Event listener for the "Highlight Closest Pair" button.
        highlightButton.addActionListener(e -> {
            if (points != null && points.size() > 1) {
                if (isHighlighted) {
                    // If already highlighted, reset the state.
                    closestPoint1 = null;
                    closestPoint2 = null;
                    computationTime = 0;
                    isHighlighted = false;
                } else {
                    // Use multithreading to find the closest pair efficiently.
                    ExecutorService executor = Executors.newFixedThreadPool(cpuCores);
                    try {
                        List<Future<Pair>> futures = new ArrayList<>();

                        int chunkSize = points.size() / cpuCores; // Divide points among threads.
                        for (int i = 0; i < cpuCores; i++) {
                            int start = i * chunkSize;
                            int end = (i == cpuCores - 1) ? points.size() : start + chunkSize;
                            List<Point> subset = points.subList(start, end);
                            futures.add(executor.submit(new ClosestPairTask(subset))); // Submit task.
                        }

                        Pair globalClosestPair = null;
                        double minDistance = Double.MAX_VALUE;
                        List<Point> allPoints = new ArrayList<>(points);

                        long startTime = System.nanoTime(); // Start timing the operation.

                        // Find closest pair within subsets.
                        for (Future<Pair> future : futures) {
                            Pair result = future.get(); // Fetch results from threads.
                            if (result.distance < minDistance) {
                                // Update the global closest pair if a smaller distance is found.
                                minDistance = result.distance;
                                globalClosestPair = result;
                            }
                        }

                        // Cross-subset comparison to ensure closest pair is found across all threads.
                        for (int i = 0; i < allPoints.size(); i++) {
                            for (int j = i + 1; j < allPoints.size(); j++) {
                                Point p1 = allPoints.get(i);
                                Point p2 = allPoints.get(j);
                                double distance = p1.distance(p2);
                                if (distance < minDistance) {
                                    minDistance = distance;
                                    globalClosestPair = new Pair(p1, p2, distance);
                                }
                            }
                        }

                        computationTime = System.nanoTime() - startTime; // End timing.

                        if (globalClosestPair != null) {
                            updateClosestPair(globalClosestPair.p1, globalClosestPair.p2); // Update closest pair.
                        }

                    } catch (Exception ex) {
                        ex.printStackTrace();
                    } finally {
                        executor.shutdown(); // Shutdown the thread pool.
                    }
                }
                canvas.repaint(); // Trigger repaint to update the UI.
            }
        });

        // Adjust the window to a maximized frame for better visibility.
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setVisible(true);
    }

    private synchronized void updateClosestPair(Point p1, Point p2) {
        // Update the closest points and mark the state as highlighted.
        closestPoint1 = p1;
        closestPoint2 = p2;
        isHighlighted = true;
        repaint(); // Trigger a repaint to show the closest pair.
    }

    private class PointCanvas extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (points != null) {
                for (Point point : points) {
                    // Draw each point as a small black circle.
                    g.setColor(Color.BLACK);
                    g.fillOval(point.x - 5, point.y - 5, 10, 10);
                }
                if (closestPoint1 != null && closestPoint2 != null && isHighlighted) {
                    // Highlight the closest pair of points with red circles and a line connecting them.
                    g.setColor(Color.RED);
                    g.fillOval(closestPoint1.x - 5, closestPoint1.y - 5, 10, 10);
                    g.fillOval(closestPoint2.x - 5, closestPoint2.y - 5, 10, 10);
                    g.drawLine(closestPoint1.x, closestPoint1.y, closestPoint2.x, closestPoint2.y);

                    // Add red circles around the highlighted points for better visibility.
                    g.drawOval(closestPoint1.x - 10, closestPoint1.y - 10, 20, 20);
                    g.drawOval(closestPoint2.x - 10, closestPoint2.y - 10, 20, 20);

                    // Display coordinates of closest points with a background.
                    g.setColor(new Color(255, 255, 255, 200)); // Opaque white background.
                    g.fillRect(getWidth() - 220, 5, 200, 70);

                    g.setColor(Color.BLACK);
                    g.drawRect(getWidth() - 220, 5, 200, 70);
                    g.drawString("Point A: (" + closestPoint1.x + ", " + closestPoint1.y + ")", getWidth() - 210, 25);
                    g.drawString("Point B: (" + closestPoint2.x + ", " + closestPoint2.y + ")", getWidth() - 210, 45);
                    g.drawString("Time: " + (computationTime / 1_000_000) + " ms", getWidth() - 210, 65);
                }
            }
        }
    }

    public static void main(String[] args) {
        // Launch the application.
        SwingUtilities.invokeLater(ClosestPair::new);
    }
}

// Callable task to find the closest pair of points in a subset.
class ClosestPairTask implements Callable<Pair> {

    private final List<Point> points; // List of points to analyze.

    public ClosestPairTask(List<Point> points) {
        this.points = points;
    }

    @Override
    public Pair call() {
        double minDistance = Double.MAX_VALUE;
        Point closestPoint1 = null;
        Point closestPoint2 = null;

        // Compare each pair of points to find the closest distance in the subset.
        for (int i = 0; i < points.size(); i++) {
            for (int j = i + 1; j < points.size(); j++) {
                Point p1 = points.get(i);
                Point p2 = points.get(j);
                double distance = p1.distance(p2); // Calculate Euclidean distance.
                if (distance < minDistance) {
                    // Update the closest pair if a smaller distance is found.
                    minDistance = distance;
                    closestPoint1 = p1;
                    closestPoint2 = p2;
                }
            }
        }

        // Return the closest pair and their distance.
        return new Pair(closestPoint1, closestPoint2, minDistance);
    }
}

// Class to hold a pair of points and their distance.
class Pair {
    Point p1; // First point.
    Point p2; // Second point.
    double distance; // Distance between the two points.

    public Pair(Point p1, Point p2, double distance) {
        this.p1 = p1;
        this.p2 = p2;
        this.distance = distance;
    }
}
