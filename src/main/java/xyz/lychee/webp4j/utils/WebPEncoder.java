package xyz.lychee.webp4j.utils;

import xyz.lychee.webp4j.chunks.*;
import xyz.lychee.webp4j.natives.NativeWebP;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Encoder for generating static WebP images.
 */
public final class WebPEncoder {

    private WebPEncoder() {
        throw new AssertionError("Cannot instantiate utility class.");
    }

    /**
     * Encodes a BufferedImage to a WebP byte array with control over libwebp's per-image worker threads.
     *
     * @param image         Input BufferedImage (RGB or RGBA)
     * @param quality       Quality factor (0 to 100, ignored if lossless)
     * @param lossless      True for lossless compression, false for lossy
     * @param multiThreaded True to enable multi-threading in libwebp
     * @return WebP encoded byte array
     * @throws IOException              If encoding fails
     * @throws IllegalArgumentException If image is null
     */
    public static byte[] encode(BufferedImage image, float quality, boolean lossless, boolean multiThreaded) throws IOException {
        if (image == null) {
            throw new IllegalArgumentException("The input image cannot be null.");
        }

        if (!lossless && (quality < 0f || quality > 100f)) {
            throw new IllegalArgumentException("Quality must be between 0 and 100, but was " + quality + ".");
        }

        int width = image.getWidth();
        int height = image.getHeight();
        boolean hasAlpha = image.getColorModel().hasAlpha();

        // Single-pass pixel read. If the source raster has an unpackable 3-byte
        // BGR backing array (standard TYPE_3BYTE_BGR or custom RGB with matching
        // pixel layout), skip int[] ARGB conversion entirely and pass the raw
        // BGR bytes to WebPEncodeBGR / WebPEncodeLosslessBGR.
        byte[] encodedWebP;
        byte[] bgrPixels = hasAlpha ? null : PixelConverter.bgrPixelsOrNull(image);
        if (bgrPixels != null) {
            encodedWebP = NativeWebP.encodeBgr(bgrPixels, width, height, quality, lossless, multiThreaded);
        } else {
            int[] pixels = PixelConverter.toArgbPixels(image, hasAlpha);
            encodedWebP = NativeWebP.encode(pixels, width, height, quality, lossless, hasAlpha, multiThreaded);
        }

        if (encodedWebP == null || encodedWebP.length == 0) {
            String encodingType = lossless ? "Lossless" : "Lossy";
            throw new IOException(encodingType + " WebP encoding failed.");
        }

        return encodedWebP;
    }

    /**
     * Encodes a BufferedImage to a WebP byte array (multi-threaded by default).
     *
     * @param image    Input BufferedImage
     * @param quality  Quality factor (0 to 100, ignored if lossless)
     * @param lossless True for lossless compression, false for lossy
     * @return WebP encoded byte array
     * @throws IOException If encoding fails
     */
    public static byte[] encode(BufferedImage image, float quality, boolean lossless) throws IOException {
        return encode(image, quality, lossless, true);
    }

    /**
     * Encodes a BufferedImage to a lossy WebP byte array.
     *
     * @param image   Input BufferedImage
     * @param quality Quality factor (0 to 100)
     * @return WebP encoded byte array
     * @throws IOException If encoding fails
     */
    public static byte[] encode(BufferedImage image, float quality) throws IOException {
        return encode(image, quality, false, true);
    }

    /**
     * Encodes a BufferedImage to a lossless WebP byte array.
     *
     * @param image Input BufferedImage
     * @return WebP encoded byte array
     * @throws IOException If encoding fails
     */
    public static byte[] encodeLossless(BufferedImage image) throws IOException {
        return encode(image, 0, true, true);
    }

    /**
     * Encodes a BufferedImage directly to an OutputStream.
     *
     * @param image   Input BufferedImage
     * @param quality Quality factor (0 to 100)
     * @param out     Destination OutputStream
     * @throws IOException If encoding or writing fails
     */
    public static void encode(BufferedImage image, float quality, OutputStream out) throws IOException {
        if (out == null) {
            throw new IllegalArgumentException("OutputStream cannot be null.");
        }
        byte[] byteBuf = encode(image, quality, quality >= 100, true);
        out.write(byteBuf);
    }

    /**
     * Encodes a static image and constructs the WebP container in Java, embedding optional metadata
     * (VP8X + metadata chunks + VP8/VP8L bitstream).
     *
     * @param image    Input image (RGB or RGBA)
     * @param quality  Encoding quality (0-100)
     * @param lossless Lossless flag
     * @param exif     Optional EXIF metadata bytes (or null)
     * @param iccp     Optional ICC profile bytes (or null)
     * @param xmp      Optional XMP XML string (or null)
     * @return Complete WebP file bytes assembled in Java
     * @throws IOException If encoding fails
     */
    public static byte[] encodeWithMetadata(BufferedImage image, float quality, boolean lossless,
                                            byte[] exif, byte[] iccp, String xmp) throws IOException {
        if (image == null) {
            throw new IllegalArgumentException("Image cannot be null.");
        }
        int width = image.getWidth();
        int height = image.getHeight();
        boolean hasAlpha = image.getColorModel().hasAlpha();

        // Encode raw pixels to WebP bitstream
        byte[] bitstream = encode(image, quality, lossless, true);

        // If no metadata requested and format is simple, return standard WebP directly
        if (exif == null && iccp == null && xmp == null) {
            return bitstream;
        }

        // Extract image bitstream chunk(s)
        Chunk[] imageChunks = ChunkFactory.getChunks(bitstream);

        // Build container in Java
        int flags = (hasAlpha ? VP8XChunk.FLAG_ALPH : 0)
                | (iccp != null ? VP8XChunk.FLAG_ICCP : 0)
                | (exif != null ? VP8XChunk.FLAG_EXIF : 0)
                | (xmp != null ? VP8XChunk.FLAG_XMP : 0);

        List<Chunk> containerChunks = new ArrayList<>();
        containerChunks.add(new VP8XChunk(flags, width, height));

        if (iccp != null && iccp.length > 0) {
            containerChunks.add(new ICCPChunk(iccp));
        }

        containerChunks.addAll(Arrays.asList(imageChunks));

        if (exif != null && exif.length > 0) {
            containerChunks.add(new EXIFChunk(exif));
        }

        if (xmp != null && !xmp.isEmpty()) {
            containerChunks.add(new XMPChunk(xmp.getBytes(StandardCharsets.UTF_8)));
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        WebPChunk webpChunk = new WebPChunk(containerChunks.toArray(new Chunk[0]));
        webpChunk.write(baos);
        return baos.toByteArray();
    }
}
