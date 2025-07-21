import javax.swing.JFrame;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.crypto.SecretKey;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

@SuppressWarnings("serial")
public class SteganographyGUI extends JFrame{
	private JTextField messageField;
    private JLabel imageFileLabel;
    private JLabel audioFileLabel;
    private File imageFile;
    private File audioFile;
    private JTextArea resultArea;

    public SteganographyGUI() {
        setTitle("Hybrid Model Comparison");
        setSize(600, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Panel for inputs
        JPanel inputPanel = new JPanel();
        inputPanel.setLayout(new GridLayout(10, 5));

        // Message input
        inputPanel.add(new JLabel("Message:"));
        messageField = new JTextField();
        inputPanel.add(messageField);

        // Image file selection
        inputPanel.add(new JLabel("Cover Image:"));
        imageFileLabel = new JLabel("No file selected");
        JButton imageButton = new JButton("Select Image");
        imageButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setFileFilter(new FileNameExtensionFilter("Image files", "png", "jpg", "bmp"));
                int returnValue = fileChooser.showOpenDialog(null);
                if (returnValue == JFileChooser.APPROVE_OPTION) {
                    imageFile = fileChooser.getSelectedFile();
                    imageFileLabel.setText(imageFile.getName());
                }
            }
        });
        inputPanel.add(imageFileLabel);
        inputPanel.add(imageButton);

        // Audio file selection
        inputPanel.add(new JLabel("Cover Audio:"));
        audioFileLabel = new JLabel("No file selected");
        JButton audioButton = new JButton("Select Audio");
        audioButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setFileFilter(new FileNameExtensionFilter("Audio files", "wav"));
                int returnValue = fileChooser.showOpenDialog(null);
                if (returnValue == JFileChooser.APPROVE_OPTION) {
                    audioFile = fileChooser.getSelectedFile();
                    audioFileLabel.setText(audioFile.getName());
                }
            }
        });
        inputPanel.add(audioFileLabel);
        inputPanel.add(audioButton);

        // Result area
        resultArea = new JTextArea();
        resultArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(resultArea);

        // Buttons
        JButton embedButton = new JButton("Embed and Extract");
        embedButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    performSteganography();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(null, "An error occurred: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        // Add components to frame
        add(inputPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(embedButton, BorderLayout.SOUTH);
    }

    private void performSteganography() throws Exception {
        if (imageFile == null || audioFile == null) {
            JOptionPane.showMessageDialog(null, "Please select both an image and an audio file.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String message = messageField.getText();
        if (message.isEmpty()) {
            JOptionPane.showMessageDialog(null, "Please enter a message.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Generate encryption key
        SecretKey key = EncryptionUtil.generateKey();

        // Encrypt data
        byte[] encryptedData = EncryptionUtil.encrypt(message.getBytes(), key);

        // Image steganography
        BufferedImage image = ImageIO.read(imageFile);
        if (image == null) {
            JOptionPane.showMessageDialog(null, "Error reading cover image.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Check image capacity
        int imageCapacity = image.getWidth() * image.getHeight();
        if (encryptedData.length * 8 > imageCapacity) {
            JOptionPane.showMessageDialog(null, "Data too large to embed in the image.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // File size before embedding
        long imageSizeBefore = imageFile.length();

        // Time tracking for image steganography
        long startTimeImage = System.nanoTime();

        BufferedImage stegoImage = ImageSteganography.embedData(image, encryptedData);
        ImageIO.write(stegoImage, "png", new File("stego_image.png"));

        long endTimeImage = System.nanoTime();
        long timeTakenImage = endTimeImage - startTimeImage;

        // File size after embedding
        File stegoImageFile = new File("stego_image.png");
        long imageSizeAfter = stegoImageFile.length();

        // Extract and decrypt the message
        BufferedImage extractedImage = ImageIO.read(stegoImageFile);
        byte[] extractedDataImage = ImageSteganography.extractData(extractedImage, encryptedData.length);
        byte[] decryptedDataImage = EncryptionUtil.decrypt(extractedDataImage, key);
        String imageResult = "Decrypted message from image: " + new String(decryptedDataImage) + "\n"
                + "Time taken (image): " + timeTakenImage / 1_000_000.0 + " ms\n"
                + "Image size before: " + imageSizeBefore / 1024 + " KB, after: " + imageSizeAfter / 1024 + " KB";

        // Audio steganography
        byte[] audio = Files.readAllBytes(audioFile.toPath());

        // Check audio capacity
        int audioCapacity = audio.length;
        if (encryptedData.length * 8 > audioCapacity) {
            JOptionPane.showMessageDialog(null, "Data too large to embed in the audio.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // File size before embedding
        long audioSizeBefore = audioFile.length();

        // Time tracking for audio steganography
        long startTimeAudio = System.nanoTime();

        byte[] stegoAudio = AudioSteganography.embedData(audio, encryptedData);
        Files.write(Paths.get("stego_audio.wav"), stegoAudio);
                                                              
        long endTimeAudio = System.nanoTime();
        long timeTakenAudio = endTimeAudio - startTimeAudio;

        // File size after embedding
        File stegoAudioFile = new File("stego_audio.wav");
        long audioSizeAfter = stegoAudioFile.length();

        // Extract and decrypt the message
        byte[] extractedAudio = Files.readAllBytes(stegoAudioFile.toPath());
        byte[] extractedDataAudio = AudioSteganography.extractData(extractedAudio, encryptedData.length);
        byte[] decryptedDataAudio = EncryptionUtil.decrypt(extractedDataAudio, key);
        String audioResult = "Decrypted message  from audio: " + new String(decryptedDataAudio) + "\n"
                + "Time taken (audio): " + timeTakenAudio / 1_000_000.0 + " ms\n"
                + "Audio size before: " + audioSizeBefore / 1024 + " KB, after: " + audioSizeAfter / 1024 + " KB";

        // Display results
        resultArea.setText(imageResult + "\n\n" + audioResult);
    }

}
