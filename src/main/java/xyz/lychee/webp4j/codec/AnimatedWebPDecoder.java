package xyz.lychee.webp4j.codec;

import xyz.lychee.webp4j.chunks.VP8XChunk;
import xyz.lychee.webp4j.natives.NativeWebP;
import xyz.lychee.webp4j.utils.PixelConverter;
import xyz.lychee.webp4j.model.AnimatedWebPData;
import xyz.lychee.webp4j.model.AnimatedWebPFrame;
import xyz.lychee.webp4j.model.AnimationInfo;
import xyz.lychee.webp4j.model.WebPContainerFlag;
import xyz.lychee.webp4j.utils.Util;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Decoder for extracting individual frames and animation metadata from animated WebP images.
 *
 * @author MrNanko
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
        if (webPData[0] != 'R' || webPData[1] != 'I' || webPData[2] != 'F' || webPData[3] != 'F'
                || webPData[8] != 'W' || webPData[9] != 'E' || webPData[10] != 'B' || webPData[11] != 'P') {
            throw new IOException("Invalid WebP data: missing RIFF/WEBP signature");
        }

        AnimationInfo info = new AnimationInfo();
        List<Integer> delayList = new ArrayList<>();
        int offset = 12;
        int endOffset = webPData.length;
        int totalDuration = 0;

        while (offset + 8 <= endOffset) {
            long size = Util.toUInt32(webPData, offset + 4);
            int payloadOffset = offset + 8;
            int payloadLen = (int) Math.min(size, endOffset - payloadOffset);

            byte c0 = webPData[offset];
            byte c1 = webPData[offset + 1];
            byte c2 = webPData[offset + 2];
            byte c3 = webPData[offset + 3];

            if (c0 == 'V' && c1 == 'P' && c2 == '8' && c3 == 'X' && payloadLen >= 10) {
                int flags = Util.toInt32(webPData, payloadOffset);
                info.setWidth(1 + Util.toUInt24(webPData, payloadOffset + 4));
                info.setHeight(1 + Util.toUInt24(webPData, payloadOffset + 7));
                info.setHasTransparency(WebPContainerFlag.ALPHA.isSet(flags));
            } else if (c0 == 'A' && c1 == 'N' && c2 == 'I' && c3 == 'M' && payloadLen >= 6) {
                info.setLoopCount(Util.toUInt16(webPData, payloadOffset + 4));
            } else if (c0 == 'A' && c1 == 'N' && c2 == 'M' && c3 == 'F') {
                if (payloadLen >= 16) {
                    int duration = Util.toUInt24(webPData, payloadOffset + 12);
                    delayList.add(duration);
                    totalDuration += duration;
                } else {
                    delayList.add(0);
                }
            }

            int padding = Util.isOdd(size) ? 1 : 0;
            offset = payloadOffset + (int) size + padding;
        }

        info.setFrameCount(delayList.size());
        info.setTotalDuration(totalDuration);
        int[] delays = new int[delayList.size()];
        for (int i = 0; i < delayList.size(); i++) {
            delays[i] = delayList.get(i);
        }
        info.setDelays(delays);
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
     *
     * @param webPData Byte array containing the animated WebP image data
     * @return AnimatedWebPData containing all decoded frames and metadata
     * @throws IOException              If decoding fails
     * @throws IllegalArgumentException If webPData is null or empty
     */
    public static AnimatedWebPData decode(byte[] webPData) throws IOException {
        if (webPData == null || webPData.length == 0) {
            throw new IllegalArgumentException("The input WebP data cannot be null or empty.");
        }

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

        List<AnimatedWebPFrame> frames = new ArrayList<>(framePixels.length);
        int prevTimestamp = 0;
        for (int i = 0; i < framePixels.length; i++) {
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
     *
     * @param webPData   WebP data
     * @param frameIndex Zero-based frame index
     * @return Decoded AnimatedWebPFrame
     * @throws IOException If decoding fails or index is out of bounds
     */
    public static AnimatedWebPFrame decodeFrame(byte[] webPData, int frameIndex) throws IOException {
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
}
