package xyz.lychee.webp4j.common;

import xyz.lychee.webp4j.model.AnimatedWebPData;
import xyz.lychee.webp4j.model.AnimationInfo;
import xyz.lychee.webp4j.model.WebPBitstreamFeatures;

/**
 * Dynamic native bridge that routes calls to the best available backend.
 * <p>
 * On Java 22+ when the optional {@code webp4j-native-ffm} module is present,
 * operations route to Foreign Function &amp; Memory (FFM).
 * Otherwise, the bridge falls back cleanly to the JNI backend.
 */
public final class NativeBridge {

    private static final WebPBackend BACKEND;

    static {
        WebPBackend chosen = null;
        String preferred = System.getProperty("webp4j.backend");

        if (!"jni".equalsIgnoreCase(preferred) && getJavaMajorVersion() >= 22) {
            try {
                Class<?> ffmClass = Class.forName("xyz.lychee.webp4j.ffm.FFMWebPBackend");
                WebPBackend ffmBackend = (WebPBackend) ffmClass.getDeclaredConstructor().newInstance();
                if (ffmBackend.isAvailable()) {
                    chosen = ffmBackend;
                }
            } catch (Throwable ignored) {
            }
        }

        if (chosen == null && !"ffm".equalsIgnoreCase(preferred)) {
            try {
                Class<?> jniClass = Class.forName("xyz.lychee.webp4j.internal.JNIWebPBackend");
                WebPBackend jniBackend = (WebPBackend) jniClass.getDeclaredConstructor().newInstance();
                if (jniBackend.isAvailable()) {
                    chosen = jniBackend;
                }
            } catch (Throwable ignored) {
            }
        }

        if (chosen == null) {
            chosen = new UnavailableWebPBackend();
        }

        BACKEND = chosen;
    }

    private NativeBridge() {
        throw new AssertionError("Cannot instantiate utility class.");
    }

    public static WebPBackend getBackend() {
        return BACKEND;
    }

    public static boolean isFFM() {
        return BACKEND.isFFM();
    }

    public static String getBackendName() {
        return BACKEND.getBackendName();
    }

    public static boolean isAvailable() {
        return BACKEND.isAvailable();
    }

    public static int libWebPVersionNumber() {
        return BACKEND.libWebPVersionNumber();
    }

    public static String libWebPVersion() {
        return BACKEND.libWebPVersion();
    }

    public static Throwable unavailabilityCause() {
        return BACKEND.unavailabilityCause();
    }

    public static boolean getInfo(byte[] data, int[] dimensions) {
        return BACKEND.getInfo(data, dimensions);
    }

    public static int getFeatures(byte[] data, int dataSize, WebPBitstreamFeatures features) {
        return BACKEND.getFeatures(data, dataSize, features);
    }

    public static byte[] encode(int[] pixels, int width, int height,
                                float quality, boolean lossless, boolean hasAlpha,
                                boolean multiThreaded) {
        return BACKEND.encode(pixels, width, height, quality, lossless, hasAlpha, multiThreaded);
    }

    public static byte[] encodeBgr(byte[] pixels, int width, int height,
                                   float quality, boolean lossless, boolean multiThreaded) {
        return BACKEND.encodeBgr(pixels, width, height, quality, lossless, multiThreaded);
    }

    public static boolean decodeInto(byte[] data, int[] output, int outputStride) {
        return BACKEND.decodeInto(data, output, outputStride);
    }

    public static boolean getGifInfo(byte[] gifData, AnimationInfo info) {
        return BACKEND.getGifInfo(gifData, info);
    }

    public static byte[] encodeGifToWebP(byte[] gifData,
                                         float quality,
                                         boolean lossless,
                                         int compressionMethod,
                                         boolean extractFirstFrameOnly,
                                         int loopCount,
                                         int kmin,
                                         int kmax,
                                         boolean minimizeSize,
                                         boolean allowMixed,
                                         boolean multiThreaded) {
        return BACKEND.encodeGifToWebP(
                gifData, quality, lossless, compressionMethod,
                extractFirstFrameOnly, loopCount, kmin, kmax,
                minimizeSize, allowMixed, multiThreaded
        );
    }

    public static byte[] encodeAnimated(int[][] frames,
                                        int[] delays,
                                        int width,
                                        int height,
                                        float quality,
                                        boolean lossless,
                                        int compressionMethod,
                                        int loopCount,
                                        int kmin,
                                        int kmax,
                                        boolean minimizeSize,
                                        boolean allowMixed,
                                        boolean multiThreaded) {
        return BACKEND.encodeAnimated(
                frames, delays, width, height, quality,
                lossless, compressionMethod, loopCount, kmin, kmax,
                minimizeSize, allowMixed, multiThreaded
        );
    }

    public static boolean decodeAnimated(byte[] webPData, AnimatedWebPData result) {
        return BACKEND.decodeAnimated(webPData, result);
    }

    public static int getJavaMajorVersion() {
        String version = System.getProperty("java.specification.version");
        if (version == null) {
            version = System.getProperty("java.version", "8");
        }
        if (version.startsWith("1.")) {
            version = version.substring(2);
        }
        int dotIndex = version.indexOf('.');
        if (dotIndex != -1) {
            version = version.substring(0, dotIndex);
        }
        try {
            return Integer.parseInt(version);
        } catch (NumberFormatException e) {
            return 8;
        }
    }

    private static final class UnavailableWebPBackend implements WebPBackend {

        private final Throwable cause = new IllegalStateException(
                "No WebP native backend is available. Ensure 'webp4j-native-jni' or 'webp4j-native-ffm' is on the classpath."
        );

        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public String getBackendName() {
            return "NONE";
        }

        @Override
        public boolean isFFM() {
            return false;
        }

        @Override
        public int libWebPVersionNumber() {
            return 0;
        }

        @Override
        public String libWebPVersion() {
            return null;
        }

        @Override
        public Throwable unavailabilityCause() {
            return cause;
        }

        @Override
        public boolean getInfo(byte[] data, int[] dimensions) {
            return false;
        }

        @Override
        public int getFeatures(byte[] data, int dataSize, WebPBitstreamFeatures features) {
            return -1;
        }

        @Override
        public byte[] encode(int[] pixels, int width, int height, float quality, boolean lossless, boolean hasAlpha, boolean multiThreaded) {
            return null;
        }

        @Override
        public byte[] encodeBgr(byte[] pixels, int width, int height, float quality, boolean lossless, boolean multiThreaded) {
            return null;
        }

        @Override
        public boolean decodeInto(byte[] data, int[] output, int outputStride) {
            return false;
        }

        @Override
        public boolean getGifInfo(byte[] gifData, AnimationInfo info) {
            return false;
        }

        @Override
        public byte[] encodeGifToWebP(byte[] gifData, float quality, boolean lossless, int compressionMethod, boolean extractFirstFrameOnly, int loopCount, int kmin, int kmax, boolean minimizeSize, boolean allowMixed, boolean multiThreaded) {
            return null;
        }

        @Override
        public byte[] encodeAnimated(int[][] frames, int[] delays, int width, int height, float quality, boolean lossless, int compressionMethod, int loopCount, int kmin, int kmax, boolean minimizeSize, boolean allowMixed, boolean multiThreaded) {
            return null;
        }

        @Override
        public boolean decodeAnimated(byte[] webPData, AnimatedWebPData result) {
            return false;
        }
    }
}
