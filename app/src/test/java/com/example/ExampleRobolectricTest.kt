package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
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
    assertEquals("Chakib Aiming Wheel Biliardo", appName)
  }

  @Test
  fun `config persistence test`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val customConfig = AimOverlayConfig(
      ballRadiusDp = 35f,
      lineColor = 0xFF00E5FF.toInt(),
      showRebound = true,
      reboundCount = 2
    )
    AimOverlayConfig.save(context, customConfig)
    val loaded = AimOverlayConfig.load(context)

    assertEquals(35f, loaded.ballRadiusDp, 0.01f)
    assertEquals(0xFF00E5FF.toInt(), loaded.lineColor)
    assertTrue(loaded.showRebound)
    assertEquals(2, loaded.reboundCount)
  }

  @Test
  fun `table calibration persistence test`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val sampleTable = CalibratedTable(
      leftRatio = 0.05f,
      topRatio = 0.15f,
      rightRatio = 0.95f,
      bottomRatio = 0.85f,
      isLandscape = true,
      isCalibrated = true
    )
    AimOverlayConfig.saveCalibration(context, sampleTable)
    val loadedTable = AimOverlayConfig.loadCalibration(context)

    assertEquals(0.05f, loadedTable.leftRatio, 0.001f)
    assertEquals(0.95f, loadedTable.rightRatio, 0.001f)
    assertTrue(loadedTable.isCalibrated)
  }
}
