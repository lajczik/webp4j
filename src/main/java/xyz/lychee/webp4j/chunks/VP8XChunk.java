package xyz.lychee.webp4j.chunks;

import xyz.lychee.webp4j.model.WebPContainerFlag;

import java.io.IOException;
import java.io.OutputStream;
import java.util.EnumSet;

import static xyz.lychee.webp4j.utils.Util.checkUInt24;

/**
 * Extended container chunk (VP8X) defining canvas dimensions and feature flags.
 *
 * @author lajczik
 */
public class VP8XChunk extends Chunk {

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

    /**
     * Returns the set of active container flags for this VP8X chunk.
     *
     * @return EnumSet of active WebPContainerFlag instances
     */
    public EnumSet<WebPContainerFlag> getContainerFlags() {
        return WebPContainerFlag.fromMask(rsvILEXARReserved);
    }

    public boolean hasICCPFlag() {
        return WebPContainerFlag.ICC_PROFILE.isSet(rsvILEXARReserved);
    }

    public boolean hasALPHFlag() {
        return WebPContainerFlag.ALPHA.isSet(rsvILEXARReserved);
    }

    public boolean hasEXIFFlag() {
        return WebPContainerFlag.EXIF.isSet(rsvILEXARReserved);
    }

    public boolean hasXMPFlag() {
        return WebPContainerFlag.XMP.isSet(rsvILEXARReserved);
    }

    public boolean hasANIMFlag() {
        return WebPContainerFlag.ANIMATION.isSet(rsvILEXARReserved);
    }

    public boolean hasFlag(WebPContainerFlag flag) {
        return flag != null && flag.isSet(rsvILEXARReserved);
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
