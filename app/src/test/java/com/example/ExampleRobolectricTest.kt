package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.BrokenPreset
import com.example.model.PrankConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Screen Break Prank", appName)
    }

    @Test
    fun `test presets configuration validity`() {
        BrokenPreset.values().forEach { preset ->
            val config = PrankConfig.createFromPreset(preset)
            assertNotNull(config)
            assertTrue(config.verticalLineCount > 0)
            assertNotNull(config.linePalette)
            assertNotNull(config.flickerIntensity)
        }
    }
}
