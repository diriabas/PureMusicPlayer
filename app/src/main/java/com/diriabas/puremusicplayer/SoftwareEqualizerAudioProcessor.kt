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

    fun updateSettings(enabled: Boolean, levels: IntArray, bass: Int, virtualizer: Int) {
        settings.set(Settings(enabled, (0 until 10).map { levels.getOrElse(it) { 0 }.coerceIn(-12, 12) },
            bass.coerceIn(0, 100), virtualizer.coerceIn(0, 100)))
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) return AudioProcessor.AudioFormat.NOT_SET
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
        val frameBytes = channels * 2
        val usableBytes = inputBytes - (inputBytes % frameBytes)
        val width = 1.0 + (if (current.enabled) current.virtualizer / 100.0 else 0.0) * 0.65

        val frame = DoubleArray(channels)
        var offset = 0
        while (offset < usableBytes) {
            for (channel in 0 until channels) {
                var sample = source.getShort().toDouble() / 32768.0
                if (current.enabled) {
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
                val clipped = (frame[channel].coerceIn(-1.0, 0.999969) * 32768.0).toInt()
                output.putShort(clipped.toShort())
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
        // Bass control is a broad low-frequency bell, followed by the ten graphic EQ bands.
        built += peaking(85.0, 0.9, if (value.enabled) value.bass / 100.0 * 9.0 else 0.0)
        for (i in frequencies.indices) {
            val f = frequencies[i].coerceAtMost(sampleRate * 0.45)
            built += peaking(f, 1.0, gains[i].toDouble())
        }
        coefficients = built.toTypedArray()
        filterStates = Array(channels) { Array(coefficients.size) { State() } }
        appliedSettings = value
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
