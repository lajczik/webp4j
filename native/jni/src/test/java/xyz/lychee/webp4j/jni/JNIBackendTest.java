package xyz.lychee.webp4j.jni;

import org.junit.jupiter.api.Test;
import xyz.lychee.webp4j.common.WebPBackend;
import xyz.lychee.webp4j.model.WebPBitstreamFeatures;

import static org.junit.jupiter.api.Assertions.*;

public class JNIBackendTest {

    @Test
    public void testJNIBackendOperations() {
        assertTrue(NativeWebP.isJniAvailable(), "NativeWebP JNI should be available: " + NativeWebP.jniUnavailabilityCause());
        assertTrue(NativeWebP.jniLibWebPVersionNumber() > 0);

        WebPBackend backend = new JNIWebPBackend();
        assertTrue(backend.isAvailable());
        assertFalse(backend.isFFM());
        assertEquals("JNI", backend.getBackendName());
        assertNotNull(backend.libWebPVersion());

        int width = 32;
        int height = 24;
        int[] pixels = new int[width * height];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = 0xFF123456;
        }

        byte[] encoded = backend.encode(pixels, width, height, 80f, false, false, false);
        assertNotNull(encoded, "Encoding via JNI should produce bytes");
        assertTrue(encoded.length > 0);

        int[] dims = new int[2];
        boolean infoOk = backend.getInfo(encoded, dims);
        assertTrue(infoOk);
        assertEquals(width, dims[0]);
        assertEquals(height, dims[1]);

        WebPBitstreamFeatures features = new WebPBitstreamFeatures();
        int status = backend.getFeatures(encoded, encoded.length, features);
        assertEquals(0, status);
        assertEquals(width, features.getWidth());
        assertEquals(height, features.getHeight());

        int[] decoded = new int[width * height];
        boolean decodeOk = backend.decodeInto(encoded, decoded, width * 4);
        assertTrue(decodeOk);
    }
}
