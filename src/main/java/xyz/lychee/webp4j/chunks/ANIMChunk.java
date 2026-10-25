package xyz.lychee.webp4j.chunks;

import lombok.Getter;

import java.io.IOException;
import java.io.OutputStream;

import static xyz.lychee.webp4j.utils.Util.checkUInt16;

@Getter
public class ANIMChunk extends Chunk {
    public static final byte[] ANIM = new byte[]{'A', 'N', 'I', 'M'};

    private final int backgroundColor;
    private final int loopCount;

    public ANIMChunk(int backgroundColor, int loopCount) {
        super(ANIM, 4 + 2);
        this.backgroundColor = backgroundColor;
        this.loopCount = checkUInt16(loopCount);
    }

    @Override
    public void writePayload(OutputStream out) throws IOException {
        byte[] payload = new byte[6];
        payload[0] = (byte) (backgroundColor & 0xFF);
        payload[1] = (byte) ((backgroundColor >>> 8) & 0xFF);
        payload[2] = (byte) ((backgroundColor >>> 16) & 0xFF);
        payload[3] = (byte) ((backgroundColor >>> 24) & 0xFF);
        payload[4] = (byte) (loopCount & 0xFF);
        payload[5] = (byte) ((loopCount >>> 8) & 0xFF);
        out.write(payload, 0, 6);
    }

}
