package xyz.lychee.webp4j;

import xyz.lychee.webp4j.codec.AnimatedWebPDecoder;
import xyz.lychee.webp4j.codec.AnimatedWebPEncoder;
import xyz.lychee.webp4j.codec.BatchProcessor;
import xyz.lychee.webp4j.chunks.Chunk;
import xyz.lychee.webp4j.model.GifToWebPConfig;
import xyz.lychee.webp4j.codec.GifToWebPConverter;
import xyz.lychee.webp4j.natives.NativeWebP;
import xyz.lychee.webp4j.model.AnimatedWebPData;
import xyz.lychee.webp4j.model.AnimatedWebPFrame;
import xyz.lychee.webp4j.model.AnimationInfo;
import xyz.lychee.webp4j.model.WebPBitstreamFeatures;
import xyz.lychee.webp4j.codec.WebPDecoder;
import xyz.lychee.webp4j.codec.WebPEncoder;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;

/**
 * Main facade class for WebP encoding, decoding, conversion, and inspection operations.
 *
 * @author MrNanko
 * @author lajczik
 * @since 1.4.0
 */
public final class WebPCodec {

    private WebPCodec() {
        throw new AssertionError("Cannot instantiate utility class.");
    }

    // ============================================
    // Platform Availability & Native Info
    // ============================================

