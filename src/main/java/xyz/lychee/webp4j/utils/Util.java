package xyz.lychee.webp4j.utils;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

/**
 * General bitstream and I/O utility methods for WebP chunk and data manipulation.
 *
 * @author lajczik
 */
public final class Util {

    public static final long UINT32_MAX = 4294967295L; // 2 ^ 32 - 1
    public static final int UINT24_MAX = 16777215;    // 2 ^ 24 - 1
    public static final int UINT16_MAX = 65535;       // 2 ^ 16 - 1;
    public static final BigInteger BI_UINT32_MAX = BigInteger.valueOf(UINT32_MAX);
    private static final byte[] ZERO_PADDING = new byte[64];

    private Util() {
        throw new UnsupportedOperationException();
    }

    public static boolean arrayEquals(byte[] a, byte[] b) {
        return Arrays.equals(a, b);
    }

    public static int checkUInt16(int arg) {
        return checkArgRange(arg, 0, UINT16_MAX, "uint16");
    }

    public static int checkUInt24(int arg) {
        return checkArgRange(arg, 0, UINT24_MAX, "uint24");
    }

    public static long checkUInt32(long arg) {
        return checkArgRange(arg, 0, UINT32_MAX, "uint32");
    }

    public static BigInteger checkUInt32(BigInteger arg) {
        return checkArgRange(arg, BigInteger.ZERO, BI_UINT32_MAX, "uint32");
    }

    private static long checkArgRange(long arg, long min, long max, String msg) {
        if (arg < min) throw new IllegalArgumentException(msg + " < " + min);
        else if (arg > max) throw new IllegalArgumentException(msg + " > " + max);
        return arg;
    }

    private static int checkArgRange(int arg, int min, int max, String msg) {
        if (arg < min) throw new IllegalArgumentException(msg + " < " + min);
        else if (arg > max) throw new IllegalArgumentException(msg + " > " + max);
        return arg;
    }

    private static BigInteger checkArgRange(BigInteger arg, BigInteger min, BigInteger max, String msg) {
        Objects.requireNonNull(arg);
        Objects.requireNonNull(min);
        Objects.requireNonNull(max);
        Objects.requireNonNull(msg);
        if (arg.compareTo(min) < 0) throw new IllegalArgumentException(msg + " < " + min);
        else if (arg.compareTo(max) > 0) throw new IllegalArgumentException(msg + " > " + max);
        return arg;
    }

    public static void writePadding(OutputStream out, int payload) throws IOException {
        while (payload > 0) {
            int chunk = Math.min(payload, ZERO_PADDING.length);
            out.write(ZERO_PADDING, 0, chunk);
            payload -= chunk;
        }
    }

    public static void writePadding(OutputStream out, long payload) throws IOException {
        while (payload > 0) {
            int chunk = (int) Math.min(payload, (long) ZERO_PADDING.length);
            out.write(ZERO_PADDING, 0, chunk);
            payload -= chunk;
        }
    }

    public static int readInt8(InputStream in) throws IOException {
        int b = in.read();
        if (b == -1) throw new EOFException();
        return b;
    }

    public static int readUInt8(InputStream in) throws IOException {
        return readInt8(in);
    }

    public static void writeInt8(OutputStream out, int val) throws IOException {
        out.write((byte) val);
    }

    public static void writeUInt8(OutputStream out, int val) throws IOException {
        writeInt8(out, val);
    }

    public static int readInt16(InputStream in) throws IOException {
        int b0 = in.read();
        int b1 = in.read();
        if ((b0 | b1) < 0) throw new EOFException();
        return (b1 << 8) | b0;
    }

    public static int readUInt16(InputStream in) throws IOException {
        return readInt16(in);
    }

    public static void writeInt16(OutputStream out, int val) throws IOException {
        out.write(val & 0xFF);
        out.write((val >>> 8) & 0xFF);
    }

    public static void writeUInt16(OutputStream out, int val) throws IOException {
        writeInt16(out, val);
    }

    public static int readInt24(InputStream in) throws IOException {
        int b0 = in.read();
        int b1 = in.read();
        int b2 = in.read();
        if ((b0 | b1 | b2) < 0) throw new EOFException();
        return (b2 << 16) | (b1 << 8) | b0;
    }

