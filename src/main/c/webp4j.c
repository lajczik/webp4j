#include <jni.h>
#include <limits.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <webp/encode.h>
#include <webp/decode.h>
#include <webp/mux.h>
#include <webp/demux.h>
#include "xyz_lychee_webp4j_natives_NativeWebP.h"

#ifdef HAVE_GIFLIB
#include "gif_decoder.h"
#endif

/*
 * Pixels cross the JNI boundary as Java ARGB ints (0xAARRGGBB). This binding
 * requires a little-endian target so that those ints are BGRA in byte order,
 * matching libwebp's WebPPictureImportBGRA/BGRX, WebPDecodeBGRAInto and
 * MODE_BGRA entry points with no conversion pass. All release platforms
 * (win/mac/linux, x64/aarch64) are little-endian; there is no big-endian path.
 */
#if defined(__BYTE_ORDER__) && (__BYTE_ORDER__ != __ORDER_LITTLE_ENDIAN__)
#error "webp4j requires a little-endian target (Java ARGB int == BGRA bytes)"
#endif

/*
 * Critical-section rule: between GetPrimitiveArrayCritical and the matching
 * ReleasePrimitiveArrayCritical there must be NO other JNI calls and no
 * unbounded blocking, and every early-return path must release in reverse
 * acquisition order. Long-running work (WebPEncode, WebPAnimEncoderAdd, the
 * giflib scan) is kept outside critical sections; the one exception is
 * decodeInto, where decoding directly into the pinned Java array IS the
 * zero-copy.
 */

/*
 * Sanity cap for the total number of pixels the native animated decoders will materialize.
 * The Java layer applies the (configurable) WebPLimits first; this is the last line of defence
 * for callers that reach the native entry points directly.
 */
#define WEBP4J_MAX_TOTAL_PIXELS (1ULL << 30)

/* ---------------------------------------------------------------------------
 * Cached classes and field IDs (initialized once in JNI_OnLoad).
 * The global class refs keep the jfieldIDs valid for the library's lifetime.
 * ------------------------------------------------------------------------- */

static jclass g_features_class;        /* xyz.lychee.webp4j.model.WebPBitstreamFeatures */
static jfieldID g_features_width;
static jfieldID g_features_height;
static jfieldID g_features_has_alpha;
static jfieldID g_features_has_animation;
static jfieldID g_features_format;

static jclass g_anim_info_class;       /* xyz.lychee.webp4j.model.AnimationInfo */
static jfieldID g_anim_info_width;
static jfieldID g_anim_info_height;
static jfieldID g_anim_info_frame_count;
static jfieldID g_anim_info_loop_count;
static jfieldID g_anim_info_has_transparency;

static jclass g_anim_data_class;       /* xyz.lychee.webp4j.model.AnimatedWebPData */
static jfieldID g_anim_data_canvas_width;
static jfieldID g_anim_data_canvas_height;
static jfieldID g_anim_data_loop_count;
static jfieldID g_anim_data_bgcolor;
static jfieldID g_anim_data_frame_count;
static jfieldID g_anim_data_frame_pixels;
static jfieldID g_anim_data_timestamps;

static jclass g_int_array_class;       /* int[] */

static jclass CacheClass(JNIEnv* env, const char* name) {
    jclass local = (*env)->FindClass(env, name);
    if (local == NULL) {
        return NULL;
    }
    jclass global = (jclass)(*env)->NewGlobalRef(env, local);
    (*env)->DeleteLocalRef(env, local);
    return global;
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    (void)reserved;
    JNIEnv* env;
    if ((*vm)->GetEnv(vm, (void**)&env, JNI_VERSION_1_8) != JNI_OK) {
        return JNI_ERR;
    }

    g_features_class = CacheClass(env, "xyz/lychee/webp4j/model/WebPBitstreamFeatures");
    g_anim_info_class = CacheClass(env, "xyz/lychee/webp4j/model/AnimationInfo");
    g_anim_data_class = CacheClass(env, "xyz/lychee/webp4j/model/AnimatedWebPData");
    g_int_array_class = CacheClass(env, "[I");
    if (g_features_class == NULL || g_anim_info_class == NULL ||
        g_anim_data_class == NULL || g_int_array_class == NULL) {
        return JNI_ERR;
    }

    g_features_width         = (*env)->GetFieldID(env, g_features_class, "width", "I");
    g_features_height        = (*env)->GetFieldID(env, g_features_class, "height", "I");
    g_features_has_alpha     = (*env)->GetFieldID(env, g_features_class, "hasAlpha", "Z");
    g_features_has_animation = (*env)->GetFieldID(env, g_features_class, "hasAnimation", "Z");
    g_features_format        = (*env)->GetFieldID(env, g_features_class, "format", "I");

    g_anim_info_width            = (*env)->GetFieldID(env, g_anim_info_class, "width", "I");
    g_anim_info_height           = (*env)->GetFieldID(env, g_anim_info_class, "height", "I");
    g_anim_info_frame_count      = (*env)->GetFieldID(env, g_anim_info_class, "frameCount", "I");
    g_anim_info_loop_count       = (*env)->GetFieldID(env, g_anim_info_class, "loopCount", "I");
    g_anim_info_has_transparency = (*env)->GetFieldID(env, g_anim_info_class, "hasTransparency", "Z");

    g_anim_data_canvas_width  = (*env)->GetFieldID(env, g_anim_data_class, "canvasWidth", "I");
    g_anim_data_canvas_height = (*env)->GetFieldID(env, g_anim_data_class, "canvasHeight", "I");
    g_anim_data_loop_count    = (*env)->GetFieldID(env, g_anim_data_class, "loopCount", "I");
    g_anim_data_bgcolor       = (*env)->GetFieldID(env, g_anim_data_class, "bgcolor", "I");
    g_anim_data_frame_count   = (*env)->GetFieldID(env, g_anim_data_class, "frameCount", "I");
    g_anim_data_frame_pixels  = (*env)->GetFieldID(env, g_anim_data_class, "framePixels", "[[I");
    g_anim_data_timestamps    = (*env)->GetFieldID(env, g_anim_data_class, "timestamps", "[I");

    if (g_features_width == NULL || g_features_height == NULL ||
        g_features_has_alpha == NULL || g_features_has_animation == NULL ||
        g_features_format == NULL ||
        g_anim_info_width == NULL || g_anim_info_height == NULL ||
        g_anim_info_frame_count == NULL || g_anim_info_loop_count == NULL ||
        g_anim_info_has_transparency == NULL ||
        g_anim_data_canvas_width == NULL || g_anim_data_canvas_height == NULL ||
        g_anim_data_loop_count == NULL || g_anim_data_bgcolor == NULL ||
        g_anim_data_frame_count == NULL || g_anim_data_frame_pixels == NULL ||
        g_anim_data_timestamps == NULL) {
        (*env)->ExceptionClear(env);
        return JNI_ERR;
    }

    return JNI_VERSION_1_8;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM* vm, void* reserved) {
    (void)reserved;
    JNIEnv* env;
    if ((*vm)->GetEnv(vm, (void**)&env, JNI_VERSION_1_8) != JNI_OK) {
        return;
    }
    if (g_features_class != NULL)  (*env)->DeleteGlobalRef(env, g_features_class);
    if (g_anim_info_class != NULL) (*env)->DeleteGlobalRef(env, g_anim_info_class);
    if (g_anim_data_class != NULL) (*env)->DeleteGlobalRef(env, g_anim_data_class);
    if (g_int_array_class != NULL) (*env)->DeleteGlobalRef(env, g_int_array_class);
}

