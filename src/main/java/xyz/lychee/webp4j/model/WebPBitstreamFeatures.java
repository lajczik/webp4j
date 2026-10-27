package xyz.lychee.webp4j.model;

import xyz.lychee.webp4j.chunks.*;
import xyz.lychee.webp4j.utils.RiffScanner;
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
 *
 * @author MrNanko
 * @author lajczik
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
     * Does not require the native libwebp library and allocates no chunk objects. Only the first
     * bytes of the image header are interpreted, so a truncated file (for example the first
     * 64 bytes of a file) is sufficient.
     *
     * @param webPData WebP image byte array
     * @return Populated WebPBitstreamFeatures
     * @throws IOException If parsing fails or the data is not a valid WebP container
     */
    public static WebPBitstreamFeatures parse(byte[] webPData) throws IOException {
        if (webPData == null || webPData.length < 12) {
            throw new IllegalArgumentException("WebP data cannot be null or shorter than 12 bytes.");
        }

        RiffScanner scanner = RiffScanner.open(webPData);
        WebPBitstreamFeatures f = new WebPBitstreamFeatures();
        boolean canvasKnown = false;

        while (scanner.next()) {
            int id = scanner.fourCC();
            int off = scanner.payloadOffset();
            int len = scanner.payloadLength();

            if (id == RiffScanner.VP8X) {
                if (len >= 10) {
                    int flags = Util.toInt32(webPData, off);
                    f.setWidth(1 + Util.toUInt24(webPData, off + 4));
                    f.setHeight(1 + Util.toUInt24(webPData, off + 7));
                    f.setHasAlpha(WebPContainerFlag.ALPHA.isSet(flags));
                    f.setHasAnimation(WebPContainerFlag.ANIMATION.isSet(flags));
                    canvasKnown = true;
                }
            } else if (id == RiffScanner.ALPH) {
                f.setHasAlpha(true);
            } else if (id == RiffScanner.ANIM) {
                f.setHasAnimation(true);
            } else if (id == RiffScanner.ANMF) {
                // Animated image: canvas and flags come from VP8X, frames are not inspected here.
                f.setHasAnimation(true);
                break;
            } else if (id == RiffScanner.VP8L) {
                if (!canvasKnown && len >= 5 && (webPData[off] & 0xFF) == 0x2F) {
                    int b0 = webPData[off + 1] & 0xFF;
                    int b1 = webPData[off + 2] & 0xFF;
                    int b2 = webPData[off + 3] & 0xFF;
                    int b3 = webPData[off + 4] & 0xFF;
                    f.setWidth(1 + (((b1 & 0x3F) << 8) | b0));
                    f.setHeight(1 + (((b3 & 0x0F) << 10) | (b2 << 2) | ((b1 & 0xC0) >> 6)));
                    f.setHasAlpha((b3 & 0x10) != 0);
                }
                f.setFormat(2); // Lossless
                break;
            } else if (id == RiffScanner.VP8) {
                if (!canvasKnown && len >= 10
                        && (webPData[off + 3] & 0xFF) == 0x9D
                        && (webPData[off + 4] & 0xFF) == 0x01
                        && (webPData[off + 5] & 0xFF) == 0x2A) {
                    f.setWidth((webPData[off + 6] & 0xFF) | ((webPData[off + 7] & 0x3F) << 8));
                    f.setHeight((webPData[off + 8] & 0xFF) | ((webPData[off + 9] & 0x3F) << 8));
                }
                if (!f.isHasAnimation()) {
                    f.setFormat(1); // Lossy
                }
                break;
            }
        }

        if (f.getWidth() <= 0 || f.getHeight() <= 0) {
            throw new IOException("Invalid WebP data: no image header found");
        }
        return f;
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