    public static int readUInt24(InputStream in) throws IOException {
        return readInt24(in);
    }

    public static void writeInt24(OutputStream out, int val) throws IOException {
        out.write(val & 0xFF);
        out.write((val >>> 8) & 0xFF);
        out.write((val >>> 16) & 0xFF);
    }

    public static void writeUInt24(OutputStream out, int val) throws IOException {
        writeInt24(out, val);
    }

    public static int readInt32(InputStream in) throws IOException {
        int b0 = in.read();
        int b1 = in.read();
        int b2 = in.read();
        int b3 = in.read();
        if ((b0 | b1 | b2 | b3) < 0) throw new EOFException();
        return (b3 << 24) | (b2 << 16) | (b1 << 8) | b0;
    }

    public static int toInt32(byte[] buf) {
        return toInt32(buf, 0);
    }

    public static int toInt32(byte[] buf, int offset) {
        return (((buf[offset + 3] & 0xFF) << 24) |
                ((buf[offset + 2] & 0xFF) << 16) |
                ((buf[offset + 1] & 0xFF) << 8) |
                ((buf[offset] & 0xFF)));
    }

    public static long toUInt32(int val) {
        return ((long) val) & 0xFFFFFFFFL;
    }

    public static long toUInt32(byte[] buf) {
        return toUInt32(toInt32(buf, 0));
    }

    public static long toUInt32(byte[] buf, int offset) {
        return toUInt32(toInt32(buf, offset));
    }

    public static int toUInt24(byte[] buf, int offset) {
        return (((buf[offset + 2] & 0xFF) << 16) |
                ((buf[offset + 1] & 0xFF) << 8) |
                ((buf[offset] & 0xFF)));
    }

    public static int toUInt16(byte[] buf, int offset) {
        return (((buf[offset + 1] & 0xFF) << 8) |
                ((buf[offset] & 0xFF)));
    }

    public static long readUInt32(InputStream in) throws IOException {
        return toUInt32(readInt32(in));
    }

    public static void writeInt32(OutputStream out, long val) throws IOException {
        out.write((int) (val & 0xFF));
        out.write((int) ((val >>> 8) & 0xFF));
        out.write((int) ((val >>> 16) & 0xFF));
        out.write((int) ((val >>> 24) & 0xFF));
    }

    public static void writeUInt32(OutputStream out, long val) throws IOException {
        writeInt32(out, val);
    }

    public static int read1Based(InputStream in) throws IOException {
        return readUInt24(in) + 1;
    }

    public static void write1Based(OutputStream out, int i) throws IOException {
        checkUInt24(i);
        writeUInt24(out, i - 1);
    }

    public static byte[] readFourCC(InputStream in) throws IOException {
        byte[] buf = new byte[4];
        if (readNBytes(in, buf, 0, 4) != 4) throw new EOFException();
        return buf;
    }

    public static void readFourCC(InputStream in, byte[] buf) throws IOException {
        if (Objects.requireNonNull(buf).length != 4) throw new IllegalArgumentException("buf length must be 4");
        if (readNBytes(in, buf, 0, 4) != 4) throw new EOFException();
    }

    public static void writeFourCC(OutputStream out, byte[] fourCC) throws IOException {
        out.write(fourCC);
    }

    public static void writeFourCC(OutputStream out, byte c1, byte c2, byte c3, byte c4) throws IOException {
        out.write(c1);
        out.write(c2);
        out.write(c3);
        out.write(c4);
    }

    public static byte[] readNBytes(InputStream in, int len) throws IOException {
        byte[] buf = new byte[len];
        if (readNBytes(in, buf, 0, len) != len) throw new EOFException();
        return buf;
    }

    public static int readNBytes(InputStream in, byte[] b, int off, int len) throws IOException {
        Objects.requireNonNull(in);
        int n = 0;
        while (n < len) {
            int count = in.read(b, off + n, len - n);
            if (count < 0)
                break;
            n += count;
        }
        return n;
    }

    public static boolean isOdd(long num) {
        return (num & 1) == 1;
    }

