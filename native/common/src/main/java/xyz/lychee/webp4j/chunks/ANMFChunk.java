package xyz.lychee.webp4j.chunks;

import java.io.IOException;
import java.io.OutputStream;

import static xyz.lychee.webp4j.utils.Util.checkUInt24;

public class ANMFChunk extends LISTChunk {

    public static final int BLENDING_MASK = 1 << 1;
    public static final int DISPOSAL_METHOD_MASK = 1 << 0;
    public static final byte[] ANMF = new byte[]{'A', 'N', 'M', 'F'};
    private final int x, y, width, height, duration, reservedBD;

    public ANMFChunk(int x, int y, int width, int height, int duration, int flags, Chunk... subchunks) {
        super(ANMF, 3 + 3 + 3 + 3 + 3 + 1, subchunks);
        this.x = checkUInt24(x);
        this.y = checkUInt24(y);
        this.width = checkUInt24(width);
        this.height = checkUInt24(height);
        this.duration = checkUInt24(duration);
        this.reservedBD = flags;
    }

    public int getFrameX() {
        return x;
    }

    public int getFrameY() {
        return y;
    }

    public int getFrameWidth() {
        return width;
    }

    public int getFrameHeight() {
        return height;
    }

    public int getFrameDuration() {
        return duration;
    }

    public int getFlags() {
        return reservedBD;
    }

    public int getBlendingFlag() {
        return reservedBD & BLENDING_MASK;
    }

    public int getDisposalMethodFlag() {
        return reservedBD & DISPOSAL_METHOD_MASK;
    }

    @Override
    public void writePayload(OutputStream out) throws IOException {
        byte[] payload = new byte[16];
        payload[0] = (byte) (x & 0xFF);
        payload[1] = (byte) ((x >>> 8) & 0xFF);
        payload[2] = (byte) ((x >>> 16) & 0xFF);

        payload[3] = (byte) (y & 0xFF);
        payload[4] = (byte) ((y >>> 8) & 0xFF);
        payload[5] = (byte) ((y >>> 16) & 0xFF);

        int w1 = width - 1;
        payload[6] = (byte) (w1 & 0xFF);
        payload[7] = (byte) ((w1 >>> 8) & 0xFF);
        payload[8] = (byte) ((w1 >>> 16) & 0xFF);

        int h1 = height - 1;
        payload[9] = (byte) (h1 & 0xFF);
        payload[10] = (byte) ((h1 >>> 8) & 0xFF);
        payload[11] = (byte) ((h1 >>> 16) & 0xFF);

        payload[12] = (byte) (duration & 0xFF);
        payload[13] = (byte) ((duration >>> 8) & 0xFF);
        payload[14] = (byte) ((duration >>> 16) & 0xFF);

        payload[15] = (byte) (reservedBD & 0xFF);

        out.write(payload, 0, 16);
        writeSubchunks(out);
    }

}
