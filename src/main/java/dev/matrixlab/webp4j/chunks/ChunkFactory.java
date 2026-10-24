package dev.matrixlab.webp4j.chunks;

import dev.matrixlab.webp4j.utils.Util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ChunkFactory {

    private ChunkFactory() {
    }

    public static Chunk[] getChunks(byte[] webPData) {
        byte[] chunkHeaderBuf = Arrays.copyOfRange(webPData, 12, 16);
        if (Util.arrayEquals(chunkHeaderBuf, VP8Chunk.VP8)) {
            return new Chunk[]{getVP8Chunk(webPData)};
        } else if (Util.arrayEquals(chunkHeaderBuf, VP8LChunk.VP8L)) {
            return new Chunk[]{getVP8LChunk(webPData)};
        } else {
            long lengthALPH = Util.toUInt32(Arrays.copyOfRange(webPData, 34, 38));
            int pad = Util.isOdd(lengthALPH) ? 1 : 0;
            byte[] bufALPH = Arrays.copyOfRange(webPData, 38, 38 + (int) lengthALPH);
            int offsetVP8Len = 42 + bufALPH.length + pad;
            long lengthVP8 = Util.toUInt32(Arrays.copyOfRange(webPData, offsetVP8Len, offsetVP8Len + 4));
            int offsetVP8Data = 46 + bufALPH.length + pad;
            byte[] bufVP8 = Arrays.copyOfRange(webPData, offsetVP8Data, offsetVP8Data + (int) lengthVP8);
            return new Chunk[]{new ALPHChunk(bufALPH), new VP8Chunk(bufVP8)};
        }
    }

    public static Chunk[] getLossyChunks(byte[] webPData) {
        byte[] chunkHeaderBuf = Arrays.copyOfRange(webPData, 12, 16);
        if (Util.arrayEquals(chunkHeaderBuf, VP8Chunk.VP8)) {
            return new Chunk[]{getVP8Chunk(webPData)};
        } else {
            long lengthALPH = Util.toUInt32(Arrays.copyOfRange(webPData, 34, 38));
            int pad = Util.isOdd(lengthALPH) ? 1 : 0;
            byte[] bufALPH = Arrays.copyOfRange(webPData, 38, 38 + (int) lengthALPH);
            int offsetVP8Len = 42 + bufALPH.length + pad;
            long lengthVP8 = Util.toUInt32(Arrays.copyOfRange(webPData, offsetVP8Len, offsetVP8Len + 4));
            int offsetVP8Data = 46 + bufALPH.length + pad;
            byte[] bufVP8 = Arrays.copyOfRange(webPData, offsetVP8Data, offsetVP8Data + (int) lengthVP8);
            return new Chunk[]{new ALPHChunk(bufALPH), new VP8Chunk(bufVP8)};
        }
    }

    public static VP8Chunk getVP8Chunk(byte[] webPData) {
        byte[] lengthBuf = Arrays.copyOfRange(webPData, 16, 20);
        long length = Util.toUInt32(lengthBuf);
        byte[] buf = Arrays.copyOfRange(webPData, 20, 20 + (int) length);
        return new VP8Chunk(buf);
    }

    public static VP8LChunk getVP8LChunk(byte[] webPData) {
        byte[] lengthBuf = Arrays.copyOfRange(webPData, 16, 20);
        long length = Util.toUInt32(lengthBuf);

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
            byte[] fourCC = Arrays.copyOfRange(data, offset, offset + 4);
            long size = Util.toUInt32(Arrays.copyOfRange(data, offset + 4, offset + 8));
            int payloadOffset = offset + 8;
            int payloadLen = (int) size;
            if (payloadOffset + payloadLen > endOffset) {
                payloadLen = Math.max(0, endOffset - payloadOffset);
            }
            byte[] payload = Arrays.copyOfRange(data, payloadOffset, payloadOffset + payloadLen);

            chunks.add(createChunk(fourCC, payload));

            int padding = Util.isOdd(size) ? 1 : 0;
            offset = payloadOffset + payloadLen + padding;
        }
        return Collections.unmodifiableList(chunks);
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
            int flags = (payload[0] & 0xFF) | ((payload[1] & 0xFF) << 8) | ((payload[2] & 0xFF) << 16) | ((payload[3] & 0xFF) << 24);
            int canvasWidth = 1 + ((payload[4] & 0xFF) | ((payload[5] & 0xFF) << 8) | ((payload[6] & 0xFF) << 16));
            int canvasHeight = 1 + ((payload[7] & 0xFF) | ((payload[8] & 0xFF) << 8) | ((payload[9] & 0xFF) << 16));
            return new VP8XChunk(flags, canvasWidth, canvasHeight);
        } else if (Util.arrayEquals(fourCC, ANIMChunk.ANIM) && payload.length >= 6) {
            int bgColor = (payload[0] & 0xFF) | ((payload[1] & 0xFF) << 8) | ((payload[2] & 0xFF) << 16) | ((payload[3] & 0xFF) << 24);
            int loopCount = (payload[4] & 0xFF) | ((payload[5] & 0xFF) << 8);
            return new ANIMChunk(bgColor, loopCount);
        } else if (Util.arrayEquals(fourCC, ANMFChunk.ANMF) && payload.length >= 16) {
            int x = (payload[0] & 0xFF) | ((payload[1] & 0xFF) << 8) | ((payload[2] & 0xFF) << 16);
            int y = (payload[3] & 0xFF) | ((payload[4] & 0xFF) << 8) | ((payload[5] & 0xFF) << 16);
            int width = 1 + ((payload[6] & 0xFF) | ((payload[7] & 0xFF) << 8) | ((payload[8] & 0xFF) << 16));
            int height = 1 + ((payload[9] & 0xFF) | ((payload[10] & 0xFF) << 8) | ((payload[11] & 0xFF) << 16));
            int duration = (payload[12] & 0xFF) | ((payload[13] & 0xFF) << 8) | ((payload[14] & 0xFF) << 16);
            int flags = payload[15] & 0xFF;

            List<Chunk> subchunks = Collections.emptyList();
            if (payload.length > 16) {
                subchunks = parseChunksFromOffset(payload, 16, payload.length);
            }
            return new ANMFChunk(x, y, width, height, duration, flags, subchunks.toArray(new Chunk[0]));
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
