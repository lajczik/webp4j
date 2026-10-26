package xyz.lychee.webp4j.imageio;

import javax.imageio.ImageWriteParam;
import java.util.Locale;

/**
 * ImageWriteParam implementation for configuring WebP compression quality and lossless mode.
 *
 * @author lajczik
 */
public class WebPImageWriteParam extends ImageWriteParam {

    public static final String COMPRESSION_TYPE_LOSSY = "Lossy";
    public static final String COMPRESSION_TYPE_LOSSLESS = "Lossless";

    private static final String[] COMPRESSION_TYPES = {
            COMPRESSION_TYPE_LOSSY,
            COMPRESSION_TYPE_LOSSLESS
    };

    public WebPImageWriteParam(Locale locale) {
        super(locale);
        this.canWriteCompressed = true;
        this.compressionTypes = COMPRESSION_TYPES;
        this.compressionType = COMPRESSION_TYPE_LOSSY;
        this.compressionMode = MODE_EXPLICIT;
        this.compressionQuality = 0.75f;
    }

    /**
     * Checks if lossless WebP compression is selected.
     *
     * @return true if lossless, false if lossy
     */
    public boolean isLossless() {
        return COMPRESSION_TYPE_LOSSLESS.equalsIgnoreCase(getCompressionType());
    }

    /**
     * Toggles between lossless and lossy WebP compression.
     *
     * @param lossless true for lossless (VP8L), false for lossy (VP8)
     */
    public void setLossless(boolean lossless) {
        setCompressionType(lossless ? COMPRESSION_TYPE_LOSSLESS : COMPRESSION_TYPE_LOSSY);
    }
}
