package xyz.lychee.webp4j.chunks;

import xyz.lychee.webp4j.utils.RiffScanner;
import xyz.lychee.webp4j.utils.Util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Factory and parser for RIFF chunk structures in WebP files.
 * <p>
 * Parsing is driven by {@link RiffScanner}, so declared chunk sizes are never trusted: a size that
 * exceeds the container is clamped, and no input can make the parser loop or index out of bounds.
 *
 * @author lajczik
 */
public final class ChunkFactory {

    private static final Chunk[] EMPTY_CHUNK_ARRAY = new Chunk[0];

    private ChunkFactory() {
    }

    /**
     * Extracts the image bitstream chunks ({@code VP8L}, or optional {@code ALPH} followed by
     * {@code VP8 }) from a still WebP file, as produced by libwebp's encoder.
     *
     * @param webPData complete still WebP file
     * @return the bitstream chunks, or an empty array when the data is null or shorter than 16 bytes
     * @throws IllegalArgumentException if the data is not a still WebP image with an image bitstream
     */
    public static Chunk[] getChunks(byte[] webPData) {
        if (webPData == null || webPData.length < 16) {
            return EMPTY_CHUNK_ARRAY;
        }
        return extractBitstream(webPData, true);
    }

    /**
     * Extracts the lossy bitstream chunks ({@code ALPH} optional, {@code VP8 }) from a still WebP file.
     */
    public static Chunk[] getLossyChunks(byte[] webPData) {
        if (webPData == null || webPData.length < 16) {
            return EMPTY_CHUNK_ARRAY;
        }
        return extractBitstream(webPData, false);
    }

    private static Chunk[] extractBitstream(byte[] webPData, boolean allowLossless) {
        RiffScanner scanner;
        try {
            scanner = RiffScanner.open(webPData);
        } catch (IOException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }

        ALPHChunk alpha = null;
        while (scanner.next()) {
            int id = scanner.fourCC();
            int off = scanner.payloadOffset();
            int len = scanner.payloadLength();
            if (id == RiffScanner.ALPH && len > 0) {
                alpha = new ALPHChunk(Arrays.copyOfRange(webPData, off, off + len));
            } else if (id == RiffScanner.VP8) {
                VP8Chunk vp8 = new VP8Chunk(Arrays.copyOfRange(webPData, off, off + len));
                return alpha != null ? new Chunk[]{alpha, vp8} : new Chunk[]{vp8};
            } else if (id == RiffScanner.VP8L && allowLossless) {
                return new Chunk[]{new VP8LChunk(Arrays.copyOfRange(webPData, off, off + len))};
            } else if (id == RiffScanner.ANMF) {
                break;
            }
        }
        throw new IllegalArgumentException("Not a still WebP image: no VP8/VP8L bitstream chunk found");
    }

    public static VP8Chunk getVP8Chunk(byte[] webPData) {
        Chunk[] chunks = getLossyChunks(webPData);
        return (VP8Chunk) chunks[chunks.length - 1];
    }

    public static VP8LChunk getVP8LChunk(byte[] webPData) {
        Chunk[] chunks = getChunks(webPData);
        if (chunks.length != 1 || !(chunks[0] instanceof VP8LChunk)) {
            throw new IllegalArgumentException("Not a lossless WebP image");
        }
        return (VP8LChunk) chunks[0];
    }

    /**
     * Parses all chunks from a complete WebP byte array (with RIFF header).
     *
     * @param webPData WebP image byte array
     * @return List of parsed chunks
     * @throws IOException If the data does not represent a valid WebP container
     */
    public static List<Chunk> parseAll(byte[] webPData) throws IOException {
        if (webPData == null || webPData.length < 12) {
            throw new IllegalArgumentException("Invalid WebP data: too short for RIFF header");
        }
        return collect(RiffScanner.open(webPData), webPData);
    }

    /**
     * Parses chunks from a byte array slice.
     */
    public static List<Chunk> parseChunksFromOffset(byte[] data, int startOffset, int endOffset) {
        return collect(RiffScanner.slice(data, startOffset, endOffset), data);
    }

    private static List<Chunk> collect(RiffScanner scanner, byte[] data) {
        List<Chunk> chunks = new ArrayList<>();
        while (scanner.next()) {
            chunks.add(createChunkFromOffset(data, scanner.headerOffset(), scanner.payloadOffset(), scanner.payloadLength()));
        }
        return Collections.unmodifiableList(chunks);
    }

    private static boolean matches(byte[] data, int offset, byte[] fourCC) {
        return data[offset] == fourCC[0] &&
                data[offset + 1] == fourCC[1] &&
                data[offset + 2] == fourCC[2] &&
                data[offset + 3] == fourCC[3];
    }