/* ===========================================================================
 * Backend-independent core (shared by the JNI and the FFM entry points)
 * =========================================================================== */

/* ---------------------------------------------------------------------------
 * Static encoding
 * ------------------------------------------------------------------------- */

typedef struct {
    float quality;        /* lossy quality, or lossless effort */
    int lossless;
    int method;           /* 0..6 */
    int near_lossless;    /* 0..100, 100 = off */
    int exact;
    int alpha_quality;    /* 0..100 */
    int sharp_yuv;
    int target_size;      /* bytes, 0 = off */
    int multi_threaded;
} EncodeOptions;

/*
 * Options of libwebp's simple-API Encode() (picture_enc.c): preset DEFAULT, lossless preset
 * quality 70, pic.use_argb = lossless. Output is identical to WebPEncode(Lossless)BGRA/BGR.
 */
static void SimpleEncodeOptions(EncodeOptions* o, float quality, int lossless, int multi_threaded) {
    o->quality = lossless ? 70.f : quality;
    o->lossless = lossless ? 1 : 0;
    o->method = 4;
    o->near_lossless = 100;
    o->exact = 0;
    o->alpha_quality = 100;
    o->sharp_yuv = 0;
    o->target_size = 0;
    o->multi_threaded = multi_threaded ? 1 : 0;
}

static int SetupEncode(WebPConfig* config, WebPPicture* pic, WebPMemoryWriter* wrt,
                       int width, int height, const EncodeOptions* o) {
    if (!WebPConfigPreset(config, WEBP_PRESET_DEFAULT, o->quality)) {
        return 0;
    }
    config->lossless = o->lossless;
    config->method = o->method;
    config->near_lossless = o->near_lossless;
    config->exact = o->exact;
    config->alpha_quality = o->alpha_quality;
    config->use_sharp_yuv = o->sharp_yuv;
    config->target_size = o->target_size;
    /* thread_level is a pure speed knob: the output is bit-identical to single-threaded. */
    config->thread_level = o->multi_threaded;
    if (!WebPValidateConfig(config)) {
        return 0;
    }

    if (!WebPPictureInit(pic)) {
        return 0;
    }
    /* Sharp YUV is applied during ARGB -> YUV conversion, which only happens for ARGB pictures. */
    pic->use_argb = (o->lossless || o->sharp_yuv) ? 1 : 0;
    pic->width = width;
    pic->height = height;

    WebPMemoryWriterInit(wrt);
    pic->writer = WebPMemoryWrite;
    pic->custom_ptr = wrt;
    return 1;
}

static int DimensionsOk(int64_t width, int64_t height) {
    return width > 0 && height > 0 && width <= WEBP_MAX_DIMENSION && height <= WEBP_MAX_DIMENSION;
}

/* ---------------------------------------------------------------------------
 * Animated encoding (WebPAnimEncoder) - shared by encodeAnimated and GIF conversion
 * ------------------------------------------------------------------------- */

typedef struct {
    float quality;
    int lossless;
    int method;
    int loop_count;
    int kmin;
    int kmax;
    int minimize_size;
    int allow_mixed;
    int multi_threaded;
    uint32_t bgcolor;
} AnimParams;

typedef struct {
    WebPAnimEncoder* enc;
    WebPConfig config;
    int64_t timestamp_ms;
} AnimCtx;

static int AnimBegin(AnimCtx* ctx, int width, int height, const AnimParams* p) {
    memset(ctx, 0, sizeof(*ctx));

    WebPAnimEncoderOptions options;
    if (!WebPAnimEncoderOptionsInit(&options)) {
        return 0;
    }
    options.anim_params.loop_count = p->loop_count;
    options.anim_params.bgcolor = p->bgcolor;
    options.kmin = p->kmin;
    options.kmax = p->kmax;
    options.minimize_size = p->minimize_size ? 1 : 0;
    options.allow_mixed = p->allow_mixed ? 1 : 0;

    if (!WebPConfigInit(&ctx->config)) {
        return 0;
    }
    ctx->config.lossless = p->lossless ? 1 : 0;
    ctx->config.quality = p->quality;
    ctx->config.method = p->method;
    ctx->config.thread_level = p->multi_threaded ? 1 : 0;
    if (!WebPValidateConfig(&ctx->config)) {
        return 0;
    }

    ctx->enc = WebPAnimEncoderNew(width, height, &options);
    return ctx->enc != NULL;
}

static void AnimFree(AnimCtx* ctx) {
    if (ctx->enc != NULL) {
        WebPAnimEncoderDelete(ctx->enc);
        ctx->enc = NULL;
    }
}

/* Adds an imported picture, then advances the timeline by its display duration. */
static int AnimAdd(AnimCtx* ctx, WebPPicture* pic, int duration_ms) {
    if (duration_ms < 0 || ctx->timestamp_ms + duration_ms > INT_MAX) {
        return 0;
    }
    if (!WebPAnimEncoderAdd(ctx->enc, pic, (int)ctx->timestamp_ms, &ctx->config)) {
        return 0;
    }
    ctx->timestamp_ms += duration_ms;
    return 1;
}

/* Flushes the last frame and assembles the file. On success the caller owns out->bytes. */
static int AnimFinish(AnimCtx* ctx, WebPData* out) {
    WebPDataInit(out);
    return WebPAnimEncoderAdd(ctx->enc, NULL, (int)ctx->timestamp_ms, NULL) &&
           WebPAnimEncoderAssemble(ctx->enc, out);
}

/* ---------------------------------------------------------------------------
 * GIF -> WebP conversion
 * ------------------------------------------------------------------------- */

#ifdef HAVE_GIFLIB

typedef struct {
    float quality;
    int lossless;
    int method;
    int extract_first;
    int loop_count;       /* -1 = use the GIF's own loop count */
    int kmin;
    int kmax;
    int minimize_size;
    int allow_mixed;
    int multi_threaded;
} GifConvertParams;

/* gif2webp uses a transparent white background hint. */
#define GIF_BGCOLOR_HINT 0x00FFFFFFu

/* Encodes one canvas-sized RGBA frame as a still WebP. On success the caller owns out->bytes. */
static int EncodeStaticRGBA(const uint8_t* rgba, int width, int height,
                            const GifConvertParams* p, WebPData* out) {
    WebPConfig config;
    WebPPicture pic;
    WebPMemoryWriter wrt;
    int ok = 0;

    WebPDataInit(out);
    if (!WebPConfigInit(&config)) {
        return 0;
    }
    config.lossless = p->lossless ? 1 : 0;
    config.quality = p->quality;
    config.method = p->method;
    config.thread_level = p->multi_threaded ? 1 : 0;
    if (!WebPValidateConfig(&config) || !WebPPictureInit(&pic)) {
        return 0;
    }
    pic.use_argb = 1;
    pic.width = width;
    pic.height = height;
    WebPMemoryWriterInit(&wrt);
    pic.writer = WebPMemoryWrite;
    pic.custom_ptr = &wrt;

    if (WebPPictureImportRGBA(&pic, rgba, width * 4) && WebPEncode(&config, &pic) && wrt.size > 0) {
        out->bytes = wrt.mem;
        out->size = wrt.size;
        ok = 1;
    } else {
        WebPMemoryWriterClear(&wrt);
    }
    WebPPictureFree(&pic);
    return ok;
}

