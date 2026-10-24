package dev.matrixlab.webp4j.chunks;

public class XMPChunk extends RawDataChunk {

    static final byte[] XMP = new byte[]{'X', 'M', 'P', ' '};

    public XMPChunk(byte[] data) {
        super(XMP, data);
    }

}
