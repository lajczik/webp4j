package xyz.lychee.webp4j;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xyz.lychee.webp4j.codec.AnimatedWebPDecoder;
import xyz.lychee.webp4j.codec.AnimatedWebPEncoder;
import xyz.lychee.webp4j.imageio.WebPImageIO;
import xyz.lychee.webp4j.imageio.WebPImageReader;
import xyz.lychee.webp4j.imageio.WebPImageWriteParam;
import xyz.lychee.webp4j.imageio.WebPImageWriter;
import xyz.lychee.webp4j.model.AnimationInfo;
import xyz.lychee.webp4j.model.WebPFrameFlag;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.*;

class ImageIOTest {

    @TempDir
    Path tempDir;

    private static final String TEST_RGB_WEBP = "src/test/resources/test_rgb.webp";

    @BeforeAll
    static void setup() {
        WebPImageIO.register();
    }

    private BufferedImage createTestImage(int width, int height, boolean hasAlpha) {
        int type = hasAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage img = new BufferedImage(width, height, type);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(Color.RED);
            g.fillRect(0, 0, width / 2, height);
            g.setColor(hasAlpha ? new Color(0, 255, 0, 128) : Color.GREEN);
            g.fillRect(width / 2, 0, width / 2, height);
        } finally {
            g.dispose();
        }
        return img;
    }

    @Test
    void testImageIOReaderAndWriterDiscovery() {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("webp");
        assertTrue(readers.hasNext(), "Should find ImageReader for format 'webp'");
        ImageReader reader = readers.next();
        assertInstanceOf(WebPImageReader.class, reader);

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("webp");
        assertTrue(writers.hasNext(), "Should find ImageWriter for format 'webp'");
        ImageWriter writer = writers.next();
        assertInstanceOf(WebPImageWriter.class, writer);

        Iterator<ImageReader> mimeReaders = ImageIO.getImageReadersByMIMEType("image/webp");
        assertTrue(mimeReaders.hasNext(), "Should find ImageReader for MIME 'image/webp'");

        Iterator<ImageWriter> mimeWriters = ImageIO.getImageWritersByMIMEType("image/webp");
        assertTrue(mimeWriters.hasNext(), "Should find ImageWriter for MIME 'image/webp'");
    }

    @Test
    void testImageIOReadExistingWebP() throws IOException {
        File file = new File(TEST_RGB_WEBP);
        assertTrue(file.exists());

        // Standard ImageIO.read(File)
        BufferedImage img = ImageIO.read(file);
        assertNotNull(img, "ImageIO.read(File) should return a BufferedImage");
        assertEquals(2248, img.getWidth());
        assertEquals(1442, img.getHeight());

        // Standard ImageIO.read(InputStream)
        try (ByteArrayInputStream in = new ByteArrayInputStream(java.nio.file.Files.readAllBytes(file.toPath()))) {
            BufferedImage fromStream = ImageIO.read(in);
            assertNotNull(fromStream);
            assertEquals(2248, fromStream.getWidth());
        }
    }

    @Test
    void testImageIOWriteAndReadRoundTrip() throws IOException {
        BufferedImage srcOpaque = createTestImage(64, 48, false);
        BufferedImage srcAlpha = createTestImage(64, 48, true);

        File opaqueFile = tempDir.resolve("io_opaque.webp").toFile();
        File alphaFile = tempDir.resolve("io_alpha.webp").toFile();

        // Standard ImageIO.write(image, "webp", File)
        boolean writeOpaqueOk = ImageIO.write(srcOpaque, "webp", opaqueFile);
        assertTrue(writeOpaqueOk, "ImageIO.write should return true for webp format");
        assertTrue(opaqueFile.exists() && opaqueFile.length() > 0);

        boolean writeAlphaOk = ImageIO.write(srcAlpha, "webp", alphaFile);
        assertTrue(writeAlphaOk, "ImageIO.write should return true for webp format");
        assertTrue(alphaFile.exists() && alphaFile.length() > 0);

        // Read back via ImageIO.read(File)
        BufferedImage readOpaque = ImageIO.read(opaqueFile);
        assertNotNull(readOpaque);
        assertEquals(64, readOpaque.getWidth());
        assertEquals(48, readOpaque.getHeight());

        BufferedImage readAlpha = ImageIO.read(alphaFile);
        assertNotNull(readAlpha);
        assertEquals(64, readAlpha.getWidth());
        assertEquals(48, readAlpha.getHeight());
        assertTrue(readAlpha.getColorModel().hasAlpha());
    }

    @Test
    void testImageIOWriteWithCustomParam() throws IOException {
        BufferedImage src = createTestImage(80, 80, true);
        ImageWriter writer = ImageIO.getImageWritersByFormatName("webp").next();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);

            WebPImageWriteParam param = (WebPImageWriteParam) writer.getDefaultWriteParam();
            param.setLossless(true);

            writer.write(null, new IIOImage(src, null, null), param);
        } finally {
            writer.dispose();
        }

        byte[] losslessData = baos.toByteArray();
        assertTrue(losslessData.length > 0);

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(losslessData));
        assertNotNull(decoded);
        assertEquals(80, decoded.getWidth());
        assertEquals(80, decoded.getHeight());
    }

    @Test
    void testImageIOAnimatedWebPReaderMultiImage() throws IOException {
        // Create an animated WebP with 2 frames
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (AnimatedWebPEncoder encoder = new AnimatedWebPEncoder(baos, true, 30, 30)) {
            BufferedImage f1 = createTestImage(30, 30, false);
            BufferedImage f2 = createTestImage(30, 30, true);
            encoder.setFrame(0, f1, 100, 80.0f, false, WebPFrameFlag.ALPHA_BLENDING, WebPFrameFlag.NO_DISPOSE);
            encoder.setFrame(1, f2, 120, 80.0f, false, WebPFrameFlag.NO_BLEND, WebPFrameFlag.DISPOSE_TO_BACKGROUND);
        }
        byte[] animBytes = baos.toByteArray();

        ImageReader reader = ImageIO.getImageReadersByFormatName("webp").next();
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(animBytes))) {
            reader.setInput(iis);

            int numImages = reader.getNumImages(true);
            assertEquals(2, numImages, "Animated WebP should report 2 frames");

            BufferedImage frame0 = reader.read(0);
            assertNotNull(frame0);
            assertEquals(30, frame0.getWidth());

            BufferedImage frame1 = reader.read(1);
            assertNotNull(frame1);
            assertEquals(30, frame1.getWidth());
        } finally {
            reader.dispose();
        }
    }

    @Test
    void testImageIOWriteSequenceAnimatedWebP() throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("webp").next();
        assertTrue(writer.canWriteSequence(), "WebPImageWriter should support sequence writing");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            writer.prepareWriteSequence(null);

            WebPImageWriteParam param = (WebPImageWriteParam) writer.getDefaultWriteParam();
            param.setLoopCount(3);
            param.setBackgroundColor(0xFF00FF00);

            // Frame 0: 40x40, 150ms delay, custom quality 60
            BufferedImage f1 = createTestImage(40, 40, false);
            param.setDelay(150);
            param.setQuality(60.0f);
            param.setFrameFlags(WebPFrameFlag.ALPHA_BLENDING, WebPFrameFlag.NO_DISPOSE);
            writer.writeToSequence(new IIOImage(f1, null, null), param);

            // Frame 1: 40x40, 250ms delay, custom quality 95
            BufferedImage f2 = createTestImage(40, 40, true);
            param.setDelay(250);
            param.setQuality(95.0f);
            param.setFrameFlags(WebPFrameFlag.NO_BLEND, WebPFrameFlag.DISPOSE_TO_BACKGROUND);
            writer.writeToSequence(new IIOImage(f2, null, null), param);

            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }

        byte[] animBytes = baos.toByteArray();
        assertTrue(animBytes.length > 0);

        // Verify with AnimatedWebPDecoder
        AnimationInfo info = AnimatedWebPDecoder.getAnimationInfo(animBytes);
        assertNotNull(info);
        assertEquals(2, info.getFrameCount());
        assertEquals(3, info.getLoopCount());
        assertEquals(40, info.getWidth());
        assertEquals(40, info.getHeight());
        assertEquals(400, info.getTotalDuration());
        assertArrayEquals(new int[]{150, 250}, info.getDelays());

        // Verify with ImageIO reader reading the generated sequence
        ImageReader reader = ImageIO.getImageReadersByFormatName("webp").next();
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(animBytes))) {
            reader.setInput(iis);
            assertEquals(2, reader.getNumImages(true));
            BufferedImage r0 = reader.read(0);
            assertNotNull(r0);
            assertEquals(40, r0.getWidth());
            assertEquals(40, r0.getHeight());

            BufferedImage r1 = reader.read(1);
            assertNotNull(r1);
            assertEquals(40, r1.getWidth());
            assertEquals(40, r1.getHeight());
        } finally {
            reader.dispose();
        }
    }

    @Test
    void testImageIOWriteSequenceWithoutPrepareThrows() throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("webp").next();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            BufferedImage img = createTestImage(20, 20, false);

            assertThrows(IllegalStateException.class, () ->
                    writer.writeToSequence(new IIOImage(img, null, null), null));
            assertThrows(IllegalStateException.class, writer::endWriteSequence);
        } finally {
            writer.dispose();
        }
    }

    @Test
    void testWebPImageWriteParamQuality() {
        WebPImageWriteParam param = new WebPImageWriteParam(null);
        assertEquals(75.0f, param.getQuality(), 0.01f);
        assertEquals(0.75f, param.getCompressionQuality(), 0.001f);

        param.setQuality(92.5f);
        assertEquals(92.5f, param.getQuality(), 0.01f);
        assertEquals(0.925f, param.getCompressionQuality(), 0.001f);
        assertFalse(param.isLossless());

        param.setCompressionQuality(0.50f);
        assertEquals(50.0f, param.getQuality(), 0.01f);

        assertThrows(IllegalArgumentException.class, () -> param.setQuality(-1.0f));
        assertThrows(IllegalArgumentException.class, () -> param.setQuality(100.1f));
    }
}
