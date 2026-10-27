package xyz.lychee.webp4j.utils;

import xyz.lychee.webp4j.model.AnimationInfo;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Java-based GIF decoder using ImageIO.
 * This is used as a fallback when native giflib is unavailable,
 * or for testing purposes.
 *
 * @author MrNanko
 * @author lajczik
 */
public final class GifDecoderJava {

    private static final String GRAPHIC_CONTROL_EXTENSION = "GraphicControlExtension";

    /** Delay used when a GIF frame declares none (matches the native decoder and browsers). */
    private static final int DEFAULT_DELAY_MS = 100;

    private GifDecoderJava() {
        throw new AssertionError("Cannot instantiate utility class.");
    }

    /**
     * Represents a single frame in a GIF animation.
     */
    public static class GifFrame {
        public BufferedImage image;
        public int delayMs;
        public int disposeMethod;  // 0=NONE, 1=NONE, 2=BACKGROUND, 3=RESTORE_PREVIOUS
        public int leftOffset;
        public int topOffset;
    }

    /**
     * Holds all decoded GIF data including frames and metadata.
     */
    public static class GifData {
        public List<GifFrame> frames;
        public int loopCount;
        public int width;
        public int height;
        public boolean hasTransparency;
    }

    /** Per-frame values read from a single pass over the frame metadata tree. */
    private static final class FrameMeta {
        int delayMs = DEFAULT_DELAY_MS;
        int disposeMethod;
        int transparentIndex = -1;
        int left;
        int top;
    }

    /**
     * Decodes a GIF from a byte array using Java ImageIO.
     * Extracts all frames, delays, disposal methods, and loop count.
     *
     * @param gifBytes Byte array containing the GIF image data
     * @return GifData containing all frames and metadata
     * @throws IOException If decoding fails
     */
    public static GifData decodeGif(byte[] gifBytes) throws IOException {
        // A memory-backed stream: ImageIO.createImageInputStream would spill to a temporary file.
        try (ImageInputStream stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(gifBytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) {
                throw new IOException("No GIF image reader found");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(stream);

                GifData data = new GifData();
                data.frames = new ArrayList<>();

                int numImages = reader.getNumImages(true);
                if (numImages <= 0) {
                    throw new IOException("GIF contains no decodable frames");
                }
                if (numImages > WebPLimits.getMaxFrames()) {
                    throw new IOException("GIF has " + numImages + " frames, the limit is " + WebPLimits.getMaxFrames());
                }

                IIOMetadata streamMetadata = reader.getStreamMetadata();
                data.loopCount = extractLoopCount(streamMetadata);
                int[] screen = extractLogicalScreen(streamMetadata);

                for (int i = 0; i < numImages; i++) {
                    BufferedImage image = reader.read(i);
                    FrameMeta meta = readFrameMeta(reader.getImageMetadata(i));

                    GifFrame frame = new GifFrame();
                    frame.image = image;
                    frame.delayMs = meta.delayMs;
                    frame.disposeMethod = meta.disposeMethod;
                    frame.leftOffset = meta.left;
                    frame.topOffset = meta.top;

                    if (image.getColorModel().hasAlpha() || meta.transparentIndex >= 0) {
                        data.hasTransparency = true;
                    }

                    data.frames.add(frame);

                    if (i == 0) {
                        // The canvas is the logical screen; the first frame may be a smaller sub-rectangle.
                        data.width = screen != null ? screen[0] : image.getWidth();
                        data.height = screen != null ? screen[1] : image.getHeight();
                        WebPLimits.checkImage(data.width, data.height);
                    }
                }

                return data;
            } finally {
                reader.dispose();
            }
        }
    }

