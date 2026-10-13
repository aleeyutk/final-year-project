import java.awt.image.BufferedImage;

public class ImageSteganography {

    public static final int HEADER_MAGIC = 0x53; // 'S' for Steganography
    public static final int HEADER_BITS = 64;    // 8-byte metadata header

    /**
     * Embeds encrypted data into the image using multi-channel RGB LSB steganography
     * with pseudo-random pixel scattering.
     *
     * @param image    Cover image
     * @param data     Encrypted payload bytes
     * @param password Secret passphrase for scattering
     * @param channels Channel mode: 1 (Blue only) or 3 (Red, Green, Blue)
     * @param bitDepth Number of LSB bits per channel: 1, 2, 3, or 4
     * @return Stego image with embedded payload
     */
    public static BufferedImage embedData(BufferedImage image, byte[] data, String password, int channels, int bitDepth) throws Exception {
        if (channels != 1 && channels != 3) {
            throw new IllegalArgumentException("Channels must be 1 (Blue) or 3 (RGB).");
        }
        if (bitDepth < 1 || bitDepth > 4) {
            throw new IllegalArgumentException("Bit-depth must be between 1 and 4 LSBs.");
        }

        int width = image.getWidth();
        int height = image.getHeight();
        int totalPixels = width * height;

        int bitsPerPixel = channels * bitDepth;
        int totalPayloadBits = data.length * 8;
        int payloadPixelsNeeded = (totalPayloadBits + bitsPerPixel - 1) / bitsPerPixel;
        int totalPixelsNeeded = HEADER_BITS + payloadPixelsNeeded;

        if (totalPixelsNeeded > totalPixels) {
            int maxBytes = ((totalPixels - HEADER_BITS) * bitsPerPixel) / 8;
            throw new Exception(String.format("Data too large for image at %d-bit %s mode. Max capacity: %d bytes (%.1f KB), Required: %d bytes.",
                    bitDepth, channels == 3 ? "RGB" : "Blue", maxBytes, maxBytes / 1024.0, data.length));
        }

        BufferedImage stegoImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                stegoImage.setRGB(x, y, image.getRGB(x, y));
            }
        }

        int[] indices = StegoScatteringUtil.generatePermutation(totalPixels, totalPixelsNeeded, password);

        // 1. Prepare 64-bit self-describing metadata header:
        // [Byte 0: Magic (0x53)][Byte 1: Channels][Byte 2: BitDepth][Byte 3: 0x00][Bytes 4-7: Length]
        byte[] header = new byte[8];
        header[0] = (byte) HEADER_MAGIC;
        header[1] = (byte) channels;
        header[2] = (byte) bitDepth;
        header[3] = 0;
        int len = data.length;
        header[4] = (byte) ((len >> 24) & 0xFF);
        header[5] = (byte) ((len >> 16) & 0xFF);
        header[6] = (byte) ((len >> 8) & 0xFF);
        header[7] = (byte) (len & 0xFF);

        // Embed 64-bit header into the first 64 scattered pixels using 1-bit Blue LSB
        for (int i = 0; i < HEADER_BITS; i++) {
            int bit = (header[i / 8] >> (7 - (i % 8))) & 1;
            int pixelIdx = indices[i];
            int x = pixelIdx % width;
            int y = pixelIdx / width;
            int rgb = stegoImage.getRGB(x, y);
            rgb = (rgb & 0xFFFFFFFE) | bit;
            stegoImage.setRGB(x, y, rgb);
        }

        // 2. Embed payload bytes across subsequent pixels at requested channels & bit-depth
        int bitOffset = 0;
        for (int p = 0; p < payloadPixelsNeeded; p++) {
            int pixelIdx = indices[HEADER_BITS + p];
            int x = pixelIdx % width;
            int y = pixelIdx / width;
            int rgb = stegoImage.getRGB(x, y);

            int a = (rgb >> 24) & 0xFF;
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;

            if (channels == 3) {
                // Red channel
                if (bitOffset < totalPayloadBits) {
                    int k = Math.min(bitDepth, totalPayloadBits - bitOffset);
                    int bits = getBits(data, bitOffset, k);
                    r = (r & ~((1 << k) - 1)) | bits;
                    bitOffset += k;
                }
                // Green channel
                if (bitOffset < totalPayloadBits) {
                    int k = Math.min(bitDepth, totalPayloadBits - bitOffset);
                    int bits = getBits(data, bitOffset, k);
                    g = (g & ~((1 << k) - 1)) | bits;
                    bitOffset += k;
                }
                // Blue channel
                if (bitOffset < totalPayloadBits) {
                    int k = Math.min(bitDepth, totalPayloadBits - bitOffset);
                    int bits = getBits(data, bitOffset, k);
                    b = (b & ~((1 << k) - 1)) | bits;
                    bitOffset += k;
                }
            } else {
                // Blue channel only
                if (bitOffset < totalPayloadBits) {
                    int k = Math.min(bitDepth, totalPayloadBits - bitOffset);
                    int bits = getBits(data, bitOffset, k);
                    b = (b & ~((1 << k) - 1)) | bits;
                    bitOffset += k;
                }
            }

            int newRgb = (a << 24) | (r << 16) | (g << 8) | b;
            stegoImage.setRGB(x, y, newRgb);
        }

        return stegoImage;
    }

    /**
     * Default multi-channel embedding (RGB, 1-bit depth) with password scattering.
     */
    public static BufferedImage embedData(BufferedImage image, byte[] data, String password) throws Exception {
        return embedData(image, data, password, 3, 1);
    }

    /**
     * Sequential fallback embedding overload for backward compatibility.
     */
    public static BufferedImage embedData(BufferedImage image, byte[] data) throws Exception {
        return embedData(image, data, null, 1, 1);
    }

    /**
     * Extracts data from the stego image. Automatically detects channel mode (RGB vs Blue)
     * and bit-depth (1-4 LSBs) from the embedded self-describing header.
     */
    public static byte[] extractData(BufferedImage image, String password) throws Exception {
        int width = image.getWidth();
        int height = image.getHeight();
        int totalPixels = width * height;

        if (totalPixels < HEADER_BITS) {
            throw new Exception("Image is too small to contain a steganographic header.");
        }

        // 1. Recover 64-bit metadata header
        int[] headerIndices = StegoScatteringUtil.generatePermutation(totalPixels, HEADER_BITS, password);
        byte[] header = new byte[8];
        for (int i = 0; i < HEADER_BITS; i++) {
            int pixelIdx = headerIndices[i];
            int x = pixelIdx % width;
            int y = pixelIdx / width;
            int bit = image.getRGB(x, y) & 1;
            header[i / 8] = (byte) ((header[i / 8] << 1) | bit);
        }

        int magic = header[0] & 0xFF;
        int channels;
        int bitDepth;
        int dataLength;

        if (magic == HEADER_MAGIC) {
            // Modern self-describing header
            channels = header[1] & 0xFF;
            bitDepth = header[2] & 0xFF;
            dataLength = ((header[4] & 0xFF) << 24) | ((header[5] & 0xFF) << 16)
                    | ((header[6] & 0xFF) << 8) | (header[7] & 0xFF);
        } else {
            // Legacy 32-bit header fallback (channels = 1, bitDepth = 1)
            int legacyLen = 0;
            for (int i = 0; i < 32; i++) {
                int pixelIdx = headerIndices[i];
                int x = pixelIdx % width;
                int y = pixelIdx / width;
                int bit = image.getRGB(x, y) & 1;
                legacyLen = (legacyLen << 1) | bit;
            }
            if (legacyLen > 0 && legacyLen <= (totalPixels - 32) / 8) {
                channels = 1;
                bitDepth = 1;
                dataLength = legacyLen;
            } else {
                throw new Exception("Invalid stego header or incorrect passphrase.");
            }
        }

        if (dataLength < 0 || (channels != 1 && channels != 3) || bitDepth < 1 || bitDepth > 4) {
            throw new Exception("Corrupted carrier or invalid passphrase.");
        }

        int bitsPerPixel = channels * bitDepth;
        int totalPayloadBits = dataLength * 8;
        int payloadPixelsNeeded = (totalPayloadBits + bitsPerPixel - 1) / bitsPerPixel;
        int totalPixelsNeeded = HEADER_BITS + payloadPixelsNeeded;

        if (totalPixelsNeeded > totalPixels) {
            throw new Exception("Corrupted carrier: payload size exceeds image capacity.");
        }

        // 2. Extract payload bytes using permutation
        int[] fullIndices = StegoScatteringUtil.generatePermutation(totalPixels, totalPixelsNeeded, password);
        byte[] data = new byte[dataLength];

        int bitOffset = 0;
        for (int p = 0; p < payloadPixelsNeeded && bitOffset < totalPayloadBits; p++) {
            int pixelIdx = fullIndices[HEADER_BITS + p];
            int x = pixelIdx % width;
            int y = pixelIdx / width;
            int rgb = image.getRGB(x, y);

            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;

            if (channels == 3) {
                // Red
                if (bitOffset < totalPayloadBits) {
                    int k = Math.min(bitDepth, totalPayloadBits - bitOffset);
                    int bits = r & ((1 << k) - 1);
                    setBits(data, bitOffset, bits, k);
                    bitOffset += k;
                }
                // Green
                if (bitOffset < totalPayloadBits) {
                    int k = Math.min(bitDepth, totalPayloadBits - bitOffset);
                    int bits = g & ((1 << k) - 1);
                    setBits(data, bitOffset, bits, k);
                    bitOffset += k;
                }
                // Blue
                if (bitOffset < totalPayloadBits) {
                    int k = Math.min(bitDepth, totalPayloadBits - bitOffset);
                    int bits = b & ((1 << k) - 1);
                    setBits(data, bitOffset, bits, k);
                    bitOffset += k;
                }
            } else {
                // Blue only
                if (bitOffset < totalPayloadBits) {
                    int k = Math.min(bitDepth, totalPayloadBits - bitOffset);
                    int bits = b & ((1 << k) - 1);
                    setBits(data, bitOffset, bits, k);
                    bitOffset += k;
                }
            }
        }

        return data;
    }

    /**
     * Sequential extraction overload for backward compatibility.
     */
    public static byte[] extractData(BufferedImage image) throws Exception {
        return extractData(image, null);
    }

    /**
     * Legacy extraction method for backward compatibility.
     */
    public static byte[] extractData(BufferedImage image, int dataSize) {
        try {
            return extractData(image);
        } catch (Exception e) {
            int width = image.getWidth();
            int height = image.getHeight();
            byte[] data = new byte[dataSize];
            int dataIndex = 0;

            for (int i = 0; i < height; i++) {
                for (int j = 0; j < width; j++) {
                    if (dataIndex < dataSize * 8) {
                        int rgb = image.getRGB(j, i);
                        int bit = rgb & 1;
                        data[dataIndex / 8] = (byte) ((data[dataIndex / 8] << 1) | bit);
                        dataIndex++;
                    }
                }
            }
            return data;
        }
    }

    /**
     * Reads k bits from byte array starting at bitOffset.
     */
    private static int getBits(byte[] data, int bitOffset, int k) {
        int val = 0;
        for (int i = 0; i < k; i++) {
            int pos = bitOffset + i;
            int bit = (data[pos / 8] >> (7 - (pos % 8))) & 1;
            val = (val << 1) | bit;
        }
        return val;
    }

    /**
     * Writes k bits into byte array starting at bitOffset.
     */
    private static void setBits(byte[] data, int bitOffset, int val, int k) {
        for (int i = 0; i < k; i++) {
            int pos = bitOffset + i;
            int bit = (val >> (k - 1 - i)) & 1;
            int bytePos = pos / 8;
            int bitPos = pos % 8;
            if (bit == 1) {
                data[bytePos] = (byte) (data[bytePos] | (1 << (7 - bitPos)));
            } else {
                data[bytePos] = (byte) (data[bytePos] & ~(1 << (7 - bitPos)));
            }
        }
    }

    /**
     * Calculates Mean Squared Error (MSE) across R, G, B channels.
     */
    public static double calculateMSE(BufferedImage original, BufferedImage stego) {
        int width = Math.min(original.getWidth(), stego.getWidth());
        int height = Math.min(original.getHeight(), stego.getHeight());
        double sum = 0.0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb1 = original.getRGB(x, y);
                int rgb2 = stego.getRGB(x, y);

                int r1 = (rgb1 >> 16) & 0xFF;
                int g1 = (rgb1 >> 8) & 0xFF;
                int b1 = rgb1 & 0xFF;

                int r2 = (rgb2 >> 16) & 0xFF;
                int g2 = (rgb2 >> 8) & 0xFF;
                int b2 = rgb2 & 0xFF;

                sum += (r1 - r2) * (r1 - r2);
                sum += (g1 - g2) * (g1 - g2);
                sum += (b1 - b2) * (b1 - b2);
            }
        }
        return sum / (3.0 * width * height);
    }

    /**
     * Calculates Peak Signal-to-Noise Ratio (PSNR) in decibels (dB).
     * Standard benchmark metric: > 40 dB indicates imperceptible modification.
     */
    public static double calculatePSNR(BufferedImage original, BufferedImage stego) {
        double mse = calculateMSE(original, stego);
        if (mse == 0.0) {
            return Double.POSITIVE_INFINITY;
        }
        return 10.0 * Math.log10((255.0 * 255.0) / mse);
    }
}