typedef struct {
    const GifConvertParams* params;
    uint8_t* first;       /* first frame, held until we know whether the GIF is animated */
    int first_duration;
    int frames;
    int failed;
    int have_encoder;
    AnimCtx anim;
    WebPData still;       /* result when only the first frame is wanted */
    int have_still;
} GifConvertState;

static int AnimAddRGBA(AnimCtx* anim, const uint8_t* rgba, int width, int height, int duration_ms) {
    WebPPicture pic;
    if (!WebPPictureInit(&pic)) {
        return 0;
    }
    pic.use_argb = 1;
    pic.width = width;
    pic.height = height;
    int ok = WebPPictureImportRGBA(&pic, rgba, width * 4) && AnimAdd(anim, &pic, duration_ms);
    WebPPictureFree(&pic);
    return ok;
}

static int GifConvertCallback(void* user, const GifStreamInfo* info, const uint8_t* rgba, int duration_ms) {
    GifConvertState* s = (GifConvertState*)user;
    const GifConvertParams* p = s->params;
    int w = info->canvas_width, h = info->canvas_height;

    if (s->frames == 0) {
        if (p->extract_first) {
            s->have_still = EncodeStaticRGBA(rgba, w, h, p, &s->still);
            s->failed = !s->have_still;
            s->frames = 1;
            return 0;  /* the rest of the file is never parsed */
        }
        s->first = (uint8_t*)malloc((size_t)w * h * 4);
        if (s->first == NULL) {
            s->failed = 1;
            return 0;
        }
        memcpy(s->first, rgba, (size_t)w * h * 4);
        s->first_duration = duration_ms;
        s->frames = 1;
        return 1;
    }

    if (!s->have_encoder) {
        /* Second frame: the GIF is animated. The loop count is known by now. */
        AnimParams ap;
        ap.quality = p->quality;
        ap.lossless = p->lossless;
        ap.method = p->method;
        ap.loop_count = p->loop_count == -1 ? info->loop_count : p->loop_count;
        ap.kmin = p->kmin;
        ap.kmax = p->kmax;
        ap.minimize_size = p->minimize_size;
        ap.allow_mixed = p->allow_mixed;
        ap.multi_threaded = p->multi_threaded;
        ap.bgcolor = GIF_BGCOLOR_HINT;
        if (!AnimBegin(&s->anim, w, h, &ap)) {
            s->failed = 1;
            return 0;
        }
        s->have_encoder = 1;
        if (!AnimAddRGBA(&s->anim, s->first, w, h, s->first_duration)) {
            s->failed = 1;
            return 0;
        }
        free(s->first);
        s->first = NULL;
    }

    if (!AnimAddRGBA(&s->anim, rgba, w, h, duration_ms)) {
        s->failed = 1;
        return 0;
    }
    s->frames++;
    return 1;
}

/*
 * Converts GIF bytes to a still (one frame, or first-frame-only) or animated WebP in a single
 * streaming pass: memory use is O(canvas), independent of the frame count.
 * On success the caller owns out->bytes (release with WebPDataClear / WebPFree).
 */
static int ConvertGif(const uint8_t* data, size_t size, const GifConvertParams* p, WebPData* out) {
    GifConvertState s;
    GifStreamInfo info;
    int ok = 0;

    memset(&s, 0, sizeof(s));
    s.params = p;
    WebPDataInit(out);

    if (DecodeGifStream(data, size, GifConvertCallback, &s, &info) && !s.failed) {
        if (s.have_still) {
            *out = s.still;
            WebPDataInit(&s.still);
            ok = 1;
        } else if (!s.have_encoder && s.first != NULL) {
            /* Single-frame GIF -> still image */
            ok = EncodeStaticRGBA(s.first, info.canvas_width, info.canvas_height, p, out);
        } else if (s.have_encoder) {
            ok = AnimFinish(&s.anim, out);
        }
    }

    AnimFree(&s.anim);
    free(s.first);
    WebPDataClear(&s.still);
    if (!ok) {
        WebPDataClear(out);
    }
    return ok;
}

#endif /* HAVE_GIFLIB */

/* ===========================================================================
 * JNI entry points
 * =========================================================================== */

/* ---------------------------------------------------------------------------
 * Probes
 * ------------------------------------------------------------------------- */

/*
 * Smoke test: verifies the library loaded, the JNI bindings link, and libwebp
 * is callable. Invoked from NativeWebP's static initializer.
 */
JNIEXPORT jint JNICALL Java_xyz_lychee_webp4j_natives_NativeWebP_getLibWebPVersion
  (JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz;
    return (jint) WebPGetEncoderVersion();
}

/*
 * Retrieves width/height of a WebP image into dimensions[0..1].
 */
JNIEXPORT jboolean JNICALL Java_xyz_lychee_webp4j_natives_NativeWebP_getInfo
  (JNIEnv *env, jclass clazz, jbyteArray data, jintArray dimensions) {
    (void)clazz;
    if (data == NULL || dimensions == NULL) {
        return JNI_FALSE;
    }
    jsize data_size = (*env)->GetArrayLength(env, data);
    if ((*env)->GetArrayLength(env, dimensions) < 2) {
        return JNI_FALSE;
    }

    WebPBitstreamFeatures features;
    jbyte* webp_data = (*env)->GetPrimitiveArrayCritical(env, data, NULL);
    if (webp_data == NULL) {
        return JNI_FALSE;
    }
    VP8StatusCode status = WebPGetFeatures((const uint8_t*)webp_data, (size_t)data_size, &features);
    (*env)->ReleasePrimitiveArrayCritical(env, data, webp_data, JNI_ABORT);

    if (status != VP8_STATUS_OK) {
        return JNI_FALSE;
    }

    jint dims[2] = { (jint)features.width, (jint)features.height };
    (*env)->SetIntArrayRegion(env, dimensions, 0, 2, dims);
    return JNI_TRUE;
}

/*
 * Extracts WebP bitstream features into a WebPBitstreamFeatures object.
 * Returns the VP8StatusCode from WebPGetFeatures (0 = OK).
 * dataSize must not exceed the array length (VP8_STATUS_INVALID_PARAM otherwise).
 */
JNIEXPORT jint JNICALL Java_xyz_lychee_webp4j_natives_NativeWebP_getFeatures
  (JNIEnv *env, jclass clazz, jbyteArray data, jint dataSize, jobject featuresObj) {
    (void)clazz;
    if (data == NULL || featuresObj == NULL) {
        return VP8_STATUS_INVALID_PARAM;
    }
    if (dataSize < 0 || dataSize > (*env)->GetArrayLength(env, data)) {
        return VP8_STATUS_INVALID_PARAM;
    }

    WebPBitstreamFeatures features;
    jbyte* webp_data = (*env)->GetPrimitiveArrayCritical(env, data, NULL);
    if (webp_data == NULL) {
        return -1;
    }
    int status = WebPGetFeatures((const uint8_t*)webp_data, (size_t)dataSize, &features);
    (*env)->ReleasePrimitiveArrayCritical(env, data, webp_data, JNI_ABORT);

    if (status != VP8_STATUS_OK) {
        return status;
    }

    (*env)->SetIntField(env, featuresObj, g_features_width, features.width);
    (*env)->SetIntField(env, featuresObj, g_features_height, features.height);
    (*env)->SetBooleanField(env, featuresObj, g_features_has_alpha,
                            features.has_alpha ? JNI_TRUE : JNI_FALSE);
    (*env)->SetBooleanField(env, featuresObj, g_features_has_animation,
                            features.has_animation ? JNI_TRUE : JNI_FALSE);
    (*env)->SetIntField(env, featuresObj, g_features_format, features.format);
    return status;
}

