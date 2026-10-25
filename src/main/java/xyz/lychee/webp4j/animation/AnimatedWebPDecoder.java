package xyz.lychee.webp4j.animation;

import xyz.lychee.webp4j.chunks.VP8XChunk;
import xyz.lychee.webp4j.common.NativeBridge;
import xyz.lychee.webp4j.internal.PixelConverter;
import xyz.lychee.webp4j.model.AnimatedWebPData;
import xyz.lychee.webp4j.model.AnimatedWebPFrame;
import xyz.lychee.webp4j.model.AnimationInfo;
import xyz.lychee.webp4j.utils.Util;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Decoder for extracting individual frames from animated WebP images.
 * <p>
 * This class uses the libwebp WebPAnimDecoder API to decode animated WebP
 * images into individual frames as BufferedImage objects with their timestamps.
 *
 * @author MrNanko
 */
public final class AnimatedWebPDecoder {

    private AnimatedWebPDecoder() {
        throw new AssertionError("Cannot instantiate utility class.");
    }

    /**
     * Extracts animation metadata (dimensions, loop count, frame count, transparency)
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
        int frameCount = 0;
        int offset = 12;
        int endOffset = webPData.length;

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
                info.setHasTransparency((flags & VP8XChunk.ALPH_MASK) != 0);
            } else if (c0 == 'A' && c1 == 'N' && c2 == 'I' && c3 == 'M' && payloadLen >= 6) {
                info.setLoopCount(Util.toUInt16(webPData, payloadOffset + 4));
            } else if (c0 == 'A' && c1 == 'N' && c2 == 'M' && c3 == 'F') {
                frameCount++;
            }

            int padding = Util.isOdd(size) ? 1 : 0;
            offset = payloadOffset + (int) size + padding;
        }

        info.setFrameCount(frameCount);
        return info;
    }

    /**
     * Decodes an animated WebP image into individual frames.
     * <p>
     * Each frame is a fully composited canvas-sized RGBA BufferedImage.
     * The returned {@link AnimatedWebPData} contains all frames with their
     * timestamps, as well as animation metadata (canvas size, loop count, etc.).
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

        // Create result container for native layer to populate
        AnimatedWebPData result = new AnimatedWebPData();

        // Decode using native WebPAnimDecoder
        boolean success = NativeBridge.decodeAnimated(webPData, result);
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

        // Each BufferedImage wraps its frame's pixel array directly — no
        // second per-frame buffer ever exists.
        List<AnimatedWebPFrame> frames = new ArrayList<>(framePixels.length);
        for (int i = 0; i < framePixels.length; i++) {
            BufferedImage image = PixelConverter.wrapPixels(framePixels[i], canvasWidth, canvasHeight, true);
            frames.add(new AnimatedWebPFrame(image, timestamps[i]));
        }

        // Drop the redundant references; the images now own the pixel arrays.
        result.setFramePixels(null);
        result.setTimestamps(null);

        result.setFrames(frames);
        return result;
    }
}
