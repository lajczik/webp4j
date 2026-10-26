package xyz.lychee.webp4j.utils;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.*;

/**
 * General image manipulation and transformation utilities for image preparation before encoding.
 */
public final class ImageHelper {

    private ImageHelper() {
        throw new AssertionError("Cannot instantiate utility class.");
    }

    /**
     * Creates a BufferedImage backed by a byte array of RGBA interleaved pixels.
     */
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

    /**
     * Resizes a BufferedImage to target dimensions using high-quality bicubic interpolation.
     *
     * @param src          Source image
     * @param targetWidth  Desired width
     * @param targetHeight Desired height
     * @return Resized BufferedImage
     */
    public static BufferedImage resize(BufferedImage src, int targetWidth, int targetHeight) {
        if (src == null) {
            throw new IllegalArgumentException("Source image cannot be null");
        }
        if (targetWidth <= 0 || targetHeight <= 0) {
            throw new IllegalArgumentException("Target dimensions must be positive");
        }

        boolean hasAlpha = hasAlpha(src);
        int type = hasAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage result = new BufferedImage(targetWidth, targetHeight, type);
        Graphics2D g = result.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (hasAlpha) {
                g.setComposite(AlphaComposite.Src);
            }
            g.drawImage(src, 0, 0, targetWidth, targetHeight, null);
        } finally {
            g.dispose();
        }
        return result;
    }

    /**
     * Creates a thumbnail preserving aspect ratio, scaled so that neither dimension exceeds maxDimension.
     *
     * @param src          Source image
     * @param maxDimension Maximum allowed width or height
     * @return Resized thumbnail BufferedImage
     */
    public static BufferedImage createThumbnail(BufferedImage src, int maxDimension) {
        if (src == null) {
            throw new IllegalArgumentException("Source image cannot be null");
        }
        if (maxDimension <= 0) {
            throw new IllegalArgumentException("Max dimension must be positive");
        }

        int origWidth = src.getWidth();
        int origHeight = src.getHeight();
        if (origWidth <= maxDimension && origHeight <= maxDimension) {
            return clone(src);
        }

        double scale = Math.min((double) maxDimension / origWidth, (double) maxDimension / origHeight);
        int targetWidth = Math.max(1, (int) Math.round(origWidth * scale));
        int targetHeight = Math.max(1, (int) Math.round(origHeight * scale));

        return resize(src, targetWidth, targetHeight);
    }

    /**
     * Creates a deep copy of a BufferedImage.
     *
     * @param src Source image
     * @return Cloned BufferedImage
     */
    public static BufferedImage clone(BufferedImage src) {
        if (src == null) {
            return null;
        }
        ColorModel cm = src.getColorModel();
        boolean isAlphaPremultiplied = cm.isAlphaPremultiplied();
        WritableRaster raster = src.copyData(src.getRaster().createCompatibleWritableRaster());
        return new BufferedImage(cm, raster, isAlphaPremultiplied, null);
    }

    /**
     * Checks if a BufferedImage has an active alpha channel.
     *
     * @param image Input image
     * @return True if image contains transparency
     */
    public static boolean hasAlpha(BufferedImage image) {
        if (image == null || image.getColorModel() == null) {
            return false;
        }
        return image.getColorModel().hasAlpha();
    }

    /**
     * Converts a generic {@link java.awt.Image} to a {@link BufferedImage}.
     *
     * @param img Input image
     * @return BufferedImage representation
     */
    public static BufferedImage toBufferedImage(Image img) {
        if (img instanceof BufferedImage) {
            return (BufferedImage) img;
        }
        if (img == null) {
            return null;
        }

        int width = img.getWidth(null);
        int height = img.getHeight(null);
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Image dimensions could not be determined immediately");
        }

        BufferedImage bimage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = bimage.createGraphics();
        try {
            g.drawImage(img, 0, 0, null);
        } finally {
            g.dispose();
        }
        return bimage;
    }
}
