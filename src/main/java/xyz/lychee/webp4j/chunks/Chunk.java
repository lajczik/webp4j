package xyz.lychee.webp4j.chunks;

import xyz.lychee.webp4j.utils.Util;
import lombok.Getter;

import java.io.IOException;
import java.io.OutputStream;

import static xyz.lychee.webp4j.utils.Util.*;

@Getter
public abstract class Chunk {
    private final byte[] fourCC;
    private final long size;
    private final int padding;

    public Chunk(byte[] fourCC, long size) {
        this.fourCC = fourCC;
        this.size = size;
        padding = isOdd(size) ? 1 : 0;
    }

    public long getFullSize() {
        return size + padding + 4 + 4;
    }

    protected void writeChunkHeader(OutputStream out) throws IOException {
        writeFourCC(out, fourCC);
        writeUInt32(out, size);
    }

    protected void writePadding(OutputStream out) throws IOException {
        Util.writePadding(out, padding);
    }

    public final void write(OutputStream out) throws IOException {
        writeChunkHeader(out);
        writePayload(out);
        writePadding(out);
    }

    public abstract void writePayload(OutputStream out) throws IOException;

}
