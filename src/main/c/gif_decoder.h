#ifndef WEBP4J_GIF_DECODER_H
#define WEBP4J_GIF_DECODER_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/*
 * Sanity cap on the number of pixels of a GIF canvas or of a single frame raster. A GIF header can
 * declare a 65535x65535 canvas (17 GB of RGBA); anything above this cap is rejected before any
 * memory is reserved. Override at compile time with -DWEBP4J_GIF_MAX_PIXELS=...
 */
#ifndef WEBP4J_GIF_MAX_PIXELS
#define WEBP4J_GIF_MAX_PIXELS 100000000LL
#endif

typedef struct {
    int canvas_width;
    int canvas_height;
    int loop_count;        /* NETSCAPE2.0 loop count seen so far (0 = infinite) */
    int has_transparency;  /* a Graphic Control Extension with a transparent index was seen */
    int frame_index;       /* zero-based index of the frame being delivered */
} GifStreamInfo;

/*
 * Receives each frame, composed onto the persistent canvas, as canvas-sized straight-alpha RGBA.
 * The buffer is only valid during the call. Return 1 to continue decoding, 0 to stop (not an error).
 */
typedef int (*GifFrameCallback)(void* user, const GifStreamInfo* info,
                                const uint8_t* rgba, int duration_ms);

/*
 * Single-pass streaming GIF decode. Memory use is O(canvas) regardless of the number of frames:
 * one canvas plus, only if a frame uses "restore previous" disposal, one snapshot.
 * Handles interlaced images and clears only the frame rectangle for "restore to background".
 *
 * Returns 1 if at least one frame was delivered, 0 on failure. out_info (optional) receives the
 * final stream state.
 */
int DecodeGifStream(const uint8_t* data, size_t data_size,
                    GifFrameCallback callback, void* user, GifStreamInfo* out_info);

/*
 * Reads GIF metadata without decompressing the image data (compressed blocks are skipped).
 * Returns 1 on success, 0 on failure.
 */
int GetGifInfo(const uint8_t* data, size_t data_size,
               int* width, int* height, int* frame_count,
               int* loop_count, int* has_transparency);

#ifdef __cplusplus
}
#endif

#endif /* WEBP4J_GIF_DECODER_H */
