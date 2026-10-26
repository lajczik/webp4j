package xyz.lychee.webp4j.chunks;

/**
 * Top-level WebP container chunk wrapping subchunks into a RIFF-WEBP container.
 *
 * @author lajczik
 */
public class WebPChunk extends RIFFChunk {
    static final byte[] WEBP = new byte[]{'W', 'E', 'B', 'P'};

    public WebPChunk(Chunk... subchunks) {
        super(WEBP, subchunks);
    }
}
