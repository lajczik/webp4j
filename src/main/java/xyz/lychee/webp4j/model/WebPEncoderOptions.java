package xyz.lychee.webp4j.model;

/**
 * Fine-grained options for encoding a static WebP image, mapped one-to-one onto libwebp's
 * {@code WebPConfig}.
 * <p>
 * Instances are created with {@link #lossy(float)} or {@link #lossless()} and tuned with the
 * chained setters:
 * <pre>{@code
 * WebPEncoderOptions options = WebPEncoderOptions.lossless()
 *         .setMethod(6)          // slowest, smallest output
 *         .setQuality(100);      // for lossless: compression effort 0..100
 * byte[] webp = WebPEncoder.encode(image, options);
 * }</pre>
 *
 * @author lajczik
 */
public final class WebPEncoderOptions {

    /** Effort used by libwebp's simple lossless API. */
    static final float SIMPLE_LOSSLESS_EFFORT = 70f;

    private float quality = 75f;
    private boolean lossless;
    private int method = 4;
    private int nearLossless = 100;
    private boolean exact;
    private int alphaQuality = 100;
    private boolean sharpYuv;
    private int targetSize;
    private boolean multiThreaded = true;
    private byte[] exif;
    private byte[] iccp;
    private String xmp;

    private WebPEncoderOptions() {
    }

    /** Lossy options with the given quality (0 = smallest file, 100 = best quality). */
    public static WebPEncoderOptions lossy(float quality) {
        return new WebPEncoderOptions().setQuality(quality);
    }

    /** Lossless options with libwebp's default effort. */
    public static WebPEncoderOptions lossless() {
        WebPEncoderOptions o = new WebPEncoderOptions();
        o.lossless = true;
        o.quality = SIMPLE_LOSSLESS_EFFORT;
        return o;
    }

    /** Lossless options with explicit compression effort (0 = fast, 100 = smallest). */
    public static WebPEncoderOptions lossless(int effort) {
        return lossless().setQuality(effort);
    }

    /**
     * Quality for lossy encoding (0..100). For lossless encoding it is the compression effort
     * (0 = fast, 100 = slowest and smallest).
     */
    public WebPEncoderOptions setQuality(float quality) {
        if (Float.isNaN(quality) || quality < 0f || quality > 100f) {
            throw new IllegalArgumentException("Quality must be between 0 and 100, but was " + quality);
        }
        this.quality = quality;
        return this;
    }

    /** Selects lossless (VP8L) or lossy (VP8) compression. */
    public WebPEncoderOptions setLossless(boolean lossless) {
        this.lossless = lossless;
        return this;
    }

    /** Speed/size trade-off: 0 (fast) .. 6 (slow, smaller). Default 4. */
    public WebPEncoderOptions setMethod(int method) {
        if (method < 0 || method > 6) {
            throw new IllegalArgumentException("Method must be between 0 and 6, but was " + method);
        }
        this.method = method;
        return this;
    }

    /** Near-lossless preprocessing: 100 = off (default), 0 = maximum preprocessing. Lossless only. */
    public WebPEncoderOptions setNearLossless(int nearLossless) {
        if (nearLossless < 0 || nearLossless > 100) {
            throw new IllegalArgumentException("Near-lossless must be between 0 and 100, but was " + nearLossless);
        }
        this.nearLossless = nearLossless;
        return this;
    }

    /** Preserves the RGB values of fully transparent pixels instead of optimizing them away. */
    public WebPEncoderOptions setExact(boolean exact) {
        this.exact = exact;
        return this;
    }

    /** Quality of the alpha channel for lossy images (0..100, default 100 = lossless alpha). */
    public WebPEncoderOptions setAlphaQuality(int alphaQuality) {
        if (alphaQuality < 0 || alphaQuality > 100) {
            throw new IllegalArgumentException("Alpha quality must be between 0 and 100, but was " + alphaQuality);
        }
        this.alphaQuality = alphaQuality;
        return this;
    }

    /** Uses slower but sharper RGB to YUV conversion for lossy images. */
    public WebPEncoderOptions setSharpYuv(boolean sharpYuv) {
        this.sharpYuv = sharpYuv;
        return this;
    }

    /** Target size of the output in bytes for lossy images; 0 (default) disables size targeting. */
    public WebPEncoderOptions setTargetSize(int targetSize) {
        if (targetSize < 0) {
            throw new IllegalArgumentException("Target size must not be negative: " + targetSize);
        }
        this.targetSize = targetSize;
        return this;
    }

    /** Lets libwebp use several threads for one image (default true). */
    public WebPEncoderOptions setMultiThreaded(boolean multiThreaded) {
        this.multiThreaded = multiThreaded;
        return this;
    }

    public float getQuality() {
        return quality;
    }

    public boolean isLossless() {
        return lossless;
    }

    public int getMethod() {
        return method;
    }

    public int getNearLossless() {
        return nearLossless;
    }

    public boolean isExact() {
        return exact;
    }

    public int getAlphaQuality() {
        return alphaQuality;
    }

    public boolean isSharpYuv() {
        return sharpYuv;
    }

    public int getTargetSize() {
        return targetSize;
    }

    public boolean isMultiThreaded() {
        return multiThreaded;
    }

    /** Sets raw EXIF metadata bytes to be embedded in the container. */
    public WebPEncoderOptions setExif(byte[] exif) {
        this.exif = exif != null ? exif.clone() : null;
        return this;
    }

    /** Returns a defensive copy of the configured EXIF metadata bytes, or {@code null}. */
    public byte[] getExif() {
        return exif != null ? exif.clone() : null;
    }

    /** Sets raw ICC color profile bytes to be embedded in the container. */
    public WebPEncoderOptions setIccProfile(byte[] iccp) {
        this.iccp = iccp != null ? iccp.clone() : null;
        return this;
    }

    /** Returns a defensive copy of the configured ICC profile bytes, or {@code null}. */
    public byte[] getIccProfile() {
        return iccp != null ? iccp.clone() : null;
    }

    /** Sets XMP metadata XML string to be embedded in the container. */
    public WebPEncoderOptions setXmp(String xmp) {
        this.xmp = xmp;
        return this;
    }

    /** Returns the configured XMP metadata string, or {@code null}. */
    public String getXmp() {
        return xmp;
    }

    /** True if any container metadata (EXIF, ICC profile, or XMP) is configured. */
    public boolean hasMetadata() {
        return (exif != null && exif.length > 0)
                || (iccp != null && iccp.length > 0)
                || (xmp != null && !xmp.isEmpty());
    }

    /** Creates an independent copy of these options. */
    public WebPEncoderOptions copy() {
        WebPEncoderOptions o = new WebPEncoderOptions();
        o.quality = this.quality;
        o.lossless = this.lossless;
        o.method = this.method;
        o.nearLossless = this.nearLossless;
        o.exact = this.exact;
        o.alphaQuality = this.alphaQuality;
        o.sharpYuv = this.sharpYuv;
        o.targetSize = this.targetSize;
        o.multiThreaded = this.multiThreaded;
        o.exif = this.exif != null ? this.exif.clone() : null;
        o.iccp = this.iccp != null ? this.iccp.clone() : null;
        o.xmp = this.xmp;
        return o;
    }

    /**
     * True when the options differ from what the basic quality/lossless encoder already does, so
     * the extended native entry point is required.
     */
    public boolean requiresAdvancedEncoder() {
        return method != 4 || nearLossless != 100 || exact || alphaQuality != 100 || sharpYuv || targetSize != 0
                || (lossless && quality != SIMPLE_LOSSLESS_EFFORT);
    }
}
