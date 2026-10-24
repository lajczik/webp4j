package dev.matrixlab.webp4j.chunks;

import java.io.IOException;
import java.io.OutputStream;

import static dev.matrixlab.webp4j.utils.Util.writeFourCC;

public class RIFFChunk extends LISTChunk {

    static final byte[] RIFF = new byte[]{'R', 'I', 'F', 'F'};

    private final byte[] fourCC;

    public RIFFChunk(byte[] fourCC, Chunk... subchunks) {
        super(RIFF, 4, subchunks);
        this.fourCC = fourCC;
    }

    public byte[] getTag() {
        return fourCC.clone();
    }

    @Override
    public void writePayload(OutputStream out) throws IOException {
        writeFourCC(out, fourCC);
        writeSubchunks(out);
    }

}
