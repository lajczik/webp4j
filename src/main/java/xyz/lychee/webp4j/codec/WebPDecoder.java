package xyz.lychee.webp4j.codec;

import xyz.lychee.webp4j.chunks.Chunk;
import xyz.lychee.webp4j.chunks.ChunkFactory;
import xyz.lychee.webp4j.model.VP8StatusCode;
import xyz.lychee.webp4j.model.WebPBitstreamFeatures;
import xyz.lychee.webp4j.model.WebPContainerFlag;
import xyz.lychee.webp4j.natives.NativeWebP;
import xyz.lychee.webp4j.utils.ImageHelper;
import xyz.lychee.webp4j.utils.PixelConverter;
import xyz.lychee.webp4j.utils.RiffScanner;
import xyz.lychee.webp4j.utils.Util;
import xyz.lychee.webp4j.utils.WebPLimits;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;

/**
 * Decoder for decoding static WebP images and extracting WebP container metadata.
 *
 * @author lajczik
 */
public final class WebPDecoder {

    /** Number of leading file bytes that is always sufficient to read dimensions, format and flags. */
    private static final int HEADER_PROBE_BYTES = 64;

    private WebPDecoder() {
        throw new AssertionError("Cannot instantiate utility class.");
    }

    /**
     * Decodes a WebP image (stored as a byte array) into an RGB/RGBA BufferedImage.
     * <p>
     * The declared image size is checked against {@link WebPLimits} before the pixel buffer is allocated.
     *
     * @param webPData Byte array containing WebP image data
     * @return Decoded BufferedImage
     * @throws IOException              If decoding fails, input is invalid or exceeds the configured limits
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
        WebPLimits.checkImage(width, height);

        int[] pixels = new int[width * height];
        if (!decodeInto(webPData, pixels, width * 4)) {
            throw new IOException("Failed to decode WebP data.");
        }

        return PixelConverter.wrapPixels(pixels, width, height, hasAlpha);
    }

    /**
     * Decodes a WebP image from a File.
     */
    public static BufferedImage decode(File file) throws IOException {
        return decode(Util.readFileBytes(file));
    }

    /**
     * Decodes a WebP image from a Path.
     */
    public static BufferedImage decode(Path path) throws IOException {
        return decode(Util.readFileBytes(path));
    }

    /**
     * Decodes a WebP image from an InputStream.
     */
    public static BufferedImage decode(InputStream in) throws IOException {
        return decode(Util.readAllBytes(in));
    }

    /**
     * Decodes a WebP image from a ByteBuffer.
     */
    public static BufferedImage decode(ByteBuffer buffer) throws IOException {
        return decode(Util.toByteArray(buffer));
    }

    /**
     * Decodes the first frame of a WebP file, whether it is a static WebP or an animated WebP.
     * Ideal for generating thumbnails or preview rasters. For animations only the first frame is decoded.
     *
     * @param webPData WebP image data
     * @return First frame as a BufferedImage
     * @throws IOException If decoding fails
     */
    public static BufferedImage decodeFirstFrame(byte[] webPData) throws IOException {
        if (isAnimated(webPData)) {
            return AnimatedWebPDecoder.decodeFirstFrame(webPData).getImage();
        }
        return decode(webPData);
    }

    /**
     * Decodes the first frame of a WebP file from a File.
     */
    public static BufferedImage decodeFirstFrame(File file) throws IOException {
        return decodeFirstFrame(Util.readFileBytes(file));
    }

    /**
     * Decodes the first frame of a WebP file from a Path.
     */
    public static BufferedImage decodeFirstFrame(Path path) throws IOException {
        return decodeFirstFrame(Util.readFileBytes(path));
    }

    /**
     * Decodes the first frame of a WebP file from an InputStream.
     */
    public static BufferedImage decodeFirstFrame(InputStream in) throws IOException {
        return decodeFirstFrame(Util.readAllBytes(in));
    }

