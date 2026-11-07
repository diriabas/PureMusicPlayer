package com.diriabas.puremusicplayer

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** Software EQ applied to decoded PCM inside Media3, independent of Android AudioEffect support. */
@androidx.media3.common.util.UnstableApi
class SoftwareEqualizerAudioProcessor : BaseAudioProcessor() {

    data class Settings(
        val enabled: Boolean = true,
        val levelsDb: List<Int> = List(10) { 0 },
        val bass: Int = 0,
        val virtualizer: Int = 0
    )

    private data class Coefficients(val b0: Double, val b1: Double, val b2: Double,
                                    val a1: Double, val a2: Double)
    private data class State(var z1: Double = 0.0, var z2: Double = 0.0)

    private val settings = AtomicReference(Settings())
    private val frequencies = doubleArrayOf(31.0, 62.0, 125.0, 250.0, 500.0,
        1000.0, 2000.0, 4000.0, 8000.0, 16000.0)
    private var appliedSettings: Settings? = null
    private var coefficients = emptyArray<Coefficients>()
    private var filterStates = emptyArray<Array<State>>()
    private var sampleRate = 44100
    private var channels = 2
    private var bytesPerSample = 2
    private var pcmFloat = false
    private var preGain = 1.0

    fun updateSettings(enabled: Boolean, levels: IntArray, bass: Int, virtualizer: Int) {
        settings.set(Settings(enabled, (0 until 10).map { levels.getOrElse(it) { 0 }.coerceIn(-12, 12) },
            bass.coerceIn(0, 100), virtualizer.coerceIn(0, 100)))
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        pcmFloat = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        bytesPerSample = if (pcmFloat) 4 else 2
        sampleRate = inputAudioFormat.sampleRate
        channels = inputAudioFormat.channelCount
        rebuildFilters(settings.get())
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val inputBytes = inputBuffer.remaining()
        if (inputBytes == 0) return
        val current = settings.get()
        if (current != appliedSettings) rebuildFilters(current)

        val output = replaceOutputBuffer(inputBytes).order(ByteOrder.nativeOrder())
        val source = inputBuffer.order(ByteOrder.nativeOrder())
        val frameBytes = channels * bytesPerSample
        val usableBytes = inputBytes - (inputBytes % frameBytes)
        val width = 1.0 + (if (current.enabled) current.virtualizer / 100.0 else 0.0) * 0.65

        val frame = DoubleArray(channels)
        var offset = 0
        while (offset < usableBytes) {
            for (channel in 0 until channels) {
                var sample = if (pcmFloat) {
                    source.getFloat().toDouble().coerceIn(-1.0, 1.0)
                } else {
                    source.getShort().toDouble() / 32768.0
                }
                if (current.enabled) {
                    // Reserve headroom for the combined EQ/bass response before filtering.
                    // This prevents boosted presets from driving PCM samples into clipping.
                    sample *= preGain
                    for (filter in coefficients.indices) {
                        val c = coefficients[filter]
                        val state = filterStates[channel][filter]
                        val y = c.b0 * sample + state.z1
                        state.z1 = c.b1 * sample - c.a1 * y + state.z2
                        state.z2 = c.b2 * sample - c.a2 * y
                        sample = y
                    }
                }
                frame[channel] = sample
            }
            if (current.enabled && channels >= 2 && current.virtualizer > 0) {
                val left = frame[0]
                val right = frame[1]
                val mid = (left + right) * 0.5
                val side = (left - right) * 0.5 * width
                frame[0] = mid + side
                frame[1] = mid - side
            }
            for (channel in 0 until channels) {
                // Soft limiter engages only near digital full scale. Unlike the old
                // preamp, it does not attenuate the entire signal when bass is raised.
                val limited = softLimit(frame[channel])
                val safe = limited.coerceIn(-1.0, 0.999969)
                if (pcmFloat) {
                    output.putFloat(safe.toFloat())
                } else {
                    val pcm = (safe * 32768.0).toInt()
                    output.putShort(pcm.toShort())
                }
            }
            offset += frameBytes
        }
        // Preserve any incomplete trailing bytes unchanged (normally none for PCM frames).
        while (source.hasRemaining()) output.put(source.get())
        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }

