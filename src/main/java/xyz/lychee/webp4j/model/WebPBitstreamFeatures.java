package xyz.lychee.webp4j.model;

import xyz.lychee.webp4j.chunks.*;
import xyz.lychee.webp4j.utils.Util;
import lombok.Getter;
import lombok.Setter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;
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
     * Returns true if the image is encoded with lossless compression.
     */
    public boolean isLossless() {
        return format == 2;
    }

    /**
     * Returns true if the image is encoded with lossy compression.
     */
    public boolean isLossy() {
        return format == 1;
    }

    /**
     * Checks if the bitstream contains an alpha channel.
     *
     * @return true if alpha channel is present
     */
    public boolean hasAlpha() {
        return hasAlpha;
    }

    /**
     * Checks if the bitstream contains animation frames.
     *
     * @return true if animated
     */
    public boolean hasAnimation() {
        return hasAnimation;
    }

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
        if (webPData[0] != 'R' || webPData[1] != 'I' || webPData[2] != 'F' || webPData[3] != 'F'
                || webPData[8] != 'W' || webPData[9] != 'E' || webPData[10] != 'B' || webPData[11] != 'P') {
            throw new IOException("Invalid WebP data: missing RIFF/WEBP signature");
        }

        // Fast-path inspection without allocating Chunk objects or slicing payload byte arrays
        if (webPData.length >= 26) {
            byte c0 = webPData[12];
            byte c1 = webPData[13];
            byte c2 = webPData[14];
            byte c3 = webPData[15];

            // VP8 (lossy) simple container
            if (c0 == 'V' && c1 == 'P' && c2 == '8' && c3 == ' ') {
                if (webPData.length >= 30
                        && (webPData[23] & 0xFF) == 0x9D
                        && (webPData[24] & 0xFF) == 0x01
                        && (webPData[25] & 0xFF) == 0x2A) {
                    WebPBitstreamFeatures f = new WebPBitstreamFeatures();
                    f.setWidth((webPData[26] & 0xFF) | ((webPData[27] & 0x3F) << 8));
                    f.setHeight((webPData[28] & 0xFF) | ((webPData[29] & 0x3F) << 8));
                    f.setFormat(1); // Lossy
                    f.setHasAlpha(false);
                    f.setHasAnimation(false);
                    return f;
                }
            } else if (c0 == 'V' && c1 == 'P' && c2 == '8' && c3 == 'L') {
                // VP8L (lossless) simple container
                if (webPData.length >= 25 && (webPData[20] & 0xFF) == 0x2F) {
                    int b0 = webPData[21] & 0xFF;
                    int b1 = webPData[22] & 0xFF;
                    int b2 = webPData[23] & 0xFF;
                    int b3 = webPData[24] & 0xFF;
                    WebPBitstreamFeatures f = new WebPBitstreamFeatures();
                    f.setWidth(1 + (((b1 & 0x3F) << 8) | b0));
                    f.setHeight(1 + (((b3 & 0x0F) << 10) | (b2 << 2) | ((b1 & 0xC0) >> 6)));
                    f.setHasAlpha((b3 & 0x10) != 0);
                    f.setFormat(2); // Lossless
                    f.setHasAnimation(false);
                    return f;
                }
            } else if (c0 == 'V' && c1 == 'P' && c2 == '8' && c3 == 'X') {
                // VP8X extended container
                if (webPData.length >= 30) {
                    WebPBitstreamFeatures f = new WebPBitstreamFeatures();
                    int flags = Util.toInt32(webPData, 20);
                    f.setWidth(1 + Util.toUInt24(webPData, 24));
                    f.setHeight(1 + Util.toUInt24(webPData, 27));
                    f.setHasAlpha((flags & VP8XChunk.ALPH_MASK) != 0);
                    f.setHasAnimation((flags & VP8XChunk.ANIM_MASK) != 0);

                    // Scan subsequent chunks for format if not animated
                    int offset = 30; // 12 + 8 (VP8X header) + 10 (VP8X payload)
                    while (offset + 8 <= webPData.length) {
                        long chunkSize = Util.toUInt32(webPData, offset + 4);
                        if (webPData[offset] == 'V' && webPData[offset + 1] == 'P' && webPData[offset + 2] == '8') {
                            if (webPData[offset + 3] == 'L') {
                                f.setFormat(2); // Lossless
                                break;
                            } else if (webPData[offset + 3] == ' ') {
                                if (!f.isHasAnimation()) {
                                    f.setFormat(1); // Lossy
                                }
                                break;
                            }
                        } else if (webPData[offset] == 'A' && webPData[offset + 1] == 'L'
                                && webPData[offset + 2] == 'P' && webPData[offset + 3] == 'H') {
                            f.setHasAlpha(true);
                        } else if (webPData[offset] == 'A' && webPData[offset + 1] == 'N'
                                && webPData[offset + 2] == 'I' && webPData[offset + 3] == 'M') {
                            f.setHasAnimation(true);
                        }
                        int padding = Util.isOdd(chunkSize) ? 1 : 0;
                        offset += 8 + (int) chunkSize + padding;
                    }
                    return f;
                }
            }
        }

        // Fallback for complex/non-standard WebP files
        List<Chunk> chunks = ChunkFactory.parseAll(webPData);
        return fromChunks(chunks);
    }

    /**
     * Parses WebP bitstream features from a File.
     */
    public static WebPBitstreamFeatures parse(File file) throws IOException {
        return parse(Util.readFileBytes(file));
    }

    /**
     * Parses WebP bitstream features from a Path.
     */
    public static WebPBitstreamFeatures parse(Path path) throws IOException {
        return parse(Util.readFileBytes(path));
    }

    /**
     * Parses WebP bitstream features from an InputStream.
     */
    public static WebPBitstreamFeatures parse(InputStream in) throws IOException {
        return parse(Util.readAllBytes(in));
    }

    /**
     * Parses WebP bitstream features from a ByteBuffer.
     */
    public static WebPBitstreamFeatures parse(ByteBuffer buffer) throws IOException {
        return parse(Util.toByteArray(buffer));
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
