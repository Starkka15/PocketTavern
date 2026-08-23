cmake_minimum_required(VERSION 3.22)
project(pockettavern_diffusion)

# Reproduces the flags already validated end-to-end (SD1.5 + SDXL, correct output) on this exact
# target device -- see ~/code/MNN/project/android/build/CMakeCache.txt for the reference config
# these were checked against. Deltas from that reference, and why:
#   - MNN_SEP_BUILD=OFF: NOT the top-level default (MNN's own CMakeLists.txt defaults it ON) --
#     must be set explicitly, or the diffusion engine builds as its own separate libdiffusion.so
#     instead of folding into libMNN.so as one library, which is what was actually tested.
#   - MNN_BUILD_TEST/TOOLS/BENCHMARK/CONVERTER/DEMO=OFF (session build had TEST/TOOLS/BENCHMARK
#     ON): those only add extra standalone executables (unit tests, MNNConvert, benchmark
#     tools) irrelevant to an app and don't affect the MNN/diffusion library content itself --
#     pure build-time/scope reduction, no behavioral risk.
#   - MNN_OPENCL=OFF (session build had it ON): confirmed-dead backend for this project (see
#     mnn_sdxl_android_pipeline memory) and never invoked at runtime (backend type is always
#     CPU). Turning it off saves real .so size; if anything unexpected breaks, flip back to ON
#     to match the exact session-validated config as a first debugging step.
#   - ANDROID_STL is set to c++_shared below in the app's build.gradle.kts /
#     externalNativeBuild block (not here), NOT c++_static like the session's standalone CLI
#     build -- PocketTavern already ships other native libs as prebuilt AARs (LiteRT-LM,
#     Llamatik), and statically linking libc++ into more than one .so in the same process is an
#     NDK-documented hazard (duplicate global state / ODR issues).
set(MNN_BUILD_LLM ON CACHE BOOL "" FORCE)
set(MNN_BUILD_DIFFUSION ON CACHE BOOL "" FORCE)
set(MNN_LOW_MEMORY ON CACHE BOOL "" FORCE)
set(MNN_ARM82 ON CACHE BOOL "" FORCE)
set(MNN_SUPPORT_TRANSFORMER_FUSE ON CACHE BOOL "" FORCE)
set(MNN_SEP_BUILD OFF CACHE BOOL "" FORCE)
# MNN_BUILD_DIFFUSION auto-forces MNN_BUILD_OPENCV=ON (see MNN/CMakeLists.txt's own
# IF(MNN_BUILD_DIFFUSION) block) but NOT MNN_IMGCODECS -- the diffusion OBJECT library's compiled
# code only actually gets linked into the MNN target when MNN_BUILD_DIFFUSION AND
# MNN_BUILD_OPENCV AND MNN_IMGCODECS are all true (MNN/CMakeLists.txt line ~900); without this,
# the build compiles cleanly but fails to *link*, with createDiffusion()/runXL() as undefined
# symbols. Matches the -DMNN_IMGCODECS=ON in MNN's own docs/transformers/diffusion.md reference
# build command.
set(MNN_IMGCODECS ON CACHE BOOL "" FORCE)
set(MNN_OPENCL OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_TEST OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_BENCHMARK OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_CONVERTER OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_DEMO OFF CACHE BOOL "" FORCE)
# Without this, MNN's own tools/cv and transformers/llm/engine CMakeLists.txt files add a
# POST_BUILD add_custom_command() (to copy headers to a location relevant only to MNN's own
# standalone build/test scripts) that CMake rejects outright on an OBJECT library (which
# MNNOpenCV/llm both are here, under MNN_SEP_BUILD=OFF) -- confirmed by reading both files
# directly. This flag (option(MNN_BUILD_FOR_ANDROID_COMMAND "Build from command" OFF) in MNN's
# own top-level CMakeLists.txt, not something we added) is ON=true in the reference Android CLI
# build validated earlier this session, and routes those same targets through a plain
# INSTALL(...) call instead when set, avoiding the restriction entirely.
set(MNN_BUILD_FOR_ANDROID_COMMAND ON CACHE BOOL "" FORCE)

add_subdirectory(MNN)

add_library(pockettavern_diffusion SHARED
    jni_diffusion.cpp
)

target_include_directories(pockettavern_diffusion PRIVATE
    MNN/include
    MNN/transformers/diffusion/engine/include
)

# static_cast (not dynamic_cast) is used deliberately in jni_diffusion.cpp instead of RTTI --
# MNN's own sources are built with -fno-rtti (see MNN/AGENTS.md's coding style notes), and
# typeinfo compatibility across translation units built with different -frtti settings isn't
# something to rely on. static_cast is safe here because this JNI layer is the one place that
# calls Diffusion::createDiffusion() and always knows the real dynamic type it asked for.
target_link_libraries(pockettavern_diffusion
    MNN
    log
)
