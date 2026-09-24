package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import java.util.Random
import kotlin.concurrent.thread
import kotlin.math.sin

class GlitchSoundGenerator {
    private var audioTrack: AudioTrack? = null
    @Volatile
    private var isPlaying = false
    private var playbackThread: Thread? = null
    private val random = Random()

    fun start() {
        if (isPlaying) return
        isPlaying = true

        playbackThread = thread(name = "GlitchAudioWorker") {
            val sampleRate = 22050
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(sampleRate / 4)

            try {
                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack?.play()

                val buffer = ShortArray(1024)
                var phaseHum = 0.0
                val humFrequency = 60.0 // 60Hz power transformer hum
                val phaseIncrement = (2.0 * Math.PI * humFrequency) / sampleRate

                var sparkCooldown = 0

                while (isPlaying) {
                    val inSpark = sparkCooldown > 0
                    if (inSpark) {
                        sparkCooldown--
                    } else if (random.nextFloat() < 0.06f) {
                        // Trigger sudden electrical crackle spark
                        sparkCooldown = random.nextInt(3) + 1
                    }

                    for (i in buffer.indices) {
                        phaseHum += phaseIncrement
                        if (phaseHum > 2.0 * Math.PI) {
                            phaseHum -= 2.0 * Math.PI
                        }

                        // Background subtle 60Hz hum + harmonic
                        val hum = (sin(phaseHum) * 2000.0) + (sin(phaseHum * 2.0) * 800.0)

                        val sampleValue = if (inSpark) {
                            // Violent white/pink noise burst + sharp impulses
                            val whiteNoise = (random.nextDouble() * 2.0 - 1.0) * 16000.0
                            val impulse = if (random.nextFloat() < 0.15f) (random.nextInt(60000) - 30000).toDouble() else 0.0
                            (hum * 0.3 + whiteNoise * 0.7 + impulse).coerceIn(Short.MIN_VALUE.toDouble(), Short.MAX_VALUE.toDouble())
                        } else {
                            // Subtle background hiss and line buzz
                            val lowHiss = (random.nextDouble() * 2.0 - 1.0) * 1200.0
                            (hum + lowHiss).coerceIn(Short.MIN_VALUE.toDouble(), Short.MAX_VALUE.toDouble())
                        }

                        buffer[i] = sampleValue.toInt().toShort()
                    }

                    audioTrack?.write(buffer, 0, buffer.size)
                }
            } catch (e: Exception) {
                Log.e("GlitchSound", "Audio generation error", e)
            } finally {
                releaseAudio()
            }
        }
    }

    fun playSingleSpark() {
        if (!isPlaying) {
            thread {
                try {
                    val sampleRate = 22050
                    val count = sampleRate / 8 // ~125ms zap
                    val buffer = ShortArray(count)
                    val r = Random()
                    for (i in 0 until count) {
                        val decay = 1.0 - (i.toDouble() / count)
                        val noise = (r.nextDouble() * 2.0 - 1.0) * 28000.0 * decay
                        buffer[i] = noise.toInt().toShort()
                    }
                    val track = AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build()
                        )
                        .setBufferSizeInBytes(buffer.size * 2)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build()

                    track.write(buffer, 0, buffer.size)
                    track.play()
                    Thread.sleep(150)
                    track.stop()
                    track.release()
                } catch (_: Exception) {}
            }
        }
    }

    fun stop() {
        isPlaying = false
        playbackThread?.interrupt()
        playbackThread = null
    }

    private fun releaseAudio() {
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
    }
}
