package xyz.lychee.webp4j;

import xyz.lychee.webp4j.codec.AnimatedWebPDecoder;
import xyz.lychee.webp4j.chunks.*;
import xyz.lychee.webp4j.model.AnimationInfo;
import xyz.lychee.webp4j.model.WebPBitstreamFeatures;
import xyz.lychee.webp4j.model.WebPContainerFlag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WebPChunkTest {

    private static final String TEST_RGB_WEBP = "src/test/resources/test_rgb.webp";

    @Test
    void testParseChunksFromRealWebP() throws IOException {
        byte[] data = Files.readAllBytes(Paths.get(TEST_RGB_WEBP));
        List<Chunk> chunks = WebPCodec.getChunks(data);

        assertNotNull(chunks);
        assertFalse(chunks.isEmpty());
        assertTrue(chunks.get(0) instanceof VP8Chunk, "First chunk of test_rgb.webp should be VP8");

        VP8Chunk vp8 = (VP8Chunk) chunks.get(0);
        assertTrue(vp8.getSize() > 0);
        assertNotNull(vp8.getRawData());
    }

    @Test
    void testBitstreamFeaturesChunkParser() throws IOException {
        byte[] data = Files.readAllBytes(Paths.get(TEST_RGB_WEBP));
        WebPBitstreamFeatures features = WebPBitstreamFeatures.parse(data);

        assertNotNull(features);
        assertEquals(2248, features.getWidth());
        assertEquals(1442, features.getHeight());
        assertEquals(1, features.getFormat()); // 1 = lossy VP8
        assertFalse(features.isHasAlpha());
        assertFalse(features.isHasAnimation());
    }

    @Test
    void testWebPCodecInfoViaChunksWithoutJNI() throws IOException {
        byte[] data = Files.readAllBytes(Paths.get(TEST_RGB_WEBP));
        int[] dims = WebPCodec.getWebPInfo(data);

        assertNotNull(dims);
        assertEquals(2, dims.length);
        assertEquals(2248, dims[0]);
        assertEquals(1442, dims[1]);

        WebPBitstreamFeatures features = WebPCodec.getBitstreamFeatures(data);
        assertNotNull(features);
        assertEquals(2248, features.getWidth());
        assertEquals(1442, features.getHeight());
        assertEquals(1, features.getFormat());
    }

    @Test
    void testIsAnimatedOnStaticImage() throws IOException {
        byte[] data = Files.readAllBytes(Paths.get(TEST_RGB_WEBP));
        assertFalse(WebPCodec.isAnimated(data));
    }

    @Test
    void testChunkAssemblyAndSerialization() throws IOException {
        VP8XChunk vp8x = new VP8XChunk(400, 300, WebPContainerFlag.ANIMATION, WebPContainerFlag.ALPHA);
        assertEquals(400, vp8x.getCanvasWidth());
        assertEquals(300, vp8x.getCanvasHeight());
        assertTrue(vp8x.hasANIMFlag());
        assertTrue(vp8x.hasALPHFlag());
        assertTrue(vp8x.hasFlag(WebPContainerFlag.ANIMATION));
        assertTrue(vp8x.hasFlag(WebPContainerFlag.ALPHA));

        ANIMChunk anim = new ANIMChunk(0xFFFFFFFF, 5);
        assertEquals(0xFFFFFFFF, anim.getBackgroundColor());
        assertEquals(5, anim.getLoopCount());

        byte[] fakeFrameData = new byte[]{1, 2, 3, 4};
        VP8Chunk vp8 = new VP8Chunk(fakeFrameData);
        ANMFChunk anmf = new ANMFChunk(0, 0, 400, 300, 100, 0, vp8);
        assertEquals(400, anmf.getFrameWidth());
        assertEquals(300, anmf.getFrameHeight());
        assertEquals(100, anmf.getFrameDuration());

        WebPChunk webp = new WebPChunk(vp8x, anim, anmf);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        webp.write(out);
        byte[] serialized = out.toByteArray();

        assertTrue(serialized.length > 12);
        assertEquals('R', (char) serialized[0]);
        assertEquals('I', (char) serialized[1]);
        assertEquals('F', (char) serialized[2]);
        assertEquals('F', (char) serialized[3]);
        assertEquals('W', (char) serialized[8]);
        assertEquals('E', (char) serialized[9]);
        assertEquals('B', (char) serialized[10]);
        assertEquals('P', (char) serialized[11]);

        // Round-trip parse
        List<Chunk> parsed = ChunkFactory.parseAll(serialized);
        assertEquals(3, parsed.size());
        assertTrue(parsed.get(0) instanceof VP8XChunk);
        assertTrue(parsed.get(1) instanceof ANIMChunk);
        assertTrue(parsed.get(2) instanceof ANMFChunk);

        // Metadata extraction
        AnimationInfo animInfo = AnimatedWebPDecoder.getAnimationInfo(serialized);
        assertEquals(400, animInfo.getWidth());
        assertEquals(300, animInfo.getHeight());
        assertEquals(5, animInfo.getLoopCount());
        assertEquals(1, animInfo.getFrameCount());
        assertTrue(animInfo.hasTransparency());
    }
}
