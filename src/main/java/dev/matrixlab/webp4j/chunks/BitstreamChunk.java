package dev.matrixlab.webp4j.chunks;

public class BitstreamChunk extends RawDataChunk {

    public BitstreamChunk(byte[] fourCC, byte[] data) {
        super(fourCC, data);
    }
}
