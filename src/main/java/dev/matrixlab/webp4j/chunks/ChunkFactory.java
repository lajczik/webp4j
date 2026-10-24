package xyz.lychee.discordbot.webp;

import java.util.Arrays;

public final class ChunkFactory {
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
}
