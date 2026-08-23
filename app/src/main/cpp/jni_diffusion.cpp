#include <jni.h>
#include <android/log.h>

#include <functional>
#include <memory>
#include <string>

#include <MNN/MNNForwardType.h>
#include "diffusion/diffusion.hpp"
#include "diffusion/stable_diffusion_xl.hpp"

#define LOG_TAG "PocketTavernDiffusion"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace MNN::DIFFUSION;

namespace {

std::string jstringToStd(JNIEnv* env, jstring s) {
    if (s == nullptr) {
        return std::string();
    }
    const char* chars = env->GetStringUTFChars(s, nullptr);
    std::string result(chars);
    env->ReleaseStringUTFChars(s, chars);
    return result;
}

// Wraps a Kotlin (Int) -> Unit lambda (a plain kotlin.jvm.functions.Function1 object from the
// Kotlin side) so it can be handed to StableDiffusionXL::runXL()'s
// std::function<void(int)> progressCallback. Non-copyable on purpose: it owns two global refs,
// and std::function normally takes its callable by value/copy -- passing this into runXL() via
// std::ref() (see nativeGenerateXL below) instead of by value is what keeps that safe. Called
// synchronously on the same thread that entered nativeGenerateXL (runXL() has no internal
// worker thread -- it blocks until the whole generation is done), so this needs no
// AttachCurrentThread/DetachCurrentThread handling; the JNIEnv* handed to nativeGenerateXL stays
// valid for the whole call.
class KotlinProgressCallback {
public:
    KotlinProgressCallback(const KotlinProgressCallback&) = delete;
    KotlinProgressCallback& operator=(const KotlinProgressCallback&) = delete;

    KotlinProgressCallback(JNIEnv* env, jobject callback) : mEnv(env) {
        mCallback = env->NewGlobalRef(callback);
        jclass callbackClass = env->GetObjectClass(mCallback);
        mInvokeMethod = env->GetMethodID(callbackClass, "invoke", "(Ljava/lang/Object;)Ljava/lang/Object;");
        env->DeleteLocalRef(callbackClass);
        mIntegerClass = static_cast<jclass>(env->NewGlobalRef(env->FindClass("java/lang/Integer")));
        mIntegerCtor = env->GetMethodID(mIntegerClass, "<init>", "(I)V");
    }

    ~KotlinProgressCallback() {
        mEnv->DeleteGlobalRef(mCallback);
        mEnv->DeleteGlobalRef(mIntegerClass);
    }

    void operator()(int progress) {
        jobject boxed = mEnv->NewObject(mIntegerClass, mIntegerCtor, progress);
        jobject result = mEnv->CallObjectMethod(mCallback, mInvokeMethod, boxed);
        mEnv->DeleteLocalRef(boxed);
        if (result != nullptr) {
            mEnv->DeleteLocalRef(result);
        }
        // A plain progress-reporting lambda isn't expected to throw, but an uncaught pending
        // JNI exception here would corrupt the next JNI call runXL() makes internally (its own
        // Module::onForward calls between progress ticks) -- guard defensively.
        if (mEnv->ExceptionCheck()) {
            mEnv->ExceptionDescribe();
            mEnv->ExceptionClear();
        }
    }

private:
    JNIEnv* mEnv;
    jobject mCallback;
    jclass mIntegerClass;
    jmethodID mInvokeMethod;
    jmethodID mIntegerCtor;
};

}  // namespace

extern "C" {

// modelType matches MNN::DIFFUSION::DiffusionModelType (0 = SD1.5, 4 = SDXL, etc. -- see
// diffusion.hpp). Backend is always MNN_FORWARD_CPU: not exposed as a parameter since this
// build has MNN_OPENCL compiled out (confirmed dead path for this project, see
// mnn_sdxl_android_pipeline memory) and there is currently no other real choice.
JNIEXPORT jlong JNICALL
Java_com_pockettavern_app_data_local_inference_MnnDiffusionBridge_nativeCreate(
        JNIEnv* env, jobject /*thiz*/, jstring modelPath, jint modelType, jint memoryMode) {
    std::string path = jstringToStd(env, modelPath);
    auto* diffusion = Diffusion::createDiffusion(
            path, static_cast<DiffusionModelType>(modelType), MNN_FORWARD_CPU, memoryMode);
    if (diffusion == nullptr) {
        LOGE("createDiffusion returned null for modelType=%d path=%s", modelType, path.c_str());
        return 0;
    }
    return reinterpret_cast<jlong>(diffusion);
}

JNIEXPORT jboolean JNICALL
Java_com_pockettavern_app_data_local_inference_MnnDiffusionBridge_nativeLoad(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    if (handle == 0) {
        return JNI_FALSE;
    }
    auto* diffusion = reinterpret_cast<Diffusion*>(handle);
    return diffusion->load() ? JNI_TRUE : JNI_FALSE;
}

// SDXL-specific: calls StableDiffusionXL::runXL() directly, not the base class's simplified
// run() (which hardcodes cfgScale=5.0 and an empty negative prompt -- confirmed this session
// that pureTukanoNSFW ignores prompts almost entirely at that default; a real cfgScale here is
// the fix). Only valid when nativeCreate's modelType was STABLE_DIFFUSION_XL (4) -- the
// static_cast below is unchecked, matching this JNI layer being the one place that always knows
// the real dynamic type it asked createDiffusion() for (see CMakeLists.txt's comment on why
// static_cast, not dynamic_cast/RTTI, is used here).
JNIEXPORT jboolean JNICALL
Java_com_pockettavern_app_data_local_inference_MnnDiffusionBridge_nativeGenerateXL(
        JNIEnv* env, jobject /*thiz*/, jlong handle,
        jstring prompt, jstring negativePrompt, jstring outputPath,
        jint width, jint height, jint steps, jint seed, jfloat cfgScale,
        jobject progressCallback) {
    if (handle == 0) {
        return JNI_FALSE;
    }
    auto* sdxl = static_cast<StableDiffusionXL*>(reinterpret_cast<Diffusion*>(handle));

    std::string promptStd = jstringToStd(env, prompt);
    std::string negativePromptStd = jstringToStd(env, negativePrompt);
    std::string outputPathStd = jstringToStd(env, outputPath);

    KotlinProgressCallback callback(env, progressCallback);
    bool ok = sdxl->runXL(promptStd, negativePromptStd, outputPathStd, width, height, steps, seed,
                           cfgScale, std::ref(callback));
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_pockettavern_app_data_local_inference_MnnDiffusionBridge_nativeDestroy(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    if (handle == 0) {
        return;
    }
    // Diffusion's destructor is virtual -- deleting through the base pointer correctly runs
    // ~StableDiffusionXL() (or whichever concrete type), no downcast needed for this call.
    delete reinterpret_cast<Diffusion*>(handle);
}

}  // extern "C"