/* ---------------------------------------------------------------------------
 * Static image encode/decode
 * ------------------------------------------------------------------------- */

/*
 * Materializes the bitstream as a Java byte array. Frees the picture and the
 * writer in all cases.
 */
static jbyteArray FinishEncode(JNIEnv* env, WebPConfig* config, WebPPicture* pic,
                               WebPMemoryWriter* wrt, int imported) {
    int ok = imported && WebPEncode(config, pic);
    WebPPictureFree(pic);

    jbyteArray result = NULL;
    if (ok && wrt->size > 0 && wrt->size <= (size_t)INT_MAX) {
        result = (*env)->NewByteArray(env, (jsize)wrt->size);
        if (result != NULL) {
            (*env)->SetByteArrayRegion(env, result, 0, (jsize)wrt->size, (const jbyte*)wrt->mem);
        }
    }
    WebPMemoryWriterClear(wrt);
    return result;
}

/*
 * Shared body of encode / encodeAdvanced: packed ARGB pixels (BGRA bytes on LE).
 * The only work inside the critical section is the single linear import pass;
 * the expensive WebPEncode runs after the array is released, so GC is never
 * blocked during compression.
 */
static jbyteArray EncodeArgb(JNIEnv* env, jintArray pixels, jint width, jint height,
                             jboolean hasAlpha, const EncodeOptions* options) {
    if (pixels == NULL || !DimensionsOk(width, height) ||
        (jlong)(*env)->GetArrayLength(env, pixels) != (jlong)width * height) {
        return NULL;
    }

    WebPConfig config;
    WebPPicture pic;
    WebPMemoryWriter wrt;
    if (!SetupEncode(&config, &pic, &wrt, width, height, options)) {
        return NULL;
    }

    jint* p = (*env)->GetPrimitiveArrayCritical(env, pixels, NULL);
    int imported = 0;
    if (p != NULL) {
        imported = hasAlpha
            ? WebPPictureImportBGRA(&pic, (const uint8_t*)p, width * 4)
            : WebPPictureImportBGRX(&pic, (const uint8_t*)p, width * 4);
        (*env)->ReleasePrimitiveArrayCritical(env, pixels, p, JNI_ABORT);
    }

    return FinishEncode(env, &config, &pic, &wrt, imported);
}

/*
 * Encodes packed ARGB pixels (BGRA bytes on LE) to a WebP bitstream.
 */
JNIEXPORT jbyteArray JNICALL Java_xyz_lychee_webp4j_natives_NativeWebP_encode
  (JNIEnv *env, jclass clazz, jintArray pixels, jint width, jint height,
   jfloat quality, jboolean lossless, jboolean hasAlpha, jboolean multiThreaded) {
    (void)clazz;
    EncodeOptions options;
    SimpleEncodeOptions(&options, quality, lossless, multiThreaded);
    return EncodeArgb(env, pixels, width, height, hasAlpha, &options);
}

/*
 * Encodes packed ARGB pixels with the full set of libwebp options.
 */
JNIEXPORT jbyteArray JNICALL Java_xyz_lychee_webp4j_natives_NativeWebP_encodeAdvanced
  (JNIEnv *env, jclass clazz, jintArray pixels, jint width, jint height, jboolean hasAlpha,
   jfloat quality, jboolean lossless, jint method, jint nearLossless, jboolean exact,
   jint alphaQuality, jboolean sharpYuv, jint targetSize, jboolean multiThreaded) {
    (void)clazz;
    EncodeOptions options;
    options.quality = quality;
    options.lossless = lossless ? 1 : 0;
    options.method = method;
    options.near_lossless = nearLossless;
    options.exact = exact ? 1 : 0;
    options.alpha_quality = alphaQuality;
    options.sharp_yuv = sharpYuv ? 1 : 0;
    options.target_size = targetSize;
    options.multi_threaded = multiThreaded ? 1 : 0;
    return EncodeArgb(env, pixels, width, height, hasAlpha, &options);
}

/*
 * Encodes interleaved BGR bytes (a TYPE_3BYTE_BGR backing array) to a WebP
 * bitstream. Fast path for ImageIO's most common output format - the byte
 * order already matches WebPPictureImportBGR, so the image's backing array
 * is imported directly with no Java-side conversion.
 */
JNIEXPORT jbyteArray JNICALL Java_xyz_lychee_webp4j_natives_NativeWebP_encodeBgr
  (JNIEnv *env, jclass clazz, jbyteArray pixels, jint width, jint height,
   jfloat quality, jboolean lossless, jboolean multiThreaded) {
    (void)clazz;
    if (pixels == NULL || !DimensionsOk(width, height) ||
        (jlong)(*env)->GetArrayLength(env, pixels) != (jlong)width * height * 3) {
        return NULL;
    }

    EncodeOptions options;
    SimpleEncodeOptions(&options, quality, lossless, multiThreaded);

    WebPConfig config;
    WebPPicture pic;
    WebPMemoryWriter wrt;
    if (!SetupEncode(&config, &pic, &wrt, width, height, &options)) {
        return NULL;
    }

    jbyte* p = (*env)->GetPrimitiveArrayCritical(env, pixels, NULL);
    int imported = 0;
    if (p != NULL) {
        imported = WebPPictureImportBGR(&pic, (const uint8_t*)p, width * 3);
        (*env)->ReleasePrimitiveArrayCritical(env, pixels, p, JNI_ABORT);
    }

    return FinishEncode(env, &config, &pic, &wrt, imported);
}

/*
 * Decodes a WebP bitstream directly into a packed ARGB int[].
 *
 * Zero-copy: libwebp writes BGRA bytes straight into the pinned Java array,
 * which is typically the backing store of the BufferedImage being returned.
 * Both arrays stay pinned across the whole decode - the accepted cost; for
 * extreme image sizes this can delay GC for the duration of the decode.
 * Nested criticals are legal here because no other JNI call happens between
 * acquisition and release.
 */
JNIEXPORT jboolean JNICALL Java_xyz_lychee_webp4j_natives_NativeWebP_decodeInto
  (JNIEnv *env, jclass clazz, jbyteArray data, jintArray output, jint outputStride) {
    (void)clazz;
    if (data == NULL || output == NULL || outputStride <= 0) {
        return JNI_FALSE;
    }
    jsize data_size = (*env)->GetArrayLength(env, data);
    size_t output_size = (size_t)(*env)->GetArrayLength(env, output) * 4;

    jbyte* in = (*env)->GetPrimitiveArrayCritical(env, data, NULL);
    if (in == NULL) {
        return JNI_FALSE;
    }
    jint* out = (*env)->GetPrimitiveArrayCritical(env, output, NULL);
    if (out == NULL) {
        (*env)->ReleasePrimitiveArrayCritical(env, data, in, JNI_ABORT);
        return JNI_FALSE;
    }

    uint8_t* decoded = WebPDecodeBGRAInto((const uint8_t*)in, (size_t)data_size,
                                          (uint8_t*)out, output_size, (int)outputStride);

    (*env)->ReleasePrimitiveArrayCritical(env, output, out, 0);
    (*env)->ReleasePrimitiveArrayCritical(env, data, in, JNI_ABORT);

    return decoded != NULL ? JNI_TRUE : JNI_FALSE;
}

