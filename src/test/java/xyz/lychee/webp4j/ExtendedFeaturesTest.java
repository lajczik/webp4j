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
}
