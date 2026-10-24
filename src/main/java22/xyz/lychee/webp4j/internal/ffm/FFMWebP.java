package xyz.lychee.webp4j.internal.ffm;

import xyz.lychee.webp4j.internal.NativeLibraryLoader;
import xyz.lychee.webp4j.model.AnimatedWebPData;
import xyz.lychee.webp4j.model.AnimationInfo;
import xyz.lychee.webp4j.model.WebPBitstreamFeatures;

import java.io.File;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.util.Optional;

/**
 * Foreign Function &amp; Memory (FFM) API implementation for Java 22+.
 * <p>
 * Replaces legacy JNI with type-safe, direct downcalls via {@link Linker}
 * and off-heap/pinned memory accesses via {@link MemorySegment}.
 */
public final class FFMWebP {

    private static final Throwable UNAVAILABILITY_CAUSE;
    private static final int VERSION;

    private static final MethodHandle MH_VERSION;
    private static final MethodHandle MH_GET_INFO;
    private static final MethodHandle MH_GET_FEATURES;
    private static final MethodHandle MH_ENCODE;
    private static final MethodHandle MH_ENCODE_BGR;
    private static final MethodHandle MH_ENCODE_BGRA_LIBWEBP;
    private static final MethodHandle MH_ENCODE_LOSSLESS_BGRA_LIBWEBP;
    private static final MethodHandle MH_ENCODE_BGR_LIBWEBP;
    private static final MethodHandle MH_ENCODE_LOSSLESS_BGR_LIBWEBP;
    private static final MethodHandle MH_DECODE_INTO;
    private static final MethodHandle MH_FREE;
    private static final MethodHandle MH_ENCODE_ANIMATED;
    private static final MethodHandle MH_FREE_ANIMATED;
    private static final MethodHandle MH_DECODE_ANIMATED;
    private static final MethodHandle MH_GET_GIF_INFO;
    private static final MethodHandle MH_ENCODE_GIF;

    private FFMWebP() {
        throw new AssertionError("Cannot instantiate utility class.");
    }

    public static boolean isAvailable() {
        return UNAVAILABILITY_CAUSE == null;
    }

    public static int getVersion() {
        return VERSION;
    }

    public static Throwable getUnavailabilityCause() {
        return UNAVAILABILITY_CAUSE;
    }

