package com.pockettavern.app.ui.audio

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.PlaybackParams
import android.os.Build
import com.pockettavern.app.data.local.inference.OnDevicePocketTtsModelManager
import com.pockettavern.app.util.DebugLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.util.Random
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnDevicePocketTtsEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelManager: OnDevicePocketTtsModelManager,
) {
    private val lock = Mutex()
    private var env: OrtEnvironment? = null

    private var encSession: OrtSession? = null
    private var txtSession: OrtSession? = null
    private var lmMainSession: OrtSession? = null
    private var lmFlowSession: OrtSession? = null
    private var decSession: OrtSession? = null

    private var tokenizer: PocketTtsTokenizer? = null
    private var loadedModelDir: String? = null

    // Cache of voice embeddings: voiceName -> float array of shape [1, N, 1024]
    private val voiceLatentsCache = mutableMapOf<String, Array<Array<FloatArray>>>()

    private var currentAudioTrack: AudioTrack? = null
    @Volatile
    private var isPlaying = false
    @Volatile
    private var stopRequested = false

    val isReady: Boolean
        get() = lmMainSession != null && tokenizer != null

    suspend fun ensureLoaded() = lock.withLock {
        if (isReady && loadedModelDir == modelManager.modelsDir.absolutePath) return@withLock
        if (!modelManager.isModelDownloaded()) {
            throw IllegalStateException("Pocket TTS models are not downloaded yet.")
        }

        withContext(Dispatchers.IO) {
            unloadInternal()

            DebugLogger.log("OnDevicePocketTtsEngine: Initializing ONNX Runtime environment...")
            val environment = OrtEnvironment.getEnvironment().also { env = it }
            val sessionOpts = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(4)
                setInterOpNumThreads(1)
            }

            val dir = modelManager.modelsDir
            DebugLogger.log("OnDevicePocketTtsEngine: Loading ONNX sessions from $dir...")
            encSession = environment.createSession(File(dir, "encoder.onnx").absolutePath, sessionOpts)
            txtSession = environment.createSession(File(dir, "text_conditioner.onnx").absolutePath, sessionOpts)
            lmMainSession = environment.createSession(File(dir, "lm_main.int8.onnx").absolutePath, sessionOpts)
            lmFlowSession = environment.createSession(File(dir, "lm_flow.int8.onnx").absolutePath, sessionOpts)
            decSession = environment.createSession(File(dir, "decoder.int8.onnx").absolutePath, sessionOpts)

            tokenizer = PocketTtsTokenizer.fromFiles(
                File(dir, "vocab.json"),
                File(dir, "token_scores.json")
            )
            loadedModelDir = dir.absolutePath
            DebugLogger.log("OnDevicePocketTtsEngine: Successfully loaded all models and tokenizer.")
        }
    }

    private fun unloadInternal() {
        try {
            encSession?.close()
            txtSession?.close()
            lmMainSession?.close()
            lmFlowSession?.close()
            decSession?.close()
            encSession = null
            txtSession = null
            lmMainSession = null
            lmFlowSession = null
            decSession = null
            tokenizer = null
            loadedModelDir = null
            voiceLatentsCache.clear()
        } catch (e: Exception) {
            DebugLogger.logError("OnDevicePocketTtsEngine", "Error unloading sessions", e)
        }
    }

    suspend fun unload() = lock.withLock {
        stop()
        unloadInternal()
    }

    fun stop() {
        stopRequested = true
        isPlaying = false
        try {
            currentAudioTrack?.apply {
                if (playState == AudioTrack.PLAYSTATE_PLAYING) {
                    pause()
                    flush()
                    stop()
                }
                release()
            }
            currentAudioTrack = null
        } catch (e: Exception) {
            DebugLogger.logError("OnDevicePocketTtsEngine", "Error stopping AudioTrack", e)
        }
    }

    fun isSpeaking(): Boolean = isPlaying

    suspend fun speak(text: String, voiceName: String = "cosette_calm", speed: Float = 1.0f) = withContext(Dispatchers.IO) {
        ensureLoaded()
        val environment = env ?: return@withContext
        val tok = tokenizer ?: return@withContext
        val lmMain = lmMainSession ?: return@withContext
        val lmFlow = lmFlowSession ?: return@withContext
        val dec = decSession ?: return@withContext

        stopRequested = false
        isPlaying = true

        try {
            // 1. Get or compute voice latents
            val voiceLatents = getOrComputeVoiceLatents(voiceName)

            // 2. Split text into sentences for synthesis
            val sentences = splitIntoSentences(text)
            if (sentences.isEmpty()) return@withContext

            val sampleRate = 24000
            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBufferSize * 4, 65536))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && speed != 1.0f) {
                try {
                    audioTrack.playbackParams = PlaybackParams().apply { setSpeed(speed.coerceIn(0.5f, 2.0f)) }
                } catch (e: Exception) {
                    DebugLogger.log("OnDevicePocketTtsEngine: speed control unsupported: ${e.message}")
                }
            }

            currentAudioTrack = audioTrack
            audioTrack.play()

            var totalSamplesWritten = 0
            for (sentence in sentences) {
                if (stopRequested || !isActive) break

                val tokenIds = tok.encode(sentence)
                if (tokenIds.isEmpty()) continue

                // Synthesize sentence latents
                val latents = generateLatentsForTokens(environment, lmMain, lmFlow, voiceLatents, tokenIds)
                if (latents.isEmpty() || stopRequested) continue

                // Decode latents into 24kHz PCM audio samples
                val pcmShorts = decodeLatentsToPcm(environment, dec, latents)
                if (pcmShorts.isNotEmpty() && !stopRequested) {
                    val written = audioTrack.write(pcmShorts, 0, pcmShorts.size, AudioTrack.WRITE_BLOCKING)
                    if (written > 0) {
                        totalSamplesWritten += written
                    }
                }
            }

            // Wait for AudioTrack to finish playing all buffered samples
            if (!stopRequested && totalSamplesWritten > 0) {
                while (audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING &&
                    audioTrack.playbackHeadPosition < totalSamplesWritten &&
                    !stopRequested && isActive
                ) {
                    kotlinx.coroutines.delay(50)
                }
            }
        } catch (e: Exception) {
            DebugLogger.logError("OnDevicePocketTtsEngine", "Synthesis error", e)
            throw e
        } finally {
            isPlaying = false
        }
    }

    private fun getOrComputeVoiceLatents(voiceName: String): Array<Array<FloatArray>> {
        voiceLatentsCache[voiceName]?.let { return it }

        val environment = env ?: throw IllegalStateException("OrtEnvironment is null")
        val enc = encSession ?: throw IllegalStateException("Encoder session is null")

        val voiceFile = modelManager.getVoiceFile(voiceName)
        val audioSamples = if (voiceFile != null && voiceFile.exists()) {
            loadWavSamples(voiceFile)
        } else {
            // Default fallback: 2 seconds 24kHz neutral silence
            FloatArray(24000 * 2) { 0.0f }
        }

        val audioTensor = OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(audioSamples),
            longArrayOf(1, 1, audioSamples.size.toLong())
        )

        val result = enc.run(mapOf("audio" to audioTensor))
        @Suppress("UNCHECKED_CAST")
        val latents = (result[0].value as Array<Array<FloatArray>>)
        result.close()
        audioTensor.close()

        voiceLatentsCache[voiceName] = latents
        return latents
    }

    private fun generateLatentsForTokens(
        environment: OrtEnvironment,
        lmMain: OrtSession,
        lmFlow: OrtSession,
        voiceLatents: Array<Array<FloatArray>>,
        tokenIds: LongArray,
    ): List<FloatArray> {
        val txtSession = txtSession ?: return emptyList()

        // 1. Text embedding
        val tokenTensor = OnnxTensor.createTensor(
            environment,
            LongBuffer.wrap(tokenIds),
            longArrayOf(1, tokenIds.size.toLong())
        )
        val txtResult = txtSession.run(mapOf("token_ids" to tokenTensor))
        @Suppress("UNCHECKED_CAST")
        val textEmbeddings = txtResult[0].value as Array<Array<FloatArray>>
        txtResult.close()
        tokenTensor.close()

        // 2. Direct memory buffers for transformer layer KV caches and offsets
        val kvBuffers = Array(6) {
            ByteBuffer.allocateDirect(2 * 1 * 1000 * 16 * 64 * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
        }
        val stepOffsets = LongArray(6) { 0L }

        fun createLmStateInputs(): MutableMap<String, OnnxTensor> {
            val stateMap = mutableMapOf<String, OnnxTensor>()
            for (i in 0 until 6) {
                kvBuffers[i].position(0)
                stateMap["state_${i * 3}"] = OnnxTensor.createTensor(
                    environment,
                    kvBuffers[i].duplicate(),
                    longArrayOf(2, 1, 1000, 16, 64)
                )
                stateMap["state_${i * 3 + 1}"] = OnnxTensor.createTensor(
                    environment,
                    FloatBuffer.allocate(0),
                    longArrayOf(0)
                )
                stateMap["state_${i * 3 + 2}"] = OnnxTensor.createTensor(
                    environment,
                    LongBuffer.wrap(longArrayOf(stepOffsets[i])),
                    longArrayOf(1)
                )
            }
            return stateMap
        }

        fun updateBuffersFromOutputs(result: OrtSession.Result) {
            for (i in 0 until 6) {
                val outKv = result[2 + i * 3] as OnnxTensor
                kvBuffers[i].position(0)
                kvBuffers[i].put(outKv.floatBuffer)
                kvBuffers[i].position(0)

                val outOffset = result[2 + i * 3 + 2] as OnnxTensor
                stepOffsets[i] = outOffset.longBuffer.get(0)
            }
        }

        val emptySeqTensor = OnnxTensor.createTensor(
            environment,
            FloatBuffer.allocate(0),
            longArrayOf(1, 0, 32)
        )
        val emptyTextTensor = OnnxTensor.createTensor(
            environment,
            FloatBuffer.allocate(0),
            longArrayOf(1, 0, 1024)
        )

        // 3. Condition with voice embeddings
        val voiceTensor = OnnxTensor.createTensor(environment, voiceLatents)
        val voiceState = createLmStateInputs()
        val voiceInputs = mutableMapOf<String, OnnxTensor>().apply {
            put("sequence", emptySeqTensor)
            put("text_embeddings", voiceTensor)
            putAll(voiceState)
        }
        val voiceOut = lmMain.run(voiceInputs)
        updateBuffersFromOutputs(voiceOut)
        voiceOut.close()
        voiceTensor.close()
        for (t in voiceState.values) t.close()

        // 4. Condition with text embeddings
        val textTensor = OnnxTensor.createTensor(environment, textEmbeddings)
        val textState = createLmStateInputs()
        val textInputs = mutableMapOf<String, OnnxTensor>().apply {
            put("sequence", emptySeqTensor)
            put("text_embeddings", textTensor)
            putAll(textState)
        }
        val textOut = lmMain.run(textInputs)
        updateBuffersFromOutputs(textOut)
        textOut.close()
        textTensor.close()
        for (t in textState.values) t.close()

        // 5. Autoregressive frame generation with 4-step LSD Euler ODE integration
        val latents = mutableListOf<FloatArray>()
        val maxFrames = minOf(300, maxOf(30, (tokenIds.size * 3.5).toInt()))
        val random = Random()

        // Start token is Float.NaN (special start mask for Kyutai Pocket TTS)
        var currentLatent = FloatArray(32) { Float.NaN }
        var eosStep: Int? = null
        val framesAfterEos = 2

        val lsdSteps = 4
        val dt = 1.0f / lsdSteps
        val sTensors = Array(lsdSteps) { j ->
            OnnxTensor.createTensor(environment, FloatBuffer.wrap(floatArrayOf(j.toFloat() / lsdSteps)), longArrayOf(1, 1))
        }
        val tTensors = Array(lsdSteps) { j ->
            OnnxTensor.createTensor(environment, FloatBuffer.wrap(floatArrayOf((j + 1).toFloat() / lsdSteps)), longArrayOf(1, 1))
        }

        for (step in 0 until maxFrames) {
            if (stopRequested) break

            val seqTensor = OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(currentLatent),
                longArrayOf(1, 1, 32)
            )

            val stepState = createLmStateInputs()
            val stepInputs = mutableMapOf<String, OnnxTensor>().apply {
                put("sequence", seqTensor)
                put("text_embeddings", emptyTextTensor)
                putAll(stepState)
            }

            val stepOut = lmMain.run(stepInputs)
            val conditioningTensor = stepOut[0] as OnnxTensor
            @Suppress("UNCHECKED_CAST")
            val eosLogit = (stepOut[1].value as Array<FloatArray>)[0][0]

            updateBuffersFromOutputs(stepOut)

            // Flow prediction with 4-step Euler integration
            var x = FloatArray(32) { (random.nextGaussian() * Math.sqrt(0.7)).toFloat() }

            for (j in 0 until lsdSteps) {
                val xTensor = OnnxTensor.createTensor(environment, FloatBuffer.wrap(x), longArrayOf(1, 32))
                val flowInputs = mapOf(
                    "c" to conditioningTensor,
                    "s" to sTensors[j],
                    "t" to tTensors[j],
                    "x" to xTensor
                )
                val flowOut = lmFlow.run(flowInputs)
                @Suppress("UNCHECKED_CAST")
                val flowDir = (flowOut[0].value as Array<FloatArray>)[0]

                val nextX = FloatArray(32) { idx -> x[idx] + flowDir[idx] * dt }
                x = nextX

                flowOut.close()
                xTensor.close()
            }

            latents.add(x)
            currentLatent = x

            seqTensor.close()
            stepOut.close()
            for (t in stepState.values) t.close()

            if (eosLogit > -4.0f && eosStep == null) {
                eosStep = step
            }
            if (eosStep != null && step >= eosStep + framesAfterEos) {
                break
            }
        }

        for (t in sTensors) t.close()
        for (t in tTensors) t.close()
        emptySeqTensor.close()
        emptyTextTensor.close()

        return latents
    }

    private fun decodeLatentsToPcm(
        environment: OrtEnvironment,
        dec: OrtSession,
        latents: List<FloatArray>,
    ): ShortArray {
        if (latents.isEmpty()) return shortArrayOf()

        // Chunked decoding of 15 frames per batch with state accumulation
        val chunkSize = 15
        val allShorts = mutableListOf<Short>()

        // Initialize 56 Mimi decoder state buffers
        val decoderStates = mutableMapOf<String, OnnxTensor>()
        for (info in dec.inputInfo) {
            if (info.key.startsWith("state_")) {
                val tensorInfo = info.value.info as? TensorInfo
                if (tensorInfo != null) {
                    val shape = tensorInfo.shape
                    val nonNegShape = LongArray(shape.size) { idx -> if (shape[idx] < 0) 0L else shape[idx] }
                    var totalElements = 1
                    for (d in nonNegShape) {
                        totalElements *= d.toInt()
                    }
                    val tensor = when (tensorInfo.type) {
                        OnnxJavaType.BOOL -> {
                            OnnxTensor.createTensor(
                                environment,
                                ByteBuffer.wrap(ByteArray(totalElements) { 1 }),
                                nonNegShape,
                                OnnxJavaType.BOOL
                            )
                        }
                        OnnxJavaType.INT64 -> {
                            OnnxTensor.createTensor(
                                environment,
                                LongBuffer.allocate(totalElements),
                                nonNegShape
                            )
                        }
                        else -> {
                            OnnxTensor.createTensor(
                                environment,
                                FloatBuffer.allocate(totalElements),
                                nonNegShape
                            )
                        }
                    }
                    decoderStates[info.key] = tensor
                }
            }
        }

        for (index in 0 until latents.size step chunkSize) {
            if (stopRequested) break
            val count = minOf(chunkSize, latents.size - index)
            val flatChunk = FloatArray(count * 32)
            for (f in 0 until count) {
                System.arraycopy(latents[index + f], 0, flatChunk, f * 32, 32)
            }

            val chunkTensor = OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(flatChunk),
                longArrayOf(1, count.toLong(), 32)
            )

            val decInputs = mutableMapOf<String, OnnxTensor>().apply {
                put("latent", chunkTensor)
                putAll(decoderStates)
            }

            val decResult = dec.run(decInputs)
            val audioTensor = decResult[0] as OnnxTensor
            val floatBuf = audioTensor.floatBuffer
            while (floatBuf.hasRemaining()) {
                val sample = floatBuf.get()
                allShorts.add((sample.coerceIn(-1.0f, 1.0f) * 32767.0f).toInt().toShort())
            }

            // Update decoder state buffers for next chunk
            var stateIndex = 1
            for (info in dec.inputInfo) {
                if (info.key.startsWith("state_")) {
                    decoderStates[info.key]?.close()
                    val outTensor = decResult[stateIndex] as OnnxTensor
                    val tensorInfo = outTensor.info
                    val shape = tensorInfo.shape
                    val nonNegShape = LongArray(shape.size) { idx -> if (shape[idx] < 0) 0L else shape[idx] }
                    var totalElements = 1
                    for (d in nonNegShape) totalElements *= d.toInt()

                    val copyTensor = when (tensorInfo.type) {
                        OnnxJavaType.BOOL -> {
                            val direct = ByteBuffer.allocateDirect(totalElements)
                            if (totalElements > 0) {
                                val byteBuf = outTensor.byteBuffer
                                byteBuf.position(0)
                                direct.put(byteBuf)
                                direct.position(0)
                            }
                            OnnxTensor.createTensor(environment, direct, nonNegShape, OnnxJavaType.BOOL)
                        }
                        OnnxJavaType.INT64 -> {
                            val direct = ByteBuffer.allocateDirect(totalElements * 8)
                                .order(ByteOrder.nativeOrder())
                                .asLongBuffer()
                            if (totalElements > 0) {
                                val longBuf = outTensor.longBuffer
                                longBuf.position(0)
                                direct.put(longBuf)
                                direct.position(0)
                            }
                            OnnxTensor.createTensor(environment, direct, nonNegShape)
                        }
                        else -> {
                            val direct = ByteBuffer.allocateDirect(totalElements * 4)
                                .order(ByteOrder.nativeOrder())
                                .asFloatBuffer()
                            if (totalElements > 0) {
                                val floatBuf = outTensor.floatBuffer
                                floatBuf.position(0)
                                direct.put(floatBuf)
                                direct.position(0)
                            }
                            OnnxTensor.createTensor(environment, direct, nonNegShape)
                        }
                    }
                    decoderStates[info.key] = copyTensor
                    stateIndex++
                }
            }

            chunkTensor.close()
            decResult.close()
        }

        for (t in decoderStates.values) t.close()

        val resultShorts = ShortArray(allShorts.size)
        for (i in allShorts.indices) resultShorts[i] = allShorts[i]
        return resultShorts
    }

    private fun loadWavSamples(file: File): FloatArray {
        return try {
            val bytes = file.readBytes()
            val sampleRate = if (bytes.size > 28) {
                ByteBuffer.wrap(bytes, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int
            } else 24000
            val pcmOffset = if (bytes.size > 44 && String(bytes.sliceArray(0..3)) == "RIFF") 44 else 0
            val pcmBytes = bytes.sliceArray(pcmOffset until bytes.size)
            val numShorts = pcmBytes.size / 2
            val shortBuffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            val rawSamples = FloatArray(numShorts)
            for (i in 0 until numShorts) {
                rawSamples[i] = shortBuffer.get(i) / 32768.0f
            }

            // Resample to 24,000 Hz if necessary
            if (sampleRate != 24000 && sampleRate > 0) {
                val ratio = 24000.0 / sampleRate.toDouble()
                val targetLength = (rawSamples.size * ratio).toInt()
                val resampled = FloatArray(targetLength)
                for (i in 0 until targetLength) {
                    val srcIdx = (i / ratio).toInt().coerceIn(0, rawSamples.size - 1)
                    resampled[i] = rawSamples[srcIdx]
                }
                resampled
            } else {
                rawSamples
            }
        } catch (e: Exception) {
            DebugLogger.logError("OnDevicePocketTtsEngine", "Error loading WAV samples from $file", e)
            FloatArray(24000 * 2) { 0.0f }
        }
    }

    private fun splitIntoSentences(text: String): List<String> {
        val raw = text.trim()
        if (raw.isEmpty()) return emptyList()
        val sentences = raw.split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return if (sentences.isEmpty()) listOf(raw) else sentences
    }
}
