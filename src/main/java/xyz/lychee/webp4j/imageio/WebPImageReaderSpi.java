package xyz.lychee.webp4j.imageio;

import javax.imageio.ImageReader;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.util.Locale;

/**
 * Service Provider Interface for WebPImageReader.
 *
 * @author lajczik
 */
public class WebPImageReaderSpi extends ImageReaderSpi {

    private static final String VENDOR_NAME = "xyz.lychee";
    private static final String VERSION = "2.5.0";
    private static final String[] NAMES = {"webp", "WEBP", "WebP"};
    private static final String[] SUFFIXES = {"webp"};
    private static final String[] MIME_TYPES = {"image/webp", "image/x-webp"};
    private static final String READER_CLASS_NAME = "xyz.lychee.webp4j.imageio.WebPImageReader";
    private static final Class<?>[] INPUT_TYPES = {ImageInputStream.class};
    private static final String[] WRITER_SPI_NAMES = {"xyz.lychee.webp4j.imageio.WebPImageWriterSpi"};

    public WebPImageReaderSpi() {
        super(
                VENDOR_NAME,
                VERSION,
                NAMES,
                SUFFIXES,
                MIME_TYPES,
                READER_CLASS_NAME,
                INPUT_TYPES,
                WRITER_SPI_NAMES,
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
    public boolean canDecodeInput(Object input) throws IOException {
        if (!(input instanceof ImageInputStream)) {
            return false;
        }
        ImageInputStream stream = (ImageInputStream) input;
        stream.mark();
        try {
            byte[] signature = new byte[12];
            int read = stream.read(signature);
            if (read < 12) {
                return false;
            }
            return signature[0] == 'R' && signature[1] == 'I' && signature[2] == 'F' && signature[3] == 'F'
                    && signature[8] == 'W' && signature[9] == 'E' && signature[10] == 'B' && signature[11] == 'P';
        } finally {
            stream.reset();
        }
    }

    @Override
    public String getDescription(Locale locale) {
        return "Google WebP Image Reader";
    }

    @Override
    public ImageReader createReaderInstance(Object extension) {
        return new WebPImageReader(this);
    }
}
