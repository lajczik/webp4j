package xyz.lychee.webp4j;

import org.junit.jupiter.api.Test;
import xyz.lychee.webp4j.codec.AnimatedWebPEncoder;
import xyz.lychee.webp4j.model.GifToWebPConfig;
import xyz.lychee.webp4j.model.AnimatedWebPData;
import xyz.lychee.webp4j.model.WebPBitstreamFeatures;
import xyz.lychee.webp4j.codec.WebPEncoder;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class EncoderTest {

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
    void testWebPEncoderStaticImageLossyAndLossless() throws IOException {
        BufferedImage img = createTestImage(64, 64, true);

        // Lossy
        byte[] lossy = WebPEncoder.encode(img, 80.0f);
        assertNotNull(lossy);
        assertTrue(lossy.length > 0);
        WebPBitstreamFeatures f1 = WebPCodec.getBitstreamFeatures(lossy);
        assertEquals(64, f1.getWidth());
        assertEquals(64, f1.getHeight());
        assertTrue(f1.isHasAlpha());

        // Lossless
        byte[] lossless = WebPEncoder.encodeLossless(img);
        assertNotNull(lossless);
        assertTrue(lossless.length > 0);
        WebPBitstreamFeatures f2 = WebPCodec.getBitstreamFeatures(lossless);
        assertEquals(64, f2.getWidth());
        assertEquals(64, f2.getHeight());

        // To OutputStream
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        WebPEncoder.encode(img, 75.0f, baos);
        byte[] streamBytes = baos.toByteArray();
        assertTrue(streamBytes.length > 0);
    }

    @Test
    void testWebPEncoderWithMetadata() throws IOException {
        BufferedImage img = createTestImage(32, 32, false);
        byte[] exif = new byte[]{1, 2, 3, 4};
        byte[] iccp = new byte[]{5, 6, 7, 8};
        String xmp = "<x:xmpmeta>test</x:xmpmeta>";

        byte[] webp = WebPEncoder.encodeWithMetadata(img, 90.0f, false, exif, iccp, xmp);
        assertNotNull(webp);
        assertTrue(webp.length > 0);

        WebPBitstreamFeatures f = WebPCodec.getBitstreamFeatures(webp);
        assertEquals(32, f.getWidth());
        assertEquals(32, f.getHeight());
    }

    @Test
    void testAnimatedWebPEncoderStreaming() throws IOException {
        int width = 48;
        int height = 48;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (AnimatedWebPEncoder encoder = new AnimatedWebPEncoder(baos, true, 3, width, height, 0, 0)) {
            BufferedImage f1 = createTestImage(width, height, true);
            BufferedImage f2 = createTestImage(width, height, true);
            BufferedImage f3 = createTestImage(width, height, true);

            encoder.setFrame(0, f1, 100, 80.0f, false);
            encoder.setFrame(1, f2, 120, 80.0f, false);
            encoder.setFrame(2, f3, 140, 80.0f, false);
            encoder.flush();
        }

        byte[] animBytes = baos.toByteArray();
        assertTrue(animBytes.length > 0);

        AnimatedWebPData decoded = WebPCodec.decodeAnimatedWebP(animBytes);
        assertNotNull(decoded);
        assertEquals(width, decoded.getCanvasWidth());
        assertEquals(height, decoded.getCanvasHeight());
        assertEquals(3, decoded.getFrameCount());
    }

    @Test
    void testAnimatedWebPEncoderListEncode() throws IOException {
        List<BufferedImage> frames = new ArrayList<>();
        int[] delays = new int[]{100, 100};

        BufferedImage f1 = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g1 = f1.createGraphics();
        g1.setColor(Color.RED);
        g1.fillRect(0, 0, 40, 40);
        g1.dispose();

        BufferedImage f2 = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = f2.createGraphics();
        g2.setColor(Color.BLUE);
        g2.fillRect(0, 0, 40, 40);
        g2.dispose();

        frames.add(f1);
        frames.add(f2);

        byte[] animBytes = AnimatedWebPEncoder.encode(frames, delays, new GifToWebPConfig());
        assertNotNull(animBytes);
        assertTrue(animBytes.length > 0);

        AnimatedWebPData decoded = WebPCodec.decodeAnimatedWebP(animBytes);
        assertEquals(40, decoded.getCanvasWidth());
        assertEquals(40, decoded.getCanvasHeight());
        assertEquals(2, decoded.getFrameCount());
    }
}
