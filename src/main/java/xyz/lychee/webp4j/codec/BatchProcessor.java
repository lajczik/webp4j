package xyz.lychee.webp4j.codec;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Parallel bulk encoding/decoding of independent WebP images.
 * <p>
 * Each image in a batch is encoded or decoded independently of the others, so
 * the work fans out across a thread pool for real multi-core speedup. The heavy
 * native compression in {@link WebPEncoder#encode} runs after the JNI array
 * critical section is released and native methods hold no JVM-wide lock, so
 * multiple Java threads call into libwebp concurrently without contention.
 * <p>
 * Results are <strong>order-preserving</strong>: result element {@code i} always
 * corresponds to input element {@code i}, regardless of completion order.
 * <p>
 * <strong>Thread-safety contract:</strong> every input must be a distinct object
 * that is not mutated by another thread during the call. The single-image
 * methods only read each image's backing array (zero-copy), so concurrent reads
 * of separate images are safe; sharing one mutable {@link BufferedImage} across
 * the batch while it is being modified externally is not.
 * <p>
 * Three flavours are offered:
 * <ul>
 *   <li>list methods ({@link #encodeImages}, {@link #decodeImages}) for batches that fit in memory;</li>
 *   <li>the same with a caller-owned {@link ExecutorService} for environments that need to cap or
 *       share threads (a caller-supplied executor is never shut down by this class);</li>
 *   <li>streaming methods ({@link #encodeEach}, {@link #decodeEach}) that keep only a bounded window
 *       of images in flight, for sequences too large to hold in memory.</li>
 * </ul>
 *
 * @author MrNanko
 * @author lajczik
 * @since 2.4.0
 */
public final class BatchProcessor {

    private BatchProcessor() {
        throw new AssertionError("Cannot instantiate utility class.");
    }

    /**
     * Lazily-initialized shared pool, created on first batch call (holder idiom).
     * Threads are daemons so the pool never blocks JVM shutdown.
     */
    private static final class DefaultPool {
        static final ExecutorService INSTANCE = Executors.newFixedThreadPool(
                Math.max(1, Runtime.getRuntime().availableProcessors()),
                new ThreadFactory() {
                    private final AtomicInteger counter = new AtomicInteger(1);

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "webp4j-batch-" + counter.getAndIncrement());
                        t.setDaemon(true);
                        return t;
                    }
                });
    }

    // ============================================
    // Encoding
    // ============================================

    /**
     * Encodes a list of images to WebP in parallel using the shared internal pool.
     *
     * @param images   Images to encode (null or empty yields an empty list)
     * @param quality  Quality factor (0-100), ignored when lossless
     * @param lossless True for lossless encoding
     * @return An immutable list of encoded WebP byte arrays, one per input image, in order
     * @throws IOException              If encoding any image fails (names the failing index)
     * @throws IllegalArgumentException If any image element is null
     */
    public static List<byte[]> encodeImages(List<BufferedImage> images, float quality, boolean lossless)
            throws IOException {
        return encodeImages(images, quality, lossless, DefaultPool.INSTANCE);
    }

    /**
     * Encodes a list of images to lossy WebP in parallel using the shared internal pool.
     */
    public static List<byte[]> encodeImages(List<BufferedImage> images, float quality) throws IOException {
        return encodeImages(images, quality, false, DefaultPool.INSTANCE);
    }

    /**
     * Encodes a list of images to lossless WebP in parallel using the shared internal pool.
     */
    public static List<byte[]> encodeLosslessImages(List<BufferedImage> images) throws IOException {
        return encodeImages(images, 0, true, DefaultPool.INSTANCE);
    }

    /**
     * Encodes a list of images to WebP in parallel using a caller-supplied executor.
     * <p>
     * The executor is not shut down by this method.
     *
     * @param images   Images to encode (null or empty yields an empty list)
     * @param quality  Quality factor (0-100), ignored when lossless
     * @param lossless True for lossless encoding
     * @param executor Executor to run the encode tasks on
     * @return An immutable list of encoded WebP byte arrays, one per input image, in order
     * @throws IOException              If encoding any image fails (names the failing index)
     * @throws IllegalArgumentException If executor is null or any image element is null
     */
    public static List<byte[]> encodeImages(List<BufferedImage> images, float quality, boolean lossless,
                                            ExecutorService executor) throws IOException {
        if (images == null || images.isEmpty()) {
            return Collections.emptyList();
        }
        requireExecutor(executor);
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i) == null) {
                throw new IllegalArgumentException("Image at index " + i + " is null");
            }
        }
        // multiThreaded=false: the batch already runs one image per pool thread,
        // so letting libwebp also multi-thread each encode would oversubscribe the CPU.
        return runBatch(executor, images.size(),
                index -> WebPEncoder.encode(images.get(index), quality, lossless, false));
    }

    // ============================================
    // Decoding
    // ============================================

    /**
     * Decodes a list of WebP byte arrays into images in parallel using the shared internal pool.
     *
     * @param encodedImages WebP-encoded byte arrays (null or empty yields an empty list)
     * @return An immutable list of decoded images, one per input array, in order
     * @throws IOException              If decoding any array fails (names the failing index)
     * @throws IllegalArgumentException If any element is null
     */
    public static List<BufferedImage> decodeImages(List<byte[]> encodedImages) throws IOException {
        return decodeImages(encodedImages, DefaultPool.INSTANCE);
    }

    /**
     * Decodes a list of WebP byte arrays into images in parallel using a caller-supplied executor.
     * <p>
     * The executor is not shut down by this method.
     *
     * @param encodedImages WebP-encoded byte arrays (null or empty yields an empty list)
     * @param executor     Executor to run the decode tasks on
     * @return An immutable list of decoded images, one per input array, in order
     * @throws IOException              If decoding any array fails (names the failing index)
     * @throws IllegalArgumentException If executor is null or any element is null
     */
    public static List<BufferedImage> decodeImages(List<byte[]> encodedImages, ExecutorService executor)
            throws IOException {
        if (encodedImages == null || encodedImages.isEmpty()) {
            return Collections.emptyList();
        }
        requireExecutor(executor);
        for (int i = 0; i < encodedImages.size(); i++) {
            if (encodedImages.get(i) == null) {
                throw new IllegalArgumentException("Encoded image at index " + i + " is null");
            }
        }
        return runBatch(executor, encodedImages.size(), index -> WebPDecoder.decode(encodedImages.get(index)));
    }

    // ============================================
    // Streaming (bounded memory)
    // ============================================

    /**
     * Receives one result of a streaming batch. Called on the caller's thread, in input order.
     */
    @FunctionalInterface
    public interface ResultConsumer<T> {
        void accept(int index, T result) throws IOException;
    }

    /**
     * Encodes images from an iterator in parallel and hands each result to {@code consumer} in input
     * order, keeping at most {@code 2 x threads} images in flight. Unlike
     * {@link #encodeImages(List, float, boolean)} neither all inputs nor all results are held in memory,
     * so arbitrarily long sequences (for example a directory of photos) can be processed.
     *
     * @param images   Source of images; a null element is rejected
     * @param quality  Quality factor (0-100), ignored when lossless
     * @param lossless True for lossless encoding
     * @param consumer Receives (index, encoded bytes) in input order
     * @throws IOException If encoding fails or the consumer throws (remaining work is cancelled)
     */
    public static void encodeEach(Iterator<BufferedImage> images, float quality, boolean lossless,
                                  ResultConsumer<byte[]> consumer) throws IOException {
        encodeEach(images, quality, lossless, DefaultPool.INSTANCE, defaultWindow(), consumer);
    }

    /**
     * Same as {@link #encodeEach(Iterator, float, boolean, ResultConsumer)} with a caller-supplied
     * executor and in-flight window.
     */
    public static void encodeEach(Iterator<BufferedImage> images, float quality, boolean lossless,
                                  ExecutorService executor, int maxInFlight,
                                  ResultConsumer<byte[]> consumer) throws IOException {
        requireExecutor(executor);
        streamBatch(images, executor, maxInFlight, consumer,
                image -> WebPEncoder.encode(image, quality, lossless, false));
    }

    /**
     * Decodes WebP byte arrays from an iterator in parallel and hands each image to {@code consumer}
     * in input order, keeping at most {@code 2 x threads} images in flight.
     */
    public static void decodeEach(Iterator<byte[]> encodedImages, ResultConsumer<BufferedImage> consumer)
            throws IOException {
        decodeEach(encodedImages, DefaultPool.INSTANCE, defaultWindow(), consumer);
    }

    /**
     * Same as {@link #decodeEach(Iterator, ResultConsumer)} with a caller-supplied executor and window.
     */
    public static void decodeEach(Iterator<byte[]> encodedImages, ExecutorService executor, int maxInFlight,
                                  ResultConsumer<BufferedImage> consumer) throws IOException {
        requireExecutor(executor);
        streamBatch(encodedImages, executor, maxInFlight, consumer, WebPDecoder::decode);
    }

    private static int defaultWindow() {
        return Math.max(2, Runtime.getRuntime().availableProcessors() * 2);
    }

    @FunctionalInterface
    private interface ValueTask<I, T> {
        T run(I input) throws IOException;
    }

    private static <I, T> void streamBatch(Iterator<I> inputs, ExecutorService executor, int maxInFlight,
                                           ResultConsumer<T> consumer, ValueTask<I, T> task) throws IOException {
        if (inputs == null || consumer == null) {
            throw new IllegalArgumentException("Inputs and consumer cannot be null");
        }
        if (maxInFlight < 1) {
            throw new IllegalArgumentException("maxInFlight must be at least 1");
        }

        ArrayDeque<Future<T>> window = new ArrayDeque<>();
        int submitted = 0;
        int delivered = 0;
        try {
            while (inputs.hasNext() || !window.isEmpty()) {
                while (inputs.hasNext() && window.size() < maxInFlight) {
                    final I input = inputs.next();
                    if (input == null) {
                        throw new IllegalArgumentException("Input at index " + submitted + " is null");
                    }
                    window.add(executor.submit(() -> task.run(input)));
                    submitted++;
                }
                consumer.accept(delivered, await(window.poll(), delivered));
                delivered++;
            }
        } catch (IOException | RuntimeException e) {
            cancelAll(window);
            throw e;
        }
    }

    private static <T> T await(Future<T> future, int index) throws IOException {
        try {
            return future.get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            throw new IOException("Batch processing failed at index " + index
                    + (cause != null && cause.getMessage() != null ? ": " + cause.getMessage() : ""), cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Batch processing was interrupted at index " + index, e);
        }
    }

    // ============================================
    // Internal
    // ============================================

    /**
     * An indexed unit of batch work, allowed to throw the checked exceptions the
     * single-image codec methods declare.
     */
    @FunctionalInterface
    private interface IndexedTask<T> {
        T run(int index) throws IOException;
    }

    /** Carries the index of the failed task through the executor. */
    private static final class IndexedFailure extends Exception {
        private static final long serialVersionUID = 1L;
        final int index;

        IndexedFailure(int index, Throwable cause) {
            super(cause);
            this.index = index;
        }
    }

    /** A completed task together with the index it belongs to. */
    private static final class IndexedResult<T> {
        final int index;
        final T value;

        IndexedResult(int index, T value) {
            this.index = index;
            this.value = value;
        }
    }

    /**
     * Submits {@code size} indexed tasks and collects their results in input order. Fails fast: the
     * first task to <em>complete</em> with an error (not the first in input order) cancels the rest and
     * surfaces as an {@link IOException} naming the failing index.
     */
    private static <T> List<T> runBatch(ExecutorService executor, int size, IndexedTask<T> task) throws IOException {
        CompletionService<IndexedResult<T>> completion = new ExecutorCompletionService<>(executor);
        List<Future<IndexedResult<T>>> futures = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            final int index = i;
            futures.add(completion.submit(() -> {
                try {
                    return new IndexedResult<>(index, task.run(index));
                } catch (Exception | Error e) {
                    throw new IndexedFailure(index, e);
                }
            }));
        }

        List<T> results = new ArrayList<>(Collections.<T>nCopies(size, null));
        try {
            for (int done = 0; done < size; done++) {
                IndexedResult<T> r = completion.take().get();
                results.set(r.index, r.value);
            }
        } catch (ExecutionException e) {
            cancelAll(futures);
            Throwable cause = e.getCause();
            int index = -1;
            if (cause instanceof IndexedFailure) {
                index = ((IndexedFailure) cause).index;
                cause = cause.getCause();
            }
            throw new IOException("Batch processing failed at index " + index
                    + (cause != null && cause.getMessage() != null ? ": " + cause.getMessage() : ""), cause);
        } catch (InterruptedException e) {
            cancelAll(futures);
            Thread.currentThread().interrupt();
            throw new IOException("Batch processing was interrupted", e);
        }
        return Collections.unmodifiableList(results);
    }

    private static void cancelAll(Iterable<? extends Future<?>> futures) {
        for (Future<?> f : futures) {
            f.cancel(true);
        }
    }

    private static void requireExecutor(ExecutorService executor) {
        if (executor == null) {
            throw new IllegalArgumentException("Executor cannot be null");
        }
    }
}
