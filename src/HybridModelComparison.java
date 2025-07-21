import javax.swing.SwingUtilities;


public class HybridModelComparison {

	public static void main(String[] args) {
		SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new SteganographyGUI().setVisible(true);
            }
        });
	
	}}
