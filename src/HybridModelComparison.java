import javax.swing.SwingUtilities;

/**
 * Main application launcher for the Hybrid Steganography & Cryptography Suite.
 * - Default: Launches the interactive 3-tab Swing desktop GUI.
 * - Argument '--benchmark' or '-b': Runs the automated scientific benchmark suite in terminal.
 */
public class HybridModelComparison {

    public static void main(String[] args) {
        if (args.length > 0 && (args[0].equalsIgnoreCase("--benchmark")
                || args[0].equalsIgnoreCase("-b")
                || args[0].equalsIgnoreCase("--cli"))) {
            HybridBenchmark.main(args);
            return;
        }

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new SteganographyGUI().setVisible(true);
            }
        });
    }
}

