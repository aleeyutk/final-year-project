import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Random;

/**
 * Utility for pseudo-random pixel and audio sample scattering.
 * Uses a deterministic Fisher-Yates permutation seeded by SHA-256 of the stego passphrase.
 * Distributes embedded bits uniformly across carrier files to defeat Chi-Square,
 * Sample Pairs Analysis (SPA), and visual steganalysis.
 */
public class StegoScatteringUtil {

    /**
     * Derives a deterministic 64-bit PRNG seed from the passphrase using SHA-256.
     */
    public static long deriveSeed(String password) {
        if (password == null || password.isEmpty()) {
            return 0L;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.wrap(hash);
            return buffer.getLong();
        } catch (Exception e) {
            return password.hashCode();
        }
    }

    /**
     * Generates a pseudo-random permutation of unique indices across available carrier slots.
     * Uses a partial Fisher-Yates shuffle seeded by the derived stego-key.
     *
     * @param totalSlots Total available carrier units (pixels or audio samples)
     * @param count      Number of indices required for embedding
     * @param password   Secret passphrase used as the scattering key (null for sequential)
     * @return Array of unique indices in deterministic pseudo-random order
     */
    public static int[] generatePermutation(int totalSlots, int count, String password) {
        if (count > totalSlots) {
            throw new IllegalArgumentException(
                    "Embedding requires " + count + " slots, but only " + totalSlots + " are available in cover carrier.");
        }

        // Sequential fallback if no password provided
        if (password == null || password.isEmpty()) {
            int[] sequential = new int[count];
            for (int i = 0; i < count; i++) {
                sequential[i] = i;
            }
            return sequential;
        }

        int[] pool = new int[totalSlots];
        for (int i = 0; i < totalSlots; i++) {
            pool[i] = i;
        }

        Random rng = new Random(deriveSeed(password));
        for (int i = 0; i < count; i++) {
            int j = i + rng.nextInt(totalSlots - i);
            int temp = pool[i];
            pool[i] = pool[j];
            pool[j] = temp;
        }

        return Arrays.copyOf(pool, count);
    }
}
