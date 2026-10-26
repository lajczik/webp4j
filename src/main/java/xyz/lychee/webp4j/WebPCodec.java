package xyz.lychee.webp4j;

import xyz.lychee.webp4j.animation.AnimatedWebPDecoder;
import xyz.lychee.webp4j.animation.AnimatedWebPEncoder;
import xyz.lychee.webp4j.batch.BatchProcessor;
import xyz.lychee.webp4j.chunks.*;
import xyz.lychee.webp4j.gif.GifToWebPConfig;
import xyz.lychee.webp4j.gif.GifToWebPConverter;
import xyz.lychee.webp4j.natives.NativeWebP;
import xyz.lychee.webp4j.utils.PixelConverter;
import xyz.lychee.webp4j.model.AnimatedWebPData;
import xyz.lychee.webp4j.model.AnimationInfo;
import xyz.lychee.webp4j.model.VP8StatusCode;
import xyz.lychee.webp4j.model.WebPBitstreamFeatures;
import xyz.lychee.webp4j.utils.WebPEncoder;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Main facade class for WebP encoding and decoding operations.
 * <p>
 * This class provides a unified API for all WebP-related operations including:
 * <ul>
 *   <li>Static image encoding/decoding</li>
 *   <li>GIF to WebP conversion</li>
 *   <li>Animated WebP creation</li>
 *   <li>WebP container and RIFF chunk inspection</li>
 * </ul>
 *
 * @author MrNanko
 * @since 1.4.0
 */
public final class WebPCodec {

    private WebPCodec() {
        throw new AssertionError("Cannot instantiate utility class.");
    }

    // ============================================
    // Platform Availability
    // ============================================

