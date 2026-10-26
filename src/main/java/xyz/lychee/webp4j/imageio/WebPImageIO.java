package xyz.lychee.webp4j.imageio;

import javax.imageio.spi.IIORegistry;

/**
 * Utility for explicitly registering or ensuring registration of WebP ImageIO plugins.
 *
 * @author lajczik
 */
public final class WebPImageIO {

    private WebPImageIO() {
        throw new AssertionError("Cannot instantiate utility class.");
    }

    /**
     * Ensures that WebP ImageReader and ImageWriter service providers are registered
     * with the default ImageIO registry.
     */
    public static void register() {
        IIORegistry registry = IIORegistry.getDefaultInstance();
        registry.registerServiceProvider(new WebPImageReaderSpi());
        registry.registerServiceProvider(new WebPImageWriterSpi());
    }
}
