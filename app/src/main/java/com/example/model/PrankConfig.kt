package com.example.model

import java.io.Serializable

enum class BrokenPreset(val title: String, val subtitle: String, val badge: String) {
    OLED_GREEN_LINE(
        "OLED Green Line of Death",
        "Infamous vertical neon green line with flickering pink companions",
        "Most Realistic"
    ),
    CATASTROPHIC_LCD(
        "Shattered LCD & Ink Bleed",
        "Branching glass fractures, liquid ink leakage & dead pixel bands",
        "Classic Drop"
    ),
    CYBER_GLITCH(
        "Digital Glitch & CRT Tear",
        "Horizontal displacement tears, scanlines & chromatic split",
        "Cyberpunk"
    ),
    RIBBON_CABLE(
        "Loose Ribbon Cable",
        "Spontaneous blackouts, vertical sync rolling & sporadic strobes",
        "Intermittent"
    ),
    HARDWARE_MELTDOWN(
        "Hardware Meltdown",
        "Full combination of cracks, neon lines, tears, noise and flicker",
        "Maximum Chaos"
    ),
    CUSTOM(
        "Custom Disaster",
        "Manually fine-tune line count, flicker rate, cracks and sound",
        "Pro"
    )
}

enum class LinePalette(val displayName: String, val previewColors: List<Long>) {
    NEON_GREEN("OLED Neon Green", listOf(0xFF00FF66, 0xFF00E65A, 0xFF39FF14)),
    MAGENTA_CYAN("Magenta & Cyan", listOf(0xFFFF007F, 0xFF00E5FF, 0xFFE040FB)),
    RAINBOW_OLED("Multi-Color OLED", listOf(0xFF00FF66, 0xFFFF007F, 0xFF00E5FF, 0xFFFFFFFF, 0xFFFFD700)),
    BURNT_WHITE("Blown White & Amber", listOf(0xFFFFFFFF, 0xFFFFF59D, 0xFFFFD54F)),
    VAMPIRE_RED("Warning Red & Crimson", listOf(0xFFFF1744, 0xFFFF5252, 0xFFFF8A80))
}

enum class FlickerIntensity(val displayName: String, val rateMs: Long, val probability: Float) {
    OFF("Off", 0L, 0f),
    LOW("Subtle (Intermittent)", 400L, 0.15f),
    MEDIUM("Realistic (Loose Cable)", 180L, 0.35f),
    HIGH("Heavy Strobe", 80L, 0.60f),
    CHAOS("Furious Chaos", 40L, 0.85f)
}

enum class GlitchIntensity(val displayName: String, val sliceCount: Int) {
    OFF("Off", 0),
    LOW("Low (Few Tears)", 3),
    MEDIUM("Medium (Noticeable Glitch)", 7),
    HIGH("High (Heavy Displacement)", 14),
    EXTREME("Extreme (Screen Melt)", 24)
}

enum class TriggerMode(val displayName: String, val delaySeconds: Int) {
    IMMEDIATE("Immediate Launch", 0),
    TIMER_5S("5 Seconds Delay", 5),
    TIMER_10S("10 Seconds Delay", 10),
    TIMER_30S("30 Seconds Delay", 30),
    SHAKE("Motion / Pick-up Shake", 0)
}

enum class TouchMode(val displayName: String, val description: String) {
    PASS_THROUGH("Pass-Through (Ghost)", "Victim can still use all apps underneath seamlessly"),
    ELECTRIC_SPARKS("Electric Spark Taps", "Tapping the screen generates crackle sparks and shocks")
}

