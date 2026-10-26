package xyz.lychee.webp4j.chunks;

import xyz.lychee.webp4j.model.WebPContainerFlag;

import java.io.IOException;
import java.io.OutputStream;

import static xyz.lychee.webp4j.utils.Util.checkUInt24;

public class VP8XChunk extends Chunk {
    public static final int ICCP_MASK = WebPContainerFlag.ICC_PROFILE.getMask();
    public static final int ALPH_MASK = WebPContainerFlag.ALPHA.getMask();
    public static final int EXIF_MASK = WebPContainerFlag.EXIF.getMask();
    public static final int XMP_MASK = WebPContainerFlag.XMP.getMask();
    public static final int ANIM_MASK = WebPContainerFlag.ANIMATION.getMask();
    public static final int FLAG_ICCP = WebPContainerFlag.ICC_PROFILE.getMask();
    public static final int FLAG_ALPH = WebPContainerFlag.ALPHA.getMask();
    public static final int FLAG_EXIF = WebPContainerFlag.EXIF.getMask();
    public static final int FLAG_XMP = WebPContainerFlag.XMP.getMask();
    public static final int FLAG_ANIM = WebPContainerFlag.ANIMATION.getMask();

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

    public VP8XChunk(int canvasWidth, int canvasHeight, WebPContainerFlag... flags) {
        this(WebPContainerFlag.toMask(flags), canvasWidth, canvasHeight);
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

    public boolean hasFlag(WebPContainerFlag flag) {
        return WebPContainerFlag.hasFlag(rsvILEXARReserved, flag);
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
