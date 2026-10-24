package dev.matrixlab.webp4j.chunks;

import lombok.Getter;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Getter
public class LISTChunk extends Chunk {
    private final List<Chunk> subchunks;

    public LISTChunk(byte[] fourCC, long extsize, Chunk... subchunks) {
        super(fourCC, sizeSum(subchunks) + extsize);
        this.subchunks = Collections.unmodifiableList(Arrays.asList(subchunks));
    }

    private static long sizeSum(Chunk... subchunks) {
        long size = 0;
        for (Chunk chunk : subchunks) {
            size += chunk.getFullSize();
        }
        return size;
    }

    protected void writeSubchunks(OutputStream out) throws IOException {
        for (Chunk chunk : subchunks) {
            chunk.write(out);
        }
    }

    @Override
    public void writePayload(OutputStream out) throws IOException {
        writeSubchunks(out);
    }

}
