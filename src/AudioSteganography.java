public class AudioSteganography {
	public static byte[] embedData(byte[] audio, byte[] data) throws Exception {
        int maxCapacity = audio.length;

        if (data.length * 8 > maxCapacity) {
            throw new Exception("Data too large to embed in audio.");
        }

        byte[] result = new byte[audio.length];
        System.arraycopy(audio, 0, result, 0, audio.length);
        int dataIndex = 0;
        int dataLength = data.length * 8;

        for (int i = 0; i < audio.length; i++) {
            if (dataIndex < dataLength) {
                int bit = (data[dataIndex / 8] >> (7 - (dataIndex % 8))) & 1;
                result[i] = (byte)((audio[i] & 0xFE) | bit);
                dataIndex++;
            }
        }
        return result;
    }

    public static byte[] extractData(byte[] audio, int dataSize) {
        byte[] data = new byte[dataSize];
        int dataIndex = 0;

        for (int i = 0; i < audio.length; i++) {
            if (dataIndex < dataSize * 8) {
                int bit = audio[i] & 1;
                data[dataIndex / 8] = (byte)((data[dataIndex / 8] << 1) | bit);
                dataIndex++;
            }
        }
        return data;
    }
}