    public static boolean getInfo(byte[] data, int[] dimensions) {
        if (data == null || dimensions == null || dimensions.length < 2) {
            return false;
        }

        if (MH_GET_INFO != null) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment inSeg = arena.allocateFrom(ValueLayout.JAVA_BYTE, data);
                MemorySegment wPtr = arena.allocate(ValueLayout.JAVA_INT);
                MemorySegment hPtr = arena.allocate(ValueLayout.JAVA_INT);

                int ok = (int) MH_GET_INFO.invokeExact(inSeg, (long) data.length, wPtr, hPtr);
                if (ok != 0) {
                    dimensions[0] = wPtr.get(ValueLayout.JAVA_INT, 0);
                    dimensions[1] = hPtr.get(ValueLayout.JAVA_INT, 0);
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }

        // Pure Java chunk parse fallback
        try {
            WebPBitstreamFeatures feat = WebPBitstreamFeatures.parse(data);
            if (feat != null && feat.getWidth() > 0 && feat.getHeight() > 0) {
                dimensions[0] = feat.getWidth();
                dimensions[1] = feat.getHeight();
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    public static int getFeatures(byte[] data, int dataSize, WebPBitstreamFeatures features) {
        if (data == null || features == null) return -1;

        if (MH_GET_FEATURES != null) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment inSeg = arena.allocateFrom(ValueLayout.JAVA_BYTE, data);
                MemorySegment wPtr = arena.allocate(ValueLayout.JAVA_INT);
                MemorySegment hPtr = arena.allocate(ValueLayout.JAVA_INT);
                MemorySegment aPtr = arena.allocate(ValueLayout.JAVA_INT);
                MemorySegment animPtr = arena.allocate(ValueLayout.JAVA_INT);
                MemorySegment fmtPtr = arena.allocate(ValueLayout.JAVA_INT);

                int status = (int) MH_GET_FEATURES.invokeExact(
                        inSeg, (long) dataSize, wPtr, hPtr, aPtr, animPtr, fmtPtr
                );
                if (status == 0) {
                    features.setWidth(wPtr.get(ValueLayout.JAVA_INT, 0));
                    features.setHeight(hPtr.get(ValueLayout.JAVA_INT, 0));
                    features.setHasAlpha(aPtr.get(ValueLayout.JAVA_INT, 0) != 0);
                    features.setHasAnimation(animPtr.get(ValueLayout.JAVA_INT, 0) != 0);
                    features.setFormat(fmtPtr.get(ValueLayout.JAVA_INT, 0));
                    return 0;
                }
            } catch (Throwable ignored) {
            }
        }

        // Pure Java chunk parse fallback
        try {
            WebPBitstreamFeatures parsed = WebPBitstreamFeatures.parse(data);
            if (parsed != null) {
                features.setWidth(parsed.getWidth());
                features.setHeight(parsed.getHeight());
                features.setHasAlpha(parsed.isHasAlpha());
                features.setHasAnimation(parsed.isHasAnimation());
                features.setFormat(parsed.getFormat());
                return 0;
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    public static byte[] encode(int[] pixels, int width, int height,
                                float quality, boolean lossless, boolean hasAlpha,
                                boolean multiThreaded) {
        if (pixels == null || width <= 0 || height <= 0 || pixels.length != width * height) {
            return null;
        }

        if (MH_ENCODE != null && MH_FREE != null) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment pixelSeg = arena.allocateFrom(ValueLayout.JAVA_INT, pixels);
                MemorySegment outPtrPtr = arena.allocate(ValueLayout.ADDRESS);

                long size = (long) MH_ENCODE.invokeExact(
                        pixelSeg, width, height, quality,
                        lossless ? 1 : 0, hasAlpha ? 1 : 0, multiThreaded ? 1 : 0,
                        outPtrPtr
                );

                if (size <= 0) return null;
                MemorySegment outPtr = outPtrPtr.get(ValueLayout.ADDRESS, 0).reinterpret(size);
                byte[] res = outPtr.toArray(ValueLayout.JAVA_BYTE);
                MH_FREE.invokeExact(outPtr);
                return res;
            } catch (Throwable t) {
                return null;
            }
        }

        if (lossless ? (MH_ENCODE_LOSSLESS_BGRA_LIBWEBP != null) : (MH_ENCODE_BGRA_LIBWEBP != null)) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment pixelSeg = arena.allocateFrom(ValueLayout.JAVA_INT, pixels);
                MemorySegment outPtrPtr = arena.allocate(ValueLayout.ADDRESS);
                long size;
                if (lossless) {
                    size = (long) MH_ENCODE_LOSSLESS_BGRA_LIBWEBP.invokeExact(
                            pixelSeg, width, height, width * 4, outPtrPtr
                    );
                } else {
                    size = (long) MH_ENCODE_BGRA_LIBWEBP.invokeExact(
                            pixelSeg, width, height, width * 4, quality, outPtrPtr
                    );
                }
                if (size <= 0) return null;
                MemorySegment outPtr = outPtrPtr.get(ValueLayout.ADDRESS, 0).reinterpret(size);
                byte[] res = outPtr.toArray(ValueLayout.JAVA_BYTE);
                if (MH_FREE != null) {
                    MH_FREE.invokeExact(outPtr);
                }
                return res;
            } catch (Throwable t) {
                return null;
            }
        }

        return null;
    }

