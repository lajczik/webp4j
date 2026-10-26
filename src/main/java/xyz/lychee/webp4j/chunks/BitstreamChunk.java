package xyz.lychee.webp4j.chunks;

/**
 * Base chunk for image bitstreams (VP8 lossy or VP8L lossless).
 *
 * @author lajczik
 */
public class BitstreamChunk extends RawDataChunk {

    public BitstreamChunk(byte[] fourCC, byte[] data) {
        super(fourCC, data);
    }
}
