package xyz.lychee.webp4j;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xyz.lychee.webp4j.chunks.ANMFChunk;
import xyz.lychee.webp4j.chunks.Chunk;
import xyz.lychee.webp4j.chunks.ChunkFactory;
import xyz.lychee.webp4j.chunks.VP8XChunk;
import xyz.lychee.webp4j.codec.AnimatedWebPDecoder;
import xyz.lychee.webp4j.codec.AnimatedWebPEncoder;
import xyz.lychee.webp4j.codec.BatchProcessor;
import xyz.lychee.webp4j.codec.GifToWebPConverter;
import xyz.lychee.webp4j.codec.WebPDecoder;
import xyz.lychee.webp4j.codec.WebPEncoder;
import xyz.lychee.webp4j.model.AnimatedWebPData;
import xyz.lychee.webp4j.model.AnimatedWebPFrame;
import xyz.lychee.webp4j.model.GifToWebPConfig;
import xyz.lychee.webp4j.model.WebPBitstreamFeatures;
import xyz.lychee.webp4j.model.WebPEncoderOptions;
import xyz.lychee.webp4j.natives.NativeLibraryLoader;
import xyz.lychee.webp4j.utils.RiffScanner;
import xyz.lychee.webp4j.utils.Util;
import xyz.lychee.webp4j.utils.WebPLimits;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Regression tests for the defects found in the code review: hostile container sizes, unbounded
 * allocation, duplicated output, silent decode failures, GIF composition and the frame offset format.
 */
class RegressionTest {

    @AfterEach
    void resetLimits() {
        WebPLimits.reset();
    }

    // ------------------------------------------------------------------ helpers

    private static byte[] chunk(String id, long declaredSize, byte[] payload) {
        ByteBuffer b = ByteBuffer.allocate(8 + payload.length).order(ByteOrder.LITTLE_ENDIAN);
        b.put(id.getBytes()).putInt((int) declaredSize).put(payload);
        return b.array();
    }

    private static byte[] riff(byte[]... chunks) {
        int n = 4;
        for (byte[] c : chunks) n += c.length;
        ByteBuffer b = ByteBuffer.allocate(8 + n).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(n).put("WEBP".getBytes());
        for (byte[] c : chunks) b.put(c);
        return b.array();
    }

