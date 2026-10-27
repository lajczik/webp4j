# WebP4j

<div align="center">

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Java: 8 - 22+](https://img.shields.io/badge/Java-8%20--%2022%2B-orange.svg)](https://openjdk.org/)
[![libwebp: 1.6.0](https://img.shields.io/badge/libwebp-1.6.0-green.svg)](https://developers.google.com/speed/webp)
[![Architecture: Multi-Release JAR](https://img.shields.io/badge/JAR-Multi--Release%20(JNI%20%2B%20FFM)-purple.svg)]()
[![Platform: Win / macOS / Linux](https://img.shields.io/badge/Platform-Windows%20%7C%20macOS%20%7C%20Linux-brightgreen.svg)]()

**High-performance WebP library for modern Java — Static & Animated WebP encoding, decoding, metadata injection, and GIF conversion.**

[Key Features](#-key-features) • [Installation](#-installation) • [Animated WebP](#-animated-webp-creation--decoding) • [Static Images](#-static-webp-encoding--decoding) • [API Architecture](#-clean-modular-api) • [Performance](#-performance)

</div>

---

## 🌟 Overview

**WebP4j** is an industrial-grade Java library bridging Java applications with Google's state-of-the-art **[libwebp 1.6.0](https://developers.google.com/speed/webp)** native library. It runs across **Java 8 through Java 22+** via a Multi-Release JAR that automatically uses standard JNI on Java 8–21 and modern Foreign Function & Memory (FFM, JEP 454) on Java 22+.

Whether you need to generate lightweight **animated WebP** banners from scratch, convert legacy **GIF animations**, batch-compress millions of e-commerce photos with zero memory copies, or parse **RIFF chunks and EXIF/ICC metadata** without third-party dependencies, WebP4j provides a clean, zero-allocation-focused API.

---

## ⚡ Key Features

- 🎬 **First-Class Animated WebP Generation**:
  - Build animated WebP images from lists of `BufferedImage` frames with per-frame custom durations.
  - **Streaming frame-by-frame composer** (`AnimatedWebPEncoder`) with precise control over frame offsets, alpha blending (`ALPHA_BLENDING`, `NO_BLEND`), and disposal methods (`DISPOSE_TO_BACKGROUND`, `NO_DISPOSE`).
  - Configurable canvas background color, infinite loops, and custom loop counts.
  - Automatic **Frame Normalization** (`CONTAIN`, `COVER`, `STRETCH`) for assembling animations from mismatched image sizes.
- 🎞️ **Animated WebP Decoding**:
  - Extract frames with accurate cumulative timestamps and display delays.
  - **O(1) Instant Header Inspection**: Query canvas dimensions, frame count, loop count, and transparency directly from container chunks in microseconds without decoding pixel buffers.
- ☕ **Standard Java `ImageIO` Integration**:
  - Automatically registered via `javax.imageio.spi.ImageReaderSpi` and `ImageWriterSpi`.
  - Zero-friction drop-in: use `ImageIO.read(file)` and `ImageIO.write(image, "webp", file)` directly anywhere in your existing codebase.
  - Full support for `WebPImageWriteParam` (lossless toggle, compression quality).
- 🖼️ **Static WebP Encoding & Decoding**:
  - RGB & RGBA support with lossy (0–100 quality) and lossless compression.
  - **In-place decoding**: Decode directly into pre-allocated `BufferedImage` instances to eliminate garbage collection in render loops.
  - **Fast Thumbnails**: Generate aspect-ratio-preserving thumbnails directly from WebP byte arrays or files.
  - Comprehensive overloads for `byte[]`, `File`, `Path`, `InputStream`, `OutputStream`, and `ByteBuffer`.
- 📦 **Container Metadata & RIFF Chunks**:
  - Pure-Java chunk parser and assembler supporting `VP8`, `VP8L`, `VP8X`, `ANIM`, `ANMF`, `ALPH`, `ICCP`, `EXIF`, and `XMP`.
  - Type-safe flag enums (`WebPContainerFlag`, `WebPFrameFlag`) with direct bitmask inspection (`flag.isSet(mask)`, `fromMask(mask)`).
  - Inject ICC color profiles, EXIF tags, and XMP metadata into static WebP images.
- ⚡ **Zero-Copy & Multi-Core Performance**:
  - Direct JNI raster extraction avoiding intermediate format conversion buffers.
  - Native multi-threaded tile encoding via libwebp's `thread_level`.
  - Dedicated **`BatchProcessor`** for concurrent bulk encoding and decoding, including bounded-memory streaming (`encodeEach` / `decodeEach`).
- 🔄 **GIF to WebP Conversion**:
  - Dual-engine pipeline: native high-speed `giflib` decoder with graceful fallback to Java ImageIO.
- 🌐 **True Cross-Platform Support**:
  - Precompiled binaries bundled inside the JAR for **Windows (x64, ARM64)**, **macOS (Intel, Apple Silicon ARM64)**, and **Linux (x64, ARM64)**.

---

## 📦 Installation

### Maven

```xml
<dependency>
    <groupId>io.github.lajczik</groupId>
    <artifactId>webp4j</artifactId>
    <version>2.7.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'io.github.lajczik:webp4j:2.7.0'
```

### Supported Environments

| Operating System | Architecture | Binary Format | Engine |
|:---|:---|:---|:---|
| **Windows** | x86_64, aarch64 (ARM64) | `.dll` | JNI / FFM |
| **macOS** | x86_64 (Intel), arm64 (Apple Silicon) | `.dylib` | JNI / FFM |
| **Linux** | x86_64, aarch64 (ARM64) | `.so` | JNI / FFM |

> **Building from source** requires JDK 22 or newer (the FFM classes are compiled with `--release 22`; the rest targets Java 8). `./gradlew check` runs the suite on both backends (`test` = FFM, `testJni` = JNI).

> **Java 22+ Note**: WebP4j packages a Multi-Release JAR with Automatic Module `xyz.lychee.webp4j`. When running on Java 22+, use `--enable-native-access=xyz.lychee.webp4j` (or `ALL-UNNAMED` on classpath) to allow unrestricted native access without JVM warnings.

---

## 🎬 Animated WebP Creation & Decoding

Animated WebP offers 30–80% smaller file sizes than GIF with full 24-bit color and 8-bit alpha transparency. WebP4j offers multiple intuitive ways to create and inspect animations:

### 1. Simple Multi-Frame Animation

```java
import xyz.lychee.webp4j.codec.AnimatedWebPEncoder;
import xyz.lychee.webp4j.model.GifToWebPConfig;
import xyz.lychee.webp4j.utils.FrameNormalizer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.List;

// Load source images
List<BufferedImage> frames = Arrays.asList(
    ImageIO.read(new File("frame1.png")),
    ImageIO.read(new File("frame2.png")),
    ImageIO.read(new File("frame3.png"))
);

// Normalize canvas size if frames differ in dimension
List<BufferedImage> normalizedFrames = FrameNormalizer.normalize(frames);

// Frame delays in milliseconds (e.g. 100ms per frame = 10 FPS)
int[] delays = new int[]{100, 150, 100};

// Configure encoding (lossless or lossy quality)
GifToWebPConfig config = GifToWebPConfig.createLosslessConfig()
        .setLoopCount(0); // 0 = infinite loop

// Encode to file
AnimatedWebPEncoder.encode(new File("output_animation.webp"), normalizedFrames, delays, config);
```

### 2. Fine-Grained Frame-by-Frame Encoder

For advanced control over blending, disposal methods, canvas backgrounds, and frame coordinates. Frames are collected in memory and the container is written **once, when the encoder is closed** (`close()` is idempotent; if encoding a frame throws, nothing is written):

```java
import xyz.lychee.webp4j.codec.AnimatedWebPEncoder;
import xyz.lychee.webp4j.model.WebPFrameFlag;

import java.io.FileOutputStream;

try (FileOutputStream out = new FileOutputStream("custom_anim.webp");
     AnimatedWebPEncoder encoder = new AnimatedWebPEncoder(
             out,
             true,    // hasAlpha
             400,     // canvas width
             300,     // canvas height
             0,       // loop count (0 = infinite)
             0x00000000 // background color (ARGB)
     )) {

    // Frame 0: Standard opaque base frame (alpha blend + keep canvas)
    encoder.setFrame(0, frame1, 100, 80.0f, false,
            WebPFrameFlag.ALPHA_BLENDING, WebPFrameFlag.NO_DISPOSE);

    // Frame 1: Transparent overlay with canvas background wipe
    encoder.setFrame(1, frame2, 120, 85.0f, false,
            WebPFrameFlag.NO_BLEND, WebPFrameFlag.DISPOSE_TO_BACKGROUND);

    // Individual frames can also use fine-grained WebPEncoderOptions:
    // encoder.addFrame(frame3, 100, WebPEncoderOptions.lossy(85f).setSharpYuv(true));
} // close() writes the animation

// Raw pixel frames may be placed at an offset; offsets must be even (the container stores x/2, y/2):
// encoder.setFrame(2, pixels, 16, 8, 64, 64, 100, 80.0f, false);
```

### 3. Decoding Animated WebP into Frames

Extract all frames, delays, and animation statistics:

```java
import xyz.lychee.webp4j.codec.AnimatedWebPDecoder;
import xyz.lychee.webp4j.model.AnimatedWebPData;
import xyz.lychee.webp4j.model.AnimatedWebPFrame;

import java.io.File;

// Decode animation file
AnimatedWebPData animation = AnimatedWebPDecoder.decode(new File("input_animation.webp"));

System.out.println("Canvas: " + animation.getCanvasWidth() + "x" + animation.getCanvasHeight());
System.out.println("Frame Count: " + animation.getFrameCount());
System.out.println("Loop Count: " + animation.getLoopCount());

for (int i = 0; i < animation.getFrames().size(); i++) {
    AnimatedWebPFrame frame = animation.getFrames().get(i);
    System.out.printf("Frame #%d: timestamp=%d ms, duration=%d ms%n",
            i, frame.getTimestamp(), frame.getDuration());
    
    // Process or save frame image
    BufferedImage img = frame.getImage();
}
```

### 4. Instant Animation Inspection (O(1) No Decoding)

Query metadata in microseconds without allocating large image buffers:

```java
import xyz.lychee.webp4j.codec.AnimatedWebPDecoder;
import xyz.lychee.webp4j.model.AnimationInfo;

File file = new File("large_animation.webp");

// Reads only RIFF container headers (zero pixel decoding)
AnimationInfo info = AnimatedWebPDecoder.getAnimationInfo(file);

System.out.println("Dimensions: " + info.getWidth() + "x" + info.getHeight());
System.out.println("Frames: " + info.getFrameCount());
System.out.println("Total Duration: " + info.getTotalDuration() + " ms");
System.out.println("Has Transparency: " + info.hasTransparency());
```

---

## 🖼️ Static WebP Encoding & Decoding

### Static Encoding

```java
import xyz.lychee.webp4j.codec.WebPEncoder;

BufferedImage image = ImageIO.read(new File("photo.jpg"));

// Lossy encode (Quality 0.0 to 100.0)
WebPEncoder.encode(image, new File("photo.webp"), 82.0f);

// Lossless encode (Ideal for graphics, screenshots, transparent PNGs)
WebPEncoder.encodeLossless(image, new File("screenshot.webp"));

// Encode with embedded EXIF / ICC Profile / XMP metadata
byte[] exifData = ...;
byte[] iccProfile = ...;
String xmpXml = "<x:xmpmeta>...</x:xmpmeta>";

WebPEncoder.encodeWithMetadata(image, new File("rich_image.webp"), 85.0f, false,
        exifData, iccProfile, xmpXml);
```

### Static Decoding & Fast Features

```java
import xyz.lychee.webp4j.codec.WebPDecoder;
import xyz.lychee.webp4j.model.WebPBitstreamFeatures;

File webpFile = new File("photo.webp");

// 1. Standard decode
BufferedImage image = WebPDecoder.decode(webpFile);

// 2. Fast O(1) metadata check (reads first 30-64 bytes without reading full file)
int[] dims = WebPDecoder.getInfo(webpFile); // [width, height]
boolean isAnim = WebPDecoder.isAnimated(webpFile);
boolean hasAlpha = WebPDecoder.hasAlpha(webpFile);

// 3. Extract bitstream features (format, alpha, dimensions)
WebPBitstreamFeatures features = WebPDecoder.getBitstreamFeatures(webpFile);
System.out.println("Format: " + (features.isLossless() ? "Lossless VP8L" : "Lossy VP8"));

// 4. In-place decoding into an existing canvas (zero GC allocations)
BufferedImage canvas = new BufferedImage(dims[0], dims[1], BufferedImage.TYPE_INT_ARGB);
WebPDecoder.decode(webpBytes, canvas);

// 5. Generate high-quality thumbnail preserving aspect ratio
BufferedImage thumbnail = WebPDecoder.createThumbnail(webpFile, 200);
```

---

## ☕ Standard Java ImageIO Integration

WebP4j automatically registers Service Provider Interfaces (SPI) with Java's standard `ImageIO` framework. Any legacy or third-party code calling `ImageIO` automatically gains WebP reading and writing capabilities:

```java
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import xyz.lychee.webp4j.imageio.WebPImageWriteParam;

// 1. Reading WebP with standard ImageIO
BufferedImage inputImage = ImageIO.read(new File("input.webp"));

// 2. Writing WebP with standard ImageIO
ImageIO.write(inputImage, "webp", new File("output.webp"));

// 3. Customizing compression via WebPImageWriteParam
ImageWriter writer = ImageIO.getImageWritersByFormatName("webp").next();
try (ImageOutputStream ios = ImageIO.createImageOutputStream(new File("output_custom.webp"))) {
    writer.setOutput(ios);

    WebPImageWriteParam param = (WebPImageWriteParam) writer.getDefaultWriteParam();
    param.setQuality(85.0f);   // Custom quality (0.0 - 100.0) or param.setCompressionQuality(0.85f)
    // param.setLossless(true); // Or toggle lossless VP8L compression

    writer.write(null, new javax.imageio.IIOImage(inputImage, null, null), param);
} finally {
    writer.dispose();
}

// 4. Creating Animated WebP via ImageIO Sequence Writing (per-frame quality & delay)
ImageWriter animWriter = ImageIO.getImageWritersByFormatName("webp").next();
try (ImageOutputStream ios = ImageIO.createImageOutputStream(new File("animation.webp"))) {
    animWriter.setOutput(ios);
    animWriter.prepareWriteSequence(null);

    WebPImageWriteParam param = (WebPImageWriteParam) animWriter.getDefaultWriteParam();
    param.setLoopCount(0); // 0 = infinite loop

    // Frame 1: 100ms display delay, quality 80
    param.setDelay(100);
    param.setQuality(80.0f);
    param.setFrameFlags(WebPFrameFlag.ALPHA_BLENDING, WebPFrameFlag.NO_DISPOSE);
    animWriter.writeToSequence(new javax.imageio.IIOImage(frame1, null, null), param);

    // Frame 2: 150ms display delay, quality 90 (or lossless)
    param.setDelay(150);
    param.setQuality(90.0f);
    param.setFrameFlags(WebPFrameFlag.NO_BLEND, WebPFrameFlag.DISPOSE_TO_BACKGROUND);
    animWriter.writeToSequence(new javax.imageio.IIOImage(frame2, null, null), param);

    animWriter.endWriteSequence();
} finally {
    animWriter.dispose();
}
```

---

## 🔄 GIF to WebP Conversion

Convert legacy animated GIFs to compact animated WebP files:

```java
import xyz.lychee.webp4j.codec.GifToWebPConverter;
import xyz.lychee.webp4j.model.GifToWebPConfig;

import java.io.File;

File gifFile = new File("banner.gif");
File webpFile = new File("banner.webp");

// Lossy conversion (Quality 80)
GifToWebPConfig config = GifToWebPConfig.createLossyConfig(80.0f)
        .setCompressionMethod(4)
        .setMinimizeSize(true);

GifToWebPConverter.convert(gifFile, webpFile, config);

// Lossless conversion
GifToWebPConverter.convertLossless(gifFile, new File("banner_lossless.webp"));
```

---

## 🚀 Parallel Batch Processing

When processing hundreds or thousands of images, `BatchProcessor` distributes native libwebp operations across CPU cores:

```java
import xyz.lychee.webp4j.codec.BatchProcessor;

List<BufferedImage> images = ...;

// Parallel lossy encode (uses Runtime.availableProcessors() threads)
List<byte[]> webpByteArrays = BatchProcessor.encodeImages(images, 80.0f);

// Parallel lossless encode
List<byte[]> losslessWebps = BatchProcessor.encodeLosslessImages(images);

// Parallel decode
List<BufferedImage> decodedImages = BatchProcessor.decodeImages(webpByteArrays);

// Custom thread pool cap
ExecutorService customPool = Executors.newFixedThreadPool(4);
List<byte[]> capped = BatchProcessor.encodeImages(images, 80.0f, false, customPool);
```

---

## 🏗️ Clean, Modular API

All functionality is neatly organized into dedicated functional packages:

```text
xyz.lychee.webp4j
 ├── WebPCodec.java            # Unified facade covering all operations
 ├── codec
 │    ├── WebPEncoder.java          # Static WebP encoding & metadata assembly
 │    ├── WebPDecoder.java          # Static WebP decoding, headers, and thumbnails
 │    ├── AnimatedWebPEncoder.java  # Animated WebP composition & streaming
 │    ├── AnimatedWebPDecoder.java  # Animated WebP extraction & animation info
 │    ├── BatchProcessor.java       # Parallel multi-core batch processing
 │    └── GifToWebPConverter.java   # GIF to WebP native/Java conversion
 ├── imageio
 │    ├── WebPImageReader.java      # ImageIO Reader SPI implementation
 │    ├── WebPImageWriter.java      # ImageIO Writer SPI implementation
 │    └── WebPImageWriteParam.java  # ImageIO WebP compression parameter handler
 ├── model
 │    ├── WebPContainerFlag.java    # VP8X feature flag enum (ANIMATION, ALPHA, EXIF, etc.)
 │    ├── WebPFrameFlag.java        # ANMF frame flag enum (NO_BLEND, DISPOSE_TO_BACKGROUND)
 │    ├── AnimationInfo.java        # Non-decoding animation statistics
 │    ├── AnimatedWebPData.java     # Decoded animation container
 │    ├── AnimatedWebPFrame.java    # Single animation frame image & timing
 │    ├── WebPBitstreamFeatures.java# Bitstream dimensions, format, alpha flags
 │    └── GifToWebPConfig.java      # Conversion & compression parameters
 ├── chunks                         # Pure-Java RIFF chunk parser & builder
 │    ├── Chunk.java, ChunkFactory.java
 │    └── VP8XChunk, ANIMChunk, ANMFChunk, VP8Chunk, VP8LChunk, etc.
 ├── utils
 │    ├── PixelConverter.java       # Direct zero-copy raster bridge
 │    ├── ImageHelper.java          # Resizing, cloning, thumbnails, RGBA creation
 │    ├── FrameNormalizer.java      # Dimension normalization for animations
 │    └── Util.java                 # Fast zero-allocation endian stream I/O
 └── natives
      ├── NativeWebP.java           # JNI / FFM native bridge bindings
      └── NativeLibraryLoader.java  # Platform binary resource unpacker
```

---

## 🛡️ Safety Limits for Untrusted Input

A WebP header of a few dozen bytes can declare a 16383×16383 canvas, and an animation can declare thousands of such frames. WebP4j checks the *declared* size against `WebPLimits` **before** reserving any memory, and rejects with an `IOException`:

```java
import xyz.lychee.webp4j.utils.WebPLimits;

WebPLimits.setMaxPixels(50_000_000L);        // one image / one animation canvas (default 100 Mpx)
WebPLimits.setMaxTotalPixels(128_000_000L);  // all frames of an animation together (default 256 Mpx)
WebPLimits.setMaxFrames(5_000);              // default 20 000
WebPLimits.setMaxInputBytes(64L << 20);      // bytes read from a stream or file (default 1 GiB)
```

The container parsers are overflow-safe: a hostile chunk size can neither make a scan loop forever nor read outside the data. The native GIF decoder rejects canvases above `WEBP4J_GIF_MAX_PIXELS` (CMake option, default 100 Mpx).

---

## 🎚️ Encoder Options & Metadata

`WebPEncoderOptions` exposes libwebp's `WebPConfig` for static images and allows embedding container metadata (EXIF, ICC profile, XMP):

```java
byte[] smallest = WebPEncoder.encode(image,
        WebPEncoderOptions.lossless(100).setMethod(6));   // lossless: effort 0..100

byte[] photo = WebPEncoder.encode(image,
        WebPEncoderOptions.lossy(80f)
                .setSharpYuv(true)
                .setAlphaQuality(90)
                .setMethod(5)
                .setExif(exifBytes)
                .setIccProfile(iccBytes)
                .setXmp(xmpXml));
```

`AnimatedWebPEncoder` also supports container metadata (`setExif`, `setIccProfile`, `setXmp`). Parallel batch and streaming operations in `BatchProcessor` (`encodeImages`, `encodeEach`) fully support `WebPEncoderOptions`.

Options beyond quality/lossless need a native library built from 2.7.0 or later (older binaries throw `UnsupportedOperationException`).

---

## 🔧 Native Library Loading

The bundled binary is extracted once into `<java.io.tmpdir>/webp4j-<sha256>/` (override the base directory with `-Dwebp4j.tmpdir=...`), verified by hash on every start and shared by all JVMs. To use your own build (musl/Alpine, other architectures) point `-Dwebp4j.native.path=/path/to/libwebp4j.so` to it; as a last resort `System.loadLibrary("webp4j")` is tried.

---

## 🔁 Migrating to 2.7.0

| Change | What to do |
|:---|:---|
| `AnimatedWebPEncoder` writes the container once on `close()`; `flush()` is deprecated (it now equals `finish()` and is idempotent) | Remove explicit `flush()` calls |
| Constructors lost the `frames` parameter (the old ones remain, deprecated) | Drop the frame count argument |
| `setChunkAt` was removed; `getChunks()` returns the chunks that would be written | Use `setFrame(index, ANMFChunk)` / `addFrame(ANMFChunk)` |
| ANMF frame offsets must be even and are stored as `x/2`, `y/2` as the container specification requires | Round offsets down to even values |
| `WebPEncoder.encode(image, quality, out)` no longer switches to lossless at quality ≥ 100 | Call `encodeLossless` for lossless output |
| Maven coordinates are `io.github.lajczik:webp4j` | Update the dependency |

---

## 📊 Performance

WebP4j is engineered for server-side environments where memory efficiency and throughput matter:

- **Low-Copy Raster Pipeline**: Contiguous `TYPE_INT_ARGB`, `TYPE_INT_RGB`, and `TYPE_3BYTE_BGR` raster arrays are handed to libwebp without intermediate conversion. With the JNI backend the arrays are pinned (no copy); the FFM backend copies the input into native memory once.
- **Cheap Header Parsing**: Dimensions, format and animation flags are read from the first 64 bytes of a file, without allocating chunk objects; metadata (EXIF/ICC/XMP) is read by skipping over the image data.
- **Single-pass Chunk Scanning**: `RiffScanner` walks the container with no per-chunk allocation, and all offset arithmetic is done in `long`.
- **Multi-Threaded Encoding**: libwebp's `thread_level` is enabled by default (bit-identical output, faster on multi-core CPUs). Measure on your own data; no benchmark is bundled.

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).  
Native WebP capabilities powered by Google's [libwebp](https://developers.google.com/speed/webp) (BSD-3-Clause).
