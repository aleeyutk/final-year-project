public class AudioSteganography {

    /**
     * Locates the start index of PCM audio samples in a WAV file by parsing RIFF chunks.
     * Prevents corrupting the 44-byte (or extended) RIFF/WAVE header.
     */
    public static int findDataOffset(byte[] audio) {
        for (int i = 12; i < Math.min(audio.length - 8, 1024); i++) {
            if (audio[i] == 'd' && audio[i + 1] == 'a' && audio[i + 2] == 't' && audio[i + 3] == 'a') {
                return i + 8; // skip 4 bytes of 'data' tag + 4 bytes of chunk length
            }
        }
        return 44; // Standard canonical PCM WAV header offset fallback
    }

    /**
     * Embeds encrypted data into the audio PCM samples using pseudo-random sample scattering.
     * Leaves the WAV RIFF header untouched and scatters bits across the PCM samples
     * using a permutation seeded by the passphrase.
     */
    public static byte[] embedData(byte[] audio, byte[] data, String password) throws Exception {
        int dataOffset = findDataOffset(audio);
        int availableSamples = audio.length - dataOffset;
        int totalBits = 32 + (data.length * 8);

        if (totalBits > availableSamples) {
            throw new Exception("Data too large to embed in audio. Required: " + totalBits + " bits, Available: " + availableSamples + " samples.");
        }

        byte[] result = new byte[audio.length];
        System.arraycopy(audio, 0, result, 0, audio.length);

        int[] indices = StegoScatteringUtil.generatePermutation(availableSamples, totalBits, password);
        int dataLength = data.length;

        for (int bitIndex = 0; bitIndex < totalBits; bitIndex++) {
            int bit;
            if (bitIndex < 32) {
                bit = (dataLength >> (31 - bitIndex)) & 1;
            } else {
                int bytePos = (bitIndex - 32) / 8;
                int bitPos = (bitIndex - 32) % 8;
                bit = (data[bytePos] >> (7 - bitPos)) & 1;
            }

            int sampleIdx = dataOffset + indices[bitIndex];
            result[sampleIdx] = (byte) ((result[sampleIdx] & 0xFE) | bit);
        }

        return result;
    }

    /**
     * Sequential embedding overload for backward compatibility.
     */
    public static byte[] embedData(byte[] audio, byte[] data) throws Exception {
        return embedData(audio, data, null);
    }

    /**
     * Extracts data using password-based pseudo-random sample scattering.
     */
    public static byte[] extractData(byte[] audio, String password) throws Exception {
        int dataOffset = findDataOffset(audio);
        int availableSamples = audio.length - dataOffset;

        if (availableSamples < 32) {
            throw new Exception("Audio file is too small to contain a steganographic header.");
        }

        // 1. Recover 32-bit length header
        int[] headerIndices = StegoScatteringUtil.generatePermutation(availableSamples, 32, password);
        int dataLength = 0;
        for (int i = 0; i < 32; i++) {
            int sampleIdx = dataOffset + headerIndices[i];
            int bit = audio[sampleIdx] & 1;
            dataLength = (dataLength << 1) | bit;
        }

        if (dataLength < 0 || (32L + (long) dataLength * 8) > availableSamples) {
            throw new Exception("Corrupted carrier or invalid passphrase. Extracted length: " + dataLength);
        }

        // 2. Extract payload bytes using full permutation
        int totalBits = 32 + (dataLength * 8);
        int[] fullIndices = StegoScatteringUtil.generatePermutation(availableSamples, totalBits, password);
        byte[] data = new byte[dataLength];

        for (int i = 0; i < dataLength * 8; i++) {
            int sampleIdx = dataOffset + fullIndices[32 + i];
            int bit = audio[sampleIdx] & 1;
            int bytePos = i / 8;
            data[bytePos] = (byte) ((data[bytePos] << 1) | bit);
        }

        return data;
    }

    /**
     * Sequential extraction overload for backward compatibility.
     */
    public static byte[] extractData(byte[] audio) throws Exception {
        return extractData(audio, null);
    }

    /**
     * Legacy extraction method for backward compatibility.
     */
    public static byte[] extractData(byte[] audio, int dataSize) {
        try {
            return extractData(audio);
        } catch (Exception e) {
            byte[] data = new byte[dataSize];
            int dataIndex = 0;
            int dataOffset = findDataOffset(audio);

            for (int i = dataOffset; i < audio.length; i++) {
                if (dataIndex < dataSize * 8) {
                    int bit = audio[i] & 1;
                    data[dataIndex / 8] = (byte) ((data[dataIndex / 8] << 1) | bit);
                    dataIndex++;
                }
            }
            return data;
        }
    }

    /**
     * Calculates Signal-to-Noise Ratio (SNR) in decibels (dB) between cover audio and stego audio.
     */
    public static double calculateSNR(byte[] originalAudio, byte[] stegoAudio) {
        int offset = findDataOffset(originalAudio);
        int length = Math.min(originalAudio.length, stegoAudio.length);
        double signalPower = 0.0;
        double noisePower = 0.0;

        for (int i = offset; i < length; i++) {
            double orig = originalAudio[i];
            double noise = originalAudio[i] - stegoAudio[i];
            signalPower += orig * orig;
            noisePower += noise * noise;
        }

        if (noisePower == 0.0) {
            return Double.POSITIVE_INFINITY;
        }
        if (signalPower == 0.0) {
            return 0.0;
        }
        return 10.0 * Math.log10(signalPower / noisePower);
    }
}
