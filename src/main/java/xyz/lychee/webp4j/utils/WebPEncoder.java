package xyz.lychee.webp4j.utils;

import lombok.Getter;
import xyz.lychee.webp4j.chunks.*;
import xyz.lychee.webp4j.natives.NativeWebP;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Getter
public class WebPEncoder implements AutoCloseable, Flushable {
    public static final int FLAG_NO_BLEND = 1 << 1;
    public static final int FLAG_ALPHA_BLENDING = 0 << 1;
    public static final int FLAG_NO_DISPOSE = 0 << 0;
    public static final int FLAG_DISPOSE_TO_BACKGROUND = 1 << 0;

    private final OutputStream out;
    private final boolean alpha;
    private final List<Chunk> chunks;

    public WebPEncoder(OutputStream out, boolean alpha, int frames, int width, int height) {
        this(out, alpha, frames, width, height, 0, 0x00000000);
    }

    public WebPEncoder(OutputStream out, boolean alpha, int frames, int width, int height, int loopCount, int backgroundColor) {
        this.out = out;
        this.alpha = alpha;

        this.chunks = new ArrayList<>(2 + Math.max(0, frames));
        this.chunks.add(new VP8XChunk(VP8XChunk.FLAG_ANIM | (alpha ? VP8XChunk.FLAG_ALPH : 0), width, height));
        this.chunks.add(new ANIMChunk(backgroundColor, loopCount));
        for (int i = 0; i < frames; i++) {
            this.chunks.add(null);
        }
    }

    public static void encode(BufferedImage image, float quality, OutputStream out) throws IOException {
        if (image == null) {
            throw new IllegalArgumentException("Image cannot be null.");
        }
        boolean hasAlpha = image.getColorModel().hasAlpha();
        byte[] bgr = hasAlpha ? null : PixelConverter.bgrPixelsOrNull(image);
        byte[] byteBuf;
        if (bgr != null) {
            byteBuf = NativeWebP.encodeBgr(bgr, image.getWidth(), image.getHeight(), quality, quality >= 100, true);
        } else {
            int[] pixels = PixelConverter.toArgbPixels(image, hasAlpha);
            byteBuf = NativeWebP.encode(pixels, image.getWidth(), image.getHeight(), quality, quality >= 100, hasAlpha, true);
        }

        if (byteBuf == null || byteBuf.length == 0) {
            throw new IOException("WebP encoding failed.");
        }

        out.write(byteBuf);
    }

    /**
     * Encodes a static image and constructs the WebP container in Java, embedding optional metadata.
     *
     * @param image    Input image
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
            throw new IllegalArgumentException("Image cannot be null");
        }
        int width = image.getWidth();
        int height = image.getHeight();
        boolean hasAlpha = image.getColorModel().hasAlpha();

        // Encode raw pixels to WebP bitstream
        byte[] bitstream;
        byte[] bgr = hasAlpha ? null : PixelConverter.bgrPixelsOrNull(image);
        if (bgr != null) {
            bitstream = NativeWebP.encodeBgr(bgr, width, height, quality, lossless, true);
        } else {
            int[] pixels = PixelConverter.toArgbPixels(image, hasAlpha);
            bitstream = NativeWebP.encode(pixels, width, height, quality, lossless, hasAlpha, true);
        }

        if (bitstream == null || bitstream.length == 0) {
            throw new IOException("Failed to encode image bitstream");
        }

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

    public void setFrame(int index, BufferedImage frame, int duration, float quality) throws IOException {
        setFrame(index, frame, duration, quality, false);
    }

    public void setFrame(int index, BufferedImage frame, int duration, float quality, boolean lossless) throws IOException {
        int width = frame.getWidth();
        int height = frame.getHeight();
        boolean frameAlpha = frame.getColorModel().hasAlpha();

        byte[] webPData;
        byte[] bgr = frameAlpha ? null : PixelConverter.bgrPixelsOrNull(frame);
        if (bgr != null) {
            webPData = NativeWebP.encodeBgr(bgr, width, height, quality, lossless, true);
        } else {
            int[] pixels = PixelConverter.toArgbPixels(frame, frameAlpha);
            webPData = NativeWebP.encode(pixels, width, height, quality, lossless, this.alpha, true);
        }

        if (webPData == null || webPData.length == 0) {
            throw new IOException("Failed to encode frame " + index);
        }

        Chunk[] subchunks = ChunkFactory.getChunks(webPData);
        setChunkAt(index + 2, new ANMFChunk(0, 0, width, height, duration, 0, subchunks));
    }

    public void setFrame(int index, int[] pixels, int x, int y, int width, int height, int duration, int flags, float quality) throws IOException {
        setFrame(index, pixels, x, y, width, height, duration, flags, quality, false);
    }

    public void setFrame(int index, int[] pixels, int x, int y, int width, int height, int duration, int flags, float quality, boolean lossless) throws IOException {
        byte[] webPData = NativeWebP.encode(pixels, width, height, quality, lossless, this.alpha, true);
        if (webPData == null || webPData.length == 0) {
            throw new IOException("Failed to encode frame " + index);
        }

        Chunk[] subchunks = ChunkFactory.getChunks(webPData);
        setChunkAt(index + 2, new ANMFChunk(x, y, width, height, duration, flags, subchunks));
    }

    public void addFrame(BufferedImage frame, int duration, float quality, boolean lossless) throws IOException {
        int width = frame.getWidth();
        int height = frame.getHeight();
        boolean frameAlpha = frame.getColorModel().hasAlpha();

        byte[] webPData;
        byte[] bgr = frameAlpha ? null : PixelConverter.bgrPixelsOrNull(frame);
        if (bgr != null) {
            webPData = NativeWebP.encodeBgr(bgr, width, height, quality, lossless, true);
        } else {
            int[] pixels = PixelConverter.toArgbPixels(frame, frameAlpha);
            webPData = NativeWebP.encode(pixels, width, height, quality, lossless, this.alpha, true);
        }

        if (webPData == null || webPData.length == 0) {
            throw new IOException("Failed to encode frame");
        }

        Chunk[] subchunks = ChunkFactory.getChunks(webPData);
        chunks.add(new ANMFChunk(0, 0, width, height, duration, 0, subchunks));
    }

    public void setChunkAt(int index, Chunk chunk) {
        while (chunks.size() <= index) {
            chunks.add(null);
        }
        chunks.set(index, chunk);
    }

    @Override
    public void flush() throws IOException {
        List<Chunk> nonNull = new ArrayList<>(chunks.size());
        for (Chunk c : chunks) {
            if (c != null) {
                nonNull.add(c);
            }
        }
        WebPChunk webp = new WebPChunk(nonNull.toArray(new Chunk[0]));
        webp.write(this.out);
        this.out.flush();
    }

    @Override
    public void close() throws IOException {
        flush();
    }
}