    private fun rebuildFilters(value: Settings) {
        val gains = if (value.enabled) value.levelsDb else List(10) { 0 }
        val built = ArrayList<Coefficients>(11)
        // Dedicated low-shelf control boosts the low-frequency range smoothly.
        built += lowShelf(140.0, 0.707, if (value.enabled) value.bass / 100.0 * 9.0 else 0.0)
        for (i in frequencies.indices) {
            val f = frequencies[i].coerceAtMost(sampleRate * 0.45)
            built += peaking(f, 1.0, gains[i].toDouble())
        }
        coefficients = built.toTypedArray()
        preGain = calculateHeadroom(coefficients, value.virtualizer)
        filterStates = Array(channels) { Array(coefficients.size) { State() } }
        appliedSettings = value
    }

    /**
     * Measures the cascaded filter's peak frequency-response gain and applies
     * matching pre-attenuation. This preserves the EQ curve while providing
     * digital headroom, instead of letting bass/EQ boosts clip the output.
     */
    private fun calculateHeadroom(filters: Array<Coefficients>, virtualizer: Int): Double {
        var peakDb = 0.0
        val maxFrequency = (sampleRate * 0.48).coerceAtLeast(20.0)
        val minFrequency = 20.0
        val points = 512
        for (i in 0 until points) {
            val fraction = i.toDouble() / (points - 1)
            val frequency = minFrequency * (maxFrequency / minFrequency).pow(fraction)
            val w = 2.0 * PI * frequency / sampleRate
            val cosW = cos(w)
            val sinW = sin(w)
            var totalMagnitude = 1.0
            for (c in filters) {
                val numReal = c.b0 + c.b1 * cosW + c.b2 * cos(2.0 * w)
                val numImag = -c.b1 * sinW - c.b2 * sin(2.0 * w)
                val denReal = 1.0 + c.a1 * cosW + c.a2 * cos(2.0 * w)
                val denImag = -c.a1 * sinW - c.a2 * sin(2.0 * w)
                val numerator = kotlin.math.sqrt(numReal * numReal + numImag * numImag)
                val denominator = kotlin.math.sqrt(denReal * denReal + denImag * denImag)
                    .coerceAtLeast(1e-12)
                totalMagnitude *= numerator / denominator
            }
            val gainDb = 20.0 * kotlin.math.log10(totalMagnitude.coerceAtLeast(1e-12))
            if (gainDb > peakDb) peakDb = gainDb
        }
        // Stereo widening can also raise peaks when the side signal is strong.
        val width = 1.0 + (virtualizer / 100.0) * 0.65
        val widthHeadroomDb = if (virtualizer > 0) 20.0 * kotlin.math.log10(width) else 0.0
        val requiredDb = (peakDb + widthHeadroomDb).coerceAtLeast(0.0)
        return 10.0.pow(-requiredDb / 20.0)
    }

    private fun softLimit(value: Double): Double {
        val magnitude = kotlin.math.abs(value)
        if (magnitude <= 0.92) return value
        val excess = magnitude - 0.92
        val limited = 0.92 + 0.08 * (excess / (excess + 0.08))
        return if (value < 0.0) -limited else limited
    }

    private fun lowShelf(frequency: Double, slope: Double, gainDb: Double): Coefficients {
        if (gainDb == 0.0) return Coefficients(1.0, 0.0, 0.0, 0.0, 0.0)
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * frequency.coerceAtMost(sampleRate * 0.40) / sampleRate
        val cosW = cos(w0)
        val sinW = sin(w0)
        val alpha = sinW / 2.0 * kotlin.math.sqrt(
            (a + 1.0 / a) * (1.0 / slope - 1.0) + 2.0
        )
        val beta = 2.0 * kotlin.math.sqrt(a) * alpha
        val a0 = (a + 1.0) + (a - 1.0) * cosW + beta
        return Coefficients(
            a * ((a + 1.0) - (a - 1.0) * cosW + beta) / a0,
            2.0 * a * ((a - 1.0) - (a + 1.0) * cosW) / a0,
            a * ((a + 1.0) - (a - 1.0) * cosW - beta) / a0,
            -2.0 * ((a - 1.0) + (a + 1.0) * cosW) / a0,
            ((a + 1.0) + (a - 1.0) * cosW - beta) / a0
        )
    }

    private fun peaking(frequency: Double, q: Double, gainDb: Double): Coefficients {
        if (gainDb == 0.0) return Coefficients(1.0, 0.0, 0.0, 0.0, 0.0)
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * frequency / sampleRate
        val alpha = sin(w0) / (2.0 * q)
        val cosW = cos(w0)
        val a0 = 1.0 + alpha / a
        return Coefficients(
            (1.0 + alpha * a) / a0,
            (-2.0 * cosW) / a0,
            (1.0 - alpha * a) / a0,
            (-2.0 * cosW) / a0,
            (1.0 - alpha / a) / a0
        )
    }
}
