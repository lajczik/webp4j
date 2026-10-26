package xyz.lychee.webp4j.codec;

import xyz.lychee.webp4j.chunks.*;
import xyz.lychee.webp4j.natives.NativeWebP;
import xyz.lychee.webp4j.utils.PixelConverter;
import xyz.lychee.webp4j.utils.Util;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Encoder for generating static WebP images.
 *
 * @author MrNanko
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
     */
    public static byte[] encode(BufferedImage image, float quality, boolean lossless) throws IOException {
        return encode(image, quality, lossless, true);
    }

    /**
     * Encodes a BufferedImage to a lossy WebP byte array.
     */
    public static byte[] encode(BufferedImage image, float quality) throws IOException {
        return encode(image, quality, false, true);
    }

    /**
     * Encodes a BufferedImage to a lossless WebP byte array.
     */
    public static byte[] encodeLossless(BufferedImage image) throws IOException {
        return encode(image, 0, true, true);
    }

    /**
     * Encodes a BufferedImage directly to an OutputStream.
     */
    public static void encode(BufferedImage image, float quality, OutputStream out) throws IOException {
        if (out == null) {
            throw new IllegalArgumentException("OutputStream cannot be null.");
        }
        out.write(encode(image, quality, quality >= 100, true));
    }

    /**
     * Encodes a BufferedImage directly to an OutputStream with lossless control.
     */
    public static void encode(BufferedImage image, float quality, boolean lossless, OutputStream out) throws IOException {
        if (out == null) {
            throw new IllegalArgumentException("OutputStream cannot be null.");
        }
        out.write(encode(image, quality, lossless, true));
    }

    /**
     * Encodes a BufferedImage directly to an OutputStream with multi-threaded control.
     */
    public static void encode(BufferedImage image, float quality, boolean lossless, boolean multiThreaded, OutputStream out) throws IOException {
        if (out == null) {
            throw new IllegalArgumentException("OutputStream cannot be null.");
        }
        out.write(encode(image, quality, lossless, multiThreaded));
    }

    /**
     * Encodes a BufferedImage to lossless WebP directly to an OutputStream.
     */
    public static void encodeLossless(BufferedImage image, OutputStream out) throws IOException {
        encode(image, 0, true, true, out);
    }

    /**
     * Encodes a BufferedImage to a File.
     */
    public static void encode(BufferedImage image, File file, float quality) throws IOException {
        Util.writeBytes(file, encode(image, quality));
    }

    /**
     * Encodes a BufferedImage to a File with lossless control.
     */
    public static void encode(BufferedImage image, File file, float quality, boolean lossless) throws IOException {
        Util.writeBytes(file, encode(image, quality, lossless));
    }

    /**
     * Encodes a BufferedImage to a Path.
     */
    public static void encode(BufferedImage image, Path path, float quality) throws IOException {
        Util.writeBytes(path, encode(image, quality));
    }

    /**
     * Encodes a BufferedImage to a Path with lossless control.
     */
    public static void encode(BufferedImage image, Path path, float quality, boolean lossless) throws IOException {
        Util.writeBytes(path, encode(image, quality, lossless));
    }

    /**
     * Encodes a BufferedImage to a lossless WebP File.
     */
    public static void encodeLossless(BufferedImage image, File file) throws IOException {
        Util.writeBytes(file, encodeLossless(image));
    }

    /**
     * Encodes a BufferedImage to a lossless WebP Path.
     */
    public static void encodeLossless(BufferedImage image, Path path) throws IOException {
        Util.writeBytes(path, encodeLossless(image));
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

        byte[] bitstream = encode(image, quality, lossless, true);

        if (exif == null && iccp == null && xmp == null) {
            return bitstream;
        }

        Chunk[] imageChunks = ChunkFactory.getChunks(bitstream);

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

    public static void encodeWithMetadata(BufferedImage image, File file, float quality, boolean lossless,
                                          byte[] exif, byte[] iccp, String xmp) throws IOException {
        Util.writeBytes(file, encodeWithMetadata(image, quality, lossless, exif, iccp, xmp));
    }

    public static void encodeWithMetadata(BufferedImage image, Path path, float quality, boolean lossless,
                                          byte[] exif, byte[] iccp, String xmp) throws IOException {
        Util.writeBytes(path, encodeWithMetadata(image, quality, lossless, exif, iccp, xmp));
    }

    public static void encodeWithMetadata(BufferedImage image, OutputStream out, float quality, boolean lossless,
                                          byte[] exif, byte[] iccp, String xmp) throws IOException {
        if (out == null) {
            throw new IllegalArgumentException("OutputStream cannot be null.");
        }
        out.write(encodeWithMetadata(image, quality, lossless, exif, iccp, xmp));
    }
}
