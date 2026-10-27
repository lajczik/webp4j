package xyz.lychee.webp4j.imageio;

import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.spi.ImageWriterSpi;
import javax.imageio.stream.ImageOutputStream;
import java.io.OutputStream;
import java.util.Locale;

/**
 * Service Provider Interface for WebPImageWriter.
 *
 * @author lajczik
 */
public class WebPImageWriterSpi extends ImageWriterSpi {

    private static final String VENDOR_NAME = "xyz.lychee";
    private static final String VERSION = xyz.lychee.webp4j.WebPVersion.get();
    private static final String[] NAMES = {"webp", "WEBP", "WebP"};
    private static final String[] SUFFIXES = {"webp"};
    private static final String[] MIME_TYPES = {"image/webp", "image/x-webp"};
    private static final String WRITER_CLASS_NAME = "xyz.lychee.webp4j.imageio.WebPImageWriter";
    private static final Class<?>[] OUTPUT_TYPES = {ImageOutputStream.class, OutputStream.class};
    private static final String[] READER_SPI_NAMES = {"xyz.lychee.webp4j.imageio.WebPImageReaderSpi"};

    public WebPImageWriterSpi() {
        super(
                VENDOR_NAME,
                VERSION,
                NAMES,
                SUFFIXES,
                MIME_TYPES,
                WRITER_CLASS_NAME,
                OUTPUT_TYPES,
                READER_SPI_NAMES,
                false,
                null,
                null,
                null,
                null,
                false,
                null,
                null,
                null,
                null
        );
    }

    @Override
    public boolean canEncodeImage(ImageTypeSpecifier type) {
        return true;
    }

    @Override
    public String getDescription(Locale locale) {
        return "Google WebP Image Writer";
    }

    @Override
    public ImageWriter createWriterInstance(Object extension) {
        return new WebPImageWriter(this);
    }
}