    /**
     * Checks if WebP support is available on the current platform.
     * <p>
     * This method verifies that the native WebP library has been successfully
     * loaded for the current operating system and architecture. It provides
     * a lightweight way to check platform support without attempting to
     * encode or decode an image.
     * <p>
     * Example usage:
     * <pre>
     * if (WebPCodec.isAvailable()) {
     *     // Show WebP export option in UI
     *     byte[] webpData = WebPCodec.encodeImage(image, 75);
     * } else {
     *     // Hide WebP option or show unsupported message
     * }
     * </pre>
     *
     * @return true if WebP operations are supported on this platform, false otherwise
     */
    public static boolean isAvailable() {
        try {
            return NativeWebP.isAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Returns the version of the bundled libwebp native library as a
     * {@code "major.minor.patch"} string (e.g. {@code "1.6.0"}).
     * <p>
     * The version is captured once when the native library loads, so this is a
     * cheap field read with no native call.
     *
     * @return the libwebp version, or null if WebP support is unavailable on
     *         this platform (see {@link #isAvailable()})
     */
    public static String libWebPVersion() {
        try {
            return NativeWebP.libWebPVersion();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Indicates whether the Foreign Function &amp; Memory (FFM) API backend is active.
     * On Java 22+, FFM is preferred and used if available. On Java &lt; 22, JNI is used.
     *
     * @return true if running under Java 22+ FFM backend, false if using JNI
     */
    public static boolean isFFM() {
        try {
            return NativeWebP.isFFM();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Returns the name of the active native interop backend ("FFM" or "JNI").
     *
     * @return "FFM" on Java 22+ when FFM is active, "JNI" otherwise
     */
    public static String getBackendName() {
        try {
            return NativeWebP.getBackendName();
        } catch (Throwable t) {
            return "JNI";
        }
    }

    // ============================================
    // Static Image Encoding/Decoding & Inspection
    // ============================================

    /**
     * Retrieves information (dimensions) about a WebP image directly in Java by parsing
     * container chunks without crossing into native C.
     *
     * @param webPData Byte array containing WebP image data
     * @return int array containing width and height of the image [width, height]
     * @throws IOException If there is an error processing the image
     */
    public static int[] getWebPInfo(byte[] webPData) throws IOException {
        if (webPData == null || webPData.length < 12) {
            throw new IllegalArgumentException("WebP data cannot be null or shorter than 12 bytes.");
        }
        try {
            WebPBitstreamFeatures features = WebPBitstreamFeatures.parse(webPData);
            return new int[]{features.getWidth(), features.getHeight()};
        } catch (Exception e) {
            try {
                if (isAvailable()) {
                    int[] dimensions = new int[2];
                    if (NativeWebP.getInfo(webPData, dimensions)) {
                        return dimensions;
                    }
                }
            } catch (Throwable ignored) {
            }
            throw new IOException("Failed to retrieve WebP image information: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves bitstream features (dimensions, format, alpha, animation) of a WebP image.
     * Primary parsing is executed directly in Java from RIFF container chunks with zero JNI boundary overhead.
     *
     * @param webPData Byte array containing WebP image data
     * @return WebPBitstreamFeatures object containing image metadata
     * @throws IOException If the data is invalid or cannot be parsed
     */
    public static WebPBitstreamFeatures getBitstreamFeatures(byte[] webPData) throws IOException {
        if (webPData == null || webPData.length == 0) {
            throw new IllegalArgumentException("WebP data cannot be null or empty.");
        }
        try {
            return WebPBitstreamFeatures.parse(webPData);
        } catch (Exception e) {
            try {
                if (isAvailable()) {
                    WebPBitstreamFeatures features = new WebPBitstreamFeatures();
                    int status = NativeWebP.getFeatures(webPData, webPData.length, features);
                    if (VP8StatusCode.getStatusCode(status) == VP8StatusCode.VP8_STATUS_OK) {
                        return features;
                    }
                }
            } catch (Throwable ignored) {
            }
            throw new IOException("Failed to retrieve WebP bitstream features: " + e.getMessage(), e);
        }
    }

    /**
     * Parses and returns all top-level RIFF chunks from a WebP image container.
     *
     * @param webPData Byte array containing WebP image data
     * @return List of parsed Chunk objects
     * @throws IOException If the container format is invalid
     */
    public static List<Chunk> getChunks(byte[] webPData) throws IOException {
        if (webPData == null || webPData.length == 0) {
            throw new IllegalArgumentException("WebP data cannot be null or empty.");
        }
        return ChunkFactory.parseAll(webPData);
    }

    /**
     * Checks if a WebP file is animated using fast container chunk inspection,
     * without invoking native code or decoding pixels.
     *
     * @param webPData Byte array containing WebP image data
     * @return true if the image contains animation chunks or flags
     */
    public static boolean isAnimated(byte[] webPData) {
        if (webPData == null || webPData.length < 16) {
            return false;
        }
        try {
            WebPBitstreamFeatures features = WebPBitstreamFeatures.parse(webPData);
            return features.isHasAnimation();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Extracts raw EXIF metadata bytes from a WebP image if present.
     *
     * @param webPData WebP image data
     * @return Raw EXIF payload, or null if not present
     */
    public static byte[] getExifMetadata(byte[] webPData) {
        try {
            for (Chunk c : getChunks(webPData)) {
                if (c instanceof EXIFChunk) {
                    return ((EXIFChunk) c).getRawData();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Extracts raw ICC color profile bytes from a WebP image if present.
     *
     * @param webPData WebP image data
     * @return Raw ICC profile payload, or null if not present
     */
    public static byte[] getIccProfile(byte[] webPData) {
        try {
            for (Chunk c : getChunks(webPData)) {
                if (c instanceof ICCPChunk) {
                    return ((ICCPChunk) c).getRawData();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Extracts XMP metadata XML string from a WebP image if present.
     *
     * @param webPData WebP image data
     * @return XMP XML string, or null if not present
     */
    public static String getXmpMetadata(byte[] webPData) {
        try {
            for (Chunk c : getChunks(webPData)) {
                if (c instanceof XMPChunk) {
                    byte[] data = ((XMPChunk) c).getRawData();
                    return new String(data, StandardCharsets.UTF_8);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Encodes a static image with optional metadata directly into a complete WebP container
     * assembled on the Java side.
     *
     * @param image    Input BufferedImage
     * @param quality  Quality parameter (0-100)
     * @param lossless True for lossless, false for lossy
     * @param exif     Optional EXIF byte array (or null)
     * @param iccp     Optional ICC profile byte array (or null)
     * @param xmp      Optional XMP XML string (or null)
     * @return Byte array containing the complete WebP file
     * @throws IOException If encoding fails
     */
    public static byte[] encodeImageWithMetadata(BufferedImage image, float quality, boolean lossless,
                                                 byte[] exif, byte[] iccp, String xmp) throws IOException {
        return WebPEncoder.encodeWithMetadata(image, quality, lossless, exif, iccp, xmp);
    }

    /**
     * Encodes an RGB/RGBA BufferedImage to a WebP encoded byte array.
     *
     * @param bufferedImage The input BufferedImage in RGB/RGBA format.
     * @param quality       The WebP quality parameter (0-100). Ignored when lossless is true.
     * @param lossless      True for lossless encoding, false for lossy encoding.
     * @return A byte array containing the WebP encoded data.
     * @throws IOException              If an error occurs during image conversion or encoding.
     * @throws IllegalArgumentException If bufferedImage is null.
     */
    public static byte[] encodeImage(BufferedImage bufferedImage, float quality, boolean lossless) throws IOException {
        return encodeImage(bufferedImage, quality, lossless, true);
    }

    /**
     * Encodes an RGB/RGBA BufferedImage to a WebP encoded byte array, with
     * explicit control over libwebp's multi-threaded encoding.
     *
     * @param bufferedImage The input BufferedImage in RGB/RGBA format.
     * @param quality       The WebP quality parameter (0-100). Ignored when lossless is true.
     * @param lossless      True for lossless encoding, false for lossy encoding.
     * @param multiThreaded True to let libwebp multi-thread this encode.
     * @return A byte array containing the WebP encoded data.
     * @throws IOException              If an error occurs during image conversion or encoding.
     * @throws IllegalArgumentException If bufferedImage is null, or quality is outside 0-100 for lossy encoding.
     */
    public static byte[] encodeImage(BufferedImage bufferedImage, float quality, boolean lossless,
                                     boolean multiThreaded) throws IOException {
        if (bufferedImage == null) {
            throw new IllegalArgumentException("The input BufferedImage cannot be null.");
        }

        if (!lossless && (quality < 0f || quality > 100f)) {
            throw new IllegalArgumentException("Quality must be between 0 and 100, but was " + quality + ".");
        }

        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();
        boolean hasAlpha = bufferedImage.getColorModel().hasAlpha();

        byte[] encodedWebP;
        byte[] bgrPixels = hasAlpha ? null : PixelConverter.bgrPixelsOrNull(bufferedImage);
        if (bgrPixels != null) {
            encodedWebP = NativeWebP.encodeBgr(bgrPixels, width, height, quality, lossless, multiThreaded);
        } else {
            int[] pixels = PixelConverter.toArgbPixels(bufferedImage, hasAlpha);
            encodedWebP = NativeWebP.encode(pixels, width, height, quality, lossless, hasAlpha, multiThreaded);
        }
        if (encodedWebP == null || encodedWebP.length == 0) {
            String encodingType = lossless ? "Lossless" : "Lossy";
            throw new IOException(encodingType + " WebP encoding failed.");
        }

        return encodedWebP;
    }

    /**
     * Encodes an RGB/RGBA BufferedImage to a lossy WebP encoded byte array.
     */
    public static byte[] encodeImage(BufferedImage bufferedImage, float quality) throws IOException {
        return encodeImage(bufferedImage, quality, false);
    }

    /**
     * Encodes an RGB/RGBA BufferedImage to a lossless WebP encoded byte array.
     */
    public static byte[] encodeLosslessImage(BufferedImage bufferedImage) throws IOException {
        return encodeImage(bufferedImage, 0, true);
    }

    /**
     * Decodes a WebP image (stored as a byte array) into an RGB/RGBA BufferedImage.
     */
    public static BufferedImage decodeImage(byte[] webPData) throws IOException {
        if (webPData == null || webPData.length == 0) {
            throw new IllegalArgumentException("The input WebP data cannot be null or empty.");
        }

        WebPBitstreamFeatures features = getBitstreamFeatures(webPData);
        int width = features.getWidth();
        int height = features.getHeight();
        boolean hasAlpha = features.isHasAlpha();

        int[] pixels = new int[width * height];
        if (!NativeWebP.decodeInto(webPData, pixels, width * 4)) {
            throw new IOException("Failed to decode WebP data.");
        }

        return PixelConverter.wrapPixels(pixels, width, height, hasAlpha);
    }

    // ============================================
    // Batch Processing (Delegate to BatchProcessor)
    // ============================================

    public static List<byte[]> encodeImages(List<BufferedImage> images, float quality, boolean lossless) throws IOException {
        return BatchProcessor.encodeImages(images, quality, lossless);
    }

    public static List<BufferedImage> decodeImages(List<byte[]> encodedImages) throws IOException {
        return BatchProcessor.decodeImages(encodedImages);
    }

    // ============================================
    // GIF to WebP Conversion (Delegate to GifToWebPConverter)
    // ============================================

    public static AnimationInfo getGifInfo(byte[] gifData) throws IOException {
        return GifToWebPConverter.getInfo(gifData);
    }

    public static byte[] encodeGifToWebP(byte[] gifData) throws IOException {
        return GifToWebPConverter.convert(gifData);
    }

    public static byte[] encodeGifToWebP(byte[] gifData, GifToWebPConfig config) throws IOException {
        return GifToWebPConverter.convert(gifData, config);
    }

    public static byte[] encodeGifToWebPLossless(byte[] gifData) throws IOException {
        return GifToWebPConverter.convertLossless(gifData);
    }

    // ============================================
    // Animated WebP Creation (Delegate to AnimatedWebPEncoder)
    // ============================================

    public static byte[] createAnimatedWebP(List<BufferedImage> frames, int[] delays, GifToWebPConfig config) throws IOException {
        return AnimatedWebPEncoder.encode(frames, delays, config);
    }

    /**
     * Streams an animated WebP directly to an OutputStream using chunk assembly in Java.
     *
     * @param out    Destination output stream
     * @param frames List of frames to encode
     * @param delays Delays in milliseconds for each frame
     * @param config Conversion configuration
     * @throws IOException If encoding fails
     */
    public static void createAnimatedWebP(OutputStream out, List<BufferedImage> frames, int[] delays, GifToWebPConfig config) throws IOException {
        AnimatedWebPEncoder.encode(out, frames, delays, config);
    }

    // ============================================
    // Animated WebP Decoding & Metadata (Delegate to AnimatedWebPDecoder)
    // ============================================

    /**
     * Retrieves animation metadata (dimensions, loop count, frame count, transparency)
     * using container chunk inspection without decoding any frame pixels.
     *
     * @param webPData Byte array containing the animated WebP image data
     * @return AnimationInfo populated from container chunks
     * @throws IOException If parsing fails
     */
    public static AnimationInfo getAnimatedWebPInfo(byte[] webPData) throws IOException {
        return AnimatedWebPDecoder.getAnimationInfo(webPData);
    }

    public static AnimatedWebPData decodeAnimatedWebP(byte[] webPData) throws IOException {
        return AnimatedWebPDecoder.decode(webPData);
    }

}
