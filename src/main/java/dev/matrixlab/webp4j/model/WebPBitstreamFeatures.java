package dev.matrixlab.webp4j.model;

import dev.matrixlab.webp4j.chunks.*;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.util.List;

/**
 * Represents WebP bitstream features.
 * This class is used by JNI code to return image metadata, and can also be
 * constructed via pure Java chunk parsing.
 */
@Getter
@Setter
public class WebPBitstreamFeatures {

    /**
     * Image width in pixels.
     */
    private int width;

    /**
     * Image height in pixels.
     */
    private int height;

    /**
     * True if the bitstream contains an alpha channel.
     */
    private boolean hasAlpha;

    /**
     * True if the bitstream is an animation.
     */
    private boolean hasAnimation;

    /**
     * Image format: 0 = undefined/mixed, 1 = lossy, 2 = lossless.
     */
    private int format;

    /**
     * Parses WebP bitstream features directly from a WebP byte array using RIFF chunk inspection.
     * Does not require the native libwebp library.
     *
     * @param webPData WebP image byte array
     * @return Populated WebPBitstreamFeatures
     * @throws IOException If parsing fails or the data is not a valid WebP container
     */
    public static WebPBitstreamFeatures parse(byte[] webPData) throws IOException {
        if (webPData == null || webPData.length < 12) {
            throw new IllegalArgumentException("WebP data cannot be null or shorter than 12 bytes.");
        }
        List<Chunk> chunks = ChunkFactory.parseAll(webPData);
        return fromChunks(chunks);
    }

    /**
     * Constructs WebPBitstreamFeatures from a list of parsed WebP chunks.
     *
     * @param chunks Parsed chunks
     * @return Populated WebPBitstreamFeatures
     */
    public static WebPBitstreamFeatures fromChunks(List<Chunk> chunks) {
        WebPBitstreamFeatures features = new WebPBitstreamFeatures();
        if (chunks == null || chunks.isEmpty()) {
            return features;
        }

        for (Chunk chunk : chunks) {
            if (chunk instanceof VP8XChunk) {
                VP8XChunk vp8x = (VP8XChunk) chunk;
                features.setWidth(vp8x.getCanvasWidth());
                features.setHeight(vp8x.getCanvasHeight());
                features.setHasAlpha(vp8x.hasALPHFlag());
                features.setHasAnimation(vp8x.hasANIMFlag());
            } else if (chunk instanceof VP8LChunk) {
                if (features.getWidth() == 0) {
                    byte[] data = ((VP8LChunk) chunk).getRawData();
                    if (data != null && data.length >= 5 && (data[0] & 0xFF) == 0x2F) {
                        int b0 = data[1] & 0xFF;
                        int b1 = data[2] & 0xFF;
                        int b2 = data[3] & 0xFF;
                        int b3 = data[4] & 0xFF;
                        features.setWidth(1 + (((b1 & 0x3F) << 8) | b0));
                        features.setHeight(1 + (((b3 & 0x0F) << 10) | (b2 << 2) | ((b1 & 0xC0) >> 6)));
                        features.setHasAlpha((b3 & 0x10) != 0);
                    }
                }
                if (features.getFormat() == 0) {
                    features.setFormat(2); // Lossless
                }
            } else if (chunk instanceof VP8Chunk) {
                if (features.getWidth() == 0) {
                    byte[] data = ((VP8Chunk) chunk).getRawData();
                    if (data != null && data.length >= 10
                            && (data[3] & 0xFF) == 0x9D && (data[4] & 0xFF) == 0x01 && (data[5] & 0xFF) == 0x2A) {
                        features.setWidth((data[6] & 0xFF) | ((data[7] & 0x3F) << 8));
                        features.setHeight((data[8] & 0xFF) | ((data[9] & 0x3F) << 8));
                    }
                }
                if (features.getFormat() == 0 && !features.isHasAnimation()) {
                    features.setFormat(1); // Lossy
                }
            } else if (chunk instanceof ALPHChunk) {
                features.setHasAlpha(true);
            } else if (chunk instanceof ANIMChunk) {
                features.setHasAnimation(true);
            }
        }

        return features;
    }

    @Override
    public String toString() {
        return "WebPBitstreamFeatures{" +
                "width=" + width +
                ", height=" + height +
                ", hasAlpha=" + hasAlpha +
                ", hasAnimation=" + hasAnimation +
                ", format=" + format +
                '}';
    }
}
