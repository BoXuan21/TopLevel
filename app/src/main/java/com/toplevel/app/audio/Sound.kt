package com.toplevel.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import com.toplevel.app.data.Popup
import com.toplevel.app.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * All app audio, generated in code (no sound files):
 * "System" sound effects and an original dark ambient theme. The user can swap the theme for their own track.
 */
object Sound {
    private const val RATE = 22050
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    // ───────────────────────── Synthesis helpers ─────────────────────────

    private const val TABLE = 4096
    private val sine = FloatArray(TABLE) { sin(2 * PI * it / TABLE).toFloat() }
    private fun osc(phase: Double): Float = sine[((phase - kotlin.math.floor(phase)) * TABLE).toInt() and (TABLE - 1)]
    private fun hz(midi: Int) = 440.0 * 2.0.pow((midi - 69) / 12.0)

    /** Adds a bell-like note (sine + inharmonic partial, exponential decay) into [buf], wrapping at the end if [wrap]. */
    private fun bell(buf: FloatArray, startSec: Double, midi: Int, durSec: Double, amp: Float, wrap: Boolean = false) {
        val f = hz(midi)
        val s0 = (startSec * RATE).toInt()
        val n = (durSec * RATE).toInt()
        for (i in 0 until n) {
            val t = i.toDouble() / RATE
            val env = (if (i < 40) i / 40f else 1f) * exp(-t * 4.2 / durSec).toFloat()
            val v = (osc(f * t) + 0.35f * osc(f * 2.76 * t) * exp(-t * 9).toFloat() + 0.2f * osc(f * 2 * t)) * env * amp
            var idx = s0 + i
            if (idx >= buf.size) { if (wrap) idx %= buf.size else break }
            buf[idx] += v
        }
    }

    /** Soft pad chord: detuned additive saws with slow attack/release. */
    private fun pad(buf: FloatArray, startSec: Double, notes: List<Int>, durSec: Double, amp: Float) {
        val s0 = (startSec * RATE).toInt()
        val atk = 0.9; val rel = 1.2
        val n = ((durSec + rel) * RATE).toInt()
        for (i in 0 until n) {
            val t = i.toDouble() / RATE
            val env = when {
                t < atk -> (t / atk)
                t < durSec -> 1.0
                else -> exp(-(t - durSec) * 3.5)
            }.toFloat()
            var v = 0f
            for (m in notes) {
                val f = hz(m)
                for (d in doubleArrayOf(-0.0025, 0.0025)) {
                    for (h in 1..5) v += osc(f * (1 + d) * h * t) / h.toFloat().pow(1.6f)
                }
            }
            buf[(s0 + i) % buf.size] += v * env * amp
        }
    }

    private fun noiseSwell(buf: FloatArray, startSec: Double, durSec: Double, amp: Float) {
        val rnd = Random(7); var lp = 0f
        val s0 = (startSec * RATE).toInt(); val n = (durSec * RATE).toInt()
        for (i in 0 until n) {
            val p = i.toFloat() / n
            lp += (rnd.nextFloat() * 2 - 1 - lp) * (0.02f + 0.25f * p) // opening low-pass = "whoosh"
            val env = sin(PI * p).toFloat()
            if (s0 + i < buf.size) buf[s0 + i] += lp * env * amp
        }
    }

    private fun toPcm(buf: FloatArray, peak: Float = 0.85f): ShortArray {
        val m = buf.maxOf { kotlin.math.abs(it) }.coerceAtLeast(1e-6f)
        val g = peak / m
        return ShortArray(buf.size) { (buf[it] * g * Short.MAX_VALUE).roundToInt().coerceIn(-32767, 32767).toShort() }
    }

    // ───────────────────────── Sound effects ─────────────────────────

    enum class Fx { OPEN, QUEST, LEVEL_UP, WARNING, ACHIEVEMENT }

