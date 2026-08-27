import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    namespace = "com.pockettavern.app"
    compileSdk = 36  // required by Llamatik (llama.cpp GGUF); targetSdk stays 35
    // AGP 8.7.3's default ndkVersion (27.0.12077973) conflicts with the NDK actually installed
    // and used for the MNN native build (r29, /opt/android-ndk) -- pin explicitly to match.
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "com.pockettavern.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 28
        versionName = "2.3.5"

        // Stories (native ensemble) = private/dev feature for now. Visible in debug builds,
        // hidden in the public release (overridden false below). Keeps PocketTavern simple.
        buildConfigField("boolean", "STORIES_ENABLED", "true")

        // On-device SDXL (MNN). Usable only if you have an MNN-converted SDXL model set to
        // point the downloader at, and none are published yet -- so it stays a debug/dev
        // feature and is hidden from the public release (overridden false below), which also
        // strips libMNN.so from the release APK. Flip both when real models exist.
        buildConfigField("boolean", "MNN_SDXL_ENABLED", "true")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Phones are arm64; dropping x86_64 ~halves the APK (large on-device native libs).
        ndk { abiFilters += "arm64-v8a" }

        externalNativeBuild {
            cmake {
                // c++_shared, not c++_static: PocketTavern already ships other native libs as
                // prebuilt AARs (LiteRT-LM, Llamatik) -- statically linking libc++ into more
                // than one .so in the same process is an NDK-documented hazard (duplicate
                // global state / ODR issues). See app/src/main/cpp/CMakeLists.txt's comment.
                arguments += "-DANDROID_STL=c++_shared"
                // AGP maps the Gradle "debug" build variant to CMAKE_BUILD_TYPE=Debug by default
                // (no -O optimization) unless told otherwise -- confirmed via
                // app/.cxx/Debug/*/arm64-v8a/CMakeCache.txt showing CMAKE_BUILD_TYPE=Debug, vs.
                // the validated-fast CLI reference build's CMAKE_BUILD_TYPE=Release. On-device
                // this was ~170s/UNet-step instead of the CLI's ~30-33s/step -- a ~5x gap in line
                // with an unoptimized debug build of compute-heavy tensor math, not just a
                // scheduling/affinity difference (see Power_High comment in
                // stable_diffusion_xl.cpp, which addressed a real but much smaller effect).
                // Force Release for the native side regardless of the app's own debug/release
                // variant -- there's no reason to ship or test an unoptimized libMNN.so.
                arguments += "-DCMAKE_BUILD_TYPE=Release"
                // Without this, Ninja builds every default target CMake defines -- MNN_BUILD_LLM
                // pulls in a pile of demo/tool executables (llm_demo, llm_bench, embedding_demo,
                // quantize_llm, etc.) regardless of MNN_BUILD_TOOLS/MNN_BUILD_DEMO, none of which
                // the app needs. pockettavern_diffusion links against MNN, which pulls it in
                // transitively -- nothing else needs to be built.
                targets += "pockettavern_diffusion"
            }
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("pockettavern.keystore")
            storePassword = localProps.getProperty("KEYSTORE_PASSWORD", "")
            keyAlias = localProps.getProperty("KEY_ALIAS", "pockettavern")
            keyPassword = localProps.getProperty("KEY_PASSWORD", "")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("boolean", "STORIES_ENABLED", "false")  // hide Stories in the public release
            buildConfigField("boolean", "MNN_SDXL_ENABLED", "false")  // no published SDXL models yet
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        // litertlm-android ships newer Kotlin metadata (2.3.x) than our compiler (2.1.x).
        // Safe to consume — it's a JNI-wrapper lib with simple public types.
        freeCompilerArgs += "-Xskip-metadata-version-check"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // MNN_SDXL_ENABLED=false only hides the UI; the .so files would still be packaged and
    // are pure dead weight in a build that cannot reach them. Drop them from release.
    androidComponents {
        onVariants(selector().withBuildType("release")) { variant ->
            variant.packaging.jniLibs.excludes.add("**/libMNN.so")
            variant.packaging.jniLibs.excludes.add("**/libpockettavern_diffusion.so")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            // Pinned rather than left to AGP's default so the SDK-managed cmake/ninja package
            // Gradle downloads is predictable. 3.22.1 (a common AGP default) failed to configure
            // MNN with MNN_SEP_BUILD=OFF: CMake rejected add_custom_command(TARGET ... PRE_BUILD)
            // calls on the "MNNOpenCV"/"llm" OBJECT-library targets (tools/cv/CMakeLists.txt,
            // transformers/llm/engine/CMakeLists.txt) -- the same config built fine with the
            // system cmake (4.4.2) used for the desktop/Android CLI builds validated earlier
            // this session, so this looks like version-specific enforcement of that restriction.
            // Using the newest SDK-managed version to get closer to what actually worked.
            version = "4.1.2"
        }
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material3.adaptive)
    implementation(libs.androidx.material3.adaptive.layout)
    implementation(libs.androidx.material3.adaptive.navigation)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Network
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.kotlinx.serialization)

    // On-device inference (LiteRT-LM, Apache-2.0). minSdk 23, arm64-v8a/x86_64.
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.13.1")
    // On-device GGUF inference via llama.cpp (Llamatik, MIT). Unlocks the GGUF ecosystem.
    implementation("com.llamatik:library:1.8.0")

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Image Loading
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)

    // Chrome Custom Tabs for OAuth
    implementation("androidx.browser:browser:1.8.0")

    // Room database (character/chat index)
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // EXIF orientation for uploaded photos
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // SAF DocumentFile support (for folder import)
    implementation("androidx.documentfile:documentfile:1.0.1")

    // Encrypted storage for API keys and tokens
    implementation("androidx.security:security-crypto:1.0.0")
}
