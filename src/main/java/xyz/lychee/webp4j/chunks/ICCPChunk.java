package xyz.lychee.webp4j.chunks;

/**
 * ICC color profile chunk (ICCP).
 *
 * @author lajczik
 */
public class ICCPChunk extends RawDataChunk {

    static final byte[] ICCP = new byte[]{'I', 'C', 'C', 'P'};

    public ICCPChunk(byte[] data) {
        super(ICCP, data);
    }

}
