package dev.matrixlab.webp4j;

import dev.matrixlab.webp4j.internal.NativeWebP;
import dev.matrixlab.webp4j.model.WebPBitstreamFeatures;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

public class FFMWebPTest {

    @Test
    public void testFFMBackendActiveOnJava22Plus() {
        String javaVersion = System.getProperty("java.specification.version");
        int major = 8;
        try {
            major = Integer.parseInt(javaVersion.contains(".") ? javaVersion.split("\\.")[1] : javaVersion);
        } catch (NumberFormatException ignored) {
        }

        if (major >= 22) {
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
}