    private fun render(fx: Fx): ShortArray {
        val len = when (fx) { Fx.LEVEL_UP -> 2.4; Fx.ACHIEVEMENT -> 1.8; Fx.WARNING -> 1.2; Fx.QUEST -> 1.3; Fx.OPEN -> 1.0 }
        val b = FloatArray((len * RATE).toInt())
        when (fx) {
            Fx.OPEN -> {
                noiseSwell(b, 0.0, 0.22, 0.25f)
                bell(b, 0.10, 88, 0.8, 0.5f)        // E6
                bell(b, 0.19, 95, 0.8, 0.4f)        // B6
            }
            Fx.QUEST -> {
                listOf(84, 88, 91, 96).forEachIndexed { i, m -> bell(b, i * 0.07, m, 1.0, 0.45f) } // C E G C
                bell(b, 0.30, 100, 0.9, 0.25f)
            }
            Fx.ACHIEVEMENT -> {
                noiseSwell(b, 0.0, 0.35, 0.3f)
                listOf(81, 85, 88, 93, 97).forEachIndexed { i, m -> bell(b, 0.15 + i * 0.08, m, 1.3, 0.4f) } // A maj
                pad(b, 0.4, listOf(69, 73, 76), 0.8, 0.05f)
            }
            Fx.LEVEL_UP -> {
                noiseSwell(b, 0.0, 0.6, 0.35f)
                listOf(74, 77, 81, 86, 89, 93, 98).forEachIndexed { i, m -> bell(b, 0.35 + i * 0.075, m, 1.6, 0.38f) } // D minor up
                pad(b, 0.85, listOf(62, 69, 74, 78), 0.9, 0.05f) // resolves to D major: "you got stronger"
                bell(b, 0.9, 102, 1.4, 0.2f)
            }
            Fx.WARNING -> {
                for (k in 0..1) {
                    val s0 = (k * 0.42 * RATE).toInt()
                    for (i in 0 until (0.38 * RATE).toInt()) {
                        val t = i.toDouble() / RATE
                        val env = (exp(-t * 6) * (if (i < 200) i / 200.0 else 1.0)).toFloat()
                        val v = (osc(hz(45) * t) + osc(hz(46) * t) + 0.4f * osc(hz(57) * t)) * env * 0.4f
                        if (s0 + i < b.size) b[s0 + i] += v
                    }
                }
            }
        }
        return toPcm(b, 0.8f)
    }

    private val fxCache = mutableMapOf<Fx, ShortArray>()

