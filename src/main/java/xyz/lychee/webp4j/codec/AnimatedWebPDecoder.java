package xyz.lychee.webp4j.codec;

import xyz.lychee.webp4j.model.AnimatedWebPData;
import xyz.lychee.webp4j.model.AnimatedWebPFrame;
import xyz.lychee.webp4j.model.AnimationInfo;
import xyz.lychee.webp4j.model.WebPBitstreamFeatures;
import xyz.lychee.webp4j.model.WebPContainerFlag;
import xyz.lychee.webp4j.natives.NativeWebP;
import xyz.lychee.webp4j.utils.PixelConverter;
import xyz.lychee.webp4j.utils.RiffScanner;
import xyz.lychee.webp4j.utils.Util;
import xyz.lychee.webp4j.utils.WebPLimits;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Decoder for extracting individual frames and animation metadata from animated WebP images.
 *
 * @author MrNanko
 * @author lajczik
 */
public final class AnimatedWebPDecoder {

    private AnimatedWebPDecoder() {
        throw new AssertionError("Cannot instantiate utility class.");
    }

    /**
     * Extracts animation metadata (dimensions, loop count, frame count, delays, transparency)
     * by scanning RIFF WebP chunks directly without decoding any pixel rasters or copying payload buffers.
     *
     * @param webPData Byte array containing the animated WebP image data
     * @return AnimationInfo populated from container chunks
     * @throws IOException If chunk parsing fails
     */
    public static AnimationInfo getAnimationInfo(byte[] webPData) throws IOException {
        if (webPData == null || webPData.length < 12) {
            throw new IllegalArgumentException("The input WebP data cannot be null or empty.");
        }

        RiffScanner scanner = RiffScanner.open(webPData);
        AnimationInfo info = new AnimationInfo();
        int[] delays = new int[16];
        int frameCount = 0;
        long totalDuration = 0;

        while (scanner.next()) {
            int id = scanner.fourCC();
            int off = scanner.payloadOffset();
            int len = scanner.payloadLength();

            if (id == RiffScanner.VP8X && len >= 10) {
                int flags = Util.toInt32(webPData, off);
                info.setWidth(1 + Util.toUInt24(webPData, off + 4));
                info.setHeight(1 + Util.toUInt24(webPData, off + 7));
                info.setHasTransparency(WebPContainerFlag.ALPHA.isSet(flags));
            } else if (id == RiffScanner.ANIM && len >= 6) {
                info.setLoopCount(Util.toUInt16(webPData, off + 4));
            } else if (id == RiffScanner.ANMF) {
                int duration = (len >= 16) ? Util.toUInt24(webPData, off + 12) : 0;
                if (frameCount == delays.length) {
                    delays = Arrays.copyOf(delays, delays.length * 2);
                }
                delays[frameCount++] = duration;
                totalDuration += duration;
            }
        }

        info.setFrameCount(frameCount);
        info.setTotalDuration((int) Math.min(Integer.MAX_VALUE, totalDuration));
        info.setDelays(Arrays.copyOf(delays, frameCount));
        return info;
    }

    public static AnimationInfo getAnimationInfo(File file) throws IOException {
        return getAnimationInfo(Util.readFileBytes(file));
    }

    public static AnimationInfo getAnimationInfo(Path path) throws IOException {
        return getAnimationInfo(Util.readFileBytes(path));
    }

    public static AnimationInfo getAnimationInfo(InputStream in) throws IOException {
        return getAnimationInfo(Util.readAllBytes(in));
    }

    public static AnimationInfo getAnimationInfo(ByteBuffer buffer) throws IOException {
        return getAnimationInfo(Util.toByteArray(buffer));
    }

    /**
     * Fast inspection of total frame count without full animation decoding.
     *
     * @param webPData WebP data
     * @return Number of frames in animation, or 0 if not animated
     */
    public static int getFrameCount(byte[] webPData) {
        try {
            return getAnimationInfo(webPData).getFrameCount();
        } catch (Exception e) {
            return 0;
        }
    }