/* ---------------------------------------------------------------------------
 * GIF
 * ------------------------------------------------------------------------- */

/*
 * Gets GIF metadata via giflib and populates an AnimationInfo object.
 * Returns JNI_FALSE when giflib is unavailable, triggering the Java fallback.
 *
 * Uses GetByteArrayElements (not a critical section): the giflib scan walks
 * the whole file and its duration is unbounded.
 */
JNIEXPORT jboolean JNICALL Java_xyz_lychee_webp4j_natives_NativeWebP_getGifInfo
  (JNIEnv *env, jclass clazz, jbyteArray gifData, jobject info) {
    (void)clazz;
#ifdef HAVE_GIFLIB
    if (gifData == NULL || info == NULL) {
        return JNI_FALSE;
    }
    jsize data_size = (*env)->GetArrayLength(env, gifData);
    jbyte* gif_bytes = (*env)->GetByteArrayElements(env, gifData, NULL);
    if (gif_bytes == NULL) {
        return JNI_FALSE;
    }

    int width, height, frame_count, loop_count, has_transparency;
    int success = GetGifInfo((const uint8_t*)gif_bytes, (size_t)data_size,
                             &width, &height, &frame_count,
                             &loop_count, &has_transparency);
    (*env)->ReleaseByteArrayElements(env, gifData, gif_bytes, JNI_ABORT);

    if (!success) {
        return JNI_FALSE;
    }

    (*env)->SetIntField(env, info, g_anim_info_width, width);
    (*env)->SetIntField(env, info, g_anim_info_height, height);
    (*env)->SetIntField(env, info, g_anim_info_frame_count, frame_count);
    (*env)->SetIntField(env, info, g_anim_info_loop_count, loop_count);
    (*env)->SetBooleanField(env, info, g_anim_info_has_transparency,
                            has_transparency ? JNI_TRUE : JNI_FALSE);
    return JNI_TRUE;
#else
    (void)env; (void)gifData; (void)info;
    return JNI_FALSE;
#endif
}

/*
 * Converts GIF data to WebP using the native giflib decoder (primary path).
 * Returns NULL on any failure, triggering the Java ImageIO fallback.
 */
JNIEXPORT jbyteArray JNICALL Java_xyz_lychee_webp4j_natives_NativeWebP_encodeGifToWebP
  (JNIEnv *env, jclass clazz, jbyteArray gifData, jfloat quality,
   jboolean lossless, jint compressionMethod, jboolean extractFirstFrameOnly,
   jint loopCount, jint kmin, jint kmax, jboolean minimizeSize, jboolean allowMixed,
   jboolean multiThreaded) {
    (void)clazz;
#ifdef HAVE_GIFLIB
    jsize data_size = (*env)->GetArrayLength(env, gifData);
    jbyte* gif_bytes = (*env)->GetByteArrayElements(env, gifData, NULL);
    if (gif_bytes == NULL) {
        return NULL;
    }

    GifConvertParams params;
    params.quality = quality;
    params.lossless = lossless ? 1 : 0;
    params.method = compressionMethod;
    params.extract_first = extractFirstFrameOnly ? 1 : 0;
    params.loop_count = loopCount;
    params.kmin = kmin;
    params.kmax = kmax;
    params.minimize_size = minimizeSize ? 1 : 0;
    params.allow_mixed = allowMixed ? 1 : 0;
    params.multi_threaded = multiThreaded ? 1 : 0;

    WebPData webp;
    int ok = ConvertGif((const uint8_t*)gif_bytes, (size_t)data_size, &params, &webp);
    (*env)->ReleaseByteArrayElements(env, gifData, gif_bytes, JNI_ABORT);

    jbyteArray result = NULL;
    if (ok && webp.size <= (size_t)INT_MAX) {
        result = (*env)->NewByteArray(env, (jsize)webp.size);
        if (result != NULL) {
            (*env)->SetByteArrayRegion(env, result, 0, (jsize)webp.size, (const jbyte*)webp.bytes);
        }
    }
    WebPDataClear(&webp);
    return result;
#else
    (void)env; (void)gifData; (void)quality; (void)lossless; (void)compressionMethod;
    (void)extractFirstFrameOnly; (void)loopCount; (void)kmin; (void)kmax;
    (void)minimizeSize; (void)allowMixed; (void)multiThreaded;
    return NULL;
#endif
}

/* ---------------------------------------------------------------------------
 * Animated WebP
 * ------------------------------------------------------------------------- */

/*
 * Encodes an animated WebP from packed ARGB frames (int[][]).
 *
 * Per frame, only WebPPictureImportBGRA's single linear pass runs inside the
 * critical section; the heavy WebPAnimEncoderAdd runs after the frame array
 * is released. Frame arrays are read-only (JNI_ABORT) - they may be live
 * BufferedImage backing stores.
 */
JNIEXPORT jbyteArray JNICALL Java_xyz_lychee_webp4j_natives_NativeWebP_encodeAnimated
  (JNIEnv *env, jclass clazz, jobjectArray frames, jintArray delays, jint width, jint height,
   jfloat quality, jboolean lossless, jint compressionMethod, jint loopCount,
   jint kmin, jint kmax, jboolean minimizeSize, jboolean allowMixed, jboolean multiThreaded) {
    (void)clazz;
    jsize frame_count = (*env)->GetArrayLength(env, frames);
    if (frame_count == 0 || !DimensionsOk(width, height) ||
        (*env)->GetArrayLength(env, delays) < frame_count) {
        return NULL;
    }
    jlong expected_pixels = (jlong)width * height;

    jbyteArray result = NULL;
    AnimCtx anim;
    WebPData webp_data;
    WebPDataInit(&webp_data);
    memset(&anim, 0, sizeof(anim));

    AnimParams params;
    params.quality = quality;
    params.lossless = lossless ? 1 : 0;
    params.method = compressionMethod;
    params.loop_count = loopCount;
    params.kmin = kmin;
    params.kmax = kmax;
    params.minimize_size = minimizeSize ? 1 : 0;
    params.allow_mixed = allowMixed ? 1 : 0;
    params.multi_threaded = multiThreaded ? 1 : 0;
    params.bgcolor = 0x00000000;  /* transparent background */

    if (!AnimBegin(&anim, width, height, &params)) {
        goto cleanup;
    }

    for (jsize i = 0; i < frame_count; i++) {
        jintArray frame = (jintArray)(*env)->GetObjectArrayElement(env, frames, i);
        if (frame == NULL ||
            (jlong)(*env)->GetArrayLength(env, frame) != expected_pixels) {
            if (frame != NULL) (*env)->DeleteLocalRef(env, frame);
            goto cleanup;
        }

        jint duration = 0;
        (*env)->GetIntArrayRegion(env, delays, i, 1, &duration);

        WebPPicture picture;
        if (!WebPPictureInit(&picture)) {
            (*env)->DeleteLocalRef(env, frame);
            goto cleanup;
        }
        picture.width = width;
        picture.height = height;
        picture.use_argb = 1;

        /* WebPPictureImportBGRA allocates the picture buffer itself. */
        jint* frame_pixels = (*env)->GetPrimitiveArrayCritical(env, frame, NULL);
        int imported = 0;
        if (frame_pixels != NULL) {
            imported = WebPPictureImportBGRA(&picture, (const uint8_t*)frame_pixels, width * 4);
            (*env)->ReleasePrimitiveArrayCritical(env, frame, frame_pixels, JNI_ABORT);
        }
        (*env)->DeleteLocalRef(env, frame);

        int added = imported && AnimAdd(&anim, &picture, duration);
        WebPPictureFree(&picture);
        if (!added) {
            goto cleanup;
        }
    }

    if (!AnimFinish(&anim, &webp_data)) {
        goto cleanup;
    }

    if (webp_data.size <= (size_t)INT_MAX) {
        result = (*env)->NewByteArray(env, (jsize)webp_data.size);
        if (result != NULL) {
            (*env)->SetByteArrayRegion(env, result, 0, (jsize)webp_data.size, (const jbyte*)webp_data.bytes);
        }
    }

cleanup:
    WebPDataClear(&webp_data);
    AnimFree(&anim);
    return result;
}

