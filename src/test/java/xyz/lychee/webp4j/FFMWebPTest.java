package xyz.lychee.webp4j;

import xyz.lychee.webp4j.natives.NativeWebP;
import xyz.lychee.webp4j.model.WebPBitstreamFeatures;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

public class FFMWebPTest {

    @Test
    public void testFFMBackendActiveOnJava22Plus() {
        String javaVersion = System.getProperty("java.specification.version");
        // The testJni Gradle task leaves the Java 22 classes off the classpath and sets this property.
        boolean forcedJni = "jni".equalsIgnoreCase(System.getProperty("webp4j.backend"));
        int major = 8;
        try {
            major = Integer.parseInt(javaVersion.contains(".") ? javaVersion.split("\\.")[1] : javaVersion);
        } catch (NumberFormatException ignored) {
        }

        if (major >= 22 && !forcedJni) {
            if (NativeWebP.unavailabilityCause() != null) {
                NativeWebP.unavailabilityCause().printStackTrace();
            }
            assertTrue(NativeWebP.isFFM(), "Expected FFM backend to be active on Java 22+ (running on " + javaVersion + ")");
            assertEquals("FFM", NativeWebP.getBackendName());
            assertTrue(WebPCodec.isFFM());
            assertEquals("FFM", WebPCodec.getBackendName());
            assertTrue(NativeWebP.isAvailable(), "FFM backend should be available: " + NativeWebP.unavailabilityCause());
        } else {
            assertFalse(NativeWebP.isFFM(), "Expected JNI backend on Java < 22");
            assertEquals("JNI", NativeWebP.getBackendName());
        }
    }

    @Test
    public void testGetInfoAndFeaturesViaFFMBackend() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/test_rgb.webp")) {
            assertNotNull(in, "test_rgb.webp must exist in test resources");
            byte[] bytes = new byte[in.available()];
            int read = in.read(bytes);
            assertEquals(bytes.length, read);

            int[] dims = new int[2];
            boolean infoOk = NativeWebP.getInfo(bytes, dims);
            assertTrue(infoOk);
            assertEquals(2248, dims[0]);
            assertEquals(1442, dims[1]);

            WebPBitstreamFeatures features = new WebPBitstreamFeatures();
            int status = NativeWebP.getFeatures(bytes, bytes.length, features);
            assertEquals(0, status);
            assertEquals(2248, features.getWidth());
            assertEquals(1442, features.getHeight());
            assertFalse(features.isHasAlpha());
            assertFalse(features.isHasAnimation());
        }
    }

    @Test
    public void testDecodeAnimatedWebP() throws Exception {
        int width = 100;
        int height = 100;
        int frameCount = 3;
        java.util.List<java.awt.image.BufferedImage> frames = new java.util.ArrayList<>();
        int[] delays = new int[frameCount];

        java.awt.Color[] colors = {java.awt.Color.RED, java.awt.Color.GREEN, java.awt.Color.BLUE};
        for (int i = 0; i < frameCount; i++) {
            java.awt.image.BufferedImage frame = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g2d = frame.createGraphics();
            g2d.setColor(colors[i]);
            g2d.fillRect(0, 0, width, height);
            g2d.dispose();
            frames.add(frame);
            delays[i] = 100;
        }

        byte[] webPData = WebPCodec.createAnimatedWebP(frames, delays, null);
        assertNotNull(webPData);
        assertTrue(webPData.length > 0);

        xyz.lychee.webp4j.model.AnimatedWebPData result = new xyz.lychee.webp4j.model.AnimatedWebPData();
        boolean ok = NativeWebP.decodeAnimated(webPData, result);
        assertTrue(ok, "NativeWebP.decodeAnimated should succeed");
        assertEquals(width, result.getCanvasWidth());
        assertEquals(height, result.getCanvasHeight());
        assertEquals(frameCount, result.getFrameCount());
        assertNotNull(result.getFramePixels());
        assertEquals(frameCount, result.getFramePixels().length);
        assertEquals(width * height, result.getFramePixels()[0].length);
        assertNotNull(result.getTimestamps());
        assertEquals(frameCount, result.getTimestamps().length);

        xyz.lychee.webp4j.model.AnimatedWebPData highLevel = WebPCodec.decodeAnimatedWebP(webPData);
        assertNotNull(highLevel);
        assertEquals(frameCount, highLevel.getFrames().size());
        assertEquals(width, highLevel.getFrames().get(0).getImage().getWidth());
        assertEquals(height, highLevel.getFrames().get(0).getImage().getHeight());
    }
}