    public static int getFrameCount(File file) {
        try {
            return getFrameCount(Util.readFileBytes(file));
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Decodes an animated WebP image into individual frames.
     * <p>
     * The declared canvas size and frame count are validated against {@link WebPLimits} before any
     * pixel memory is reserved.
     *
     * @param webPData Byte array containing the animated WebP image data
     * @return AnimatedWebPData containing all decoded frames and metadata
     * @throws IOException              If decoding fails or the image exceeds the configured limits
     * @throws IllegalArgumentException If webPData is null or empty
     */
    public static AnimatedWebPData decode(byte[] webPData) throws IOException {
        if (webPData == null || webPData.length == 0) {
            throw new IllegalArgumentException("The input WebP data cannot be null or empty.");
        }
        checkLimits(webPData);

        AnimatedWebPData result = new AnimatedWebPData();

        boolean success = NativeWebP.decodeAnimated(webPData, result);
        if (!success) {
            throw new IOException("Failed to decode animated WebP image.");
        }

        int[][] framePixels = result.getFramePixels();
        int[] timestamps = result.getTimestamps();

        if (framePixels == null || timestamps == null) {
            throw new IOException("Native decoder returned incomplete data.");
        }

        int canvasWidth = result.getCanvasWidth();
        int canvasHeight = result.getCanvasHeight();
        int decoded = Math.min(result.getFrameCount(), framePixels.length);

        List<AnimatedWebPFrame> frames = new ArrayList<>(decoded);
        int prevTimestamp = 0;
        for (int i = 0; i < decoded; i++) {
            BufferedImage image = PixelConverter.wrapPixels(framePixels[i], canvasWidth, canvasHeight, true);
            int ts = timestamps[i];
            int duration = ts - prevTimestamp;
            prevTimestamp = ts;
            frames.add(new AnimatedWebPFrame(image, ts, duration));
        }

        result.setFramePixels(null);
        result.setTimestamps(null);
        result.setFrames(frames);
        return result;
    }

    /**
     * Validates the declared canvas size and frame count against the configured limits.
     */
    private static void checkLimits(byte[] webPData) throws IOException {
        AnimationInfo info = getAnimationInfo(webPData);
        int width = info.getWidth();
        int height = info.getHeight();
        if (width <= 0 || height <= 0) {
            WebPBitstreamFeatures features = WebPBitstreamFeatures.parse(webPData);
            width = features.getWidth();
            height = features.getHeight();
        }
        WebPLimits.checkAnimation(width, height, Math.max(1, info.getFrameCount()));
    }

    public static AnimatedWebPData decode(File file) throws IOException {
        return decode(Util.readFileBytes(file));
    }

    public static AnimatedWebPData decode(Path path) throws IOException {
        return decode(Util.readFileBytes(path));
    }

    public static AnimatedWebPData decode(InputStream in) throws IOException {
        return decode(Util.readAllBytes(in));
    }

    public static AnimatedWebPData decode(ByteBuffer buffer) throws IOException {
        return decode(Util.toByteArray(buffer));
    }

    /**
     * Decodes an animated WebP image and extracts all frames as a list of {@link BufferedImage} instances.
     *
     * @param webPData Byte array containing the animated WebP image data
     * @return List of decoded BufferedImage frames
     * @throws IOException If decoding fails
     */
    public static List<BufferedImage> decodeImages(byte[] webPData) throws IOException {
        AnimatedWebPData data = decode(webPData);
        List<AnimatedWebPFrame> frames = data.getFrames();
        List<BufferedImage> images = new ArrayList<>(frames.size());
        for (AnimatedWebPFrame frame : frames) {
            images.add(frame.getImage());
        }
        return images;
    }

    public static List<BufferedImage> decodeImages(File file) throws IOException {
        return decodeImages(Util.readFileBytes(file));
    }

    public static List<BufferedImage> decodeImages(Path path) throws IOException {
        return decodeImages(Util.readFileBytes(path));
    }

    public static List<BufferedImage> decodeImages(InputStream in) throws IOException {
        return decodeImages(Util.readAllBytes(in));
    }

    public static List<BufferedImage> decodeImages(ByteBuffer buffer) throws IOException {
        return decodeImages(Util.toByteArray(buffer));
    }

    /**
     * Decodes a specific frame by index from an animated WebP.
     * <p>
     * Frame 0 is decoded on its own without touching the other frames. Later frames depend on
     * the composition of all previous ones, so they require decoding the animation up to the end
     * (use {@link #decode(byte[])} when more than one frame is needed).
     *
     * @param webPData   WebP data
     * @param frameIndex Zero-based frame index
     * @return Decoded AnimatedWebPFrame
     * @throws IOException If decoding fails or index is out of bounds
     */
    public static AnimatedWebPFrame decodeFrame(byte[] webPData, int frameIndex) throws IOException {
        if (frameIndex == 0) {
            return decodeFirstFrame(webPData);
        }
        AnimatedWebPData data = decode(webPData);
        if (data.getFrames() == null || frameIndex < 0 || frameIndex >= data.getFrames().size()) {
            throw new IndexOutOfBoundsException("Frame index " + frameIndex + " out of bounds");
        }
        return data.getFrames().get(frameIndex);
    }

    public static AnimatedWebPFrame decodeFrame(File file, int frameIndex) throws IOException {
        return decodeFrame(Util.readFileBytes(file), frameIndex);
    }

    public static AnimatedWebPFrame decodeFrame(Path path, int frameIndex) throws IOException {
        return decodeFrame(Util.readFileBytes(path), frameIndex);
    }

    /**
     * Decodes only the first frame of an animation.
     * <p>
     * The first frame is composed on a transparent canvas, so it can be reconstructed from its own
     * bitstream: the frame payload is wrapped into a still WebP file and decoded with a single
     * libwebp call, instead of decoding and keeping every frame of the animation. If the container
     * has an unexpected structure the method falls back to full decoding.
     *
     * @param webPData animated (or still) WebP data
     * @return the first frame as a canvas-sized image
     * @throws IOException if decoding fails
     */
    public static AnimatedWebPFrame decodeFirstFrame(byte[] webPData) throws IOException {
        if (webPData == null || webPData.length == 0) {
            throw new IllegalArgumentException("The input WebP data cannot be null or empty.");
        }
        AnimatedWebPFrame fast = tryDecodeFirstFrame(webPData);
        if (fast != null) {
            return fast;
        }
        AnimatedWebPData data = decode(webPData);
        if (data.getFrames() == null || data.getFrames().isEmpty()) {
            throw new IOException("Animated WebP contains no frames.");
        }
        return data.getFrames().get(0);
    }

    private static AnimatedWebPFrame tryDecodeFirstFrame(byte[] data) throws IOException {
        RiffScanner scanner;
        try {
            scanner = RiffScanner.open(data);
        } catch (IOException e) {
            return null;
        }

        int canvasW = 0;
        int canvasH = 0;
        while (scanner.next()) {
            int off = scanner.payloadOffset();
            int len = scanner.payloadLength();
            if (scanner.fourCC() == RiffScanner.VP8X && len >= 10) {
                canvasW = 1 + Util.toUInt24(data, off + 4);
                canvasH = 1 + Util.toUInt24(data, off + 7);
            } else if (scanner.fourCC() == RiffScanner.ANMF) {
                if (len < 16 || canvasW <= 0 || canvasH <= 0) {
                    return null;
                }
                return composeFirstFrame(data, off, len, canvasW, canvasH);
            }
        }
        return null;
    }

    private static AnimatedWebPFrame composeFirstFrame(byte[] data, int off, int len,
                                                       int canvasW, int canvasH) throws IOException {
        WebPLimits.checkImage(canvasW, canvasH);
        int x = Util.toUInt24(data, off) * 2;
        int y = Util.toUInt24(data, off + 3) * 2;
        int frameW = 1 + Util.toUInt24(data, off + 6);
        int frameH = 1 + Util.toUInt24(data, off + 9);
        int duration = Util.toUInt24(data, off + 12);
        WebPLimits.checkImage(frameW, frameH);

        // Locate the frame's bitstream chunks (optional ALPH, then VP8 or VP8L).
        int end = off + len;
        RiffScanner sub = RiffScanner.slice(data, off + 16, end);
        int bitstreamStart = -1;
        boolean hasAlph = false;
        while (sub.next()) {
            int id = sub.fourCC();
            if (id == RiffScanner.ALPH) {
                hasAlph = true;
                if (bitstreamStart < 0) {
                    bitstreamStart = sub.headerOffset();
                }
            } else if (id == RiffScanner.VP8 || id == RiffScanner.VP8L) {
                if (bitstreamStart < 0) {
                    bitstreamStart = sub.headerOffset();
                }
                break;
            }
        }
        if (bitstreamStart < 0) {
            return null;
        }

        int vp8xLen = hasAlph ? 18 : 0;
        int bitstreamLen = end - bitstreamStart;
        byte[] still = new byte[12 + vp8xLen + bitstreamLen];
        writeAscii(still, 0, "RIFF");
        writeLe32(still, 4, still.length - 8);
        writeAscii(still, 8, "WEBP");
        int pos = 12;
        if (hasAlph) {
            writeAscii(still, pos, "VP8X");
            writeLe32(still, pos + 4, 10);
            still[pos + 8] = (byte) WebPContainerFlag.ALPHA.getMask();
            writeLe24(still, pos + 12, frameW - 1);
            writeLe24(still, pos + 15, frameH - 1);
            pos += 18;
        }
        System.arraycopy(data, bitstreamStart, still, pos, bitstreamLen);

        BufferedImage frame = WebPDecoder.decode(still);
        if (frame.getWidth() != frameW || frame.getHeight() != frameH) {
            return null;
        }

        int[] src = PixelConverter.liveBackingOrNull(frame);
        if (src == null) {
            src = frame.getRGB(0, 0, frameW, frameH, null, 0, frameW);
        }
        boolean opaqueSource = !frame.getColorModel().hasAlpha();

        int[] canvas = new int[canvasW * canvasH];
        int copyW = Math.min(frameW, canvasW - x);
        int copyH = Math.min(frameH, canvasH - y);
        for (int row = 0; row < copyH; row++) {
            int s = row * frameW;
            int d = (y + row) * canvasW + x;
            for (int col = 0; col < copyW; col++) {
                // libwebp decodes a key frame straight into the zero-filled canvas, without blending.
                int px = src[s + col];
                canvas[d + col] = opaqueSource ? (px | 0xFF000000) : px;
            }
        }

        BufferedImage image = PixelConverter.wrapPixels(canvas, canvasW, canvasH, true);
        return new AnimatedWebPFrame(image, duration, duration);
    }

    private static void writeAscii(byte[] b, int off, String s) {
        for (int i = 0; i < s.length(); i++) {
            b[off + i] = (byte) s.charAt(i);
        }
    }

    private static void writeLe32(byte[] b, int off, int v) {
        b[off] = (byte) v;
        b[off + 1] = (byte) (v >>> 8);
        b[off + 2] = (byte) (v >>> 16);
        b[off + 3] = (byte) (v >>> 24);
    }

    private static void writeLe24(byte[] b, int off, int v) {
        b[off] = (byte) v;
        b[off + 1] = (byte) (v >>> 8);
        b[off + 2] = (byte) (v >>> 16);
    }
}