/*
 * Decodes an animated WebP into packed ARGB frames and populates an
 * AnimatedWebPData object (framePixels, timestamps, metadata).
 *
 * The input uses GetByteArrayElements, NOT a critical section: the
 * WebPAnimDecoder retains a pointer into the buffer for its whole lifetime,
 * and the frame loop must make JNI calls (NewIntArray, SetIntArrayRegion,
 * SetObjectArrayElement) while that pointer stays valid - both are illegal
 * inside a critical section.
 *
 * The per-frame SetIntArrayRegion copy is unavoidable: the decoder owns and
 * reuses its internal frame buffer.
 */
JNIEXPORT jboolean JNICALL Java_xyz_lychee_webp4j_natives_NativeWebP_decodeAnimated
  (JNIEnv *env, jclass clazz, jbyteArray webPData, jobject result) {
    (void)clazz;
    jboolean ok = JNI_FALSE;
    WebPAnimDecoder* dec = NULL;

    jsize data_size = (*env)->GetArrayLength(env, webPData);
    jbyte* webp_bytes = (*env)->GetByteArrayElements(env, webPData, NULL);
    if (webp_bytes == NULL) {
        return JNI_FALSE;
    }

    WebPData webp_data;
    webp_data.bytes = (const uint8_t*)webp_bytes;
    webp_data.size = (size_t)data_size;

    WebPAnimDecoderOptions dec_options;
    if (!WebPAnimDecoderOptionsInit(&dec_options)) {
        goto cleanup;
    }
    /* BGRA frame buffers == Java ARGB ints on LE: frames go into int[]
     * arrays that the Java side wraps directly as TYPE_INT_ARGB images. */
    dec_options.color_mode = MODE_BGRA;
    dec_options.use_threads = 1;

    dec = WebPAnimDecoderNew(&webp_data, &dec_options);
    if (dec == NULL) {
        goto cleanup;
    }

    WebPAnimInfo anim_info;
    if (!WebPAnimDecoderGetInfo(dec, &anim_info)) {
        goto cleanup;
    }

    uint32_t canvas_width = anim_info.canvas_width;
    uint32_t canvas_height = anim_info.canvas_height;
    uint32_t frame_count = anim_info.frame_count;
    uint64_t frame_pixels64 = (uint64_t)canvas_width * canvas_height;
    if (canvas_width == 0 || canvas_height == 0 || frame_count == 0 ||
        frame_pixels64 > (uint64_t)INT_MAX ||
        frame_pixels64 * frame_count > WEBP4J_MAX_TOTAL_PIXELS) {
        goto cleanup;
    }
    jsize frame_pixels = (jsize)frame_pixels64;

    jobjectArray framePixelsArray =
        (*env)->NewObjectArray(env, (jsize)frame_count, g_int_array_class, NULL);
    if (framePixelsArray == NULL) {
        goto cleanup;
    }
    jintArray timestampsArray = (*env)->NewIntArray(env, (jsize)frame_count);
    if (timestampsArray == NULL) {
        goto cleanup;
    }

    uint32_t frame_index = 0;
    while (WebPAnimDecoderHasMoreFrames(dec) && frame_index < frame_count) {
        uint8_t* buf;
        int timestamp;
        if (!WebPAnimDecoderGetNext(dec, &buf, &timestamp)) {
            goto cleanup;
        }

        jintArray frameArray = (*env)->NewIntArray(env, frame_pixels);
        if (frameArray == NULL) {
            goto cleanup;
        }
        (*env)->SetIntArrayRegion(env, frameArray, 0, frame_pixels, (const jint*)buf);
        (*env)->SetObjectArrayElement(env, framePixelsArray, (jsize)frame_index, frameArray);
        (*env)->DeleteLocalRef(env, frameArray);

        jint ts = (jint)timestamp;
        (*env)->SetIntArrayRegion(env, timestampsArray, (jsize)frame_index, 1, &ts);
        frame_index++;
    }

    (*env)->SetIntField(env, result, g_anim_data_canvas_width, (jint)canvas_width);
    (*env)->SetIntField(env, result, g_anim_data_canvas_height, (jint)canvas_height);
    (*env)->SetIntField(env, result, g_anim_data_loop_count, (jint)anim_info.loop_count);
    (*env)->SetIntField(env, result, g_anim_data_bgcolor, (jint)anim_info.bgcolor);
    (*env)->SetIntField(env, result, g_anim_data_frame_count, (jint)frame_index);
    (*env)->SetObjectField(env, result, g_anim_data_frame_pixels, framePixelsArray);
    (*env)->SetObjectField(env, result, g_anim_data_timestamps, timestampsArray);
    ok = JNI_TRUE;

cleanup:
    if (dec != NULL) {
        WebPAnimDecoderDelete(dec);
    }
    (*env)->ReleaseByteArrayElements(env, webPData, webp_bytes, JNI_ABORT);
    return ok;
}


/* ===========================================================================
 * Foreign Function & Memory API (FFM) - C ABI Entry Points
 *
 * Direct C ABI exports for Java 22+ java.lang.foreign downcalls.
 * Bypasses JNIEnv and critical-section pinning overhead completely.
 *
 * Memory returned through out_data parameters is allocated by libwebp and
 * must be released with webp4j_ffm_free (WebPFree).
 * =========================================================================== */

#ifdef _WIN32
#define WEBP4J_FFM_EXPORT __declspec(dllexport)
#else
#define WEBP4J_FFM_EXPORT __attribute__((visibility("default")))
#endif

WEBP4J_FFM_EXPORT int32_t webp4j_ffm_get_version(void) {
    return (int32_t)WebPGetEncoderVersion();
}

WEBP4J_FFM_EXPORT int32_t webp4j_ffm_get_info(const uint8_t* data, size_t size,
                                              int32_t* width, int32_t* height) {
    if (data == NULL || width == NULL || height == NULL) return 0;
    int w = 0, h = 0;
    int ok = WebPGetInfo(data, size, &w, &h);
    if (ok) {
        *width = w;
        *height = h;
        return 1;
    }
    return 0;
}

WEBP4J_FFM_EXPORT int32_t webp4j_ffm_get_features(const uint8_t* data, size_t size,
                                                  int32_t* width, int32_t* height,
                                                  int32_t* has_alpha, int32_t* has_animation,
                                                  int32_t* format) {
    if (data == NULL) return -1;
    WebPBitstreamFeatures features;
    VP8StatusCode status = WebPGetFeatures(data, size, &features);
    if (status != VP8_STATUS_OK) return (int32_t)status;

    if (width) *width = features.width;
    if (height) *height = features.height;
    if (has_alpha) *has_alpha = features.has_alpha ? 1 : 0;
    if (has_animation) *has_animation = features.has_animation ? 1 : 0;
    if (format) *format = features.format;
    return 0;
}

