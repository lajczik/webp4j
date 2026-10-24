package dev.matrixlab.webp4j.animation;

import dev.matrixlab.webp4j.chunks.ANIMChunk;
import dev.matrixlab.webp4j.chunks.ANMFChunk;
import dev.matrixlab.webp4j.chunks.Chunk;
import dev.matrixlab.webp4j.chunks.ChunkFactory;
import dev.matrixlab.webp4j.chunks.VP8XChunk;
import dev.matrixlab.webp4j.internal.NativeWebP;
import dev.matrixlab.webp4j.internal.PixelConverter;
import dev.matrixlab.webp4j.model.AnimatedWebPData;
import dev.matrixlab.webp4j.model.AnimatedWebPFrame;
import dev.matrixlab.webp4j.model.AnimationInfo;

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
     * by scanning RIFF WebP chunks directly without decoding any pixel rasters.
     *
     * @param webPData Byte array containing the animated WebP image data
     * @return AnimationInfo populated from container chunks
     * @throws IOException If chunk parsing fails
     */
    public static AnimationInfo getAnimationInfo(byte[] webPData) throws IOException {
        if (webPData == null || webPData.length == 0) {
            throw new IllegalArgumentException("The input WebP data cannot be null or empty.");
        }
        List<Chunk> chunks = ChunkFactory.parseAll(webPData);
        AnimationInfo info = new AnimationInfo();
        int frameCount = 0;
        for (Chunk chunk : chunks) {
            if (chunk instanceof VP8XChunk) {
                VP8XChunk vp8x = (VP8XChunk) chunk;
                info.setWidth(vp8x.getCanvasWidth());
                info.setHeight(vp8x.getCanvasHeight());
                info.setHasTransparency(vp8x.hasALPHFlag());
            } else if (chunk instanceof ANIMChunk) {
                info.setLoopCount(((ANIMChunk) chunk).getLoopCount());
            } else if (chunk instanceof ANMFChunk) {
                frameCount++;
            }
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
