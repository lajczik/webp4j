package xyz.lychee.webp4j.chunks;

import xyz.lychee.webp4j.utils.Util;
import lombok.Getter;

import java.io.IOException;
import java.io.OutputStream;

import static xyz.lychee.webp4j.utils.Util.isOdd;

/**
 * Base abstract class representing a WebP RIFF chunk.
 *
 * @author lajczik
 */
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
        byte[] header = new byte[8];
        header[0] = fourCC[0];
        header[1] = fourCC[1];
        header[2] = fourCC[2];
        header[3] = fourCC[3];
        header[4] = (byte) (size & 0xFF);
        header[5] = (byte) ((size >>> 8) & 0xFF);
        header[6] = (byte) ((size >>> 16) & 0xFF);
        header[7] = (byte) ((size >>> 24) & 0xFF);
        out.write(header, 0, 8);
    }

    protected void writePadding(OutputStream out) throws IOException {
        if (padding > 0) {
            out.write(0);
        }
    }

    public final void write(OutputStream out) throws IOException {
        writeChunkHeader(out);
        writePayload(out);
        writePadding(out);
    }

    public abstract void writePayload(OutputStream out) throws IOException;

}