    private static BufferedImage image(int w, int h, int type, long seed) {
        BufferedImage img = new BufferedImage(w, h, type);
        Random rnd = new Random(seed);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = (type == BufferedImage.TYPE_INT_ARGB ? (128 + rnd.nextInt(128)) << 24 : 0xFF000000)
                        | (x * 255 / Math.max(1, w - 1)) << 16 | (y * 255 / Math.max(1, h - 1)) << 8 | rnd.nextInt(256);
                img.setRGB(x, y, argb);
            }
        }
        return img;
    }

    private static void assertSingleRiff(byte[] data) {
        assertEquals('R', data[0]);
        long declared = (ByteBuffer.wrap(data, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xFFFFFFFFL) + 8;
        assertEquals(data.length, declared, "output must contain exactly one RIFF container");
    }

    private static int[] pixels(BufferedImage img) {
        return img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
    }

    // ------------------------------------------------------------------ K1: hostile chunk sizes

    @Test
    void hostileChunkSizeNeverHangsTheContainerScanners() {
        byte[] vp8x = chunk("VP8X", 10, new byte[]{0, 0, 0, 0, 9, 0, 0, 9, 0, 0});
        byte[] animVp8x = chunk("VP8X", 10, new byte[]{2, 0, 0, 0, 9, 0, 0, 9, 0, 0});
        byte[] evil = chunk("XXXX", 0xFFFFFFF8L, new byte[8]);
        byte[] oddEvil = chunk("YYYY", 0xFFFFFFFFL, new byte[8]);

        for (byte[] size : new byte[][]{evil, oddEvil}) {
            byte[] still = riff(vp8x, size, new byte[32]);
            byte[] anim = riff(animVp8x, size, new byte[32]);

            assertTimeoutPreemptively(Duration.ofSeconds(5), () -> { WebPBitstreamFeatures.parse(still); });
            assertTimeoutPreemptively(Duration.ofSeconds(5), () -> { AnimatedWebPDecoder.getAnimationInfo(anim); });
            assertTimeoutPreemptively(Duration.ofSeconds(5), () -> { ChunkFactory.parseAll(still); });
            assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
                assertThrows(IOException.class, () -> WebPDecoder.decode(still));
            });
        }
    }

    @Test
    void scannerAdvancesAndClampsOversizedChunks() throws IOException {
        byte[] data = riff(chunk("AAAA", 4, new byte[4]), chunk("BBBB", 0xFFFFFFF0L, new byte[10]));
        RiffScanner s = RiffScanner.open(data);
        assertTrue(s.next());
        assertEquals(4, s.payloadLength());
        assertTrue(s.next());
        assertEquals(10, s.payloadLength(), "oversized payload is clamped to the container");
        assertEquals(0xFFFFFFF0L, s.declaredSize());
        assertFalse(s.next());
    }

    // ------------------------------------------------------------------ K2: allocation limits

    @Test
    void headerDeclaringHugeImageIsRejectedBeforeAllocation() {
        // VP8L signature + 14-bit width/height fields all ones => 16384 x 16384
        byte[] vp8l = {0x2F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x0F, 0, 0, 0};
        byte[] file = riff(chunk("VP8L", 8, vp8l));
        IOException e = assertThrows(IOException.class, () -> WebPDecoder.decode(file));
        assertTrue(e.getMessage().contains("pixel limit"), e.getMessage());
    }

    @Test
    void customPixelLimitIsHonoured() throws IOException {
        byte[] webp = WebPEncoder.encodeLossless(image(40, 40, BufferedImage.TYPE_INT_RGB, 1));
        WebPLimits.setMaxPixels(1000);
        assertThrows(IOException.class, () -> WebPDecoder.decode(webp));
        WebPLimits.setMaxPixels(1600);
        assertEquals(40, WebPDecoder.decode(webp).getWidth());
    }

    @Test
    void animationTotalPixelLimitIsCheckedBeforeDecoding() throws IOException {
        List<BufferedImage> frames = new ArrayList<>();
        for (int i = 0; i < 4; i++) frames.add(image(30, 30, BufferedImage.TYPE_INT_ARGB, i));
        byte[] anim = AnimatedWebPEncoder.encode(frames, 50, GifToWebPConfig.createLosslessConfig());

        WebPLimits.setMaxTotalPixels(30 * 30 * 3);
        IOException e = assertThrows(IOException.class, () -> AnimatedWebPDecoder.decode(anim));
        assertTrue(e.getMessage().contains("total pixel limit"), e.getMessage());

        WebPLimits.setMaxTotalPixels(30 * 30 * 4);
        assertEquals(4, AnimatedWebPDecoder.decode(anim).getFrames().size());

        WebPLimits.setMaxFrames(3);
        assertThrows(IOException.class, () -> AnimatedWebPDecoder.decode(anim));
    }

    @Test
    void readAllBytesEnforcesTheInputLimit() throws IOException {
        byte[] data = new byte[5000];
        assertEquals(5000, Util.readAllBytes(new ByteArrayInputStream(data)).length);
        assertEquals(0, Util.readAllBytes(new ByteArrayInputStream(new byte[0])).length);

        WebPLimits.setMaxInputBytes(4999);
        assertThrows(IOException.class, () -> Util.readAllBytes(new ByteArrayInputStream(data)));
        WebPLimits.setMaxInputBytes(5000);
        assertEquals(5000, Util.readAllBytes(new ByteArrayInputStream(data)).length);
    }

    // ------------------------------------------------------------------ K3 / N2: single, safe output

    @Test
    void animatedEncoderWritesExactlyOneContainer() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (AnimatedWebPEncoder encoder = new AnimatedWebPEncoder(out, true, 24, 24)) {
            encoder.setFrame(0, image(24, 24, BufferedImage.TYPE_INT_ARGB, 1), 100, 80f);
            encoder.setFrame(1, image(24, 24, BufferedImage.TYPE_INT_ARGB, 2), 100, 80f);
        }
        assertSingleRiff(out.toByteArray());
        assertEquals(2, AnimatedWebPDecoder.decode(out.toByteArray()).getFrames().size());
    }

    @Test
    void closeIsIdempotent() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        AnimatedWebPEncoder encoder = new AnimatedWebPEncoder(out, false, 16, 16);
        encoder.setFrame(0, image(16, 16, BufferedImage.TYPE_INT_RGB, 1), 100, 80f);
        encoder.close();
        int size = out.size();
        encoder.close();
        assertEquals(size, out.size());
        assertThrows(IllegalStateException.class,
                () -> encoder.setFrame(1, image(16, 16, BufferedImage.TYPE_INT_RGB, 2), 100, 80f));
    }

    @Test
    void failedFrameMeansNothingIsWritten() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThrows(IllegalArgumentException.class, () -> {
            try (AnimatedWebPEncoder encoder = new AnimatedWebPEncoder(out, false, 16, 16)) {
                encoder.setFrame(0, image(16, 16, BufferedImage.TYPE_INT_RGB, 1), 100, 80f);
                encoder.setFrame(1, image(16, 16, BufferedImage.TYPE_INT_RGB, 2), 100, 500f); // invalid quality
            }
        });
        assertEquals(0, out.size(), "a half-built animation must never reach the stream");
    }

    @Test
    void missingFrameAndEmptyAnimationAreReported() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        AnimatedWebPEncoder gap = new AnimatedWebPEncoder(out, false, 16, 16);
        gap.setFrame(1, image(16, 16, BufferedImage.TYPE_INT_RGB, 1), 100, 80f);
        assertThrows(IOException.class, gap::close);

        AnimatedWebPEncoder empty = new AnimatedWebPEncoder(new ByteArrayOutputStream(), false, 16, 16);
        assertThrows(IllegalStateException.class, empty::close);
    }

    @Test
    void frameOutsideTheCanvasIsRejected() throws IOException {
        AnimatedWebPEncoder encoder = new AnimatedWebPEncoder(new ByteArrayOutputStream(), true, 16, 16);
        encoder.setFrame(0, new int[20 * 20], 0, 0, 20, 20, 100, 0, 80f, true);
        assertThrows(IOException.class, encoder::close);
    }

    @Test
    void imageIoSequenceWriterWritesOneContainer() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("webp").next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            writer.prepareWriteSequence(null);
            for (int i = 0; i < 3; i++) {
                writer.writeToSequence(new IIOImage(image(20, 20, BufferedImage.TYPE_INT_ARGB, i), null, null),
                        writer.getDefaultWriteParam());
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
        assertSingleRiff(out.toByteArray());
        assertEquals(3, AnimatedWebPDecoder.getFrameCount(out.toByteArray()));
    }

    @Test
    void imageIoReaderReadsEveryFrameConsistently() throws IOException {
        List<BufferedImage> frames = new ArrayList<>();
        for (int i = 0; i < 4; i++) frames.add(image(16, 16, BufferedImage.TYPE_INT_ARGB, 10 + i));
        byte[] anim = AnimatedWebPEncoder.encode(frames, 40, GifToWebPConfig.createLosslessConfig());
        AnimatedWebPData all = AnimatedWebPDecoder.decode(anim);

        ImageReader reader = ImageIO.getImageReadersByFormatName("webp").next();
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(anim))) {
            reader.setInput(iis);
            assertEquals(4, reader.getNumImages(true));
            for (int i = 3; i >= 0; i--) {   // out of order on purpose
                assertArrayEquals(pixels(all.getFrame(i).getImage()), pixels(reader.read(i)), "frame " + i);
            }
        } finally {
            reader.dispose();
        }
    }

    // ------------------------------------------------------------------ K4: decode into any destination

    @Test
    void decodeIntoDestinationWorksForEveryImageType() throws IOException {
        BufferedImage src = image(24, 17, BufferedImage.TYPE_INT_ARGB, 7);
        byte[] webp = WebPEncoder.encodeLossless(src);
        int[] expected = pixels(WebPDecoder.decode(webp));

        int[] types = {BufferedImage.TYPE_INT_ARGB, BufferedImage.TYPE_INT_RGB, BufferedImage.TYPE_3BYTE_BGR,
                BufferedImage.TYPE_4BYTE_ABGR, BufferedImage.TYPE_INT_ARGB_PRE};
        for (int type : types) {
            BufferedImage dst = new BufferedImage(24, 17, type);
            WebPDecoder.decode(webp, dst);
            assertNotEquals(0, dst.getRGB(5, 5), "destination type " + type + " stayed empty");
            if (type == BufferedImage.TYPE_INT_ARGB || type == BufferedImage.TYPE_4BYTE_ABGR) {
                assertArrayEquals(expected, pixels(dst), "destination type " + type);
            }
        }
    }

    // ------------------------------------------------------------------ K6: frame offsets

    @Test
    void frameOffsetIsStoredDividedByTwo() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (AnimatedWebPEncoder encoder = new AnimatedWebPEncoder(out, true, 32, 32)) {
            encoder.setFrame(0, new int[8 * 8], 6, 10, 8, 8, 100, 0, 80f, true);
        }
        byte[] file = out.toByteArray();

        RiffScanner s = RiffScanner.open(file);
        boolean found = false;
        while (s.next()) {
            if (s.fourCC() == RiffScanner.ANMF) {
                int off = s.payloadOffset();
                assertEquals(3, Util.toUInt24(file, off), "x is stored as x / 2");
                assertEquals(5, Util.toUInt24(file, off + 3), "y is stored as y / 2");
                found = true;
            }
        }
        assertTrue(found);

        for (Chunk c : ChunkFactory.parseAll(file)) {
            if (c instanceof ANMFChunk) {
                assertEquals(6, ((ANMFChunk) c).getFrameX());
                assertEquals(10, ((ANMFChunk) c).getFrameY());
            }
        }
        assertEquals(1, AnimatedWebPDecoder.decode(file).getFrames().size());
    }

    @Test
    void oddOffsetsAndInvalidSizesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ANMFChunk(1, 0, 8, 8, 100, 0));
        assertThrows(IllegalArgumentException.class, () -> new ANMFChunk(0, 3, 8, 8, 100, 0));
        assertThrows(IllegalArgumentException.class, () -> new ANMFChunk(0, 0, 0, 8, 100, 0));
        assertThrows(IllegalArgumentException.class, () -> new VP8XChunk(0, 0, 8));
        assertDoesNotThrow(() -> new VP8XChunk(0, 1 << 24, 1));
    }

    // ------------------------------------------------------------------ first frame / metadata

    @Test
    void firstFrameFastPathMatchesFullDecoding() throws IOException {
        for (boolean lossless : new boolean[]{true, false}) {
            List<BufferedImage> frames = new ArrayList<>();
            for (int i = 0; i < 3; i++) frames.add(image(33, 21, BufferedImage.TYPE_INT_ARGB, 100 + i));
            GifToWebPConfig config = lossless ? GifToWebPConfig.createLosslessConfig() : GifToWebPConfig.createLossyConfig(70f);
            byte[] anim = AnimatedWebPEncoder.encode(frames, 60, config);

            AnimatedWebPFrame fast = AnimatedWebPDecoder.decodeFirstFrame(anim);
            AnimatedWebPFrame full = AnimatedWebPDecoder.decode(anim).getFrame(0);
            assertArrayEquals(pixels(full.getImage()), pixels(fast.getImage()), "lossless=" + lossless);
            assertEquals(full.getTimestamp(), fast.getTimestamp());
            assertEquals(full.getDuration(), fast.getDuration());
            assertEquals(33, WebPDecoder.createThumbnail(anim, 200).getWidth());
        }
    }

    @Test
    void metadataIsReadFromFilesWithoutLoadingThem(@TempDir Path dir) throws IOException {
        byte[] exif = {1, 2, 3, 4, 5};
        byte[] icc = new byte[300];
        Arrays.fill(icc, (byte) 9);
        byte[] webp = WebPEncoder.encodeWithMetadata(image(50, 40, BufferedImage.TYPE_INT_ARGB, 3), 80f, false,
                exif, icc, "<x:xmpmeta/>");
        Path file = dir.resolve("meta.webp");
        Files.write(file, webp);

        assertArrayEquals(exif, WebPDecoder.getExifMetadata(file.toFile()));
        assertArrayEquals(icc, WebPDecoder.getIccProfile(file.toFile()));
        assertEquals("<x:xmpmeta/>", WebPDecoder.getXmpMetadata(file.toFile()));
        assertArrayEquals(exif, WebPDecoder.getExifMetadata(webp));
        assertTrue(WebPDecoder.hasAlpha(file));
        assertArrayEquals(new int[]{50, 40}, WebPDecoder.getInfo(file));
        assertFalse(WebPDecoder.isAnimated(file));
        assertNull(WebPDecoder.getExifMetadata(new File(dir.toFile(), "missing.webp")));
    }

    // ------------------------------------------------------------------ extended encoder options

    @Test
    void encoderOptionsProduceValidSmallerOutput() throws IOException {
        BufferedImage img = image(96, 96, BufferedImage.TYPE_INT_ARGB, 5);
        byte[] basic = WebPEncoder.encode(img, WebPEncoderOptions.lossless());
        assertArrayEquals(pixels(img), pixels(WebPDecoder.decode(basic)));

        byte[] best;
        try {
            best = WebPEncoder.encode(img, WebPEncoderOptions.lossless().setMethod(6).setQuality(100).setExact(true));
        } catch (UnsupportedOperationException oldNative) {
            assumeTrue(false, "native library without the extended encoder");
            return;
        }
        assertTrue(best.length <= basic.length, "method 6 / effort 100 must not be larger than the default");
        assertArrayEquals(pixels(img), pixels(WebPDecoder.decode(best)));

        byte[] lossy = WebPEncoder.encode(img, WebPEncoderOptions.lossy(60f).setSharpYuv(true).setAlphaQuality(80).setMethod(5));
        WebPBitstreamFeatures f = WebPDecoder.getBitstreamFeatures(lossy);
        assertTrue(f.isLossy());
        assertEquals(96, f.getWidth());

        byte[] nl = WebPEncoder.encode(img, WebPEncoderOptions.lossless().setNearLossless(60));
        assertTrue(WebPDecoder.getBitstreamFeatures(nl).isLossless());

        assertThrows(IllegalArgumentException.class, () -> WebPEncoderOptions.lossy(101f));
        assertThrows(IllegalArgumentException.class, () -> WebPEncoderOptions.lossless().setMethod(7));
    }

    @Test
    void stillImagesStayLossyAtQuality100AndNaNIsRejected() throws IOException {
        BufferedImage img = image(32, 32, BufferedImage.TYPE_INT_RGB, 8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        WebPEncoder.encode(img, 100f, out);
        assertTrue(WebPDecoder.getBitstreamFeatures(out.toByteArray()).isLossy());
        assertThrows(IllegalArgumentException.class, () -> WebPEncoder.encode(img, Float.NaN, false));
    }

    // ------------------------------------------------------------------ GIF

    private static IIOMetadataNode child(IIOMetadataNode root, String name) {
        for (int i = 0; i < root.getLength(); i++) {
            if (root.item(i).getNodeName().equals(name)) return (IIOMetadataNode) root.item(i);
        }
        IIOMetadataNode node = new IIOMetadataNode(name);
        root.appendChild(node);
        return node;
    }

    private static final class GifFrameSpec {
        final BufferedImage image;
        final int x, y, disposal;

        GifFrameSpec(BufferedImage image, int x, int y, int disposal) {
            this.image = image;
            this.x = x;
            this.y = y;
            this.disposal = disposal;
        }
    }

    private static byte[] writeGif(List<GifFrameSpec> frames, boolean interlaced) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (interlaced) {
                param.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
            }
            writer.prepareWriteSequence(null);
            String[] disposals = {"none", "doNotDispose", "restoreToBackgroundColor", "restoreToPrevious"};
            for (GifFrameSpec f : frames) {
                IIOMetadata meta = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(f.image), param);
                String format = meta.getNativeMetadataFormatName();
                IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree(format);
                IIOMetadataNode gce = child(root, "GraphicControlExtension");
                gce.setAttribute("disposalMethod", disposals[f.disposal]);
                gce.setAttribute("userInputFlag", "FALSE");
                gce.setAttribute("transparentColorFlag", "FALSE");
                gce.setAttribute("delayTime", "10");
                gce.setAttribute("transparentColorIndex", "0");
                IIOMetadataNode desc = child(root, "ImageDescriptor");
                desc.setAttribute("imageLeftPosition", Integer.toString(f.x));
                desc.setAttribute("imageTopPosition", Integer.toString(f.y));
                meta.setFromTree(format, root);
                writer.writeToSequence(new IIOImage(f.image, null, meta), param);
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private static BufferedImage solid(int w, int h, int rgb) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) img.setRGB(x, y, rgb);
        return img;
    }

    @Test
    void interlacedGifIsDeinterlaced() throws IOException {
        int w = 8, h = 20;
        BufferedImage src = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            int rgb = (y * 12) << 16 | (255 - y * 12) << 8 | 0x40;   // a distinct colour per row
            for (int x = 0; x < w; x++) src.setRGB(x, y, rgb);
        }
        byte[] gif = writeGif(Collections.singletonList(new GifFrameSpec(src, 0, 0, 0)), true);

        // The test is only meaningful if the writer really produced an interlaced image.
        boolean interlaced;
        ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(gif))) {
            reader.setInput(iis);
            IIOMetadataNode root = (IIOMetadataNode) reader.getImageMetadata(0).getAsTree("javax_imageio_gif_image_1.0");
            interlaced = "TRUE".equalsIgnoreCase(child(root, "ImageDescriptor").getAttribute("interlaceFlag"));
        } finally {
            reader.dispose();
        }
        assumeTrue(interlaced, "GIF writer did not produce an interlaced image");

        GifToWebPConfig config = GifToWebPConfig.createLosslessConfig().setExtractFirstFrameOnly(true);
        for (byte[] webp : new byte[][]{GifToWebPConverter.convert(gif, config),
                GifToWebPConverter.convertUsingJavaImageIO(gif, config)}) {
            BufferedImage decoded = WebPDecoder.decode(webp);
            for (int y = 0; y < h; y++) {
                assertEquals(src.getRGB(3, y) & 0xFFFFFF, decoded.getRGB(3, y) & 0xFFFFFF, "row " + y);
            }
        }
    }

    @Test
    void disposeToBackgroundClearsOnlyTheFrameRectangle() throws IOException {
        List<GifFrameSpec> frames = new ArrayList<>();
        frames.add(new GifFrameSpec(solid(8, 8, 0xFFFF00), 0, 0, 1));   // full canvas, kept
        frames.add(new GifFrameSpec(solid(2, 2, 0x0000FF), 0, 0, 2));   // sub-rectangle, restored to background
        frames.add(new GifFrameSpec(solid(2, 2, 0xFF0000), 6, 6, 1));   // drawn after the disposal
        byte[] gif = writeGif(frames, false);

        GifToWebPConfig config = GifToWebPConfig.createLosslessConfig().setKmin(0).setKmax(1);
        for (byte[] webp : new byte[][]{GifToWebPConverter.convert(gif, config),
                GifToWebPConverter.convertUsingJavaImageIO(gif, config)}) {
            AnimatedWebPData anim = AnimatedWebPDecoder.decode(webp);
            assertEquals(3, anim.getFrames().size());
            BufferedImage last = anim.getFrame(2).getImage();
            assertEquals(0xFFFFFF00, last.getRGB(4, 4), "content outside the disposed rectangle must survive");
            assertEquals(0, last.getRGB(0, 0) >>> 24, "the disposed rectangle is transparent");
            assertEquals(0xFFFF0000, last.getRGB(7, 7));
        }
    }

    @Test
    void gifInfoCountsFramesWithoutDecoding() throws IOException {
        List<GifFrameSpec> frames = new ArrayList<>();
        for (int i = 0; i < 5; i++) frames.add(new GifFrameSpec(solid(10, 6, i * 40), 0, 0, 1));
        byte[] gif = writeGif(frames, false);
        assertEquals(5, GifToWebPConverter.getInfo(gif).getFrameCount());
        assertEquals(10, GifToWebPConverter.getInfo(gif).getWidth());
        assertEquals(6, GifToWebPConverter.getInfo(gif).getHeight());
    }

    @Test
    void corruptGifFailsCleanly() {
        byte[] garbage = "GIF89a\u0010\u0000\u0010\u0000\u0000\u0000\u0000;".getBytes();
        assertThrows(IOException.class, () -> GifToWebPConverter.convert(garbage));
    }

    // ------------------------------------------------------------------ batch

    @Test
    void batchFailsFastWithTheFailingIndex() throws IOException {
        List<byte[]> data = new ArrayList<>();
        byte[] good = WebPEncoder.encodeLossless(image(64, 64, BufferedImage.TYPE_INT_RGB, 1));
        for (int i = 0; i < 12; i++) data.add(good);
        data.set(7, new byte[]{1, 2, 3});
        IOException e = assertThrows(IOException.class, () -> BatchProcessor.decodeImages(data));
        assertTrue(e.getMessage().contains("index 7"), e.getMessage());
    }

    @Test
    void streamingBatchPreservesOrderWithBoundedWindow() throws IOException {
        List<BufferedImage> images = new ArrayList<>();
        for (int i = 0; i < 25; i++) images.add(image(8 + i, 8, BufferedImage.TYPE_INT_RGB, i));

        List<Integer> widths = new ArrayList<>();
        BatchProcessor.encodeEach(images.iterator(), 80f, false,
                (index, bytes) -> widths.add(WebPDecoder.getInfo(bytes)[0]));
        assertEquals(25, widths.size());
        for (int i = 0; i < 25; i++) assertEquals(8 + i, widths.get(i));

        Iterator<BufferedImage> broken = Arrays.asList(images.get(0), null).iterator();
        assertThrows(IllegalArgumentException.class, () -> BatchProcessor.encodeEach(broken, 80f, false, (i, b) -> { }));
    }

    @Test
    void imageIoReadParamRegionSubsamplingAndDestinationOffset() throws IOException {
        BufferedImage src = image(40, 30, BufferedImage.TYPE_INT_RGB, 21);
        byte[] webp = WebPEncoder.encodeLossless(src);
        int[] full = pixels(WebPDecoder.decode(webp));

        ImageReader reader = ImageIO.getImageReadersByFormatName("webp").next();
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(webp))) {
            reader.setInput(iis);

            javax.imageio.ImageReadParam region = reader.getDefaultReadParam();
            region.setSourceRegion(new java.awt.Rectangle(4, 6, 20, 10));
            region.setSourceSubsampling(2, 2, 0, 0);
            BufferedImage part = reader.read(0, region);
            assertEquals(10, part.getWidth());
            assertEquals(5, part.getHeight());
            assertEquals(full[(6 + 2) * 40 + (4 + 4)] & 0xFFFFFF, part.getRGB(2, 1) & 0xFFFFFF);

            BufferedImage dest = new BufferedImage(50, 40, BufferedImage.TYPE_INT_RGB);
            javax.imageio.ImageReadParam placed = reader.getDefaultReadParam();
            placed.setDestination(dest);
            placed.setDestinationOffset(new java.awt.Point(7, 5));
            assertSame(dest, reader.read(0, placed));
            assertEquals(full[3 * 40 + 9] & 0xFFFFFF, dest.getRGB(7 + 9, 5 + 3) & 0xFFFFFF);
            assertEquals(0, dest.getRGB(2, 2) & 0xFFFFFF);
        } finally {
            reader.dispose();
        }
    }

    // ------------------------------------------------------------------ native loader

    @Test
    void bundledLibraryIsExtractedIntoAContentAddressedCache() {
        File lib = NativeLibraryLoader.getLoadedLibraryFile();
        if (lib == null) {
            // Another test class may not have touched the natives yet.
            assertTrue(WebPCodec.isAvailable());
            lib = NativeLibraryLoader.getLoadedLibraryFile();
        }
        assumeTrue(lib != null, "library was loaded from java.library.path or webp4j.native.path");
        assertTrue(lib.getParentFile().getName().startsWith("webp4j-"), lib.toString());
        assertTrue(lib.isFile());
    }
}