/* Shared tail of the FFM static encoders: encodes the imported picture, hands over the buffer. */
static size_t FfmFinishEncode(WebPConfig* config, WebPPicture* pic, WebPMemoryWriter* wrt,
                              int imported, uint8_t** out_data) {
    int ok = imported && WebPEncode(config, pic);
    WebPPictureFree(pic);

    if (ok && wrt->size > 0) {
        *out_data = wrt->mem;
        return wrt->size;
    }
    WebPMemoryWriterClear(wrt);
    return 0;
}

static size_t FfmEncodeArgb(const uint8_t* pixels, int32_t width, int32_t height, int32_t has_alpha,
                            const EncodeOptions* options, uint8_t** out_data) {
    if (!DimensionsOk(width, height) || pixels == NULL || out_data == NULL) return 0;
    *out_data = NULL;

    WebPConfig config;
    WebPPicture pic;
    WebPMemoryWriter wrt;
    if (!SetupEncode(&config, &pic, &wrt, width, height, options)) {
        return 0;
    }
    int imported = has_alpha
        ? WebPPictureImportBGRA(&pic, pixels, width * 4)
        : WebPPictureImportBGRX(&pic, pixels, width * 4);
    return FfmFinishEncode(&config, &pic, &wrt, imported, out_data);
}

WEBP4J_FFM_EXPORT size_t webp4j_ffm_encode(
    const uint8_t* pixels, int32_t width, int32_t height,
    float quality, int32_t lossless, int32_t has_alpha, int32_t multi_threaded,
    uint8_t** out_data) {
    EncodeOptions options;
    SimpleEncodeOptions(&options, quality, lossless, multi_threaded);
    return FfmEncodeArgb(pixels, width, height, has_alpha, &options, out_data);
}

WEBP4J_FFM_EXPORT size_t webp4j_ffm_encode_advanced(
    const uint8_t* pixels, int32_t width, int32_t height, int32_t has_alpha,
    float quality, int32_t lossless, int32_t method, int32_t near_lossless, int32_t exact,
    int32_t alpha_quality, int32_t sharp_yuv, int32_t target_size, int32_t multi_threaded,
    uint8_t** out_data) {
    EncodeOptions options;
    options.quality = quality;
    options.lossless = lossless ? 1 : 0;
    options.method = method;
    options.near_lossless = near_lossless;
    options.exact = exact ? 1 : 0;
    options.alpha_quality = alpha_quality;
    options.sharp_yuv = sharp_yuv ? 1 : 0;
    options.target_size = target_size;
    options.multi_threaded = multi_threaded ? 1 : 0;
    return FfmEncodeArgb(pixels, width, height, has_alpha, &options, out_data);
}

WEBP4J_FFM_EXPORT size_t webp4j_ffm_encode_bgr(
    const uint8_t* pixels, int32_t width, int32_t height,
    float quality, int32_t lossless, int32_t multi_threaded,
    uint8_t** out_data) {
    if (!DimensionsOk(width, height) || pixels == NULL || out_data == NULL) return 0;
    *out_data = NULL;

    EncodeOptions options;
    SimpleEncodeOptions(&options, quality, lossless, multi_threaded);

    WebPConfig config;
    WebPPicture pic;
    WebPMemoryWriter wrt;
    if (!SetupEncode(&config, &pic, &wrt, width, height, &options)) {
        return 0;
    }
    int imported = WebPPictureImportBGR(&pic, pixels, width * 3);
    return FfmFinishEncode(&config, &pic, &wrt, imported, out_data);
}

WEBP4J_FFM_EXPORT int32_t webp4j_ffm_decode_into(
    const uint8_t* data, size_t size,
    uint8_t* output_buffer, size_t output_size, int32_t output_stride) {
    if (data == NULL || output_buffer == NULL || output_stride <= 0) return 0;
    uint8_t* res = WebPDecodeBGRAInto(data, size, output_buffer, output_size, (int)output_stride);
    return res != NULL ? 1 : 0;
}

/* Releases any buffer returned through an out_data parameter of this library. */
WEBP4J_FFM_EXPORT void webp4j_ffm_free(void* ptr) {
    WebPFree(ptr);
}

WEBP4J_FFM_EXPORT size_t webp4j_ffm_encode_animated(
    const uint8_t** frames, const int32_t* delays, int32_t frame_count,
    int32_t width, int32_t height, float quality, int32_t lossless,
    int32_t compression_method, int32_t loop_count, int32_t kmin, int32_t kmax,
    int32_t minimize_size, int32_t allow_mixed, int32_t multi_threaded,
    uint8_t** out_data) {
    if (frame_count <= 0 || !DimensionsOk(width, height) || frames == NULL || delays == NULL ||
        out_data == NULL) {
        return 0;
    }
    *out_data = NULL;

    AnimParams params;
    params.quality = quality;
    params.lossless = lossless ? 1 : 0;
    params.method = compression_method;
    params.loop_count = loop_count;
    params.kmin = kmin;
    params.kmax = kmax;
    params.minimize_size = minimize_size ? 1 : 0;
    params.allow_mixed = allow_mixed ? 1 : 0;
    params.multi_threaded = multi_threaded ? 1 : 0;
    params.bgcolor = 0x00000000;

    AnimCtx anim;
    WebPData webp_data;
    WebPDataInit(&webp_data);
    size_t result = 0;

    if (!AnimBegin(&anim, width, height, &params)) {
        AnimFree(&anim);
        return 0;
    }

    int ok = 1;
    for (int32_t i = 0; i < frame_count && ok; i++) {
        WebPPicture pic;
        if (frames[i] == NULL || !WebPPictureInit(&pic)) {
            ok = 0;
            break;
        }
        pic.width = width;
        pic.height = height;
        pic.use_argb = 1;

        ok = WebPPictureImportBGRA(&pic, frames[i], width * 4) && AnimAdd(&anim, &pic, delays[i]);
        WebPPictureFree(&pic);
    }

    if (ok && AnimFinish(&anim, &webp_data)) {
        *out_data = (uint8_t*)webp_data.bytes;
        result = webp_data.size;
    } else {
        WebPDataClear(&webp_data);
    }
    AnimFree(&anim);
    return result;
}

/* ===========================================================================
 * Animated WebP Decoder - Streaming API (Zero intermediate C buffer)
 * =========================================================================== */

WEBP4J_FFM_EXPORT void* webp4j_ffm_anim_decoder_new(
    const uint8_t* webp_bytes, size_t data_size,
    int32_t* canvas_width, int32_t* canvas_height,
    int32_t* loop_count, int32_t* bgcolor, int32_t* frame_count) {
    if (webp_bytes == NULL || data_size == 0) return NULL;

    WebPData webp_data;
    webp_data.bytes = webp_bytes;
    webp_data.size = data_size;

    WebPAnimDecoderOptions dec_options;
    if (!WebPAnimDecoderOptionsInit(&dec_options)) return NULL;
    dec_options.color_mode = MODE_BGRA;
    dec_options.use_threads = 1;

    WebPAnimDecoder* dec = WebPAnimDecoderNew(&webp_data, &dec_options);
    if (dec == NULL) return NULL;

    WebPAnimInfo anim_info;
    if (!WebPAnimDecoderGetInfo(dec, &anim_info)) {
        WebPAnimDecoderDelete(dec);
        return NULL;
    }

    if (canvas_width) *canvas_width = (int32_t)anim_info.canvas_width;
    if (canvas_height) *canvas_height = (int32_t)anim_info.canvas_height;
    if (loop_count) *loop_count = (int32_t)anim_info.loop_count;
    if (bgcolor) *bgcolor = (int32_t)anim_info.bgcolor;
    if (frame_count) *frame_count = (int32_t)anim_info.frame_count;

    return (void*)dec;
}