    private static Chunk createChunkFromOffset(byte[] data, int offset, int payloadOffset, int payloadLen) {
        if (matches(data, offset, VP8Chunk.VP8)) {
            return new VP8Chunk(Arrays.copyOfRange(data, payloadOffset, payloadOffset + payloadLen));
        } else if (matches(data, offset, VP8LChunk.VP8L)) {
            return new VP8LChunk(Arrays.copyOfRange(data, payloadOffset, payloadOffset + payloadLen));
        } else if (matches(data, offset, VP8XChunk.VP8X) && payloadLen >= 10) {
            int flags = Util.toInt32(data, payloadOffset);
            int canvasWidth = 1 + Util.toUInt24(data, payloadOffset + 4);
            int canvasHeight = 1 + Util.toUInt24(data, payloadOffset + 7);
            return new VP8XChunk(flags, canvasWidth, canvasHeight);
        } else if (matches(data, offset, ANIMChunk.ANIM) && payloadLen >= 6) {
            int bgColor = Util.toInt32(data, payloadOffset);
            int loopCount = Util.toUInt16(data, payloadOffset + 4);
            return new ANIMChunk(bgColor, loopCount);
        } else if (matches(data, offset, ANMFChunk.ANMF) && payloadLen >= 16) {
            return parseAnmf(data, payloadOffset, payloadLen);
        } else if (matches(data, offset, ALPHChunk.ALPH) && payloadLen > 0) {
            return new ALPHChunk(Arrays.copyOfRange(data, payloadOffset, payloadOffset + payloadLen));
        } else if (matches(data, offset, ICCPChunk.ICCP)) {
            return new ICCPChunk(Arrays.copyOfRange(data, payloadOffset, payloadOffset + payloadLen));
        } else if (matches(data, offset, EXIFChunk.EXIF)) {
            return new EXIFChunk(Arrays.copyOfRange(data, payloadOffset, payloadOffset + payloadLen));
        } else if (matches(data, offset, XMPChunk.XMP)) {
            return new XMPChunk(Arrays.copyOfRange(data, payloadOffset, payloadOffset + payloadLen));
        } else {
            return new RawDataChunk(Arrays.copyOfRange(data, offset, offset + 4),
                    Arrays.copyOfRange(data, payloadOffset, payloadOffset + payloadLen));
        }
    }

    private static ANMFChunk parseAnmf(byte[] data, int payloadOffset, int payloadLen) {
        // The container stores the frame position divided by two (always an even pixel offset).
        int x = Util.toUInt24(data, payloadOffset) * 2;
        int y = Util.toUInt24(data, payloadOffset + 3) * 2;
        int width = 1 + Util.toUInt24(data, payloadOffset + 6);
        int height = 1 + Util.toUInt24(data, payloadOffset + 9);
        int duration = Util.toUInt24(data, payloadOffset + 12);
        int flags = data[payloadOffset + 15] & 0xFF;

        Chunk[] subchunks = EMPTY_CHUNK_ARRAY;
        if (payloadLen > 16) {
            List<Chunk> parsed = parseChunksFromOffset(data, payloadOffset + 16, payloadOffset + payloadLen);
            subchunks = parsed.toArray(new Chunk[0]);
        }
        return new ANMFChunk(x, y, width, height, duration, flags, subchunks);
    }

    /**
     * Creates a typed chunk instance based on FourCC and payload.
     *
     * @param fourCC  4-byte identifier
     * @param payload Chunk payload bytes
     * @return Specific Chunk subclass or RawDataChunk
     */
    public static Chunk createChunk(byte[] fourCC, byte[] payload) {
        if (Util.arrayEquals(fourCC, VP8Chunk.VP8)) {
            return new VP8Chunk(payload);
        } else if (Util.arrayEquals(fourCC, VP8LChunk.VP8L)) {
            return new VP8LChunk(payload);
        } else if (Util.arrayEquals(fourCC, VP8XChunk.VP8X) && payload.length >= 10) {
            int flags = Util.toInt32(payload, 0);
            int canvasWidth = 1 + Util.toUInt24(payload, 4);
            int canvasHeight = 1 + Util.toUInt24(payload, 7);
            return new VP8XChunk(flags, canvasWidth, canvasHeight);
        } else if (Util.arrayEquals(fourCC, ANIMChunk.ANIM) && payload.length >= 6) {
            int bgColor = Util.toInt32(payload, 0);
            int loopCount = Util.toUInt16(payload, 4);
            return new ANIMChunk(bgColor, loopCount);
        } else if (Util.arrayEquals(fourCC, ANMFChunk.ANMF) && payload.length >= 16) {
            return parseAnmf(payload, 0, payload.length);
        } else if (Util.arrayEquals(fourCC, ALPHChunk.ALPH) && payload.length > 0) {
            return new ALPHChunk(payload);
        } else if (Util.arrayEquals(fourCC, ICCPChunk.ICCP)) {
            return new ICCPChunk(payload);
        } else if (Util.arrayEquals(fourCC, EXIFChunk.EXIF)) {
            return new EXIFChunk(payload);
        } else if (Util.arrayEquals(fourCC, XMPChunk.XMP)) {
            return new XMPChunk(payload);
        } else {
            return new RawDataChunk(fourCC, payload);
        }
    }
}
