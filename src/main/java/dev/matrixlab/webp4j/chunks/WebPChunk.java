package dev.matrixlab.webp4j.chunks;

public class WebPChunk extends RIFFChunk {
    static final byte[] WEBP = new byte[]{'W', 'E', 'B', 'P'};

    public WebPChunk(Chunk... subchunks) {
        super(WEBP, subchunks);
    }
}
