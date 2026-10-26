package xyz.lychee.webp4j.imageio;

import xyz.lychee.webp4j.codec.WebPEncoder;
import xyz.lychee.webp4j.utils.ImageHelper;

import javax.imageio.IIOImage;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.spi.ImageWriterSpi;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.io.OutputStream;

/**
 * ImageWriter implementation for writing WebP images using libwebp.
 *
 * @author lajczik
 */
public class WebPImageWriter extends ImageWriter {

    public WebPImageWriter(ImageWriterSpi originatingProvider) {
        super(originatingProvider);
    }

    @Override
    public ImageWriteParam getDefaultWriteParam() {
        return new WebPImageWriteParam(getLocale());
    }

    @Override
    public IIOMetadata getDefaultStreamMetadata(ImageWriteParam param) {
        return null;
    }

    @Override
    public IIOMetadata getDefaultImageMetadata(ImageTypeSpecifier imageType, ImageWriteParam param) {
        return null;
    }

    @Override
    public IIOMetadata convertStreamMetadata(IIOMetadata inData, ImageWriteParam param) {
        return null;
    }

    @Override
    public IIOMetadata convertImageMetadata(IIOMetadata inData, ImageTypeSpecifier imageType, ImageWriteParam param) {
        return null;
    }

    @Override
    public void write(IIOMetadata streamMetadata, IIOImage iioImage, ImageWriteParam param) throws IOException {
        if (output == null) {
            throw new IllegalStateException("Output has not been set");
        }
        if (iioImage == null) {
            throw new IllegalArgumentException("iioImage cannot be null");
        }

        clearAbortRequest();
        processImageStarted(0);

        RenderedImage renderedImage = iioImage.getRenderedImage();
        BufferedImage image = ImageHelper.toBufferedImage(renderedImage);

        float quality = 75.0f;
        boolean lossless = false;

        if (param instanceof WebPImageWriteParam) {
            WebPImageWriteParam webpParam = (WebPImageWriteParam) param;
            lossless = webpParam.isLossless();
            if (!lossless && webpParam.getCompressionMode() == ImageWriteParam.MODE_EXPLICIT) {
                quality = webpParam.getCompressionQuality() * 100.0f;
            }
        } else if (param != null && param.getCompressionMode() == ImageWriteParam.MODE_EXPLICIT) {
            quality = param.getCompressionQuality() * 100.0f;
        }

        byte[] encoded = WebPEncoder.encode(image, quality, lossless);

        if (output instanceof ImageOutputStream) {
            ((ImageOutputStream) output).write(encoded);
        } else if (output instanceof OutputStream) {
            ((OutputStream) output).write(encoded);
        } else {
            throw new IllegalStateException("Unsupported output type: " + output.getClass().getName());
        }

        processImageComplete();
    }
}
