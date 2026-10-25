package xyz.lychee.webp4j.chunks;

import java.io.IOException;
import java.io.OutputStream;

import static xyz.lychee.webp4j.utils.Util.checkUInt24;

public class VP8XChunk extends Chunk {
    public static final int ICCP_MASK = 1 << 5;
    public static final int ALPH_MASK = 1 << 4;
    public static final int EXIF_MASK = 1 << 3;
    public static final int XMP_MASK = 1 << 2;
    public static final int ANIM_MASK = 1 << 1;
    public static final int FLAG_ICCP = 1 << 5;
    public static final int FLAG_ALPH = 1 << 4;
    public static final int FLAG_EXIF = 1 << 3;
    public static final int FLAG_XMP = 1 << 2;
    public static final int FLAG_ANIM = 1 << 1;

    public static final byte[] VP8X = new byte[]{'V', 'P', '8', 'X'};
    private final int canvasWidth;
    private final int canvasHeight;
    private final int rsvILEXARReserved;

    public VP8XChunk(int flags, int canvasWidth, int canvasHeight) {
        super(VP8X, 4 + 3 + 3);
        this.canvasWidth = checkUInt24(canvasWidth);
        this.canvasHeight = checkUInt24(canvasHeight);
        this.rsvILEXARReserved = flags;
    }

    public int getCanvasWidth() {
        return canvasWidth;
    }

    public int getCanvasHeight() {
        return canvasHeight;
    }

    public int getFlags() {
        return rsvILEXARReserved;
    }

    public boolean hasICCPFlag() {
        return (rsvILEXARReserved & ICCP_MASK) != 0;
    }

    public boolean hasALPHFlag() {
        return (rsvILEXARReserved & ALPH_MASK) != 0;
    }

    public boolean hasEXIFFlag() {
        return (rsvILEXARReserved & EXIF_MASK) != 0;
    }

    public boolean hasXMPFlag() {
        return (rsvILEXARReserved & XMP_MASK) != 0;
    }

    public boolean hasANIMFlag() {
        return (rsvILEXARReserved & ANIM_MASK) != 0;
    }

    @Override
    public void writePayload(OutputStream out) throws IOException {
        byte[] payload = new byte[10];
        payload[0] = (byte) (rsvILEXARReserved & 0xFF);
        payload[1] = (byte) ((rsvILEXARReserved >>> 8) & 0xFF);
        payload[2] = (byte) ((rsvILEXARReserved >>> 16) & 0xFF);
        payload[3] = (byte) ((rsvILEXARReserved >>> 24) & 0xFF);
        int w1 = canvasWidth - 1;
        payload[4] = (byte) (w1 & 0xFF);
        payload[5] = (byte) ((w1 >>> 8) & 0xFF);
        payload[6] = (byte) ((w1 >>> 16) & 0xFF);
        int h1 = canvasHeight - 1;
        payload[7] = (byte) (h1 & 0xFF);
        payload[8] = (byte) ((h1 >>> 8) & 0xFF);
        payload[9] = (byte) ((h1 >>> 16) & 0xFF);
        out.write(payload, 0, 10);
    }

}