    public static boolean isOdd(int num) {
        return (num & 1) == 1;
    }

    public static void skip1Byte(InputStream in) throws IOException {
        skipNBytes(in, 1);
    }

    public static void skipNBytes(InputStream in, long n) throws IOException {
        Objects.requireNonNull(in);
        while (n > 0) {
            long ns = in.skip(n);
            if (ns > 0 && ns <= n) {
                n -= ns;
            } else if (ns == 0) {
                if (in.read() == -1) {
                    throw new EOFException();
                }
                n--;
            } else throw new IOException("Unable to skip exactly");
        }
    }

    /**
     * Reads all bytes from an InputStream into a byte array.
     *
     * @param in Input stream to read from
     * @return Byte array containing all read bytes
     * @throws IOException If reading fails
     */
    public static byte[] readAllBytes(InputStream in) throws IOException {
        Objects.requireNonNull(in, "InputStream cannot be null");
        final long max = Math.min(WebPLimits.getMaxInputBytes(), Integer.MAX_VALUE - 8L);
        byte[] buf = new byte[(int) Math.min(max, Math.max(16384, in.available()))];
        int len = 0;
        while (true) {
            if (len == buf.length) {
                // Buffer full: grow geometrically, or detect that the input exceeds the limit.
                if (len >= max) {
                    if (in.read() != -1) {
                        throw new IOException("Input exceeds the limit of " + max
                                + " bytes (see WebPLimits.setMaxInputBytes)");
                    }
                    break;
                }
                buf = Arrays.copyOf(buf, (int) Math.min(max, (long) len * 2));
            }
            int n = in.read(buf, len, buf.length - len);
            if (n < 0) {
                break;
            }
            len += n;
        }
        return len == buf.length ? buf : Arrays.copyOf(buf, len);
    }

    /**
     * Reads all bytes from a File into a byte array.
     *
     * @param file File to read from
     * @return Byte array containing all read bytes
     * @throws IOException If file cannot be read
     */
    public static byte[] readFileBytes(File file) throws IOException {
        Objects.requireNonNull(file, "File cannot be null");
        return readFileBytes(file.toPath());
    }

    /**
     * Reads all bytes from a Path into a byte array.
     *
     * @param path Path to read from
     * @return Byte array containing all read bytes
     * @throws IOException If path cannot be read
     */
    public static byte[] readFileBytes(Path path) throws IOException {
        Objects.requireNonNull(path, "Path cannot be null");
        WebPLimits.checkInputSize(Files.size(path));
        return Files.readAllBytes(path);
    }

    /**
     * Writes all bytes to a File.
     *
     * @param file Target file
     * @param data Bytes to write
     * @throws IOException If writing fails
     */
    public static void writeBytes(File file, byte[] data) throws IOException {
        Objects.requireNonNull(file, "File cannot be null");
        Objects.requireNonNull(data, "Data cannot be null");
        writeBytes(file.toPath(), data);
    }

    /**
     * Writes all bytes to a Path.
     *
     * @param path Target path
     * @param data Bytes to write
     * @throws IOException If writing fails
     */
    public static void writeBytes(Path path, byte[] data) throws IOException {
        Objects.requireNonNull(path, "Path cannot be null");
        Objects.requireNonNull(data, "Data cannot be null");
        Files.write(path, data);
    }

    /**
     * Extracts a byte array from a ByteBuffer.
     * If the buffer is backed by an array and spans the entire array, returns the array directly.
     * Otherwise, copies the remaining bytes from the buffer.
     *
     * @param buffer Byte buffer to extract from
     * @return Byte array of remaining bytes in the buffer
     */
    public static byte[] toByteArray(ByteBuffer buffer) {
        Objects.requireNonNull(buffer, "ByteBuffer cannot be null");
        if (buffer.hasArray() && buffer.arrayOffset() == 0 && buffer.position() == 0
                && buffer.remaining() == buffer.capacity()) {
            return buffer.array();
        }
        byte[] bytes = new byte[buffer.remaining()];
        int originalPos = buffer.position();
        buffer.get(bytes);
        buffer.position(originalPos);
        return bytes;
    }
}