    /**
     * Decodes the first frame of a WebP file from a ByteBuffer.
     */
    public static BufferedImage decodeFirstFrame(ByteBuffer buffer) throws IOException {
        return decodeFirstFrame(Util.toByteArray(buffer));
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
        } catch (IOException | RuntimeException e) {
            if (NativeWebP.isAvailable()) {
                try {
                    int[] dimensions = new int[2];
                    if (NativeWebP.getInfo(webPData, dimensions)) {
                        return dimensions;
                    }
                } catch (LinkageError | RuntimeException nativeFailure) {
                    e.addSuppressed(nativeFailure);
                }
            }
            throw new IOException("Failed to retrieve WebP image information: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves the dimensions of a WebP file, reading only its first bytes when possible.
     */
    public static int[] getInfo(File file) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("File cannot be null");
        }
        return getInfo(file.toPath());
    }

    /**
     * Retrieves the dimensions of a WebP file, reading only its first bytes when possible.
     */
    public static int[] getInfo(Path path) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("Path cannot be null");
        }
        byte[] header = readHeader(path);
        if (header.length >= 12) {
            try {
                WebPBitstreamFeatures f = WebPBitstreamFeatures.parse(header);
                return new int[]{f.getWidth(), f.getHeight()};
            } catch (IOException | RuntimeException probeFailed) {
                // fall through to the full parse (non-standard layout)
            }
        }
        return getInfo(Util.readFileBytes(path));
    }

    public static int[] getInfo(InputStream in) throws IOException {
        return getInfo(Util.readAllBytes(in));
    }

    public static int[] getInfo(ByteBuffer buffer) throws IOException {
        return getInfo(Util.toByteArray(buffer));
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
        } catch (IOException | RuntimeException e) {
            if (NativeWebP.isAvailable()) {
                try {
                    WebPBitstreamFeatures features = new WebPBitstreamFeatures();
                    int status = NativeWebP.getFeatures(webPData, webPData.length, features);
                    if (VP8StatusCode.getStatusCode(status) == VP8StatusCode.VP8_STATUS_OK) {
                        return features;
                    }
                } catch (LinkageError | RuntimeException nativeFailure) {
                    e.addSuppressed(nativeFailure);
                }
            }
            throw new IOException("Failed to retrieve WebP bitstream features: " + e.getMessage(), e);
        }
    }

    public static WebPBitstreamFeatures getBitstreamFeatures(File file) throws IOException {
        return getBitstreamFeatures(file.toPath());
    }

    /**
     * Retrieves bitstream features of a WebP file, reading only its first bytes when possible.
     */
    public static WebPBitstreamFeatures getBitstreamFeatures(Path path) throws IOException {
        byte[] header = readHeader(path);
        if (header.length >= 12) {
            try {
                WebPBitstreamFeatures f = WebPBitstreamFeatures.parse(header);
                // The probe is conclusive only when the bitstream chunk header was reached (format known)
                // or the image is an animation (everything needed is in VP8X).
                if (f.getFormat() != 0 || f.isHasAnimation()) {
                    return f;
                }
            } catch (IOException | RuntimeException probeFailed) {
                // fall through to the full parse
            }
        }
        return getBitstreamFeatures(Util.readFileBytes(path));
    }

    public static WebPBitstreamFeatures getBitstreamFeatures(InputStream in) throws IOException {
        return getBitstreamFeatures(Util.readAllBytes(in));
    }

    public static WebPBitstreamFeatures getBitstreamFeatures(ByteBuffer buffer) throws IOException {
        return getBitstreamFeatures(Util.toByteArray(buffer));
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

    public static List<Chunk> getChunks(File file) throws IOException {
        return getChunks(Util.readFileBytes(file));
    }

    public static List<Chunk> getChunks(Path path) throws IOException {
        return getChunks(Util.readFileBytes(path));
    }

    public static List<Chunk> getChunks(InputStream in) throws IOException {
        return getChunks(Util.readAllBytes(in));
    }

    /**
     * Decodes a WebP image directly into a pre-allocated BufferedImage canvas.
     * Dimensions of the destination image must match the WebP image dimensions.
     * <p>
     * For {@code TYPE_INT_ARGB} and {@code TYPE_INT_RGB} destinations the pixels are decoded straight
     * into the image's backing array; every other image type is filled from a scratch buffer, so the
     * destination always receives the decoded pixels.
     *
     * @param webPData    WebP image data
     * @param destination Pre-allocated BufferedImage
     * @throws IOException              If decoding fails or dimensions do not match
     * @throws IllegalArgumentException If either argument is null
     */
    public static void decode(byte[] webPData, BufferedImage destination) throws IOException {
        if (webPData == null || destination == null) {
            throw new IllegalArgumentException("Inputs cannot be null.");
        }
        int[] dims = getInfo(webPData);
        if (destination.getWidth() != dims[0] || destination.getHeight() != dims[1]) {
            throw new IllegalArgumentException(String.format(
                    "Destination dimensions (%dx%d) do not match WebP dimensions (%dx%d)",
                    destination.getWidth(), destination.getHeight(), dims[0], dims[1]));
        }
        WebPLimits.checkImage(dims[0], dims[1]);

        int width = dims[0];
        int height = dims[1];
        int[] live = PixelConverter.liveBackingOrNull(destination);
        int[] pixels = live != null ? live : new int[width * height];
        if (!decodeInto(webPData, pixels, width * 4)) {
            throw new IOException("Failed to decode WebP data into destination image.");
        }
        if (live == null) {
            // The destination has a layout we cannot decode into directly: copy through the raster API.
            destination.setRGB(0, 0, width, height, pixels, 0, width);
        }
    }

    /**
     * Decodes and scales a WebP image into a thumbnail preserving aspect ratio,
     * scaled so that neither dimension exceeds maxDimension.
     *
     * @param webPData     WebP image data
     * @param maxDimension Maximum allowed width or height
     * @return Thumbnail BufferedImage
     * @throws IOException If decoding fails
     */
    public static BufferedImage createThumbnail(byte[] webPData, int maxDimension) throws IOException {
        BufferedImage img = decodeFirstFrame(webPData);
        return ImageHelper.createThumbnail(img, maxDimension);
    }

    public static BufferedImage createThumbnail(File file, int maxDimension) throws IOException {
        return createThumbnail(Util.readFileBytes(file), maxDimension);
    }

    public static BufferedImage createThumbnail(Path path, int maxDimension) throws IOException {
        return createThumbnail(Util.readFileBytes(path), maxDimension);
    }

    /**
     * Checks if a WebP file is animated using fast container chunk inspection,
     * without invoking native code or decoding pixels.
     *
     * @param webPData Byte array containing WebP image data
     * @return true if the image contains animation chunks or flags
     */
    public static boolean isAnimated(byte[] webPData) {
        if (webPData == null || webPData.length < 21) {
            return false;
        }
        if (webPData[0] != 'R' || webPData[1] != 'I' || webPData[2] != 'F' || webPData[3] != 'F'
                || webPData[8] != 'W' || webPData[9] != 'E' || webPData[10] != 'B' || webPData[11] != 'P') {
            return false;
        }
        if (webPData[12] == 'V' && webPData[13] == 'P' && webPData[14] == '8' && webPData[15] == 'X') {
            return WebPContainerFlag.ANIMATION.isSet(webPData[20] & 0xFF);
        }
        return false;
    }

    public static boolean isAnimated(File file) {
        return file != null && isAnimated(file.toPath());
    }

    public static boolean isAnimated(Path path) {
        if (path == null) {
            return false;
        }
        try {
            return isAnimated(readHeader(path));
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean isAnimated(InputStream in) {
        try {
            return isAnimated(Util.readAllBytes(in));
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isAnimated(ByteBuffer buffer) {
        try {
            return isAnimated(Util.toByteArray(buffer));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks if a WebP image contains transparency / alpha channel.
     *
     * @param webPData Byte array containing WebP image data
     * @return true if image bitstream contains an alpha channel
     */
    public static boolean hasAlpha(byte[] webPData) {
        if (webPData == null || webPData.length < 16) {
            return false;
        }
        try {
            return getBitstreamFeatures(webPData).isHasAlpha();
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean hasAlpha(File file) {
        return file != null && hasAlpha(file.toPath());
    }

    public static boolean hasAlpha(Path path) {
        try {
            byte[] header = readHeader(path);
            if (header.length >= 12) {
                try {
                    // The VP8X flag / VP8L alpha bit live in the first bytes; a positive answer needs no more.
                    if (WebPBitstreamFeatures.parse(header).isHasAlpha()) {
                        return true;
                    }
                } catch (IOException | RuntimeException probeFailed) {
                    // fall through to the full check
                }
            }
            return getBitstreamFeatures(path).isHasAlpha();
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean hasAlpha(InputStream in) {
        try {
            return hasAlpha(Util.readAllBytes(in));
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean hasAlpha(ByteBuffer buffer) {
        try {
            return hasAlpha(Util.toByteArray(buffer));
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
        return findChunkPayload(webPData, RiffScanner.EXIF);
    }

    public static byte[] getExifMetadata(File file) {
        return file == null ? null : findChunkPayload(file.toPath(), RiffScanner.EXIF);
    }

    /**
     * Extracts raw ICC color profile bytes from a WebP image if present.
     *
     * @param webPData WebP image data
     * @return Raw ICC profile payload, or null if not present
     */
    public static byte[] getIccProfile(byte[] webPData) {
        return findChunkPayload(webPData, RiffScanner.ICCP);
    }

    public static byte[] getIccProfile(File file) {
        return file == null ? null : findChunkPayload(file.toPath(), RiffScanner.ICCP);
    }

    /**
     * Extracts XMP metadata XML string from a WebP image if present.
     *
     * @param webPData WebP image data
     * @return XMP XML string, or null if not present
     */
    public static String getXmpMetadata(byte[] webPData) {
        byte[] data = findChunkPayload(webPData, RiffScanner.XMP);
        return data == null ? null : new String(data, StandardCharsets.UTF_8);
    }

    public static String getXmpMetadata(File file) {
        byte[] data = file == null ? null : findChunkPayload(file.toPath(), RiffScanner.XMP);
        return data == null ? null : new String(data, StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /** Scans the container and copies only the payload of the first chunk with the given FourCC. */
    private static byte[] findChunkPayload(byte[] webPData, int fourCC) {
        if (webPData == null) {
            return null;
        }
        try {
            RiffScanner scanner = RiffScanner.open(webPData);
            while (scanner.next()) {
                if (scanner.fourCC() == fourCC) {
                    return Arrays.copyOfRange(webPData, scanner.payloadOffset(), scanner.payloadEnd());
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // not a readable WebP container: treated as "metadata absent"
        }
        return null;
    }

    /**
     * Streams through the container of a file and reads only the payload of the requested chunk,
     * skipping image data instead of loading the whole file.
     */
    private static byte[] findChunkPayload(Path path, int fourCC) {
        try (FileChannel ch = FileChannel.open(path, StandardOpenOption.READ)) {
            long size = ch.size();
            ByteBuffer head = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
            if (!readFully(ch, head, 0)) {
                return null;
            }
            if (head.get(0) != 'R' || head.get(1) != 'I' || head.get(2) != 'F' || head.get(3) != 'F'
                    || head.get(8) != 'W' || head.get(9) != 'E' || head.get(10) != 'B' || head.get(11) != 'P') {
                return null;
            }
            long end = Math.min(size, 8L + (head.getInt(4) & 0xFFFFFFFFL));
            long pos = 12;
            ByteBuffer hdr = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            while (pos + 8 <= end) {
                hdr.clear();
                if (!readFully(ch, hdr, pos)) {
                    return null;
                }
                int id = hdr.getInt(0);
                long declared = hdr.getInt(4) & 0xFFFFFFFFL;
                long payload = pos + 8;
                if (id == fourCC) {
                    long len = Math.min(declared, end - payload);
                    WebPLimits.checkInputSize(len);
                    ByteBuffer data = ByteBuffer.allocate((int) len);
                    return readFully(ch, data, payload) ? data.array() : null;
                }
                pos = payload + declared + (declared & 1L);
            }
        } catch (IOException | RuntimeException ignored) {
            // unreadable file or limit exceeded: treated as "metadata absent"
        }
        return null;
    }

    private static boolean readFully(FileChannel ch, ByteBuffer buf, long position) throws IOException {
        long pos = position;
        while (buf.hasRemaining()) {
            int n = ch.read(buf, pos);
            if (n < 0) {
                return false;
            }
            pos += n;
        }
        return true;
    }

    /** Reads up to {@link #HEADER_PROBE_BYTES} bytes from the start of a file. */
    private static byte[] readHeader(Path path) throws IOException {
        try (FileChannel ch = FileChannel.open(path, StandardOpenOption.READ)) {
            ByteBuffer buf = ByteBuffer.allocate(HEADER_PROBE_BYTES);
            long pos = 0;
            while (buf.hasRemaining()) {
                int n = ch.read(buf, pos);
                if (n < 0) {
                    break;
                }
                pos += n;
            }
            return Arrays.copyOf(buf.array(), buf.position());
        }
    }
}
