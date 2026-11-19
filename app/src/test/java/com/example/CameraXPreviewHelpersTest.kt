package com.example

import com.example.ui.components.CameraBitmapProcessor
import com.example.ui.components.CameraHardwareCapabilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraXPreviewHelpersTest {

    @Test
    fun computeDownscaleFactor_keepsSmallFramesAtFullScale() {
        val factor = CameraBitmapProcessor.computeDownscaleFactor(
            width = 960,
            height = 720,
            maxEdgePx = 1280
        )
        assertEquals(1f, factor, 0.0001f)
    }

    @Test
    fun computeDownscaleFactor_scalesHighResolutionLandscapeFramesDownToLimit() {
        val factor = CameraBitmapProcessor.computeDownscaleFactor(
            width = 4032,
            height = 3024,
            maxEdgePx = 1280
        )
        assertEquals(1280f / 4032f, factor, 0.0001f)
        assertEquals(1280, (4032 * factor).toInt())
    }

    @Test
    fun computeDownscaleFactor_scalesHighResolutionPortraitFramesDownToLimit() {
        val factor = CameraBitmapProcessor.computeDownscaleFactor(
            width = 1920,
            height = 2560,
            maxEdgePx = 1280
        )
        assertEquals(0.5f, factor, 0.0001f)
    }

    @Test
    fun clampZoomRatio_respectsHardwareBounds() {
        assertEquals(1.0f, CameraBitmapProcessor.clampZoomRatio(0.4f, 1.0f, 5.0f), 0.0001f)
        assertEquals(5.0f, CameraBitmapProcessor.clampZoomRatio(8.5f, 1.0f, 5.0f), 0.0001f)
        assertEquals(2.5f, CameraBitmapProcessor.clampZoomRatio(2.5f, 1.0f, 5.0f), 0.0001f)
    }

    @Test
    fun computeAverageLuminance_handlesDarkBrightAndEmptyFrames() {
        val darkBytes = ByteArray(1024) { 25.toByte() }
        val brightBytes = ByteArray(1024) { 210.toByte() }
        assertEquals(25, CameraBitmapProcessor.computeAverageLuminance(darkBytes))
        assertEquals(210, CameraBitmapProcessor.computeAverageLuminance(brightBytes))
        assertEquals(128, CameraBitmapProcessor.computeAverageLuminance(ByteArray(0)))
    }

    @Test
    fun cameraHardwareCapabilities_reportsDerivedFlagsAccurately() {
        val fullCaps = CameraHardwareCapabilities(
            hasBackCamera = true,
            hasFrontCamera = true,
            hasFlashUnit = true,
            minZoomRatio = 1f,
            maxZoomRatio = 4f,
            exposureMin = -4,
            exposureMax = 4
        )
        assertTrue(fullCaps.canSwitchLens)
        assertTrue(fullCaps.supportsZoom)
        assertTrue(fullCaps.supportsExposureCompensation)

        val limitedCaps = CameraHardwareCapabilities(
            hasBackCamera = true,
            hasFrontCamera = false,
            hasFlashUnit = false,
            minZoomRatio = 1f,
            maxZoomRatio = 1f,
            exposureMin = 0,
            exposureMax = 0
        )
        assertFalse(limitedCaps.canSwitchLens)
        assertFalse(limitedCaps.supportsZoom)
        assertFalse(limitedCaps.supportsExposureCompensation)
    }
}
