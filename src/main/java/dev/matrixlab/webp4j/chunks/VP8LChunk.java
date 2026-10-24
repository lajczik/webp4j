package dev.matrixlab.webp4j.chunks;

public class VP8LChunk extends BitstreamChunk {
    static final byte[] VP8L = new byte[]{'V', 'P', '8', 'L'};

    public VP8LChunk(byte[] data) {
        super(VP8L, data);
    }
}
