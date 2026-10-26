package xyz.lychee.webp4j.imageio;

import xyz.lychee.webp4j.codec.AnimatedWebPDecoder;
import xyz.lychee.webp4j.codec.WebPDecoder;
import xyz.lychee.webp4j.model.WebPBitstreamFeatures;

import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;

/**
 * ImageReader implementation for decoding WebP images using libwebp.
 * Supports both static WebP images and animated WebP frame extraction.
 *
 * @author lajczik
 */
public class WebPImageReader extends ImageReader {

    private byte[] webPData;
    private WebPBitstreamFeatures features;
    private int width;
    private int height;
    private boolean hasAlpha;
    private boolean isAnimated;

    public WebPImageReader(ImageReaderSpi originatingProvider) {
        super(originatingProvider);
    }

    @Override
    public void setInput(Object input, boolean seekForwardOnly, boolean ignoreMetadata) {
        super.setInput(input, seekForwardOnly, ignoreMetadata);
        resetInternal();
    }

    @Override
    public int getNumImages(boolean allowSearch) throws IOException {
        if (input == null) {
            throw new IllegalStateException("Input has not been set");
        }
        readHeader();
        if (isAnimated) {
            return AnimatedWebPDecoder.getFrameCount(getWebPData());
        }
        return 1;
    }

    @Override
    public int getWidth(int imageIndex) throws IOException {
        checkIndex(imageIndex);
        readHeader();
        return width;
    }

    @Override
    public int getHeight(int imageIndex) throws IOException {
        checkIndex(imageIndex);
        readHeader();
        return height;
    }

    @Override
    public Iterator<ImageTypeSpecifier> getImageTypes(int imageIndex) throws IOException {
        checkIndex(imageIndex);
        readHeader();
        int type = hasAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        return Collections.singletonList(ImageTypeSpecifier.createFromBufferedImageType(type)).iterator();
    }

    @Override
    public IIOMetadata getStreamMetadata() {
        return null;
    }

    @Override
    public IIOMetadata getImageMetadata(int imageIndex) {
        return null;
    }

    @Override
    public BufferedImage read(int imageIndex, ImageReadParam param) throws IOException {
        if (input == null) {
            throw new IllegalStateException("Input has not been set");
        }
        checkIndex(imageIndex);
        clearAbortRequest();
        processImageStarted(imageIndex);

        byte[] data = getWebPData();
        BufferedImage result;
        if (isAnimated) {
            result = AnimatedWebPDecoder.decodeFrame(data, imageIndex).getImage();
        } else {
            result = WebPDecoder.decode(data);
        }

        if (param != null) {
            BufferedImage dest = param.getDestination();
            if (dest != null) {
                Graphics2D g = dest.createGraphics();
                try {
                    g.drawImage(result, 0, 0, null);
                } finally {
                    g.dispose();
                }
                result = dest;
            }
        }

        processImageComplete();
        return result;
    }

    @Override
    public void reset() {
        super.reset();
        resetInternal();
    }

    @Override
    public void dispose() {
        super.dispose();
        resetInternal();
    }

    private void resetInternal() {
        this.webPData = null;
        this.features = null;
        this.width = 0;
        this.height = 0;
        this.hasAlpha = false;
        this.isAnimated = false;
    }

    private void checkIndex(int imageIndex) throws IOException {
        if (imageIndex < 0) {
            throw new IndexOutOfBoundsException("imageIndex < 0: " + imageIndex);
        }
        int numImages = getNumImages(true);
        if (imageIndex >= numImages) {
            throw new IndexOutOfBoundsException("imageIndex (" + imageIndex + ") >= numImages (" + numImages + ")");
        }
    }

    private void readHeader() throws IOException {
        if (features != null) {
            return;
        }
        byte[] data = getWebPData();
        this.features = WebPDecoder.getBitstreamFeatures(data);
        this.width = features.getWidth();
        this.height = features.getHeight();
        this.hasAlpha = features.isHasAlpha();
        this.isAnimated = features.isHasAnimation();
    }

    private byte[] getWebPData() throws IOException {
        if (webPData != null) {
            return webPData;
        }
        if (!(input instanceof ImageInputStream)) {
            throw new IllegalStateException("Input is not an ImageInputStream");
        }
        ImageInputStream stream = (ImageInputStream) input;
        stream.seek(0);
        long length = stream.length();
        if (length > 0 && length <= Integer.MAX_VALUE) {
            byte[] data = new byte[(int) length];
            stream.readFully(data);
            this.webPData = data;
            return data;
        } else {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(32768);
            byte[] chunk = new byte[8192];
            int bytesRead;
            while ((bytesRead = stream.read(chunk)) != -1) {
                buffer.write(chunk, 0, bytesRead);
            }
            this.webPData = buffer.toByteArray();
            return this.webPData;
        }
    }
}
