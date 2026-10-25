package xyz.lychee.webp4j.utils;

import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.image.*;

public class ImageHelper {

    public static BufferedImage createImageRGBA(int width, int height, byte[] rgbaData) {
        DataBufferByte buffer = new DataBufferByte(rgbaData, rgbaData.length);

        int[] bandOffsets = {0, 1, 2, 3};

        PixelInterleavedSampleModel sampleModel = new PixelInterleavedSampleModel(
                DataBuffer.TYPE_BYTE, width, height, 4, width * 4, bandOffsets);

        ColorModel colorModel = new ComponentColorModel(
                ColorSpace.getInstance(ColorSpace.CS_sRGB),
                true,
                false,
                Transparency.TRANSLUCENT,
                DataBuffer.TYPE_BYTE);

        WritableRaster raster = Raster.createWritableRaster(sampleModel, buffer, null);

        return new BufferedImage(colorModel, raster, false, null);
    }
}
