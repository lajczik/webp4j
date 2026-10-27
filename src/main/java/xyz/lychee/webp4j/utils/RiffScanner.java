package xyz.lychee.webp4j.utils;

import java.io.IOException;

/**
 * Allocation-free, overflow-safe forward iterator over the chunks of a RIFF/WebP container.
 * <p>
 * All arithmetic is done in {@code long}; every call to {@link #next()} advances by at least
 * 8 bytes, so a hostile chunk size can never make a scan loop forever or step backwards. A chunk
 * whose declared size reaches past the end of the container is clamped to the available bytes and
 * ends the scan (truncated files stay inspectable).
 *
 * @author lajczik
 */
public final class RiffScanner {

    public static final int VP8X = fourCC('V', 'P', '8', 'X');
    public static final int VP8 = fourCC('V', 'P', '8', ' ');
    public static final int VP8L = fourCC('V', 'P', '8', 'L');
    public static final int ALPH = fourCC('A', 'L', 'P', 'H');
    public static final int ANIM = fourCC('A', 'N', 'I', 'M');
    public static final int ANMF = fourCC('A', 'N', 'M', 'F');
    public static final int ICCP = fourCC('I', 'C', 'C', 'P');
    public static final int EXIF = fourCC('E', 'X', 'I', 'F');
    public static final int XMP = fourCC('X', 'M', 'P', ' ');

    private final byte[] data;
    private final long end;
    private long nextOffset;

    private int id;
    private int payloadOffset;
    private int payloadLength;
    private long declaredSize;

    private RiffScanner(byte[] data, long start, long end) {
        this.data = data;
        this.nextOffset = start;
        this.end = end;
    }

    /**
     * Packs four ASCII characters into the little-endian integer used by {@link #fourCC()}.
     */
    public static int fourCC(char a, char b, char c, char d) {
        return a | (b << 8) | (c << 16) | (d << 24);
    }

    /**
     * Opens a scanner over the chunks of a complete WebP file ("RIFF" ... "WEBP" header required).
     * The scan range is limited by the RIFF size field and by the array length.
     *
     * @throws IOException if the RIFF/WEBP signature is missing
     */
    public static RiffScanner open(byte[] webp) throws IOException {
        if (webp == null || webp.length < 12) {
            throw new IOException("Invalid WebP data: shorter than the 12 byte RIFF header");
        }
        if (webp[0] != 'R' || webp[1] != 'I' || webp[2] != 'F' || webp[3] != 'F') {
            throw new IOException("Invalid WebP data: missing RIFF signature");
        }
        if (webp[8] != 'W' || webp[9] != 'E' || webp[10] != 'B' || webp[11] != 'P') {
            throw new IOException("Invalid WebP data: missing WEBP signature");
        }
        long riffEnd = 8L + Util.toUInt32(webp, 4);
        long end = Math.min(webp.length, Math.max(12L, riffEnd));
        return new RiffScanner(webp, 12, end);
    }

    /**
     * Opens a scanner over a sub-range that contains only chunks (for example the payload of ANMF
     * after its 16 byte header).
     */
    public static RiffScanner slice(byte[] data, int start, int end) {
        if (start < 0 || end > data.length || start > end) {
            throw new IllegalArgumentException("Invalid range " + start + ".." + end + " for " + data.length + " bytes");
        }
        return new RiffScanner(data, start, end);
    }

    /**
     * Advances to the next chunk.
     *
     * @return false when no further complete chunk header is available
     */
    public boolean next() {
        long off = nextOffset;
        if (off + 8 > end) {
            return false;
        }
        int o = (int) off;
        id = (data[o] & 0xFF) | ((data[o + 1] & 0xFF) << 8) | ((data[o + 2] & 0xFF) << 16) | ((data[o + 3] & 0xFF) << 24);
        declaredSize = Util.toUInt32(data, o + 4);
        long payload = off + 8;
        payloadOffset = (int) payload;
        payloadLength = (int) Math.min(declaredSize, end - payload);
        nextOffset = payload + declaredSize + (declaredSize & 1L);
        return true;
    }

    /** FourCC of the current chunk, comparable with the constants of this class. */
    public int fourCC() {
        return id;
    }

    /** Offset of the first payload byte of the current chunk. */
    public int payloadOffset() {
        return payloadOffset;
    }

    /** Number of payload bytes actually available (clamped to the container). */
    public int payloadLength() {
        return payloadLength;
    }

    /** Payload size as declared in the chunk header (may exceed {@link #payloadLength()}). */
    public long declaredSize() {
        return declaredSize;
    }

    /** Offset of the 8 byte header of the current chunk. */
    public int headerOffset() {
        return payloadOffset - 8;
    }

    /** Offset just after the payload of the current chunk (excluding the padding byte). */
    public int payloadEnd() {
        return payloadOffset + payloadLength;
    }
}