    /**
     * Reads the logical screen size (the animation canvas) from the stream metadata.
     *
     * @return {width, height}, or null if the metadata does not provide it
     */
    private static int[] extractLogicalScreen(IIOMetadata metadata) {
        if (metadata == null) {
            return null;
        }
        Node root = metadata.getAsTree(metadata.getNativeMetadataFormatName());
        NodeList nodes = ((IIOMetadataNode) root).getElementsByTagName("LogicalScreenDescriptor");
        if (nodes.getLength() == 0) {
            return null;
        }
        IIOMetadataNode node = (IIOMetadataNode) nodes.item(0);
        try {
            int w = Integer.parseInt(node.getAttribute("logicalScreenWidth"));
            int h = Integer.parseInt(node.getAttribute("logicalScreenHeight"));
            return w > 0 && h > 0 ? new int[]{w, h} : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Extracts loop count from GIF stream metadata.
     * Parses the ApplicationExtensions node for NETSCAPE2.0 loop extension.
     *
     * @param metadata Stream metadata
     * @return Loop count (0=infinite, N=loop N times)
     */
    private static int extractLoopCount(IIOMetadata metadata) {
        if (metadata == null) {
            return 0;  // Default: infinite loop
        }

        String nativeMetadataFormatName = metadata.getNativeMetadataFormatName();
        Node root = metadata.getAsTree(nativeMetadataFormatName);

        NodeList applicationExtensions = ((IIOMetadataNode) root).getElementsByTagName("ApplicationExtensions");
        if (applicationExtensions.getLength() == 0) {
            return 0;  // No loop extension, default to infinite
        }

        IIOMetadataNode appExtNode = (IIOMetadataNode) applicationExtensions.item(0);
        NodeList appExtChildren = appExtNode.getChildNodes();

        for (int i = 0; i < appExtChildren.getLength(); i++) {
            Node appExt = appExtChildren.item(i);
            int loopCount = extractLoopCountFromExtension(appExt);
            if (loopCount >= 0) {
                return loopCount;
            }
        }

        return 0;  // Default: infinite loop
    }

    /**
     * Extracts loop count from a single ApplicationExtension node.
     *
     * @param appExt ApplicationExtension node
     * @return Loop count if found, or -1 if not a valid NETSCAPE2.0 extension
     */
    private static int extractLoopCountFromExtension(Node appExt) {
        if (!"ApplicationExtension".equals(appExt.getNodeName())) {
            return -1;
        }

        NamedNodeMap attrs = appExt.getAttributes();
        Node appIdNode = attrs.getNamedItem("applicationID");
        Node authCodeNode = attrs.getNamedItem("authenticationCode");

        if (appIdNode == null || authCodeNode == null) {
            return -1;
        }

        String appId = appIdNode.getNodeValue();
        String authCode = authCodeNode.getNodeValue();

        // NETSCAPE2.0 extension contains loop count
        if (!"NETSCAPE".equals(appId) || !"2.0".equals(authCode)) {
            return -1;
        }

        if (!(appExt instanceof IIOMetadataNode)) {
            return -1;
        }

        byte[] appData = (byte[]) ((IIOMetadataNode) appExt).getUserObject();
        if (appData == null || appData.length < 3 || appData[0] != 1) {
            return -1;
        }

        // Loop count is stored in bytes 1-2 (little-endian)
        return (appData[1] & 0xFF) | ((appData[2] & 0xFF) << 8);
    }

    /**
     * Reads delay, disposal method, transparency and position of a frame in a single pass over
     * its metadata tree (building the tree is the expensive part).
     */
    private static FrameMeta readFrameMeta(IIOMetadata metadata) {
        FrameMeta meta = new FrameMeta();
        if (metadata == null) {
            return meta;
        }

        IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(metadata.getNativeMetadataFormatName());

        NodeList gces = root.getElementsByTagName(GRAPHIC_CONTROL_EXTENSION);
        if (gces.getLength() > 0) {
            IIOMetadataNode gce = (IIOMetadataNode) gces.item(0);

            String delay = gce.getAttribute("delayTime");
            if (!delay.isEmpty()) {
                try {
                    // delayTime is in 1/100 seconds; 0 means "unspecified"
                    int centis = Integer.parseInt(delay);
                    meta.delayMs = centis > 0 ? centis * 10 : DEFAULT_DELAY_MS;
                } catch (NumberFormatException ignored) {
                    // keep the default
                }
            }

            // disposalMethod: none, doNotDispose, restoreToBackgroundColor, restoreToPrevious
            String disposal = gce.getAttribute("disposalMethod");
            if ("restoreToBackgroundColor".equals(disposal)) {
                meta.disposeMethod = 2;
            } else if ("restoreToPrevious".equals(disposal)) {
                meta.disposeMethod = 3;
            }

            String flag = gce.getAttribute("transparentColorFlag");
            String index = gce.getAttribute("transparentColorIndex");
            if ("true".equals(flag) && !index.isEmpty()) {
                try {
                    meta.transparentIndex = Integer.parseInt(index);
                } catch (NumberFormatException ignored) {
                    // no transparency
                }
            }
        }

        NodeList descriptors = root.getElementsByTagName("ImageDescriptor");
        if (descriptors.getLength() > 0) {
            IIOMetadataNode descriptor = (IIOMetadataNode) descriptors.item(0);
            try {
                String left = descriptor.getAttribute("imageLeftPosition");
                String top = descriptor.getAttribute("imageTopPosition");
                if (!left.isEmpty()) {
                    meta.left = Integer.parseInt(left);
                }
                if (!top.isEmpty()) {
                    meta.top = Integer.parseInt(top);
                }
            } catch (NumberFormatException ignored) {
                // keep (0, 0)
            }
        }

        return meta;
    }

    /**
     * Gets GIF information without full decoding.
     * This is a lightweight operation for querying GIF properties.
     *
     * @param gifBytes Byte array containing the GIF image data
     * @param info AnimationInfo object to populate
     * @return true on success, false on failure
     */
    public static boolean getGifInfo(byte[] gifBytes, AnimationInfo info) {
        try (ImageInputStream stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(gifBytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) {
                return false;
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(stream);

                int numImages = reader.getNumImages(true);
                info.setFrameCount(numImages);

                IIOMetadata streamMetadata = reader.getStreamMetadata();
                int[] screen = extractLogicalScreen(streamMetadata);
                info.setWidth(screen != null ? screen[0] : reader.getWidth(0));
                info.setHeight(screen != null ? screen[1] : reader.getHeight(0));
                info.setLoopCount(extractLoopCount(streamMetadata));

                // Check for transparency in any frame
                boolean hasTransparency = false;
                for (int i = 0; i < numImages && !hasTransparency; i++) {
                    hasTransparency = readFrameMeta(reader.getImageMetadata(i)).transparentIndex >= 0;
                }
                info.setHasTransparency(hasTransparency);

                return true;
            } finally {
                reader.dispose();
            }
        } catch (Exception e) {
            return false;
        }
    }
}
