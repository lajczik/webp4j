package xyz.lychee.webp4j;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xyz.lychee.webp4j.codec.AnimatedWebPDecoder;
import xyz.lychee.webp4j.codec.AnimatedWebPEncoder;
import xyz.lychee.webp4j.codec.BatchProcessor;
import xyz.lychee.webp4j.codec.GifToWebPConverter;
import xyz.lychee.webp4j.codec.WebPDecoder;
import xyz.lychee.webp4j.codec.WebPEncoder;
import xyz.lychee.webp4j.model.AnimatedWebPData;
import xyz.lychee.webp4j.model.AnimatedWebPFrame;
import xyz.lychee.webp4j.model.AnimationInfo;
import xyz.lychee.webp4j.model.WebPBitstreamFeatures;
import xyz.lychee.webp4j.model.WebPContainerFlag;
import xyz.lychee.webp4j.model.WebPFrameFlag;
import xyz.lychee.webp4j.utils.ImageHelper;
import xyz.lychee.webp4j.utils.Util;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ExtendedFeaturesTest {

    @TempDir
    Path tempDir;

    private BufferedImage createTestImage(int width, int height, boolean hasAlpha) {
        int type = hasAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage img = new BufferedImage(width, height, type);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, width / 2, height / 2);
        g.setColor(Color.GREEN);
        g.fillRect(width / 2, 0, width / 2, height / 2);
        g.setColor(Color.BLUE);
        g.fillRect(0, height / 2, width / 2, height / 2);
        g.setColor(hasAlpha ? new Color(255, 255, 0, 128) : Color.YELLOW);
        g.fillRect(width / 2, height / 2, width / 2, height / 2);
        g.dispose();
        return img;
    }

    @Test
    void testWebPBitstreamFeaturesAndGetters() throws IOException {
        BufferedImage img = createTestImage(40, 30, true);
        byte[] lossy = WebPEncoder.encode(img, 80.0f);
        byte[] lossless = WebPEncoder.encodeLossless(img);

        WebPBitstreamFeatures fLossy = WebPBitstreamFeatures.parse(new ByteArrayInputStream(lossy));
        assertTrue(fLossy.isLossy());
        assertFalse(fLossy.isLossless());
        assertTrue(fLossy.hasAlpha());
        assertFalse(fLossy.hasAnimation());
        assertEquals(40, fLossy.getWidth());
        assertEquals(30, fLossy.getHeight());

        WebPBitstreamFeatures fLossless = WebPBitstreamFeatures.parse(ByteBuffer.wrap(lossless));
        assertTrue(fLossless.isLossless());
        assertFalse(fLossless.isLossy());
        assertEquals(40, fLossless.getWidth());
        assertEquals(30, fLossless.getHeight());
    }

    @Test
    void testImageHelperUtilities() {
        BufferedImage src = createTestImage(100, 50, true);

        // resize
        BufferedImage resized = ImageHelper.resize(src, 50, 25);
        assertEquals(50, resized.getWidth());
        assertEquals(25, resized.getHeight());
        assertTrue(ImageHelper.hasAlpha(resized));

        // thumbnail
        BufferedImage thumb = ImageHelper.createThumbnail(src, 40);
        assertEquals(40, thumb.getWidth());
        assertEquals(20, thumb.getHeight());

        // clone
        BufferedImage copy = ImageHelper.clone(src);
        assertEquals(src.getWidth(), copy.getWidth());
        assertEquals(src.getHeight(), copy.getHeight());
        assertEquals(src.getRGB(10, 10), copy.getRGB(10, 10));

        // toBufferedImage
        BufferedImage converted = ImageHelper.toBufferedImage(src);
        assertSame(src, converted);
    }

    @Test
    void testFileAndPathAndStreamOverloads() throws IOException {
        BufferedImage original = createTestImage(32, 32, false);
        File webpFile = tempDir.resolve("test_image.webp").toFile();
        Path webpPath = webpFile.toPath();

        // Encode to File
        WebPEncoder.encode(original, webpFile, 80.0f);
        assertTrue(webpFile.exists());
        assertTrue(webpFile.length() > 0);

        // Decode from File
        BufferedImage fromFile = WebPDecoder.decode(webpFile);
        assertEquals(32, fromFile.getWidth());
        assertEquals(32, fromFile.getHeight());

        // Decode from Path
        BufferedImage fromPath = WebPDecoder.decode(webpPath);
        assertEquals(32, fromPath.getWidth());

        // Info from File
        int[] info = WebPDecoder.getInfo(webpFile);
        assertEquals(32, info[0]);
        assertEquals(32, info[1]);

        // Features from Path
        WebPBitstreamFeatures features = WebPDecoder.getBitstreamFeatures(webpPath);
        assertEquals(32, features.getWidth());

        // isAnimated from File
        assertFalse(WebPDecoder.isAnimated(webpFile));

        // Encode to OutputStream
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        WebPEncoder.encodeLossless(original, baos);
        assertTrue(baos.size() > 0);

        // Decode from InputStream
        BufferedImage fromStream = WebPDecoder.decode(new ByteArrayInputStream(baos.toByteArray()));
        assertEquals(32, fromStream.getWidth());

        // Decode from ByteBuffer
        BufferedImage fromBuffer = WebPDecoder.decode(ByteBuffer.wrap(baos.toByteArray()));
        assertEquals(32, fromBuffer.getWidth());
    }

    @Test
    void testAnimatedWebPDelaysDurationsAndFrameAccess() throws IOException {
        BufferedImage f1 = createTestImage(20, 20, false);
        BufferedImage f2 = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = f2.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 20, 20);
        g.dispose();

        List<BufferedImage> frames = Arrays.asList(f1, f2);
        int[] delays = new int[]{120, 250};

        File animFile = tempDir.resolve("anim.webp").toFile();
        AnimatedWebPEncoder.encode(animFile, frames, delays, null);
        assertTrue(animFile.exists());

        // Frame count & animation info
        assertEquals(2, AnimatedWebPDecoder.getFrameCount(animFile));
        AnimationInfo info = AnimatedWebPDecoder.getAnimationInfo(animFile);
        assertEquals(2, info.getFrameCount());
        assertEquals(370, info.getTotalDuration());
        assertNotNull(info.getDelays());
        assertEquals(2, info.getDelays().length);
        assertEquals(120, info.getDelays()[0]);
        assertEquals(250, info.getDelays()[1]);

        // Decode full
        AnimatedWebPData decoded = AnimatedWebPDecoder.decode(animFile);
        assertEquals(2, decoded.getFrames().size());
        assertEquals(370, decoded.getTotalDuration());
        AnimatedWebPFrame frame0 = decoded.getFrame(0);
        AnimatedWebPFrame frame1 = decoded.getFrame(1);
        assertEquals(120, frame0.getTimestamp());
        assertEquals(120, frame0.getDuration());
        assertEquals(370, frame1.getTimestamp());
        assertEquals(250, frame1.getDuration());

        // Single frame decode
        AnimatedWebPFrame single0 = AnimatedWebPDecoder.decodeFrame(animFile, 0);
        assertEquals(20, single0.getImage().getWidth());
        assertEquals(120, single0.getDuration());

        // decodeFirstFrame
        BufferedImage firstFrame = WebPDecoder.decodeFirstFrame(animFile);
        assertNotNull(firstFrame);
        assertEquals(20, firstFrame.getWidth());
    }

    @Test
    void testBatchProcessorOverloads() throws IOException {
        BufferedImage img1 = createTestImage(16, 16, false);
        BufferedImage img2 = createTestImage(16, 16, true);
        List<BufferedImage> list = Arrays.asList(img1, img2);

        List<byte[]> lossy = BatchProcessor.encodeImages(list, 80.0f);
        assertEquals(2, lossy.size());

        List<byte[]> lossless = BatchProcessor.encodeLosslessImages(list);
        assertEquals(2, lossless.size());

        List<BufferedImage> decoded = BatchProcessor.decodeImages(lossless);
        assertEquals(2, decoded.size());
    }

    @Test
    void testGifToWebPFileConversion() throws IOException {
        File gifFile = new File("src/test/resources/test_gif.gif");
        if (gifFile.exists()) {
            AnimationInfo info = GifToWebPConverter.getInfo(gifFile);
            assertNotNull(info);
            assertTrue(info.getWidth() > 0);

            File outWebP = tempDir.resolve("converted.webp").toFile();
            GifToWebPConverter.convert(gifFile, outWebP);
            assertTrue(outWebP.exists());
            assertTrue(outWebP.length() > 0);
        }
    }

    @Test
    void testWebPFlagsEnumsAndEncoderUsage() throws IOException {
        // WebPFrameFlag masks & combinations
        assertEquals(0, WebPFrameFlag.NO_DISPOSE.getMask());
        assertEquals(1, WebPFrameFlag.DISPOSE_TO_BACKGROUND.getMask());
        assertEquals(0, WebPFrameFlag.ALPHA_BLENDING.getMask());
        assertEquals(2, WebPFrameFlag.NO_BLEND.getMask());

        int combined = WebPFrameFlag.toMask(WebPFrameFlag.NO_BLEND, WebPFrameFlag.DISPOSE_TO_BACKGROUND);
        assertEquals(3, combined);
        assertTrue(WebPFrameFlag.hasFlag(combined, WebPFrameFlag.NO_BLEND));
        assertTrue(WebPFrameFlag.hasFlag(combined, WebPFrameFlag.DISPOSE_TO_BACKGROUND));
        assertFalse(WebPFrameFlag.hasFlag(combined, WebPFrameFlag.ALPHA_BLENDING));
        assertFalse(WebPFrameFlag.hasFlag(combined, WebPFrameFlag.NO_DISPOSE));

        // Direct enum isSet() checks
        assertTrue(WebPFrameFlag.NO_BLEND.isSet(combined));
        assertTrue(WebPFrameFlag.DISPOSE_TO_BACKGROUND.isSet(combined));
        assertFalse(WebPFrameFlag.ALPHA_BLENDING.isSet(combined));
        assertFalse(WebPFrameFlag.NO_DISPOSE.isSet(combined));

        // fromMask() enum sets
        java.util.EnumSet<WebPFrameFlag> frameSet = WebPFrameFlag.fromMask(combined);
        assertTrue(frameSet.contains(WebPFrameFlag.NO_BLEND));
        assertTrue(frameSet.contains(WebPFrameFlag.DISPOSE_TO_BACKGROUND));
        assertFalse(frameSet.contains(WebPFrameFlag.ALPHA_BLENDING));
        assertFalse(frameSet.contains(WebPFrameFlag.NO_DISPOSE));

        java.util.EnumSet<WebPFrameFlag> defaultFrameSet = WebPFrameFlag.fromMask(0);
        assertTrue(defaultFrameSet.contains(WebPFrameFlag.ALPHA_BLENDING));
        assertTrue(defaultFrameSet.contains(WebPFrameFlag.NO_DISPOSE));
        assertFalse(defaultFrameSet.contains(WebPFrameFlag.NO_BLEND));
        assertFalse(defaultFrameSet.contains(WebPFrameFlag.DISPOSE_TO_BACKGROUND));

        // AnimatedWebPEncoder with WebPFrameFlag
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (AnimatedWebPEncoder encoder = new AnimatedWebPEncoder(baos, true, 2, 20, 20)) {
            BufferedImage f1 = createTestImage(20, 20, true);
            BufferedImage f2 = createTestImage(20, 20, false);
            encoder.setFrame(0, f1, 100, 80.0f, false, WebPFrameFlag.ALPHA_BLENDING, WebPFrameFlag.NO_DISPOSE);
            encoder.setFrame(1, f2, 150, 80.0f, true, WebPFrameFlag.NO_BLEND, WebPFrameFlag.DISPOSE_TO_BACKGROUND);
        }
        byte[] animBytes = baos.toByteArray();
        assertTrue(animBytes.length > 0);

        AnimationInfo info = AnimatedWebPDecoder.getAnimationInfo(animBytes);
        assertEquals(2, info.getFrameCount());

        // WebPContainerFlag
        int containerMask = WebPContainerFlag.toMask(WebPContainerFlag.ANIMATION, WebPContainerFlag.ALPHA, WebPContainerFlag.EXIF);
        assertTrue(WebPContainerFlag.hasFlag(containerMask, WebPContainerFlag.ANIMATION));
        assertTrue(WebPContainerFlag.hasFlag(containerMask, WebPContainerFlag.ALPHA));
        assertTrue(WebPContainerFlag.hasFlag(containerMask, WebPContainerFlag.EXIF));
        assertFalse(WebPContainerFlag.hasFlag(containerMask, WebPContainerFlag.XMP));
        assertFalse(WebPContainerFlag.hasFlag(containerMask, WebPContainerFlag.ICC_PROFILE));

        // Direct enum isSet() checks
        assertTrue(WebPContainerFlag.ANIMATION.isSet(containerMask));
        assertTrue(WebPContainerFlag.ALPHA.isSet(containerMask));
        assertTrue(WebPContainerFlag.EXIF.isSet(containerMask));
        assertFalse(WebPContainerFlag.XMP.isSet(containerMask));
        assertFalse(WebPContainerFlag.ICC_PROFILE.isSet(containerMask));

        // fromMask() enum sets
        java.util.EnumSet<WebPContainerFlag> containerSet = WebPContainerFlag.fromMask(containerMask);
        assertTrue(containerSet.contains(WebPContainerFlag.ANIMATION));
        assertTrue(containerSet.contains(WebPContainerFlag.ALPHA));
        assertTrue(containerSet.contains(WebPContainerFlag.EXIF));
        assertFalse(containerSet.contains(WebPContainerFlag.XMP));
        assertFalse(containerSet.contains(WebPContainerFlag.ICC_PROFILE));
    }

    @Test
    void testOptimizedUtilStreamReading() throws IOException {
        byte[] data = new byte[]{0x12, 0x34, 0x56, 0x78};
        try (ByteArrayInputStream in = new ByteArrayInputStream(data)) {
            int val16 = Util.readInt16(in);
            assertEquals(0x3412, val16);
        }

        try (ByteArrayInputStream in = new ByteArrayInputStream(data)) {
            int val24 = Util.readInt24(in);
            assertEquals(0x563412, val24);
        }

        try (ByteArrayInputStream in = new ByteArrayInputStream(data)) {
            int val32 = Util.readInt32(in);
            assertEquals(0x78563412, val32);
        }

        // EOF check
        assertThrows(java.io.EOFException.class, () -> {
            try (ByteArrayInputStream in = new ByteArrayInputStream(new byte[]{0x01})) {
                Util.readInt16(in);
            }
        });
    }

    @Test
    void testOptimizedHeaderInspectionAndAlpha() throws IOException {
        BufferedImage opaque = createTestImage(40, 30, false);
        BufferedImage transparent = createTestImage(40, 30, true);

        File opaqueFile = tempDir.resolve("opaque.webp").toFile();
        File transFile = tempDir.resolve("transparent.webp").toFile();

        WebPEncoder.encode(opaque, opaqueFile, 80.0f);
        WebPEncoder.encode(transparent, transFile, 80.0f);

        // hasAlpha
        assertFalse(WebPDecoder.hasAlpha(opaqueFile));
        assertFalse(WebPCodec.hasAlpha(opaqueFile));
        assertTrue(WebPDecoder.hasAlpha(transFile));
        assertTrue(WebPCodec.hasAlpha(transFile));

        // getInfo from File (optimized 64-byte header reader)
        int[] dims = WebPDecoder.getInfo(opaqueFile);
        assertEquals(40, dims[0]);
        assertEquals(30, dims[1]);

        // isAnimated (optimized 30-byte header reader)
        assertFalse(WebPDecoder.isAnimated(opaqueFile));
        assertFalse(WebPDecoder.isAnimated(transFile));
    }

    @Test
    void testInPlaceDestinationDecoding() throws IOException {
        BufferedImage src = createTestImage(32, 32, true);
        byte[] webPData = WebPEncoder.encode(src, 90.0f);

        BufferedImage dest = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        WebPDecoder.decode(webPData, dest);

        assertEquals(32, dest.getWidth());
        assertEquals(32, dest.getHeight());
        assertNotEquals(0, dest.getRGB(10, 10));

        // Dimension mismatch error
        BufferedImage wrongSize = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        assertThrows(IllegalArgumentException.class, () -> WebPDecoder.decode(webPData, wrongSize));
    }

    @Test
    void testWebPThumbnailCreation() throws IOException {
        BufferedImage src = createTestImage(100, 50, false);
        byte[] webPData = WebPEncoder.encode(src, 80.0f);

        BufferedImage thumb = WebPDecoder.createThumbnail(webPData, 40);
        assertNotNull(thumb);
        assertEquals(40, thumb.getWidth());
        assertEquals(20, thumb.getHeight());

        File webpFile = tempDir.resolve("thumb_test.webp").toFile();
        WebPEncoder.encode(src, webpFile, 80.0f);
        BufferedImage thumbFile = WebPDecoder.createThumbnail(webpFile, 40);
        assertEquals(40, thumbFile.getWidth());
        assertEquals(20, thumbFile.getHeight());
    }
}