WEBP4J_FFM_EXPORT int32_t webp4j_ffm_anim_decoder_get_next(
    void* dec_ptr, uint8_t* out_frame, size_t frame_bytes, int32_t* out_timestamp) {
    if (dec_ptr == NULL || out_frame == NULL) return 0;
    WebPAnimDecoder* dec = (WebPAnimDecoder*)dec_ptr;

    /* The caller-supplied buffer size must match the canvas exactly, never exceed what the decoder owns. */
    WebPAnimInfo info;
    if (!WebPAnimDecoderGetInfo(dec, &info) ||
        frame_bytes != (size_t)info.canvas_width * info.canvas_height * 4) {
        return 0;
    }

    if (!WebPAnimDecoderHasMoreFrames(dec)) return 0;

    uint8_t* buf = NULL;
    int ts = 0;
    if (!WebPAnimDecoderGetNext(dec, &buf, &ts)) return 0;

    memcpy(out_frame, buf, frame_bytes);
    if (out_timestamp) *out_timestamp = (int32_t)ts;
    return 1;
}

WEBP4J_FFM_EXPORT void webp4j_ffm_anim_decoder_delete(void* dec_ptr) {
    if (dec_ptr != NULL) {
        WebPAnimDecoderDelete((WebPAnimDecoder*)dec_ptr);
    }
}

/* Kept for ABI compatibility: all out_data buffers are released the same way. */
WEBP4J_FFM_EXPORT void webp4j_ffm_free_animated(void* ptr) {
    WebPFree(ptr);
}


WEBP4J_FFM_EXPORT int32_t webp4j_ffm_decode_animated(
    const uint8_t* webp_bytes, size_t data_size,
    int32_t* canvas_width, int32_t* canvas_height,
    int32_t* loop_count, int32_t* bgcolor, int32_t* frame_count,
    uint8_t** out_all_pixels, int32_t** out_timestamps) {
    if (webp_bytes == NULL || data_size == 0 || out_all_pixels == NULL || out_timestamps == NULL) {
        return 0;
    }
    *out_all_pixels = NULL;
    *out_timestamps = NULL;

    WebPData webp_data;
    webp_data.bytes = webp_bytes;
    webp_data.size = data_size;

    WebPAnimDecoderOptions dec_options;
    if (!WebPAnimDecoderOptionsInit(&dec_options)) {
        return 0;
    }
    dec_options.color_mode = MODE_BGRA;
    dec_options.use_threads = 1;

    WebPAnimDecoder* dec = WebPAnimDecoderNew(&webp_data, &dec_options);
    if (dec == NULL) {
        return 0;
    }

    WebPAnimInfo anim_info;
    if (!WebPAnimDecoderGetInfo(dec, &anim_info)) {
        WebPAnimDecoderDelete(dec);
        return 0;
    }

    uint64_t cw = anim_info.canvas_width;
    uint64_t ch = anim_info.canvas_height;
    uint64_t fc = anim_info.frame_count;
    uint64_t frame_px = cw * ch;
    if (cw == 0 || ch == 0 || fc == 0 || frame_px > (uint64_t)INT32_MAX ||
        frame_px * fc > WEBP4J_MAX_TOTAL_PIXELS) {
        WebPAnimDecoderDelete(dec);
        return 0;
    }
    size_t frame_bytes = (size_t)frame_px * 4;

    uint8_t* all_pixels = (uint8_t*)WebPMalloc((size_t)fc * frame_bytes);
    int32_t* timestamps = (int32_t*)WebPMalloc((size_t)fc * sizeof(int32_t));
    if (!all_pixels || !timestamps) {
        WebPFree(all_pixels);
        WebPFree(timestamps);
        WebPAnimDecoderDelete(dec);
        return 0;
    }

    uint32_t frame_idx = 0;
    while (WebPAnimDecoderHasMoreFrames(dec) && frame_idx < fc) {
        uint8_t* buf = NULL;
        int ts = 0;
        if (!WebPAnimDecoderGetNext(dec, &buf, &ts)) {
            break;
        }
        memcpy(all_pixels + (size_t)frame_idx * frame_bytes, buf, frame_bytes);
        timestamps[frame_idx] = ts;
        frame_idx++;
    }
    WebPAnimDecoderDelete(dec);

    if (canvas_width) *canvas_width = (int32_t)cw;
    if (canvas_height) *canvas_height = (int32_t)ch;
    if (loop_count) *loop_count = (int32_t)anim_info.loop_count;
    if (bgcolor) *bgcolor = (int32_t)anim_info.bgcolor;
    if (frame_count) *frame_count = (int32_t)frame_idx;
    *out_all_pixels = all_pixels;
    *out_timestamps = timestamps;
    return 1;
}

#ifdef HAVE_GIFLIB
WEBP4J_FFM_EXPORT int32_t webp4j_ffm_get_gif_info(
    const uint8_t* gif_bytes, size_t data_size,
    int32_t* width, int32_t* height, int32_t* frame_count,
    int32_t* loop_count, int32_t* has_transparency) {
    if (gif_bytes == NULL || data_size == 0) return 0;
    int w = 0, h = 0, fc = 0, lc = 0, ht = 0;
    int success = GetGifInfo(gif_bytes, data_size, &w, &h, &fc, &lc, &ht);
    if (!success) return 0;
    if (width) *width = w;
    if (height) *height = h;
    if (frame_count) *frame_count = fc;
    if (loop_count) *loop_count = lc;
    if (has_transparency) *has_transparency = ht;
    return 1;
}

WEBP4J_FFM_EXPORT size_t webp4j_ffm_encode_gif(
    const uint8_t* gif_bytes, size_t data_size,
    float quality, int32_t lossless, int32_t compression_method,
    int32_t extract_first, int32_t loop_count, int32_t kmin, int32_t kmax,
    int32_t minimize_size, int32_t allow_mixed, int32_t multi_threaded,
    uint8_t** out_data) {
    if (gif_bytes == NULL || data_size == 0 || out_data == NULL) return 0;
    *out_data = NULL;

    GifConvertParams params;
    params.quality = quality;
    params.lossless = lossless ? 1 : 0;
    params.method = compression_method;
    params.extract_first = extract_first ? 1 : 0;
    params.loop_count = loop_count;
    params.kmin = kmin;
    params.kmax = kmax;
    params.minimize_size = minimize_size ? 1 : 0;
    params.allow_mixed = allow_mixed ? 1 : 0;
    params.multi_threaded = multi_threaded ? 1 : 0;

    WebPData webp;
    if (!ConvertGif(gif_bytes, data_size, &params, &webp)) {
        return 0;
    }
    *out_data = (uint8_t*)webp.bytes;
    return webp.size;
}
#endif
