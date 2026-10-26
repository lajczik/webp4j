package xyz.lychee.webp4j.codec;

import xyz.lychee.webp4j.chunks.Chunk;
import xyz.lychee.webp4j.chunks.ChunkFactory;
import xyz.lychee.webp4j.chunks.EXIFChunk;
import xyz.lychee.webp4j.chunks.ICCPChunk;
import xyz.lychee.webp4j.chunks.XMPChunk;
import xyz.lychee.webp4j.model.VP8StatusCode;
import xyz.lychee.webp4j.model.WebPBitstreamFeatures;
import xyz.lychee.webp4j.natives.NativeWebP;
import xyz.lychee.webp4j.utils.PixelConverter;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Decoder for decoding static WebP images and extracting WebP container metadata.
 *
 * @author MrNanko
 */
public final class WebPDecoder {

    private WebPDecoder() {
        throw new AssertionError("Cannot instantiate utility class.");
    }

    /**
     * Decodes a WebP image (stored as a byte array) into an RGB/RGBA BufferedImage.
     *
     * @param webPData Byte array containing WebP image data
     * @return Decoded BufferedImage
     * @throws IOException              If decoding fails or input is invalid
     * @throws IllegalArgumentException If webPData is null or empty
     */
    public static BufferedImage decode(byte[] webPData) throws IOException {
        if (webPData == null || webPData.length == 0) {
            throw new IllegalArgumentException("The input WebP data cannot be null or empty.");
        }

        WebPBitstreamFeatures features = getBitstreamFeatures(webPData);
        int width = features.getWidth();
        int height = features.getHeight();
        boolean hasAlpha = features.isHasAlpha();

        int[] pixels = new int[width * height];
        if (!decodeInto(webPData, pixels, width * 4)) {
            throw new IOException("Failed to decode WebP data.");
        }

        return PixelConverter.wrapPixels(pixels, width, height, hasAlpha);
    }

    /**
     * Decodes a WebP image directly into a pre-allocated ARGB int pixel array.
     *
     * @param webPData     Byte array containing WebP image data
     * @param output       Output int array to receive ARGB pixels
     * @param outputStride Stride in bytes (typically width * 4)
     * @return true if decoding succeeded, false otherwise
     */
    public static boolean decodeInto(byte[] webPData, int[] output, int outputStride) {
        if (webPData == null || output == null) {
            return false;
        }
        return NativeWebP.decodeInto(webPData, output, outputStride);
    }

    /**
     * Retrieves information (dimensions) about a WebP image directly in Java by parsing
     * container chunks without crossing into native C.
     *
     * @param webPData Byte array containing WebP image data
     * @return int array containing width and height of the image [width, height]
     * @throws IOException If there is an error processing the image
     */
    public static int[] getInfo(byte[] webPData) throws IOException {
        if (webPData == null || webPData.length < 12) {
            throw new IllegalArgumentException("WebP data cannot be null or shorter than 12 bytes.");
        }
        try {
            WebPBitstreamFeatures features = WebPBitstreamFeatures.parse(webPData);
            return new int[]{features.getWidth(), features.getHeight()};
        } catch (Exception e) {
            try {
                if (NativeWebP.isAvailable()) {
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
                if (NativeWebP.isAvailable()) {
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
}
