package xyz.lychee.webp4j.chunks;

/**
 * XMP metadata chunk (XMP).
 *
 * @author lajczik
 */
public class XMPChunk extends RawDataChunk {

    static final byte[] XMP = new byte[]{'X', 'M', 'P', ' '};

    public XMPChunk(byte[] data) {
        super(XMP, data);
    }

}
