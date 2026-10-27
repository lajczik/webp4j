package xyz.lychee.webp4j.imageio;

import xyz.lychee.webp4j.codec.AnimatedWebPDecoder;
import xyz.lychee.webp4j.codec.WebPDecoder;
import xyz.lychee.webp4j.model.AnimatedWebPData;
import xyz.lychee.webp4j.utils.WebPLimits;
import xyz.lychee.webp4j.model.WebPBitstreamFeatures;

import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import java.awt.Rectangle;
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
    private int frameCount = 1;
    private AnimatedWebPData animation;

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
            return frameCount;
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
            result = readAnimatedFrame(data, imageIndex);
        } else {
            result = WebPDecoder.decode(data);
        }

        result = applyReadParam(result, param);

        processImageComplete();
        return result;
    }

    /**
     * Honours the source region, subsampling and destination (image and offset) of an {@link ImageReadParam}.
     * Decoding is always whole-frame (WebP has no random access), so these are applied to the decoded raster.
     */
    private BufferedImage applyReadParam(BufferedImage image, ImageReadParam param) {
        if (param == null) {
            return image;
        }
        BufferedImage dest = param.getDestination();
        Rectangle src = new Rectangle();
        Rectangle dst = new Rectangle();
        computeRegions(param, image.getWidth(), image.getHeight(), dest, src, dst);

        int xPeriod = param.getSourceXSubsampling();
        int yPeriod = param.getSourceYSubsampling();
        boolean identity = dest == null && xPeriod == 1 && yPeriod == 1 && src.x == 0 && src.y == 0
                && src.width == image.getWidth() && src.height == image.getHeight();
        if (identity) {
            return image;
        }

        BufferedImage out = dest;
        if (out == null) {
            int type = image.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
            out = new BufferedImage(dst.x + dst.width, dst.y + dst.height, type);
        }
        int[] row = new int[src.width];
        for (int dy = 0; dy < dst.height; dy++) {
            image.getRGB(src.x, src.y + dy * yPeriod, src.width, 1, row, 0, src.width);
            for (int dx = 0; dx < dst.width; dx++) {
                out.setRGB(dst.x + dx, dst.y + dy, row[dx * xPeriod]);
            }
        }
        return out;
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
        this.frameCount = 1;
        this.animation = null;
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
        this.frameCount = isAnimated ? AnimatedWebPDecoder.getFrameCount(data) : 1;
    }

    /**
     * Frame 0 is decoded on its own; later frames depend on the composition of the previous ones, so the
     * animation is decoded once and cached for the lifetime of the input (reading every frame is O(N), not O(N^2)).
     */
    private BufferedImage readAnimatedFrame(byte[] data, int imageIndex) throws IOException {
        if (animation == null) {
            if (imageIndex == 0) {
                return AnimatedWebPDecoder.decodeFirstFrame(data).getImage();
            }
            animation = AnimatedWebPDecoder.decode(data);
        }
        return animation.getFrame(imageIndex).getImage();
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
            WebPLimits.checkInputSize(length);
            byte[] data = new byte[(int) length];
            stream.readFully(data);
            this.webPData = data;
            return data;
        } else {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(32768);
            byte[] chunk = new byte[8192];
            int bytesRead;
            long total = 0;
            while ((bytesRead = stream.read(chunk)) != -1) {
                total += bytesRead;
                WebPLimits.checkInputSize(total);
                buffer.write(chunk, 0, bytesRead);
            }
            this.webPData = buffer.toByteArray();
            return this.webPData;
        }
    }
}
