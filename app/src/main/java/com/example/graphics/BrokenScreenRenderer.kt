package com.example.graphics

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import com.example.model.FlickerIntensity
import com.example.model.GlitchIntensity
import com.example.model.LinePalette
import com.example.model.PrankConfig
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin

class BrokenScreenRenderer {

    private val random = Random(42) // Seeded for stable baseline structure
    private val dynamicRandom = Random()

    // Paints
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val glassCrackLightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(225, 255, 255, 255)
        strokeWidth = 1.8f
    }
    private val glassCrackShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(190, 15, 20, 30)
        strokeWidth = 2.4f
    }
    private val inkBleedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(250, 10, 10, 15)
    }
    private val inkBleedEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.argb(160, 80, 0, 80)
    }
    private val scanlinePaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.argb(32, 0, 0, 0)
    }
    private val glitchBlockPaint = Paint().apply {
        style = Paint.Style.FILL
    }
    private val blackoutPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.argb(235, 8, 8, 12)
    }
    private val sparkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(255, 120, 240, 255)
        strokeWidth = 3f
    }

    // Cached geometric data structures
    private var cachedWidth = 0f
    private var cachedHeight = 0f
    private var cachedConfig: PrankConfig? = null

    // Lines representation
    private data class VerticalLine(
        val relativeX: Float,
        val width: Float,
        val colorInt: Int,
        val jitterAmplitude: Float,
        val isIntermittent: Boolean
    )
    private data class HorizontalLine(
        val relativeY: Float,
        val height: Float,
        val colorInt: Int
    )
    private val verticalLines = mutableListOf<VerticalLine>()
    private val horizontalLines = mutableListOf<HorizontalLine>()

    // Glass crack geometry
    private val glassCrackPathsLight = mutableListOf<Path>()
    private val glassCrackPathsShadow = mutableListOf<Path>()
    private val inkBleedPath = Path()

    // Interactive sparks
    data class SparkPoint(val x: Float, val y: Float, val timestamp: Long)
    private val activeSparks = mutableListOf<SparkPoint>()

    // Flicker state tracking
    private var lastFlickerToggle = 0L
    private var isBlackoutFrame = false
    private var isBrightBurst = false
    private var horizontalJitterOffset = 0f

    fun updateDimensions(width: Float, height: Float, config: PrankConfig) {
        if (width <= 0f || height <= 0f) return
        if (width == cachedWidth && height == cachedHeight && config == cachedConfig) return

        cachedWidth = width
        cachedHeight = height
        cachedConfig = config

        regenerateGeometry(width, height, config)
    }

    private fun regenerateGeometry(w: Float, h: Float, config: PrankConfig) {
        val r = Random(1337)
        verticalLines.clear()
        horizontalLines.clear()
        glassCrackPathsLight.clear()
        glassCrackPathsShadow.clear()
        inkBleedPath.reset()

        // 1. Generate Vertical Lines
        val paletteColors = getPaletteColorInts(config.linePalette)
        val count = config.verticalLineCount

        for (i in 0 until count) {
            val relX = if (i == 0) 0.28f else if (i == 1) 0.65f else r.nextFloat()
            val strokeW = when {
                i == 0 -> 4.5f // Primary thick line
                r.nextFloat() < 0.25f -> 6f + r.nextFloat() * 4f
                r.nextFloat() < 0.4f -> 1f + r.nextFloat() * 1.5f
                else -> 2f + r.nextFloat() * 2.5f
            }
            val color = paletteColors[r.nextInt(paletteColors.size)]
            verticalLines.add(
                VerticalLine(
                    relativeX = relX,
                    width = strokeW,
                    colorInt = color,
                    jitterAmplitude = 1.5f + r.nextFloat() * 3f,
                    isIntermittent = r.nextFloat() < 0.4f
                )
            )
        }

        // Horizontal lines / bands
        for (i in 0 until config.horizontalLineCount) {
            val relY = r.nextFloat()
            val strokeH = 1.5f + r.nextFloat() * 3.5f
            val color = paletteColors[r.nextInt(paletteColors.size)]
            horizontalLines.add(HorizontalLine(relY, strokeH, color))
        }

        // 2. Generate Glass Cracks if enabled
        if (config.showGlassCracks) {
            val impactX = w * config.crackImpactX
            val impactY = h * config.crackImpactY

            val branchCount = 12
            for (b in 0 until branchCount) {
                val baseAngle = (b.toDouble() / branchCount) * (2 * Math.PI) + (r.nextFloat() * 0.2 - 0.1)
                var currX = impactX
                var currY = impactY

                val lightPath = Path()
                val shadowPath = Path()
                lightPath.moveTo(currX, currY)
                shadowPath.moveTo(currX + 1.2f, currY + 1.2f)

                val segments = 8 + r.nextInt(10)
                var dist = 0f
                val maxDist = Math.max(w, h) * (0.35f + r.nextFloat() * 0.55f)

                for (s in 0 until segments) {
                    val step = (maxDist / segments) * (0.7f + r.nextFloat() * 0.6f)
                    dist += step
                    val angleJitter = (r.nextFloat() - 0.5f) * 0.5f
                    val angle = baseAngle + angleJitter

                    currX += (cos(angle) * step).toFloat()
                    currY += (sin(angle) * step).toFloat()

                    lightPath.lineTo(currX, currY)
                    shadowPath.lineTo(currX + 1.2f, currY + 1.2f)

                    // Sub-fracture fork
                    if (s > 2 && r.nextFloat() < 0.35f) {
                        val forkPath = Path()
                        val forkShadow = Path()
                        forkPath.moveTo(currX, currY)
                        forkShadow.moveTo(currX + 1.2f, currY + 1.2f)

                        val forkAngle = angle + (if (r.nextBoolean()) 0.6f else -0.6f) + (r.nextFloat() * 0.3f - 0.15f)
                        var fX = currX
                        var fY = currY
                        val forkSteps = 3 + r.nextInt(4)
                        val forkStepDist = step * 0.65f
                        for (fs in 0 until forkSteps) {
                            fX += (cos(forkAngle) * forkStepDist).toFloat()
                            fY += (sin(forkAngle) * forkStepDist).toFloat()
                            forkPath.lineTo(fX, fY)
                            forkShadow.lineTo(fX + 1.2f, fY + 1.2f)
                        }
                        glassCrackPathsLight.add(forkPath)
                        glassCrackPathsShadow.add(forkShadow)
                    }
                }
                glassCrackPathsLight.add(lightPath)
                glassCrackPathsShadow.add(shadowPath)
            }

            // Concentric spiderweb connecting arcs
            val arcRings = 5
            for (ring in 1..arcRings) {
                val radius = (Math.min(w, h) * 0.08f * ring)
                var angle = 0.0
                val arcLight = Path()
                val arcShadow = Path()
                var first = true

                while (angle < 2 * Math.PI) {
                    val rDist = radius * (0.85f + r.nextFloat() * 0.3f)
                    val aX = impactX + (cos(angle) * rDist).toFloat()
                    val aY = impactY + (sin(angle) * rDist).toFloat()

                    if (first) {
                        arcLight.moveTo(aX, aY)
                        arcShadow.moveTo(aX + 1.2f, aY + 1.2f)
                        first = false
                    } else {
                        arcLight.lineTo(aX, aY)
                        arcShadow.lineTo(aX + 1.2f, aY + 1.2f)
                    }
                    angle += 0.35 + r.nextFloat() * 0.2
                }
                glassCrackPathsLight.add(arcLight)
                glassCrackPathsShadow.add(arcShadow)
            }
        }

        // 3. Generate Liquid Crystal Bleed Blob
        if (config.showLcdInkBleed) {
            val impactX = w * config.crackImpactX
            val impactY = h * config.crackImpactY
            val lobeCount = 9
            var first = true

            for (l in 0 until lobeCount) {
                val angle = (l.toDouble() / lobeCount) * (2 * Math.PI)
                val dist = (Math.min(w, h) * 0.12f) * (0.5f + r.nextFloat() * 0.9f)
                val bx = impactX + (cos(angle) * dist).toFloat()
                val by = impactY + (sin(angle) * dist).toFloat()

                if (first) {
                    inkBleedPath.moveTo(bx, by)
                    first = false
                } else {
                    inkBleedPath.lineTo(bx, by)
                }
            }
            inkBleedPath.close()
        }
    }

    private fun getPaletteColorInts(palette: LinePalette): List<Int> {
        return palette.previewColors.map { it.toInt() }
    }

    fun addSpark(x: Float, y: Float) {
        activeSparks.add(SparkPoint(x, y, System.currentTimeMillis()))
    }

    /**
     * Renders the broken screen layer directly onto the canvas.
     */
    fun draw(canvas: Canvas, w: Float, h: Float, config: PrankConfig) {
        if (w <= 0f || h <= 0f) return
        updateDimensions(w, h, config)

        val now = System.currentTimeMillis()

        // Calculate flicker frame state
        val flicker = config.flickerIntensity
        if (flicker != FlickerIntensity.OFF) {
            if (now - lastFlickerToggle > flicker.rateMs) {
                lastFlickerToggle = now
                isBlackoutFrame = dynamicRandom.nextFloat() < (flicker.probability * 0.35f)
                isBrightBurst = dynamicRandom.nextFloat() < (flicker.probability * 0.45f)
                horizontalJitterOffset = if (dynamicRandom.nextFloat() < flicker.probability) {
                    (dynamicRandom.nextFloat() * 8f - 4f)
                } else {
                    0f
                }
            }
        } else {
            isBlackoutFrame = false
            isBrightBurst = false
            horizontalJitterOffset = 0f
        }

        // If blackout flicker frame, draw semi-opaque blackout and flash lines
        if (isBlackoutFrame) {
            blackoutPaint.alpha = 220
            canvas.drawRect(0f, 0f, w, h, blackoutPaint)
        }

        // 1. Draw Glitch Displacement Slices
        if (config.glitchIntensity != GlitchIntensity.OFF) {
            val sliceCount = config.glitchIntensity.sliceCount
            for (i in 0 until sliceCount) {
                if (dynamicRandom.nextFloat() < 0.4f) {
                    val sliceY = dynamicRandom.nextFloat() * h
                    val sliceH = 4f + dynamicRandom.nextFloat() * 32f
                    val offsetX = (dynamicRandom.nextFloat() * 60f - 30f)

                    // Draw colored RGB tear strip
                    glitchBlockPaint.color = when (dynamicRandom.nextInt(4)) {
                        0 -> Color.argb(120, 255, 0, 80)   // Magenta ghost
                        1 -> Color.argb(120, 0, 230, 255)  // Cyan ghost
                        2 -> Color.argb(140, 0, 255, 100)  // Green streak
                        else -> Color.argb(170, 20, 20, 25) // Dead strip
                    }
                    canvas.drawRect(offsetX, sliceY, w + offsetX, sliceY + sliceH, glitchBlockPaint)

                    // Random white static confetti inside slice
                    if (dynamicRandom.nextFloat() < 0.3f) {
                        glitchBlockPaint.color = Color.argb(200, 255, 255, 255)
                        val noiseX = dynamicRandom.nextFloat() * w
                        val noiseW = 20f + dynamicRandom.nextFloat() * 80f
                        canvas.drawRect(noiseX, sliceY, noiseX + noiseW, sliceY + sliceH * 0.6f, glitchBlockPaint)
                    }
                }
            }
        }

        // 2. Draw CRT Scanlines
        if (config.showScanlines) {
            val step = 6f
            var y = 0f
            while (y < h) {
                canvas.drawRect(0f, y, w, y + 2.2f, scanlinePaint)
                y += step
            }
        }

        // 3. Draw Vertical Broken Lines
        for (line in verticalLines) {
            // Check intermittent visibility
            if (line.isIntermittent && dynamicRandom.nextFloat() < 0.2f) {
                continue
            }

            val jitter = if (horizontalJitterOffset != 0f) {
                horizontalJitterOffset + (dynamicRandom.nextFloat() * line.jitterAmplitude - line.jitterAmplitude / 2f)
            } else {
                0f
            }

            val x = (line.relativeX * w) + jitter

            // Outer Neon Glow
            glowPaint.color = line.colorInt
            glowPaint.alpha = if (isBrightBurst) 180 else 90
            glowPaint.strokeWidth = line.width * 3.8f
            canvas.drawLine(x, 0f, x, h, glowPaint)

            // Inner Sharp Bright Line
            linePaint.color = line.colorInt
            linePaint.alpha = if (isBrightBurst) 255 else 230
            linePaint.strokeWidth = line.width
            canvas.drawLine(x, 0f, x, h, linePaint)

            // Hot Center Core (Pure White Core) for realistic OLED emission
            linePaint.color = Color.WHITE
            linePaint.alpha = 240
            linePaint.strokeWidth = Math.max(1f, line.width * 0.35f)
            canvas.drawLine(x, 0f, x, h, linePaint)
        }

        // 4. Draw Horizontal Broken Lines
        for (hline in horizontalLines) {
            if (dynamicRandom.nextFloat() < 0.15f) continue
            val y = hline.relativeY * h
            linePaint.color = hline.colorInt
            linePaint.alpha = 210
            linePaint.strokeWidth = hline.height
            canvas.drawLine(0f, y, w, y, linePaint)
        }

        // 5. Draw Liquid Crystal Ink Bleed
        if (config.showLcdInkBleed) {
            canvas.drawPath(inkBleedPath, inkBleedPaint)
            canvas.drawPath(inkBleedPath, inkBleedEdgePaint)

            // Ink droplet splatters
            val impactX = w * config.crackImpactX
            val impactY = h * config.crackImpactY
            inkBleedPaint.alpha = 240
            for (i in 0..4) {
                val dropDist = (Math.min(w, h) * 0.16f) + (i * 14f)
                val dropAngle = (i * 1.3)
                val dx = impactX + (cos(dropAngle) * dropDist).toFloat()
                val dy = impactY + (sin(dropAngle) * dropDist).toFloat()
                val radius = 5f + (5 - i) * 2f
                canvas.drawCircle(dx, dy, radius, inkBleedPaint)
            }
        }

        // 6. Draw Glass Cracks (Radial & Concentric spiderweb)
        if (config.showGlassCracks) {
            // First pass: shadow / refraction drop line
            for (path in glassCrackPathsShadow) {
                canvas.drawPath(path, glassCrackShadowPaint)
            }
            // Second pass: bright white sharp hairline fracture
            for (path in glassCrackPathsLight) {
                canvas.drawPath(path, glassCrackLightPaint)
            }

            // Impact center crushing white glow
            val impactX = w * config.crackImpactX
            val impactY = h * config.crackImpactY
            val crushPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(210, 255, 255, 255)
                style = Paint.Style.FILL
            }
            canvas.drawCircle(impactX, impactY, 7f, crushPaint)
            crushPaint.color = Color.argb(120, 255, 255, 255)
            canvas.drawCircle(impactX, impactY, 15f, crushPaint)
        }

        // 7. Draw Active Touch Sparks
        val sparkIterator = activeSparks.iterator()
        while (sparkIterator.hasNext()) {
            val spark = sparkIterator.next()
            val age = now - spark.timestamp
            if (age > 280L) {
                sparkIterator.remove()
            } else {
                val progress = age / 280f
                val alpha = ((1f - progress) * 255).toInt().coerceIn(0, 255)
                sparkPaint.alpha = alpha

                // Draw electric lightning branches
                for (b in 0..5) {
                    val angle = (b * 60.0 + (dynamicRandom.nextFloat() * 30 - 15)) * Math.PI / 180.0
                    val len = (40f + dynamicRandom.nextFloat() * 50f) * (1f - progress * 0.5f)
                    val midLen = len * 0.5f
                    val midAngle = angle + (dynamicRandom.nextFloat() * 0.4 - 0.2)

                    val midX = spark.x + (cos(midAngle) * midLen).toFloat()
                    val midY = spark.y + (sin(midAngle) * midLen).toFloat()
                    val endX = spark.x + (cos(angle) * len).toFloat()
                    val endY = spark.y + (sin(angle) * len).toFloat()

                    canvas.drawLine(spark.x, spark.y, midX, midY, sparkPaint)
                    canvas.drawLine(midX, midY, endX, endY, sparkPaint)
                }

                sparkPaint.color = Color.WHITE
                canvas.drawCircle(spark.x, spark.y, 8f * (1f - progress), sparkPaint)
                sparkPaint.color = Color.argb(255, 120, 240, 255)
            }
        }
    }
}