data class PrankConfig(
    val preset: BrokenPreset = BrokenPreset.OLED_GREEN_LINE,
    val verticalLineCount: Int = 5,
    val horizontalLineCount: Int = 2,
    val linePalette: LinePalette = LinePalette.NEON_GREEN,
    val flickerIntensity: FlickerIntensity = FlickerIntensity.MEDIUM,
    val glitchIntensity: GlitchIntensity = GlitchIntensity.MEDIUM,
    val showGlassCracks: Boolean = true,
    val showLcdInkBleed: Boolean = false,
    val showScanlines: Boolean = true,
    val soundEnabled: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val touchMode: TouchMode = TouchMode.PASS_THROUGH,
    val triggerMode: TriggerMode = TriggerMode.IMMEDIATE,
    val crackImpactX: Float = 0.38f, // Relative 0..1
    val crackImpactY: Float = 0.42f
) : Serializable {

    companion object {
        fun createFromPreset(preset: BrokenPreset): PrankConfig {
            return when (preset) {
                BrokenPreset.OLED_GREEN_LINE -> PrankConfig(
                    preset = preset,
                    verticalLineCount = 4,
                    horizontalLineCount = 1,
                    linePalette = LinePalette.NEON_GREEN,
                    flickerIntensity = FlickerIntensity.MEDIUM,
                    glitchIntensity = GlitchIntensity.LOW,
                    showGlassCracks = false,
                    showLcdInkBleed = false,
                    showScanlines = true,
                    soundEnabled = false,
                    hapticsEnabled = true,
                    touchMode = TouchMode.PASS_THROUGH
                )
                BrokenPreset.CATASTROPHIC_LCD -> PrankConfig(
                    preset = preset,
                    verticalLineCount = 8,
                    horizontalLineCount = 3,
                    linePalette = LinePalette.RAINBOW_OLED,
                    flickerIntensity = FlickerIntensity.HIGH,
                    glitchIntensity = GlitchIntensity.MEDIUM,
                    showGlassCracks = true,
                    showLcdInkBleed = true,
                    showScanlines = false,
                    soundEnabled = true,
                    hapticsEnabled = true,
                    touchMode = TouchMode.PASS_THROUGH
                )
                BrokenPreset.CYBER_GLITCH -> PrankConfig(
                    preset = preset,
                    verticalLineCount = 6,
                    horizontalLineCount = 4,
                    linePalette = LinePalette.MAGENTA_CYAN,
                    flickerIntensity = FlickerIntensity.HIGH,
                    glitchIntensity = GlitchIntensity.EXTREME,
                    showGlassCracks = false,
                    showLcdInkBleed = false,
                    showScanlines = true,
                    soundEnabled = true,
                    hapticsEnabled = true,
                    touchMode = TouchMode.PASS_THROUGH
                )
                BrokenPreset.RIBBON_CABLE -> PrankConfig(
                    preset = preset,
                    verticalLineCount = 12,
                    horizontalLineCount = 2,
                    linePalette = LinePalette.BURNT_WHITE,
                    flickerIntensity = FlickerIntensity.CHAOS,
                    glitchIntensity = GlitchIntensity.HIGH,
                    showGlassCracks = false,
                    showLcdInkBleed = false,
                    showScanlines = true,
                    soundEnabled = false,
                    hapticsEnabled = true,
                    touchMode = TouchMode.PASS_THROUGH
                )
                BrokenPreset.HARDWARE_MELTDOWN -> PrankConfig(
                    preset = preset,
                    verticalLineCount = 14,
                    horizontalLineCount = 5,
                    linePalette = LinePalette.RAINBOW_OLED,
                    flickerIntensity = FlickerIntensity.CHAOS,
                    glitchIntensity = GlitchIntensity.EXTREME,
                    showGlassCracks = true,
                    showLcdInkBleed = true,
                    showScanlines = true,
                    soundEnabled = true,
                    hapticsEnabled = true,
                    touchMode = TouchMode.PASS_THROUGH
                )
                BrokenPreset.CUSTOM -> PrankConfig(
                    preset = preset,
                    verticalLineCount = 6,
                    horizontalLineCount = 2,
                    linePalette = LinePalette.NEON_GREEN,
                    flickerIntensity = FlickerIntensity.MEDIUM,
                    glitchIntensity = GlitchIntensity.MEDIUM,
                    showGlassCracks = true,
                    showLcdInkBleed = true,
                    showScanlines = true,
                    soundEnabled = false,
                    hapticsEnabled = true,
                    touchMode = TouchMode.PASS_THROUGH
                )
            }
        }
    }
}
