package xyz.lychee.webp4j.chunks;

import xyz.lychee.webp4j.utils.Util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ChunkFactory {

    private static final Chunk[] EMPTY_CHUNK_ARRAY = new Chunk[0];

    private ChunkFactory() {
    }

    private static boolean matchesFourCC(byte[] data, int offset, byte[] fourCC) {
        return data[offset] == fourCC[0] &&
               data[offset + 1] == fourCC[1] &&
               data[offset + 2] == fourCC[2] &&
               data[offset + 3] == fourCC[3];
    }

    public static Chunk[] getChunks(byte[] webPData) {
        if (webPData == null || webPData.length < 16) {
            return EMPTY_CHUNK_ARRAY;
        }
        if (matchesFourCC(webPData, 12, VP8Chunk.VP8)) {
            return new Chunk[]{getVP8Chunk(webPData)};
        } else if (matchesFourCC(webPData, 12, VP8LChunk.VP8L)) {
            return new Chunk[]{getVP8LChunk(webPData)};
        } else {
            long lengthALPH = Util.toUInt32(webPData, 34);
            int pad = Util.isOdd(lengthALPH) ? 1 : 0;
            byte[] bufALPH = Arrays.copyOfRange(webPData, 38, 38 + (int) lengthALPH);
            int offsetVP8Len = 42 + bufALPH.length + pad;
            long lengthVP8 = Util.toUInt32(webPData, offsetVP8Len);
            int offsetVP8Data = 46 + bufALPH.length + pad;
            byte[] bufVP8 = Arrays.copyOfRange(webPData, offsetVP8Data, offsetVP8Data + (int) lengthVP8);
            return new Chunk[]{new ALPHChunk(bufALPH), new VP8Chunk(bufVP8)};
        }
    }

    public static Chunk[] getLossyChunks(byte[] webPData) {
        if (webPData == null || webPData.length < 16) {
            return EMPTY_CHUNK_ARRAY;
        }
        if (matchesFourCC(webPData, 12, VP8Chunk.VP8)) {
            return new Chunk[]{getVP8Chunk(webPData)};
        } else {
            long lengthALPH = Util.toUInt32(webPData, 34);
            int pad = Util.isOdd(lengthALPH) ? 1 : 0;
            byte[] bufALPH = Arrays.copyOfRange(webPData, 38, 38 + (int) lengthALPH);
            int offsetVP8Len = 42 + bufALPH.length + pad;
            long lengthVP8 = Util.toUInt32(webPData, offsetVP8Len);
            int offsetVP8Data = 46 + bufALPH.length + pad;
            byte[] bufVP8 = Arrays.copyOfRange(webPData, offsetVP8Data, offsetVP8Data + (int) lengthVP8);
            return new Chunk[]{new ALPHChunk(bufALPH), new VP8Chunk(bufVP8)};
        }
    }

    public static VP8Chunk getVP8Chunk(byte[] webPData) {
        long length = Util.toUInt32(webPData, 16);
        byte[] buf = Arrays.copyOfRange(webPData, 20, 20 + (int) length);
        return new VP8Chunk(buf);
    }

    public static VP8LChunk getVP8LChunk(byte[] webPData) {
        long length = Util.toUInt32(webPData, 16);
        byte[] buf = Arrays.copyOfRange(webPData, 20, 20 + (int) length);
        return new VP8LChunk(buf);
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
        if (webPData[0] != 'R' || webPData[1] != 'I' || webPData[2] != 'F' || webPData[3] != 'F') {
            throw new IOException("Invalid WebP data: missing RIFF signature");
        }
        if (webPData[8] != 'W' || webPData[9] != 'E' || webPData[10] != 'B' || webPData[11] != 'P') {
            throw new IOException("Invalid WebP data: missing WEBP signature");
        }

        return parseChunksFromOffset(webPData, 12, webPData.length);
    }

    /**
     * Parses chunks from a byte array slice.
     */
    public static List<Chunk> parseChunksFromOffset(byte[] data, int startOffset, int endOffset) {
        List<Chunk> chunks = new ArrayList<>();
        int offset = startOffset;
        while (offset + 8 <= endOffset) {
            long size = Util.toUInt32(data, offset + 4);
            int payloadOffset = offset + 8;
            int payloadLen = (int) size;
            if (payloadOffset + payloadLen > endOffset) {
                payloadLen = Math.max(0, endOffset - payloadOffset);
            }

            chunks.add(createChunkFromOffset(data, offset, payloadOffset, payloadLen));

            int padding = Util.isOdd(size) ? 1 : 0;
            offset = payloadOffset + payloadLen + padding;
        }
        return Collections.unmodifiableList(chunks);
    }

    private static Chunk createChunkFromOffset(byte[] data, int offset, int payloadOffset, int payloadLen) {
        if (matchesFourCC(data, offset, VP8Chunk.VP8)) {
            byte[] payload = Arrays.copyOfRange(data, payloadOffset, payloadOffset + payloadLen);
            return new VP8Chunk(payload);
        } else if (matchesFourCC(data, offset, VP8LChunk.VP8L)) {
            byte[] payload = Arrays.copyOfRange(data, payloadOffset, payloadOffset + payloadLen);
            return new VP8LChunk(payload);
        } else if (matchesFourCC(data, offset, VP8XChunk.VP8X) && payloadLen >= 10) {
            int flags = Util.toInt32(data, payloadOffset);
            int canvasWidth = 1 + Util.toUInt24(data, payloadOffset + 4);
            int canvasHeight = 1 + Util.toUInt24(data, payloadOffset + 7);
            return new VP8XChunk(flags, canvasWidth, canvasHeight);
        } else if (matchesFourCC(data, offset, ANIMChunk.ANIM) && payloadLen >= 6) {
            int bgColor = Util.toInt32(data, payloadOffset);
            int loopCount = Util.toUInt16(data, payloadOffset + 4);
            return new ANIMChunk(bgColor, loopCount);
        } else if (matchesFourCC(data, offset, ANMFChunk.ANMF) && payloadLen >= 16) {
            int x = Util.toUInt24(data, payloadOffset);
            int y = Util.toUInt24(data, payloadOffset + 3);
            int width = 1 + Util.toUInt24(data, payloadOffset + 6);
            int height = 1 + Util.toUInt24(data, payloadOffset + 9);
            int duration = Util.toUInt24(data, payloadOffset + 12);
            int flags = data[payloadOffset + 15] & 0xFF;

            Chunk[] subchunks = EMPTY_CHUNK_ARRAY;
            if (payloadLen > 16) {
                List<Chunk> parsedSubchunks = parseChunksFromOffset(data, payloadOffset + 16, payloadOffset + payloadLen);
                subchunks = parsedSubchunks.toArray(new Chunk[parsedSubchunks.size()]);
            }
            return new ANMFChunk(x, y, width, height, duration, flags, subchunks);
        } else if (matchesFourCC(data, offset, ALPHChunk.ALPH)) {
            byte[] payload = Arrays.copyOfRange(data, payloadOffset, payloadOffset + payloadLen);
            return new ALPHChunk(payload);
        } else if (matchesFourCC(data, offset, ICCPChunk.ICCP)) {
            byte[] payload = Arrays.copyOfRange(data, payloadOffset, payloadOffset + payloadLen);
            return new ICCPChunk(payload);
        } else if (matchesFourCC(data, offset, EXIFChunk.EXIF)) {
            byte[] payload = Arrays.copyOfRange(data, payloadOffset, payloadOffset + payloadLen);
            return new EXIFChunk(payload);
        } else if (matchesFourCC(data, offset, XMPChunk.XMP)) {
            byte[] payload = Arrays.copyOfRange(data, payloadOffset, payloadOffset + payloadLen);
            return new XMPChunk(payload);
        } else {
            byte[] fourCC = Arrays.copyOfRange(data, offset, offset + 4);
            byte[] payload = Arrays.copyOfRange(data, payloadOffset, payloadOffset + payloadLen);
            return new RawDataChunk(fourCC, payload);
        }
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
            int x = Util.toUInt24(payload, 0);
            int y = Util.toUInt24(payload, 3);
            int width = 1 + Util.toUInt24(payload, 6);
            int height = 1 + Util.toUInt24(payload, 9);
            int duration = Util.toUInt24(payload, 12);
            int flags = payload[15] & 0xFF;

            Chunk[] subchunks = EMPTY_CHUNK_ARRAY;
            if (payload.length > 16) {
                List<Chunk> parsedSubchunks = parseChunksFromOffset(payload, 16, payload.length);
                subchunks = parsedSubchunks.toArray(new Chunk[parsedSubchunks.size()]);
            }
            return new ANMFChunk(x, y, width, height, duration, flags, subchunks);
        } else if (Util.arrayEquals(fourCC, ALPHChunk.ALPH)) {
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
