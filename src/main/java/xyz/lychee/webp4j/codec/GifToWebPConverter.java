package xyz.lychee.webp4j.codec;

import xyz.lychee.webp4j.model.AnimationInfo;
import xyz.lychee.webp4j.model.GifToWebPConfig;
import xyz.lychee.webp4j.natives.NativeWebP;
import xyz.lychee.webp4j.utils.GifDecoderJava;
import xyz.lychee.webp4j.utils.PixelConverter;
import xyz.lychee.webp4j.utils.Util;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;

/**
 * Converter for transforming GIF images to WebP format.
 * <p>
 * This class provides methods to convert both static and animated GIF images to WebP format.
 * It supports a dual-path approach:
 * <ul>
 *   <li>Primary: Native giflib decoder (fast, complete GIF support)</li>
 *   <li>Fallback: Java ImageIO decoder (slower, but works when native library unavailable)</li>
 * </ul>
 *
 * @author MrNanko
 */
public final class GifToWebPConverter {

    private GifToWebPConverter() {
        throw new AssertionError("Cannot instantiate utility class.");
    }

    /**
     * Gets information about a GIF image without performing full conversion.
     * <p>
     * This is useful for determining whether a GIF is animated, its dimensions,
     * and other properties before deciding how to convert it.
     *
     * @param gifData Byte array containing the GIF image data
     * @return AnimationInfo containing frame count, dimensions, loop count, and transparency info
     * @throws IOException              If reading GIF information fails
     * @throws IllegalArgumentException If gifData is null or empty
     */
    public static AnimationInfo getInfo(byte[] gifData) throws IOException {
        if (gifData == null || gifData.length == 0) {
            throw new IllegalArgumentException("GIF data cannot be null or empty");
        }

        AnimationInfo info = new AnimationInfo();

        // Try native path first
        try {
            boolean success = NativeWebP.getGifInfo(gifData, info);
            if (success) {
                return info;
            }
        } catch (UnsatisfiedLinkError e) {
            // Native library not available, fall back to Java ImageIO
        }

        // If native path returned false or library unavailable, use Java ImageIO fallback
        boolean success = GifDecoderJava.getGifInfo(gifData, info);
        if (!success) {
            throw new IOException("Failed to read GIF information");
        }

        return info;
    }

    public static AnimationInfo getInfo(File file) throws IOException {
        return getInfo(Util.readFileBytes(file));
    }

    public static AnimationInfo getInfo(Path path) throws IOException {
        return getInfo(Util.readFileBytes(path));
    }

    public static AnimationInfo getInfo(InputStream in) throws IOException {
        return getInfo(Util.readAllBytes(in));
    }

    /**
     * Converts a GIF image to WebP format with default lossy settings.
     * <p>
     * This is a convenience method that uses quality factor 75 and lossy compression.
     * For more control over the conversion, use {@link #convert(byte[], GifToWebPConfig)}.
     *
     * @param gifData Byte array containing the GIF image data
     * @return Byte array containing the WebP encoded image
     * @throws IOException If conversion fails
     */
    public static byte[] convert(byte[] gifData) throws IOException {
        return convert(gifData, new GifToWebPConfig());
    }

    /**
     * Converts a GIF image to WebP format.
     * <p>
     * This method automatically detects whether the GIF is static or animated,
     * and handles both cases appropriately.
     *
     * @param gifData Byte array containing the GIF image data
     * @param config  Configuration for conversion (quality, lossless, compression, etc.)
     * @return Byte array containing the WebP encoded image
     * @throws IOException              If conversion fails
     * @throws IllegalArgumentException If gifData or config is null
     */
    public static byte[] convert(byte[] gifData, GifToWebPConfig config) throws IOException {
        if (gifData == null || gifData.length == 0) {
            throw new IllegalArgumentException("GIF data cannot be null or empty");
        }
        if (config == null) {
            throw new IllegalArgumentException("Config cannot be null");
        }

        // Try native path first (giflib + JNI) for best performance
        byte[] result = null;
        try {
            result = NativeWebP.encodeGifToWebP(
                    gifData,
                    config.getQuality(),
                    config.isLossless(),
                    config.getCompressionMethod(),
                    config.isExtractFirstFrameOnly(),
                    config.getLoopCount(),
                    config.getKmin(),
                    config.getKmax(),
                    config.isMinimizeSize(),
                    config.isAllowMixed(),
                    config.isMultiThreaded()
            );

            if (result != null && result.length > 0) {
                return result;  // Success via native path
            }
        } catch (UnsatisfiedLinkError e) {
            // Native library not available, fall back to Java ImageIO
        }

        // If native path returned null or failed to load, try Java ImageIO fallback
        if (result == null) {
            return convertUsingJavaImageIO(gifData, config);
        }

        throw new IOException("Native GIF encoding returned empty result");
    }

    /**
     * Converts a GIF image to lossless WebP format.
     * <p>
     * This is a convenience method that uses lossless compression to preserve
     * the original GIF quality without any loss.
     *
     * @param gifData Byte array containing the GIF image data
     * @return Byte array containing the lossless WebP encoded image
     * @throws IOException If conversion fails
     */
    public static byte[] convertLossless(byte[] gifData) throws IOException {
        return convert(gifData, GifToWebPConfig.createLosslessConfig());
    }

    public static byte[] convert(File gifFile, GifToWebPConfig config) throws IOException {
        return convert(Util.readFileBytes(gifFile), config);
    }