    public static byte[] encodeBgr(byte[] pixels, int width, int height,
                                   float quality, boolean lossless, boolean multiThreaded) {
        if (pixels == null || width <= 0 || height <= 0 || pixels.length != width * height * 3) {
            return null;
        }

        if (MH_ENCODE_BGR != null && MH_FREE != null) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment pixelSeg = arena.allocateFrom(ValueLayout.JAVA_BYTE, pixels);
                MemorySegment outPtrPtr = arena.allocate(ValueLayout.ADDRESS);

                long size = (long) MH_ENCODE_BGR.invokeExact(
                        pixelSeg, width, height, quality,
                        lossless ? 1 : 0, multiThreaded ? 1 : 0,
                        outPtrPtr
                );

                if (size <= 0) return null;
                MemorySegment outPtr = outPtrPtr.get(ValueLayout.ADDRESS, 0).reinterpret(size);
                byte[] res = outPtr.toArray(ValueLayout.JAVA_BYTE);
                MH_FREE.invokeExact(outPtr);
                return res;
            } catch (Throwable t) {
                return null;
            }
        }

        if (lossless ? (MH_ENCODE_LOSSLESS_BGR_LIBWEBP != null) : (MH_ENCODE_BGR_LIBWEBP != null)) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment pixelSeg = arena.allocateFrom(ValueLayout.JAVA_BYTE, pixels);
                MemorySegment outPtrPtr = arena.allocate(ValueLayout.ADDRESS);
                long size;
                if (lossless) {
                    size = (long) MH_ENCODE_LOSSLESS_BGR_LIBWEBP.invokeExact(
                            pixelSeg, width, height, width * 3, outPtrPtr
                    );
                } else {
                    size = (long) MH_ENCODE_BGR_LIBWEBP.invokeExact(
                            pixelSeg, width, height, width * 3, quality, outPtrPtr
                    );
                }
                if (size <= 0) return null;
                MemorySegment outPtr = outPtrPtr.get(ValueLayout.ADDRESS, 0).reinterpret(size);
                byte[] res = outPtr.toArray(ValueLayout.JAVA_BYTE);
                if (MH_FREE != null) {
                    MH_FREE.invokeExact(outPtr);
                }
                return res;
            } catch (Throwable t) {
                return null;
            }
        }

        return null;
    }

    public static boolean decodeInto(byte[] data, int[] output, int outputStride) {
        if (data == null || output == null || MH_DECODE_INTO == null) {
            return false;
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment inSeg = arena.allocateFrom(ValueLayout.JAVA_BYTE, data);
            MemorySegment nativeOut = arena.allocate(ValueLayout.JAVA_INT, output.length);
            long outSize = (long) output.length * 4L;

            Object res = MH_DECODE_INTO.invoke(inSeg, (long) data.length, nativeOut, outSize, outputStride);
            boolean ok = false;
            if (res instanceof Integer) {
                ok = ((Integer) res) != 0;
            } else if (res instanceof MemorySegment) {
                ok = !((MemorySegment) res).equals(MemorySegment.NULL);
            }
            if (ok) {
                MemorySegment.copy(nativeOut, ValueLayout.JAVA_INT, 0, output, 0, output.length);
                return true;
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    public static byte[] encodeAnimated(int[][] frames, int[] delays, int width, int height,
                                        float quality, boolean lossless, int compressionMethod,
                                        int loopCount, int kmin, int kmax,
                                        boolean minimizeSize, boolean allowMixed, boolean multiThreaded) {
        if (frames == null || delays == null || width <= 0 || height <= 0 ||
                frames.length == 0 || delays.length < frames.length) {
            return null;
        }

        if (MH_ENCODE_ANIMATED != null && MH_FREE_ANIMATED != null) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment framePtrs = arena.allocate(ValueLayout.ADDRESS, frames.length);
                for (int i = 0; i < frames.length; i++) {
                    MemorySegment fSeg = arena.allocateFrom(ValueLayout.JAVA_INT, frames[i]);
                    framePtrs.setAtIndex(ValueLayout.ADDRESS, i, fSeg);
                }
                MemorySegment delaysSeg = arena.allocateFrom(ValueLayout.JAVA_INT, delays);
                MemorySegment outPtrPtr = arena.allocate(ValueLayout.ADDRESS);

                long size = (long) MH_ENCODE_ANIMATED.invokeExact(
                        framePtrs, delaysSeg, frames.length, width, height, quality,
                        lossless ? 1 : 0, compressionMethod, loopCount, kmin, kmax,
                        minimizeSize ? 1 : 0, allowMixed ? 1 : 0, multiThreaded ? 1 : 0,
                        outPtrPtr
                );

                if (size <= 0) return null;
                MemorySegment outPtr = outPtrPtr.get(ValueLayout.ADDRESS, 0).reinterpret(size);
                byte[] res = outPtr.toArray(ValueLayout.JAVA_BYTE);
                MH_FREE_ANIMATED.invokeExact(outPtr);
                return res;
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    public static boolean decodeAnimated(byte[] webPData, AnimatedWebPData result) {
        if (webPData == null || result == null || MH_DECODE_ANIMATED == null) return false;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment inSeg = arena.allocateFrom(ValueLayout.JAVA_BYTE, webPData);
            MemorySegment cwPtr = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment chPtr = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment loopPtr = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment bgPtr = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment fcPtr = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment outPixelsPtr = arena.allocate(ValueLayout.ADDRESS);
            MemorySegment outTsPtr = arena.allocate(ValueLayout.ADDRESS);

            int ok = (int) MH_DECODE_ANIMATED.invokeExact(
                    inSeg, (long) webPData.length, cwPtr, chPtr, loopPtr, bgPtr, fcPtr, outPixelsPtr, outTsPtr
            );
            if (ok == 0) return false;

            int cw = cwPtr.get(ValueLayout.JAVA_INT, 0);
            int ch = chPtr.get(ValueLayout.JAVA_INT, 0);
            int loop = loopPtr.get(ValueLayout.JAVA_INT, 0);
            int bg = bgPtr.get(ValueLayout.JAVA_INT, 0);
            int fc = fcPtr.get(ValueLayout.JAVA_INT, 0);

            MemorySegment allPixelsSeg = outPixelsPtr.get(ValueLayout.ADDRESS, 0).reinterpret((long) fc * cw * ch * 4L);
            MemorySegment tsSeg = outTsPtr.get(ValueLayout.ADDRESS, 0).reinterpret((long) fc * 4L);

            int framePixels = cw * ch;
            int[][] frames = new int[fc][framePixels];
            for (int i = 0; i < fc; i++) {
                MemorySegment slice = allPixelsSeg.asSlice((long) i * framePixels * 4L, (long) framePixels * 4L);
                MemorySegment.copy(slice, ValueLayout.JAVA_INT, 0, frames[i], 0, framePixels);
            }

            int[] timestamps = new int[fc];
            MemorySegment.copy(tsSeg, ValueLayout.JAVA_INT, 0, timestamps, 0, fc);

            if (MH_FREE != null) {
                MH_FREE.invokeExact(allPixelsSeg);
                MH_FREE.invokeExact(tsSeg);
            }

            result.setCanvasWidth(cw);
            result.setCanvasHeight(ch);
            result.setLoopCount(loop);
            result.setBgcolor(bg);
            result.setFrameCount(fc);
            result.setFramePixels(frames);
            result.setTimestamps(timestamps);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean getGifInfo(byte[] gifData, AnimationInfo info) {
        if (gifData == null || info == null || MH_GET_GIF_INFO == null) return false;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment inSeg = arena.allocateFrom(ValueLayout.JAVA_BYTE, gifData);
            MemorySegment wPtr = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment hPtr = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment fcPtr = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment loopPtr = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment transPtr = arena.allocate(ValueLayout.JAVA_INT);

            int ok = (int) MH_GET_GIF_INFO.invokeExact(
                    inSeg, (long) gifData.length, wPtr, hPtr, fcPtr, loopPtr, transPtr
            );
            if (ok == 0) return false;

            info.setWidth(wPtr.get(ValueLayout.JAVA_INT, 0));
            info.setHeight(hPtr.get(ValueLayout.JAVA_INT, 0));
            info.setFrameCount(fcPtr.get(ValueLayout.JAVA_INT, 0));
            info.setLoopCount(loopPtr.get(ValueLayout.JAVA_INT, 0));
            info.setHasTransparency(transPtr.get(ValueLayout.JAVA_INT, 0) != 0);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static byte[] encodeGifToWebP(byte[] gifData, float quality, boolean lossless,
                                         int compressionMethod, boolean extractFirstFrameOnly,
                                         int loopCount, int kmin, int kmax,
                                         boolean minimizeSize, boolean allowMixed, boolean multiThreaded) {
        if (gifData == null || gifData.length == 0 || MH_ENCODE_GIF == null) return null;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment inSeg = arena.allocateFrom(ValueLayout.JAVA_BYTE, gifData);
            MemorySegment outPtrPtr = arena.allocate(ValueLayout.ADDRESS);

            long size = (long) MH_ENCODE_GIF.invokeExact(
                    inSeg, (long) gifData.length, quality, lossless ? 1 : 0, compressionMethod,
                    extractFirstFrameOnly ? 1 : 0, loopCount, kmin, kmax,
                    minimizeSize ? 1 : 0, allowMixed ? 1 : 0, multiThreaded ? 1 : 0,
                    outPtrPtr
            );
            if (size <= 0) return null;
            MemorySegment outSeg = outPtrPtr.get(ValueLayout.ADDRESS, 0).reinterpret(size);
            byte[] res = outSeg.toArray(ValueLayout.JAVA_BYTE);
            if (MH_FREE != null) {
                MH_FREE.invokeExact(outSeg);
            }
            return res;
        } catch (Throwable t) {
            return null;
        }
    }

    private static Optional<MethodHandle> findHandle(Linker linker, SymbolLookup lookup,
                                                     String symbolName, FunctionDescriptor descriptor) {
        try {
            Optional<MemorySegment> symbol = lookup.find(symbolName);
            if (symbol.isPresent()) {
                return Optional.of(linker.downcallHandle(symbol.get(), descriptor));
            }
        } catch (Throwable ignored) {
        }
        return Optional.empty();
    }

    static {
        Throwable cause = null;
        int version = 0;

        MethodHandle mhVersion = null;
        MethodHandle mhGetInfo = null;
        MethodHandle mhGetFeatures = null;
        MethodHandle mhEncode = null;
        MethodHandle mhEncodeBgr = null;
        MethodHandle mhEncodeBgraLib = null;
        MethodHandle mhEncodeLosslessBgraLib = null;
        MethodHandle mhEncodeBgrLib = null;
        MethodHandle mhEncodeLosslessBgrLib = null;
        MethodHandle mhDecodeInto = null;
        MethodHandle mhFree = null;
        MethodHandle mhEncodeAnimated = null;
        MethodHandle mhFreeAnimated = null;
        MethodHandle mhDecodeAnimated = null;
        MethodHandle mhGetGifInfo = null;
        MethodHandle mhEncodeGif = null;

        try {
            NativeLibraryLoader.loadLibrary();
            File libFile = NativeLibraryLoader.getLoadedLibraryFile();

            Linker linker = Linker.nativeLinker();
            SymbolLookup libLookup = null;
            if (libFile != null) {
                try {
                    libLookup = SymbolLookup.libraryLookup(libFile.toPath(), Arena.global());
                } catch (Throwable ignored) {
                }
            }
            SymbolLookup loaderLookup = SymbolLookup.loaderLookup();
            SymbolLookup finalLibLookup = libLookup;

            SymbolLookup lookup = name -> {
                if (finalLibLookup != null) {
                    Optional<MemorySegment> opt = finalLibLookup.find(name);
                    if (opt.isPresent()) return opt;
                }
                return loaderLookup.find(name);
            };

            // Version lookup
            mhVersion = findHandle(linker, lookup, "webp4j_ffm_get_version",
                    FunctionDescriptor.of(ValueLayout.JAVA_INT))
                    .or(() -> findHandle(linker, lookup, "WebPGetEncoderVersion",
                            FunctionDescriptor.of(ValueLayout.JAVA_INT)))
                    .orElse(null);

            if (mhVersion != null) {
                version = (int) mhVersion.invokeExact();
            }

            // GetInfo lookup
            mhGetInfo = findHandle(linker, lookup, "webp4j_ffm_get_info",
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
                            ValueLayout.ADDRESS, ValueLayout.ADDRESS))
                    .or(() -> findHandle(linker, lookup, "WebPGetInfo",
                            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
                                    ValueLayout.ADDRESS, ValueLayout.ADDRESS)))
                    .orElse(null);

            // GetFeatures lookup
            mhGetFeatures = findHandle(linker, lookup, "webp4j_ffm_get_features",
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
                            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS))
                    .orElse(null);

            // WebP4J custom encode lookup
            mhEncode = findHandle(linker, lookup, "webp4j_ffm_encode",
                    FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                            ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS))
                    .orElse(null);

            mhEncodeBgr = findHandle(linker, lookup, "webp4j_ffm_encode_bgr",
                    FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                            ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS))
                    .orElse(null);

            // Upstream libwebp encode fallback lookup
            mhEncodeBgraLib = findHandle(linker, lookup, "WebPEncodeBGRA",
                    FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                            ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT, ValueLayout.ADDRESS))
                    .orElse(null);

            mhEncodeLosslessBgraLib = findHandle(linker, lookup, "WebPEncodeLosslessBGRA",
                    FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                            ValueLayout.JAVA_INT, ValueLayout.ADDRESS))
                    .orElse(null);

            mhEncodeBgrLib = findHandle(linker, lookup, "WebPEncodeBGR",
                    FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                            ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT, ValueLayout.ADDRESS))
                    .orElse(null);

            mhEncodeLosslessBgrLib = findHandle(linker, lookup, "WebPEncodeLosslessBGR",
                    FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                            ValueLayout.JAVA_INT, ValueLayout.ADDRESS))
                    .orElse(null);

            // DecodeInto lookup
            mhDecodeInto = findHandle(linker, lookup, "webp4j_ffm_decode_into",
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
                            ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT))
                    .or(() -> findHandle(linker, lookup, "WebPDecodeBGRAInto",
                            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
                                    ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT)))
                    .orElse(null);

            // Free lookup
            mhFree = findHandle(linker, lookup, "webp4j_ffm_free",
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS))
                    .or(() -> findHandle(linker, lookup, "WebPFree",
                            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)))
                    .orElse(null);

            // Animated encode lookup
            mhEncodeAnimated = findHandle(linker, lookup, "webp4j_ffm_encode_animated",
                    FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                            ValueLayout.JAVA_INT, ValueLayout.ADDRESS))
                    .orElse(null);

            mhFreeAnimated = findHandle(linker, lookup, "webp4j_ffm_free_animated",
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS))
                    .orElse(null);

            // Decode animated lookup
            mhDecodeAnimated = findHandle(linker, lookup, "webp4j_ffm_decode_animated",
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
                            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS, ValueLayout.ADDRESS))
                    .orElse(null);

            // Get GIF info lookup
            mhGetGifInfo = findHandle(linker, lookup, "webp4j_ffm_get_gif_info",
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
                            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS))
                    .orElse(null);

            // Encode GIF lookup
            mhEncodeGif = findHandle(linker, lookup, "webp4j_ffm_encode_gif",
                    FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
                            ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS))
                    .orElse(null);

        } catch (Throwable t) {
            cause = t;
            version = 0;
        }

        UNAVAILABILITY_CAUSE = cause;
        VERSION = version;

        MH_VERSION = mhVersion;
        MH_GET_INFO = mhGetInfo;
        MH_GET_FEATURES = mhGetFeatures;
        MH_ENCODE = mhEncode;
        MH_ENCODE_BGR = mhEncodeBgr;
        MH_ENCODE_BGRA_LIBWEBP = mhEncodeBgraLib;
        MH_ENCODE_LOSSLESS_BGRA_LIBWEBP = mhEncodeLosslessBgraLib;
        MH_ENCODE_BGR_LIBWEBP = mhEncodeBgrLib;
        MH_ENCODE_LOSSLESS_BGR_LIBWEBP = mhEncodeLosslessBgrLib;
        MH_DECODE_INTO = mhDecodeInto;
        MH_FREE = mhFree;
        MH_ENCODE_ANIMATED = mhEncodeAnimated;
        MH_FREE_ANIMATED = mhFreeAnimated;
        MH_DECODE_ANIMATED = mhDecodeAnimated;
        MH_GET_GIF_INFO = mhGetGifInfo;
        MH_ENCODE_GIF = mhEncodeGif;
    }
}
