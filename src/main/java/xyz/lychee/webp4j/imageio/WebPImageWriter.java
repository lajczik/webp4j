package xyz.lychee.webp4j.imageio;

import xyz.lychee.webp4j.codec.AnimatedWebPEncoder;
import xyz.lychee.webp4j.codec.WebPEncoder;
import xyz.lychee.webp4j.model.GifToWebPConfig;
import xyz.lychee.webp4j.model.WebPFrameFlag;
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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * ImageWriter implementation for writing static WebP images and animated WebP sequences using libwebp.
 *
 * @author lajczik
 */
public class WebPImageWriter extends ImageWriter {

    private static class SequenceFrame {
        final BufferedImage image;
        final int duration;
        final float quality;
        final boolean lossless;
        final WebPFrameFlag[] flags;

        SequenceFrame(BufferedImage image, int duration, float quality, boolean lossless, WebPFrameFlag[] flags) {
            this.image = image;
            this.duration = duration;
            this.quality = quality;
            this.lossless = lossless;
            this.flags = flags;
        }
    }

    private List<SequenceFrame> sequenceFrames = null;
    private int sequenceLoopCount = 0;
    private int sequenceBackgroundColor = 0;

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

    // ============================================
    // Animated WebP Sequence Support
    // ============================================

    @Override
    public boolean canWriteSequence() {
        return true;
    }

    @Override
    public void prepareWriteSequence(IIOMetadata streamMetadata) throws IOException {
        if (output == null) {
            throw new IllegalStateException("Output has not been set");
        }
        this.sequenceFrames = new ArrayList<>();
        this.sequenceLoopCount = 0;
        this.sequenceBackgroundColor = 0;
    }

    @Override
    public void writeToSequence(IIOImage image, ImageWriteParam param) throws IOException {
        if (output == null) {
            throw new IllegalStateException("Output has not been set");
        }
        if (image == null) {
            throw new IllegalArgumentException("image cannot be null");
        }
        if (sequenceFrames == null) {
            throw new IllegalStateException("prepareWriteSequence has not been called");
        }

        clearAbortRequest();
        processImageStarted(sequenceFrames.size());

        RenderedImage renderedImage = image.getRenderedImage();
        BufferedImage frame = ImageHelper.toBufferedImage(renderedImage);

        float quality = 75.0f;
        boolean lossless = false;
        int duration = 100;
        WebPFrameFlag[] flags = new WebPFrameFlag[0];

        if (param instanceof WebPImageWriteParam) {
            WebPImageWriteParam webpParam = (WebPImageWriteParam) param;
            lossless = webpParam.isLossless();
            if (!lossless && webpParam.getCompressionMode() == ImageWriteParam.MODE_EXPLICIT) {
                quality = webpParam.getCompressionQuality() * 100.0f;
            }
            duration = webpParam.getDelay();
            flags = webpParam.getFrameFlags();
            if (this.sequenceLoopCount == 0 && webpParam.getLoopCount() > 0) {
                this.sequenceLoopCount = webpParam.getLoopCount();
            }
            if (this.sequenceBackgroundColor == 0 && webpParam.getBackgroundColor() != 0) {
                this.sequenceBackgroundColor = webpParam.getBackgroundColor();
            }
        } else if (param != null && param.getCompressionMode() == ImageWriteParam.MODE_EXPLICIT) {
            quality = param.getCompressionQuality() * 100.0f;
        }

        sequenceFrames.add(new SequenceFrame(frame, duration, quality, lossless, flags));
        processImageComplete();
    }

    @Override
    public void endWriteSequence() throws IOException {
        if (output == null) {
            throw new IllegalStateException("Output has not been set");
        }
        if (sequenceFrames == null) {
            throw new IllegalStateException("prepareWriteSequence has not been called");
        }
        if (sequenceFrames.isEmpty()) {
            sequenceFrames = null;
            return;
        }

        int width = sequenceFrames.get(0).image.getWidth();
        int height = sequenceFrames.get(0).image.getHeight();
        boolean hasAlpha = false;

        for (SequenceFrame sf : sequenceFrames) {
            if (sf.image.getWidth() != width || sf.image.getHeight() != height) {
                throw new IOException(String.format("Sequence frame dimensions (%dx%d) do not match canvas dimensions (%dx%d)",
                        sf.image.getWidth(), sf.image.getHeight(), width, height));
            }
            if (sf.image.getColorModel().hasAlpha()) {
                hasAlpha = true;
            }
        }

        byte[] animData = encodeSequence(width, height, hasAlpha);

        if (output instanceof ImageOutputStream) {
            ((ImageOutputStream) output).write(animData);
        } else if (output instanceof OutputStream) {
            ((OutputStream) output).write(animData);
        } else {
            throw new IllegalStateException("Unsupported output type: " + output.getClass().getName());
        }

        sequenceFrames = null;
    }

    /**
     * Encodes the collected frames. When every frame uses the same quality/mode and no explicit frame
     * flags or background are requested, libwebp's WebPAnimEncoder is used (inter-frame optimization
     * and sub-rectangle frames give much smaller files); otherwise each frame is written as requested.
     */
    private byte[] encodeSequence(int width, int height, boolean hasAlpha) throws IOException {
        SequenceFrame first = sequenceFrames.get(0);
        boolean uniform = sequenceBackgroundColor == 0;
        for (SequenceFrame sf : sequenceFrames) {
            if (sf.flags.length != 0 || sf.lossless != first.lossless || sf.quality != first.quality) {
                uniform = false;
                break;
            }
        }

        if (uniform) {
            List<BufferedImage> images = new ArrayList<>(sequenceFrames.size());
            int[] delays = new int[sequenceFrames.size()];
            for (int i = 0; i < delays.length; i++) {
                images.add(sequenceFrames.get(i).image);
                delays[i] = sequenceFrames.get(i).duration;
            }
            GifToWebPConfig config = first.lossless
                    ? GifToWebPConfig.createLosslessConfig()
                    : GifToWebPConfig.createLossyConfig(first.quality);
            config.setLoopCount(sequenceLoopCount);
            return AnimatedWebPEncoder.encode(images, delays, config);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream(Math.max(16384, sequenceFrames.size() * 8192));
        try (AnimatedWebPEncoder encoder = new AnimatedWebPEncoder(baos, hasAlpha, width, height, sequenceLoopCount, sequenceBackgroundColor)) {
            for (int i = 0; i < sequenceFrames.size(); i++) {
                SequenceFrame sf = sequenceFrames.get(i);
                encoder.setFrame(i, sf.image, sf.duration, sf.quality, sf.lossless, sf.flags);
            }
        }
        return baos.toByteArray();
    }

    @Override
    public void reset() {
        super.reset();
        this.sequenceFrames = null;
        this.sequenceLoopCount = 0;
        this.sequenceBackgroundColor = 0;
    }
}