    public static byte[] convert(Path gifPath, GifToWebPConfig config) throws IOException {
        return convert(Util.readFileBytes(gifPath), config);
    }

    public static void convert(File gifFile, File webpFile) throws IOException {
        convert(gifFile, webpFile, new GifToWebPConfig());
    }

    public static void convert(File gifFile, File webpFile, GifToWebPConfig config) throws IOException {
        Util.writeBytes(webpFile, convert(gifFile, config));
    }

    public static void convert(Path gifPath, Path webpPath) throws IOException {
        convert(gifPath, webpPath, new GifToWebPConfig());
    }

    public static void convert(Path gifPath, Path webpPath, GifToWebPConfig config) throws IOException {
        Util.writeBytes(webpPath, convert(gifPath, config));
    }

    public static void convert(InputStream in, OutputStream out, GifToWebPConfig config) throws IOException {
        if (out == null) {
            throw new IllegalArgumentException("OutputStream cannot be null");
        }
        out.write(convert(Util.readAllBytes(in), config));
    }

    /**
     * Fallback implementation: Encodes GIF to WebP using Java ImageIO for GIF decoding
     * and Java-side chunk container assembly.
     *
     * @param gifData Byte array containing the GIF image data
     * @param config  Configuration for conversion
     * @return Byte array containing the WebP encoded image
     * @throws IOException If conversion fails
     */
    public static byte[] convertUsingJavaImageIO(byte[] gifData, GifToWebPConfig config) throws IOException {
        // Decode GIF using Java ImageIO
        GifDecoderJava.GifData gif = GifDecoderJava.decodeGif(gifData);

        if (config.isExtractFirstFrameOnly() || gif.frames.size() == 1) {
            BufferedImage firstFrame = gif.frames.get(0).image;
            return encodeSingleFrame(firstFrame, config.getQuality(), config.isLossless(),
                    config.isMultiThreaded());
        }

        // Animated GIF: assemble WebP chunks directly in Java
        BufferedImage canvas = new BufferedImage(gif.width, gif.height, BufferedImage.TYPE_INT_ARGB);
        int[] canvasPixels = ((DataBufferInt) canvas.getRaster().getDataBuffer()).getData();
        BufferedImage previousFrame = null;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int loopCount = config.getLoopCount() == -1 ? gif.loopCount : config.getLoopCount();

        try (AnimatedWebPEncoder encoder = new AnimatedWebPEncoder(baos, gif.hasTransparency, gif.frames.size(),
                gif.width, gif.height, loopCount, 0)) {
            for (int i = 0; i < gif.frames.size(); i++) {
                previousFrame = composeFrame(gif, i, canvas, previousFrame);
                int duration = gif.frames.get(i).delayMs;
                encoder.setFrame(i, canvasPixels.clone(), 0, 0, gif.width, gif.height,
                        duration, 0, config.getQuality(), config.isLossless());
            }
            encoder.flush();
        }

        return baos.toByteArray();
    }

    /**
     * Encodes a single BufferedImage frame to WebP.
     */
    private static byte[] encodeSingleFrame(BufferedImage image, float quality, boolean lossless,
                                            boolean multiThreaded) throws IOException {
        return WebPEncoder.encode(image, quality, lossless, multiThreaded);
    }

    /**
     * Composes a single GIF frame onto the accumulator canvas based on disposal method.
     */
    private static BufferedImage composeFrame(GifDecoderJava.GifData gif, int frameIndex,
                                              BufferedImage canvas, BufferedImage previousFrame) {
        GifDecoderJava.GifFrame frame = gif.frames.get(frameIndex);

        if (frameIndex > 0) {
            GifDecoderJava.GifFrame prevFrame = gif.frames.get(frameIndex - 1);
            switch (prevFrame.disposeMethod) {
                case 2: // RESTORE_TO_BACKGROUND
                    Graphics2D bgG2d = canvas.createGraphics();
                    try {
                        bgG2d.setComposite(AlphaComposite.Clear);
                        bgG2d.fillRect(
                                prevFrame.leftOffset,
                                prevFrame.topOffset,
                                prevFrame.image.getWidth(),
                                prevFrame.image.getHeight()
                        );
                    } finally {
                        bgG2d.dispose();
                    }
                    break;
                case 3: // RESTORE_PREVIOUS
                    if (previousFrame != null) {
                        Graphics2D restoreG2d = canvas.createGraphics();
                        try {
                            restoreG2d.setComposite(AlphaComposite.Src);
                            restoreG2d.drawImage(previousFrame, 0, 0, null);
                        } finally {
                            restoreG2d.dispose();
                        }
                    }
                    break;
                default:
                    // 0 or 1: DO_NOT_DISPOSE
                    break;
            }
        }

        BufferedImage updatedPreviousFrame = previousFrame;
        if (frameIndex < gif.frames.size() - 1 && frame.disposeMethod == 3) {
            if (updatedPreviousFrame == null) {
                updatedPreviousFrame = new BufferedImage(gif.width, gif.height, BufferedImage.TYPE_INT_ARGB);
            }
            Graphics2D g2d = updatedPreviousFrame.createGraphics();
            try {
                g2d.drawImage(canvas, 0, 0, null);
            } finally {
                g2d.dispose();
            }
        }

        Graphics2D g2d = canvas.createGraphics();
        try {
            g2d.setComposite(AlphaComposite.SrcOver);
            g2d.drawImage(frame.image, frame.leftOffset, frame.topOffset, null);
        } finally {
            g2d.dispose();
        }

        return updatedPreviousFrame;
    }
}
