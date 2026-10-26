package xyz.lychee.webp4j;

import org.junit.jupiter.api.Test;
import xyz.lychee.webp4j.codec.AnimatedWebPDecoder;
import xyz.lychee.webp4j.codec.AnimatedWebPEncoder;
import xyz.lychee.webp4j.chunks.Chunk;
import xyz.lychee.webp4j.model.AnimatedWebPData;
import xyz.lychee.webp4j.model.AnimationInfo;
import xyz.lychee.webp4j.model.WebPBitstreamFeatures;
import xyz.lychee.webp4j.codec.WebPDecoder;
import xyz.lychee.webp4j.codec.WebPEncoder;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DecoderTest {

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
    void testWebPDecoderDecodeAndInfo() throws IOException {
        BufferedImage original = createTestImage(32, 24, true);
        byte[] webp = WebPEncoder.encodeLossless(original);

        // WebPDecoder.getInfo
        int[] dims = WebPDecoder.getInfo(webp);
        assertEquals(32, dims[0]);
        assertEquals(24, dims[1]);

        // WebPDecoder.getBitstreamFeatures
        WebPBitstreamFeatures features = WebPDecoder.getBitstreamFeatures(webp);
        assertEquals(32, features.getWidth());
        assertEquals(24, features.getHeight());
        assertTrue(features.isHasAlpha());
        assertFalse(features.isHasAnimation());

        // WebPDecoder.isAnimated
        assertFalse(WebPDecoder.isAnimated(webp));

        // WebPDecoder.decode
        BufferedImage decoded = WebPDecoder.decode(webp);
        assertNotNull(decoded);
        assertEquals(32, decoded.getWidth());
        assertEquals(24, decoded.getHeight());

        // WebPDecoder.decodeInto
        int[] pixels = new int[32 * 24];
        boolean success = WebPDecoder.decodeInto(webp, pixels, 32 * 4);
        assertTrue(success);

        // Compare top-left pixel (RED)
        int rgbOrig = original.getRGB(5, 5);
        int rgbDec = decoded.getRGB(5, 5);
        assertEquals(rgbOrig, rgbDec);
    }

    @Test
    void testWebPDecoderMetadataExtraction() throws IOException {
        BufferedImage img = createTestImage(20, 20, false);
        byte[] exif = new byte[]{0x45, 0x78, 0x69, 0x66, 0x00, 0x01};
        byte[] iccp = new byte[]{0x00, 0x00, 0x02, 0x00, 0x61, 0x70};
        String xmp = "<x:xmpmeta>test</x:xmpmeta>";

        byte[] webpWithMeta = WebPEncoder.encodeWithMetadata(img, 80.0f, false, exif, iccp, xmp);

        // Chunks
        List<Chunk> chunks = WebPDecoder.getChunks(webpWithMeta);
        assertNotNull(chunks);
        assertTrue(chunks.size() >= 3);

        // EXIF
        byte[] extractedExif = WebPDecoder.getExifMetadata(webpWithMeta);
        assertArrayEquals(exif, extractedExif);

        // ICCP
        byte[] extractedIccp = WebPDecoder.getIccProfile(webpWithMeta);
        assertArrayEquals(iccp, extractedIccp);

        // XMP
        String extractedXmp = WebPDecoder.getXmpMetadata(webpWithMeta);
        assertEquals(xmp, extractedXmp);
    }

    @Test
    void testAnimatedWebPDecoder() throws IOException {
        BufferedImage frame1 = new BufferedImage(30, 30, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g1 = frame1.createGraphics();
        g1.setColor(Color.RED);
        g1.fillRect(0, 0, 30, 30);
        g1.dispose();

        BufferedImage frame2 = new BufferedImage(30, 30, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = frame2.createGraphics();
        g2.setColor(Color.BLUE);
        g2.fillRect(0, 0, 30, 30);
        g2.dispose();

        List<BufferedImage> frames = new ArrayList<>();
        frames.add(frame1);
        frames.add(frame2);

        int[] delays = new int[]{100, 150};
        byte[] animBytes = AnimatedWebPEncoder.encode(frames, delays, null);

        // WebPDecoder.isAnimated
        assertTrue(WebPDecoder.isAnimated(animBytes));

        // AnimatedWebPDecoder.getAnimationInfo
        AnimationInfo info = AnimatedWebPDecoder.getAnimationInfo(animBytes);
        assertEquals(30, info.getWidth());
        assertEquals(30, info.getHeight());
        assertEquals(2, info.getFrameCount());

        // AnimatedWebPDecoder.decode
        AnimatedWebPData decoded = AnimatedWebPDecoder.decode(animBytes);
        assertNotNull(decoded);
        assertEquals(30, decoded.getCanvasWidth());
        assertEquals(30, decoded.getCanvasHeight());
        assertEquals(2, decoded.getFrames().size());
        assertEquals(100, decoded.getFrames().get(0).getTimestamp());
        assertEquals(250, decoded.getFrames().get(1).getTimestamp());

        // AnimatedWebPDecoder.decodeImages
        List<BufferedImage> decodedImages = AnimatedWebPDecoder.decodeImages(animBytes);
        assertEquals(2, decodedImages.size());
        assertEquals(30, decodedImages.get(0).getWidth());
        assertEquals(30, decodedImages.get(1).getWidth());
    }

    @Test
    void testWebPCodecFacadeDelegation() throws IOException {
        BufferedImage img = createTestImage(16, 16, false);
        byte[] webp = WebPEncoder.encode(img, 75.0f);

        // WebPCodec delegations to WebPDecoder
        int[] info = WebPCodec.getWebPInfo(webp);
        assertEquals(16, info[0]);
        assertEquals(16, info[1]);

        WebPBitstreamFeatures features = WebPCodec.getBitstreamFeatures(webp);
        assertEquals(16, features.getWidth());
        assertEquals(16, features.getHeight());

        assertFalse(WebPCodec.isAnimated(webp));

        BufferedImage decoded = WebPCodec.decodeImage(webp);
        assertNotNull(decoded);
        assertEquals(16, decoded.getWidth());
        assertEquals(16, decoded.getHeight());
    }

    @Test
    void testWebPDecoderInputValidation() {
        assertThrows(IllegalArgumentException.class, () -> WebPDecoder.decode(null));
        assertThrows(IllegalArgumentException.class, () -> WebPDecoder.decode(new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> WebPDecoder.getInfo(null));
        assertThrows(IllegalArgumentException.class, () -> WebPDecoder.getInfo(new byte[5]));
        assertThrows(IllegalArgumentException.class, () -> WebPDecoder.getBitstreamFeatures(null));
        assertThrows(IllegalArgumentException.class, () -> WebPDecoder.getChunks(null));
        assertFalse(WebPDecoder.isAnimated(null));
        assertFalse(WebPDecoder.isAnimated(new byte[10]));
        assertNull(WebPDecoder.getExifMetadata(null));
        assertNull(WebPDecoder.getIccProfile(null));
        assertNull(WebPDecoder.getXmpMetadata(null));
    }
}
