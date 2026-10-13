import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;

public class EncryptionUtil {
    private static final int GCM_IV_LENGTH = 12; // 96 bits recommended for GCM
    private static final int GCM_TAG_LENGTH = 128; // 128-bit authentication tag
    private static final int SALT_LENGTH = 16;
    private static final int PBKDF2_ITERATIONS = 65536;
    private static final int KEY_LENGTH = 256; // AES-256

    /**
     * Authenticated AES-256-GCM encryption with PBKDF2 key derivation.
     * Generates a random 16-byte salt and 12-byte IV, returning [salt + iv + ciphertext].
     */
    public static byte[] encrypt(byte[] data, String password) throws Exception {
        byte[] salt = new byte[SALT_LENGTH];
        SecureRandom random = new SecureRandom();
        random.nextBytes(salt);

        SecretKey key = deriveKey(password, salt);

        byte[] iv = new byte[GCM_IV_LENGTH];
        random.nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        byte[] cipherText = cipher.doFinal(data);

        ByteBuffer buffer = ByteBuffer.allocate(salt.length + iv.length + cipherText.length);
        buffer.put(salt);
        buffer.put(iv);
        buffer.put(cipherText);
        return buffer.array();
    }

    /**
     * Authenticated AES-256-GCM decryption with PBKDF2 key derivation.
     * Verifies cryptographic integrity via GCM authentication tag.
     */
    public static byte[] decrypt(byte[] encryptedPayload, String password) throws Exception {
        if (encryptedPayload.length < SALT_LENGTH + GCM_IV_LENGTH + 16) {
            throw new IllegalArgumentException("Encrypted payload is too short to be valid.");
        }

        ByteBuffer buffer = ByteBuffer.wrap(encryptedPayload);
        byte[] salt = new byte[SALT_LENGTH];
        buffer.get(salt);

        byte[] iv = new byte[GCM_IV_LENGTH];
        buffer.get(iv);

        byte[] cipherText = new byte[buffer.remaining()];
        buffer.get(cipherText);

        SecretKey key = deriveKey(password, salt);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        return cipher.doFinal(cipherText);
    }

    /**
     * Derives a 256-bit AES secret key from a password and salt using PBKDF2.
     */
    public static SecretKey deriveKey(String password, byte[] salt) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH);
        SecretKey secret = factory.generateSecret(spec);
        return new SecretKeySpec(secret.getEncoded(), "AES");
    }

    // --- Legacy / SecretKey Support Methods ---

    public static byte[] encrypt(byte[] data, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return cipher.doFinal(data);
    }

    public static byte[] decrypt(byte[] data, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, key);
        return cipher.doFinal(data);
    }

    public static SecretKey generateKey() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(128);
        return keyGen.generateKey();
    }
}