    public static boolean isAvailable() {
        try {
            return NativeWebP.isAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    public static String libWebPVersion() {
        try {
            return NativeWebP.libWebPVersion();
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean isFFM() {
        try {
            return NativeWebP.isFFM();
        } catch (Throwable t) {
            return false;
        }
    }

    public static String getBackendName() {
        try {
            return NativeWebP.getBackendName();
        } catch (Throwable t) {
            return "JNI";
        }
    }

    // ============================================
    // WebP Information & Inspection (WebPDecoder)
    // ============================================

    public static int[] getWebPInfo(byte[] webPData) throws IOException {
        return WebPDecoder.getInfo(webPData);
    }

    public static int[] getWebPInfo(File file) throws IOException {
        return WebPDecoder.getInfo(file);
    }

    public static int[] getWebPInfo(Path path) throws IOException {
        return WebPDecoder.getInfo(path);
    }

    public static int[] getWebPInfo(InputStream in) throws IOException {
        return WebPDecoder.getInfo(in);
    }

    public static int[] getWebPInfo(ByteBuffer buffer) throws IOException {
        return WebPDecoder.getInfo(buffer);
    }

    public static WebPBitstreamFeatures getBitstreamFeatures(byte[] webPData) throws IOException {
        return WebPDecoder.getBitstreamFeatures(webPData);
    }

    public static WebPBitstreamFeatures getBitstreamFeatures(File file) throws IOException {
        return WebPDecoder.getBitstreamFeatures(file);
    }

    public static WebPBitstreamFeatures getBitstreamFeatures(Path path) throws IOException {
        return WebPDecoder.getBitstreamFeatures(path);
    }

    public static WebPBitstreamFeatures getBitstreamFeatures(InputStream in) throws IOException {
        return WebPDecoder.getBitstreamFeatures(in);
    }

    public static WebPBitstreamFeatures getBitstreamFeatures(ByteBuffer buffer) throws IOException {
        return WebPDecoder.getBitstreamFeatures(buffer);
    }

    public static List<Chunk> getChunks(byte[] webPData) throws IOException {
        return WebPDecoder.getChunks(webPData);
    }

    public static List<Chunk> getChunks(File file) throws IOException {
        return WebPDecoder.getChunks(file);
    }

    public static List<Chunk> getChunks(Path path) throws IOException {
        return WebPDecoder.getChunks(path);
    }

    public static boolean isAnimated(byte[] webPData) {
        return WebPDecoder.isAnimated(webPData);
    }

    public static boolean isAnimated(File file) {
        return WebPDecoder.isAnimated(file);
    }

    public static boolean isAnimated(Path path) {
        return WebPDecoder.isAnimated(path);
    }

    public static boolean isAnimated(InputStream in) {
        return WebPDecoder.isAnimated(in);
    }

    public static boolean isAnimated(ByteBuffer buffer) {
        return WebPDecoder.isAnimated(buffer);
    }

    public static byte[] getExifMetadata(byte[] webPData) {
        return WebPDecoder.getExifMetadata(webPData);
    }

    public static byte[] getExifMetadata(File file) {
        return WebPDecoder.getExifMetadata(file);
    }

    public static byte[] getIccProfile(byte[] webPData) {
        return WebPDecoder.getIccProfile(webPData);
    }

    public static byte[] getIccProfile(File file) {
        return WebPDecoder.getIccProfile(file);
    }

    public static String getXmpMetadata(byte[] webPData) {
        return WebPDecoder.getXmpMetadata(webPData);
    }

    public static String getXmpMetadata(File file) {
        return WebPDecoder.getXmpMetadata(file);
    }

    // ============================================
    // Static Image Encoding (WebPEncoder)
    // ============================================

    public static byte[] encodeImageWithMetadata(BufferedImage image, float quality, boolean lossless,
                                                 byte[] exif, byte[] iccp, String xmp) throws IOException {
        return WebPEncoder.encodeWithMetadata(image, quality, lossless, exif, iccp, xmp);
    }

    public static void encodeImageWithMetadata(BufferedImage image, File file, float quality, boolean lossless,
                                              byte[] exif, byte[] iccp, String xmp) throws IOException {
        WebPEncoder.encodeWithMetadata(image, file, quality, lossless, exif, iccp, xmp);
    }

    public static void encodeImageWithMetadata(BufferedImage image, Path path, float quality, boolean lossless,
                                              byte[] exif, byte[] iccp, String xmp) throws IOException {
        WebPEncoder.encodeWithMetadata(image, path, quality, lossless, exif, iccp, xmp);
    }

    public static byte[] encodeImageWithMetadata(BufferedImage image, xyz.lychee.webp4j.model.WebPEncoderOptions options) throws IOException {
        return WebPEncoder.encodeWithMetadata(image, options);
    }

    public static void encodeImageWithMetadata(BufferedImage image, File file, xyz.lychee.webp4j.model.WebPEncoderOptions options) throws IOException {
        WebPEncoder.encodeWithMetadata(image, file, options);
    }

    public static void encodeImageWithMetadata(BufferedImage image, Path path, xyz.lychee.webp4j.model.WebPEncoderOptions options) throws IOException {
        WebPEncoder.encodeWithMetadata(image, path, options);
    }

    public static byte[] encodeImage(BufferedImage bufferedImage, float quality, boolean lossless) throws IOException {
        return WebPEncoder.encode(bufferedImage, quality, lossless);
    }

    /**
     * Encodes an image with fine-grained libwebp options, see {@link xyz.lychee.webp4j.model.WebPEncoderOptions}.
     */
    public static byte[] encodeImage(BufferedImage bufferedImage, xyz.lychee.webp4j.model.WebPEncoderOptions options) throws IOException {
        return WebPEncoder.encode(bufferedImage, options);
    }

    public static void encodeImage(BufferedImage image, File file, xyz.lychee.webp4j.model.WebPEncoderOptions options) throws IOException {
        WebPEncoder.encode(image, file, options);
    }

    public static void encodeImage(BufferedImage image, Path path, xyz.lychee.webp4j.model.WebPEncoderOptions options) throws IOException {
        WebPEncoder.encode(image, path, options);
    }

    public static void encodeImage(BufferedImage image, OutputStream out, xyz.lychee.webp4j.model.WebPEncoderOptions options) throws IOException {
        WebPEncoder.encode(image, out, options);
    }

    public static byte[] encodeImage(BufferedImage bufferedImage, float quality, boolean lossless,
                                     boolean multiThreaded) throws IOException {
        return WebPEncoder.encode(bufferedImage, quality, lossless, multiThreaded);
    }

    public static byte[] encodeImage(BufferedImage bufferedImage, float quality) throws IOException {
        return WebPEncoder.encode(bufferedImage, quality);
    }

    public static byte[] encodeLosslessImage(BufferedImage bufferedImage) throws IOException {
        return WebPEncoder.encodeLossless(bufferedImage);
    }

    public static void encodeImage(BufferedImage image, File file, float quality) throws IOException {
        WebPEncoder.encode(image, file, quality);
    }

    public static void encodeImage(BufferedImage image, File file, float quality, boolean lossless) throws IOException {
        WebPEncoder.encode(image, file, quality, lossless);
    }

    public static void encodeImage(BufferedImage image, Path path, float quality) throws IOException {
        WebPEncoder.encode(image, path, quality);
    }

    public static void encodeImage(BufferedImage image, Path path, float quality, boolean lossless) throws IOException {
        WebPEncoder.encode(image, path, quality, lossless);
    }

    public static void encodeLosslessImage(BufferedImage image, File file) throws IOException {
        WebPEncoder.encodeLossless(image, file);
    }

    public static void encodeLosslessImage(BufferedImage image, Path path) throws IOException {
        WebPEncoder.encodeLossless(image, path);
    }

    public static void encodeImage(BufferedImage image, float quality, OutputStream out) throws IOException {
        WebPEncoder.encode(image, quality, out);
    }

    public static void encodeImage(BufferedImage image, float quality, boolean lossless, OutputStream out) throws IOException {
        WebPEncoder.encode(image, quality, lossless, out);
    }

    public static void encodeLosslessImage(BufferedImage image, OutputStream out) throws IOException {
        WebPEncoder.encodeLossless(image, out);
    }

    // ============================================
    // Static Image Decoding (WebPDecoder)
    // ============================================

    public static BufferedImage decodeImage(byte[] webPData) throws IOException {
        return WebPDecoder.decode(webPData);
    }

    public static void decodeImage(byte[] webPData, BufferedImage destination) throws IOException {
        WebPDecoder.decode(webPData, destination);
    }

    public static BufferedImage decodeImage(File file) throws IOException {
        return WebPDecoder.decode(file);
    }

    public static BufferedImage decodeImage(Path path) throws IOException {
        return WebPDecoder.decode(path);
    }

    public static BufferedImage decodeImage(InputStream in) throws IOException {
        return WebPDecoder.decode(in);
    }

    public static BufferedImage decodeImage(ByteBuffer buffer) throws IOException {
        return WebPDecoder.decode(buffer);
    }

    public static BufferedImage decodeFirstFrame(byte[] webPData) throws IOException {
        return WebPDecoder.decodeFirstFrame(webPData);
    }

    public static BufferedImage decodeFirstFrame(File file) throws IOException {
        return WebPDecoder.decodeFirstFrame(file);
    }

    public static BufferedImage decodeFirstFrame(Path path) throws IOException {
        return WebPDecoder.decodeFirstFrame(path);
    }

    public static BufferedImage decodeFirstFrame(InputStream in) throws IOException {
        return WebPDecoder.decodeFirstFrame(in);
    }

    public static BufferedImage createThumbnail(byte[] webPData, int maxDimension) throws IOException {
        return WebPDecoder.createThumbnail(webPData, maxDimension);
    }

    public static BufferedImage createThumbnail(File file, int maxDimension) throws IOException {
        return WebPDecoder.createThumbnail(file, maxDimension);
    }

    public static BufferedImage createThumbnail(Path path, int maxDimension) throws IOException {
        return WebPDecoder.createThumbnail(path, maxDimension);
    }

    public static boolean hasAlpha(byte[] webPData) {
        return WebPDecoder.hasAlpha(webPData);
    }

    public static boolean hasAlpha(File file) {
        return WebPDecoder.hasAlpha(file);
    }

    public static boolean hasAlpha(Path path) {
        return WebPDecoder.hasAlpha(path);
    }

    public static boolean hasAlpha(InputStream in) {
        return WebPDecoder.hasAlpha(in);
    }

    // ============================================
    // Batch Processing (BatchProcessor)
    // ============================================

    public static List<byte[]> encodeImages(List<BufferedImage> images, float quality, boolean lossless) throws IOException {
        return BatchProcessor.encodeImages(images, quality, lossless);
    }

    public static List<byte[]> encodeImages(List<BufferedImage> images, float quality) throws IOException {
        return BatchProcessor.encodeImages(images, quality);
    }

    public static List<byte[]> encodeLosslessImages(List<BufferedImage> images) throws IOException {
        return BatchProcessor.encodeLosslessImages(images);
    }

    public static List<BufferedImage> decodeImages(List<byte[]> encodedImages) throws IOException {
        return BatchProcessor.decodeImages(encodedImages);
    }

    // ============================================
    // GIF to WebP Conversion (GifToWebPConverter)
    // ============================================

    public static AnimationInfo getGifInfo(byte[] gifData) throws IOException {
        return GifToWebPConverter.getInfo(gifData);
    }

    public static AnimationInfo getGifInfo(File file) throws IOException {
        return GifToWebPConverter.getInfo(file);
    }

    public static AnimationInfo getGifInfo(InputStream in) throws IOException {
        return GifToWebPConverter.getInfo(in);
    }

    public static byte[] encodeGifToWebP(byte[] gifData) throws IOException {
        return GifToWebPConverter.convert(gifData);
    }

    public static byte[] encodeGifToWebP(byte[] gifData, GifToWebPConfig config) throws IOException {
        return GifToWebPConverter.convert(gifData, config);
    }

    public static byte[] encodeGifToWebPLossless(byte[] gifData) throws IOException {
        return GifToWebPConverter.convertLossless(gifData);
    }

    public static void encodeGifToWebP(File gifFile, File webpFile) throws IOException {
        GifToWebPConverter.convert(gifFile, webpFile);
    }

    public static void encodeGifToWebP(File gifFile, File webpFile, GifToWebPConfig config) throws IOException {
        GifToWebPConverter.convert(gifFile, webpFile, config);
    }

    public static void encodeGifToWebP(Path gifPath, Path webpPath) throws IOException {
        GifToWebPConverter.convert(gifPath, webpPath);
    }

    public static void encodeGifToWebP(Path gifPath, Path webpPath, GifToWebPConfig config) throws IOException {
        GifToWebPConverter.convert(gifPath, webpPath, config);
    }

    // ============================================
    // Animated WebP Creation (AnimatedWebPEncoder)
    // ============================================

    public static byte[] createAnimatedWebP(List<BufferedImage> frames, int[] delays, GifToWebPConfig config) throws IOException {
        return AnimatedWebPEncoder.encode(frames, delays, config);
    }

    public static byte[] createAnimatedWebPWithOptions(List<BufferedImage> frames, int[] delays, xyz.lychee.webp4j.model.WebPEncoderOptions options) throws IOException {
        return AnimatedWebPEncoder.encodeWithOptions(frames, delays, options);
    }

    public static byte[] createAnimatedWebP(List<BufferedImage> frames, int delay, GifToWebPConfig config) throws IOException {
        return AnimatedWebPEncoder.encode(frames, delay, config);
    }

    public static byte[] createAnimatedWebPWithOptions(List<BufferedImage> frames, int delay, xyz.lychee.webp4j.model.WebPEncoderOptions options) throws IOException {
        return AnimatedWebPEncoder.encodeWithOptions(frames, delay, options);
    }

    public static void createAnimatedWebP(OutputStream out, List<BufferedImage> frames, int[] delays, GifToWebPConfig config) throws IOException {
        AnimatedWebPEncoder.encode(out, frames, delays, config);
    }

    public static void createAnimatedWebPWithOptions(OutputStream out, List<BufferedImage> frames, int[] delays, xyz.lychee.webp4j.model.WebPEncoderOptions options) throws IOException {
        AnimatedWebPEncoder.encodeWithOptions(out, frames, delays, options);
    }

    public static void createAnimatedWebP(File file, List<BufferedImage> frames, int[] delays, GifToWebPConfig config) throws IOException {
        AnimatedWebPEncoder.encode(file, frames, delays, config);
    }

    public static void createAnimatedWebPWithOptions(File file, List<BufferedImage> frames, int[] delays, xyz.lychee.webp4j.model.WebPEncoderOptions options) throws IOException {
        AnimatedWebPEncoder.encodeWithOptions(file, frames, delays, options);
    }

    public static void createAnimatedWebP(Path path, List<BufferedImage> frames, int[] delays, GifToWebPConfig config) throws IOException {
        AnimatedWebPEncoder.encode(path, frames, delays, config);
    }

    public static void createAnimatedWebPWithOptions(Path path, List<BufferedImage> frames, int[] delays, xyz.lychee.webp4j.model.WebPEncoderOptions options) throws IOException {
        AnimatedWebPEncoder.encodeWithOptions(path, frames, delays, options);
    }

    // ============================================
    // Animated WebP Decoding & Metadata (AnimatedWebPDecoder)
    // ============================================

    public static AnimationInfo getAnimatedWebPInfo(byte[] webPData) throws IOException {
        return AnimatedWebPDecoder.getAnimationInfo(webPData);
    }

    public static AnimationInfo getAnimatedWebPInfo(File file) throws IOException {
        return AnimatedWebPDecoder.getAnimationInfo(file);
    }

    public static AnimationInfo getAnimatedWebPInfo(Path path) throws IOException {
        return AnimatedWebPDecoder.getAnimationInfo(path);
    }

    public static AnimationInfo getAnimatedWebPInfo(InputStream in) throws IOException {
        return AnimatedWebPDecoder.getAnimationInfo(in);
    }

    public static int getFrameCount(byte[] webPData) {
        return AnimatedWebPDecoder.getFrameCount(webPData);
    }

    public static int getFrameCount(File file) {
        return AnimatedWebPDecoder.getFrameCount(file);
    }

    public static AnimatedWebPData decodeAnimatedWebP(byte[] webPData) throws IOException {
        return AnimatedWebPDecoder.decode(webPData);
    }

    public static AnimatedWebPData decodeAnimatedWebP(File file) throws IOException {
        return AnimatedWebPDecoder.decode(file);
    }

    public static AnimatedWebPData decodeAnimatedWebP(Path path) throws IOException {
        return AnimatedWebPDecoder.decode(path);
    }

    public static AnimatedWebPData decodeAnimatedWebP(InputStream in) throws IOException {
        return AnimatedWebPDecoder.decode(in);
    }

    public static List<BufferedImage> decodeAnimatedWebPImages(byte[] webPData) throws IOException {
        return AnimatedWebPDecoder.decodeImages(webPData);
    }

    public static AnimatedWebPFrame decodeAnimatedWebPFrame(byte[] webPData, int frameIndex) throws IOException {
        return AnimatedWebPDecoder.decodeFrame(webPData, frameIndex);
    }
}
