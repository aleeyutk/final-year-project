import java.awt.image.BufferedImage;

public class ImageSteganography {
	public static BufferedImage embedData(BufferedImage image, byte[] data) throws Exception {
        int width = image.getWidth();
        int height = image.getHeight();
        int maxCapacity = width * height;

        if (data.length * 8 > maxCapacity) {
            throw new Exception("Data too large to embed in image.");
        }

        int dataIndex = 0;
        int dataLength = data.length * 8;

        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                if (dataIndex < dataLength) {
                    int rgb = image.getRGB(j, i);
                    int bit = (data[dataIndex / 8] >> (7 - (dataIndex % 8))) & 1;
                    rgb = (rgb & 0xFFFFFFFE) | bit;
                    image.setRGB(j, i, rgb);
                    dataIndex++;
                }
            }
        }
        return image;
    }

    public static byte[] extractData(BufferedImage image, int dataSize) {
        int width = image.getWidth();
        int height = image.getHeight();
        byte[] data = new byte[dataSize];
        int dataIndex = 0;

        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                if (dataIndex < dataSize * 8) {
                    int rgb = image.getRGB(j, i);
                    int bit = rgb & 1;
                    data[dataIndex / 8] = (byte)((data[dataIndex / 8] << 1) | bit);
                    dataIndex++;
                }
            }
        }
        return data;
    }
}