    fun play(ctx: Context, fx: Fx) {
        if (!Prefs(ctx).sfxOn) return
        scope.launch {
            val pcm = synchronized(fxCache) { fxCache.getOrPut(fx) { render(fx) } }
            try {
                val t = AudioTrack.Builder().setAudioAttributes(attrs)
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.size * 2).build()
                t.write(pcm, 0, pcm.size)
                t.setVolume(0.9f)
                t.play()
                kotlinx.coroutines.delay(pcm.size * 1000L / RATE + 200)
                t.release()
            } catch (_: Exception) { }
        }
    }

    fun forPopup(kind: Popup.Kind): Fx = when (kind) {
        Popup.Kind.LEVEL_UP -> Fx.LEVEL_UP
        Popup.Kind.ACHIEVEMENT -> Fx.ACHIEVEMENT
        Popup.Kind.WARNING, Popup.Kind.BOSS -> Fx.WARNING
        Popup.Kind.QUEST, Popup.Kind.CLEAR -> Fx.QUEST
        else -> Fx.OPEN
    }

    // ───────────────────────── Background music ─────────────────────────

    /** Original dark theme: D minor, i–VI–iv–V, pad + drone + bell arpeggio + heartbeat. Loops seamlessly. */
    private fun renderTheme(): ShortArray {
        val beat = 60.0 / 66            // 66 bpm
        val bar = beat * 4
        val chords = listOf(            // root position + upper voices
            listOf(50, 57, 62, 65),     // Dm
            listOf(46, 53, 58, 62),     // Bb
            listOf(43, 50, 55, 58),     // Gm
            listOf(45, 52, 57, 61),     // A
        )
        val bars = chords.size * 2      // each chord 2 bars
        val total = bar * bars
        val b = FloatArray((total * RATE).toInt())
        chords.forEachIndexed { ci, c ->
            val start = ci * 2 * bar
            pad(b, start, c, 2 * bar - 0.2, 0.030f)
            // drone on the root, an octave down
            val f = hz(c[0] - 12)
            val s0 = (start * RATE).toInt(); val n = (2 * bar * RATE).toInt()
            for (i in 0 until n) {
                val t = i.toDouble() / RATE
                val env = (minOf(1.0, t / 0.6) * minOf(1.0, (2 * bar - t) / 0.6)).toFloat()
                val trem = 0.8f + 0.2f * osc(0.25 * t)
                b[(s0 + i) % b.size] += (osc(f * t) + 0.3f * osc(f * 2 * t)) * env * trem * 0.16f
            }
            // bell arpeggio in 8ths, second bar only every other chord for breathing room
            val arp = listOf(c[1] + 12, c[2] + 12, c[3] + 12, c[2] + 24, c[3] + 12, c[2] + 12)
            val steps = if (ci % 2 == 0) 16 else 10
            for (k in 0 until steps) {
                bell(b, start + k * beat / 2, arp[k % arp.size], 1.6, if (k % 4 == 0) 0.09f else 0.06f, wrap = true)
            }
            // heartbeat on beats 1 and 3 of every bar
            for (barI in 0..1) for (bt in listOf(0, 2)) {
                val s = ((start + barI * bar + bt * beat) * RATE).toInt()
                for (i in 0 until (0.35 * RATE).toInt()) {
                    val t = i.toDouble() / RATE
                    val freq = 55 + 50 * exp(-t * 18)
                    val v = osc(freq * t) * exp(-t * 9).toFloat() * 0.35f
                    b[(s + i) % b.size] += v
                }
            }
        }
        return toPcm(b, 0.7f)
    }

    @Volatile private var themePcm: ShortArray? = null
    private var themeTrack: AudioTrack? = null
    private var player: MediaPlayer? = null
    private var playingKey: String? = null

    /** Start/refresh background music from the settings. Call from onStart and after settings change. */
    fun startMusic(ctx: Context) {
        val p = Prefs(ctx)
        val key = "${p.musicMode}|${p.musicUri}"
        val vol = p.musicVolume
        if (key == playingKey) { setVolume(vol); resume(); return }
        stopMusic()
        playingKey = key
        when (p.musicMode) {
            "theme" -> scope.launch {
                val pcm = themePcm ?: renderTheme().also { themePcm = it }
                if (playingKey != key) return@launch
                try {
                    val t = AudioTrack.Builder().setAudioAttributes(attrs)
                        .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                        .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.size * 2).build()
                    t.write(pcm, 0, pcm.size)
                    t.setLoopPoints(0, pcm.size, -1)
                    t.setVolume(vol)
                    t.play()
                    synchronized(this@Sound) { themeTrack = t }
                } catch (_: Exception) { }
            }
            "custom" -> if (p.musicUri.isNotBlank()) try {
                player = MediaPlayer().apply {
                    setAudioAttributes(attrs)
                    setDataSource(ctx, Uri.parse(p.musicUri))
                    isLooping = true
                    setVolume(vol, vol)
                    setOnPreparedListener { it.start() }
                    prepareAsync()
                }
            } catch (_: Exception) { player = null }
        }
    }

    fun setVolume(v: Float) {
        synchronized(this) { themeTrack?.setVolume(v) }
        try { player?.setVolume(v, v) } catch (_: Exception) { }
    }

    /** App went to the background. */
    fun pause() {
        synchronized(this) { try { themeTrack?.pause() } catch (_: Exception) { } }
        try { if (player?.isPlaying == true) player?.pause() } catch (_: Exception) { }
    }

    private fun resume() {
        synchronized(this) { try { themeTrack?.play() } catch (_: Exception) { } }
        try { if (player != null && player?.isPlaying == false) player?.start() } catch (_: Exception) { }
    }

    fun stopMusic() {
        playingKey = null
        synchronized(this) { try { themeTrack?.stop(); themeTrack?.release() } catch (_: Exception) { }; themeTrack = null }
        try { player?.release() } catch (_: Exception) { }
        player = null
    }
}
