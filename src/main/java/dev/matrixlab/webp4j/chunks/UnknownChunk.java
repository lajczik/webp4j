package dev.matrixlab.webp4j.chunks;

public class UnknownChunk extends RawDataChunk {

    public UnknownChunk(byte[] fourCC, byte[] data) {
        super(fourCC, data);
    }

}
