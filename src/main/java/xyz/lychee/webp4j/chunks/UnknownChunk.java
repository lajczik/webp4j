package xyz.lychee.webp4j.chunks;

/**
 * Fallback chunk preserving unknown RIFF fourCC headers and payload bytes.
 *
 * @author lajczik
 */
public class UnknownChunk extends RawDataChunk {

    public UnknownChunk(byte[] fourCC, byte[] data) {
        super(fourCC, data);
    }

}
