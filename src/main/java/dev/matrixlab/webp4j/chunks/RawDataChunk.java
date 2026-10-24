package dev.matrixlab.webp4j.chunks;

import lombok.Getter;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

@Getter
public class RawDataChunk extends Chunk {
    private final byte[] rawData;

    public RawDataChunk(byte[] fourCC, byte[] data) {
        super(fourCC, data.length);
        this.rawData = Objects.requireNonNull(data);
    }

    protected void writeRawData(OutputStream out) throws IOException {
        out.write(rawData);
    }

    @Override
    public void writePayload(OutputStream out) throws IOException {
        writeRawData(out);
    }

}
